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
        assertEquals("La gracia",WordMeaning.selection(s,0,9));
        assertEquals("la misericordia",WordMeaning.selection(s,12,27));
        assertNull(WordMeaning.selection(s,-1,8));
        assertNull(WordMeaning.selection(s,5,999));
    }
    @Test public void supportsMultiwordPhrasesWithAccentsAndWhitespace(){
        assertEquals("Reino de Dios",WordMeaning.phrase("  Reino  de   Dios "));
        assertEquals("Hijo del hombre",WordMeaning.phrase("Hijo\\n del\\t hombre"));
        assertEquals("Espíritu Santo",WordMeaning.phrase("Espíritu Santo"));
        String verse="Creed en el Reino de Dios.";
        assertEquals("Reino de Dios",WordMeaning.selection(verse,12,25));
        assertEquals("Señor",WordMeaning.phrase(" Señor "));
        assertNull(WordMeaning.phrase(" "));
        assertNull(WordMeaning.phrase("un token con ? redirect=https://malicioso.test"));
        assertNull(WordMeaning.phrase("123 numeración"));
        assertNull(WordMeaning.phrase("a".repeat(181)));
        assertNull(WordMeaning.phrase("y ".repeat(25).trim()));
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
    @Test public void searchesForTheCompletePhraseWithoutLosingAccents(){
        assertEquals("https://es.wikipedia.org/wiki/Especial:Buscar?search=Reino%20de%20Dios",
            WordMeaning.wikipediaSearchUrl("Reino de Dios"));
        assertEquals("https://es.wiktionary.org/wiki/Especial:Buscar?search=Esp%C3%ADritu%20Santo",
            WordMeaning.wiktionarySearchUrl("Espíritu Santo"));
        assertEquals("https://es.wikipedia.org/wiki/Especial:Buscar?search=Hijo%20del%20hombre",
            WordMeaning.wikipediaSearchUrl("Hijo   del hombre"));
        assertFalse(WordMeaning.oneWord("Reino de Dios"));
        assertNull(WordMeaning.local("Reino de Dios"));
    }
    @Test(expected=IllegalArgumentException.class)
    public void blocksUnsafePunctuation(){
        WordMeaning.wiktionarySearchUrl("fariseo?redirect=https://evil.test");
    }
}
