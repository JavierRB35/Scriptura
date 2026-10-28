package com.scriptura.app;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fragmento bíblico seleccionado para la consulta explícita a Gemini; sin datos privados. */
public final class GeminiSelection {
    private static final Pattern VERSE=Pattern.compile("(?m)(^|\\n\\n)([0-9]+) {2}");
    public static final int MAX_SELECTION=1200;
    private static final int CONTEXT_RADIUS=360;
    private GeminiSelection(){}

    private static final class Marker {
        final int verse,start,end;
        Marker(int verse,int start,int end){
            this.verse=verse;this.start=start;this.end=end;
        }
    }
    public static final class Fragment {
        public final String reference,quote,context;
        public final int firstVerse,lastVerse;
        Fragment(String reference,String quote,String context,int firstVerse,int lastVerse){
            this.reference=reference;this.quote=quote;this.context=context;
            this.firstVerse=firstVerse;this.lastVerse=lastVerse;
        }
    }
    public static Fragment fromSelection(String displayed,String book,int chapter,int begin,int end){
        if(displayed==null||book==null||book.trim().isEmpty()||chapter<1||
            begin<0||end>displayed.length()||end<=begin)return null;
        while(begin<end&&Character.isWhitespace(displayed.charAt(begin)))begin++;
        while(end>begin&&Character.isWhitespace(displayed.charAt(end-1)))end--;
        if(end<=begin||end-begin>MAX_SELECTION)return null;

        Matcher matcher=VERSE.matcher(displayed);
        ArrayList<Marker> verses=new ArrayList<>();
        int previousStart=-1,previousNumber=-1;
        while(matcher.find()){
            if(previousStart>=0)verses.add(new Marker(previousNumber,previousStart,matcher.start()));
            previousNumber=Integer.parseInt(matcher.group(2));
            previousStart=matcher.end();
        }
        if(previousStart>=0)verses.add(new Marker(previousNumber,previousStart,displayed.length()));

        int first=-1,last=-1,firstTextStart=-1;
        for(Marker verse:verses){
            if(begin<verse.end&&end>verse.start){
                if(first<0){first=verse.verse;firstTextStart=verse.start;}
                last=verse.verse;
            }
        }
        // Reject a selection that starts in a verse marker or outside the biblical text.
        if(first<0||begin<firstTextStart)return null;
        String quote=displayed.substring(begin,end);
        int from=Math.max(0,begin-CONTEXT_RADIUS);
        int to=Math.min(displayed.length(),end+CONTEXT_RADIUS);
        String context=displayed.substring(from,to).trim();
        String reference=book+" "+chapter+":"+first+(first==last?"":"-"+last);
        return new Fragment(reference,quote,context,first,last);
    }
    public static String question(Fragment fragment){
        if(fragment==null)throw new IllegalArgumentException("Falta selección");
        return "Interpreta específicamente el fragmento seleccionado, considerando el "+
            "capítulo y los versículos cercanos. Explica el significado literal "+
            "y, cuando proceda, la interpretación histórica y teológica católica. "+
            "Distingue hechos del texto, tradición e hipótesis sin inventar citas. "+
            "Responde en español de forma comprensible y no te limites a resumir el capítulo. "+
            "FRAGMENTO EXACTO: «"+fragment.quote+"».";
    }
}
