package com.scriptura.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class WordMeaningTest {
    @Test public void handlesAccentsAndCapitalLetters(){
        assertNotNull(WordMeaning.local("MISERICORDIA"));
        assertNotNull(WordMeaning.local("Salvación"));
        assertNotNull(WordMeaning.local("apóstoles"));
        assertEquals(WordMeaning.local("PROJIMO"),WordMeaning.local("prójimo"));
    }
    @Test public void permitsOnlyOneWord(){
        assertTrue(WordMeaning.oneWord("espíritu"));
        assertTrue(WordMeaning.oneWord("Señor"));
        assertFalse(WordMeaning.oneWord("la gracia"));
        assertFalse(WordMeaning.oneWord("gracia,"));
        assertFalse(WordMeaning.oneWord(""));
    }
    @Test public void preservesSelectionBoundaries(){
        String s="La gracia y la misericordia.";
        assertEquals("gracia",WordMeaning.selection(s,3,9));
        assertNull(WordMeaning.selection(s,0,9));
        assertNull(WordMeaning.selection(s,-1,8));
        assertNull(WordMeaning.selection(s,5,999));
    }
    @Test public void createsSafeExternalLookupUrls(){
        assertEquals("https://ec.aciprensa.com/wiki/Especial:Buscar?search=Salvaci%C3%B3n",
            WordMeaning.catholicSearchUrl("Salvación"));
        assertEquals("https://es.wikipedia.org/w/index.php?search=ap%C3%B3stoles",
            WordMeaning.wikipediaSearchUrl("apóstoles"));
        assertEquals("https://es.wikipedia.org/w/index.php?search=Jes%C3%BAs",
            WordMeaning.wikipediaSearchUrl("Jesús"));
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsPhrasesInExternalLookups(){
        WordMeaning.catholicSearchUrl("la gracia");
    }

}
