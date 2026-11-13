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

    @Test public void exportPreservesMultiVerseReferenceAndLegacySingleVerse(){
        List<StudyAnnotations.Item> items=new ArrayList<>();
        items.add(new StudyAnnotations.Item(1,"Gn",1,1,3,0,
            "En el principio\\n\\n2  La tierra",StudyAnnotations.NOTE,"Una nota","",1L));
        items.add(item(2,"Gn",1,3,"Sea la luz",StudyAnnotations.UNDERLINE,"",""));
        String out=AnnotationExport.build(items);
        assertTrue(out.contains("· v.1–3 · Nota"));
        assertTrue(out.contains("· v.3 · Subrayado"));
        assertFalse(out.contains("· v.3–3"));
    }

    @Test public void aiInterpretationExportPreservesFullAnswerAndExactReference(){
        List<StudyAnnotations.Item> items=new ArrayList<>();
        items.add(new StudyAnnotations.Item(7,"Jn",3,16,17,4,
            "tanto amó Dios al mundo",StudyAnnotations.AI,
            "Primera línea de la interpretación.\nSegunda línea.","",7L));
        String exported=AnnotationExport.build(items);
        assertTrue(exported.contains("· v.16–17 · Interpretación IA"));
        assertTrue(exported.contains("«tanto amó Dios al mundo»"));
        assertTrue(exported.contains("Primera línea de la interpretación.\n  Segunda línea."));
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
        assertEquals("Interpretación IA",AnnotationExport.kindLabel(StudyAnnotations.AI));
    }
}
