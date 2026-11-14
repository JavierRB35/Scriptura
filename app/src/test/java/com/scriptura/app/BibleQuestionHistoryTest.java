package com.scriptura.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public final class BibleQuestionHistoryTest {
    @Test public void guardaYPuedeRecuperarPreguntaRespuestaYCitas() throws Exception {
        List<BibleQuestionReferences.Ref> refs=BibleQuestionReferences.parse(
            Arrays.asList("Mt 17:3","Mc 9:4","Lc 9:30-31"));
        String json=BibleQuestionHistory.encode(
            "¿Quiénes aparecieron con Jesús en la transfiguración?",
            "Moisés y Elías (según los evangelios).",refs);
        BibleQuestionHistory.Entry recovered=BibleQuestionHistory.decode(json,123456789L);
        assertNotNull(recovered);
        assertEquals("¿Quiénes aparecieron con Jesús en la transfiguración?",recovered.question);
        assertEquals("Moisés y Elías (según los evangelios).",recovered.answer);
        assertEquals(3,recovered.references.size());
        assertEquals("Mt 17:3",recovered.references.get(0).query);
        assertEquals("Lc 9:30-31",recovered.references.get(2).query);
        assertEquals(123456789L,recovered.created);
    }

    @Test public void respuestaSinCitasNoSePuedeGuardar() throws Exception {
        try{
            BibleQuestionHistory.encode("pregunta","respuesta",Collections.emptyList());
            fail("No se deben archivar respuestas sin evidencia bíblica");
        }catch(IllegalArgumentException expected){}
    }

    @Test public void registrosDanadosNoBloqueanElHistorial() {
        assertNull(BibleQuestionHistory.decode("no es JSON",10));
        assertNull(BibleQuestionHistory.decode("{\"version\":2,\"question\":\"p\"}",10));
        assertNull(BibleQuestionHistory.decode(
            "{\"version\":1,\"question\":\"p\",\"answer\":\"a\",\"citations\":[\"Libro falso 1:1\"]}",10));
    }

    @Test public void noConvierteCitasInventadasEnEnlaces() {
        BibleQuestionHistory.Entry entry=BibleQuestionHistory.decode(
            "{\"version\":1,\"question\":\"p\",\"answer\":\"a\",\"citations\":[\"Mt 17:3\",\"NoExiste 2:4\"]}",10);
        assertNotNull(entry);
        assertEquals(1,entry.references.size());
        assertEquals("Mt 17:3",entry.references.get(0).query);
    }
}
