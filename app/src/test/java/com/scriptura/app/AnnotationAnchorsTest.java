package com.scriptura.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;

public class AnnotationAnchorsTest {
    private static final String CHAPTER="1  En el principio creó Dios el cielo.\n\n"+
        "2  La tierra estaba desordenada y vacía.\n\n"+
        "3  Dios dijo: Sea la luz.";

    @Test public void anchorsSelectionToVerseRatherThanScreenPage(){
        int start=CHAPTER.indexOf("desordenada");
        AnnotationAnchors.Anchor anchor=AnnotationAnchors.fromSelection(CHAPTER,start,start+11);
        assertNotNull(anchor);
        assertEquals(2,anchor.verse);
        assertEquals("desordenada",anchor.quote);
        AnnotationAnchors.Range range=AnnotationAnchors.inDisplay(CHAPTER,anchor.verse,
            anchor.offset,anchor.quote);
        assertNotNull(range);
        assertEquals(start,range.start);
        assertEquals(start+11,range.end);
    }
    @Test public void findsSameTextWhenDifferentVerseRangeDisplayed(){
        String verseOnly="2  La tierra estaba desordenada y vacía.";
        AnnotationAnchors.Anchor anchor=AnnotationAnchors.fromSelection(
            CHAPTER,CHAPTER.indexOf("vacía"),CHAPTER.indexOf("vacía")+5);
        AnnotationAnchors.Range range=AnnotationAnchors.inDisplay(verseOnly,
            anchor.verse,anchor.offset,anchor.quote);
        assertNotNull(range);
        assertEquals("vacía",verseOnly.substring(range.start,range.end));
    }
    @Test public void rejectsMultiVerseAndOutsideSelection(){
        assertNull(AnnotationAnchors.fromSelection(CHAPTER,-1,8));
        assertNull(AnnotationAnchors.fromSelection(CHAPTER,CHAPTER.indexOf("cielo."),
            CHAPTER.indexOf("La tierra")+3));
        assertNull(AnnotationAnchors.fromSelection(CHAPTER,0,1));
    }
    @Test public void visibleVersesAreEnumerated(){
        assertEquals(Arrays.asList(1,2,3),AnnotationAnchors.verseNumbers(CHAPTER));
    }
    @Test public void changedTextMustNotPaintTheWrongQuote(){
        assertNull(AnnotationAnchors.inDisplay(CHAPTER,2,0,"texto cambiado"));
        assertNull(AnnotationAnchors.inDisplay(CHAPTER,42,0,"Dios"));
    }
    @Test public void repeatedQuoteIsNotReanchoredAmbiguously(){
        String v="7  luz y luz y más luz";
        assertNull(AnnotationAnchors.inDisplay(v,7,999,"luz"));
        assertNotNull(AnnotationAnchors.inDisplay(v,7,0,"luz"));
    }
}
