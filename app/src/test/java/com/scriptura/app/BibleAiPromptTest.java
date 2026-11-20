package com.scriptura.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BibleAiPromptTest {
    @Test public void ordenaInterpretacionEnBloquesConContextoHistorico() {
        String prompt=BibleAiPrompt.build(
            "Juan 2:1-11","1  Hubo una boda en Caná.","Comentario local",
            "¿Qué significan las bodas en este relato?");
        int explanation=prompt.indexOf(BibleAiPrompt.EXPLANATION+"\n");
        int history=prompt.indexOf(BibleAiPrompt.HISTORY+"\n");
        int spiritual=prompt.indexOf(BibleAiPrompt.SPIRITUAL+"\n");
        int reflection=prompt.indexOf(BibleAiPrompt.APPLICATION+"\n");
        assertTrue(explanation>0);
        assertTrue(history>explanation);
        assertTrue(spiritual>history);
        assertTrue(reflection>spiritual);
        assertTrue(prompt.contains("costumbres"));
        assertTrue(prompt.contains("origen y función de las prácticas"));
        assertTrue(prompt.contains("Si faltan datos fiables"));
        assertTrue(prompt.contains("indicios del texto"));
        assertTrue(prompt.contains("No inventes cronologías"));
        assertTrue(prompt.contains("la tradición católica"));
        assertTrue(prompt.contains("TEXTO LOCAL:\n1  Hubo una boda"));
        assertTrue(prompt.contains("PETICIÓN: ¿Qué significan las bodas"));
    }

    @Test public void lasObrasExtracanonicasConservanSusLimitesYFormato() {
        String prompt=BibleAiPrompt.build(
            "BIBLIOTECA EXTRACANÓNICA · Evangelio de Tomás · Dicho 3",
            "Jesús dijo", "Traducción de trabajo", "Explica el texto");
        assertTrue(prompt.contains("NO pertenece al canon bíblico"));
        assertTrue(prompt.contains("solo en el texto local"));
        assertTrue(prompt.contains("nunca como doctrina católica ni autoridad bíblica"));
        assertTrue(prompt.contains(BibleAiPrompt.HISTORY));
        assertTrue(prompt.contains(BibleAiPrompt.SPIRITUAL));
        assertFalse(prompt.contains("Actúa como acompañante de lectura bíblica"));
    }

    @Test public void limitaLosTextosEnviadosYConservaLaPregunta() {
        String text="a".repeat(10000),context="b".repeat(1200),question="c".repeat(2000);
        String prompt=BibleAiPrompt.build("Mateo 5",text,context,question);
        assertTrue(prompt.contains("a".repeat(9000)));
        assertFalse(prompt.contains("a".repeat(9001)));
        assertTrue(prompt.contains("b".repeat(1000)));
        assertFalse(prompt.contains("b".repeat(1001)));
        assertTrue(prompt.contains("c".repeat(1800)));
        assertFalse(prompt.contains("c".repeat(1801)));
    }
}
