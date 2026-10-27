package com.scriptura.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Atlas divulgativo sin dependencia de API. Coordenadas modernas orientativas:
 * no prueban la identificación de un asentamiento antiguo ni el camino seguido.
 * Las referencias son citas bíblicas, no reconstrucciones arqueológicas.
 */
public final class BibleGeography {
    private BibleGeography() {}

    public static final String WELL="Identificación ampliamente aceptada";
    public static final String APPROX="Localización aproximada";
    public static final String DEBATED="Identificación discutida";
    public static final String REGION="Región, no punto exacto";

    public static final class Place {
        public final String id,name,modern,certainty,description,refs,books;
        public final double lat,lon;
        Place(String id,String name,String modern,double lat,double lon,
              String certainty,String description,String refs,String books) {
            this.id=id;this.name=name;this.modern=modern;this.lat=lat;this.lon=lon;
            this.certainty=certainty;this.description=description;this.refs=refs;this.books=books;
        }
    }

    public static final class Journey {
        public final String id,title,person,summary,refs,books;
        public final String[] stops;
        Journey(String id,String title,String person,String summary,String refs,String books,String... stops){
            this.id=id;this.title=title;this.person=person;this.summary=summary;
            this.refs=refs;this.books=books;this.stops=stops;
        }
    }

    private static final LinkedHashMap<String,Place> PLACES=new LinkedHashMap<>();
    private static final ArrayList<Journey> JOURNEYS=new ArrayList<>();
    static {
        add("ur","Ur de los caldeos","Tell el-Muqayyar (propuesta)",30.963,46.104,DEBATED,
            "Ur meridional es una identificación habitual, pero existen hipótesis alternativas sobre la Ur de Abraham.","Gn 11:28-31; 15:7","Gn");
        add("haran","Harán","Harran, Turquía",36.863,39.035,WELL,
            "Ciudad mesopotámica relacionada con la familia de Abraham y la estancia de Jacob.","Gn 11:31; 12:4; 27:43","Gn");
        add("shechem","Siquem","Tell Balata, zona de Nablus",32.214,35.283,APPROX,
            "Lugar donde Abraham levanta un altar y donde se detiene Jacob.","Gn 12:6; 33:18; Jos 24:1","Gn,Jos,Jc,1R,Jn");
        add("bethel","Betel","Beitin (identificación tradicional)",31.935,35.223,DEBATED,
            "Relacionado con el sueño de Jacob. La identificación exacta del sitio antiguo es discutida.","Gn 28:10-22; 35:1-15","Gn,Jos,Jc,1R,2R");
        add("hebron","Hebrón","Al-Jalil / Hebrón",31.533,35.095,APPROX,
            "Ciudad de los relatos patriarcales, vinculada a Mambré y a David.","Gn 13:18; 23:2; 2S 2:1","Gn,Jos,2S,1Cro");
        add("beersheba","Berseba","Tel Be'er Sheva (y área histórica)",31.245,34.841,APPROX,
            "Lugar vinculado a Abraham, Isaac y Jacob.","Gn 21:31-33; 26:23; 28:10","Gn,1R");
        add("egypt","Egipto","Valle del Nilo (punto meramente regional)",30.045,31.236,REGION,
            "Región clave para José y el Éxodo. El marcador no indica un lugar concreto de estancia.","Gn 12:10; 46:1-7; Ex 1","Gn,Ex,Nm,Dt,Is,Jr,Mt");
        add("sinai","Sinaí / Horeb","Península del Sinaí (marcador ilustrativo)",28.539,33.975,DEBATED,
            "No existe consenso sobre la localización exacta del monte bíblico ni del recorrido del Éxodo.","Ex 3:1; 19:1-20; Dt 1:6","Ex,Nm,Dt,1R");
        add("nebo","Monte Nebo","Área del monte Nebo, Jordania",31.767,35.727,APPROX,
            "Lugar vinculado a la visión de la Tierra Prometida por Moisés.","Dt 34:1-4","Nm,Dt");
        add("jordan","Río Jordán","Río Jordán (punto de referencia)",32.000,35.562,REGION,
            "Río que aparece en diversos relatos del Antiguo Testamento y en los Evangelios.","Jos 3; Mc 1:5-9","Jos,2R,Mt,Mc,Lc,Jn");
        add("jericho","Jericó","Tell es-Sultan / Jericó",31.871,35.445,APPROX,
            "Ciudad próxima al valle del Jordán, en Josué y en el ministerio de Jesús.","Jos 6; Lc 18:35; 19:1","Jos,1R,2R,Mt,Mc,Lc");
        add("jerusalem","Jerusalén","Jerusalén",31.778,35.235,WELL,
            "Centro de la monarquía, del Templo, de la pasión y resurrección de Jesús, y de la Iglesia primitiva.","2S 5; Mc 11-16; Hch 2","2S,1R,2R,1Cro,2Cro,Esd,Ne,Sal,Is,Jr,Lm,Ez,Dn,Za,Mt,Mc,Lc,Jn,Hch,Ga");
        add("bethlehem","Belén","Belén",31.704,35.203,WELL,
            "Ciudad asociada al nacimiento de Jesús y a la historia de David.","Rt 1:19; 1S 16; Lc 2:4-7","Rt,1S,Mt,Lc");
        add("nazareth","Nazaret","Nazaret",32.700,35.298,WELL,
            "Ciudad de Galilea vinculada a la infancia y vida cotidiana de Jesús.","Lc 1:26; 2:39; Mc 6:1","Mt,Mc,Lc,Jn");
        add("capernaum","Cafarnaún","Kfar Nahum / Cafarnaún",32.881,35.575,WELL,
            "Localidad a orillas del lago de Galilea, escenario frecuente de la predicación y los milagros de Jesús.","Mt 4:13; Mc 1:21; Jn 6:24","Mt,Mc,Lc,Jn");
        add("galilee","Lago de Galilea","Kineret / mar de Galilea",32.824,35.589,REGION,
            "Lago alrededor del cual se desarrollan numerosos relatos evangélicos. El punto representa el lago.","Mc 1:16; Lc 5:1","Mt,Mc,Lc,Jn");
        add("cana","Caná de Galilea","Kafr Kanna (identificación tradicional)",32.748,35.342,DEBATED,
            "Lugar de las bodas narradas en Juan 2. Se proponen distintos emplazamientos para la Caná antigua.","Jn 2:1-11; 4:46","Jn");
        add("nain","Naín","Nein, Galilea",32.632,35.351,APPROX,
            "Localidad mencionada en el episodio de la resurrección del hijo de la viuda.","Lc 7:11-17","Lc");
        add("magdala","Magdala","Migdal, lago de Galilea",32.839,35.514,APPROX,
            "Localidad galilea relacionada tradicionalmente con María Magdalena.","Lc 8:2","Mt,Mc,Lc,Jn");
        add("bethany","Betania","Al-Eizariya, cerca de Jerusalén",31.771,35.263,APPROX,
            "Población asociada con Marta, María y Lázaro; no confundir con Betania al otro lado del Jordán.","Jn 11; Lc 10:38-42","Mt,Mc,Lc,Jn");
        add("tyre","Tiro","Sour / Tiro, Líbano",33.271,35.196,WELL,
            "Ciudad costera fenicia mencionada en el Antiguo Testamento y en los Evangelios.","1R 5; Mc 7:24","1R,Is,Ez,Mt,Mc");
        add("sidon","Sidón","Saida / Sidón, Líbano",33.563,35.375,WELL,
            "Puerto fenicio y enclave mediterráneo mencionado en narraciones bíblicas.","1R 17:9; Mc 7:31","1R,Mt,Mc,Lc,Hch");
        add("banias","Cesarea de Filipo","Banias, norte de Israel",33.249,35.694,APPROX,
            "Entorno de la confesión de Pedro según Mateo y Marcos.","Mt 16:13; Mc 8:27","Mt,Mc");
        add("damascus","Damasco","Damasco, Siria",33.514,36.293,WELL,
            "Ciudad hacia la que se dirigía Saulo en el relato de Hechos.","Hch 9:1-22","Gn,1R,2R,Is,Jr,Hch,2Co,Ga");
        add("antioch","Antioquía de Siria","Antakya, Turquía",36.204,36.160,WELL,
            "Importante núcleo de la Iglesia primitiva y punto de partida misionero.","Hch 11:19-26; 13:1-3","Hch,Ga");
        add("caesarea","Cesarea Marítima","Parque de Cesarea, costa mediterránea",32.500,34.891,WELL,
            "Puerto romano desde el que salen y al que llegan diversos viajes de Hechos.","Hch 10; 21:8","Hch");
        add("tarsus","Tarso","Tarsus, Turquía",36.917,34.895,WELL,
            "Ciudad de procedencia de Pablo.","Hch 9:11; 21:39","Hch");
        add("salamis","Salamina de Chipre","Salamis, Chipre",35.185,33.903,APPROX,
            "Primera escala chipriota del viaje misionero descrito en Hechos 13.","Hch 13:5","Hch");
        add("paphos","Pafos","Paphos, Chipre",34.756,32.410,WELL,
            "Etapa del viaje misionero por Chipre.","Hch 13:6-13","Hch");
        add("philippi","Filipos","Filippoi, Grecia",41.014,24.288,WELL,
            "Ciudad macedonia donde se forma una comunidad cristiana.","Hch 16:11-40; Flp 1:1","Hch,Flp");
        add("thessalonica","Tesalónica","Tesalónica, Grecia",40.641,22.944,WELL,
            "Ciudad visitada por Pablo durante su segundo viaje misionero.","Hch 17:1-9; 1Ts 1","Hch,1Ts,2Ts");
        add("athens","Atenas","Atenas, Grecia",37.975,23.725,WELL,
            "Escenario del discurso de Pablo en el Areópago.","Hch 17:16-34","Hch");
        add("corinth","Corinto","Corinto antiguo, Grecia",37.905,22.880,WELL,
            "Ciudad donde Pablo permanece y a cuya comunidad dirige sus cartas.","Hch 18:1-17; 1Co 1","Hch,1Co,2Co");
        add("ephesus","Éfeso","Yacimiento de Efes, Turquía",37.940,27.342,WELL,
            "Ciudad de Asia Menor asociada con la predicación de Pablo.","Hch 19; Ef 1","Hch,Ef");
        add("malta","Malta","Isla de Malta (punto regional)",35.937,14.376,REGION,
            "Isla a la que llegan los náufragos en el viaje de Pablo a Roma.","Hch 28:1-10","Hch");
        add("rome","Roma","Roma, Italia",41.902,12.496,WELL,
            "Ciudad a la que llega Pablo según Hechos 28.","Hch 28:14-31; Rm 1","Hch,Rm,2Tm");
        journey("abraham","Viajes de Abraham","Abraham",
            "Etapas narradas en Génesis; no conocemos los caminos concretos entre localidades.",
            "Gn 11:27–13:18; 21:31","Gn",
            "ur","haran","shechem","bethel","egypt","hebron","beersheba");
        journey("jacob","Itinerario de Jacob","Jacob",
            "Secuencia orientativa de lugares de Génesis. No representa todos los movimientos ni sus rutas exactas.",
            "Gn 28–35","Gn",
            "beersheba","bethel","haran","shechem","bethel","hebron");
        journey("exodus","Éxodo y llegada a Canaán","Moisés y el pueblo de Israel",
            "Solo regiones y lugares clave: ni la posición del Sinaí ni la ruta del Éxodo están establecidas.",
            "Ex 1–19; Nm 20–21; Dt 34; Jos 3","Ex,Nm,Dt,Jos",
            "egypt","sinai","nebo","jordan","jericho");
        journey("jesus-galilea","Jesús en Galilea","Jesús",
            "Lugares que aparecen en los Evangelios. No se propone un itinerario cronológico exhaustivo.",
            "Lc 1–8; Mt 4; Mc 1–8; Jn 2","Mt,Mc,Lc,Jn",
            "nazareth","cana","galilee","capernaum","nain","banias");
        journey("jesus-judea","Jesús en Judea y Jerusalén","Jesús",
            "Lugares evangélicos representativos; no se afirma que la secuencia sea un viaje concreto.",
            "Lc 2; 10; 18–24; Jn 11–20","Mt,Mc,Lc,Jn",
            "bethlehem","jericho","bethany","jerusalem");
        journey("pablo-1","Primer viaje misionero (selección)","Pablo",
            "Selección no exhaustiva: el itinerario completo comprende más escalas.",
            "Hch 13–14","Hch",
            "antioch","salamis","paphos","antioch");
        journey("pablo-2","Segundo viaje misionero (selección)","Pablo",
            "Escalas destacadas, no todos los puntos; la representación es esquemática.",
            "Hch 15:36–18:22","Hch",
            "antioch","philippi","thessalonica","athens","corinth","ephesus","caesarea");
        journey("pablo-roma","Viaje de Pablo hacia Roma (selección)","Pablo",
            "Etapas representativas con el naufragio en Malta. No reconstruye trayecto marítimo.",
            "Hch 27–28","Hch",
            "caesarea","malta","rome");
    }

    private static void add(String id,String name,String modern,double lat,double lon,
                            String certainty,String description,String refs,String books){
        PLACES.put(id,new Place(id,name,modern,lat,lon,certainty,description,refs,books));
    }
    private static void journey(String id,String title,String person,String summary,String refs,
                                String books,String... stops){
        JOURNEYS.add(new Journey(id,title,person,summary,refs,books,stops));
    }
    public static List<Place> allPlaces(){return new ArrayList<>(PLACES.values());}
    public static List<Journey> allJourneys(){return new ArrayList<>(JOURNEYS);}
    public static Place place(String id){return PLACES.get(id);}
    public static Journey journey(String id){
        for(Journey j:JOURNEYS)if(j.id.equals(id))return j;
        return null;
    }
    public static List<Place> stops(Journey j) {
        ArrayList<Place> result=new ArrayList<>();
        for(String id:j.stops){Place p=place(id);if(p!=null)result.add(p);}
        return result;
    }
    private static boolean hasBook(String csv,String book) {
        return Arrays.asList(csv.split(",")).contains(book);
    }
    public static List<Place> placesForBook(String book){
        ArrayList<Place> result=new ArrayList<>();
        for(Place p:PLACES.values())if(hasBook(p.books,book))result.add(p);
        return result;
    }
    public static List<Journey> journeysForBook(String book){
        ArrayList<Journey> result=new ArrayList<>();
        for(Journey j:JOURNEYS)if(hasBook(j.books,book))result.add(j);
        return result;
    }
    private static String fold(String s){
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","");
    }
    public static List<Place> search(String term){
        String t=fold(term==null?"":term.trim());
        ArrayList<Place> result=new ArrayList<>();
        if(t.isEmpty())return allPlaces();
        for(Place p:PLACES.values()){
            if(fold(p.name+" "+p.modern+" "+p.description+" "+p.refs).contains(t))result.add(p);
        }
        return result;
    }
    public static List<Place> mentionedInPassage(String book,String text){
        String t=fold(text==null?"":text);
        ArrayList<Place> result=new ArrayList<>();
        for(Place p:PLACES.values()){
            if(!hasBook(p.books,book))continue;
            String n=fold(p.name);
            if(n.contains("/")||n.contains(" / "))continue;
            String[] possibilities=n.split("\\s*/\\s*");
            for(String candidate:possibilities){
                if(candidate.length()>3 && (t.matches("(?s).*\\b"+java.util.regex.Pattern.quote(candidate)+"\\b.*") ||
                    (candidate.equals("lago de galilea") && t.contains("mar de galilea")))){
                    result.add(p);break;
                }
            }
        }
        return result;
    }
    public static int placeCount(){return PLACES.size();}
    public static int journeyCount(){return JOURNEYS.size();}
}
