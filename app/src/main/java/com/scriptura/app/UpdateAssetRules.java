package com.scriptura.app;

import java.util.Locale;

/** Validación estricta de metadatos publicados por GitHub, sin dependencias Android. */
public final class UpdateAssetRules {
    public static final long MAX_BYTES=150L*1024*1024;
    private UpdateAssetRules(){}
    public static boolean valid(String tag,long id,long size,String name,
                                String digest,int versionCode){
        ReleaseVersions v=ReleaseVersions.parse(tag);
        return tag!=null&&tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+-build\\.[0-9]+")
            &&v!=null&&v.build>0&&v.build==versionCode
            &&id>0&&size>=4096&&size<=MAX_BYTES
            &&("Scriptura-"+tag.substring(1)+".apk").equals(name)
            &&digest!=null&&digest.matches("sha256:[0-9a-fA-F]{64}");
    }
    public static String digestHex(String digest){
        if(digest==null||!digest.matches("sha256:[0-9a-fA-F]{64}"))
            throw new IllegalArgumentException("Huella SHA-256 ausente");
        return digest.substring(7).toLowerCase(Locale.ROOT);
    }
    /** La URL de descarga autenticada se construye solo desde el ID validado. */
    public static String assetApiUrl(String repository,long id){
        if(!"JavierRB35/Scriptura".equals(repository)||id<=0)
            throw new IllegalArgumentException("Origen de actualización no autorizado");
        return "https://api.github.com/repos/"+repository+"/releases/assets/"+id;
    }
}