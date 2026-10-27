package com.scriptura.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Catálogo histórico local independiente de scriptura.db.
 * No lee ni altera EPUB, notas privadas, favoritos, historial o claves.
 */
public final class HistoricalContextStore extends SQLiteOpenHelper {
    private static final String DATABASE="scriptura_contexto.db";
    private static final int DATABASE_VERSION=1;
    private static final String[] ASSETS={
        "contexto_grupos.json","contexto_oficios.json","contexto_vida.json"
    };
    private final Context app;
    public HistoricalContextStore(Context context){
        super(context,DATABASE,null,DATABASE_VERSION);
        app=context.getApplicationContext();
    }
    public static final class Topic {
        public final int id,position;
        public final String title,category,period,brief,detail,certainty,source;
        Topic(int id,int position,String title,String category,String period,
                String brief,String detail,String certainty,String source){
            this.id=id;this.position=position;this.title=title;
            this.category=category;this.period=period;this.brief=brief;
            this.detail=detail;this.certainty=certainty;this.source=source;
        }
    }
    public static final class Reference {
        public final String label,url;
        Reference(String label,String url){this.label=label;this.url=url;}
    }
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE topics(_id INTEGER PRIMARY KEY,title TEXT NOT NULL,category TEXT NOT NULL,period TEXT NOT NULL,"+
                "scope TEXT NOT NULL,aliases TEXT NOT NULL,brief TEXT NOT NULL,detail TEXT NOT NULL,"+
                "certainty TEXT NOT NULL,source TEXT NOT NULL)");
        db.execSQL("CREATE TABLE bibliography(ref TEXT PRIMARY KEY,label TEXT NOT NULL,url TEXT NOT NULL)");
        db.execSQL("CREATE INDEX topics_category ON topics(category)");
        try {
            for(String filename:ASSETS) {
                JSONObject data=new JSONObject(readAsset(filename));
                JSONObject bibliography=data.optJSONObject("bibliography");
                if(bibliography!=null) {
                    JSONArray names=bibliography.names();
                    if(names!=null)for(int i=0;i<names.length();i++){
                        String key=names.getString(i);
                        JSONObject source=bibliography.getJSONObject(key);
                        ContentValues v=new ContentValues();
                        v.put("ref",key);
                        v.put("label",source.getString("label"));
                        v.put("url",source.getString("url"));
                        db.insertOrThrow("bibliography",null,v);
                    }
                }
                JSONArray entries=data.getJSONArray("entries");
                for(int i=0;i<entries.length();i++){
                    JSONObject item=entries.getJSONObject(i);
                    if(item.getString("brief").trim().isEmpty()||
                            item.getString("detail").trim().isEmpty())throw new IllegalStateException("Entrada histórica incompleta");
                    ContentValues v=new ContentValues();
                    v.put("_id",item.getInt("id"));
                    for(String column:new String[]{"title","category","period","scope","brief","detail","certainty","source"}){
                        v.put(column,item.getString(column));
                    }
                    v.put("aliases",item.getJSONArray("aliases").toString());
                    db.insertOrThrow("topics",null,v);
                }
            }
        }catch(Exception ex){
            // Al crear la base de datos, SQLiteOpenHelper revierte la transacción si falla el catálogo.
            throw new IllegalStateException("No se pudo inicializar el catálogo histórico",ex);
        }
    }
    private String readAsset(String name)throws Exception{
        try(InputStream input=app.getAssets().open(name);ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))>=0)output.write(buffer,0,count);
            return new String(output.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        // Catálogo de solo lectura, separado de los datos personales del usuario.
        db.execSQL("DROP TABLE IF EXISTS topics");
        db.execSQL("DROP TABLE IF EXISTS bibliography");
        onCreate(db);
    }
    public List<Topic> forPassage(String book,String passage) {
        if(passage==null||passage.trim().isEmpty())return Collections.emptyList();
        String text=HistoricalTermMatcher.normalize(passage);
        if(text.isEmpty())return Collections.emptyList();
        boolean nt=Books.isNew(book);
        ArrayList<Topic> result=new ArrayList<>();
        try(Cursor cur=getReadableDatabase().rawQuery(
            "SELECT _id,title,category,period,scope,aliases,brief,detail,certainty,source FROM topics",null)){
            while(cur.moveToNext()){
                if(!HistoricalTermMatcher.appliesTo(cur.getString(4),nt))continue;
                int earliest=Integer.MAX_VALUE;
                try{
                    JSONArray aliases=new JSONArray(cur.getString(5));
                    for(int i=0;i<aliases.length();i++){
                        int pos=HistoricalTermMatcher.firstPosition(text,aliases.optString(i));
                        if(pos>=0&&pos<earliest)earliest=pos;
                    }
                }catch(Exception invalid){continue;}
                if(earliest!=Integer.MAX_VALUE)
                    result.add(new Topic(cur.getInt(0),earliest,cur.getString(1),
                        cur.getString(2),cur.getString(3),cur.getString(6),
                        cur.getString(7),cur.getString(8),cur.getString(9)));
            }
        }
        result.sort(Comparator.comparingInt((Topic t)->t.position).thenComparing(t->t.title));
        return result;
    }
    public Reference reference(String key){
        try(Cursor cur=getReadableDatabase().rawQuery("SELECT label,url FROM bibliography WHERE ref=?",new String[]{key})){
            if(cur.moveToFirst())return new Reference(cur.getString(0),cur.getString(1));
        }
        return null;
    }
    public int count(){
        try(Cursor cur=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM topics",null)){
            return cur.moveToFirst()?cur.getInt(0):0;
        }
    }
}
