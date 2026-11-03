package com.scriptura.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Seed catalogue and learned chapter analyses. Independent of the private Bible. */
final class GeographyStore extends SQLiteOpenHelper {
    static final String DATABASE="scriptura_geografia.db";
    private final Context app;
    private JSONObject catalog;
    GeographyStore(Context context){super(context,DATABASE,null,1);app=context.getApplicationContext();}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE geo_places(id TEXT PRIMARY KEY,payload TEXT NOT NULL,origin TEXT NOT NULL)");
        db.execSQL("CREATE TABLE geo_occurrences(book TEXT NOT NULL,chapter INTEGER NOT NULL,verse INTEGER NOT NULL,place_id TEXT NOT NULL,PRIMARY KEY(book,chapter,verse,place_id))");
        db.execSQL("CREATE INDEX geo_chapter ON geo_occurrences(book,chapter)");
        db.execSQL("CREATE TABLE geo_analyses(cache_key TEXT PRIMARY KEY,book TEXT NOT NULL,chapter INTEGER NOT NULL,payload TEXT NOT NULL)");
        db.execSQL("CREATE TABLE geo_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        throw new IllegalStateException("Versión geográfica no compatible");
    }
    private JSONObject catalog()throws Exception{
        if(catalog==null)try(InputStream in=app.getAssets().open("geografia_catalogo.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            catalog=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
        }
        return catalog;
    }
    private SQLiteDatabase ready()throws Exception{
        SQLiteDatabase db=getWritableDatabase();String version=catalog().getString("version");
        try(Cursor c=db.rawQuery("SELECT value FROM geo_meta WHERE key='seed_version'",null)){
            if(c.moveToFirst()&&version.equals(c.getString(0)))return db;
        }
        db.beginTransaction();
        try{
            // Only replace the seed layer: learned places and previous analyses survive upgrades.
            db.delete("geo_occurrences",null,null);
            JSONArray rows=catalog().getJSONArray("entries");
            for(int i=0;i<rows.length();i++){
                JSONObject p=rows.getJSONObject(i);new GeographyData.Entry(p);
                ContentValues values=new ContentValues();values.put("id",p.getString("id"));
                values.put("payload",p.toString());values.put("origin","seed");
                db.insertWithOnConflict("geo_places",null,values,SQLiteDatabase.CONFLICT_REPLACE);
                JSONArray occurrences=p.getJSONArray("occurrences");
                for(int k=0;k<occurrences.length();k++){
                    JSONArray r=occurrences.getJSONArray(k);
                    db.execSQL("INSERT OR IGNORE INTO geo_occurrences VALUES(?,?,?,?)",
                        new Object[]{r.getString(0),r.getInt(1),r.getInt(2),p.getString("id")});
                }
            }
            db.execSQL("INSERT OR REPLACE INTO geo_meta VALUES('seed_version',?)",new Object[]{version});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return db;
    }
    synchronized List<GeographyData.Entry> candidates(String book,int chapter,String text)throws Exception{
        SQLiteDatabase db=ready();LinkedHashMap<String,GeographyData.Entry> rows=new LinkedHashMap<>();
        try(Cursor c=db.rawQuery("SELECT DISTINCT p.payload FROM geo_places p JOIN geo_occurrences o ON p.id=o.place_id WHERE o.book=? AND o.chapter=? ORDER BY o.verse",new String[]{book,""+chapter})){
            while(c.moveToNext()){
                GeographyData.Entry p=new GeographyData.Entry(new JSONObject(c.getString(0)));rows.put(p.id,p);
            }
        }
        // The external verse index has no deuterocanonical books. Only use explicit Spanish
        // labels there; scanning every English alias would turn words such as "sin" into places.
        if(Arrays.asList("Tb","Jdt","1M","2M","Sb","Si","Ba").contains(book))
        try(Cursor c=db.rawQuery("SELECT payload FROM geo_places WHERE origin='seed'",null)){
            while(c.moveToNext()){
                GeographyData.Entry p=new GeographyData.Entry(new JSONObject(c.getString(0)));
                if(!rows.containsKey(p.id)&&(p.data.optBoolean("editorial")||p.data.optString("name_language").equals("es"))
                        &&p.name.length()>3&&GeographyData.contains(text,p.name))rows.put(p.id,p);
            }
        }
        return new ArrayList<>(rows.values());
    }
    synchronized GeographyData.Analysis load(String book,int chapter,String text)throws Exception{
        SQLiteDatabase db=ready();String key=GeographyData.cacheKey(book,chapter,text);
        try(Cursor c=db.rawQuery("SELECT payload FROM geo_analyses WHERE cache_key=?",new String[]{key})){
            if(c.moveToFirst())return GeographyData.Analysis.parse(c.getString(0));
        }
        List<GeographyData.Entry> candidates=candidates(book,chapter,text);
        Map<String,GeographyData.Entry> byId=new LinkedHashMap<>();for(GeographyData.Entry p:candidates)byId.put(p.id,p);
        List<GeographyData.Mention> mentions=new ArrayList<>();
        Map<Integer,String> verses=GeographyData.verseText(text);
        JSONArray chapters=catalog().getJSONArray("chapters");
        for(int i=0;i<chapters.length();i++){
            JSONObject row=chapters.getJSONObject(i);
            if(!row.getString("book").equals(book)||row.getInt("chapter")!=chapter)continue;
            boolean all=true;JSONArray source=row.getJSONArray("mentions");
            for(int k=0;k<source.length();k++){
                JSONObject m=source.getJSONObject(k);GeographyData.Entry p=byId.get(m.getString("place_id"));
                if(p==null||!p.matches(text)){all=false;continue;}
                List<Integer> refs=new ArrayList<>();JSONArray numbers=m.getJSONArray("verses");
                for(int v=0;v<numbers.length();v++)if(verses.containsKey(numbers.getInt(v)))refs.add(numbers.getInt(v));
                if(refs.isEmpty()){all=false;continue;}
                mentions.add(new GeographyData.Mention(p,m.getString("name"),m.getString("role"),m.getString("description"),refs,true));
            }
            // Reviewed against Acts 16. Do not generalize completeness to another chapter or excerpt.
            if(all&&book.equals("Hch")&&chapter==16&&verses.size()==40){
                GeographyData.Analysis result=new GeographyData.Analysis(mentions,"Catálogo · Hechos 16",true,true);
                save(book,chapter,text,result);return result;
            }
            mentions.clear();
        }
        for(GeographyData.Entry p:candidates){
            List<Integer> refs=new ArrayList<>();
            for(Map.Entry<Integer,String> v:verses.entrySet())if(p.matches(v.getValue()))refs.add(v.getKey());
            if(!refs.isEmpty())mentions.add(new GeographyData.Mention(p,p.name,"mencionado en el texto",p.description,refs,true));
        }
        mentions.sort(Comparator.comparingInt(m->m.verses.get(0)));
        GeographyData.Analysis result=new GeographyData.Analysis(mentions,"Catálogo local · cobertura parcial",false,false);
        save(book,chapter,text,result);return result;
    }
    synchronized void save(String book,int chapter,String text,GeographyData.Analysis result)throws Exception{
        SQLiteDatabase db=ready();db.beginTransaction();
        try{
            for(GeographyData.Mention m:result.mentions){
                ContentValues p=new ContentValues();p.put("id",m.place.id);p.put("payload",m.place.data.toString());p.put("origin","ai");
                // A model response can add an unlocated place, never overwrite the sourced catalogue.
                db.insertWithOnConflict("geo_places",null,p,SQLiteDatabase.CONFLICT_IGNORE);
            }
            ContentValues values=new ContentValues();values.put("cache_key",GeographyData.cacheKey(book,chapter,text));
            values.put("book",book);values.put("chapter",chapter);values.put("payload",result.json().toString());
            db.insertWithOnConflict("geo_analyses",null,values,SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
}
