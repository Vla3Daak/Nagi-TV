package ru.vladaak.nagitv;
import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;
/** Parses the official RSS feed; preview URLs are never used as originals. */
public final class ZerochanCatalog {
    private static final String MEDIA="http://search.yahoo.com/mrss/";
    public static final class Entry {
        public final String id,title;public final int width,height;
        Entry(String id,String title,int width,int height){this.id=id;this.title=title;this.width=width;this.height=height;}
    }
    public static boolean safeTags(String tags){
        String s=tags.toLowerCase(Locale.ROOT);
        for(String word:new String[]{"nsfw","r-18","r18","nude","nudity","naked","topless","bottomless","nipples","breast","cleavage","ecchi","hentai","bikini","swimsuit","underwear","panties","lingerie","pantyshot","upskirt","skirt lift","see-through","seethrough","gore"})if(s.contains(word))return false;
        return true;
    }
    private static String text(Element item,String name){NodeList n=item.getElementsByTagName(name);return n.getLength()==0?"":n.item(0).getTextContent().trim();}
    public static List<Entry> parse(String xml)throws Exception{
        if(xml.length()>2*1024*1024)throw new IOException("Слишком большой RSS Zerochan");
        String upper=xml.toUpperCase(Locale.ROOT);
        if(upper.contains("<!DOCTYPE")||upper.contains("<!ENTITY"))throw new IOException("DTD/ENTITY в RSS запрещены");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setValidating(false);f.setExpandEntityReferences(false);
        DocumentBuilder b=f.newDocumentBuilder();b.setEntityResolver((publicId,systemId)->{throw new SAXException("Внешние XML-ресурсы запрещены");});
        b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler(){public void error(SAXParseException e)throws SAXException{throw e;}public void fatalError(SAXParseException e)throws SAXException{throw e;}});
        Document doc=b.parse(new InputSource(new StringReader(xml)));
        if(!"rss".equals(doc.getDocumentElement().getNodeName()))throw new IOException("Ожидался RSS-каталог Zerochan");
        NodeList items=doc.getElementsByTagName("item");LinkedHashMap<String,Entry> accepted=new LinkedHashMap<>();
        for(int i=0;i<Math.min(items.getLength(),100);i++){
            Element item=(Element)items.item(i);String link=text(item,"link");
            if(link.startsWith("http://www.zerochan.net/")||link.startsWith("http://zerochan.net/"))link="https:"+link.substring(5);
            String id=ArtSources.zerochanId(link);if(id==null)continue;
            NodeList contents=item.getElementsByTagNameNS(MEDIA,"content");if(contents.getLength()==0)continue;
            Element media=(Element)contents.item(0);int w,h;
            try{w=Integer.parseInt(media.getAttribute("width"));h=Integer.parseInt(media.getAttribute("height"));}catch(NumberFormatException e){continue;}
            if(!Policy.fourK(w,h))continue;
            NodeList keywords=item.getElementsByTagNameNS(MEDIA,"keywords");
            if(keywords.getLength()==0||!safeTags(keywords.item(0).getTextContent()))continue;
            String title=text(item,"title");if(title.length()>100)title=title.substring(0,100);
            accepted.put(id,new Entry(id,title,w,h));if(accepted.size()>=50)break;
        }
        return new ArrayList<>(accepted.values());
    }
    private ZerochanCatalog(){}
}
