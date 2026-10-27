package com.scriptura.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Obras pictóricas clásicas con reproducciones etiquetadas PD en Wikimedia Commons.
 * Catálogo editorial inicial, no reconstrucción histórica de la escena bíblica.
 * No incluye imágenes del EPUB protegido ni las publica en el repositorio.
 */
public final class BiblicalArt {
    private BiblicalArt(){}
    public static final class Painting {
        public final String id,title,author,date,book,reference,description,file;
        Painting(String id,String title,String author,String date,String book,String reference,
                 String description,String file){
            this.id=id;this.title=title;this.author=author;this.date=date;
            this.book=book;this.reference=reference;this.description=description;this.file=file;
        }
    }
    private static final List<Painting> CATALOG=buildCatalog();
    private static List<Painting> buildCatalog(){
        ArrayList<Painting> catalog=new ArrayList<>(Arrays.asList(
        new Painting("creation","La creación de Adán","Miguel Ángel","c. 1511",
            "Gn","Gn 1:26-27","Fresco de la Capilla Sixtina. Lectura artística del relato de la creación.",
            "Creation of Adam Michelangelo.jpg"),
        new Painting("annunciation","La Anunciación","Fra Angélico","c. 1440",
            "Lc","Lc 1:26-38","El anuncio del ángel Gabriel a María; representación pictórica, no documento histórico.",
            "Fra Angelico - Annunciation.jpg"),
        new Painting("matthew","La vocación de san Mateo","Caravaggio","1599-1600",
            "Mt","Mt 9:9-13","La llamada de Mateo representada mediante luz y gestos característicos del Barroco.",
            "The Calling of Saint Matthew by Carvaggio.jpg"),
        new Painting("transfiguration","La Transfiguración","Rafael","1516-1520",
            "Mt","Mt 17:1-9","La transfiguración de Jesús y su interpretación renacentista.",
            "Transfiguration Raphael.jpg"),
        new Painting("last-supper","La última cena","Leonardo da Vinci","1495-1498",
            "Lc","Lc 22:14-20","Interpretación artística de la cena de Jesús con sus discípulos.",
            "Last Supper by Leonardo da Vinci.jpg"),
        new Painting("prodigal","El regreso del hijo pródigo","Rembrandt","c. 1668-1669",
            "Lc","Lc 15:11-32","La parábola de la misericordia y el perdón.",
            "Rembrandt - The Return of the Prodigal Son - WGA19133.jpg")
    ));
        catalog.addAll(ArtCatalog.additional());
        return Collections.unmodifiableList(catalog);
    }
    public static List<Painting> all(){return CATALOG;}
    public static List<Painting> byBook(String book){
        List<Painting> found=new ArrayList<>();
        for(Painting p:CATALOG)if(p.book.equals(book))found.add(p);
        return found;
    }
    public static List<Painting> forPassage(String book,int chapter){
        List<Painting> found=new ArrayList<>();
        for(Painting p:byBook(book)){
            String token=p.reference.substring(p.reference.indexOf(' ')+1);
            try{
                int c=Integer.parseInt(token.substring(0,token.indexOf(':')));
                if(c==chapter)found.add(p);
            }catch(Exception ignored){}
        }
        return found;
    }
    private static String escape(String name){
        try{return URLEncoder.encode(name,"UTF-8").replace("+","%20");}
        catch(Exception error){return name.replace(" ","%20");}
    }

    public static String commonsPage(Painting item){
        // Mostrar la búsqueda de la obra y su autor; no inventar nombres de archivo.
        return "https://commons.wikimedia.org/w/index.php?search="+
            escape(item.file.replace(".jpg",""))+"&title=Special:MediaSearch&type=image";
    }
    private static String searchEndpoint(Painting item){
        return "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search"+
            "&gsrnamespace=6&gsrlimit=10&gsrsearch="+escape(item.file.replace(".jpg",""))+
            "&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=640"+
            "&iiextmetadatafilter=LicenseShortName%7CCopyrighted";
    }
    private static boolean licensed(org.json.JSONObject metadata){
        if(metadata==null)return false;
        org.json.JSONObject raw=metadata.optJSONObject("LicenseShortName");
        String license=raw==null?"":raw.optString("value","").toLowerCase(java.util.Locale.ROOT);
        return license.contains("public domain")||license.equals("cc0")||
            license.startsWith("pd-")||license.startsWith("cc by")||license.startsWith("cc-by");
    }
    private static final class Resolved {
        final String thumb,commons;
        Resolved(String thumbnail,String attribution){thumb=thumbnail;commons=attribution;}
    }
    private static Resolved searchLicensedPainting(Painting item) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(searchEndpoint(item)).openConnection();
        conn.setConnectTimeout(10000);conn.setReadTimeout(11000);
        conn.setRequestProperty("User-Agent","ScripturaAndroid/0.7 (+https://github.com/JavierRB35/Scriptura)");
        try{
            if(conn.getResponseCode()!=200)return null;
            byte[] json;
            try(InputStream stream=conn.getInputStream()){json=readLimited(stream);}
            org.json.JSONObject result=new org.json.JSONObject(
                new String(json,java.nio.charset.StandardCharsets.UTF_8));
            org.json.JSONObject query=result.optJSONObject("query");
            org.json.JSONObject pages=query==null?null:query.optJSONObject("pages");
            if(pages==null)return null;
            String[] pieces=item.author.trim().split(" ");
            String surname=pieces[pieces.length-1].toLowerCase(java.util.Locale.ROOT);
            java.util.Iterator<String> it=pages.keys();
            Resolved best=null;
            int bestScore=0;
            while(it.hasNext()){
                org.json.JSONObject page=pages.optJSONObject(it.next());
                if(page==null)continue;
                String name=page.optString("title","");
                String lower=name.toLowerCase(java.util.Locale.ROOT);
                if(!name.startsWith("File:"))continue;
                org.json.JSONArray array=page.optJSONArray("imageinfo");
                if(array==null)continue;
                org.json.JSONObject info=array.optJSONObject(0);
                if(info==null||!licensed(info.optJSONObject("extmetadata")))continue;
                String thumb=info.optString("thumburl","");
                String url=info.optString("descriptionurl","");
                if(!thumb.startsWith("https://upload.wikimedia.org/") ||
                   !url.startsWith("https://commons.wikimedia.org/wiki/File:"))continue;
                // No mostrar una pintura distinta porque sea el primer resultado de búsqueda.
                int score=lower.contains(surname)?5:0;
                for(String word:item.file.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}0-9]+")){
                    if(word.length()>4 && lower.contains(word))score++;
                }
                if(score>=6&&score>bestScore){best=new Resolved(thumb,url);bestScore=score;}
            }
            return best;
        }finally{conn.disconnect();}
    }

    private static final ExecutorService IMAGES=Executors.newFixedThreadPool(2);

    /** Máximo ocho fichas activas por página; imágenes descargadas bajo demanda. */
    public static void thumbnail(Context context,ImageView target,Painting item){
        File dir=new File(context.getCacheDir(),"scriptura-arte-miniaturas");
        File imageFile=new File(dir,item.id+".img");
        File sourceFile=new File(dir,item.id+".url");
        IMAGES.execute(()->{
            byte[] data=null;
            String page=null;
            try {
                if(imageFile.isFile()&&imageFile.length()>100&&imageFile.length()<1_400_000
                   &&sourceFile.isFile()){
                    try(InputStream in=new java.io.FileInputStream(imageFile)){
                        data=readLimited(in);
                    }
                    try(InputStream in=new java.io.FileInputStream(sourceFile)){
                        page=new String(readLimited(in),java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
                if(data==null||page==null){
                    Resolved resolved=searchLicensedPainting(item);
                    if(resolved!=null){
                        HttpURLConnection conn=(HttpURLConnection)new URL(resolved.thumb).openConnection();
                        conn.setConnectTimeout(9000);conn.setReadTimeout(13000);
                        conn.setRequestProperty("User-Agent","ScripturaAndroid/0.7 (Wikimedia Commons)");
                        try{
                            if(conn.getResponseCode()==200){
                                try(InputStream in=conn.getInputStream()){data=readLimited(in);}
                                page=resolved.commons;
                            }
                        }finally{conn.disconnect();}
                        if(data!=null){
                            dir.mkdirs();
                            try(FileOutputStream out=new FileOutputStream(imageFile)){out.write(data);}
                            try(FileOutputStream out=new FileOutputStream(sourceFile)){
                                out.write(page.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            }
                        }
                    }
                }
                Bitmap decoded=data==null?null:BitmapFactory.decodeByteArray(data,0,data.length);
                if(decoded!=null){
                    final Bitmap picture=decoded;final String source=page;
                    target.post(()->{
                        target.setImageBitmap(picture);
                        target.setTag(source);
                    });
                }else{
                    target.post(()->target.setContentDescription(
                        "Miniatura no disponible o archivo sin derechos verificados. Consultar Wikimedia Commons."));
                }
            }catch(Exception ignored){
                target.post(()->target.setContentDescription(
                    "No se pudo cargar la imagen. Se puede consultar su ficha en Wikimedia Commons."));
            }
        });
    }
    private static byte[] readLimited(InputStream stream) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        byte[] buffer=new byte[8192];int n;
        while((n=stream.read(buffer))!=-1){
            output.write(buffer,0,n);
            if(output.size()>1_400_000)throw new java.io.IOException("Miniatura demasiado grande");
        }
        return output.toByteArray();
    }
}
