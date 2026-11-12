package com.scriptura.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Orders saved marks using the canonical order of the 73 books, not alphabetically. */
public final class AnnotationLibrary {
    private static final Map<String,Integer> BOOK_ORDER=new HashMap<>();
    static {
        int ordinal=0;
        for(String code:Books.NAMES.keySet())BOOK_ORDER.put(code,ordinal++);
    }
    private AnnotationLibrary(){}

    private static int rank(String code){
        Integer value=BOOK_ORDER.get(code);
        return value==null?Integer.MAX_VALUE:value;
    }

    /** Returns a sorted copy: never changes database rows or the caller's list. */
    public static List<StudyAnnotations.Item> ordered(List<StudyAnnotations.Item> stored){
        ArrayList<StudyAnnotations.Item> items=new ArrayList<>(stored);
        items.sort((a,b)->{
            int cmp=Integer.compare(rank(a.book),rank(b.book));
            if(cmp!=0)return cmp;
            cmp=a.book.compareTo(b.book);
            if(cmp!=0)return cmp;
            cmp=Integer.compare(a.chapter,b.chapter);
            if(cmp!=0)return cmp;
            cmp=Integer.compare(a.verse,b.verse);
            if(cmp!=0)return cmp;
            cmp=Integer.compare(a.endVerse,b.endVerse);
            if(cmp!=0)return cmp;
            cmp=Integer.compare(a.offset,b.offset);
            if(cmp!=0)return cmp;
            cmp=Long.compare(a.created,b.created);
            return cmp!=0?cmp:Long.compare(a.id,b.id);
        });
        return items;
    }
}
