import ru.vladaak.nagitv.*;
import java.util.*;
import java.nio.file.*;
public class ZerochanCatalogTest {
    static int n;static void ok(boolean b){n++;if(!b)throw new AssertionError("catalog test "+n);}
    interface Action{void run()throws Exception;}
    static void rejects(Action r)throws Exception{boolean bad=false;try{r.run();}catch(Exception e){bad=true;}ok(bad);}
    static String item(String id,int w,int h,String tags){return "<item><title>Test &amp; Art</title><link>http://www.zerochan.net/"+id+"</link><media:content url='https://example.com/preview.jpg' width='"+w+"' height='"+h+"'/><media:keywords>"+tags+"</media:keywords></item>";}
    static String feed(String items){return "<rss xmlns:media='http://search.yahoo.com/mrss/'><channel>"+items+"</channel></rss>";}
    public static void main(String[] args)throws Exception{
        ok(ArtSources.zerochanCatalog("https://www.zerochan.net/Desktop+Wallpaper?s=id").equals(ArtSources.ZEROCHAN_CATALOG));
        ok(ArtSources.zerochanFeed(ArtSources.ZEROCHAN_CATALOG).equals(ArtSources.ZEROCHAN_CATALOG+"&xml&d=landscape&l=100"));
        ok(ArtSources.zerochanCatalog("https://zerochan.net/Scenery?s=fav&p=2&t=1").equals("https://www.zerochan.net/Scenery?s=fav&p=2&t=1"));
        ok(ArtSources.zerochanCatalog("https://www.zerochan.net/Scenery?xml&l=24&d=landscape").equals("https://www.zerochan.net/Scenery?s=id"));
        for(String s:new String[]{"http://www.zerochan.net/Scenery","https://zerochan.net.evil.example/Scenery","https://static.zerochan.net/Scenery","https://www.zerochan.net/1234567","https://www.zerochan.net/1234567?s=id","https://www.zerochan.net/user/TestUser","https://www.zerochan.net/login","https://www.zerochan.net/Scenery?s=bad","https://www.zerochan.net/Scenery?p=0","https://www.zerochan.net/Scenery?p=101","https://www.zerochan.net/Scenery?token=secret","https://www.zerochan.net/Scenery#x","https://www.zerochan.net/Scenery%0aHeader"})rejects(()->ArtSources.zerochanCatalog(s));
        List<ZerochanCatalog.Entry> list=ZerochanCatalog.parse(feed(item("123",3840,2160,"Scenery, Sky")));
        ok(list.size()==1);ok(list.get(0).id.equals("123"));ok(list.get(0).title.equals("Test & Art"));
        ok(ZerochanCatalog.parse(feed(item("123",1920,1080,"Scenery"))).isEmpty());
        ok(ZerochanCatalog.parse(feed(item("123",2160,3840,"Scenery"))).isEmpty());
        ok(ZerochanCatalog.parse(feed(item("123",16384,8192,"Scenery"))).isEmpty());
        ok(ZerochanCatalog.parse(feed(item("123",3840,2160,"Scenery, Nude"))).isEmpty());
        ok(ZerochanCatalog.parse(feed(item("123",3840,2160,"Scenery")+item("123",3840,2160,"Scenery"))).size()==1);
        ok(ZerochanCatalog.parse(feed(item("123",3840,2160,"Scenery").replace("http://www.zerochan.net/123","https://evil.example/123"))).isEmpty());
        ok(ZerochanCatalog.parse(feed(item("123",3840,2160,"Scenery").replace("<media:keywords>Scenery</media:keywords>",""))).isEmpty());
        rejects(()->ZerochanCatalog.parse("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>"+feed("")));
        rejects(()->ZerochanCatalog.parse("<html/>"));rejects(()->ZerochanCatalog.parse("<rss>"));
        StringBuilder many=new StringBuilder();for(int i=1;i<=70;i++)many.append(item(""+i,3840,2160,"Landscape"));ok(ZerochanCatalog.parse(feed(many.toString())).size()==50);
        for(String tag:new String[]{"NSFW","Ecchi","Bikini","Skirt Lift","Underwear","Gore"})ok(!ZerochanCatalog.safeTags(tag));
        ok(ZerochanCatalog.safeTags("Sky, Clouds, Official Art, Male, Landscape"));
        if(args.length>0){List<ZerochanCatalog.Entry> live=ZerochanCatalog.parse(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8"));ok(!live.isEmpty());System.out.println("Live RSS: "+live.size()+" eligible entries; originals resolved separately");}
        System.out.println("PASS: "+n+" catalog assertions");
    }
}
