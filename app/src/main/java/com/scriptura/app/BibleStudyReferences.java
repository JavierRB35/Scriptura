package com.scriptura.app;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Referencias legibles de atlas a lectura local, sin red ni claves. */
public final class BibleStudyReferences {
    private BibleStudyReferences(){}
    public static final class Link {
        public final String label,query;
        Link(String label,String query){this.label=label;this.query=query;}
    }
    private static final Pattern NUMBER=Pattern.compile("^\\s*(\\d+)(?::(\\d+)(?:[-–](\\d+))?)?.*$");
    public static List<Link> links(String references){
        ArrayList<Link> out=new ArrayList<>();
        if(references==null)return out;
        String book="";
        for(String raw:references.split(";")){
            String piece=raw.trim();
            if(piece.isEmpty())continue;
            String body=piece;
            String explicit="";
            for(String code:Books.NAMES.keySet()){
                if(piece.startsWith(code+" ")&&code.length()>explicit.length()){
                    explicit=code;body=piece.substring(code.length()).trim();
                }
            }
            if(!explicit.isEmpty())book=explicit;
            if(book.isEmpty()||body.isEmpty())continue;
            Matcher m=NUMBER.matcher(body);
            if(!m.matches())continue;
            // Lectura de varios capítulos: se abre el primero. Los rangos de versos
            // de un mismo capítulo permanecen completos.
            boolean acrossChapters=body.matches("^\\d+:\\d+[-–]\\d+:\\d+.*");
            String query=book+" "+m.group(1);
            if(m.group(2)!=null){
                query+=":"+m.group(2);
                if(!acrossChapters&&m.group(3)!=null)query+="-"+m.group(3);
            }
            String label=(explicit.isEmpty()?book+" ":explicit+" ")+body;
            if(acrossChapters||m.group(2)==null&&body.matches("\\d+[-–]\\d+.*")){
                label+=" · desde el capítulo "+m.group(1);
            }
            out.add(new Link(label,query));
        }
        return out;
    }
}