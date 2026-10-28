package com.scriptura.app;

import java.io.File;
import java.io.IOException;

/**
 * Sustitución reversible del EPUB del usuario dentro de la misma carpeta.
 * Solo se confirma cuando la transacción SQLite de versículos ha terminado.
 * No elimina primero el archivo anterior ni sobrescribe respaldos pendientes.
 */
final class PrivateEpubSwap {
    private final File permanent;
    private final File backup;
    private boolean previous;
    private boolean installed;

    PrivateEpubSwap(File permanent) {
        this.permanent=permanent;
        this.backup=new File(permanent.getParentFile(),permanent.getName()+".backup");
    }

    void replace(File staged) throws IOException {
        if(staged==null||!staged.isFile())
            throw new IOException("El EPUB temporal no existe; se conserva la biblioteca anterior.");
        if(backup.exists())
            throw new IOException("Existe una copia de seguridad pendiente del EPUB; no se sobrescribirá.");
        if(permanent.exists()){
            if(!permanent.isFile()||!permanent.renameTo(backup))
                throw new IOException("No se pudo proteger el EPUB anterior.");
            previous=true;
        }
        if(!staged.renameTo(permanent)){
            try{rollback();}
            catch(IOException restore){throw new IOException("Falló el cambio del EPUB y su recuperación.",restore);}
            throw new IOException("No se pudo guardar el nuevo EPUB. Se conserva la biblioteca anterior.");
        }
        installed=true;
    }

    void rollback() throws IOException {
        if(installed&&permanent.exists()&&!permanent.delete())
            throw new IOException("No se pudo retirar el EPUB temporal tras un error.");
        installed=false;
        if(previous&&backup.isFile()){
            if(!backup.renameTo(permanent))
                throw new IOException("No se pudo restaurar el EPUB anterior desde el respaldo.");
            previous=false;
        }
    }

    void finish() {
        // Una vez confirmada la transacción, ya no es necesaria la copia anterior.
        if(previous&&backup.isFile()&&!backup.delete())
            android.util.Log.w("Scriptura","No se pudo retirar un respaldo antiguo; se conserva para recuperación.");
        previous=false;
        installed=false;
    }
}
