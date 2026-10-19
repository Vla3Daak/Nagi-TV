import ru.vladaak.nagitv.Policy;
public class PolicyTest{
 static int n; static void ok(boolean b){n++;if(!b)throw new AssertionError("test "+n);}
 static void rejects(Runnable r){boolean bad=false;try{r.run();}catch(IllegalArgumentException e){bad=true;}ok(bad);}
 public static void main(String[] a){
 ok(Policy.repo("https://github.com/Vla3Daak/Nagi-TV/").equals("Vla3Daak/Nagi-TV"));ok(Policy.repo("Vla3Daak/Nagi-TV.git").equals("Vla3Daak/Nagi-TV"));
 for(String s:new String[]{"https://evil.com/a/b","a/b/c","owner/repo?token=x","owner/../repo","owner","/owner/repo","owner/repo#x"})rejects(()->Policy.repo(s));
 ok(Policy.https("https://raw.githubusercontent.com/Vla3Daak/Nagi-TV/main/screensavers.json")!=null);
 for(String s:new String[]{"http://example.com/a","file:///etc/passwd","https://user:pass@example.com/a","https://localhost/a","https://127.0.0.1/a","https://10.0.0.1/a","https://example.com:123/a","https://example.com/a#fragment","https://server.local/a"})rejects(()->Policy.https(s));
 ok(Policy.newer("v0.1.1","0.1.0"));ok(Policy.newer("v1.0.0","0.9.9"));ok(Policy.newer("0.10.0","0.2.9"));ok(!Policy.newer("0.1.0","0.1.0"));ok(!Policy.newer("0.0.9","0.1.0"));ok(!Policy.newer("v0.2.0-beta","0.1.0"));ok(!Policy.newer("garbage","0.1.0"));ok(!Policy.newer("999999999999.0.0","0.1.0"));
 ok(Policy.releaseAsset("https://github.com/Vla3Daak/Nagi-TV/releases/download/v0.2.0/Nagi-TV-v0.2.0.apk","Vla3Daak/Nagi-TV"));ok(!Policy.releaseAsset("https://github.com/another/repo/releases/download/v0.2.0/Nagi-TV.apk","Vla3Daak/Nagi-TV"));ok(!Policy.releaseAsset("https://example.com/Vla3Daak/Nagi-TV/releases/download/v0.2.0/a.apk","Vla3Daak/Nagi-TV"));ok(!Policy.releaseAsset("https://github.com/Vla3Daak/Nagi-TV/blob/main/a.apk","Vla3Daak/Nagi-TV"));
 ok(Policy.fourK(3840,2160));ok(Policy.fourK(4096,2160));ok(!Policy.fourK(1920,1080));ok(!Policy.fourK(2160,3840));ok(!Policy.fourK(16384,8192));ok(!Policy.fourK(3840,0));
 System.out.println("PASS: "+n+" policy assertions");
 }
}
