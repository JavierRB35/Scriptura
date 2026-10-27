package com.scriptura.app;

import android.text.Html;
import java.util.*;
import java.text.*;
import java.util.regex.*;

public final class Liturgy {
    private Liturgy(){}
    public static String link(Calendar day){
        return String.format(Locale.US,"https://lecturasdeldia.org/%04d/%02d/%02d/",
            day.get(Calendar.YEAR),day.get(Calendar.MONTH)+1,day.get(Calendar.DAY_OF_MONTH));
    }
    public static String label(Calendar day){
        return new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy",new Locale("es","ES")).format(day.getTime());
    }
    public static final class Reading {
        public final String label,reference;
        public Reading(String a,String b){label=a;reference=b;}
    }
    public static final class Day {
        public String date,title,url,raw="";
        public final List<Reading> readings=new ArrayList<>();
    }
    public static Day download(Calendar c) throws Exception {
        String url=link(c);
        String html=GeminiClient.getText(url);
        // Solo se extraen metadatos/citas de la web; los textos se leen en el navegador
        // del proveedor o desde el ejemplar personal importado.
        String text=Html.fromHtml(html,Html.FROM_HTML_MODE_LEGACY).toString();
        text=text.replace('\u00A0',' ').replace("\r","");
        Day day=new Day();day.date=label(c);day.url=url;
        String[] lines=text.split("\n");
        for(int i=0;i<lines.length;i++){
            String s=lines[i].trim().replaceAll(" +"," ");
            if(s.contains("Semana del Tiempo Ordinario")||s.startsWith("Santos ")||s.startsWith("Solemnidad"))
                if(day.title==null&&s.length()<120)day.title=s;
            for(String name:new String[]{"Primera Lectura","Salmo Responsorial","Segunda Lectura","Evangelio"}){
                if(s.startsWith(name+" ")){
                    String bib=s.substring(name.length()).replace("▼","").trim();
                    if(bib.length()>2&&bib.length()<120&&day.readings.stream().noneMatch(r->r.label.equals(name)))
                        day.readings.add(new Reading(name,bib));
                }
            }
        }
        if(day.title==null)day.title="Lecturas de la misa";
        if(day.readings.isEmpty())throw new Exception("No fue posible extraer referencias de la fuente. Puedes abrir las lecturas en la web.");
        return day;
    }
    // Rechazar citas discontinuas para no mostrar lecturas equivocadas.
    public static String toLocalQuery(String ref){
        if(ref==null)return "";
        String r=ref.trim().replace('–','-').replace('—','-').replace('\u00A0',' ');
        Matcher m=Pattern.compile("^(.+?)\\s+(\\d{1,3})\\s*[,.:]\\s*(\\d{1,3})(?:\\s*-\\s*(\\d{1,3}))?\\s*$").matcher(r);
        if(!m.matches())return "";
        String code=Books.resolve(m.group(1));
        if(code.isEmpty())return "";
        int chapter=Integer.parseInt(m.group(2));
        int from=Integer.parseInt(m.group(3));
        int to=m.group(4)==null?from:Integer.parseInt(m.group(4));
        if(chapter<1||chapter>200||from<1||to>200||to<from)return "";
        return Books.name(code)+" "+chapter+":"+from+(from==to?"":"-"+to);
    }
}
