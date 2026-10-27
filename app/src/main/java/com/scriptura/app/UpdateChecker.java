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
 * Buscador de versiones en los RELEASES publicados del repositorio privado.
 * NO instala APK automáticamente, NO incluye un GitHub token en el código.
 */
public final class UpdateChecker {
    public static final String REPO="JavierRB35/Scriptura";
    private static final String PREFS="scriptura_update_preferences";
    private static final String CHANNEL="scriptura_app_updates";
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
        public final String tag,title,url;
        public Update(String t,String name,String u){tag=t;title=name;url=u;}
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
                .remove("last_notified").apply();
        JobScheduler jobs=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobs!=null)jobs.cancel(JOB_ID);
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
        if(!configured(c))return;
        JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(scheduler==null)return;
        JobInfo job=new JobInfo.Builder(JOB_ID,new ComponentName(c,UpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(CHECK_EVERY_MS)
                .build();
        scheduler.schedule(job);
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
        if(tag.isEmpty()||!url.startsWith(RELEASE_PREFIX))return null;
        if(!ReleaseVersions.isNewer(tag,currentVersion(c)))return null;
        return new Update(tag,title,url);
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
    /**
     * Invocar EXCLUSIVAMENTE fuera del hilo principal.
     * force=true omite la ventana de cinco minutos usada para evitar consultas repetidas.
     */
    public static Result check(Context c,boolean force){
        if(!configured(c))return new Result(null,
            "Para comprobar versiones del repositorio privado, configura tu acceso de solo lectura en Ajustes.");
        SharedPreferences p=prefs(c);
        long now=System.currentTimeMillis();
        if(!force && now-p.getLong("last_checked",0)<FOREGROUND_THROTTLE_MS){
            Update previous=remembered(c);
            return new Result(previous,previous!=null?"Versión nueva disponible":
                    p.getString("last_error","No hay versiones nuevas"));
        }
        HttpURLConnection conn=null;
        try{
            String token=readToken(c);
            URL endpoint=new URL(API);
            conn=(HttpURLConnection)endpoint.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Accept","application/vnd.github+json");
            conn.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
            conn.setRequestProperty("Authorization","Bearer "+token);
            conn.setRequestProperty("User-Agent","Scriptura-Android-Update-Checker");
            conn.setConnectTimeout(12000);conn.setReadTimeout(16000);
            int code=conn.getResponseCode();
            if(code!=200){
                String message=(code==401||code==403) ?
                    "GitHub ha rechazado la credencial o su cuota. Comprueba que el token siga vigente y tenga Contents: Read." :
                    code==404 ? "Sin acceso a Scriptura: selecciona el repositorio y permiso Contents: Read en tu token." :
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
                boolean hasApk=false;
                for(int j=0;j<assets.length();j++){
                    JSONObject asset=assets.optJSONObject(j);
                    if(asset!=null&&asset.optString("name","").matches("Scriptura-[a-zA-Z0-9.\\-]+\\.apk")
                        &&asset.optLong("size",0)>0){
                        hasApk=true;break;
                    }
                }
                if(!hasApk)continue;
                bestVersion=candidate;
                String title=release.optString("name",tag);
                best=new Update(tag,title.isEmpty()?tag:title,url);
            }
            SharedPreferences.Editor edit=p.edit().putLong("last_checked",now)
                .remove("last_error");
            if(best==null){
                edit.remove("latest_version").remove("latest_title").remove("latest_url");
            }else {
                edit.putString("latest_version",best.tag)
                    .putString("latest_title",best.title)
                    .putString("latest_url",best.url);
            }
            edit.apply();
            return new Result(best,best==null?"La aplicación está actualizada.":"Nueva versión: "+best.tag);
        }catch(Exception ex){
            // Nunca registrar ni mostrar la credencial ni el cuerpo de respuesta de GitHub.
            String info="No se pudo comprobar GitHub. Revisa conexión y credencial.";
            fail(c,info);
            return new Result(remembered(c),info);
        }finally{
            if(conn!=null)conn.disconnect();
        }
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
                "Nuevas versiones de Scriptura",NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(Intent.ACTION_VIEW,Uri.parse(update.url));
        open.addCategory(Intent.CATEGORY_BROWSABLE);
        PendingIntent pending=PendingIntent.getActivity(c,7261,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notice=new Notification.Builder(c,CHANNEL)
                .setSmallIcon(R.drawable.ic_scriptura)
                .setContentTitle("Nueva versión de Scriptura")
                .setContentText("Versión "+update.tag+" disponible. Toca para descargarla.")
                .setContentIntent(pending).setAutoCancel(true).build();
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
    public static void openRelease(Context c,Update update){
        if(update==null||!update.url.startsWith(RELEASE_PREFIX))return;
        c.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(update.url)));
    }
}
