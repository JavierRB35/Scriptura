package com.scriptura.app;

import java.util.regex.Pattern;

/** Texto destinado a voz, sin modificar las referencias visibles ni las anotaciones. */
final class BibleSpeechText {
    // Misma estructura de numeración que BibleStore.passage y AnnotationAnchors.
    private static final Pattern VERSE_MARKER=Pattern.compile("(?m)(^|\\n\\n)[0-9]+ {2}");

    private BibleSpeechText(){}

    static String chapter(String book,int number,String displayed){
        if(book==null||book.trim().isEmpty()||number<1)
            throw new IllegalArgumentException("Capítulo no válido");
        String reading=displayed==null?"":displayed.trim();
        reading=VERSE_MARKER.matcher(reading).replaceAll("$1").trim();
        return book.trim()+". Capítulo "+number+".\n\n"+reading;
    }
}
