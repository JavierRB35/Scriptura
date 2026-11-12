package com.scriptura.app;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class AnnotationLibraryTest {
    private static StudyAnnotations.Item item(long id,String book,int chapter,int verse){
        return new StudyAnnotations.Item(id,book,chapter,verse,0,
            "Fragmento",StudyAnnotations.UNDERLINE,"","",id);
    }

    @Test public void sortsByCanonicalBooksOldThenNewAndWithinChapters(){
        List<StudyAnnotations.Item> rows=new ArrayList<>(Arrays.asList(
            item(1,"Ap",1,1),item(2,"Sal",23,1),item(3,"Gn",2,4),
            item(4,"Mt",1,1),item(5,"Ex",2,1),item(6,"Gn",1,5),
            item(7,"1Jn",1,1),item(8,"Gn",1,2),item(9,"Jn",3,16)));
        List<StudyAnnotations.Item> sorted=AnnotationLibrary.ordered(rows);
        long[] expected={8,6,3,5,2,4,9,7,1};
        for(int i=0;i<expected.length;i++)assertEquals(expected[i],sorted.get(i).id);
        assertEquals("Ap",rows.get(0).book);
        assertNotSame(rows,sorted);
    }

    @Test public void includesNotesLinksAndHighlightsWithoutLosingDuplicates(){
        StudyAnnotations.Item note=new StudyAnnotations.Item(2,"Rm",8,28,0,
            "",StudyAnnotations.NOTE,"Mi nota","",5);
        StudyAnnotations.Item highlight=new StudyAnnotations.Item(1,"Rm",8,28,0,
            "Texto",StudyAnnotations.UNDERLINE,"","",3);
        StudyAnnotations.Item map=new StudyAnnotations.Item(3,"Rm",8,28,0,
            "Texto",StudyAnnotations.MAP,"","https://example.org",7);
        List<StudyAnnotations.Item> result=AnnotationLibrary.ordered(
            Arrays.asList(note,map,highlight));
        assertEquals(3,result.size());
        assertSame(highlight,result.get(0));
        assertSame(note,result.get(1));
        assertSame(map,result.get(2));
    }

    @Test public void countsOnlyBooksWithMarksInCanonicalOrder(){
        List<StudyAnnotations.Item> ordered=AnnotationLibrary.ordered(Arrays.asList(
            item(7,"Ap",1,1),item(2,"Gn",1,1),item(4,"Mt",2,3),
            item(1,"Gn",1,1),item(3,"Mt",2,3),item(8,"Gn",1,4)));
        java.util.LinkedHashMap<String,Integer> totals=AnnotationLibrary.countsByBook(ordered);
        assertEquals(Arrays.asList("Gn","Mt","Ap"),new ArrayList<>(totals.keySet()));
        assertEquals(Integer.valueOf(3),totals.get("Gn"));
        assertEquals(Integer.valueOf(2),totals.get("Mt"));
        assertEquals(Integer.valueOf(1),totals.get("Ap"));
        assertFalse(totals.containsKey("Ex"));
        assertTrue(AnnotationLibrary.countsByBook(new ArrayList<>()).isEmpty());
    }

    @Test public void emptyListRemainsEmpty(){
        assertTrue(AnnotationLibrary.ordered(new ArrayList<>()).isEmpty());
    }
}
