package com.scriptura.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class DisplayModesTest {
    @Test public void defaultIsStable(){
        assertEquals(DisplayModes.NORMAL,DisplayModes.normalize(-1));
        assertEquals(DisplayModes.NORMAL,DisplayModes.normalize(99));
        assertEquals(DisplayModes.NORMAL,DisplayModes.normalize(DisplayModes.NORMAL));
    }
    @Test public void preservesValidUserPreference(){
        assertEquals(DisplayModes.COMFORT,DisplayModes.normalize(DisplayModes.COMFORT));
        assertEquals(DisplayModes.BATTERY,DisplayModes.normalize(DisplayModes.BATTERY));
        assertEquals(DisplayModes.SYSTEM,DisplayModes.normalize(DisplayModes.SYSTEM));
        assertEquals("Lectura cómoda",DisplayModes.label(DisplayModes.COMFORT));
        assertTrue(DisplayModes.label(DisplayModes.BATTERY).contains("OLED"));
    }
    @Test public void systemModeHasOwnLabel(){
        assertEquals("Automático · según el sistema",DisplayModes.label(DisplayModes.SYSTEM));
    }
}
