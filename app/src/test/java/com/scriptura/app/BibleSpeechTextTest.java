package com.scriptura.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class BibleSpeechTextTest {
    @Test public void anunciaCapituloSinLeerLosNumerosDeVersiculo(){
        String passage="1  En el principio creó Dios el cielo.\n\n"+
            "2  La tierra estaba vacía.\n\n"+
            "10  Dios llamó al firmamento cielo.";
        String voice=BibleSpeechText.chapter("Génesis",1,passage);
        assertEquals("Génesis. Capítulo 1.\n\n"+
            "En el principio creó Dios el cielo.\n\n"+
            "La tierra estaba vacía.\n\n"+
            "Dios llamó al firmamento cielo.",voice);
        assertFalse(voice.contains("2  La tierra"));
        assertFalse(voice.contains("10  Dios"));
    }

    @Test public void conservaLosNumerosQueFormanParteDelRelato(){
        String passage="1  Había 12 hombres y 3 mujeres.\n\n"+
            "2  Caminó 40 días y llevaba 2 panes.";
        assertEquals("Mateo. Capítulo 7.\n\n"+
            "Había 12 hombres y 3 mujeres.\n\n"+
            "Caminó 40 días y llevaba 2 panes.",
            BibleSpeechText.chapter("Mateo",7,passage));
    }

    @Test public void conservaNumerosInternosYNoModificaElTextoOriginal(){
        String passage="1  El texto dice 3  veces 'ven'.\n\n"+
            "2  Se citó el número 17.";
        String original=passage;
        assertTrue(BibleSpeechText.chapter("Juan",3,passage).contains("3  veces 'ven'"));
        assertEquals(original,passage);
    }

    @Test public void resisteUnPasajeSinMarcasNumericas(){
        assertEquals("Marcos. Capítulo 4.\n\nUn pasaje sin numeración.",
            BibleSpeechText.chapter("Marcos",4," Un pasaje sin numeración. "));
    }

    @Test public void noAceptaCabecerasInvalidas(){
        try{
            BibleSpeechText.chapter("",2,"1  Texto.");
            fail("Libro vacío");
        }catch(IllegalArgumentException expected){}
        try{
            BibleSpeechText.chapter("Lucas",0,"1  Texto.");
            fail("Capítulo cero");
        }catch(IllegalArgumentException expected){}
    }
}
