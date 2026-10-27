package com.scriptura.app;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

public final class Books {
    private Books() {}
    public static final LinkedHashMap<String,String> NAMES = new LinkedHashMap<>();
    static {
        add("Gn","Génesis"); add("Ex","Éxodo"); add("Lv","Levítico"); add("Nm","Números");
        add("Dt","Deuteronomio"); add("Jos","Josué"); add("Jc","Jueces"); add("Rt","Rut");
        add("1S","1 Samuel"); add("2S","2 Samuel"); add("1R","1 Reyes"); add("2R","2 Reyes");
        add("1Cro","1 Crónicas"); add("2Cro","2 Crónicas"); add("Esd","Esdras"); add("Ne","Nehemías");
        add("Tb","Tobías"); add("Jdt","Judit"); add("Est","Ester"); add("1M","1 Macabeos");
        add("2M","2 Macabeos"); add("Jb","Job"); add("Sal","Salmos"); add("Pr","Proverbios");
        add("Qo","Eclesiastés"); add("Ct","Cantar de los Cantares"); add("Sb","Sabiduría");
        add("Si","Eclesiástico"); add("Is","Isaías"); add("Jr","Jeremías");
        add("Lm","Lamentaciones"); add("Ba","Baruc"); add("Ez","Ezequiel"); add("Dn","Daniel");
        add("Os","Oseas"); add("Jl","Joel"); add("Am","Amós"); add("Ab","Abdías");
        add("Jon","Jonás"); add("Mi","Miqueas"); add("Na","Nahúm"); add("Ha","Habacuc");
        add("So","Sofonías"); add("Ag","Ageo"); add("Za","Zacarías"); add("Ml","Malaquías");
        add("Mt","Mateo"); add("Mc","Marcos"); add("Lc","Lucas"); add("Jn","Juan");
        add("Hch","Hechos"); add("Rm","Romanos"); add("1Co","1 Corintios"); add("2Co","2 Corintios");
        add("Ga","Gálatas"); add("Ef","Efesios"); add("Flp","Filipenses"); add("Col","Colosenses");
        add("1Ts","1 Tesalonicenses"); add("2Ts","2 Tesalonicenses"); add("1Tm","1 Timoteo");
        add("2Tm","2 Timoteo"); add("Tt","Tito"); add("Flm","Filemón"); add("Hb","Hebreos");
        add("St","Santiago"); add("1P","1 Pedro"); add("2P","2 Pedro"); add("1Jn","1 Juan");
        add("2Jn","2 Juan"); add("3Jn","3 Juan"); add("Jds","Judas"); add("Ap","Apocalipsis");
    }
    private static void add(String code,String name) { NAMES.put(code,name); }
    public static String name(String code) { return NAMES.containsKey(code) ? NAMES.get(code) : code; }
    public static boolean isNew(String code) {
        boolean nt=false;
        for(String k:NAMES.keySet()) {
            if(k.equals("Mt")) nt=true;
            if(k.equals(code)) return nt;
        }
        return false;
    }
    public static String normalize(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","").replaceAll("[^a-z0-9]","");
    }
    public static String resolve(String input) {
        String q=normalize(input);
        if(q.equals("salmo")||q.equals("salmos")||q.equals("ps")||q.equals("psalm")) return "Sal";
        if(q.equals("eclesiastico")||q.equals("siracida")) return "Si";
        if(q.equals("eclesiastes")||q.equals("cohelet")) return "Qo";
        if(q.equals("hechosdelosapostoles")) return "Hch";
        if(q.equals("evangeliodesanjuan")||q.equals("sanjuan")) return "Jn";
        for(Map.Entry<String,String> e:NAMES.entrySet())
            if(q.equals(normalize(e.getKey()))||q.equals(normalize(e.getValue()))) return e.getKey();
        if(q.length()>2) {
            String only="";
            for(Map.Entry<String,String> e:NAMES.entrySet()) {
                if(normalize(e.getValue()).startsWith(q)) {
                    if(!only.isEmpty()) return "";
                    only=e.getKey();
                }
            }
            return only;
        }
        return "";
    }
}
