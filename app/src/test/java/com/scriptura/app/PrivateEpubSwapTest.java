package com.scriptura.app;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class PrivateEpubSwapTest {
    @Test public void replacesAndConfirmsWithoutLeavingBackup() throws Exception {
        File dir=Files.createTempDirectory("scriptura-epub").toFile();
        File old=new File(dir,"biblia-usuario.epub");
        File newer=new File(dir,"scriptura-import.tmp");
        Files.write(old.toPath(),"ORIGINAL".getBytes(StandardCharsets.UTF_8));
        Files.write(newer.toPath(),"NUEVA".getBytes(StandardCharsets.UTF_8));
        PrivateEpubSwap tx=new PrivateEpubSwap(old);
        tx.replace(newer);
        assertEquals("NUEVA",read(old));
        assertTrue(new File(dir,"biblia-usuario.epub.backup").exists());
        tx.finish();
        assertEquals("NUEVA",read(old));
        assertFalse(new File(dir,"biblia-usuario.epub.backup").exists());
    }
    @Test public void restoresPreviousFileWhenDbTransactionFails() throws Exception {
        File dir=Files.createTempDirectory("scriptura-epub-rollback").toFile();
        File old=new File(dir,"biblia-usuario.epub");
        File newer=new File(dir,"scriptura-import.tmp");
        Files.write(old.toPath(),"ORIGINAL".getBytes(StandardCharsets.UTF_8));
        Files.write(newer.toPath(),"NUEVA".getBytes(StandardCharsets.UTF_8));
        PrivateEpubSwap tx=new PrivateEpubSwap(old);
        tx.replace(newer);
        tx.rollback();
        assertEquals("ORIGINAL",read(old));
        assertFalse(new File(dir,"biblia-usuario.epub.backup").exists());
    }
    @Test public void neverOverwritesPendingBackup() throws Exception {
        File dir=Files.createTempDirectory("scriptura-epub-safe").toFile();
        File old=new File(dir,"biblia-usuario.epub");
        File backup=new File(dir,"biblia-usuario.epub.backup");
        File newer=new File(dir,"scriptura-import.tmp");
        Files.write(old.toPath(),"ORIGINAL".getBytes(StandardCharsets.UTF_8));
        Files.write(backup.toPath(),"RESPALDO".getBytes(StandardCharsets.UTF_8));
        Files.write(newer.toPath(),"NUEVA".getBytes(StandardCharsets.UTF_8));
        try {new PrivateEpubSwap(old).replace(newer);fail("Debe rechazar respaldo pendiente");}
        catch(IOException expected){ /* No tocar archivos existentes. */ }
        assertEquals("ORIGINAL",read(old));
        assertEquals("RESPALDO",read(backup));
        assertEquals("NUEVA",read(newer));
    }
    @Test public void rejectsMissingStagedFile() throws Exception {
        File dir=Files.createTempDirectory("scriptura-epub-missing").toFile();
        File old=new File(dir,"biblia-usuario.epub");
        Files.write(old.toPath(),"ORIGINAL".getBytes(StandardCharsets.UTF_8));
        try {new PrivateEpubSwap(old).replace(new File(dir,"missing.epub"));fail();}
        catch(IOException expected){}
        assertEquals("ORIGINAL",read(old));
    }
    private String read(File f)throws IOException{
        return new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);
    }
}
