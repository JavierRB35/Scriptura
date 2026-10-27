package com.scriptura.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReferenceTest {
    @Test public void aceptaLos73Libros() {
        assertEquals(73,Books.NAMES.size());
        assertEquals("Gn",Books.resolve("Génesis"));
        assertEquals("Jn",Books.resolve("Juan"));
        assertEquals("1Co",Books.resolve("1 Corintios"));
        assertEquals("Si",Books.resolve("Eclesiástico"));
        assertEquals("Tb",Books.resolve("Tobías"));
    }
    @Test public void reconoceReferenciasLiturgicasSimples() {
        assertEquals("Efesios 5:21-33",Liturgy.toLocalQuery("Ef 5, 21-33"));
        assertEquals("Lucas 13:18-21",Liturgy.toLocalQuery("Lc 13, 18-21"));
        assertEquals("Mateo 11:25",Liturgy.toLocalQuery("Mt 11, 25"));
        assertEquals("Juan 3:16",Liturgy.toLocalQuery("Juan 3:16"));
    }
    @Test public void noRecortaReferenciasConSaltos() {
        assertEquals("",Liturgy.toLocalQuery("Sal 127, 1bc-2. 3. 4-5"));
        assertEquals("",Liturgy.toLocalQuery("Ef 4, 32–5, 8"));
        assertEquals("",Liturgy.toLocalQuery("Sal 22, 1. 4. 5-6"));
        assertEquals("",Liturgy.toLocalQuery("Jn 3, 16-18. 21"));
        assertEquals("",Liturgy.toLocalQuery("Mt 3, 20-18"));
        assertEquals("",Liturgy.toLocalQuery("Mt 3, 0"));
    }
}
