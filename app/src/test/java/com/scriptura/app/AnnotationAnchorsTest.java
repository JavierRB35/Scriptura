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
    @Test public void anchorsTwoAndThreeVersesAndRestoresFullSelection(){
        int start=CHAPTER.indexOf("cielo.");
        int end=CHAPTER.indexOf("vacía.")+"vacía.".length();
        AnnotationAnchors.Anchor two=AnnotationAnchors.fromSelection(CHAPTER,start,end);
        assertNotNull(two);
        assertEquals(1,two.verse);
        assertEquals(2,two.endVerse);
        assertEquals(CHAPTER.substring(start,end),two.quote);
        AnnotationAnchors.Range twoRange=AnnotationAnchors.inDisplay(
            CHAPTER,two.verse,two.endVerse,two.offset,two.quote);
        assertNotNull(twoRange);
        assertEquals(start,twoRange.start);
        assertEquals(end,twoRange.end);

        int third=CHAPTER.length();
        AnnotationAnchors.Anchor three=AnnotationAnchors.fromSelection(CHAPTER,start,third);
        assertNotNull(three);
        assertEquals(3,three.endVerse);
        assertNotNull(AnnotationAnchors.inDisplay(
            CHAPTER,three.verse,three.endVerse,three.offset,three.quote));
    }
    @Test public void rejectsInvalidBoundariesAndWrongOrIncompleteText(){
        assertNull(AnnotationAnchors.fromSelection(CHAPTER,-1,8));
        assertNull(AnnotationAnchors.fromSelection(CHAPTER,0,1));
        assertNull(AnnotationAnchors.fromSelection(CHAPTER,0,CHAPTER.indexOf("La tierra")+3));
        // A selection ending inside a verse number label is not a valid anchored fragment.
        assertNull(AnnotationAnchors.fromSelection(CHAPTER,
            CHAPTER.indexOf("cielo."),CHAPTER.indexOf("2  ")+2));
        int start=CHAPTER.indexOf("cielo.");
        int end=CHAPTER.indexOf("vacía.")+"vacía.".length();
        AnnotationAnchors.Anchor anchor=AnnotationAnchors.fromSelection(CHAPTER,start,end);
        assertNotNull(anchor);
        assertNull(AnnotationAnchors.inDisplay(CHAPTER,1,3,anchor.offset,anchor.quote));
        assertNull(AnnotationAnchors.inDisplay(CHAPTER.replace("desordenada","distinta"),
            anchor.verse,anchor.endVerse,anchor.offset,anchor.quote));
        assertNull(AnnotationAnchors.inDisplay("1  En el principio creó Dios el cielo.",
            anchor.verse,anchor.endVerse,anchor.offset,anchor.quote));
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

    @Test public void returnsExactVerseStartWhenRestoringReading(){
        assertEquals(CHAPTER.indexOf("La tierra"),AnnotationAnchors.verseStart(CHAPTER,2));
        assertEquals(CHAPTER.indexOf("Dios dijo"),AnnotationAnchors.verseStart(CHAPTER,3));
        assertEquals(-1,AnnotationAnchors.verseStart(CHAPTER,42));
        assertEquals(-1,AnnotationAnchors.verseStart(null,1));
    }

    @Test public void underlineCanAnchorVerseTextWithoutPageCoordinates(){
        String fragment="2  La tierra estaba desordenada y vacía.";
        int start=fragment.indexOf("La tierra");
        AnnotationAnchors.Anchor underline=AnnotationAnchors.fromSelection(
            fragment,start,start+"La tierra".length());
        assertNotNull(underline);
        assertEquals(2,underline.verse);
        AnnotationAnchors.Range afterReload=AnnotationAnchors.inDisplay(
            CHAPTER,underline.verse,underline.offset,underline.quote);
        assertNotNull(afterReload);
        assertEquals("La tierra",CHAPTER.substring(afterReload.start,afterReload.end));
    }
}
