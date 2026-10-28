package com.scriptura.app;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Stable verse-relative text anchors; independent of Android UI and EPUB page layout. */
public final class AnnotationAnchors {
    private static final Pattern VERSE=Pattern.compile("(?m)(^|\\n\\n)([0-9]+) {2}");
    private AnnotationAnchors() {}

    public static final class Anchor {
        public final int verse,offset;
        public final String quote;
        Anchor(int verse,int offset,String quote){this.verse=verse;this.offset=offset;this.quote=quote;}
    }
    public static final class Range {
        public final int start,end;
        Range(int start,int end){this.start=start;this.end=end;}
    }
    private static final class Verse {
        final int number,start,end;
        Verse(int n,int s,int e){number=n;start=s;end=e;}
    }
    private static List<Verse> verses(String displayed){
        ArrayList<Verse> list=new ArrayList<>();
        Matcher m=VERSE.matcher(displayed);
        int lastNumber=0,lastStart=-1;
        while(m.find()){
            if(lastStart>=0)list.add(new Verse(lastNumber,lastStart,m.start()));
            lastNumber=Integer.parseInt(m.group(2));
            lastStart=m.end();
        }
        if(lastStart>=0)list.add(new Verse(lastNumber,lastStart,displayed.length()));
        return list;
    }
    /** Require a nonempty selection entirely inside one verse's text. */
    public static Anchor fromSelection(String displayed,int begin,int end){
        if(displayed==null||begin<0||end>displayed.length()||begin>=end)return null;
        while(begin<end&&Character.isWhitespace(displayed.charAt(begin)))begin++;
        while(end>begin&&Character.isWhitespace(displayed.charAt(end-1)))end--;
        if(begin>=end)return null;
        for(Verse verse:verses(displayed)){
            if(begin>=verse.start&&end<=verse.end)
                return new Anchor(verse.number,begin-verse.start,displayed.substring(begin,end));
        }
        return null;
    }
    /** Fail closed if the quote no longer matches or reanchoring would be ambiguous. */
    public static Range inDisplay(String displayed,int verse,int offset,String quote){
        if(displayed==null||quote==null||quote.isEmpty())return null;
        for(Verse v:verses(displayed)){
            if(v.number!=verse)continue;
            int start=v.start+offset;
            if(offset>=0&&start+quote.length()<=v.end&&
                displayed.regionMatches(start,quote,0,quote.length()))
                return new Range(start,start+quote.length());
            int match=displayed.indexOf(quote,v.start);
            if(match>=v.start&&match+quote.length()<=v.end&&
                displayed.indexOf(quote,match+1)>=v.end)
                return new Range(match,match+quote.length());
            return null;
        }
        return null;
    }
    public static int firstVerse(String displayed){
        List<Verse> verses=verses(displayed);
        return verses.isEmpty()?-1:verses.get(0).number;
    }
}