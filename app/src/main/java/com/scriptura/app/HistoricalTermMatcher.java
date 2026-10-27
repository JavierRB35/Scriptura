package com.scriptura.app;

import java.text.Normalizer;
import java.util.Locale;

/** Detección conservadora de palabras completas, sin red ni análisis de datos privados. */
public final class HistoricalTermMatcher {
    private HistoricalTermMatcher(){}
    public static String normalize(String text){
        if(text==null)return "";
        String simple=Normalizer.normalize(text.toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replaceAll("[^\\p{L}\\p{N}]+"," ")
            .trim();
        return simple.replaceAll("\\s+"," ");
    }
    public static int firstPosition(String normalizedPassage,String alias){
        String key=normalize(alias);
        if(key.length()<3||normalizedPassage==null||normalizedPassage.isEmpty())return -1;
        String padded=" "+normalizedPassage+" ";
        int at=padded.indexOf(" "+key+" ");
        return at<0?-1:at;
    }
    public static boolean appliesTo(String scope,boolean newTestament){
        return "ALL".equals(scope)||(newTestament?"NT":"AT").equals(scope);
    }
}
