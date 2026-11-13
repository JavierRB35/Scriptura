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
        public final int verse,endVerse,offset;
        public final String quote;
        Anchor(int verse,int offset,String quote){this(verse,verse,offset,quote);}
        Anchor(int verse,int endVerse,int offset,String quote){
            this.verse=verse;this.endVerse=endVerse;this.offset=offset;this.quote=quote;
        }
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
    /** Anchor the selection to its first and last verse within the displayed passage. */
    public static Anchor fromSelection(String displayed,int begin,int end){
        if(displayed==null||begin<0||end>displayed.length()||begin>=end)return null;
        while(begin<end&&Character.isWhitespace(displayed.charAt(begin)))begin++;
        while(end>begin&&Character.isWhitespace(displayed.charAt(end-1)))end--;
        if(begin>=end)return null;
        Verse first=null,last=null;
        for(Verse v:verses(displayed)){
            if(first==null&&begin>=v.start&&begin<v.end)first=v;
            if(end>v.start&&end<=v.end)last=v;
        }
        // Do not anchor selections starting/ending in chapter headings or verse-number labels.
        if(first==null||last==null||last.number<first.number)return null;
        return new Anchor(first.number,last.number,begin-first.start,displayed.substring(begin,end));
    }
    /** Anchor the first verse of a full chapter when an AI query has no selected fragment. */
    public static Anchor firstVerseAnchor(String displayed){
        if(displayed==null)return null;
        for(Verse verse:verses(displayed)){
            if(verse.number!=1)continue;
            int end=verse.end;
            while(end>verse.start&&Character.isWhitespace(displayed.charAt(end-1)))end--;
            if(end==verse.start)return null;
            return new Anchor(1,1,0,displayed.substring(verse.start,end));
        }
        return null;
    }

    /** Historical single-verse API retained for existing annotations and callers. */
    public static Range inDisplay(String displayed,int verse,int offset,String quote){
        return inDisplay(displayed,verse,verse,offset,quote);
    }
    /** Fail closed if either boundary or the exact selected text cannot be located safely. */
    public static Range inDisplay(String displayed,int verse,int endVerse,int offset,String quote){
        if(displayed==null||quote==null||quote.isEmpty()||endVerse<verse)return null;
        Verse first=null,last=null;
        for(Verse v:verses(displayed)){
            if(v.number==verse)first=v;
            if(v.number==endVerse)last=v;
        }
        if(first==null||last==null||last.start<first.start)return null;
        int start=first.start+offset;
        if(offset>=0&&start>=first.start&&start<first.end&&
            start+quote.length()>last.start&&start+quote.length()<=last.end&&
            displayed.regionMatches(start,quote,0,quote.length()))
            return new Range(start,start+quote.length());
        int match=displayed.indexOf(quote,first.start);
        if(match>=first.start&&match<first.end&&match+quote.length()>last.start&&
            match+quote.length()<=last.end&&
            (displayed.indexOf(quote,match+1)<0||
             displayed.indexOf(quote,match+1)>=last.end))
            return new Range(match,match+quote.length());
        return null;
    }
    public static List<Integer> verseNumbers(String displayed){
        ArrayList<Integer> values=new ArrayList<>();
        for(Verse v:verses(displayed))values.add(v.number);
        return values;
    }
    /** Offset of the verse's text in the rendered passage, useful for restoring reading position. */
    public static int verseStart(String displayed,int verse){
        if(displayed==null||verse<1)return -1;
        for(Verse value:verses(displayed)){
            if(value.number==verse)return value.start;
        }
        return -1;
    }
    public static int firstVerse(String displayed){
        List<Verse> verses=verses(displayed);
        return verses.isEmpty()?-1:verses.get(0).number;
    }
}