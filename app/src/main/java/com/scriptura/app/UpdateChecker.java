package com.scriptura.app;

import android.Manifest;
import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Buscador de versiones en Releases públicas, con acceso privado opcional.
 * Comprueba versiones, pero la descarga y la instalación se inician solo tras pulsar Actualizar.
 * Nunca incluye un GitHub token en el código ni permite instalación silenciosa.
 */
public final class UpdateChecker {
    public static final String REPO="JavierRB35/Scriptura";
    private static final String PREFS="scriptura_update_preferences";
    private static final String CHANNEL="scriptura_app_updates_action_v2";
    private static final String KEY_ALIAS="scriptura_github_read_only_v1";
    private static final String API="https://api.github.com/repos/"+REPO+"/releases?per_page=25";
    private static final String RELEASE_PREFIX="https://github.com/"+REPO+"/releases/tag/";
    private static final int JOB_ID=726104;
    private static final long CHECK_EVERY_MS=12L*60*60*1000;
    private static final long FOREGROUND_THROTTLE_MS=5L*60*1000;

    private UpdateChecker(){}
    private static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    public static final class Update {
        public final String tag,title,url,assetName,sha256;
        public final long assetId,assetSize;
        public final int versionCode;
        public Update(String tag,String title,String url,long assetId,
                      long assetSize,String assetName,String sha256,int versionCode){
            this.tag=tag;this.title=title;this.url=url;this.assetId=assetId;
            this.assetSize=assetSize;this.assetName=assetName;this.sha256=sha256;
            this.versionCode=versionCode;
        }
    }
    public static final class Result {
        public final Update update;
        public final String info;
        public Result(Update update,String info){this.update=update;this.info=info;}
        public boolean available(){return update!=null;}
    }

    private static SecretKey secret() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(KEY_ALIAS))
            return (SecretKey)ks.getKey(KEY_ALIAS,null);
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return gen.generateKey();
    }
    public static boolean configured(Context c){
        return !prefs(c).getString("private_access_token","").isEmpty();
    }
    /**
     * La clave GitHub debe ser Fine-grained, restringida a Scriptura,
     * Contents read-only, con caducidad y posibilidad de revocación.
     */
    public static void saveToken(Context c,String entered) throws Exception {
        String token=entered==null?"":entered.trim();
        if(token.isEmpty()){clearToken(c);return;}
        if(token.length()<20||token.length()>300||!token.matches("[a-zA-Z0-9_-]+"))
            throw new IllegalArgumentException("La credencial de GitHub no tiene un formato válido");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,secret());
        String value=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+"."+
                Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        prefs(c).edit().putString("private_access_token",value).remove("last_error")
                .remove("last_checked").apply();
        schedule(c);
    }
    public static void clearToken(Context c){
        prefs(c).edit().remove("private_access_token").remove("last_checked")
                .remove("last_error").remove("latest_version")
                .remove("latest_title").remove("latest_url")
                .remove("latest_asset_id").remove("latest_asset_name")
                .remove("latest_asset_size").remove("latest_asset_sha256")
                .remove("latest_version_code").remove("last_prompted")
                .remove("last_notified").apply();
        // Eliminar un token no desactiva los avisos del repositorio público.
        schedule(c);
    }
    static String downloadToken(Context c) throws Exception{
        return readToken(c);
    }
    private static String readToken(Context c) throws Exception{
        String data=prefs(c).getString("private_access_token","");
        if(data.isEmpty())return "";
        String[] parts=data.split("\\.",-1);
        if(parts.length!=2)throw new IllegalStateException("Credencial cifrada dañada. Guárdala nuevamente.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,secret(),
                new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    public static void schedule(Context c){
        JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler==null)return;
        JobInfo job=new JobInfo.Builder(JOB_ID,new ComponentName(c,UpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(CHECK_EVERY_MS)
                .build();
        try{
            if(scheduler.schedule(job)==JobScheduler.RESULT_FAILURE)
                fail(c,"Las comprobaciones en segundo plano no se pudieron programar. La comprobación manual sigue disponible.");
        }catch(RuntimeException problem){
            // Guardar el token nunca debe fallar por un problema del programador
            // de tareas (Android 14, límites de trabajos o fabricante).
            android.util.Log.w("ScripturaUpdates","No se pudo programar el aviso: "+problem.getClass().getSimpleName());
            fail(c,"Android ha rechazado la programación de avisos. Puedes comprobar las actualizaciones manualmente.");
        }
    }
    private static String currentVersion(Context c){
        try{
            android.content.pm.PackageInfo i=c.getPackageManager().getPackageInfo(c.getPackageName(),0);
            return i.versionName==null?"":i.versionName;
        }catch(Exception err){return "";}
    }
    private static Update remembered(Context c){
        SharedPreferences p=prefs(c);
        String tag=p.getString("latest_version","");
        String url=p.getString("latest_url","");
        String title=p.getString("latest_title","");
        long id=p.getLong("latest_asset_id",0),size=p.getLong("latest_asset_size",0);
        int code=p.getInt("latest_version_code",0);
        String name=p.getString("latest_asset_name","");
        String sha=p.getString("latest_asset_sha256","");
        if(tag.isEmpty()||!url.equals(RELEASE_PREFIX+tag))return null;
        if(!ReleaseVersions.isNewer(tag,currentVersion(c)))return null;
        if(!UpdateAssetRules.valid(tag,id,size,name,sha,code))return null;
        return new Update(tag,title,url,id,size,name,sha,code);
    }
    public static Update available(Context c){
        return remembered(c);
    }
    public static String lastError(Context c){
        return prefs(c).getString("last_error","");
    }
    private static void fail(Context c,String message){
        prefs(c).edit().putString("last_error",message).putLong("last_checked",
                System.currentTimeMillis()).apply();
    }
    private static HttpURLConnection openReleasesConnection(String token) throws Exception {
        HttpURLConnection connection=(HttpURLConnection)new URL(API).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
        connection.setRequestProperty("User-Agent","Scriptura-Android-Update-Checker");
        if(token!=null&&!token.isEmpty())
            connection.setRequestProperty("Authorization","Bearer "+token);
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(16000);
        return connection;
    }
    /**
     * Invocar EXCLUSIVAMENTE fuera del hilo principal.
     * Consultar anónimamente primero; solo intentar el token privado en caso de acceso denegado.
     */
    public static synchronized Result check(Context c,boolean force){
        SharedPreferences p=prefs(c);
        long now=System.currentTimeMillis();
        if(!force && now-p.getLong("last_checked",0)<FOREGROUND_THROTTLE_MS){
            Update previous=remembered(c);
            return new Result(previous,previous!=null?"Versión nueva disponible":
                    p.getString("last_error","No hay versiones nuevas"));
        }
        HttpURLConnection conn=null;
        try{
            conn=openReleasesConnection("");
            int code=conn.getResponseCode();
            if((code==403||code==404)&&configured(c)){
                conn.disconnect();
                conn=openReleasesConnection(readToken(c));
                code=conn.getResponseCode();
            }
            if(code!=200){
                String message=(code==401||code==403) ?
                    "GitHub ha limitado la consulta o rechazado el acceso privado (HTTP "+code+")." :
                    code==404 ? "No se encuentran las versiones de Scriptura en GitHub (HTTP 404)." :
                    code==429 ? "Demasiadas consultas a GitHub; inténtalo más tarde." :
                    "GitHub no responde correctamente (HTTP "+code+").";
                fail(c,message);return new Result(remembered(c),message);
            }
            String body;
            try(InputStream in=conn.getInputStream();
                ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){
                    out.write(buffer,0,n);
                    if(out.size()>1_000_000)throw new IllegalStateException("Respuesta de GitHub demasiado grande");
                }
                body=out.toString("UTF-8");
            }
            JSONArray releases=new JSONArray(body);
            ReleaseVersions installed=ReleaseVersions.parse(currentVersion(c));
            if(installed==null)throw new IllegalStateException("No se reconoce la versión instalada");
            ReleaseVersions bestVersion=null;
            Update best=null;
            for(int n=0;n<releases.length();n++){
                JSONObject release=releases.optJSONObject(n);
                if(release==null||release.optBoolean("draft",false))continue;
                String tag=release.optString("tag_name","");
                ReleaseVersions candidate=ReleaseVersions.parse(tag);
                if(candidate==null||candidate.compareTo(installed)<=0)continue;
                if(bestVersion!=null&&candidate.compareTo(bestVersion)<=0)continue;
                String url=release.optString("html_url","");
                if(!url.equals(RELEASE_PREFIX+tag))continue;
                JSONArray assets=release.optJSONArray("assets");
                if(assets==null)continue;
                Update candidateUpdate=null;
                for(int j=0;j<assets.length();j++){
                    JSONObject asset=assets.optJSONObject(j);
                    if(asset==null||!"uploaded".equals(asset.optString("state","uploaded")))continue;
                    long id=asset.optLong("id",0),size=asset.optLong("size",0);
                    String name=asset.optString("name","");
                    String hash=asset.optString("digest","");
                    int buildCode=candidate.build>Integer.MAX_VALUE?0:(int)candidate.build;
                    if(!UpdateAssetRules.valid(tag,id,size,name,hash,buildCode))continue;
                    String title=release.optString("name",tag);
                    candidateUpdate=new Update(tag,title.isEmpty()?tag:title,url,
                        id,size,name,hash,buildCode);
                    break;
                }
                if(candidateUpdate==null)continue;
                bestVersion=candidate;
                best=candidateUpdate;
            }
            SharedPreferences.Editor edit=p.edit().putLong("last_checked",now)
                .remove("last_error");
            if(best==null){
                edit.remove("latest_version").remove("latest_title").remove("latest_url")
                    .remove("latest_asset_id").remove("latest_asset_name")
                    .remove("latest_asset_size").remove("latest_asset_sha256")
                    .remove("latest_version_code");
            }else {
                edit.putString("latest_version",best.tag)
                    .putString("latest_title",best.title)
                    .putString("latest_url",best.url)
                    .putLong("latest_asset_id",best.assetId)
                    .putLong("latest_asset_size",best.assetSize)
                    .putString("latest_asset_name",best.assetName)
                    .putString("latest_asset_sha256",best.sha256)
                    .putInt("latest_version_code",best.versionCode);
            }
            edit.apply();
            return new Result(best,best==null?"La aplicación está actualizada.":"Nueva versión: "+best.tag);
        }catch(Exception ex){
            // Nunca registrar ni mostrar la credencial ni el cuerpo de respuesta de GitHub.
            String info="No se pudo comprobar GitHub. Revisa la conexión y vuelve a intentarlo.";
            fail(c,info);
            return new Result(remembered(c),info);
        }finally{
            if(conn!=null)conn.disconnect();
        }
    }
    public static boolean shouldPrompt(Context c,Update update){
        return update!=null&&!update.tag.equals(prefs(c).getString("last_prompted",""));
    }
    public static void markPrompted(Context c,Update update){
        if(update!=null)prefs(c).edit().putString("last_prompted",update.tag).apply();
    }
    public static void maybeNotify(Context c,Update update){
        if(update==null)return;
        if(Build.VERSION.SDK_INT>=33 &&
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            return;
        SharedPreferences p=prefs(c);
        if(update.tag.equals(p.getString("last_notified","")))return;
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager==null)return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Actualizaciones de Scriptura",NotificationManager.IMPORTANCE_HIGH));
        Intent open=new Intent(c,MainActivity.class);
        open.setAction(UpdateInstaller.ACTION_UPDATE);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(c,7261,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notice=new Notification.Builder(c,CHANNEL)
                .setSmallIcon(R.drawable.ic_scriptura)
                .setContentTitle("Nueva versión de Scriptura")
                .setContentText("Scriptura "+update.tag+" · Actualizar desde la app")
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentIntent(pending)
                .addAction(new Notification.Action.Builder(
                    R.drawable.ic_scriptura,"Actualizar",pending).build())
                .setAutoCancel(true).build();
        manager.notify(7261,notice);
        p.edit().putString("last_notified",update.tag).apply();
    }
    public static void requestNotificationPermission(Activity activity){
        if(Build.VERSION.SDK_INT>=33 &&
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    !=PackageManager.PERMISSION_GRANTED){
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7261);
        }
    }
    /** Fallback manual únicamente para errores persistentes; el flujo principal no sale de Scriptura. */
    public static void openRelease(Context c,Update update){
        if(update==null||!update.url.startsWith(RELEASE_PREFIX))return;
        c.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(update.url)));
    }
}
