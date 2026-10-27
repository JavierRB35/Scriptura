package com.scriptura.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Filtro sin dependencias Android. Mantiene el orden canónico de los 73 libros. */
public final class BookIndexFilter {
    private BookIndexFilter(){}
    public static String normalize(String text){
        if(text==null)return "";
        return Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT),
            Normalizer.Form.NFD).replaceAll("\\p{M}+",""); 
    }
    /** scope 0: todos; 1: Antiguo Testamento; 2: Nuevo Testamento. */
    public static boolean matches(String book,String query,int scope){
        if(!Books.NAMES.containsKey(book))return false;
        if(scope==1&&Books.isNew(book))return false;
        if(scope==2&&!Books.isNew(book))return false;
        String haystack=normalize(Books.name(book)+" "+book);
        return haystack.contains(normalize(query));
    }
    public static List<String> find(String query,int scope){
        ArrayList<String> results=new ArrayList<>();
        for(String id:Books.NAMES.keySet())if(matches(id,query,scope))results.add(id);
        return results;
    }
}
