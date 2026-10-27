package com.scriptura.app;
import org.junit.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;
public class ArtCatalogTest {
    @Test public void atLeastFortyCuratedArtworks(){
        List<BiblicalArt.Painting> works=BiblicalArt.all();
        assertTrue("Se requieren al menos 40 obras",works.size()>=40);
        HashSet<String> ids=new HashSet<>();
        for(BiblicalArt.Painting p:works){
            assertTrue("ID duplicado: "+p.id,ids.add(p.id));
            assertTrue(Books.NAMES.containsKey(p.book));
            assertFalse(p.title.isEmpty());
            assertFalse(p.author.isEmpty());
            assertFalse(p.reference.isEmpty());
            assertTrue(BiblicalArt.commonsPage(p).startsWith("https://commons.wikimedia.org/"));
        }
    }
    @Test public void usesPassageAndBookRelations(){
        assertTrue(BiblicalArt.byBook("Gn").size()>2);
        assertTrue(BiblicalArt.byBook("Lc").size()>5);
        assertTrue(BiblicalArt.forPassage("Jn",2).size()>0);
        assertTrue(BiblicalArt.forPassage("Mt",26).size()>0);
    }
}