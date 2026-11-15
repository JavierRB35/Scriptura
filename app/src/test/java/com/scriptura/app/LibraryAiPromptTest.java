package com.scriptura.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class LibraryAiPromptTest {
    @Test public void noPresentaApocrifosComoBibliaCanonica() {
        String prompt=BibleAiPrompt.build(
            "BIBLIOTECA EXTRACANÓNICA · Evangelio de Tomás · Dicho 2",
            "Jesús dijo", "Traducción provisional", "¿Qué dice?");
        assertTrue(prompt.contains("NO pertenece al canon bíblico"));
        assertTrue(prompt.contains("solo en el texto local"));
        assertFalse(prompt.contains("Actúa como acompañante de lectura bíblica"));
    }
    @Test public void conservaLasInstruccionesCanonicas() {
        String prompt=BibleAiPrompt.build("Mateo 17:3","3  Moisés y Elías","","Explica");
        assertTrue(prompt.contains("lectura bíblica en español"));
        assertTrue(prompt.contains("TEXTO LOCAL"));
    }
}
