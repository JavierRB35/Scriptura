package com.scriptura.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReadingProgressTest {
    private static final String CHAPTER="1  En el principio.\n\n"+
        "2  La tierra estaba vacía.\n\n"+
        "3  Hágase la luz.";

    @Test public void savedPositionCanBeRestoredAfterRestart(){
        ReadingProgress.Position original=new ReadingProgress.Position("Jn",3,16);
        assertEquals(original,ReadingProgress.fromStored(original.toStored()));
        assertEquals("v1|Jn|3|16",original.toStored());
    }

    @Test public void malformedPositionNeverOpensAnInvalidReference(){
        assertNull(ReadingProgress.fromStored(null));
        assertNull(ReadingProgress.fromStored(""));
        assertNull(ReadingProgress.fromStored("v1|Desconocido|1|1"));
        assertNull(ReadingProgress.fromStored("v1|Jn|0|1"));
        assertNull(ReadingProgress.fromStored("v1|Jn|3|0"));
        assertNull(ReadingProgress.fromStored("v1|Jn|999|1"));
        assertNull(ReadingProgress.fromStored("v1|Jn|3|abc"));
        assertNull(ReadingProgress.fromStored("v1|Jn|3|16|extra"));
    }

    @Test public void positionIncludesBookAndChapter(){
        assertNotEquals(new ReadingProgress.Position("Jn",3,16),
            new ReadingProgress.Position("Jn",4,16));
        assertNotEquals(new ReadingProgress.Position("Jn",3,16),
            new ReadingProgress.Position("Lc",3,16));
    }

    @Test public void visibleVerseUsesTheActualTextPosition(){
        assertEquals(1,ReadingProgress.verseAtOffset(CHAPTER,0));
        assertEquals(1,ReadingProgress.verseAtOffset(CHAPTER,CHAPTER.indexOf("principio")));
        assertEquals(2,ReadingProgress.verseAtOffset(CHAPTER,CHAPTER.indexOf("2  ")));
        assertEquals(2,ReadingProgress.verseAtOffset(CHAPTER,CHAPTER.indexOf("vacía")));
        assertEquals(3,ReadingProgress.verseAtOffset(CHAPTER,CHAPTER.indexOf("3  ")));
        assertEquals(3,ReadingProgress.verseAtOffset(CHAPTER,CHAPTER.length()));
    }

    @Test public void noVerseReturnsAbsentInsteadOfGuessing(){
        assertEquals(-1,ReadingProgress.verseAtOffset(null,0));
        assertEquals(-1,ReadingProgress.verseAtOffset("",0));
        assertEquals(-1,ReadingProgress.verseAtOffset("Texto sin numeración",10));
    }

    @Test public void supportsNumberedBooksAndLongerVerses(){
        ReadingProgress.Position p=new ReadingProgress.Position("1Co",13,4);
        assertEquals(p,ReadingProgress.fromStored(p.toStored()));
        String chapter="9  Texto.\n\n10  Otro texto.";
        assertEquals(10,ReadingProgress.verseAtOffset(chapter,chapter.indexOf("10  ")));
    }
}
