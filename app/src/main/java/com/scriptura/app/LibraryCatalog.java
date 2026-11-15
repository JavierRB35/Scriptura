package com.scriptura.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catálogo adicional y autónomo: nunca añade libros a los 73 canónicos.
 * Las obras se amplían incorporando un Markdown y una entrada en catalog.json.
 */
final class LibraryCatalog {
    private static final String MANIFEST="library/catalog.json";
    private static final Pattern HEADING=Pattern.compile("^## (.+)$");
    private static final Pattern ID=Pattern.compile("[a-z][a-z0-9-]{1,39}");
    private static final String STUDY_PREFIX="LIB/";
    private static final String REFERENCE_PREFIX="LIB:";

    static final class Work {
        final String id,title,structure,sourceUrl;
        Work(String id,String title,String structure,String sourceUrl){
            this.id=id;this.title=title;this.structure=structure;this.sourceUrl=sourceUrl;
        }
        String annotationBook(){return STUDY_PREFIX+id;}
    }
    static final class Section {
        final int index;
        final String title,text;
        final boolean editorial;
        Section(int index,String title,String text,boolean editorial){
            this.index=index;this.title=title;this.text=text;this.editorial=editorial;
        }
    }

    private final Context context;
    private List<Work> works;
    private final HashMap<String,List<Section>> loaded=new HashMap<>();

    LibraryCatalog(Context context){this.context=context.getApplicationContext();}

    List<Work> works() throws IOException {
        if(works!=null)return works;
        try{
            JSONObject document=new JSONObject(read(MANIFEST,20000));
            if(document.optInt("version")!=1)throw new IOException("Versión de catálogo no admitida");
            JSONArray rows=document.getJSONArray("works");
            if(rows.length()==0||rows.length()>100)throw new IOException("Catálogo vacío o excesivo");
            LinkedHashMap<String,Work> unique=new LinkedHashMap<>();
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);
                String id=row.getString("id");
                String title=row.getString("title");
                String source=row.getString("source_url");
                String structure=row.getString("structure");
                if(!ID.matcher(id).matches()||title.trim().isEmpty()||
                        !source.startsWith("https://")||
                        !id.concat(".md").equals(row.getString("file"))||
                        unique.containsKey(id))
                    throw new IOException("Entrada de catálogo no válida");
                unique.put(id,new Work(id,title,structure,source));
            }
            works=Collections.unmodifiableList(new ArrayList<>(unique.values()));
            return works;
        }catch(org.json.JSONException error){
            throw new IOException("Catálogo de la Biblioteca inválido",error);
        }
    }

    Work find(String id) throws IOException {
        for(Work w:works())if(w.id.equals(id))return w;
        return null;
    }

    List<Section> sections(Work work) throws IOException {
        if(work==null)throw new IOException("Obra no disponible");
        List<Section> found=loaded.get(work.id);
        if(found!=null)return found;
        List<Section> parsed=parseMarkdown(read("library/"+work.id+".md",160000));
        if(parsed.isEmpty())throw new IOException("Obra sin secciones legibles: "+work.title);
        loaded.put(work.id,Collections.unmodifiableList(parsed));
        return loaded.get(work.id);
    }

    Section section(Work work,int index) throws IOException {
        List<Section> all=sections(work);
        return index>=1&&index<=all.size()?all.get(index-1):null;
    }

    static String reference(Work work,Section section) {
        return REFERENCE_PREFIX+work.id+":"+section.index;
    }
    static boolean isReference(String value){
        return value!=null&&value.startsWith(REFERENCE_PREFIX);
    }
    static String[] decodeReference(String value){
        if(!isReference(value))return null;
        String[] parts=value.substring(REFERENCE_PREFIX.length()).split(":",-1);
        if(parts.length!=2||!ID.matcher(parts[0]).matches())return null;
        try{
            int section=Integer.parseInt(parts[1]);
            if(section<1||section>2000)return null;
            return new String[]{parts[0],String.valueOf(section)};
        }catch(NumberFormatException error){return null;}
    }
    static boolean isAnnotationBook(String value){
        return value!=null&&value.startsWith(STUDY_PREFIX);
    }

    /**
     * Los encabezados ## son las divisiones documentales de cada traducción:
     * dichos, capítulos, secciones y notas editoriales. No se crean versículos.
     */
    static List<Section> parseMarkdown(String markdown) {
        ArrayList<Section> result=new ArrayList<>();
        if(markdown==null)return result;
        String heading=null;
        StringBuilder body=new StringBuilder();
        for(String raw:markdown.replace("\r\n","\n").split("\n",-1)){
            Matcher marker=HEADING.matcher(raw);
            if(marker.matches()){
                if(heading!=null)addSection(result,heading,body.toString());
                heading=marker.group(1).trim();
                body.setLength(0);
            }else if(heading!=null){
                body.append(raw).append('\n');
            }
        }
        if(heading!=null)addSection(result,heading,body.toString());
        return result;
    }

    private static void addSection(List<Section> result,String title,String sourceText){
        String readable=sourceText.trim()
            .replace("**","")
            .replaceAll("(?m)^###\\s+","")
            .replaceAll("(?m)^---+$","");
        if(readable.isEmpty())return;
        // Marcadores de laguna [ ] y páginas ⟦p. n⟧ permanecen intactos.
        boolean editorial=title.toLowerCase(java.util.Locale.ROOT).startsWith("notas")
            ||title.toLowerCase(java.util.Locale.ROOT).startsWith("nota editorial")
            ||title.toLowerCase(java.util.Locale.ROOT).startsWith("apéndice");
        result.add(new Section(result.size()+1,title,readable,editorial));
    }

    private String read(String asset,int maximum) throws IOException {
        try(InputStream input=context.getAssets().open(asset);
            ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];
            int n;
            while((n=input.read(buffer))!=-1){
                output.write(buffer,0,n);
                if(output.size()>maximum)throw new IOException("Texto de Biblioteca demasiado grande");
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
