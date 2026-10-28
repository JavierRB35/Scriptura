package com.scriptura.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class GeminiSelectionTest {
    private static final String TEXT="1  En el principio creó Dios el cielo.\n\n"+
        "2  La tierra estaba desordenada y vacía.\n\n"+
        "3  Dios dijo: Sea la luz.";

    @Test public void selectedPhraseGetsExactVerseAndBoundedContext(){
        int start=TEXT.indexOf("desordenada");
        GeminiSelection.Fragment result=GeminiSelection.fromSelection(
            TEXT,"Génesis",1,start,start+11);
        assertNotNull(result);
        assertEquals("Génesis 1:2",result.reference);
        assertEquals("desordenada",result.quote);
        assertTrue(result.context.contains("La tierra"));
        assertTrue(result.context.contains("Dios dijo"));
        assertTrue(GeminiSelection.question(result).contains("desordenada"));
    }
    @Test public void supportsContinuousMultiVerseSelection(){
        int start=TEXT.indexOf("vacía");
        int end=TEXT.indexOf("Dios dijo")+9;
        GeminiSelection.Fragment result=GeminiSelection.fromSelection(
            TEXT,"Génesis",1,start,end);
        assertNotNull(result);
        assertEquals("Génesis 1:2-3",result.reference);
        assertEquals(2,result.firstVerse);
        assertEquals(3,result.lastVerse);
    }
    @Test public void rejectsVerseLabelsAndInvalidRanges(){
        assertNull(GeminiSelection.fromSelection(TEXT,"Gn",1,0,1));
        assertNull(GeminiSelection.fromSelection(TEXT,"Gn",1,-1,7));
        assertNull(GeminiSelection.fromSelection(TEXT,"Gn",1,7,7));
        assertNull(GeminiSelection.fromSelection(TEXT,"Gn",1,5,TEXT.length()+1));
        assertNull(GeminiSelection.fromSelection(TEXT,"",1,5,8));
    }
    @Test public void refusesExcessivelyLongSelection(){
        String s="1  "+"a".repeat(GeminiSelection.MAX_SELECTION+1);
        assertNull(GeminiSelection.fromSelection(s,"Gn",1,3,s.length()));
    }
}
