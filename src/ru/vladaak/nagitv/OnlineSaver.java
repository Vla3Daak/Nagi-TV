package ru.vladaak.nagitv;
import android.graphics.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
/** No disk cache. JSON, encoded bytes and decoded pictures live in memory only. */
final class OnlineSaver {
    private final MainActivity a;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private Future<?> task;
    private volatile boolean closed;
    private volatile int generation; private int index;
    private final ArrayList<JSONObject> images=new ArrayList<>();
    OnlineSaver(MainActivity a){this.a=a;}
    void cancel(){generation++;if(task!=null)task.cancel(true);}
    void close(){closed=true;cancel();io.shutdownNow();}
    void first(){cancel();final int g=generation;task=io.submit(()->{
        images.clear();index=0;
        try{
            JSONObject manifest=new JSONObject(Net.json(a.prefs.getString("manifest",MainActivity.MANIFEST)));
            JSONArray arr=manifest.getJSONArray("images");ArrayList<JSONObject> accepted=new ArrayList<>();
            for(int i=0;i<Math.min(100,arr.length());i++){
                JSONObject v=arr.getJSONObject(i);Policy.https(v.getString("url"));
                if(v.optInt("width",0)>=3840&&v.optInt("height",0)>=2160&&v.optString("title").length()<200)accepted.add(v);
            }
            if(accepted.isEmpty())throw new IOException("В подборке нет горизонтальных 4K-артов");
            Collections.shuffle(accepted);if(g!=generation||closed)return;images.clear();images.addAll(accepted);index=0;load(g);
        }catch(Exception e){error(g,e);}
    });}
    void next(){if(closed||images.isEmpty())return;final int g=generation;task=io.submit(()->{try{load(g);}catch(Exception e){error(g,e);}});}
    private void load(int g)throws Exception{
        Exception last=null;
        for(int tries=0;tries<Math.min(3,images.size());tries++){
            if(g!=generation||Thread.currentThread().isInterrupted())return;
            JSONObject image=images.get((index++)%images.size());
            try{
                byte[] data=Net.bytes(image.getString("url"),24*1024*1024);
                if("base64".equals(image.optString("encoding")))data=Base64.decode(data,Base64.DEFAULT);
                else if(!image.optString("encoding", "binary").equals("binary"))throw new IOException("Неизвестная кодировка изображения");
                BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,o);
                if(!Policy.fourK(o.outWidth,o.outHeight))throw new IOException("Реальный размер изображения меньше 3840×2160 или превышает лимит");
                o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.RGB_565;o.inDither=true;
                Bitmap b=BitmapFactory.decodeByteArray(data,0,data.length,o);if(b==null)throw new IOException("Не удалось декодировать изображение");
                String title=image.optString("title","Аниме-пейзаж");
                if(g!=generation||closed)return;
                a.runOnUiThread(()->{if(!closed&&g==generation&&a.scene.isSaver())a.scene.pictureReady(b,title);});return;
            }catch(OutOfMemoryError e){throw new IOException("Недостаточно оперативной памяти для 4K");}
            catch(Exception e){last=e;}
        }
        throw last==null?new IOException("Нет доступных изображений"):last;
    }
    private void error(int g,Exception e){a.runOnUiThread(()->{if(!closed&&g==generation&&a.scene.isSaver())a.scene.saverError("Не удалось загрузить арт. Проверьте интернет или подборку.");});}
}
