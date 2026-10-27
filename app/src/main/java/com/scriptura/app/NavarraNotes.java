package com.scriptura.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.Html;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

public final class NavarraNotes extends SQLiteOpenHelper {
    private static final Pattern NOTE=Pattern.compile("(?i)id=[\\\"']nt-([A-Za-z0-9]+)-(\\d+)-(\\d+)[\\\"']");
    private static final Pattern PARA=Pattern.compile("(?is)<p\\b[^>]*>(.*?)</p\\s*>");
    private final Context context;
    public NavarraNotes(Context c) {super(c,"scriptura_notas.db",null,1);context=c.getApplicationContext();}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE notes(book TEXT NOT NULL,chapter INTEGER NOT NULL,verse INTEGER NOT NULL,body TEXT NOT NULL,PRIMARY KEY(book,chapter,verse))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int now){}
    private static String plain(String html){
        return Html.fromHtml(html,Html.FROM_HTML_MODE_LEGACY).toString()
            .replace("\uFEFF","").replace("\u00A0"," ").replaceAll("[\\r\\n\\t]+"," ").replaceAll(" {2,}"," ").trim();
    }
    public int count(){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM notes",null)){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    public String get(String book,int chapter,int verse){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM notes WHERE book=? AND chapter=? AND verse=?",
            new String[]{book,""+chapter,""+verse})){
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    private void extract(SQLiteDatabase db,String html){
        Matcher ids=NOTE.matcher(html);if(!ids.find())return;
        String book=ids.group(1);if(!Books.NAMES.containsKey(book))return;
        int chap=Integer.parseInt(ids.group(2)),verse=Integer.parseInt(ids.group(3));
        Matcher paragraphs=PARA.matcher(html);
        StringBuilder body=new StringBuilder();boolean inComment=false;
        while(paragraphs.find()){
            String content=paragraphs.group(1);
            if(content.contains("nt-"+book+"-"+chap+"-"+verse)) {inComment=true;continue;}
            if(!inComment)continue;
            if(content.contains("Volver a ")||content.contains("Volver a&nbsp;"))break;
            String txt=plain(content);
            if(txt.startsWith("Volver a"))break;
            if(txt.isEmpty()||txt.matches("(?i)^(COMENTARIO|[0-9]+\\s*[A-Za-z]+[0-9, .-]*)$"))continue;
            if(body.length()>0)body.append("\n\n");
            body.append(txt);
            if(body.length()>12000)break;
        }
        if(body.length()<20)return;
        db.execSQL("INSERT OR REPLACE INTO notes(book,chapter,verse,body) VALUES(?,?,?,?)",
            new Object[]{book,chap,verse,body.substring(0,Math.min(12000,body.length()))});
    }
    public int importFromPrivateEpub() throws Exception {
        File epub=new File(context.getFilesDir(),"biblia-usuario.epub");
        if(!epub.isFile())return 0;
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();int n=0;
        try(ZipFile archive=new ZipFile(epub)){
            db.execSQL("DELETE FROM notes");
            Enumeration<? extends ZipEntry> entries=archive.entries();
            long uncompressed=0;int processed=0;
            while(entries.hasMoreElements()){
                ZipEntry ent=entries.nextElement();String name=ent.getName();
                if(!name.matches("(?i).*?/Text/part\\d+(_split_\\d+)?\\.html"))continue;
                int number=Integer.parseInt(name.replaceAll("(?i).*?/Text/part(\\d+).*","$1"));
                if(number<236||ent.getSize()>3_000_000)continue;
                if(++processed>7000)throw new IOException("Demasiados documentos de comentarios");
                uncompressed+=Math.max(0,ent.getSize());
                if(uncompressed>190_000_000)throw new IOException("Se excede el límite de descompresión");
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                try(InputStream in=archive.getInputStream(ent)){
                    byte[] block=new byte[8192];int read;
                    while((read=in.read(block))!=-1){
                        buffer.write(block,0,read);
                        if(buffer.size()>3_000_000)break;
                    }
                }
                if(buffer.size()<=3_000_000){
                    extract(db,buffer.toString("UTF-8"));
                    n++;
                }
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return count();
    }
}
