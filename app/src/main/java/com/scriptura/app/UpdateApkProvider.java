package com.scriptura.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Concede al instalador una URI temporal de solo lectura, nunca acceso a la biblioteca. */
public final class UpdateApkProvider extends ContentProvider {
    static final String FILE_NAME="scriptura-update.apk";
    public static Uri fileUri(android.content.Context c){
        return Uri.parse("content://"+c.getPackageName()+".updates/"+FILE_NAME);
    }
    private File allowed(Uri uri) throws FileNotFoundException{
        if(getContext()==null || uri==null || !("/"+FILE_NAME).equals(uri.getPath()))
            throw new FileNotFoundException("Archivo no autorizado");
        File file=new File(new File(getContext().getCacheDir(),"updates"),FILE_NAME);
        if(!file.isFile())throw new FileNotFoundException("Actualización no descargada");
        return file;
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){
        return uri!=null&&("/"+FILE_NAME).equals(uri.getPath())
            ?"application/vnd.android.package-archive":null;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException("Solo lectura");
        return ParcelFileDescriptor.open(allowed(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,
                                  String[] args,String sort){
        try{
            File f=allowed(uri);
            String[] cols=projection==null?
                new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor cursor=new MatrixCursor(cols);
            Object[] values=new Object[cols.length];
            for(int i=0;i<cols.length;i++){
                if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))
                    values[i]="Scriptura-update.apk";
                if(OpenableColumns.SIZE.equals(cols[i]))values[i]=f.length();
            }
            cursor.addRow(values);
            return cursor;
        }catch(FileNotFoundException error){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
}