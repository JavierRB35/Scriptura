package com.scriptura.app;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Builds a readable plain-text export of the user's annotations; pure logic, no Android imports. */
public final class AnnotationExport {
    private AnnotationExport(){}

    public static String build(List<StudyAnnotations.Item> items){
        StringBuilder out=new StringBuilder();
        out.append("Subrayados y notas de Scriptura\n");
        out.append("Exportados el ")
            .append(DateFormat.getDateInstance(DateFormat.LONG).format(new Date()))
            .append('\n');
        String currentBook=null;
        int currentChapter=-1;
        for(StudyAnnotations.Item item:items){
            boolean library=LibraryCatalog.isAnnotationBook(item.book);
            String displayBook=library?"Biblioteca · "+item.book.substring(4):Books.name(item.book);
            if(currentBook==null||!item.book.equals(currentBook)){
                currentBook=item.book;
                currentChapter=-1;
                out.append("\n== ").append(displayBook).append(" ==\n");
            }
            if(item.chapter!=currentChapter){
                currentChapter=item.chapter;
                out.append('\n').append(displayBook).append(library?" · sección ":" ")
                    .append(item.chapter).append('\n');
            }
            if(!library){
                out.append("· v.").append(item.verse);
                if(item.endVerse>item.verse)out.append("–").append(item.endVerse);
                out.append(" · ");
            }else out.append("· ");
            out.append(kindLabel(item.kind));
            if(!item.quote.isEmpty())
                out.append("\n  «").append(item.quote.replace('\n',' ')).append("»");
            if(!item.body.isEmpty())
                out.append("\n  ").append(item.body.replace("\n","\n  "));
            if(!item.url.isEmpty())
                out.append("\n  Enlace: ").append(item.url);
            out.append('\n');
        }
        return out.toString();
    }

    static String kindLabel(String kind){
        if(StudyAnnotations.UNDERLINE.equals(kind))return "Subrayado";
        if(StudyAnnotations.AI.equals(kind))return "Interpretación IA";
        if(StudyAnnotations.MAP.equals(kind))return "Mapa externo";
        if(StudyAnnotations.WEB.equals(kind))return "Página web";
        if(StudyAnnotations.ATLAS.equals(kind))return "Atlas de Scriptura";
        return "Nota";
    }
}
