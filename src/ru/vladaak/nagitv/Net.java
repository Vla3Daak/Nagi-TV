package ru.vladaak.nagitv;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class Net {
    public static HttpURLConnection open(String address)throws Exception{
        String url=address;
        for(int i=0;i<6;i++){
            Policy.https(url);
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(12000);c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent","NagiTV/0.1.0");c.setRequestProperty("Accept","application/vnd.github+json, application/json, image/*, application/octet-stream");
            int code=c.getResponseCode();
            if(code==301||code==302||code==303||code==307||code==308){String loc=c.getHeaderField("Location");c.disconnect();if(loc==null)throw new IOException("Пустое перенаправление");url=new URL(new URL(url),loc).toString();continue;}
            if(code!=200){c.disconnect();throw new IOException("HTTP "+code+(code==404?" — файл или опубликованный релиз не найден":""));}
            return c;
        }throw new IOException("Слишком много перенаправлений");
    }
    public static byte[] bytes(String url,int max)throws Exception{
        HttpURLConnection c=open(url);
        try {
            if(c.getContentLengthLong()>max)throw new IOException("Файл слишком большой");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                copy(in,out,max);return out.toByteArray();
            }
        }finally{c.disconnect();}
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
