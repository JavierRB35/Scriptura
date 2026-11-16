package com.scriptura.app;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import static org.junit.Assert.*;

public final class BibleReadingNavigationTest {
    private static final Map<String,Integer> COUNTS=new HashMap<>();
    static{
        COUNTS.put("Gn",50);
        COUNTS.put("Ex",40);
        COUNTS.put("Mt",28);
        COUNTS.put("Mc",16);
        COUNTS.put("Lc",24);
        COUNTS.put("Ap",22);
    }
    private static final ToIntFunction<String> CHAPTERS=code->COUNTS.getOrDefault(code,0);

    @Test public void sigueAlLibroSiguienteAlAcabarMateo(){
        BibleReadingNavigation.Destination next=BibleReadingNavigation.adjacent(
            "Mt",28,true,CHAPTERS);
        assertNotNull(next);
        assertEquals("Mc",next.book);
        assertEquals(1,next.chapter);
        assertEquals("Marcos 1",next.label());
    }
    @Test public void vuelveAlLibroAnteriorDesdeMarcosUno(){
        BibleReadingNavigation.Destination previous=BibleReadingNavigation.adjacent(
            "Mc",1,false,CHAPTERS);
        assertNotNull(previous);
        assertEquals("Mt",previous.book);
        assertEquals(28,previous.chapter);
    }
    @Test public void mantieneNavegacionInterior(){
        assertEquals(20,BibleReadingNavigation.adjacent("Mt",19,true,CHAPTERS).chapter);
        assertEquals(18,BibleReadingNavigation.adjacent("Mt",19,false,CHAPTERS).chapter);
    }
    @Test public void respetaInicioYFinalDelCanon(){
        assertNull(BibleReadingNavigation.adjacent("Gn",1,false,CHAPTERS));
        assertNull(BibleReadingNavigation.adjacent("Ap",22,true,CHAPTERS));
    }
    @Test public void saltaLosLibrosNoImportadosSinAccederALaBiblioteca(){
        assertEquals("Mt",BibleReadingNavigation.adjacent("Ex",40,true,CHAPTERS).book);
        assertEquals("Ex",BibleReadingNavigation.adjacent("Mt",1,false,CHAPTERS).book);
        assertNull(BibleReadingNavigation.adjacent("NoCanonico",1,true,CHAPTERS));
        assertNull(BibleReadingNavigation.adjacent("Mt",99,false,CHAPTERS));
    }
}
