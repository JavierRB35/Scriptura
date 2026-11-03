package com.scriptura.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;

/** A single user-selected SAF document. Never backs up API keys or cached credentials. */
final class ScripturaBackup {
    static final int PICK_DESTINATION=2031, PICK_RESTORE=2032;
    private static final String PREFS="scriptura_backup_settings";
    private static final String FILE="biblia-usuario.epub";
    private static final String[] DATABASES={"scriptura.db","scriptura_notas.db",
        "scriptura-study-annotations.db","scriptura_introducciones.db","scriptura_contexto.db","scriptura_geografia.db"};
    private static final String[] SIDECARS={"","-wal","-shm"};
    private static final int FORMAT=1;
    private static final long MAX_FILE=180_000_000L;
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean IMPORTING=new AtomicBoolean();
    private static final AtomicInteger PENDING=new AtomicInteger();
    private static final String TAG="ScripturaBackup";

    private ScripturaBackup(){}
    private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(PREFS,0);}
    static boolean configured(Context c){return !prefs(c).getString("uri","").isEmpty();}
    static long lastSuccess(Context c){return prefs(c).getLong("success",0);}
    static String lastError(Context c){return prefs(c).getString("error","");}
    static boolean importing(){return IMPORTING.get();}
    static boolean isRunning(){return PENDING.get()>0;}
    static void setImporting(boolean value){IMPORTING.set(value);}

    enum BackupResult { SAVED, UNCHANGED, IMPORT_IN_PROGRESS, DESTINATION_CHANGED }
    interface BackupCallback {
        /** Called on the main thread only after writing and read-back verification finish. */
        void finished(BackupResult result,String error);
    }

    static void chooseDestination(Context context,Uri uri,int flags,BackupCallback callback) throws Exception {
        int access=flags&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if((access&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0)
            throw new IOException("La ubicación seleccionada no permite escribir.");
        context.getContentResolver().takePersistableUriPermission(uri,access);
        // A new destination starts a new failure streak. Do not report old errors for this file.
        prefs(context).edit().putString("uri",uri.toString()).remove("fingerprint")
            .remove("had_epub").putLong("success",0).putString("error","")
            .putInt("failures",0).apply();
        backupAsync(context,true,callback);
    }

    static void backupAsync(Context context,boolean force){backupAsync(context,force,null);}
    static void backupAsync(Context context,boolean force,BackupCallback callback){
        Context app=context.getApplicationContext();
        String target=prefs(app).getString("uri","");
        if(target.isEmpty()){
            if(callback!=null)new Handler(Looper.getMainLooper()).post(()->
                callback.finished(null,"Primero elige el archivo de destino."));
            return;
        }
        // Serialise all requests, including a manual request made during the startup backup.
        // No manual request is silently dropped.
        PENDING.incrementAndGet();
        EXECUTOR.execute(()->{
            BackupResult result=null;
            String message=null;
            try{
                if(!target.equals(prefs(app).getString("uri","")))
                    result=BackupResult.DESTINATION_CHANGED;
                else result=backup(app,force,target);
            }catch(Exception error){
                Log.w(TAG,"No se pudo crear la copia",error);
                message=error.getMessage()==null?"Error al guardar la copia":error.getMessage();
                if(target.equals(prefs(app).getString("uri",""))){
                    SharedPreferences p=prefs(app);
                    p.edit().putString("error",message)
                        .putInt("failures",p.getInt("failures",0)+1).apply();
                }
            }finally{
                PENDING.decrementAndGet();
            }
            if(callback!=null){
                BackupResult completed=result;
                String completedError=message;
                new Handler(Looper.getMainLooper()).post(()->
                    callback.finished(completed,completedError));
            }
        });
    }

    static int failureCount(Context c){return prefs(c).getInt("failures",0);}
    private static File source(Context c,String name){return c.getDatabasePath(name);}
    private static String signature(Context c) throws Exception {
        StringBuilder value=new StringBuilder();
        for(String name:DATABASES){
            File f=source(c,name);
            for(String suffix:SIDECARS){
                File part=new File(f.getPath()+suffix);
                value.append(name).append(suffix).append(':').append(part.isFile()?part.length():-1)
                    .append(':').append(part.lastModified()).append(';');
            }
        }
        File epub=new File(c.getFilesDir(),FILE);
        value.append(epub.length()).append(':').append(epub.lastModified());
        value.append(':').append(c.getSharedPreferences("scriptura_display",0).getInt("display_mode",0));
        value.append(':').append(c.getSharedPreferences("scriptura_reminders",0).getBoolean("enabled",false));
        value.append(':').append(c.getSharedPreferences("MainActivity",0).getInt("font",18));
        return sha(value.toString().getBytes("UTF-8"));
    }

    private static BackupResult backup(Context c,boolean force,String target) throws Exception {
        if(IMPORTING.get())return BackupResult.IMPORT_IN_PROGRESS; // An import may update several databases sequentially.
        SharedPreferences p=prefs(c);
        String before=signature(c);
        if(!force&&before.equals(p.getString("fingerprint","")))return BackupResult.UNCHANGED;
        File epub=new File(c.getFilesDir(),FILE);
        if(p.getBoolean("had_epub",false)&&!epub.isFile())
            throw new IOException("Falta el EPUB anterior. Se conserva la última copia válida.");
        // Cache entries can be evicted by Android while a snapshot is being built.
        // Use private app storage for the short-lived staging directory instead.
        File[] stale=c.getFilesDir().listFiles();
        if(stale!=null)for(File item:stale)
            if(item.getName().startsWith("scriptura-backup-"))deleteStage(item);
        File stage=new File(c.getFilesDir(),"scriptura-backup-"+System.nanoTime());
        if(!stage.mkdirs())throw new IOException("No se pudo preparar la copia local.");
        try{
            LinkedHashMap<String,File> entries=new LinkedHashMap<>();
            for(String name:DATABASES){
                File origin=source(c,name);
                if(!origin.isFile())continue;
                File snapshot=new File(stage,name);
                try{snapshotDatabase(origin,snapshot);}
                catch(Exception failure){
                    throw new IOException("No se pudo preparar "+name+": "+failure.getMessage(),failure);
                }
                entries.put("db/"+name,snapshot);
            }
            if(epub.isFile())entries.put("files/"+FILE,epub);
            if(p.getBoolean("had_epub",false)&&!epub.isFile())
                throw new IOException("No se debe reemplazar una copia que contenía una Biblia.");
            if(epub.isFile()){
                File db=entries.get("db/scriptura.db");
                if(db==null||verseCount(db)<20000)
                    throw new IOException("La biblioteca bíblica parece incompleta. Se conserva la copia anterior.");
            }
            // A concurrent change is picked up on the following launch.
            String after=signature(c);
            if(IMPORTING.get())return BackupResult.IMPORT_IN_PROGRESS;
            if(!target.equals(p.getString("uri","")))return BackupResult.DESTINATION_CHANGED;

            JSONObject manifest=new JSONObject();manifest.put("format",FORMAT);
            JSONObject files=new JSONObject();
            for(Map.Entry<String,File> entry:entries.entrySet()){
                JSONObject info=new JSONObject();
                info.put("size",entry.getValue().length());info.put("sha256",sha(entry.getValue()));
                files.put(entry.getKey(),info);
            }
            manifest.put("files",files);
            JSONObject settings=new JSONObject();
            settings.put("font",c.getSharedPreferences("MainActivity",0).getInt("font",18));
            settings.put("guide",c.getSharedPreferences("MainActivity",0).getBoolean("primer_uso_v1",false));
            settings.put("display",c.getSharedPreferences("scriptura_display",0).getInt("display_mode",0));
            settings.put("reminders",c.getSharedPreferences("scriptura_reminders",0).getBoolean("enabled",false));

            File archive=new File(stage,"Scriptura.backup");
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(archive)))){
                for(Map.Entry<String,File> entry:entries.entrySet()){
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    try(InputStream in=new FileInputStream(entry.getValue())){copy(in,zip,MAX_FILE);}
                    zip.closeEntry();
                }
                writeEntry(zip,"manifest.json",manifest.toString());
                writeEntry(zip,"settings.json",settings.toString());
            }
            inspectArchive(archive,null); // Complete validation before touching the user's only file.
            Uri destination=Uri.parse(target);
            try(ParcelFileDescriptor fd=c.getContentResolver().openFileDescriptor(destination,"wt")){
                if(fd==null)throw new IOException("No se puede abrir el archivo de destino.");
                try(InputStream in=new FileInputStream(archive);
                    OutputStream out=new FileOutputStream(fd.getFileDescriptor())){
                    copy(in,out,MAX_FILE);out.flush();fd.getFileDescriptor().sync();
                }
            }
            // Certain document providers do not support truncation or verification.
            File readback=new File(stage,"verificacion.backup");
            try(InputStream in=c.getContentResolver().openInputStream(destination);
                OutputStream out=new FileOutputStream(readback)){
                if(in==null)throw new IOException("No se pudo verificar el archivo guardado.");
                copy(in,out,MAX_FILE);
            }
            if(!sha(readback).equals(sha(archive)))
                throw new IOException("La ubicación no ha guardado correctamente la copia.");
            p.edit().putString("fingerprint",before).putBoolean("had_epub",epub.isFile())
                .putLong("success",System.currentTimeMillis()).putString("error","")
                .putInt("failures",0).apply();
            return BackupResult.SAVED;
        }finally{deleteStage(stage);}
    }

    /**
     * Use SQLite's consistent online VACUUM INTO snapshot when available.
     * The legacy ATTACH path is retained for Android versions with SQLite < 3.27.
     * The source remains open while the snapshot is taken; no live WAL is copied raw.
     */
    static void snapshotDatabase(File origin,File snapshot) throws Exception {
        if(!snapshot.getParentFile().isDirectory())
            throw new IOException("No existe la carpeta temporal privada.");
        SQLiteDatabase db=SQLiteDatabase.openDatabase(origin.getPath(),null,SQLiteDatabase.OPEN_READWRITE);
        try{
            checkDatabase(db);
            if(supportsVacuumInto(db)){
                db.execSQL("VACUUM INTO '"+snapshot.getPath().replace("'","''")+"'");
            }else{
                snapshotDatabaseLegacy(db,snapshot);
            }
        }finally{db.close();}
        if(!snapshot.isFile()||snapshot.length()==0)
            throw new IOException("SQLite no creó la instantánea de "+origin.getName()+".");
        SQLiteDatabase verified=SQLiteDatabase.openDatabase(snapshot.getPath(),null,SQLiteDatabase.OPEN_READONLY);
        try{checkDatabase(verified);}finally{verified.close();}
    }
    private static boolean supportsVacuumInto(SQLiteDatabase db){
        try(Cursor cursor=db.rawQuery("SELECT sqlite_version()",null)){
            if(!cursor.moveToFirst())return false;
            String[] parts=cursor.getString(0).split("\\.");
            int major=Integer.parseInt(parts[0]),minor=Integer.parseInt(parts[1]);
            return major>3||(major==3&&minor>=27);
        }catch(Exception error){
            Log.w(TAG,"No se pudo consultar la versión SQLite; se usará el método clásico.",error);
            return false;
        }
    }
    /** Compatibility path for SQLite before VACUUM INTO was added (3.27). */
    private static void snapshotDatabaseLegacy(SQLiteDatabase db,File snapshot) throws Exception {
        // Android propagates the origin connection's OPEN_READWRITE flags to ATTACH.
        // Those flags cannot create the destination. Create an empty SQLite file first,
        // using an independent handle, then attach it on the pinned source connection.
        try(SQLiteDatabase target=SQLiteDatabase.openOrCreateDatabase(snapshot,null)){
            // Opening and closing creates a valid writable SQLite destination.
        }
        // Pin one connection for ATTACH, table reads and the entire transaction.
        db.beginTransaction();
        try{
            db.execSQL("ATTACH DATABASE '"+snapshot.getPath().replace("'","''")+"' AS snapshot");
            ArrayList<String[]> tables=new ArrayList<>(),indexes=new ArrayList<>();
            try(Cursor cursor=db.rawQuery("SELECT type,name,sql FROM sqlite_master "+
                "WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY type DESC,name",null)){
                while(cursor.moveToNext()){
                    String[] item={cursor.getString(1),cursor.getString(2)};
                    if("table".equals(cursor.getString(0)))tables.add(item);
                    else if("index".equals(cursor.getString(0)))indexes.add(item);
                }
            }
            for(String[] table:tables){
                String definition=table[1];int bracket=definition.indexOf('(');
                if(bracket<0)throw new IOException("Esquema de base de datos no compatible.");
                db.execSQL("CREATE TABLE snapshot."+identifier(table[0])+" "+definition.substring(bracket));
                db.execSQL("INSERT INTO snapshot."+identifier(table[0])+" SELECT * FROM main."+identifier(table[0]));
            }
            for(String[] index:indexes){
                String sql=index[1];int on=sql.toUpperCase(Locale.ROOT).indexOf(" ON ");
                if(on<0)throw new IOException("Índice SQLite no compatible.");
                String unique=sql.toUpperCase(Locale.ROOT).startsWith("CREATE UNIQUE")?"UNIQUE ":"";
                db.execSQL("CREATE "+unique+"INDEX snapshot."+identifier(index[0])+sql.substring(on));
            }
            int version=0;
            try(Cursor cur=db.rawQuery("PRAGMA main.user_version",null)){
                if(cur.moveToFirst())version=cur.getInt(0);
            }
            db.execSQL("PRAGMA snapshot.user_version="+version);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private static String identifier(String name){return "\""+name.replace("\"","\"\"")+"\"";}
    private static void checkDatabase(SQLiteDatabase db) throws IOException {
        try(Cursor cursor=db.rawQuery("PRAGMA quick_check",null)){
            if(!cursor.moveToFirst()||!"ok".equalsIgnoreCase(cursor.getString(0)))
                throw new IOException("Una base de datos no supera la comprobación de integridad.");
        }
    }
    private static void checkSchema(SQLiteDatabase db,String name) throws IOException {
        String table;
        int maxVersion;
        switch(name){
            case "scriptura.db":table="verses";maxVersion=2;break;
            case "scriptura_notas.db":table="notes";maxVersion=1;break;
            case "scriptura-study-annotations.db":table="study_annotations";maxVersion=1;break;
            case "scriptura_introducciones.db":table="introductions";maxVersion=2;break;
            case "scriptura_contexto.db":table="topics";maxVersion=1;break;
            case "scriptura_geografia.db":table="geo_places";maxVersion=1;break;
            default:throw new IOException("Base de datos inesperada.");
        }
        try(Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{table})){
            if(!c.moveToFirst())throw new IOException("Falta la tabla necesaria: "+table);
        }
        try(Cursor c=db.rawQuery("PRAGMA user_version",null)){
            if(!c.moveToFirst()||c.getInt(0)<1||c.getInt(0)>maxVersion)
                throw new IOException("La copia pertenece a una versión de base de datos incompatible.");
        }
    }
    private static long verseCount(File database){
        SQLiteDatabase db=SQLiteDatabase.openDatabase(database.getPath(),null,SQLiteDatabase.OPEN_READONLY);
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM verses",null)){
            return c.moveToFirst()?c.getLong(0):0;
        }finally{db.close();}
    }

    static final class RestorePlan {
        final File stage;final JSONObject settings;final ArrayList<String> names;
        RestorePlan(File stage,JSONObject settings,ArrayList<String> names){
            this.stage=stage;this.settings=settings;this.names=names;
        }
    }
    static RestorePlan prepareRestore(Context c,Uri uri) throws Exception {
        File stage=new File(c.getFilesDir(),"scriptura-restore-stage");
        if(new File(stage,".pending").isFile())
            throw new IOException("Existe una restauración pendiente. Reinicia Scriptura.");
        deleteStage(stage);
        if(!stage.mkdirs())throw new IOException("No se pudo preparar la restauración.");
        try{
            File archive=new File(stage,"source.backup");
            try(InputStream in=c.getContentResolver().openInputStream(uri);
                OutputStream out=new FileOutputStream(archive)){
                if(in==null)throw new IOException("No se puede leer la copia seleccionada.");
                copy(in,out,MAX_FILE);
            }
            JSONObject settings=new JSONObject();
            ArrayList<String> names=new ArrayList<>();
            inspectArchive(archive,new ArchiveReader(){
                @Override public void settings(JSONObject s){
                    // JSONObjects are mutable; copying is unnecessary within a single thread.
                    for(Iterator<String> it=s.keys();it.hasNext();){
                        String key=it.next();try{settings.put(key,s.get(key));}catch(Exception error){throw new IllegalStateException(error);}
                    }
                }
                @Override public void file(String name,InputStream in,long size) throws Exception {
                    String simple=name.substring(name.indexOf('/')+1);
                    File target=new File(stage,"new_"+simple);
                    try(OutputStream out=new FileOutputStream(target)){copy(in,out,MAX_FILE);}
                    if(name.startsWith("db/")){
                        SQLiteDatabase db=SQLiteDatabase.openDatabase(target.getPath(),null,SQLiteDatabase.OPEN_READONLY);
                        try{checkDatabase(db);checkSchema(db,simple);}finally{db.close();}
                    }
                    names.add(name);
                }
            });
            return new RestorePlan(stage,settings,names);
        }catch(Exception e){deleteStage(stage);throw e;}
    }
    private interface ArchiveReader {
        void settings(JSONObject settings) throws Exception;
        void file(String name,InputStream in,long size) throws Exception;
    }
    private static boolean accepted(String name){
        if(("files/"+FILE).equals(name))return true;
        for(String db:DATABASES)if(("db/"+db).equals(name))return true;
        return false;
    }
    private static void inspectArchive(File archive,ArchiveReader reader) throws Exception {
        try(ZipFile zip=new ZipFile(archive)){
            ZipEntry manifestEntry=zip.getEntry("manifest.json"),settingsEntry=zip.getEntry("settings.json");
            if(manifestEntry==null||settingsEntry==null)
                throw new IOException("El archivo no es una copia válida de Scriptura.");
            JSONObject manifest=new JSONObject(readText(zip,manifestEntry));
            if(manifest.optInt("format",-1)!=FORMAT)throw new IOException("Versión de copia incompatible.");
            JSONObject files=manifest.getJSONObject("files");
            JSONObject settings=new JSONObject(readText(zip,settingsEntry));
            if(reader!=null)reader.settings(settings);
            HashSet<String> listed=new HashSet<>();
            Enumeration<? extends ZipEntry> all=zip.entries();
            while(all.hasMoreElements()){
                ZipEntry entry=all.nextElement();String name=entry.getName();
                if(entry.isDirectory()||(!accepted(name)&&!name.equals("manifest.json")&&!name.equals("settings.json")))
                    throw new IOException("La copia contiene archivos no admitidos.");
                if(!listed.add(name))throw new IOException("La copia contiene nombres duplicados.");
            }
            long totalSize=0;
            for(Iterator<String> keys=files.keys();keys.hasNext();){
                String name=keys.next();
                if(!accepted(name))throw new IOException("La copia contiene un archivo inesperado.");
                ZipEntry item=zip.getEntry(name);
                if(item==null)throw new IOException("La copia está incompleta.");
                JSONObject info=files.getJSONObject(name);
                if(info.getLong("size")>MAX_FILE||info.getLong("size")<0)
                    throw new IOException("La copia contiene un archivo demasiado grande.");
                totalSize+=info.getLong("size");
                if(totalSize>250_000_000L)throw new IOException("El contenido de la copia excede el límite de seguridad.");
                File part;
                if(reader!=null){
                    try(InputStream in=zip.getInputStream(item)){reader.file(name,in,info.getLong("size"));}
                    // Validate the extracted bytes, not only the ZIP headers.
                    part=new File(archive.getParentFile(),"new_"+name.substring(name.indexOf('/')+1));
                }else{
                    part=new File(archive.getParentFile(),name.substring(name.indexOf('/')+1));
                    if(!part.isFile()){
                        part=new File(archive.getParentFile(),"verified_"+name.substring(name.indexOf('/')+1));
                        try(InputStream in=zip.getInputStream(item);OutputStream out=new FileOutputStream(part)){
                            copy(in,out,MAX_FILE);
                        }
                    }
                }
                if(part.length()!=info.getLong("size")||!sha(part).equals(info.getString("sha256")))
                    throw new IOException("La copia no supera la verificación de integridad.");
            }
            for(String name:listed)if(accepted(name)&&!files.has(name))
                throw new IOException("La copia tiene un archivo sin verificar.");
            if(files.has("files/"+FILE)&&!files.has("db/scriptura.db"))
                throw new IOException("La copia contiene un EPUB sin su biblioteca.");
            if(!listed.contains("manifest.json")||!listed.contains("settings.json"))
                throw new IOException("Faltan metadatos de la copia.");
        }
    }

    /** Only call on the UI thread after closing all SQLiteOpenHelper instances. */
    static void applyRestore(Context c,RestorePlan plan) throws Exception {
        File stage=plan.stage,old=new File(stage,"old");
        if(!old.mkdirs())throw new IOException("No se pudo proteger la biblioteca anterior.");
        File pending=new File(stage,".pending");
        if(!pending.createNewFile())throw new IOException("Restauración ya iniciada.");
        try{
            // Move originals first. On a crash, recoverInterruptedRestore rolls back.
            for(String db:DATABASES){
                File target=source(c,db);
                for(String suffix:SIDECARS)moveIfPresent(new File(target.getPath()+suffix),
                    new File(old,db+suffix));
            }
            moveIfPresent(new File(c.getFilesDir(),FILE),new File(old,FILE));
            for(String name:plan.names){
                String simple=name.substring(name.indexOf('/')+1);
                File from=new File(stage,"new_"+simple);
                File target=name.startsWith("db/")?source(c,simple):new File(c.getFilesDir(),simple);
                if(!from.renameTo(target))throw new IOException("No se pudo restaurar "+simple);
            }
            c.getSharedPreferences("MainActivity",0).edit().putInt("font",plan.settings.optInt("font",18)).commit();
            c.getSharedPreferences("MainActivity",0).edit()
                .putBoolean("primer_uso_v1",plan.settings.optBoolean("guide",false)).commit();
            c.getSharedPreferences("scriptura_display",0).edit().putInt("display_mode",plan.settings.optInt("display",0)).commit();
            c.getSharedPreferences("scriptura_reminders",0).edit()
                .putBoolean("enabled",plan.settings.optBoolean("reminders",false)).commit();
            prefs(c).edit().remove("fingerprint").remove("had_epub")
                .putString("error","").commit();
            if(!pending.delete())throw new IOException("No se pudo confirmar la restauración.");
            deleteStage(stage);
        }catch(Exception error){
            recoverInterruptedRestore(c);
            throw error;
        }
    }
    static void recoverInterruptedRestore(Context c) throws IOException {
        File stage=new File(c.getFilesDir(),"scriptura-restore-stage");
        File pending=new File(stage,".pending");
        if(!pending.isFile()){deleteStage(stage);return;}
        File old=new File(stage,"old");
        Set<String> expected=new HashSet<>();
        try(ZipFile backup=new ZipFile(new File(stage,"source.backup"))){
            Enumeration<? extends ZipEntry> entries=backup.entries();
            while(entries.hasMoreElements())expected.add(entries.nextElement().getName());
        }
        for(String db:DATABASES){
            File target=source(c,db);
            for(String suffix:SIDECARS)rollback(new File(target.getPath()+suffix),new File(old,db+suffix),
                new File(stage,"new_"+db),suffix.isEmpty()&&expected.contains("db/"+db));
        }
        rollback(new File(c.getFilesDir(),FILE),new File(old,FILE),new File(stage,"new_"+FILE),
            expected.contains("files/"+FILE));
        if(!pending.delete())throw new IOException("No se pudo cerrar la recuperación pendiente.");
        deleteStage(stage);
    }
    private static void rollback(File target,File previous,File staged,boolean expected) throws IOException {
        if(previous.isFile()){
            if(target.exists()&&!target.delete())throw new IOException("No se pudo retirar una restauración incompleta.");
            if(!previous.renameTo(target))throw new IOException("No se pudo recuperar la biblioteca anterior.");
        }else if(expected&&!staged.exists()&&target.exists()&&target.isFile()){
            // A new file was installed where none existed previously.
            if(!target.delete())throw new IOException("No se pudo deshacer la restauración.");
        }
    }
    private static void moveIfPresent(File from,File to) throws IOException {
        if(from.exists()&&!from.renameTo(to))throw new IOException("No se pudo proteger "+from.getName());
    }
    static void discardRestore(RestorePlan plan){if(plan!=null)deleteStage(plan.stage);}

    private static void writeEntry(ZipOutputStream zip,String name,String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));zip.write(value.getBytes("UTF-8"));zip.closeEntry();
    }
    private static String readText(ZipFile zip,ZipEntry e) throws Exception {
        if(e.getSize()>100_000||e.getSize()<0)throw new IOException("Metadatos de copia no válidos.");
        try(InputStream in=zip.getInputStream(e);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            copy(in,out,100_000);return out.toString("UTF-8");
        }
    }
    private static void copy(InputStream in,OutputStream out,long limit) throws IOException {
        byte[] buffer=new byte[32768];long count=0;int n;
        while((n=in.read(buffer))!=-1){
            count+=n;if(count>limit)throw new IOException("El archivo supera el límite de seguridad.");
            out.write(buffer,0,n);
        }
    }
    private static String sha(File file) throws Exception {
        try(InputStream in=new FileInputStream(file)){
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[32768];int n;
            while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);
            return hex(digest.digest());
        }
    }
    private static String sha(byte[] bytes) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static String hex(byte[] bytes){
        StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
    private static void deleteStage(File file){
        if(file==null||!file.exists())return;
        if(file.isDirectory()){
            File[] children=file.listFiles();if(children!=null)for(File child:children)deleteStage(child);
        }
        if(!file.delete())Log.w(TAG,"No se pudo limpiar el temporal: "+file.getName());
    }
}
