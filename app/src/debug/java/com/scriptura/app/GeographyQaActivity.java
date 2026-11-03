package com.scriptura.app;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Synthetic geography QA only: does not import or distribute any Bible edition. */
public final class GeographyQaActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);TextView status=new TextView(this);status.setText("Comprobando geografía…");setContentView(status);
        new Thread(()->{
            try{
                JSONObject catalog;
                try(InputStream in=getAssets().open("geografia_catalogo.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                    catalog=new JSONObject(out.toString("UTF-8"));
                }
                JSONArray mentions=catalog.getJSONArray("chapters").getJSONObject(0).getJSONArray("mentions");
                Map<Integer,StringBuilder> lines=new LinkedHashMap<>();
                for(int v=1;v<=40;v++)lines.put(v,new StringBuilder("Texto sintético de prueba. "));
                for(int i=0;i<mentions.length();i++){
                    JSONObject m=mentions.getJSONObject(i);JSONArray refs=m.getJSONArray("verses");
                    for(int k=0;k<refs.length();k++)lines.get(refs.getInt(k)).append(m.getString("name")).append(". ");
                }
                StringBuilder text=new StringBuilder();for(int v:lines.keySet())text.append(v).append("  ").append(lines.get(v)).append("\n\n");
                String scripture=text.toString().trim();
                try(GeographyStore store=new GeographyStore(this)){
                    GeographyData.Analysis first=store.load("Hch",16,scripture);
                    require(first.reviewed&&first.mentions.size()==15,"Hechos 16 incompleto");
                    GeographyData.Analysis copy=store.load("Hch",16,scripture);
                    require(copy.json().toString().equals(first.json().toString()),"La caché cambia el contenido");
                    GeographyData.Analysis other=store.load("Hch",16,"1  Derbe y Listra");
                    require(!other.reviewed&&!other.complete,"Un fragmento se considera capítulo completo");
                    String novel="1  Betulia es el lugar de esta prueba sintética.";
                    GeographyData.Analysis base=store.load("Jdt",1,novel);
                    GeographyData.Analysis ai=GeographyData.fromAi("{\"complete\":true,\"mentions\":[{\"place_id\":\"\",\"name\":\"Betulia\",\"verses\":[1],\"role\":\"escenario\",\"description\":\"Lugar de la prueba.\"}]}",
                        "Jdt",1,novel,Collections.emptyList(),base,"IA de prueba");
                    store.save("Jdt",1,novel,ai);
                    require(!store.load("Jdt",1,novel).mentions.get(0).place.located(),"Coordenadas inventadas");
                }
                try(GeographyStore reopened=new GeographyStore(this)){
                    GeographyData.Analysis cached=reopened.load("Jdt",1,"1  Betulia es el lugar de esta prueba sintética.");
                    require(cached.origin.equals("IA de prueba · generado por IA"),"La consulta no persiste al reabrir");
                    require(reopened.load("Hch",16,scripture).mentions.size()==15,"Se ha perdido el catálogo");
                    File snapshot=new File(getCacheDir(),"geography-snapshot.db");
                    if(snapshot.exists()&&!snapshot.delete())throw new IOException("No se pudo preparar la instantánea");
                    ScripturaBackup.snapshotDatabase(getDatabasePath(GeographyStore.DATABASE),snapshot);
                    try(SQLiteDatabase copied=SQLiteDatabase.openDatabase(snapshot.getPath(),null,SQLiteDatabase.OPEN_READONLY);
                        Cursor c=copied.rawQuery("SELECT COUNT(*) FROM geo_analyses",null)){
                        require(c.moveToFirst()&&c.getInt(0)>=3,"La instantánea no contiene los análisis");
                    }
                }
                write("PASS: 15 lugares, capítulo completo, caché persistente, fuente y lugares sin coordenadas.");
                runOnUiThread(()->{
                    status.setText("Geografía verificada");
                    BibleStore.Passage p=new BibleStore.Passage("Hechos 16","Hch",16,1,200,scripture);
                    new ChapterPlacesDialog(this,p,prompt->{throw new AssertionError("No debe consultar IA");},
                        false,false,0xff0d2338,0xff475969,0xfffffcf6).show();
                });
            }catch(Throwable error){
                try{write("FAIL: "+error);}catch(Exception ignored){}
                runOnUiThread(()->status.setText("Fallo de geografía: "+error));
            }
        }).start();
    }
    private void write(String text)throws Exception{
        try(FileOutputStream out=openFileOutput("geography-qa-result.txt",MODE_PRIVATE)){out.write(text.getBytes(StandardCharsets.UTF_8));}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
