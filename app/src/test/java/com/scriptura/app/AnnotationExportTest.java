package com.scriptura.app;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class AnnotationExportTest {
    private StudyAnnotations.Item item(long id,String book,int chapter,int verse,
                                       String quote,String kind,String body,String url){
        return new StudyAnnotations.Item(id,book,chapter,verse,0,quote,kind,body,url,1L);
    }

    @Test public void groupsByBookAndChapter(){
        List<StudyAnnotations.Item> items=new ArrayList<>();
        items.add(item(1,"Gn",1,1,"En el principio",StudyAnnotations.NOTE,"Texto de la nota",""));
        items.add(item(2,"Gn",1,3,"Sea la luz",StudyAnnotations.UNDERLINE,"",""));
        items.add(item(3,"Jn",1,1,"el Verbo",StudyAnnotations.WEB,"","https://example.org"));
        String out=AnnotationExport.build(items);
        assertTrue(out.contains("== Génesis =="));
        assertTrue(out.contains("== Juan =="));
        assertTrue(out.indexOf("Génesis 1")<out.indexOf("== Juan =="));
        assertTrue(out.contains("· v.1 · Nota"));
        assertTrue(out.contains("«En el principio»"));
        assertTrue(out.contains("Texto de la nota"));
        assertTrue(out.contains("· v.3 · Subrayado"));
        assertTrue(out.contains("Enlace: https://example.org"));
    }

    @Test public void emptyListProducesHeaderOnly(){
        String out=AnnotationExport.build(new ArrayList<>());
        assertTrue(out.startsWith("Subrayados y notas de Scriptura"));
        assertFalse(out.contains("· v."));
    }

    @Test public void multilineBodyIsIndented(){
        List<StudyAnnotations.Item> items=new ArrayList<>();
        items.add(item(1,"Sal",23,1,"",StudyAnnotations.NOTE,"Primera línea\nSegunda línea",""));
        String out=AnnotationExport.build(items);
        assertTrue(out.contains("\n  Primera línea\n  Segunda línea\n"));
    }

    @Test public void kindLabelsAreHuman(){
        assertEquals("Subrayado",AnnotationExport.kindLabel(StudyAnnotations.UNDERLINE));
        assertEquals("Nota",AnnotationExport.kindLabel(StudyAnnotations.NOTE));
        assertEquals("Mapa externo",AnnotationExport.kindLabel(StudyAnnotations.MAP));
        assertEquals("Página web",AnnotationExport.kindLabel(StudyAnnotations.WEB));
        assertEquals("Atlas de Scriptura",AnnotationExport.kindLabel(StudyAnnotations.ATLAS));
    }
}
