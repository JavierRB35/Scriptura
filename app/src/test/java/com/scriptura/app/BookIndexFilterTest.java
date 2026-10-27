package com.scriptura.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class BookIndexFilterTest {
    @Test public void allSeventyThreeBooksRemainAvailable(){
        assertEquals(73,BookIndexFilter.find("",0).size());
        assertEquals(46,BookIndexFilter.find("",1).size());
        assertEquals(27,BookIndexFilter.find("",2).size());
    }
    @Test public void accentAndCaseInsensitiveSearch(){
        assertEquals("Gn",BookIndexFilter.find("genesis",0).get(0));
        assertEquals("Gn",BookIndexFilter.find("GÉNESIS",0).get(0));
        assertEquals("Jn",BookIndexFilter.find("juan",2).get(0));
        assertTrue(BookIndexFilter.find("juan",1).isEmpty());
        assertTrue(BookIndexFilter.find("no existe",0).isEmpty());
    }
    @Test public void retainsCanonicalOrder(){
        List<String> books=BookIndexFilter.find("",0);
        assertEquals("Gn",books.get(0));
        assertEquals("Ap",books.get(books.size()-1));
        assertTrue(books.indexOf("Mt")<books.indexOf("Jn"));
    }
}
