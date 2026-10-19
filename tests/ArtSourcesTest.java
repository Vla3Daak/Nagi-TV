import ru.vladaak.nagitv.*;
public class ArtSourcesTest {
    static int n;static void ok(boolean b){n++;if(!b)throw new AssertionError("art test "+n);}
    static void rejects(Runnable r){boolean rejected=false;try{r.run();}catch(IllegalArgumentException e){rejected=true;}ok(rejected);}
    public static void main(String[] args){
        ok(ArtSources.IDS.length==5);ok(ArtSources.NAMES.length==5);
        for(String s:ArtSources.IDS)ok(!ArtSources.label(s).equals("Неизвестный источник"));
        ok(ArtSources.wallhaven().contains("purity=100"));ok(ArtSources.wallhaven().contains("categories=010"));ok(ArtSources.wallhaven().contains("atleast=3840x2160"));ok(ArtSources.wallhaven().contains("ratios=16x9"));
        ok(ArtSources.links("alpha","https://images.alphacoders.com/123/123456.jpg").length==1);
        ok(ArtSources.links("zerochan","https://static.zerochan.net/Example.full.1234567.png").length==1);
        ok(ArtSources.links("zerochan","https://s3.zerochan.net/full/12/34/123456.webp").length==1);
        ok(ArtSources.links("alpha","https://images.alphacoders.com/1/2.jpg\nhttps://images.alphacoders.com/1/2.jpg").length==1);
        ok(ArtSources.links("alpha","https://images.alphacoders.com/1/2.jpg\nhttps://images.alphacoders.com/1/3.PNG").length==2);
        rejects(()->ArtSources.links("alpha",""));rejects(()->ArtSources.links("alpha","https://wall.alphacoders.com/big.php?i=1227560"));
        ok(ArtSources.links("zerochan","https://www.zerochan.net/123456").length==1);
        rejects(()->ArtSources.links("alpha","https://alphacoders.com.evil.example/a.jpg"));
        rejects(()->ArtSources.links("zerochan","https://evilzerochan.net/a.jpg"));
        rejects(()->ArtSources.links("alpha","https://images.zerochan.net/a.jpg"));
        rejects(()->ArtSources.links("zerochan","https://images.alphacoders.com/a.jpg"));
        rejects(()->ArtSources.links("alpha","http://images.alphacoders.com/a.jpg"));
        rejects(()->ArtSources.links("alpha","file:///data/a.jpg"));
        rejects(()->ArtSources.links("zerochan","https://static.zerochan.net/a.avif"));
        rejects(()->ArtSources.links("zerochan","https://static.zerochan.net/a.gif"));
        rejects(()->ArtSources.links("wallhaven","https://w.wallhaven.cc/a.jpg"));
        StringBuilder many=new StringBuilder();for(int i=0;i<50;i++)many.append("https://images.alphacoders.com/1/").append(i).append(".jpg\n");
        ok(ArtSources.links("alpha",many.toString()).length==50);many.append("https://images.alphacoders.com/1/50.jpg");rejects(()->ArtSources.links("alpha",many.toString()));
        String w="https://w.wallhaven.cc/full/12/wallhaven-123456.jpg";
        ok(ArtSources.wallhavenImage(w,"anime","sfw",3840,2160));
        ok(!ArtSources.wallhavenImage(w,"anime","nsfw",3840,2160));ok(!ArtSources.wallhavenImage(w,"anime","sketchy",3840,2160));
        ok(!ArtSources.wallhavenImage(w,"general","sfw",3840,2160));ok(!ArtSources.wallhavenImage(w,"anime","sfw",1920,1080));
        ok(!ArtSources.wallhavenImage(w,"anime","sfw",2160,3840));ok(!ArtSources.wallhavenImage(w,"anime","sfw",16384,8192));
        ok(!ArtSources.wallhavenImage("https://w.wallhaven.cc.evil.example/a.jpg","anime","sfw",3840,2160));
        ok(!ArtSources.wallhavenImage("http://w.wallhaven.cc/a.jpg","anime","sfw",3840,2160));
        ok(Policy.newer("0.2.0","0.1.0"));
        ok("TestUser_123".equals(ArtSources.zerochanUser("  TestUser_123  ")));
        rejects(()->ArtSources.zerochanUser(""));rejects(()->ArtSources.zerochanUser("name@example.com"));
        rejects(()->ArtSources.zerochanUser("Name\r\nHeader"));
        ok("4424191".equals(ArtSources.zerochanId("https://www.zerochan.net/4424191")));
        ok("4424191".equals(ArtSources.zerochanId("https://zerochan.net/4424191/")));
        ok(ArtSources.zerochanId("https://www.zerochan.net/user/TestUser")==null);
        ok(ArtSources.zerochanId("https://www.zerochan.net/123?json")==null);
        ok(ArtSources.zerochanId("https://zerochan.net.evil.example/123")==null);
        ok(ArtSources.zerochanId("https://static.zerochan.net/123")==null);
        System.out.println("PASS: "+n+" art-source assertions");
    }
}
