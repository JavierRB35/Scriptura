package com.scriptura.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;

/** Debug-only, emulator-invoked smoke test for live SQLite snapshot creation. */
public final class BackupQaReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String outcome;
        try{
            SQLiteDatabase origin=context.openOrCreateDatabase("backup-qa.db",0,null);
            try{
                origin.execSQL("CREATE TABLE IF NOT EXISTS sample (id INTEGER PRIMARY KEY, value TEXT NOT NULL)");
                origin.execSQL("INSERT OR REPLACE INTO sample(id,value) VALUES(1,'copiado')");
                origin.setVersion(1);
                origin.enableWriteAheadLogging();
            }finally{origin.close();}
            File directory=new File(context.getFilesDir(),"backup-qa-staging");
            if(!directory.isDirectory()&&!directory.mkdirs())
                throw new IllegalStateException("No se pudo crear la carpeta de prueba");
            File snapshot=new File(directory,"snapshot.db");
            if(snapshot.exists()&&!snapshot.delete())
                throw new IllegalStateException("No se pudo limpiar la prueba anterior");
            ScripturaBackup.snapshotDatabase(context.getDatabasePath("backup-qa.db"),snapshot);
            SQLiteDatabase copied=SQLiteDatabase.openDatabase(snapshot.getPath(),null,SQLiteDatabase.OPEN_READONLY);
            try(Cursor values=copied.rawQuery("SELECT value FROM sample WHERE id=1",null)){
                if(!values.moveToFirst()||!"copiado".equals(values.getString(0)))
                    throw new IllegalStateException("La instantánea no contiene los datos originales");
            }finally{copied.close();}
            outcome="PASS: instantánea SQLite creada, abierta y comprobada";
        }catch(Exception error){
            outcome="FAIL: "+error.toString();
        }
        try(FileWriter result=new FileWriter(new File(context.getFilesDir(),"backup-qa-result.txt"))){
            result.write(outcome);
        }catch(Exception error){
            Log.e("ScripturaBackupQA","No se pudo guardar el resultado",error);
        }
        Log.i("ScripturaBackupQA",outcome);
    }
}
