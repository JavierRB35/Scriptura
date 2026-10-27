package com.scriptura.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Relatos ordenados de cada viaje y pasajes específicos de cada etapa. */
public final class JourneyStudy {
    private JourneyStudy(){}

    public static final class Stage {
        public final BibleGeography.Place place;
        public final String event;
        public final String scripture;
        Stage(BibleGeography.Place place,String event,String scripture){
            this.place=place;this.event=event;this.scripture=scripture;
        }
    }

    private static final Map<String,List<String[]>> EVENTS=new LinkedHashMap<>();
    private static void put(String id,String... details){
        if(details.length%3!=0)throw new IllegalArgumentException("Etapa incompleta: "+id);
        List<String[]> list=new ArrayList<>();
        for(int i=0;i<details.length;i+=3)
            list.add(new String[]{details[i],details[i+1],details[i+2]});
        EVENTS.put(id,Collections.unmodifiableList(list));
    }
    static {
        put("abraham",
            "ur","La familia de Abram sale de Ur de los caldeos rumbo a Canaán.","Gn 11:27-31",
            "haran","La llamada de Dios impulsa a Abram a dejar Harán.","Gn 12:1-5",
            "shechem","Abram llega a Siquem y edifica un altar al Señor.","Gn 12:6-7",
            "bethel","Abram levanta un altar entre Betel y Ay y continúa su camino.","Gn 12:8-9",
            "egypt","La escasez de alimentos lleva a Abram a Egipto.","Gn 12:10-20",
            "hebron","Abram habita en Mambré y edifica otro altar.","Gn 13:14-18",
            "beersheba","Abraham establece un pacto con Abimélec en Berseba.","Gn 21:22-34");
        put("jacob",
            "beersheba","Jacob sale de Berseba hacia la tierra de sus parientes.","Gn 28:10",
            "bethel","Durante la noche sueña con la escalera que une tierra y cielo.","Gn 28:10-22",
            "haran","Jacob llega a la región donde vive Labán y conoce a Raquel.","Gn 29:1-14",
            "shechem","Al regresar, Jacob acampa ante Siquem y construye un altar.","Gn 33:18-20",
            "bethel","Dios renueva su promesa a Jacob en Betel.","Gn 35:1-15",
            "hebron","Jacob vuelve junto a Isaac, su padre, en Mambré.","Gn 35:27-29");
        put("exodus",
            "egypt","El pueblo de Israel sale de Egipto tras la Pascua.","Ex 12:31-42",
            "sinai","Israel acampa en el Sinaí y recibe la alianza.","Ex 19:1-20",
            "nebo","Moisés contempla desde el Nebo la Tierra Prometida.","Dt 34:1-4",
            "jordan","Josué conduce al pueblo a través del río Jordán.","Jos 3:1-17",
            "jericho","El libro de Josué relata la toma de Jericó.","Jos 6:1-20");
        put("jesus-galilea",
            "nazareth","El ángel anuncia a María el nacimiento de Jesús.","Lc 1:26-38",
            "cana","Jesús realiza el signo del agua convertida en vino.","Jn 2:1-11",
            "galilee","Jesús llama a los primeros discípulos junto al lago.","Lc 5:1-11",
            "capernaum","Jesús enseña en la sinagoga de Cafarnaún.","Mc 1:21-28",
            "nain","Jesús devuelve la vida al hijo de una viuda.","Lc 7:11-17",
            "banias","Pedro confiesa que Jesús es el Mesías en la región de Cesarea de Filipo.","Mt 16:13-20");
        put("jesus-judea",
            "bethlehem","Jesús nace en Belén durante el censo.","Lc 2:1-7",
            "jericho","Jesús encuentra a Zaqueo y entra en su casa.","Lc 19:1-10",
            "bethany","Jesús llama a Lázaro para que salga del sepulcro.","Jn 11:1-44",
            "jerusalem","Jerusalén es escenario de la pasión, muerte y resurrección de Jesús.","Jn 19:16-30; Jn 20:1-18");
        put("pablo-1",
            "antioch","La comunidad de Antioquía envía a Pablo y Bernabé a predicar.","Hch 13:1-3",
            "salamis","Anuncian la palabra de Dios en las sinagogas de Salamina.","Hch 13:4-5",
            "paphos","En Pafos, Pablo se encuentra con el procónsul Sergio Paulo.","Hch 13:6-12",
            "antioch","Pablo y Bernabé regresan y cuentan lo sucedido en su misión.","Hch 14:26-28");
        put("pablo-2",
            "antioch","Pablo emprende un nuevo recorrido para visitar comunidades.","Hch 15:35-41",
            "philippi","En Filipos, Lidia acoge la predicación; Pablo y Silas son encarcelados.","Hch 16:11-40",
            "thessalonica","Pablo predica en la sinagoga de Tesalónica.","Hch 17:1-9",
            "athens","Pablo anuncia al Dios desconocido ante el Areópago.","Hch 17:16-34",
            "corinth","Pablo se establece en Corinto, trabajando y enseñando.","Hch 18:1-17",
            "ephesus","Pablo hace una breve escala en Éfeso.","Hch 18:19-21",
            "caesarea","Pablo desembarca en Cesarea al regresar del viaje.","Hch 18:22");
        put("pablo-roma",
            "caesarea","Pablo, custodiado, emprende la navegación hacia Italia.","Hch 27:1-2",
            "malta","Tras el naufragio, los supervivientes llegan a Malta.","Hch 28:1-10",
            "rome","Pablo llega a Roma y continúa anunciando el Reino de Dios.","Hch 28:14-31");
    }

    public static List<Stage> stages(BibleGeography.Journey journey){
        if(journey==null)return Collections.emptyList();
        List<String[]> events=EVENTS.get(journey.id);
        if(events==null)return Collections.emptyList();
        ArrayList<Stage> result=new ArrayList<>();
        for(String[] entry:events){
            BibleGeography.Place place=BibleGeography.place(entry[0]);
            if(place!=null)result.add(new Stage(place,entry[1],entry[2]));
        }
        return Collections.unmodifiableList(result);
    }
    public static boolean covers(BibleGeography.Journey journey){
        List<Stage> stages=stages(journey);
        if(stages.size()!=journey.stops.length)return false;
        for(int i=0;i<stages.size();i++)
            if(!stages.get(i).place.id.equals(journey.stops[i]))return false;
        return true;
    }
}