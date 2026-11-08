package com.scriptura.app;

import java.util.List;
import java.util.Objects;

/** Durable reading position and verse lookup, independent of Android screen geometry. */
public final class ReadingProgress {
    public static final String KEY="reading_position_v1";
    private ReadingProgress(){}

    public static final class Position {
        public final String book;
        public final int chapter,verse;
        public Position(String book,int chapter,int verse){
            this.book=book;this.chapter=chapter;this.verse=verse;
        }
        public String toStored(){return "v1|"+book+"|"+chapter+"|"+verse;}
        @Override public boolean equals(Object other){
            if(!(other instanceof Position))return false;
            Position p=(Position)other;
            return book.equals(p.book)&&chapter==p.chapter&&verse==p.verse;
        }
        @Override public int hashCode(){return Objects.hash(book,chapter,verse);}
    }
    public static Position fromStored(String saved){
        if(saved==null)return null;
        String[] parts=saved.split("\\|",-1);
        if(parts.length!=4||!"v1".equals(parts[0])||!Books.NAMES.containsKey(parts[1]))
            return null;
        try{
            int chapter=Integer.parseInt(parts[2]);
            int verse=Integer.parseInt(parts[3]);
            if(chapter<1||chapter>200||verse<1||verse>200)return null;
            return new Position(parts[1],chapter,verse);
        }catch(NumberFormatException error){return null;}
    }
    /**
     * A vertical scroll position belongs to a specific passage, not just to the reader screen.
     * An explicit jump to a verse takes precedence over restoring the previous scroll.
     */
    public static boolean shouldRestoreScroll(boolean samePage,boolean readingPassage,
                                              String previousRef,String currentRef,
                                              boolean explicitVerseJump){
        return samePage&&(!readingPassage||
            (!explicitVerseJump&&previousRef!=null&&previousRef.equals(currentRef)));
    }

    /** Return the verse at the first visible character, including its number prefix. */
    public static int verseAtOffset(String displayed,int offset){
        if(displayed==null||displayed.isEmpty())return -1;
        List<Integer> numbers=AnnotationAnchors.verseNumbers(displayed);
        int visible=-1;
        for(int number:numbers){
            int start=AnnotationAnchors.verseStart(displayed,number);
            int labelStart=Math.max(0,start-Integer.toString(number).length()-2);
            if(offset<labelStart)return visible<0?number:visible;
            visible=number;
        }
        return visible;
    }
}
