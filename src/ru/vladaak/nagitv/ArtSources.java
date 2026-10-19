package ru.vladaak.nagitv;
import java.net.URI;
import java.util.*;
/** Source policy only. Does not scrape catalogs or claim permission to redistribute art. */
public final class ArtSources {
    public static final String[] IDS={"local","manifest","wallhaven","alpha","zerochan"};
    public static final String[] NAMES={"Встроенный фон","Nagi — онлайн-подборка","Wallhaven — авто, SFW","Alpha Coders — мои арты","Zerochan — каталог / арты"};
    public static String label(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return NAMES[i];return "Неизвестный источник";}
    public static String wallhaven(){return "https://wallhaven.cc/api/v1/search?q=landscape&categories=010&purity=100&atleast=3840x2160&ratios=16x9&sorting=random";}
    public static String catalog(String id){
        if("wallhaven".equals(id))return "https://wallhaven.cc/search?q=landscape&categories=010&purity=100&atleast=3840x2160&ratios=16x9";
        if("alpha".equals(id))return "https://alphacoders.com/anime-landscape";
        if("zerochan".equals(id))return ZEROCHAN_CATALOG;
        return "https://github.com/Vla3Daak/Nagi-TV";
    }
    public static boolean wallhavenImage(String url,String category,String purity,int width,int height){
        try{return "anime".equals(category)&&"sfw".equals(purity)&&Policy.fourK(width,height)&&"w.wallhaven.cc".equalsIgnoreCase(Policy.https(url).getHost());}
        catch(Exception e){return false;}
    }
    public static final String ZEROCHAN_CATALOG="https://www.zerochan.net/Desktop+Wallpaper?s=id";
    public static String zerochanCatalog(String value){
        try{
            URI u=Policy.https(value.trim());String host=u.getHost().toLowerCase(Locale.ROOT),path=u.getPath();
            if((!host.equals("www.zerochan.net")&&!host.equals("zerochan.net"))||path==null||path.length()>300||!path.matches("/[^/]+/?")||path.matches("/[0-9]+/?")||zerochanId(value.trim())!=null)throw new Exception();
            for(char c:path.toCharArray())if(c<32||c==127)throw new Exception();
            if(Arrays.asList("/api","/login","/logout","/register","/user","/privacy","/about").contains(path.toLowerCase(Locale.ROOT)))throw new Exception();
            String raw=u.getRawPath();if(raw.endsWith("/"))raw=raw.substring(0,raw.length()-1);
            String sort="id",extra="";
            if(u.getRawQuery()!=null)for(String part:u.getRawQuery().split("&")){
                String[] kv=part.split("=",2);String k=java.net.URLDecoder.decode(kv[0],"UTF-8"),v=kv.length>1?java.net.URLDecoder.decode(kv[1],"UTF-8"):"";
                if(k.equals("s")){if(!v.equals("id")&&!v.equals("fav"))throw new Exception();sort=v;}
                else if(k.equals("p")){int n=Integer.parseInt(v);if(n<1||n>100)throw new Exception();extra+="&p="+n;}
                else if(k.equals("t")){int n=Integer.parseInt(v);if(n<0||n>2)throw new Exception();extra+="&t="+n;}
                else if(k.equals("c")){if(!v.matches("[A-Za-z]{1,20}"))throw new Exception();extra+="&c="+v;}
                else if(!k.equals("xml")&&!k.equals("json")&&!k.equals("d")&&!k.equals("l"))throw new Exception();
            }
            return "https://www.zerochan.net"+raw+"?s="+sort+extra;
        }catch(Exception e){throw new IllegalArgumentException("Нужна HTTPS-ссылка на каталог Zerochan, например Desktop+Wallpaper?s=id");}
    }
    public static String zerochanFeed(String value){return zerochanCatalog(value)+"&xml&d=landscape&l=100";}
    public static String zerochanUser(String user){
        String s=user.trim();if(!s.matches("[A-Za-z0-9][A-Za-z0-9_. -]{0,79}"))throw new IllegalArgumentException("Введите ник Zerochan, не email и не ссылку на профиль");return s;
    }
    public static String zerochanId(String url){
        try{URI u=Policy.https(url);String host=u.getHost().toLowerCase(Locale.ROOT);
            if((host.equals("www.zerochan.net")||host.equals("zerochan.net"))&&u.getQuery()==null&&u.getPath().matches("/[0-9]{1,12}/?"))return u.getPath().replace("/","");
        }catch(Exception ignored){}return null;
    }
    public static String[] links(String source,String text){
        if(!"alpha".equals(source)&&!"zerochan".equals(source))throw new IllegalArgumentException("Источник не поддерживает ручной список");
        LinkedHashSet<String> out=new LinkedHashSet<>();
        for(String value:text.split("\\s+")){
            if(value.isEmpty())continue;
            URI u=Policy.https(value);String host=u.getHost().toLowerCase(Locale.ROOT);
            String base="alpha".equals(source)?"alphacoders.com":"zerochan.net";
            if(!host.equals(base)&&!host.endsWith("."+base))throw new IllegalArgumentException("Нужна прямая ссылка с "+base);
            String path=u.getPath().toLowerCase(Locale.ROOT);
            if(!path.matches(".*\\.(jpg|jpeg|png|webp)$")&&!("zerochan".equals(source)&&zerochanId(value)!=null))throw new IllegalArgumentException("zerochan".equals(source)?"Нужна страница zerochan.net/номер или прямой JPG/PNG/WebP":"Нужен URL самого JPG/PNG/WebP, а не страницы каталога");
            out.add(value);if(out.size()>50)throw new IllegalArgumentException("Не более 50 изображений в списке");
        }
        if(out.isEmpty())throw new IllegalArgumentException("Добавьте хотя бы одну ссылку на изображение");
        return out.toArray(new String[0]);
    }
    private ArtSources(){}
}
