package com.scriptura.app;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Glosario de apoyo; las acepciones varían según idioma, época y contexto. */
public final class WordMeaning {
    private static final Map<String,String> ENTRIES;
    static {
        Map<String,String> m=new LinkedHashMap<>();
        m.put("alianza","Pacto o vínculo de compromiso. En la Biblia suele designar la relación entre Dios y su pueblo, con promesas y responsabilidades.");
        m.put("amen","Expresión de afirmación: «así sea» o «es verdad». En la oración manifiesta asentimiento y confianza.");
        m.put("apostol","Enviado o mensajero. En el Nuevo Testamento designa especialmente a los enviados por Jesucristo para anunciar el Evangelio.");
        m.put("apostoles","Plural de apóstol: enviados a anunciar el Evangelio.");
        m.put("arrepentimiento","Cambio de actitud y de conducta por reconocer el mal cometido; en lenguaje bíblico se asocia a volver a Dios.");
        m.put("bienaventurados","Dichosos o felices en un sentido profundo; expresión de las bienaventuranzas para quienes reciben las promesas del Reino.");
        m.put("bendicion","Invocación o comunicación del bien. En la Biblia puede expresar el favor de Dios y el agradecimiento humano.");
        m.put("caridad","Amor expresado en entrega y cuidado de los demás; en la tradición cristiana, virtud teologal.");
        m.put("cristo","Título griego equivalente a «Mesías», «ungido»; en el Nuevo Testamento se refiere a Jesús.");
        m.put("discipulo","Seguidor o aprendiz de un maestro. Los discípulos de Jesús escuchan su enseñanza y procuran seguirlo.");
        m.put("discipulos","Seguidores y aprendices; en los Evangelios, quienes acompañan y escuchan a Jesús.");
        m.put("evangelio","«Buena noticia». En el cristianismo, anuncio de Jesucristo y también nombre de los cuatro libros que narran su vida y enseñanza.");
        m.put("exodo","Salida o partida; bíblicamente, la liberación de Israel de Egipto y su marcha hacia la tierra prometida.");
        m.put("fariseo","Miembro de un movimiento religioso judío del período del Segundo Templo, preocupado por la interpretación y práctica de la Ley.");
        m.put("fariseos","Miembros de un movimiento religioso judío del período del Segundo Templo.");
        m.put("fe","Confianza y adhesión a Dios. En contexto cristiano también supone acoger su revelación y responder a ella.");
        m.put("gentil","En textos bíblicos judíos, persona perteneciente a los pueblos no judíos.");
        m.put("gentiles","Pueblos o personas no judías, según el uso habitual de la Biblia.");
        m.put("gloria","Honor o esplendor. La gloria de Dios alude a la manifestación de su presencia y grandeza.");
        m.put("gracia","Don gratuito y favor. En la teología cristiana se refiere especialmente a la ayuda y vida que Dios comunica.");
        m.put("idolatria","Dar culto divino a ídolos o a realidades que ocupan el lugar de Dios.");
        m.put("justicia","Rectitud y obrar conforme a lo justo. En la Biblia también se relaciona con la fidelidad a Dios y la defensa del necesitado.");
        m.put("justo","Persona recta que procura obrar bien; en muchos pasajes, quien vive en fidelidad a Dios.");
        m.put("ley","Conjunto de normas; en varios libros bíblicos puede referirse a la Torá y a las enseñanzas de la alianza.");
        m.put("mana","Alimento que, según Éxodo y Números, recibió Israel durante su travesía por el desierto.");
        m.put("mesias","«Ungido». Título asociado a un enviado esperado de Dios; los cristianos lo aplican a Jesús.");
        m.put("misericordia","Compasión activa ante la necesidad y el sufrimiento de otros; en la Biblia es también un atributo de Dios.");
        m.put("pacto","Acuerdo o compromiso; en algunos pasajes, otra forma de referirse a la alianza.");
        m.put("parabola","Relato breve que, mediante situaciones conocidas, invita a comprender una enseñanza.");
        m.put("pascua","Fiesta judía vinculada al Éxodo. En el cristianismo, celebración de la pasión, muerte y resurrección de Jesús.");
        m.put("pecado","Acción, omisión o actitud contraria al bien y a la voluntad de Dios.");
        m.put("perdon","Renuncia a mantener la culpa como deuda frente al ofensor; en la Biblia se vincula a la reconciliación con Dios y con los demás.");
        m.put("profeta","Persona que anuncia un mensaje atribuido a Dios y llama a la fidelidad, a la justicia o a la conversión.");
        m.put("profetas","Personas que transmiten un mensaje atribuido a Dios, con exhortaciones y denuncias.");
        m.put("projimo","Otra persona, especialmente considerada como destinataria del amor y la ayuda.");
        m.put("redencion","Liberación o rescate; en el cristianismo, se relaciona con la salvación realizada por Cristo.");
        m.put("reino","Dominio o soberanía. «Reino de Dios» expresa la acción y señorío de Dios, no solo un territorio.");
        m.put("resurreccion","Volver a la vida después de la muerte; en la fe cristiana, centro de la proclamación acerca de Jesús.");
        m.put("sabiduria","Capacidad de comprender y vivir con buen juicio; en los libros sapienciales se asocia al respeto de Dios.");
        m.put("sacrificio","Ofrenda de un bien, a menudo en un contexto de culto. El sentido depende del libro y del ritual.");
        m.put("salvacion","Liberación de un peligro o mal; teológicamente, acción por la que Dios rescata y lleva a la vida.");
        m.put("samaritano","Habitante de Samaría o miembro de su comunidad religiosa histórica.");
        m.put("santidad","Condición de lo consagrado a Dios y vida orientada a Él.");
        m.put("sinagoga","Lugar de reunión, oración y lectura de la Escritura en comunidades judías.");
        m.put("templo","Edificio dedicado al culto; en numerosos textos bíblicos, el santuario de Jerusalén.");
        m.put("testamento","En las expresiones «Antiguo» y «Nuevo Testamento», conjunto de libros; también se relaciona con la idea de alianza.");
        m.put("ungido","Persona consagrada mediante unción; se relaciona con el título «Mesías» o «Cristo».");
        ENTRIES=Collections.unmodifiableMap(m);
    }
    private WordMeaning(){}
    public static String normalize(String text){
        if(text==null)return "";
        return Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","");
    }
    public static String local(String word){return ENTRIES.get(normalize(word));}
    public static boolean oneWord(String selection){
        if(selection==null)return false;
        String t=selection.trim();
        return !t.isEmpty()&&t.codePointCount(0,t.length())<=45&&t.matches("(?U)[\\p{L}][\\p{L}\\p{M}'’]*");
    }
    public static String selection(String original,int start,int end){
        if(original==null||start<0||end>original.length()||start>=end)return null;
        String candidate=original.substring(start,end).trim();
        return oneWord(candidate)?candidate:null;
    }
}
