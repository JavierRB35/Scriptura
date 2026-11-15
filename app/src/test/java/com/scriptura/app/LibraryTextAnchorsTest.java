package com.scriptura.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class LibraryTextAnchorsTest {
    @Test public void conservaSeleccionExactaUnicode() {
        String source="⟦p. 7⟧ María dijo: «[ ]».";
        int start=source.indexOf("María");
        String quote=LibraryTextAnchors.selection(source,start,source.length());
        assertNotNull(quote);
        assertEquals(start,LibraryTextAnchors.locate(source,start,quote));
    }
    @Test public void fallaSiTextoHaCambiadoYLaCitaEsAmbigua() {
        assertEquals(-1,LibraryTextAnchors.locate("uno, uno",99,"uno"));
        assertEquals(2,LibraryTextAnchors.locate("a uno b",99,"uno"));
        assertEquals(-1,LibraryTextAnchors.locate("abc",0,"inexistente"));
    }
    @Test public void rechazaSeleccionVaciaOInvalida() {
        assertNull(LibraryTextAnchors.selection("uno",0,0));
        assertNull(LibraryTextAnchors.selection("uno",-1,2));
        assertNull(LibraryTextAnchors.selection("   ",0,3));
    }
}
