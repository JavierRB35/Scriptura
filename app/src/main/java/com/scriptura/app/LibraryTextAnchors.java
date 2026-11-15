package com.scriptura.app;

/** Anclajes por cita literal y desplazamiento, sin atribuir versículos a manuscritos. */
final class LibraryTextAnchors {
    private LibraryTextAnchors(){}

    static String selection(String text,int start,int end){
        if(text==null||start<0||end>text.length()||start>=end||end-start>4000)return null;
        String selected=text.substring(start,end);
        return selected.trim().isEmpty()?null:selected;
    }

    static int locate(String text,int offset,String quote){
        if(text==null||quote==null||quote.isEmpty())return -1;
        if(offset>=0&&offset+quote.length()<=text.length()
                &&text.regionMatches(offset,quote,0,quote.length()))return offset;
        // Ediciones futuras pueden desplazar un fragmento, pero nunca elegir
        // arbitrariamente entre dos apariciones idénticas.
        int first=text.indexOf(quote);
        if(first>=0&&text.indexOf(quote,first+1)<0)return first;
        return -1;
    }
}
