package ru.vladaak.nagitv;
import java.net.URI;
import java.util.regex.*;
/** Pure-Java input policies; shared with executable unit tests. */
public final class Policy {
    public static String repo(String input) {
        String s=input.trim().replaceAll("^https://github\\.com/","").replaceAll("/+$","");
        if(s.endsWith(".git"))s=s.substring(0,s.length()-4);
        if(!s.matches("[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}")||s.contains(".."))throw new IllegalArgumentException("Нужен адрес github.com/владелец/репозиторий");
        return s;
    }
    public static URI https(String s) {
        try {
            URI u=new URI(s);String h=u.getHost();
            if(!"https".equalsIgnoreCase(u.getScheme())||h==null||u.getUserInfo()!=null||u.getFragment()!=null||(u.getPort()!=-1&&u.getPort()!=443))throw new Exception();
            h=h.toLowerCase(java.util.Locale.ROOT);
            if(!h.contains(".")||!h.matches("[a-z0-9.-]+")||h.matches("[0-9.]+")||h.endsWith(".local")||h.endsWith(".localhost")||h.endsWith(".internal")||h.endsWith(".test"))throw new Exception();
            return u;
        }catch(Exception e){throw new IllegalArgumentException("Нужна публичная HTTPS-ссылка без пароля и нестандартного порта");}
    }
    public static boolean newer(String candidate,String current) {
        int[] a=version(candidate),b=version(current);if(a==null||b==null)return false;
        for(int i=0;i<3;i++){if(a[i]!=b[i])return a[i]>b[i];}return false;
    }
    private static int[] version(String v){
        Matcher m=Pattern.compile("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:\\+[A-Za-z0-9.-]+)?$").matcher(v.trim());
        if(!m.matches())return null;
        try{return new int[]{Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))};}catch(Exception e){return null;}
    }
    public static boolean releaseAsset(String url,String repo){
        try{URI u=https(url);return "github.com".equalsIgnoreCase(u.getHost())&&u.getPath().startsWith("/"+repo(repo)+"/releases/download/")&&u.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".apk")&&u.getQuery()==null;}catch(Exception e){return false;}
    }
    public static boolean fourK(int width,int height){return width>=3840&&height>=2160&&width<=8192&&height<=8192&&width>height;}
    private Policy(){}
}
