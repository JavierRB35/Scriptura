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
    @Test public void verifiedCatholicPagesUseDirectArticles(){
        assertEquals("https://ec.aciprensa.com/wiki/Fariseos",
            WordMeaning.catholicArticleUrl("fariseos"));
        assertEquals("https://ec.aciprensa.com/wiki/Saduceos",
            WordMeaning.catholicArticleUrl("SADUCEO"));
        assertEquals("https://ec.aciprensa.com/wiki/Ap%C3%B3stoles",
            WordMeaning.catholicArticleUrl("apóstoles"));
        assertEquals("https://ec.aciprensa.com/wiki/Pecado",
            WordMeaning.catholicArticleUrl("pecado"));
        assertNull(WordMeaning.catholicArticleUrl("cananeos"));
        assertNull(WordMeaning.catholicArticleUrl("pastores"));
    }
    @Test public void officialSearchRoutesPreserveAccents(){
        assertEquals("https://es.wikipedia.org/wiki/Especial:Buscar?search=Salvaci%C3%B3n",
            WordMeaning.wikipediaSearchUrl("Salvación"));
        assertEquals("https://es.wiktionary.org/wiki/Especial:Buscar?search=ap%C3%B3stoles",
            WordMeaning.wiktionarySearchUrl("apóstoles"));
        assertEquals("https://es.wikipedia.org/wiki/Especial:Buscar?search=Jes%C3%BAs",
            WordMeaning.wikipediaSearchUrl("Jesús"));
    }
    @Test(expected=IllegalArgumentException.class)
    public void blocksMultipleWords(){
        WordMeaning.wikipediaSearchUrl("la gracia");
    }
    @Test(expected=IllegalArgumentException.class)
    public void blocksUnsafePunctuation(){
        WordMeaning.wiktionarySearchUrl("fariseo?redirect=https://evil.test");
    }
}
