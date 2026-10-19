package ru.vladaak.nagitv;
import android.graphics.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
/** Separate RAM-only loader for home and saver. No files, image cache or catalog scraping. */
final class OnlineSaver {
    private final MainActivity a;
    private final boolean home;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private Future<?> task;
    private volatile boolean closed;
    private volatile int generation;
    private int index;
    private final ArrayList<JSONObject> images=new ArrayList<>();
    OnlineSaver(MainActivity a){this(a,false);}
    OnlineSaver(MainActivity a,boolean home){this.a=a;this.home=home;}
    void cancel(){generation++;if(task!=null)task.cancel(true);}
    void close(){closed=true;cancel();io.shutdownNow();}
    private boolean valid(int g){return !closed&&g==generation&&!Thread.currentThread().isInterrupted();}
    void first(){
        if(closed)return;cancel();final int g=generation;
        final String source=a.artSource(home),links=a.prefs.getString(a.artKey(home)+source+"_links","");
        final String manifest=a.prefs.getString("manifest",MainActivity.MANIFEST);
        final String zerochanUser=a.prefs.getString("zerochan_user","");
        task=io.submit(()->{
            try{
                images.clear();index=0;ArrayList<JSONObject> accepted=new ArrayList<>();
                if("local".equals(source))return;
                if("wallhaven".equals(source)){
                    JSONArray arr=new JSONObject(Net.json(ArtSources.wallhaven())).getJSONArray("data");
                    for(int i=0;i<arr.length();i++){
                        JSONObject v=arr.getJSONObject(i);
                        String url=v.getString("path");
                        if(!ArtSources.wallhavenImage(url,v.optString("category"),v.optString("purity"),v.optInt("dimension_x"),v.optInt("dimension_y")))continue;
                        accepted.add(new JSONObject().put("url",url).put("title","Wallhaven · "+v.optString("id")));
                    }
                }else if("alpha".equals(source)||"zerochan".equals(source)){
                    String[] urls=ArtSources.links(source,links);
                    for(int i=0;i<urls.length;i++){
                        String id="zerochan".equals(source)?ArtSources.zerochanId(urls[i]):null;
                        JSONObject item=new JSONObject().put("url",urls[i]).put("title",ArtSources.label(source)+" · "+(i+1));
                        if(id!=null){if(zerochanUser.isEmpty())throw new IllegalArgumentException("Укажите ник в Настроить источник → Изменить ник");item.put("zerochan_id",id).put("api_user",zerochanUser);}
                        accepted.add(item);
                    }
                }else if("manifest".equals(source)){
                    JSONArray arr=new JSONObject(Net.json(manifest)).getJSONArray("images");
                    for(int i=0;i<Math.min(100,arr.length());i++){
                        JSONObject v=arr.getJSONObject(i);Policy.https(v.getString("url"));
                        if(Policy.fourK(v.optInt("width"),v.optInt("height"))&&v.optString("title").length()<200)accepted.add(v);
                    }
                }else throw new IOException("Неизвестный источник. Выберите его заново.");
                if(accepted.isEmpty())throw new IOException("Нет подходящих горизонтальных 4K-артов");
                if(!valid(g))return;Collections.shuffle(accepted);images.addAll(accepted);load(g);
            }catch(Exception e){error(g,e);}catch(OutOfMemoryError e){error(g,new IOException("Недостаточно RAM"));}
        });
    }
    void next(){if(closed)return;final int g=generation;task=io.submit(()->{try{if(valid(g)&&!images.isEmpty())load(g);}catch(Exception e){error(g,e);}catch(OutOfMemoryError e){error(g,new IOException("Недостаточно RAM"));}});}
    private void load(int g)throws Exception{
        Exception last=null;
        for(int tries=0;tries<Math.min(3,images.size());tries++){
            if(!valid(g))return;JSONObject item=images.get((index++)%images.size());
            try{
                if(item.has("zerochan_id")&&!item.optBoolean("zerochan_resolved")){
                    String id=item.getString("zerochan_id");JSONObject v=new JSONObject(Net.zerochan(id,item.getString("api_user")));
                    if(!Policy.fourK(v.optInt("width"),v.optInt("height")))throw new IOException("Арт Zerochan "+id+" меньше 3840×2160 или не горизонтальный");
                    String full=v.getString("full"),host=Policy.https(full).getHost();
                    if(!host.equals("zerochan.net")&&!host.endsWith(".zerochan.net"))throw new IOException("Неожиданный адрес оригинала Zerochan");
                    String title=v.optString("primary","Zerochan");if(title.length()>100)title=title.substring(0,100);
                    item.put("url",full).put("title",title+" · Zerochan "+id).put("zerochan_resolved",true);
                }
                byte[] data=Net.bytes(item.getString("url"),24*1024*1024);
                if("base64".equals(item.optString("encoding")))data=Base64.decode(data,Base64.DEFAULT);
                else if(!"binary".equals(item.optString("encoding","binary")))throw new IOException("Неизвестная кодировка");
                BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);
                if(!Policy.fourK(o.outWidth,o.outHeight))throw new IOException("Нужен горизонтальный арт от 3840×2160, не более 8192 px по стороне");
                o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.RGB_565;o.inDither=true;
                Bitmap b=BitmapFactory.decodeByteArray(data,0,data.length,o);if(b==null)throw new IOException("Формат картинки не поддерживается");
                String title=item.optString("title","Аниме-пейзаж");
                if(!valid(g)){b.recycle();return;}
                a.runOnUiThread(()->{
                    if(!closed&&g==generation&&a.artActive()&&(home||a.scene.isSaver())){
                        if(home)a.scene.homePictureReady(b,title);else a.scene.pictureReady(b,title);
                    }else b.recycle();
                });return;
            }catch(OutOfMemoryError e){throw new IOException("Недостаточно оперативной памяти для 4K");}
            catch(Exception e){last=e;}
        }
        throw last==null?new IOException("Нет доступных изображений"):last;
    }
    private void error(int g,Exception e){
        final String message=e instanceof IllegalArgumentException?e.getMessage():"Не удалось загрузить арт: "+e.getMessage();
        a.runOnUiThread(()->{if(!closed&&g==generation&&a.artActive()){
            if(home)a.scene.homePictureError(message);else if(a.scene.isSaver())a.scene.saverError(message);
        }});
    }
}
