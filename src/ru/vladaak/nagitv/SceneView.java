package ru.vladaak.nagitv;
import android.animation.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import java.text.SimpleDateFormat;
import java.util.*;

/** Native, hardware-accelerated TV interface. Virtual coordinates are 1920×1080. */
final class SceneView extends View {
    private final MainActivity a;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF rect=new RectF();
    private final Typeface regular=Typeface.create("sans-serif",0),medium=Typeface.create("sans-serif-medium",0),light=Typeface.create("sans-serif-light",0);
    private final Bitmap wallpaper;
    private final ArrayList<MainActivity.AppEntry> shown=new ArrayList<>();
    private final ArrayList<S> settings=new ArrayList<>();
    private final ArrayList<Hit> hits=new ArrayList<>();
    // page: home, apps, settings, saver. zone: header, grid/sidebar, settings detail.
    private int page,zone=1,nav,selected,appPage,side,previousPage,previousZone;
    private int oldKey=-999; private float focus=1,transition=1;
    private ValueAnimator focusAnimator,pageAnimator,pictureAnimator;
    private boolean centerDown,longAction,attached;
    private final Handler h=new Handler(Looper.getMainLooper());
    private final Runnable longPress=()->{if(centerDown){longAction=true;actions();}};
    private Bitmap picture,oldPicture;private String pictureTitle="",saverMessage="Загружаем аниме-арт…";private float pictureAlpha=1;private long pictureAt;
    private final Runnable nextPicture=this::loadNext;
    private void loadNext(){if(isSaver()){if(picture==null)a.saver.first();else a.saver.next();}}
    private final String[] tabs={"Основные","Оформление","Приложения","Заставки","Пульт и звуки","Система","Обновления","О лаунчере"};
    private final String[] descriptions={"Главный экран и поведение при запуске.","Спокойный пейзаж и мягкое выделение приложений.","Любимые — ближе. Остальные — в вашем порядке.","Аниме-пейзажи и персонажи. Только онлайн, без записи на диск.","Понятное управление и тихие звуки интерфейса.","Быстрый переход к штатным настройкам Android TV.","Проверенные обновления из GitHub Releases.","Ваш атмосферный домашний экран."};
    private static final class S {String label,value;Runnable action;boolean toggle,on;S(String l,String v,Runnable r){label=l;value=v;action=r;}S(String l,boolean b,Runnable r){label=l;on=b;toggle=true;action=r;}}
    private static final class Hit {RectF box;Runnable click;Hit(RectF b,Runnable r){box=b;click=r;}}
    SceneView(MainActivity activity){super(activity);a=activity;setFocusable(true);setFocusableInTouchMode(true);requestFocus();setLayerType(View.LAYER_TYPE_HARDWARE,null);wallpaper=BitmapFactory.decodeResource(getResources(),R.drawable.atmosphere);page=a.prefs.getBoolean("start_apps",false)?1:0;dataChanged();}
    boolean isSaver(){return page==3;}boolean isSettings(){return page==2;}
    void dataChanged(){shown.clear();shown.addAll(page==0?a.favoriteApps():a.visible());if(page==0&&shown.isEmpty())shown.addAll(a.visible().subList(0,Math.min(6,a.visible().size())));if((page==0||page==1)&&selected>=shown.size())selected=Math.max(0,shown.size()-1);if(page==1)appPage=selected/12;buildSettings();invalidate();}
    void showHome(){stopSaver();page=0;zone=1;nav=0;selected=0;appPage=0;dataChanged();a.touchIdle();animatePage();announce();}
    private void showApps(){page=1;zone=1;nav=1;selected=0;appPage=0;dataChanged();animatePage();announce();}
    private void showSettings(){page=2;zone=1;nav=2;side=0;selected=0;dataChanged();animatePage();announce();}
    void back(){if(isSaver()){stopSaver();return;}if(page==2&&zone==2){zone=1;selected=0;focusChanged();return;}showHome();}
    void startSaver(){
        if(page==3)return;previousPage=page;previousZone=zone;page=3;centerDown=false;h.removeCallbacks(longPress);
        picture=null;oldPicture=null;pictureTitle="";saverMessage="Загружаем аниме-арт…";
        setKeepScreenOn(true);a.saver.first();invalidate();
    }
    void stopSaver(){
        if(page!=3)return;page=previousPage;zone=previousZone;a.saver.cancel();h.removeCallbacks(nextPicture);
        if(pictureAnimator!=null)pictureAnimator.cancel();picture=null;oldPicture=null;setKeepScreenOn(false);dataChanged();a.touchIdle();
    }
    void pictureReady(Bitmap b,String title){
        if(!isSaver())return;oldPicture=picture;picture=b;pictureTitle=title;saverMessage="";pictureAt=SystemClock.uptimeMillis();
        if(pictureAnimator!=null)pictureAnimator.cancel();
        pictureAlpha=oldPicture==null||!animations()?1:0;
        if(pictureAlpha==0){pictureAnimator=ValueAnimator.ofFloat(0,1);pictureAnimator.setDuration(1200);pictureAnimator.addUpdateListener(v->{pictureAlpha=(float)v.getAnimatedValue();if(pictureAlpha>=1)oldPicture=null;invalidate();});pictureAnimator.start();}
        h.removeCallbacks(nextPicture);h.postDelayed(nextPicture,a.prefs.getInt("saver_seconds",30)*1000L);invalidate();
    }
    void saverError(String msg){if(!isSaver())return;saverMessage=msg;h.removeCallbacks(nextPicture);h.postDelayed(nextPicture,30000);invalidate();}
    private boolean animations(){return a.prefs.getBoolean("animations",true);}
    private void setBool(String key,boolean def){a.toggle(key,def);}
    private String bool(String key,boolean def){return a.prefs.getBoolean(key,def)?"Вкл.":"Выкл.";}
    private void choices(String title,String key,int[] values,String[] labels,int def){int current=a.prefs.getInt(key,def),idx=0;for(int i=0;i<values.length;i++)if(values[i]==current)idx=i; a.pick(title,labels,idx,w->a.prefs.edit().putInt(key,values[w]).apply());}
    private void add(String l,String v,Runnable r){settings.add(new S(l,v,r));}
    private void addToggle(String l,String key,boolean def){settings.add(new S(l,a.prefs.getBoolean(key,def),()->setBool(key,def)));}
    private void buildSettings(){
        settings.clear();
        switch(side){
            case 0:
                add("Открывать по кнопке Home","Настроить",()->a.chooseHome());
                add("Начальный раздел",a.prefs.getBoolean("start_apps",false)?"Приложения":"Главная",()->a.pick("Начальный раздел",new String[]{"Главная","Приложения"},a.prefs.getBoolean("start_apps",false)?1:0,i->a.prefs.edit().putBoolean("start_apps",i==1).apply()));
                addToggle("24-часовой формат времени","clock_24",true);addToggle("Показывать дату","show_date",true);addToggle("Подсказки управления","hints",true);break;
            case 1:
                add("Фон главного экрана","Atmosphere",()->a.message("Atmosphere","В этой версии используется выбранный фон с горным озером. Онлайн-аниме-арты используются только в заставке. Это позволяет сохранить читаемый главный экран и не загружать картинки без необходимости."));
                addToggle("Плавное выделение и переходы","animations",true);addToggle("Компактные плитки","compact",false);
                add("Затемнение фона",a.prefs.getInt("dim",40)+" %",()->choices("Затемнение","dim",new int[]{20,40,60,80},new String[]{"20 %","40 %","60 %","80 %"},40));break;
            case 2:
                add("Настроить избранное",a.favoriteApps().size()+" из 6",()->{showHome();a.toast("Удерживайте OK на приложении. Все приложения доступны во вкладке «Приложения».");});
                add("Скрытые приложения",a.hidden.size()==0?"Нет":String.valueOf(a.hidden.size()),()->a.showHidden());
                addToggle("Приложения для телефона","phone_apps",false);
                settings.get(settings.size()-1).action=()->{a.toggle("phone_apps",false);a.refreshApps();};
                add("Обновить список приложений","Обновить",()->{a.refreshApps();a.toast("Обновляем список");});
                add("Изменить порядок","Удержание OK",()->a.message("Порядок приложений","На нужной плитке удерживайте OK или нажмите Menu. Выберите «Переместить левее» / «Переместить правее». Порядок избранного и всех приложений сохраняется отдельно."));break;
            case 3:
                addToggle("Включать при бездействии","saver_enabled",true);
                add("Начинать через",a.prefs.getInt("saver_minutes",5)+" мин",()->choices("Таймер бездействия","saver_minutes",new int[]{1,3,5,10,15,30},new String[]{"1 минута","3 минуты","5 минут","10 минут","15 минут","30 минут"},5));
                add("Менять арт каждые",a.prefs.getInt("saver_seconds",30)+" сек",()->choices("Интервал смены артов","saver_seconds",new int[]{15,30,60,120},new String[]{"15 секунд","30 секунд","1 минута","2 минуты"},30));
                addToggle("Показывать часы","saver_clock",true);
                add("Онлайн-подборка","Изменить ссылку",()->a.editManifest());
                add("Предпросмотр","Показать",()->startSaver());break;
            case 4:
                addToggle("Звуки навигации","sounds",true);
                add("Громкость звуков",a.prefs.getInt("volume",20)+" %",()->choices("Громкость интерфейса","volume",new int[]{0,10,20,35,50,75,100},new String[]{"Без звука","10 %","20 % — тихо","35 %","50 %","75 %","100 %"},20));
                add("Кнопки пульта","Показать",()->a.message("Управление","Стрелки — выбор.\nOK — открыть.\nУдержание OK / Menu — действия с плиткой.\nНазад — к избранному.\nHome — Nagi TV после назначения домашним приложением.\n\nВо время заставки любая навигационная кнопка возвращает в лаунчер. Кнопки питания и системные команды обрабатывает Android."));
                addToggle("Анимация фокуса","animations",true);break;
            case 5:
                add("Сеть и Wi-Fi","Открыть",()->a.openSystem(new Intent(Settings.ACTION_WIFI_SETTINGS)));
                add("Bluetooth и пульты","Открыть",()->a.openSystem(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
                add("Изображение и звук","Открыть",()->a.openSystem(new Intent(Settings.ACTION_DISPLAY_SETTINGS)));
                add("Звук","Открыть",()->a.openSystem(new Intent(Settings.ACTION_SOUND_SETTINGS)));
                add("Дата и время","Открыть",()->a.openSystem(new Intent(Settings.ACTION_DATE_SETTINGS)));
                add("Все настройки Android TV","Открыть",()->a.openSystem(new Intent(Settings.ACTION_SETTINGS)));break;
            case 6:
                add("Репозиторий GitHub",a.prefs.getString("repo",MainActivity.REPO),()->a.editRepository());
                add("Проверить обновления","GitHub Releases",()->a.updates.check());
                add("Установить загруженный APK","С подтверждением",()->a.updates.install());
                add("Удалить загруженный APK","Освободить место",()->a.updates.deleteDownload());
                add("Открыть релизы в браузере","Открыть",()->{try{a.startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/"+Policy.repo(a.prefs.getString("repo",MainActivity.REPO))+"/releases")));}catch(Exception e){a.toast("Браузер недоступен. Проверяйте обновления прямо в Nagi TV.");}});break;
            case 7:
                add("Версия Nagi TV",MainActivity.VERSION+" · versionCode 1",()->a.message("Nagi TV", "Независимый лаунчер, вдохновлённый tvOS. Не связан с Apple.\n\nПервая тестовая версия для Android TV 11. На физической приставке необходимо проверить Home, пульт и производительность."));
                add("Устройство",Build.MODEL,()->a.message("Устройство",Build.MANUFACTURER+" / "+Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+"\n"+Build.DISPLAY));
                add("Приватность","Без рекламы и аналитики",()->a.message("Приватность","Сетевые запросы: GitHub Releases и выбранная подборка заставок. Сервисам виден IP-адрес приставки. Аналитики и рекламы нет.\n\nАрты и список ссылок находятся только в RAM, дисковый кэш отключён. Загруженный APK обновления хранится отдельно до удаления. Избранное и настройки сохраняются локально."));
                add("Авторы и изображения","Подробнее",()->a.message("Авторы и изображения","Интерфейс и звуки созданы для Nagi TV. Стартовые аниме-арты сгенерированы ИИ, затем увеличены с 1376×768 до 3840×2160. Это 4K-файлы, но не исходная детализация 4K. Оригинальные персонажи, не материалы Apple.\n\nВы можете заменить подборку своими горизонтальными изображениями от 3840×2160, если имеете право их использовать."));
                add("Сбросить настройки…","С подтверждением",()->a.resetPrefs());break;
        }
        if(selected>=settings.size()&&zone==2)selected=Math.max(0,settings.size()-1);
    }
    private void text(Canvas c,String s,float x,float y,float size,int color,Typeface font){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTypeface(font);p.setTextSize(size);c.drawText(s,x,y,p);}
    private void box(Canvas c,float x,float y,float w,float h,float radius,int color){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(color);rect.set(x,y,x+w,y+h);c.drawRoundRect(rect,radius,radius,p);}
    private void rightText(Canvas c,String s,float right,float y,float size,int color,Typeface font){p.setTypeface(font);p.setTextSize(size);text(c,s,right-p.measureText(s),y,size,color,font);}
    private String fit(String s,float width,float size){p.setTypeface(medium);p.setTextSize(size);if(p.measureText(s)<=width)return s;int n=s.length();while(n>0&&p.measureText(s.substring(0,n)+"…")>width)n--;return s.substring(0,n)+"…";}
    private String time(){return new SimpleDateFormat(a.prefs.getBoolean("clock_24",true)?"HH:mm":"h:mm a",new Locale("ru")).format(new Date());}
    private String date(){return new SimpleDateFormat("EEEE, d MMMM",new Locale("ru")).format(new Date());}
    private int key(){return page*1000+(zone==0?-100-nav:page==2&&zone==1?-20-side:selected);}
    private float scaleFor(int k){if(!animations())return k==key()?1.065f:1;float amount=k==key()?focus:k==oldKey?1-focus:0;return 1+.065f*amount;}
    private void focusChanged(){oldKey=lastFocus;lastFocus=key();if(focusAnimator!=null)focusAnimator.cancel();if(!animations()){focus=1;invalidate();announce();return;}focus=0;focusAnimator=ValueAnimator.ofFloat(0,1);focusAnimator.setDuration(180);focusAnimator.setInterpolator(new DecelerateInterpolator(1.4f));focusAnimator.addUpdateListener(v->{focus=(float)v.getAnimatedValue();invalidate();});focusAnimator.start();announce();}
    private int lastFocus=0;
    private void animatePage(){if(pageAnimator!=null)pageAnimator.cancel();transition=animations()?0:1;if(!animations()){invalidate();return;}pageAnimator=ValueAnimator.ofFloat(0,1);pageAnimator.setDuration(190);pageAnimator.setInterpolator(new DecelerateInterpolator());pageAnimator.addUpdateListener(v->{transition=(float)v.getAnimatedValue();invalidate();});pageAnimator.start();}
    private void image(Canvas c,Bitmap b,RectF target,int alpha,float zoom){if(b==null)return;float bw=b.getWidth(),bh=b.getHeight(),r=Math.max(target.width()/bw,target.height()/bh)*zoom,w=bw*r,hh=bh*r;RectF to=new RectF(target.centerX()-w/2,target.centerY()-hh/2,target.centerX()+w/2,target.centerY()+hh/2);p.setShader(null);p.setColor(Color.WHITE);p.setAlpha(alpha);c.drawBitmap(b,null,to,p);p.setAlpha(255);}
    private void background(Canvas c){c.drawColor(0xff071320);image(c,wallpaper,new RectF(0,0,1920,1080),255,1);int dim=a.prefs.getInt("dim",40);int alpha=page==2?218:Math.min(210,(int)(dim*1.8));p.setShader(new LinearGradient(0,0,1920,1080,new int[]{Color.argb(alpha,4,15,27),Color.argb(Math.max(70,alpha-35),5,16,28)},null,Shader.TileMode.CLAMP));c.drawRect(0,0,1920,1080,p);p.setShader(new LinearGradient(0,500,0,1080,0x00071320,0xe0071320,Shader.TileMode.CLAMP));c.drawRect(0,0,1920,1080,p);p.setShader(null);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();c.scale(getWidth()/1920f,getHeight()/1080f);hits.clear();if(page==3){drawSaver(c);c.restore();return;}background(c);c.save();if(transition<1){c.translate(0,(1-transition)*12);c.saveLayerAlpha(0,140,1920,1020,Math.round(100+155*transition));}if(page==0)drawHome(c);else if(page==1)drawApps(c);else drawSettings(c);if(transition<1)c.restore();c.restore();drawHeader(c);drawFooter(c);c.restore();}
    private void drawHeader(Canvas c){
        box(c,108,73,42,42,12,0xffeef4ff);text(c,"N",117,106,30,0xff1c3047,medium);text(c,"Nagi TV",165,105,30,Color.WHITE,medium);
        String[] labels={"Главная","Приложения","Настройки"};float[] x={668,886,1164},w={192,254,224};
        box(c,661,60,744,70,36,0x11071320);
        for(int i=0;i<3;i++){
            boolean f=zone==0&&nav==i,active=i==page;int color=f?0xfff0f5fa:active?0x22ffffff:Color.TRANSPARENT;
            if(color!=0)box(c,x[i],66,w[i],58,29,color);text(c,labels[i],x[i]+27,105,26,f?0xff1d3045:0xffe8eff8,medium);final int n=i;hit(x[i],60,w[i],70,()->{nav=n;zone=0;activate();});
        }
        rightText(c,time(),1747,105,31,Color.WHITE,light);box(c,1770,73,42,42,21,0x20ffffff);text(c,"V",1784,101,19,0xffe9f3fc,medium);
    }
    private void drawHome(Canvas c){
        if(a.prefs.getBoolean("show_date",true))text(c,date().toUpperCase(new Locale("ru")),108,275,20,0xffd5e2ee,medium);
        int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);String line1=hour>=18||hour<5?"Вечер начинается":"Ваш день.",line2=hour>=18||hour<5?"здесь.":"Ваши истории.";
        text(c,line1,103,389,88,Color.WHITE,light);text(c,line2,103,484,88,Color.WHITE,light);text(c,"Устройтесь поудобнее.",108,554,29,0xffcedbe8,regular);
        rightText(c,"Тишина у озера",1812,618,20,0xffbdcedc,regular);text(c,"Ваши приложения",108,730,30,Color.WHITE,medium);
        if(shown.isEmpty()){text(c,"Пока нет приложений для ТВ",108,824,35,Color.WHITE,medium);text(c,"Настройки → Приложения → Приложения для телефона",108,877,25,0xffbfcede,regular);return;}
        for(int i=0;i<Math.min(6,shown.size());i++)tile(c,shown.get(i),108+i*288,770,264,148,i,page*1000+i);
    }
    private void drawApps(Canvas c){
        text(c,"Приложения",108,253,53,Color.WHITE,light);text(c,"Удерживайте OK, чтобы изменить порядок или добавить в избранное.",108,309,25,0xffc0d1e3,regular);
        int begin=appPage*12;
        if(shown.isEmpty())text(c,"Нет видимых приложений. Проверьте настройки списка.",108,438,31,0xffd3e0ef,regular);
        for(int i=begin;i<Math.min(begin+12,shown.size());i++){int local=i-begin;tile(c,shown.get(i),108+(local%6)*288,397+(local/6)*310,264,148,i,page*1000+i);}
        int pages=Math.max(1,(shown.size()+11)/12);rightText(c,(appPage+1)+" / "+pages,1812,977,24,0xffbdcee0,regular);
        if(pages>1)text(c,"Вниз после последнего ряда — следующая страница",108,977,22,0xffaebfd4,regular);
    }
    private void tile(Canvas c,MainActivity.AppEntry e,float x,float y,float w,float hh,int index,int k){
        boolean chosen=zone==1&&selected==index;
        float scale=zone==1?scaleFor(k):1;
        if(a.prefs.getBoolean("compact",false)){x+=10;w-=20;y+=5;hh-=10;}
        c.save();c.scale(scale,scale,x+w/2,y+hh/2);
        if(chosen)box(c,x-6,y-6,w+12,hh+12,24,0xffeef5ff);
        box(c,x,y,w,hh,18,0xff23384e);
        if(e.banner!=null){c.save();Path clip=new Path();clip.addRoundRect(new RectF(x,y,x+w,y+hh),18,18,Path.Direction.CW);c.clipPath(clip);image(c,e.banner,new RectF(x,y,x+w,y+hh),255,1);c.restore();}
        else{int hash=e.pkg.hashCode();p.setShader(new LinearGradient(x,y,x+w,y+hh,Color.rgb(40+(hash&31),52+((hash>>5)&31),79+((hash>>10)&31)),0xff233449,Shader.TileMode.CLAMP));rect.set(x,y,x+w,y+hh);c.drawRoundRect(rect,18,18,p);p.setShader(null);if(e.icon!=null){p.setColor(Color.WHITE);c.drawBitmap(e.icon,null,new RectF(x+w/2-43,y+hh/2-43,x+w/2+43,y+hh/2+43),p);}else text(c,e.label.substring(0,1),x+w/2-17,y+hh/2+18,52,Color.WHITE,medium);}
        c.restore();String label=fit(e.label,w+6,24);p.setTypeface(chosen?medium:regular);p.setTextSize(24);text(c,label,x+(w-p.measureText(label))/2,y+hh+44,24,chosen?Color.WHITE:0xffcad9e8,chosen?medium:regular);
        final int target=index;hit(x-8,y-8,w+16,hh+65,()->{zone=1;selected=target;activate();});
    }
    private void drawSettings(Canvas c){
        text(c,"Настройки",108,258,52,Color.WHITE,light);text(c,"Сделайте экран своим.",108,309,24,0xffbfd0e1,regular);
        for(int i=0;i<tabs.length;i++){
            float y=357+i*69;boolean active=i==side,focused=zone==1&&active;
            if(active){box(c,108,y,418,59,14,focused?0xffeef5fc:0x22ffffff);if(!focused)box(c,108,y+17,4,25,2,0xffb6d6f6);}
            text(c,String.format(Locale.ROOT,"%02d",i+1),129,y+38,19,focused?0xff617790:0xff94abc5,regular);text(c,tabs[i],179,y+39,26,focused?0xff18334b:active?Color.WHITE:0xffc0d0e1,regular);final int n=i;hit(108,y,418,59,()->{side=n;zone=2;selected=0;buildSettings();animatePage();});
        }
        text(c,tabs[side],650,252,45,Color.WHITE,light);text(c,descriptions[side],650,302,24,0xffbed0e2,regular);
        float y=352;
        if(side==1){
            c.save();Path clip=new Path();clip.addRoundRect(new RectF(650,347,1108,591),20,20,Path.Direction.CW);c.clipPath(clip);image(c,wallpaper,new RectF(650,347,1108,591),255,1);c.restore();text(c,"Atmosphere",1146,413,37,Color.WHITE,light);text(c,"Пейзаж и мягкий свет.",1146,460,25,0xffbbcee2,regular);text(c,"Пространство для ваших историй.",1146,501,24,0xffbbcee2,regular);y=622;
        }
        if(side==3){
            c.save();Path clip=new Path();clip.addRoundRect(new RectF(650,347,1812,567),20,20,Path.Direction.CW);c.clipPath(clip);image(c,wallpaper,new RectF(650,347,1812,567),210,1);p.setColor(0x70051b30);c.drawRect(650,347,1812,567,p);c.restore();text(c,"АНИМЕ-ПЕЙЗАЖИ И ПЕРСОНАЖИ",678,489,20,0xffe0eafa,medium);text(c,"Онлайн · 4K-файлы · только RAM",678,535,31,Color.WHITE,light);
            for(int i=0;i<settings.size();i++){float x=650+(i%2)*587,yy=595+(i/2)*103;settingCard(c,settings.get(i),i,x,yy,575,91);}
            text(c,"Без интернета новые арты не загрузятся. Изображения на диск не записываются.",656,947,22,0xffb3c7dc,regular);return;
        }
        for(int i=0;i<settings.size();i++){settingRow(c,settings.get(i),i,650,y+i*86,1162,76);}
        float noteY=y+settings.size()*86+25;
        String note=side==0?"Штатный лаунчер не удаляется. Назначение Home зависит от прошивки.":side==2?"Действия с плиткой: удержание OK или кнопка Menu.":side==4?"Home работает после назначения Nagi TV домашним приложением.":side==5?"Открываются системные разделы. При недоступности — общие настройки.":side==6?a.updateStatus:side==7?"Вдохновлён tvOS. Не является продуктом Apple.":"Анимацию можно отключить для минимальной нагрузки.";
        text(c,fit(note,1150,22),656,Math.min(978,noteY),22,0xffb4c8dd,regular);
    }
    private void settingRow(Canvas c,S s,int i,float x,float y,float w,float hh){boolean f=zone==2&&selected==i;box(c,x,y,w,hh,14,f?0xffeff5fb:0x10ffffff);text(c,s.label,x+26,y+48,26,f?0xff19334b:0xfff0f6fc,regular);if(s.toggle)drawToggle(c,x+w-133,y+23,s.on,f);else{String v=fit(s.value,480,24);rightText(c,v,x+w-54,y+47,24,f?0xff506c85:0xffbcd0e6,regular);text(c,"›",x+w-32,y+47,31,f?0xff506c85:0xffbcd0e6,regular);}final int n=i;hit(x,y,w,hh,()->{zone=2;selected=n;activate();});}
    private void settingCard(Canvas c,S s,int i,float x,float y,float w,float hh){boolean f=zone==2&&selected==i;box(c,x,y,w,hh,14,f?0xffeff5fb:0x10ffffff);text(c,s.label,x+22,y+29,20,f?0xff536e87:0xffaec5dd,regular);text(c,fit(s.toggle?(s.on?"Включено":"Выключено"):s.value,w-125,25),x+22,y+66,25,f?0xff18334b:Color.WHITE,regular);if(s.toggle)drawToggle(c,x+w-77,y+31,s.on,f,true);else text(c,"›",x+w-32,y+59,30,f?0xff506c85:0xffbcd0e6,regular);final int n=i;hit(x,y,w,hh,()->{zone=2;selected=n;activate();});}
    private void drawToggle(Canvas c,float x,float y,boolean on,boolean focused){drawToggle(c,x,y,on,focused,false);}
    private void drawToggle(Canvas c,float x,float y,boolean on,boolean focused,boolean shortOne){if(!shortOne)text(c,on?"Вкл.":"Выкл.",x,y+23,22,focused?0xff506c85:0xffbcd0e6,regular);float bx=x+(shortOne?0:61);box(c,bx,y,53,30,15,on?0xff95c8f5:focused?0xffafbfce:0xff3b4e61);box(c,bx+(on?26:4),y+4,22,22,11,on?0xff15344f:0xffc3d2e2);}
    private void drawFooter(Canvas c){
        if(!a.prefs.getBoolean("hints",true))return;
        String hint=page==2?"← Назад        OK Выбрать": "Стрелки — выбор     OK — открыть     Удержание OK / Menu — действия";
        text(c,hint,108,1044,21,0xffb4c8de,regular);rightText(c,page==2?"NAGI TV · "+MainActivity.VERSION:"ATMOSPHERE",1812,1044,18,0xffa7bed6,regular);
    }
    private void drawSaver(Canvas c){
        c.drawColor(0xff06111e);float duration=a.prefs.getInt("saver_seconds",30)*1000f;float elapsed=Math.min(1,(SystemClock.uptimeMillis()-pictureAt)/duration);float zoom=animations()?1+elapsed*.025f:1;
        if(oldPicture!=null)image(c,oldPicture,new RectF(0,0,1920,1080),255,1.025f);
        if(picture!=null)image(c,picture,new RectF(0,0,1920,1080),Math.round(255*pictureAlpha),zoom);
        p.setShader(new LinearGradient(0,700,0,1080,0x00000000,0x99040f1b,Shader.TileMode.CLAMP));c.drawRect(0,0,1920,1080,p);p.setShader(null);
        if(a.prefs.getBoolean("saver_clock",true)){// The clock drifts subtly between images to avoid a permanently fixed bright block.
            boolean right=(pictureAt/1000)%2==0;
            if(right){rightText(c,time(),1812,909,100,Color.WHITE,light);rightText(c,pictureTitle,1812,964,26,0xffdfebf7,regular);}else{text(c,time(),108,909,100,Color.WHITE,light);text(c,pictureTitle,112,964,26,0xffdfebf7,regular);}
        }
        if(picture==null){text(c,"Nagi TV",108,270,55,Color.WHITE,light);text(c,fit(saverMessage,1650,29),108,335,29,0xffc4d7ec,regular);text(c,"Нажмите кнопку пульта, чтобы вернуться",108,390,25,0xffa9c0d9,regular);}
        if(picture!=null&&animations()&&attached)postInvalidateDelayed(33);
    }
    private void hit(float x,float y,float w,float hh,Runnable click){hits.add(new Hit(new RectF(x,y,x+w,y+hh),click));}
    private void announce(){String s=zone==0?new String[]{"Главная","Приложения","Настройки"}[nav]:page==2?(zone==1?tabs[side]:settings.isEmpty()?tabs[side]:settings.get(selected).label):shown.isEmpty()?"Нет приложений":shown.get(Math.min(selected,shown.size()-1)).label;setContentDescription(s);announceForAccessibility(s);}
    private void move(int key){
        int before=key();
        if(zone==0){if(key==21)nav=Math.max(0,nav-1);if(key==22)nav=Math.min(2,nav+1);if(key==20){zone=1;if(page==2)side=Math.max(0,side);}}
        else if(page==2){
            if(zone==1){if(key==19){if(side==0){zone=0;nav=2;}else side--;}if(key==20)side=Math.min(tabs.length-1,side+1);if(key==22){zone=2;selected=0;}buildSettings();}
            else if(side==3){if(key==21){if(selected%2==0)zone=1;else selected--;}if(key==22&&selected%2==0)selected++;if(key==19){if(selected<2){zone=0;nav=2;}else selected-=2;}if(key==20)selected=Math.min(settings.size()-1,selected+2);}
            else{if(key==21)zone=1;if(key==19){if(selected==0){zone=0;nav=2;}else selected--;}if(key==20)selected=Math.min(settings.size()-1,selected+1);}
        }else if(!shown.isEmpty()){
            if(key==21)selected=Math.max(page==1?appPage*12:0,selected-1);
            if(key==22)selected=Math.min(shown.size()-1,Math.min(page==0?5:appPage*12+11,selected+1));
            if(key==19){if(page==0||selected-appPage*12<6){if(page==1&&appPage>0){appPage--;selected=Math.min(shown.size()-1,appPage*12+6+selected%6);animatePage();}else{zone=0;nav=page;}}else selected-=6;}
            if(key==20&&page==1){if(selected%12<6)selected=Math.min(shown.size()-1,selected+6);else if((appPage+1)*12<shown.size()){appPage++;selected=Math.min(shown.size()-1,appPage*12+selected%6);animatePage();}}
        }else if(key==19){zone=0;nav=page;}
        if(key()!=before){a.sound("move");focusChanged();}
    }
    private void activate(){a.sound("open");if(zone==0){if(nav==0)showHome();else if(nav==1)showApps();else showSettings();return;}if(page==2){if(zone==1){zone=2;selected=0;focusChanged();}else if(!settings.isEmpty()){settings.get(selected).action.run();buildSettings();invalidate();}}else if(!shown.isEmpty()&&selected<shown.size())a.launch(shown.get(selected));}
    private void actions(){if((page==0||page==1)&&zone==1&&!shown.isEmpty()&&selected<shown.size())a.actions(shown.get(selected),page==0&&!a.favoriteApps().isEmpty());else showSettings();}
    @Override public boolean onKeyDown(int key,KeyEvent e){if(key>=19&&key<=22){move(key);return true;}if(key==23||key==66||key==160||key==96){if(e.getRepeatCount()==0){centerDown=true;longAction=false;if(page!=2&&zone==1)h.postDelayed(longPress,650);}return true;}if(key==82){if(e.getRepeatCount()==0)actions();return true;}return super.onKeyDown(key,e);}
    @Override public boolean onKeyUp(int key,KeyEvent e){if(key==23||key==66||key==160||key==96){h.removeCallbacks(longPress);boolean run=centerDown&&!longAction&&!e.isCanceled();centerDown=false;if(run)activate();return true;}return super.onKeyUp(key,e);}
    @Override public void onWindowFocusChanged(boolean has){super.onWindowFocusChanged(has);if(!has){h.removeCallbacks(longPress);centerDown=false;}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;}
    @Override protected void onDetachedFromWindow(){attached=false;h.removeCallbacksAndMessages(null);if(focusAnimator!=null)focusAnimator.cancel();if(pageAnimator!=null)pageAnimator.cancel();if(pictureAnimator!=null)pictureAnimator.cancel();super.onDetachedFromWindow();}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onTouchEvent(MotionEvent e){if(isSaver()){if(e.getAction()==MotionEvent.ACTION_UP){stopSaver();performClick();}return true;}a.touchIdle();if(e.getAction()==MotionEvent.ACTION_UP){float x=e.getX()*1920/getWidth(),y=e.getY()*1080/getHeight();for(Hit hit:hits)if(hit.box.contains(x,y)){hit.click.run();performClick();invalidate();break;}}return true;}
}
