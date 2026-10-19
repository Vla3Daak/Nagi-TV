package ru.vladaak.nagitv;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    static final String VERSION="0.2.0", REPO="Vla3Daak/Nagi-TV";
    static final String MANIFEST="https://raw.githubusercontent.com/Vla3Daak/Nagi-TV/main/screensavers.json";
    SharedPreferences prefs;
    SceneView scene;
    GitHubUpdates updates;
    OnlineSaver saver,homeArt;
    final ArrayList<AppEntry> apps=new ArrayList<>();
    ArrayList<String> favorites,hidden,order;
    final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService appLoader=Executors.newSingleThreadExecutor();
    private boolean loading,closed,resumed,receiverSet;
    private SoundPool sounds;
    private final HashMap<String,Integer> soundIds=new HashMap<>();
    private final HashSet<Integer> loadedSounds=new HashSet<>();
    String updateStatus="Проверка обновлений — по запросу";
    static final class AppEntry{String pkg,label;Intent launch;Bitmap icon,banner;}
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){refreshApps();}};
    private final Runnable idle=()->{if(resumed&&!closed&&hasWindowFocus()&&prefs.getBoolean("saver_enabled",true)&&!scene.isSettings()&&!scene.isSaver()){scene.startSaver();}};
    private final Runnable clock=new Runnable(){public void run(){if(resumed&&!closed){scene.invalidate();handler.postDelayed(this,15000);}}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("nagi",MODE_PRIVATE);
        String bundledIdentity=getString(R.string.zerochan_username);
        if(!prefs.contains("zerochan_user")&&!bundledIdentity.isEmpty())prefs.edit().putString("zerochan_user",bundledIdentity).apply();
        favorites=list("favorites");hidden=list("hidden");order=list("order");
        updates=new GitHubUpdates(this);saver=new OnlineSaver(this);homeArt=new OnlineSaver(this,true);scene=new SceneView(this);setContentView(scene);
        initSounds();immersive();
        IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_PACKAGE_ADDED);f.addAction(Intent.ACTION_PACKAGE_REMOVED);f.addAction(Intent.ACTION_PACKAGE_CHANGED);f.addAction(Intent.ACTION_PACKAGE_REPLACED);f.addDataScheme("package");registerReceiver(receiver,f);receiverSet=true;
        refreshApps();
    }
    @Override protected void onResume(){super.onResume();resumed=true;if(scene!=null){scene.stopSaver();scene.resumeHomeArt();refreshApps();handler.removeCallbacks(clock);handler.post(clock);touchIdle();}}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(idle);handler.removeCallbacks(clock);if(scene!=null){scene.stopSaver();scene.pauseHomeArt();}super.onPause();}
    @Override protected void onDestroy(){closed=true;handler.removeCallbacksAndMessages(null);saver.close();homeArt.close();updates.close();appLoader.shutdownNow();if(sounds!=null)sounds.release();if(receiverSet)unregisterReceiver(receiver);super.onDestroy();}
    @Override public void onWindowFocusChanged(boolean has){super.onWindowFocusChanged(has);if(has){immersive();touchIdle();}else handler.removeCallbacks(idle);}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);scene.showHome();touchIdle();}
    @Override public void onBackPressed(){sound("back");scene.back();touchIdle();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(e.getAction()==KeyEvent.ACTION_DOWN){touchIdle();if(scene!=null&&scene.isSaver()){scene.stopSaver();return true;}}
        return super.dispatchKeyEvent(e);
    }
    void immersive(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    void touchIdle(){handler.removeCallbacks(idle);if(resumed&&!closed)handler.postDelayed(idle,prefs.getInt("saver_minutes",5)*60000L);}
    private ArrayList<String> list(String key){ArrayList<String> a=new ArrayList<>();try{JSONArray j=new JSONArray(prefs.getString(key,"[]"));for(int i=0;i<j.length();i++)a.add(j.getString(i));}catch(Exception ignored){}return a;}
    void saveLists(){prefs.edit().putString("favorites",new JSONArray(favorites).toString()).putString("hidden",new JSONArray(hidden).toString()).putString("order",new JSONArray(order).toString()).apply();}
    private Bitmap bitmap(Drawable d,int w,int h){if(d==null)return null;try{Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);d.setBounds(0,0,w,h);d.draw(new Canvas(b));return b;}catch(Exception e){return null;}}
    void refreshApps(){
        if(loading||closed)return;loading=true;
        appLoader.execute(()->{
            ArrayList<AppEntry> found=new ArrayList<>();HashSet<String> seen=new HashSet<>();PackageManager pm=getPackageManager();
            boolean phone=prefs.getBoolean("phone_apps",false);
            for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
                if(!phone&&category.equals(Intent.CATEGORY_LAUNCHER))continue;
                try{for(ResolveInfo ri:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
                    String pkg=ri.activityInfo.packageName;
                    if(pkg.equals(getPackageName())||!seen.add(pkg))continue;
                    // Do not offer competing HOME activities as ordinary entertainment apps.
                    AppEntry e=new AppEntry();e.pkg=pkg;e.label=ri.loadLabel(pm).toString();if(e.label.isEmpty())e.label=pkg;
                    e.launch=new Intent(Intent.ACTION_MAIN).addCategory(category).setComponent(new ComponentName(pkg,ri.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    e.icon=bitmap(ri.loadIcon(pm),128,128);
                    try{e.banner=bitmap(ri.activityInfo.loadBanner(pm),384,216);}catch(Exception ignored){}
                    found.add(e);
                }}catch(Exception ignored){}
            }
            Collections.sort(found,(x,y)->x.label.compareToIgnoreCase(y.label));
            runOnUiThread(()->{loading=false;if(closed)return;apps.clear();apps.addAll(found);
                if(!prefs.contains("favorites")&&!apps.isEmpty()){
                    for(AppEntry e:apps){String n=e.label.toLowerCase(Locale.ROOT);if(n.contains("youtube")||n.contains("netflix")||n.contains("anidaak")||n.contains("кинопоиск")||n.contains("vlc"))if(favorites.size()<6)favorites.add(e.pkg);}
                    for(AppEntry e:apps)if(favorites.size()<6&&!favorites.contains(e.pkg))favorites.add(e.pkg);saveLists();
                }scene.dataChanged();
            });
        });
    }
    ArrayList<AppEntry> visible(){ArrayList<AppEntry> v=new ArrayList<>();for(AppEntry e:apps)if(!hidden.contains(e.pkg))v.add(e);Collections.sort(v,(x,y)->{int a=order.indexOf(x.pkg),b=order.indexOf(y.pkg);if(a<0)a=Integer.MAX_VALUE;if(b<0)b=Integer.MAX_VALUE;return a==b?x.label.compareToIgnoreCase(y.label):Integer.compare(a,b);});return v;}
    ArrayList<AppEntry> favoriteApps(){ArrayList<AppEntry> v=new ArrayList<>();for(String p:favorites)for(AppEntry e:apps)if(e.pkg.equals(p)&&!hidden.contains(p)){v.add(e);break;}return v;}
    void launch(AppEntry e){sound("open");try{startActivity(e.launch);}catch(Exception ex){toast("Не удалось открыть приложение");refreshApps();}}
    void actions(AppEntry e,boolean fromFavorites){
        boolean fav=favorites.contains(e.pkg);String[] opts={fav?"Убрать из избранного":"В избранное", "Переместить левее","Переместить правее","Скрыть","О приложении"};
        new AlertDialog.Builder(this).setTitle(e.label).setItems(opts,(d,w)->{
            if(w==0){if(fav)favorites.remove(e.pkg);else if(favorites.size()<6)favorites.add(e.pkg);else{message("Избранное","В верхнем ряду максимум 6 приложений. Сначала уберите одно из них.");return;}}
            if(w==1||w==2){ArrayList<String> target=favorites;if(!fromFavorites){ArrayList<AppEntry> previous=visible();order.clear();for(AppEntry a:previous)order.add(a.pkg);target=order;}int x=target.indexOf(e.pkg),y=x+(w==1?-1:1);if(x>=0&&y>=0&&y<target.size())Collections.swap(target,x,y);}
            if(w==3){new AlertDialog.Builder(this).setTitle("Скрыть «"+e.label+"»?").setMessage("Вернуть приложение можно в настройках Nagi TV → Приложения → Скрытые приложения.").setPositiveButton("Скрыть",(q,v)->{if(!hidden.contains(e.pkg))hidden.add(e.pkg);saveLists();scene.dataChanged();}).setNegativeButton("Отмена",null).show();return;}
            if(w==4){openSystem(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+e.pkg)));return;}
            saveLists();scene.dataChanged();sound("open");
        }).setNegativeButton("Закрыть",null).show();
    }
    void showHidden(){ArrayList<AppEntry> h=new ArrayList<>();for(AppEntry e:apps)if(hidden.contains(e.pkg))h.add(e);if(h.isEmpty()){toast("Скрытых приложений нет");return;}String[] names=new String[h.size()];for(int i=0;i<h.size();i++)names[i]=h.get(i).label;new AlertDialog.Builder(this).setTitle("Вернуть приложение").setItems(names,(d,w)->{hidden.remove(h.get(w).pkg);saveLists();scene.dataChanged();}).setNeutralButton("Вернуть все",(d,w)->{hidden.clear();saveLists();scene.dataChanged();}).setNegativeButton("Отмена",null).show();}
    void chooseHome(){
        try{RoleManager rm=getSystemService(RoleManager.class);if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_HOME)){if(rm.isRoleHeld(RoleManager.ROLE_HOME)){toast("Nagi TV уже выбран главным экраном");return;}startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME),101);return;}}catch(Exception ignored){}
        new AlertDialog.Builder(this).setTitle("Главный экран").setMessage("Выберите Nagi TV в системных настройках домашнего приложения. Если прошивка скрывает этот пункт, используйте инструкцию ADB из комплекта. Штатный лаунчер не удаляется.").setPositiveButton("Открыть",(d,w)->openSystem(new Intent(Settings.ACTION_HOME_SETTINGS))).setNegativeButton("Отмена",null).show();
    }
    void openSystem(Intent i){try{startActivity(i);}catch(Exception e){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception x){toast("Этот раздел недоступен на прошивке");}}}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    void message(String title,String text){new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("Понятно",null).show();}
    interface TextSave{void save(String value)throws Exception;}
    void edit(String title,String initial,String hint,TextSave save){
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(initial);input.setHint(hint);input.setSelectAllOnFocus(true);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout box=new LinearLayout(this);box.setPadding(32,10,32,10);box.addView(input,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(box).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();d.setOnShowListener(v->{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{try{save.save(input.getText().toString().trim());scene.dataChanged();d.dismiss();}catch(Exception e){input.setError(e.getMessage());}});});d.show();
    }
    void editRepository(){edit("Репозиторий GitHub",prefs.getString("repo",REPO),"владелец/репозиторий",value->{prefs.edit().putString("repo",Policy.repo(value)).apply();updateStatus="Репозиторий сохранён. Нажмите «Проверить обновления».";});}
    void editManifest(){edit("Онлайн-подборка аниме-артов",prefs.getString("manifest",MANIFEST),"https://…/screensavers.json",value->{Policy.https(value);prefs.edit().putString("manifest",value).apply();saver.cancel();if("manifest".equals(artSource(true)))scene.resetHomeArt();});}
    boolean artActive(){return resumed&&!closed;}
    String artKey(boolean home){return home?"home_":"saver_";}
    String artSource(boolean home){return prefs.getString(artKey(home)+"source",home?"local":"manifest");}
    void selectArtSource(boolean home){
        int offset=home?0:1,idx=0;String current=artSource(home);
        String[] names=Arrays.copyOfRange(ArtSources.NAMES,offset,ArtSources.NAMES.length);
        for(int i=offset;i<ArtSources.IDS.length;i++)if(current.equals(ArtSources.IDS[i]))idx=i-offset;
        pick(home?"Источник фона меню":"Источник заставки",names,idx,i->{
            String source=ArtSources.IDS[i+offset];prefs.edit().putString(artKey(home)+"source",source).apply();
            if(home)scene.resetHomeArt();else saver.cancel();
            if(("alpha".equals(source)||"zerochan".equals(source))&&prefs.getString(artKey(home)+source+"_links","").isEmpty())configureArt(home);
        });
    }
    void configureArt(boolean home){
        String source=artSource(home);
        if("local".equals(source)){message("Встроенный фон","Выберите онлайн-источник, чтобы загружать аниме-арты.");return;}
        if("manifest".equals(source)){editManifest();return;}
        if("wallhaven".equals(source)){message("Wallhaven · автоматическая подборка","Аниме-пейзажи, SFW, горизонтальные файлы от 3840×2160, 16:9. Фильтр SFW не гарантирует пригодность каждого арта для детей. Картинки загружаются только в RAM. Для ручного выбора используйте личный JSON-список Nagi.");return;}
        if("zerochan".equals(source)){
            String user=prefs.getString("zerochan_user","");
            new AlertDialog.Builder(this).setTitle("Zerochan · официальный API").setMessage("Ник API: "+(user.isEmpty()?"не указан":user)+"\nДобавьте ссылки на выбранные страницы zerochan.net/номер или прямые изображения. По страницам оригиналы будут получены через API. Выдача случайной подборки сейчас недоступна (HTTP 500 при проверке).\n\nПроверяйте содержание и права на выбранные арты самостоятельно.")
                .setPositiveButton("Страницы / ссылки",(d,w)->editArtLinks(home,source))
                .setNeutralButton("Изменить ник",(d,w)->edit("Ник Zerochan для API",prefs.getString("zerochan_user",""),"Имя пользователя",value->{prefs.edit().putString("zerochan_user",ArtSources.zerochanUser(value)).apply();if("zerochan".equals(artSource(true)))scene.resetHomeArt();}))
                .setNegativeButton("Каталог",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(ArtSources.catalog(source))));}catch(Exception e){message("Zerochan",ArtSources.catalog(source));}}).show();return;
        }
        new AlertDialog.Builder(this).setTitle(ArtSources.label(source)).setMessage("Выберите изображения в каталоге и добавьте прямые HTTPS-ссылки на JPG/PNG/WebP — по одной на строку. Не ссылки на страницы. От 3840×2160. Только личное использование с соблюдением прав автора.\n\nСлучайная выдача и парсинг каталога не используются. Списки меню и заставки независимы.")
            .setPositiveButton("Ввести ссылки",(d,w)->editArtLinks(home,source))
            .setNeutralButton("Открыть каталог",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(ArtSources.catalog(source))));}catch(Exception e){message("Каталог",ArtSources.catalog(source)+"\nОткройте ссылку на телефоне или компьютере.");}})
            .setNegativeButton("Закрыть",null).show();
    }
    void editArtLinks(boolean home,String source){
        String key=artKey(home)+source+"_links";EditText input=new EditText(this);input.setSingleLine(false);input.setMinLines(3);input.setMaxLines(6);input.setText(prefs.getString(key,""));input.setHint("zerochan".equals(source)?"https://www.zerochan.net/номер\nhttps://static.zerochan.net/….jpg":"https://…/image.jpg\nhttps://…/another.png");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout box=new LinearLayout(this);box.setPadding(32,10,32,10);box.addView(input,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(("zerochan".equals(source)?"Страницы / ссылки · ":"Прямые ссылки · ")+ArtSources.label(source)).setView(box).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{try{
            String value=input.getText().toString().trim();ArtSources.links(source,value);prefs.edit().putString(key,value).apply();
            if(home)scene.resetHomeArt();else saver.cancel();scene.dataChanged();dialog.dismiss();
        }catch(Exception e){input.setError(e.getMessage());}}));dialog.show();
    }
    void toggle(String key,boolean defaultValue){prefs.edit().putBoolean(key,!prefs.getBoolean(key,defaultValue)).apply();scene.dataChanged();touchIdle();}
    void pick(String title,String[] names,int checked,java.util.function.IntConsumer action){new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(names,checked,(d,w)->{action.accept(w);scene.dataChanged();touchIdle();d.dismiss();}).setNegativeButton("Отмена",null).show();}
    void resetPrefs(){new AlertDialog.Builder(this).setTitle("Сбросить настройки Nagi TV?").setMessage("Избранное, скрытые приложения и оформление будут сброшены. Приложения приставки и системный выбор Home не изменятся.").setPositiveButton("Сбросить",(d,w)->{prefs.edit().clear().apply();scene.resetHomeArt();favorites.clear();hidden.clear();order.clear();refreshApps();scene.showHome();}).setNegativeButton("Отмена",null).show();}
    private void initSounds(){
        sounds=new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        sounds.setOnLoadCompleteListener((pool,id,status)->{if(status==0)loadedSounds.add(id);});
        soundIds.put("move",sounds.load(this,R.raw.move,1));soundIds.put("open",sounds.load(this,R.raw.open,1));soundIds.put("back",sounds.load(this,R.raw.back,1));
    }
    void sound(String name){if(sounds==null||!prefs.getBoolean("sounds",true))return;Integer id=soundIds.get(name);if(id==null||!loadedSounds.contains(id))return;float v=prefs.getInt("volume",20)/100f;sounds.play(id,v,v,1,0,1f);}
}
