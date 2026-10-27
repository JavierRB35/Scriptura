package com.scriptura.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class HistoricalTermMatcherTest {
    @Test public void matchesWholeWordsOnly(){
        String p=HistoricalTermMatcher.normalize("Los fariseos hablaron con Jesús y los pastores.");
        assertTrue(HistoricalTermMatcher.firstPosition(p,"fariseos")>=0);
        assertTrue(HistoricalTermMatcher.firstPosition(p,"pastores")>=0);
        assertEquals(-1,HistoricalTermMatcher.firstPosition(p,"fariseo"));
        assertEquals(-1,HistoricalTermMatcher.firstPosition(p,"pastora"));
    }
    @Test public void handlesAccentsAndCapitalization(){
        String p=HistoricalTermMatcher.normalize("SADUCEOS. La Ley y la Resurrección.");
        assertTrue(HistoricalTermMatcher.firstPosition(p,"saduceos")>=0);
        assertTrue(HistoricalTermMatcher.firstPosition(p,"resurreccion")>=0);
    }
    @Test public void preventsSubstringMatches(){
        String p=HistoricalTermMatcher.normalize("Hay falsos testimonios sobre el pastor.");
        assertEquals(-1,HistoricalTermMatcher.firstPosition(p,"pastores"));
        assertEquals(-1,HistoricalTermMatcher.firstPosition(p,"testamento"));
        assertTrue(HistoricalTermMatcher.firstPosition(p,"pastor")>=0);
    }
    @Test public void respectsPeriods(){
        assertTrue(HistoricalTermMatcher.appliesTo("NT",true));
        assertFalse(HistoricalTermMatcher.appliesTo("NT",false));
        assertTrue(HistoricalTermMatcher.appliesTo("AT",false));
        assertFalse(HistoricalTermMatcher.appliesTo("AT",true));
        assertTrue(HistoricalTermMatcher.appliesTo("ALL",true));
        assertTrue(HistoricalTermMatcher.appliesTo("ALL",false));
    }
}
