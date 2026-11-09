package com.scriptura.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Independent local database: never modifies Scriptura's Bible/EPUB storage. */
public final class StudyAnnotations extends SQLiteOpenHelper {
    public static final String NOTE="NOTE",MAP="MAP",WEB="WEB",ATLAS="ATLAS",UNDERLINE="UNDERLINE";
    public static final class Item {
        public final long id,created;
        public final String book,quote,kind,body,url;
        public final int chapter,verse,endVerse,offset;
        Item(long id,String book,int chapter,int verse,int offset,String quote,
             String kind,String body,String url,long created){
            this(id,book,chapter,verse,verse,offset,quote,kind,body,url,created);
        }
        Item(long id,String book,int chapter,int verse,int endVerse,int offset,String quote,
             String kind,String body,String url,long created){
            this.id=id;this.book=book;this.chapter=chapter;this.verse=verse;
            this.endVerse=endVerse;this.offset=offset;this.quote=quote;this.kind=kind;this.body=body;
            this.url=url;this.created=created;
        }
    }
    public StudyAnnotations(Context context){super(context,"scriptura-study-annotations.db",null,2);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE study_annotations ("+
            "id INTEGER PRIMARY KEY AUTOINCREMENT,"+
            "book TEXT NOT NULL,chapter INTEGER NOT NULL,verse INTEGER NOT NULL,"+
            "end_verse INTEGER NOT NULL DEFAULT 0,"+
            "offset INTEGER NOT NULL,quote TEXT NOT NULL,kind TEXT NOT NULL,"+
            "body TEXT NOT NULL,url TEXT NOT NULL,created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_study_annotation_location ON study_annotations(book,chapter,verse)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        // Additive migration: existing entries retain their ID, quote, notes and original verse.
        // A zero endpoint identifies legacy single-verse entries without rewriting user data.
        if(oldVersion<2)
            db.execSQL("ALTER TABLE study_annotations ADD COLUMN end_verse INTEGER NOT NULL DEFAULT 0");
    }
    /** Every annotation in the library, ordered for a readable export. */
    public List<Item> all(){
        ArrayList<Item> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT id,book,chapter,verse,end_verse,offset,quote,kind,body,url,created "+
            "FROM study_annotations ORDER BY book,chapter,verse,created,id",null)){
            while(c.moveToNext())result.add(new Item(c.getLong(0),c.getString(1),
                c.getInt(2),c.getInt(3),c.getInt(4)>0?c.getInt(4):c.getInt(3),
                c.getInt(5),c.getString(6),c.getString(7),c.getString(8),
                c.getString(9),c.getLong(10)));
        }
        return result;
    }
    public List<Item> forChapter(String book,int chapter){
        ArrayList<Item> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT id,book,chapter,verse,end_verse,offset,quote,kind,body,url,created "+
            "FROM study_annotations WHERE book=? AND chapter=? ORDER BY verse,created,id",
            new String[]{book,Integer.toString(chapter)})){
            while(c.moveToNext())result.add(new Item(c.getLong(0),c.getString(1),
                c.getInt(2),c.getInt(3),c.getInt(4)>0?c.getInt(4):c.getInt(3),
                c.getInt(5),c.getString(6),c.getString(7),c.getString(8),
                c.getString(9),c.getLong(10)));
        }
        return result;
    }
    public void save(long id,String book,int chapter,int verse,int offset,
                     String quote,String kind,String body,String url){
        save(id,book,chapter,verse,verse,offset,quote,kind,body,url);
    }
    public void save(long id,String book,int chapter,int verse,int endVerse,int offset,
                     String quote,String kind,String body,String url){
        if(!NOTE.equals(kind)&&!MAP.equals(kind)&&!WEB.equals(kind)&&!ATLAS.equals(kind)&&!UNDERLINE.equals(kind))
            throw new IllegalArgumentException("Tipo de anotación incorrecto");
        if(book==null||book.isEmpty()||chapter<1||verse<1||endVerse<verse||
                endVerse>200||offset<0)
            throw new IllegalArgumentException("Referencia bíblica incorrecta");
        ContentValues v=new ContentValues();
        v.put("book",book);v.put("chapter",chapter);v.put("verse",verse);
        v.put("end_verse",endVerse);
        v.put("offset",offset);v.put("quote",quote==null?"":quote);
        v.put("kind",kind);v.put("body",body==null?"":body);
        v.put("url",url==null?"":url);
        SQLiteDatabase db=getWritableDatabase();
        if(id>0){
            if(db.update("study_annotations",v,"id=?",new String[]{Long.toString(id)})!=1)
                throw new IllegalStateException("La anotación ya no existe");
        }else{
            v.put("created",System.currentTimeMillis());
            if(db.insertOrThrow("study_annotations",null,v)<=0)
                throw new IllegalStateException("No se pudo guardar la anotación");
        }
    }
    public void delete(long id){
        getWritableDatabase().delete("study_annotations","id=?",new String[]{Long.toString(id)});
    }
}