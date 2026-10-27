package com.scriptura.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.Html;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extractos breves de introducciones de la edición de Navarra.
 * Se calculan SOLO dentro del móvil desde el EPUB privado importado.
 * No se envían a un servidor, no se incluyen en el APK ni en GitHub.
 */
public final class NavarraIntroductions extends SQLiteOpenHelper {
    private static final Pattern ENTRY=Pattern.compile("(?is)<h[1-6]\\b[^>]*\\bid\\s*=\\s*['\"]in-([A-Za-z0-9]+)['\"][^>]*>.*?</h[1-6]\\s*>|<p\\b[^>]*>(.*?)</p\\s*>");
    private static final Pattern PART=Pattern.compile("(?i).*/Text/part(\\d{4})(?:_split_\\d{3})?\\.html");
    private final Context app;
    public NavarraIntroductions(Context c){
        super(c.getApplicationContext(),"scriptura_introducciones.db",null,2);
        app=c.getApplicationContext();
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE introductions(book TEXT PRIMARY KEY, source TEXT NOT NULL, body TEXT NOT NULL)");
        db.execSQL("CREATE TABLE source_revision(id INTEGER PRIMARY KEY CHECK(id=1),stamp TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int previous,int current){
        if(previous<2)
            db.execSQL("CREATE TABLE IF NOT EXISTS source_revision(id INTEGER PRIMARY KEY CHECK(id=1),stamp TEXT NOT NULL)");
    }
    private File sourceFile(){return new File(app.getFilesDir(),"biblia-usuario.epub");}
    private String fingerprint(){
        File file=sourceFile();
        if(!file.isFile())return "";
        return file.length()+":"+file.lastModified();
    }
    private boolean validSource(){
        String stamp=fingerprint();
        if(stamp.isEmpty())return false;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT stamp FROM source_revision WHERE id=1",null)){
            return c.moveToFirst()&&stamp.equals(c.getString(0));
        }
    }
    public int count(){
        if(!validSource())return 0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM introductions",null)){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    public String textFor(String book){
        if(!validSource())return "";
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT body FROM introductions WHERE book=?",new String[]{book})){
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    public String sourceBook(String book){
        if(!validSource())return "";
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT source FROM introductions WHERE book=?",new String[]{book})){
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    public static String sharedSource(String code){
        // En esta edición algunos libros tienen introducción editorial conjunta.
        switch(code){
            case "2S": return "1S";
            case "2R": return "1R";
            case "2Cro": return "1Cro";
            case "Ne": return "Esd";
            case "3Jn": return "2Jn";
            default:return code;
        }
    }
    private static String readable(String html){
        return Html.fromHtml(html,Html.FROM_HTML_MODE_LEGACY).toString()
            .replace('\u00A0',' ').replace('\uFEFF',' ')
            .replaceAll("[\\r\\n\\t]+"," ").replaceAll(" {2,}"," ").trim();
    }
    private static String preview(String value){
        if(value.length()<=760)return value;
        int cut=value.lastIndexOf(". ",740);
        if(cut<320)cut=730;
        return value.substring(0,cut+(cut==730?0:1)).trim()+(cut==730?"…":"");
    }

    /** Nunca devuelve contenido bíblico en el registro; solo el número de entradas. */
    public int importPrivateEpub() throws Exception {
        File file=sourceFile();
        if(!file.isFile())return 0;
        LinkedHashMap<String,StringBuilder> chunks=new LinkedHashMap<>();
        String current=null;
        try(ZipFile archive=new ZipFile(file)){
            ArrayList<? extends ZipEntry> list=Collections.list(archive.entries());
            Collections.sort(list,(a,b)->a.getName().compareTo(b.getName()));
            long total=0;int processed=0;
            for(ZipEntry entry:list){
                String name=entry.getName();
                Matcher namePart=PART.matcher(name);
                if(!namePart.matches())continue;
                int part=Integer.parseInt(namePart.group(1));
                if(part<236||part>271)continue;
                if(entry.getSize()>2_000_000)continue;
                if(++processed>1300)throw new IOException("Demasiados documentos introductorios");
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                try(InputStream stream=archive.getInputStream(entry)){
                    byte[] block=new byte[8192];int n;
                    while((n=stream.read(block))!=-1){
                        buffer.write(block,0,n);
                        total+=n;
                        if(buffer.size()>2_000_000||total>50_000_000)
                            throw new IOException("Las introducciones exceden el tamaño permitido");
                    }
                }
                Matcher matcher=ENTRY.matcher(buffer.toString("UTF-8"));
                while(matcher.find()){
                    if(matcher.group(1)!=null){
                        String id=matcher.group(1);
                        current=Books.NAMES.containsKey(id)?id:null;
                        if(current!=null&&!chunks.containsKey(current))
                            chunks.put(current,new StringBuilder());
                        continue;
                    }
                    if(current==null)continue;
                    StringBuilder contents=chunks.get(current);
                    if(contents.length()>=950)continue;
                    String partText=readable(matcher.group(2));
                    if(partText.length()<90||partText.startsWith("Volver al texto")
                       ||partText.startsWith("Volver al")||partText.matches("(?i)^\\d+\\..*"))
                        continue;
                    if(contents.length()>0)contents.append(" ");
                    contents.append(partText);
                }
            }
        }
        // No mostrar texto de otros libros bajo etiquetas incorrectas.
        // La introducción conjunta se identifica en cada ficha.
        Map<String,String> found=new HashMap<>();
        Map<String,String> origins=new HashMap<>();
        for(String book:Books.NAMES.keySet()){
            String from=sharedSource(book);
            StringBuilder body=chunks.get(from);
            if(body!=null&&body.length()>=120){
                found.put(book,preview(body.toString()));
                origins.put(book,from);
            }
        }
        if(found.isEmpty())throw new IOException(
            "Este EPUB no contiene introducciones reconocibles. La biblioteca bíblica importada no se modifica.");
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try{
            db.delete("introductions",null,null);
            for(String book:found.keySet())
                db.execSQL("INSERT INTO introductions(book,source,body) VALUES(?,?,?)",
                    new Object[]{book,origins.get(book),found.get(book)});
            db.execSQL("INSERT OR REPLACE INTO source_revision(id,stamp) VALUES(1,?)",
                new Object[]{fingerprint()});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return found.size();
    }
}
