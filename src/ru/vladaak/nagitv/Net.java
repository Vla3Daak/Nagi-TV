package ru.vladaak.nagitv;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class Net {
    public static HttpURLConnection open(String address)throws Exception{return open(address,null);}
    private static HttpURLConnection open(String address,String zerochanUser)throws Exception{
        String url=address;
        for(int i=0;i<6;i++){
            Policy.https(url);
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(12000);c.setReadTimeout(15000);
            String agent="NagiTV/0.2.1";
            if(zerochanUser!=null){
                String host=Policy.https(url).getHost();
                if(!"www.zerochan.net".equalsIgnoreCase(host)&&!"zerochan.net".equalsIgnoreCase(host))throw new IOException("API Zerochan перенаправил на другой сайт");
                agent="Nagi TV - "+ArtSources.zerochanUser(zerochanUser);
            }
            c.setRequestProperty("User-Agent",agent);c.setRequestProperty("Accept","application/vnd.github+json, application/json, image/*, application/octet-stream");
            int code=c.getResponseCode();
            if(code==301||code==302||code==303||code==307||code==308){String loc=c.getHeaderField("Location");c.disconnect();if(loc==null)throw new IOException("Пустое перенаправление");url=new URL(new URL(url),loc).toString();continue;}
            if(code!=200){c.disconnect();throw new IOException("HTTP "+code+(code==404?" — файл или опубликованный релиз не найден":""));}
            return c;
        }throw new IOException("Слишком много перенаправлений");
    }
    public static byte[] bytes(String url,int max)throws Exception{return bytes(url,max,null);}
    private static byte[] bytes(String url,int max,String zerochanUser)throws Exception{
        HttpURLConnection c=open(url,zerochanUser);
        try {
            if(c.getContentLengthLong()>max)throw new IOException("Файл слишком большой");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                copy(in,out,max);return out.toByteArray();
            }
        }finally{c.disconnect();}
    }
    private static long zerochanAt;
    private static synchronized void zerochanWait()throws InterruptedException{
        long now=System.nanoTime()/1000000L,wait=zerochanAt-now;
        if(wait>0)Thread.sleep(wait);zerochanAt=System.nanoTime()/1000000L+1250;
    }
    public static String zerochan(String id,String user)throws Exception{
        if(!id.matches("[0-9]{1,12}"))throw new IOException("Неверный ID Zerochan");
        ArtSources.zerochanUser(user);zerochanWait();
        return new String(bytes("https://www.zerochan.net/"+id+"?json",512*1024,user),StandardCharsets.UTF_8);
    }
    public static String zerochanFeed(String catalog,String user)throws Exception{
        ArtSources.zerochanUser(user);zerochanWait();
        return new String(bytes(ArtSources.zerochanFeed(catalog),2*1024*1024,user),StandardCharsets.UTF_8);
    }
    public static String json(String url)throws Exception{return new String(bytes(url,512*1024),StandardCharsets.UTF_8);}
    public static void file(String url,File dest,int max)throws Exception{
        HttpURLConnection c=open(url);
        try{
            if(c.getContentLengthLong()>max)throw new IOException("APK превышает допустимый размер");
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(dest)){copy(in,out,max);out.getFD().sync();}
        }catch(Exception e){dest.delete();throw e;}finally{c.disconnect();}
    }
    private static void copy(InputStream in,OutputStream out,int max)throws Exception{
        byte[] b=new byte[32768];int n,total=0;
        while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();total+=n;if(total>max)throw new IOException("Превышен лимит размера");out.write(b,0,n);}
        if(total==0)throw new IOException("Пустой файл");
    }
    private Net(){}
}
