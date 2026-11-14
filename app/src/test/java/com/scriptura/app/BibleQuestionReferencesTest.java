package com.scriptura.app;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public final class BibleQuestionReferencesTest {
    @Test public void localizaLosTresEvangeliosDeLaTransfiguracion(){
        List<BibleQuestionReferences.Ref> refs=BibleQuestionReferences.parse(
            Arrays.asList("Mt 17:1-8","Mc 9:2-8","Lc 9:28-36"));
        assertEquals(3,refs.size());
        assertEquals("Mateo 17:1-8",refs.get(0).label());
        assertEquals("Mc 9:2-8",refs.get(1).query);
        assertEquals(28,refs.get(2).first);
        assertEquals(36,refs.get(2).last);
    }

    @Test public void descartaCodigosInvalidosDuplicadosYRangosImposibles(){
        List<BibleQuestionReferences.Ref> refs=BibleQuestionReferences.parse(
            Arrays.asList("Mt 17:3","Mt 17:3","Inventado 1:1","Jn 0:1",
                "Mc 9:8-2","Lc 9:0","Gn 1:1-99","1Co 13:4-7"));
        assertEquals(2,refs.size());
        assertEquals("Mt 17:3",refs.get(0).query);
        assertEquals("1Co 13:4-7",refs.get(1).query);
    }

    @Test public void noAdmiteCitasAusentesDeLosExtractos(){
        List<BibleQuestionReferences.Ref> evidence=BibleQuestionReferences.parse(
            Arrays.asList("Mt 17:1-8","Mc 9:2-8"));
        List<BibleQuestionReferences.Ref> cited=BibleQuestionReferences.cited(evidence,
            Arrays.asList("Mt 17:3","Lc 9:30","Mt 17:9","Mc 9:4","Mc 9:4"));
        assertEquals(2,cited.size());
        assertEquals("Mt 17:3",cited.get(0).query);
        assertEquals("Mc 9:4",cited.get(1).query);
    }

    @Test public void sinFuentesNoHayCitas(){
        assertTrue(BibleQuestionReferences.cited(Collections.emptyList(),
            Arrays.asList("Mt 17:3")).isEmpty());
        assertTrue(BibleQuestionReferences.parse(null).isEmpty());
    }
}
