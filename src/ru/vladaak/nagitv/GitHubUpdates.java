package ru.vladaak.nagitv;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;
import org.json.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
final class GitHubUpdates {
    private final MainActivity a;
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private boolean busy,closed;
    GitHubUpdates(MainActivity a){this.a=a;}
    void close(){closed=true;work.shutdownNow();}
    private void ui(Runnable r){a.runOnUiThread(()->{if(!closed&&!a.isFinishing())r.run();});}
    void check(){
        if(busy){a.toast("Проверка или загрузка уже выполняется");return;}
        String repo=a.prefs.getString("repo",MainActivity.REPO);if(repo.isEmpty()){a.editRepository();return;}
        final String r;try{r=Policy.repo(repo);}catch(Exception e){a.toast(e.getMessage());return;}
        busy=true;a.updateStatus="Проверяем GitHub Releases…";a.scene.invalidate();
        work.execute(()->{
            try{
                JSONObject release=new JSONObject(Net.json("https://api.github.com/repos/"+r+"/releases/latest"));
                if(release.optBoolean("draft")||release.optBoolean("prerelease"))throw new IOException("Нужен опубликованный стабильный релиз");
                String tag=release.getString("tag_name");
                if(!Policy.newer(tag,MainActivity.VERSION)){
                    ui(()->{busy=false;a.updateStatus="Новых стабильных версий нет · "+tag;a.scene.invalidate();a.message("Обновления",a.updateStatus);});return;
                }
                JSONArray assets=release.getJSONArray("assets");JSONObject apk=null;
                for(int i=0;i<assets.length();i++){
                    JSONObject v=assets.getJSONObject(i);String n=v.optString("name");
                    if(n.startsWith("Nagi-TV-")&&n.endsWith(".apk")&&Policy.releaseAsset(v.optString("browser_download_url"),r)){if(apk!=null)throw new IOException("В релизе несколько APK Nagi-TV-. Оставьте один универсальный APK.");apk=v;}
                }
                if(apk==null)throw new IOException("Нет APK с именем Nagi-TV-<версия>.apk");
                final JSONObject chosen=apk;ui(()->{busy=false;a.updateStatus="Доступна версия "+tag;a.scene.invalidate();new AlertDialog.Builder(a).setTitle("Nagi TV · "+tag).setMessage("Скачать обновление из "+r+"?\n\nБудут проверены имя пакета, versionCode и подпись. Установка — только через системное подтверждение.").setPositiveButton("Скачать",(d,w)->download(chosen,r)).setNegativeButton("Позже",null).show();});
            }catch(Exception e){ui(()->{busy=false;a.updateStatus="Не удалось проверить: "+e.getMessage();a.scene.invalidate();a.message("GitHub Releases",a.updateStatus);});}
        });
    }
    private void download(JSONObject asset,String repo){
        if(busy||closed)return;busy=true;a.updateStatus="Загружаем APK…";a.scene.invalidate();
        work.execute(()->{
            File dir=new File(a.getFilesDir(),"updates");dir.mkdirs();File temp=new File(dir,"download.part"),dest=new File(dir,"update.apk");
            try{
                String url=asset.getString("browser_download_url");if(!Policy.releaseAsset(url,repo))throw new IOException("Неверный адрес APK");
                Net.file(url,temp,120*1024*1024);
                long expected=asset.optLong("size",-1);if(expected<=0||temp.length()!=expected)throw new IOException("Размер APK не совпадает с GitHub");
                String digest=asset.optString("digest","");if(!digest.isEmpty()&&!digest.equals("null")){
                    if(!digest.startsWith("sha256:"))throw new IOException("Неизвестный формат контрольной суммы");
                    if(!digest.substring(7).equalsIgnoreCase(sha(temp)))throw new IOException("Контрольная сумма APK не совпала");
                }
                verify(temp);
                if(dest.exists()&&!dest.delete())throw new IOException("Не удалось очистить старый APK");
                if(!temp.renameTo(dest))throw new IOException("Не удалось подготовить APK");
                ui(()->{busy=false;a.updateStatus="APK проверен. Готов к установке.";a.scene.invalidate();install();});
            }catch(Exception e){temp.delete();ui(()->{busy=false;a.updateStatus="Обновление отклонено: "+e.getMessage();a.scene.invalidate();a.message("Обновления",a.updateStatus);});}
        });
    }
    private String sha(File f)throws Exception{
        MessageDigest m=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))>0)m.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte v:m.digest())s.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return s.toString();
    }
    private void verify(File f)throws Exception{
        PackageManager pm=a.getPackageManager();int flags=PackageManager.GET_SIGNING_CERTIFICATES;
        PackageInfo next=pm.getPackageArchiveInfo(f.getAbsolutePath(),flags),current=pm.getPackageInfo(a.getPackageName(),flags);
        if(next==null||!a.getPackageName().equals(next.packageName))throw new IOException("Это APK другого приложения");
        if(next.getLongVersionCode()<=current.getLongVersionCode())throw new IOException("versionCode не увеличен");
        if(next.signingInfo==null||current.signingInfo==null)throw new IOException("Не удалось прочитать подпись");
        android.content.pm.Signature[] x=next.signingInfo.getApkContentsSigners(),y=current.signingInfo.getApkContentsSigners();
        if(x.length!=y.length)throw new IOException("Подпись отличается");
        for(android.content.pm.Signature sx:x){boolean found=false;for(android.content.pm.Signature sy:y)if(sx.equals(sy)){found=true;break;}if(!found)throw new IOException("Ключ подписи отличается от установленной версии");}
    }
    void install(){
        File dest=new File(a.getFilesDir(),"updates/update.apk");if(!dest.isFile()){a.toast("Сначала скачайте обновление");return;}
        if(!a.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(a).setTitle("Разрешение на установку").setMessage("Разрешите Nagi TV устанавливать обновления из этого источника. Затем вернитесь и выберите «Установить загруженный APK».").setPositiveButton("Настройки",(d,w)->a.openSystem(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName())))).setNegativeButton("Отмена",null).show();return;
        }
        try{
            verify(dest);Uri uri=Uri.parse("content://ru.vladaak.nagitv.updates/update.apk");
            Intent i=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("Nagi TV update",uri));a.startActivity(i);
        }catch(Exception e){a.message("Установка",e.getMessage());}
    }
    void deleteDownload(){if(busy){a.toast("Дождитесь завершения загрузки");return;}File dir=new File(a.getFilesDir(),"updates");new File(dir,"update.apk").delete();new File(dir,"download.part").delete();a.toast("Загруженный APK удалён");}
}
