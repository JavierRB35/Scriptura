package com.scriptura.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Descarga privada bajo petición y abre el instalador del sistema.
 * No hay instalación silenciosa, no se expone la clave de GitHub ni se toca la Biblia.
 */
public final class UpdateInstaller {
    public static final String ACTION_UPDATE="com.scriptura.app.action.UPDATE_NOW";
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private final Activity activity;
    private boolean waitingForPermission;
    private UpdateChecker.Update pendingPermission;

    public UpdateInstaller(Activity activity){this.activity=activity;}

    public void start(UpdateChecker.Update update){
        if(update==null){message("No hay una versión nueva disponible.");return;}
        if(!UpdateAssetRules.valid(update.tag,update.assetId,update.assetSize,
                                  update.assetName,update.sha256,update.versionCode)){
            message("La publicación carece de datos verificables. No se descargará.");
            return;
        }
        if(!ReleaseVersions.isNewer(update.tag,installedName())){
            message("Ya tienes una versión igual o más reciente.");return;
        }
        if(!BUSY.compareAndSet(false,true))return;
        AlertDialog progress=new AlertDialog.Builder(activity)
            .setTitle("Actualizando Scriptura")
            .setMessage("Descargando y verificando la APK firmada. No cierres esta pantalla.")
            .setCancelable(false).create();
        progress.show();
        new Thread(()->{
            File partial=null;
            try{
                File folder=new File(activity.getCacheDir(),"updates");
                if(!folder.isDirectory()&&!folder.mkdirs())
                    throw new IllegalStateException("No se puede preparar el almacenamiento privado.");
                partial=new File(folder,"scriptura-update.partial.apk");
                File ready=new File(folder,UpdateApkProvider.FILE_NAME);
                if(ready.exists()&&!ready.delete())
                    throw new IllegalStateException("No se puede eliminar la descarga anterior.");
                // El token solo viaja a api.github.com. Nunca a la URL de redirección.
                downloadAsset(update,partial,activity);
                verifyApk(partial,update);
                if(!partial.renameTo(ready))
                    throw new IllegalStateException("No se puede conservar la descarga verificada.");
                activity.runOnUiThread(()->{
                    progress.dismiss();
                    if(!activity.isFinishing()&&!activity.isDestroyed())
                        installVerified(update);
                });
            }catch(Exception e){
                // Nunca mostrar la excepción completa ni registrar URLs firmadas/tokens.
                activity.runOnUiThread(()->{
                    progress.dismiss();
                    if(!activity.isFinishing()&&!activity.isDestroyed())
                        message("No se ha instalado la actualización. La descarga o la verificación ha fallado. Revisa la conexión y el acceso privado de GitHub.");
                });
            }finally{
                if(partial!=null&&partial.exists())partial.delete();
                BUSY.set(false);
            }
        },"scriptura-private-apk-download").start();
    }

    private String installedName(){
        try{
            PackageInfo p=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
            return p.versionName==null?"":p.versionName;
        }catch(Exception e){return "";}
    }

    /** Las APK públicas se descargan sin token; acceso privado solo como respaldo. */
    private static HttpURLConnection connectDownload(UpdateChecker.Update update,Activity activity)
            throws Exception{
        try{
            return connectDownloadOnce(update,null);
        }catch(Exception publicError){
            if(!UpdateChecker.configured(activity))throw publicError;
            return connectDownloadOnce(update,UpdateChecker.downloadToken(activity));
        }
    }
    /** No se permite que la credencial llegue a GitHub Releases ni a sus redirecciones. */
    private static HttpURLConnection connectDownloadOnce(UpdateChecker.Update update,String token)
            throws Exception{
        boolean privateAccess=token!=null&&!token.isEmpty();
        URL url=new URL(privateAccess?
            UpdateAssetRules.assetApiUrl(UpdateChecker.REPO,update.assetId):
            UpdateAssetRules.publicDownloadUrl(UpdateChecker.REPO,update.tag,update.assetName));
        for(int hops=0;hops<4;hops++){
            if(!"https".equalsIgnoreCase(url.getProtocol())||url.getUserInfo()!=null||
                (url.getPort()!=-1&&url.getPort()!=443))
                throw new IllegalStateException("URL de descarga no autorizada");
            String host=url.getHost().toLowerCase(Locale.ROOT);
            boolean first=(hops==0);
            if(first?(privateAccess?!host.equals("api.github.com"):!host.equals("github.com")):
                !(host.equals("github.com")||host.endsWith(".githubusercontent.com")))
                throw new IllegalStateException("Servidor de descarga no autorizado");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","Scriptura-Android-Updater");
            if(first&&privateAccess){
                connection.setRequestProperty("Authorization","Bearer "+token);
                connection.setRequestProperty("Accept","application/octet-stream");
                connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
            }
            int code=connection.getResponseCode();
            if(code==200){
                String type=connection.getContentType();
                if(type!=null&&type.toLowerCase(Locale.ROOT).contains("json")){
                    connection.disconnect();
                    throw new IllegalStateException("GitHub devolvió metadatos, no una APK");
                }
                return connection;
            }
            if(code==302||code==301||code==307||code==308){
                String location=connection.getHeaderField("Location");
                connection.disconnect();
                if(location==null||location.isEmpty())
                    throw new IllegalStateException("Falta la URL de descarga");
                url=new URL(url,location);
                continue;
            }
            connection.disconnect();
            throw new IllegalStateException("GitHub devolvió HTTP "+code);
        }
        throw new IllegalStateException("Demasiadas redirecciones");
    }

    private static void downloadAsset(UpdateChecker.Update update,File partial,Activity activity)
            throws Exception{
        HttpURLConnection connection=connectDownload(update,activity);
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long count=0;
            try(InputStream input=connection.getInputStream();
                FileOutputStream output=new FileOutputStream(partial)){
                byte[] block=new byte[32768];int n;
                while((n=input.read(block))!=-1){
                    if(Thread.currentThread().isInterrupted())
                        throw new InterruptedException("Descarga cancelada");
                    count+=n;
                    if(count>update.assetSize||count>UpdateAssetRules.MAX_BYTES)
                        throw new IllegalStateException("APK demasiado grande");
                    output.write(block,0,n);
                    digest.update(block,0,n);
                }
                output.getFD().sync();
            }
            if(count!=update.assetSize)
                throw new IllegalStateException("Tamaño del APK incorrecto");
            StringBuilder value=new StringBuilder(64);
            for(byte b:digest.digest())value.append(String.format(Locale.ROOT,"%02x",b&255));
            if(!value.toString().equals(UpdateAssetRules.digestHex(update.sha256)))
                throw new IllegalStateException("Huella SHA-256 incorrecta");
        }finally{connection.disconnect();}
    }

    @SuppressWarnings("deprecation")
    private static Signature[] signatures(PackageInfo info){
        if(info==null)return null;
        if(Build.VERSION.SDK_INT>=28){
            if(info.signingInfo==null)return null;
            return info.signingInfo.getApkContentsSigners();
        }
        return info.signatures;
    }
    private static int signatureFlags(){
        return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:
            PackageManager.GET_SIGNATURES;
    }
    private void verifyApk(File apk,UpdateChecker.Update update) throws Exception{
        PackageManager pm=activity.getPackageManager();
        PackageInfo installed=pm.getPackageInfo(activity.getPackageName(),signatureFlags());
        PackageInfo downloaded=pm.getPackageArchiveInfo(apk.getAbsolutePath(),signatureFlags());
        if(downloaded==null||!activity.getPackageName().equals(downloaded.packageName))
            throw new IllegalStateException("APK de otra aplicación");
        long version=Build.VERSION.SDK_INT>=28?downloaded.getLongVersionCode():downloaded.versionCode;
        long current=Build.VERSION.SDK_INT>=28?installed.getLongVersionCode():installed.versionCode;
        if(version!=update.versionCode||version<=current)
            throw new IllegalStateException("Versión incompatible");
        Signature[] original=signatures(installed),candidate=signatures(downloaded);
        if(original==null||candidate==null||original.length!=1||candidate.length!=1||
            !MessageDigest.isEqual(original[0].toByteArray(),candidate[0].toByteArray()))
            throw new IllegalStateException("El certificado de firma no coincide");
    }
    private File readyFile(){
        return new File(new File(activity.getCacheDir(),"updates"),UpdateApkProvider.FILE_NAME);
    }
    private void installVerified(UpdateChecker.Update update){
        if(!readyFile().isFile())return;
        try{verifyApk(readyFile(),update);}
        catch(Exception e){message("No se puede instalar: la APK no pasa la verificación.");return;}
        if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){
            waitingForPermission=true;
            pendingPermission=update;
            new AlertDialog.Builder(activity)
                .setTitle("Permitir actualizar Scriptura")
                .setMessage("Solo la primera vez: Android necesita autorizar a Scriptura para iniciar su instalador. Después tendrás que confirmar la actualización.")
                .setNegativeButton("Cancelar",(d,w)->{
                    waitingForPermission=false;pendingPermission=null;
                })
                .setPositiveButton("Abrir ajustes",(d,w)->{
                    try{
                        Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:"+activity.getPackageName()));
                        activity.startActivity(settings);
                    }catch(Exception error){
                        waitingForPermission=false;pendingPermission=null;
                        message("No se pudieron abrir los ajustes de instalación.");
                    }
                }).show();
            return;
        }
        waitingForPermission=false;
        pendingPermission=null;
        Uri uri=UpdateApkProvider.fileUri(activity);
        Intent installer=new Intent(Intent.ACTION_VIEW);
        installer.setDataAndType(uri,"application/vnd.android.package-archive");
        installer.setClipData(ClipData.newRawUri("Scriptura APK",uri));
        installer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{activity.startActivity(installer);}
        catch(Exception e){message("Android no ha podido abrir el instalador de APK.");}
    }
    public void onResume(){
        if(waitingForPermission&&pendingPermission!=null&&Build.VERSION.SDK_INT>=26&&
            activity.getPackageManager().canRequestPackageInstalls())
            installVerified(pendingPermission);
    }
    private void message(String body){
        if(activity.isFinishing()||activity.isDestroyed())return;
        new AlertDialog.Builder(activity).setTitle("Actualizaciones de Scriptura")
            .setMessage(body).setPositiveButton("Entendido",null).show();
    }
}
