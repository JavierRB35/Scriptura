package com.scriptura.app;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import static org.junit.Assert.*;

public class LibraryCatalogTest {
    private String text(String id) throws Exception {
        Path file=Paths.get("src/main/assets/library/"+id+".md");
        if(!Files.exists(file))file=Paths.get("app/src/main/assets/library/"+id+".md");
        return new String(Files.readAllBytes(file),StandardCharsets.UTF_8);
    }

    @Test public void todosLosBorradoresTienenSeccionesLegibles() throws Exception {
        for(String id:new String[]{"thomas","mary","peter","infancyjames","infancythomas","judas"}){
            List<LibraryCatalog.Section> sections=LibraryCatalog.parseMarkdown(text(id));
            assertFalse(id+" está vacío",sections.isEmpty());
            for(int i=0;i<sections.size();i++){
                assertEquals(i+1,sections.get(i).index);
                assertFalse(sections.get(i).title.isEmpty());
                assertFalse(sections.get(i).text.isEmpty());
            }
        }
    }

    @Test public void tomasConserva114DichosYPrologo() throws Exception {
        List<LibraryCatalog.Section> sections=LibraryCatalog.parseMarkdown(text("thomas"));
        assertEquals(116,sections.size()); // Prólogo, 114 dichos, notas
        assertEquals("Prólogo",sections.get(0).title);
        assertTrue(sections.get(1).title.startsWith("Dicho 1."));
        assertTrue(sections.get(114).title.startsWith("Dicho 114."));
        assertTrue(sections.get(115).editorial);
    }

    @Test public void mariaYJudasConservanPaginasDeManuscrito() throws Exception {
        String mary=LibraryCatalog.parseMarkdown(text("mary")).get(0).text;
        String judas=LibraryCatalog.parseMarkdown(text("judas")).get(0).text;
        assertTrue(mary.contains("⟦p. 7⟧"));
        assertTrue(judas.contains("⟦p. 33⟧"));
    }

    @Test public void santiagoMantieneVarianteAlternativa() throws Exception {
        List<LibraryCatalog.Section> sections=LibraryCatalog.parseMarkdown(text("infancyjames"));
        assertTrue(sections.stream().anyMatch(s->s.editorial&&
            s.title.contains("Versión breve alternativa")));
    }

    @Test public void referenciaInternaNoSeConfundeConCanon() {
        LibraryCatalog.Work work=new LibraryCatalog.Work(
            "thomas","Evangelio de Tomás","114 dichos","https://www.gospels.net/thomas");
        LibraryCatalog.Section section=new LibraryCatalog.Section(3,
            "Dicho 2. Buscar y encontrar","Jesús dijo",false);
        String key=LibraryCatalog.reference(work,section);
        assertTrue(LibraryCatalog.isReference(key));
        assertEquals("LIB:thomas:3",key);
        assertArrayEquals(new String[]{"thomas","3"},LibraryCatalog.decodeReference(key));
        assertNull(LibraryCatalog.decodeReference("Mt 17:3"));
        assertTrue(LibraryCatalog.isAnnotationBook(work.annotationBook()));
        assertEquals(73,Books.NAMES.size());
    }
}
