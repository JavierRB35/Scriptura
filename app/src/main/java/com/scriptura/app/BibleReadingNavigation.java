package com.scriptura.app;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Navegación continua por los 73 libros canónicos, sin entrar en Biblioteca. */
final class BibleReadingNavigation {
    private BibleReadingNavigation(){}

    static final class Destination {
        final String book;
        final int chapter;
        Destination(String book,int chapter){
            this.book=book;
            this.chapter=chapter;
        }
        String label(){return Books.name(book)+" "+chapter;}
    }

    /** Cruza el límite del libro y omite libros todavía no disponibles en el EPUB. */
    static Destination adjacent(String book,int chapter,boolean forward,
                                ToIntFunction<String> chapterCount){
        if(book==null||chapterCount==null||!Books.NAMES.containsKey(book))return null;
        int count=chapterCount.applyAsInt(book);
        if(count<1||chapter<1||chapter>count)return null;
        if(forward&&chapter<count)return new Destination(book,chapter+1);
        if(!forward&&chapter>1)return new Destination(book,chapter-1);
        List<String> books=new ArrayList<>(Books.NAMES.keySet());
        int index=books.indexOf(book);
        for(int i=index+(forward?1:-1);i>=0&&i<books.size();i+=forward?1:-1){
            String candidate=books.get(i);
            int chapters=chapterCount.applyAsInt(candidate);
            if(chapters>0)return new Destination(candidate,forward?1:chapters);
        }
        return null;
    }
}
