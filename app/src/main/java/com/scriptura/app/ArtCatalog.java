package com.scriptura.app;

import java.util.Arrays;
import java.util.List;

/** Selección curatorial inicial. Cada ficha enlaza a Wikimedia Commons para verificar reproducción y licencia. */
public final class ArtCatalog {
    private ArtCatalog(){}
    public static List<BiblicalArt.Painting> additional(){
        return Arrays.asList(
        new BiblicalArt.Painting("paradise","La expulsión del Paraíso","Masaccio","c. 1425","Gn","Gn 3:23-24","Adán y Eva abandonan el jardín del Edén.","Expulsion from the Garden of Eden Masaccio"),
        new BiblicalArt.Painting("adam-eve","Adán y Eva","Alberto Durero","1507","Gn","Gn 3:1-7","El relato de los primeros seres humanos y la desobediencia.","Adam and Eve Albrecht Durer 1507"),
        new BiblicalArt.Painting("babel","La torre de Babel","Pieter Brueghel el Viejo","1563","Gn","Gn 11:1-9","La ambición humana y la diversidad de lenguas.","Tower of Babel Pieter Bruegel"),
        new BiblicalArt.Painting("abraham-angels","Abraham y los tres ángeles","Rembrandt","1646","Gn","Gn 18:1-15","La hospitalidad de Abraham ante tres visitantes.","Abraham and the Three Angels Rembrandt"),
        new BiblicalArt.Painting("isaac","El sacrificio de Isaac","Caravaggio","1603","Gn","Gn 22:1-19","Prueba de Abraham y salvación de Isaac.","Sacrifice of Isaac Caravaggio"),
        new BiblicalArt.Painting("jacob","La escalera de Jacob","William Blake","c. 1800","Gn","Gn 28:10-22","El sueño de Jacob y su visión de una escalera al cielo.","Jacob's Ladder William Blake"),
        new BiblicalArt.Painting("joseph","José en Egipto","Jacopo Pontormo","1518","Gn","Gn 41:1-57","Interpretación artística de los relatos de José en Egipto.","Joseph in Egypt Pontormo"),
        new BiblicalArt.Painting("moses-child","El hallazgo de Moisés","Nicolas Poussin","1638","Ex","Ex 2:1-10","La hija del faraón encuentra al niño Moisés.","Finding of Moses Nicolas Poussin"),
        new BiblicalArt.Painting("burning-bush","La zarza ardiente","Nicolas Froment","1476","Ex","Ex 3:1-6","Dios se manifiesta a Moisés desde la zarza.","Burning Bush Nicolas Froment"),
        new BiblicalArt.Painting("red-sea","El paso del mar Rojo","Nicolas Poussin","1633-1634","Ex","Ex 14:21-31","La salida del pueblo de Israel a través del mar.","Crossing of the Red Sea Nicolas Poussin"),
        new BiblicalArt.Painting("tables","Moisés con las tablas de la Ley","Rembrandt","1659","Ex","Ex 31:18","Moisés presenta las tablas de la alianza.","Moses with the Tablets of the Law Rembrandt"),
        new BiblicalArt.Painting("calf","La adoración del becerro de oro","Nicolas Poussin","c. 1633","Ex","Ex 32:1-8","El pueblo de Israel ante el ídolo del becerro.","Adoration of the Golden Calf Poussin"),
        new BiblicalArt.Painting("balaam","Balaam y el asna","Rembrandt","1626","Nm","Nm 22:21-35","Una escena de Balaam en su viaje.","Balaam and the Ass Rembrandt"),
        new BiblicalArt.Painting("joshua","Josué ordenando detenerse al sol","John Martin","1816","Jos","Jos 10:12-14","Josué en la batalla de Gabaón.","Joshua Commanding the Sun to Stand Still John Martin"),
        new BiblicalArt.Painting("samson","Sansón y Dalila","Pedro Pablo Rubens","c. 1609","Jc","Jc 16:4-21","La traición que conduce a la captura de Sansón.","Samson and Delilah Rubens"),
        new BiblicalArt.Painting("ruth","Rut y Booz","Julius Schnorr von Carolsfeld","siglo XIX","Rt","Rt 2:1-17","Encuentro de Rut y Booz en el campo.","Ruth Boaz Schnorr von Carolsfeld"),
        new BiblicalArt.Painting("goliath","David con la cabeza de Goliat","Caravaggio","c. 1609","1S","1S 17:45-54","David vencedor de Goliat.","David with the Head of Goliath Caravaggio"),
        new BiblicalArt.Painting("david-saul","David tocando el arpa ante Saúl","Rembrandt","c. 1655","1S","1S 16:14-23","La música de David calma al rey Saúl.","David Playing the Harp before Saul Rembrandt"),
        new BiblicalArt.Painting("bathsheba","Betsabé con la carta de David","Rembrandt","1654","2S","2S 11:1-5","Retrato de Betsabé inspirado en la historia de David.","Bathsheba at Her Bath Rembrandt"),
        new BiblicalArt.Painting("solomon","El juicio de Salomón","Nicolas Poussin","1649","1R","1R 3:16-28","La sentencia del rey Salomón ante dos madres.","Judgment of Solomon Poussin"),
        new BiblicalArt.Painting("elijah","Elías y el ángel","J. de Valdés Leal","siglo XVII","1R","1R 19:5-8","Elías recibe alimento durante su camino.","Elijah fed by an Angel painting"),
        new BiblicalArt.Painting("judith","Judit decapitando a Holofernes","Artemisia Gentileschi","c. 1612-1613","Jdt","Jdt 13:1-10","Judit salva a su pueblo en el relato deuterocanónico.","Judith Slaying Holofernes Artemisia Gentileschi"),
        new BiblicalArt.Painting("esther","Ester ante Asuero","Tintoretto","siglo XVI","Est","Est 5:1-8","Ester se presenta ante el rey.","Esther before Ahasuerus Tintoretto"),
        new BiblicalArt.Painting("job","Job en su desolación","William Blake","c. 1805","Jb","Jb 2:7-10","Sufrimiento y perseverancia de Job.","Job William Blake painting"),
        new BiblicalArt.Painting("isaiah","El profeta Isaías","Miguel Ángel","c. 1511","Is","Is 6:1-8","Figura del profeta en la Capilla Sixtina.","Prophet Isaiah Michelangelo Sistine Chapel"),
        new BiblicalArt.Painting("jeremiah","El profeta Jeremías","Miguel Ángel","c. 1512","Jr","Jr 1:4-10","Representación del profeta Jeremías.","Prophet Jeremiah Michelangelo Sistine Chapel"),
        new BiblicalArt.Painting("daniel","Daniel en el foso de los leones","Pedro Pablo Rubens","c. 1615","Dn","Dn 6:16-24","Daniel permanece fiel en el foso de los leones.","Daniel in the Lions Den Rubens"),
        new BiblicalArt.Painting("jonah","El profeta Jonás","Miguel Ángel","c. 1512","Jon","Jon 2:1-11","Jonás y su relato de salvación.","Prophet Jonah Michelangelo Sistine Chapel"),
        new BiblicalArt.Painting("ann-leonardo","La Anunciación","Leonardo da Vinci","c. 1472-1476","Lc","Lc 1:26-38","El saludo de Gabriel a María.","Annunciation Leonardo da Vinci"),
        new BiblicalArt.Painting("visitation","La Visitación","Domenico Ghirlandaio","1491","Lc","Lc 1:39-56","María visita a Isabel.","Visitation Domenico Ghirlandaio"),
        new BiblicalArt.Painting("nativity","La adoración de los pastores","El Greco","1612-1614","Lc","Lc 2:8-20","Los pastores contemplan al recién nacido.","Adoration of the Shepherds El Greco"),
        new BiblicalArt.Painting("magi","La adoración de los Magos","Sandro Botticelli","c. 1475","Mt","Mt 2:1-12","Los sabios de Oriente ante Jesús.","Adoration of the Magi Botticelli"),
        new BiblicalArt.Painting("egypt-flight","Descanso durante la huida a Egipto","Caravaggio","c. 1597","Mt","Mt 2:13-15","La Sagrada Familia huye a Egipto.","Rest on the Flight into Egypt Caravaggio"),
        new BiblicalArt.Painting("baptism","El bautismo de Cristo","Andrea del Verrocchio","c. 1475","Mt","Mt 3:13-17","El bautismo de Jesús en el Jordán.","Baptism of Christ Verrocchio"),
        new BiblicalArt.Painting("temptation","Las tentaciones de Cristo","Sandro Botticelli","1481-1482","Mt","Mt 4:1-11","Tentaciones de Jesús en el desierto.","Temptations of Christ Botticelli"),
        new BiblicalArt.Painting("sermon","El Sermón de la Montaña","Carl Heinrich Bloch","1877","Mt","Mt 5:1-12","Jesús enseña las bienaventuranzas.","Sermon on the Mount Carl Bloch"),
        new BiblicalArt.Painting("cana-feast","Las bodas de Caná","Paolo Veronese","1563","Jn","Jn 2:1-11","El signo del agua transformada en vino.","Wedding at Cana Paolo Veronese"),
        new BiblicalArt.Painting("samaritan-woman","Cristo y la samaritana","Guercino","c. 1640","Jn","Jn 4:5-26","Encuentro de Jesús con la mujer de Samaría.","Christ and the Samaritan Woman Guercino"),
        new BiblicalArt.Painting("good-samaritan","El buen samaritano","Vincent van Gogh","1890","Lc","Lc 10:25-37","La ayuda al prójimo en una parábola.","Good Samaritan van Gogh"),
        new BiblicalArt.Painting("martha-mary","Cristo en casa de Marta y María","Johannes Vermeer","c. 1655","Lc","Lc 10:38-42","Las dos hermanas reciben a Jesús.","Christ in the House of Martha and Mary Vermeer"),
        new BiblicalArt.Painting("lazarus","La resurrección de Lázaro","Rembrandt","c. 1630","Jn","Jn 11:38-44","Jesús llama a Lázaro fuera del sepulcro.","Raising of Lazarus Rembrandt"),
        new BiblicalArt.Painting("entry","Entrada de Jesús en Jerusalén","Giotto","c. 1305","Mt","Mt 21:1-11","Jesús entra en Jerusalén.","Entry into Jerusalem Giotto"),
        new BiblicalArt.Painting("temple","La expulsión de los mercaderes","El Greco","c. 1570","Jn","Jn 2:13-22","Jesús expulsa a los comerciantes del templo.","Christ Cleansing the Temple El Greco"),
        new BiblicalArt.Painting("footwashing","El lavatorio de los pies","Tintoretto","c. 1548","Jn","Jn 13:1-17","Jesús lava los pies a sus discípulos.","Washing of the Feet Tintoretto"),
        new BiblicalArt.Painting("gethsemane","La oración en el huerto","Andrea Mantegna","c. 1455","Mt","Mt 26:36-46","Jesús ora en Getsemaní.","Agony in the Garden Mantegna"),
        new BiblicalArt.Painting("judas","El beso de Judas","Giotto","c. 1305","Mt","Mt 26:47-50","La captura de Jesús.","Kiss of Judas Giotto"),
        new BiblicalArt.Painting("ecce","Ecce Homo","Caravaggio","c. 1605","Jn","Jn 19:1-5","Jesús presentado a la multitud.","Ecce Homo Caravaggio"),
        new BiblicalArt.Painting("crucifixion","Cristo crucificado","Diego Velázquez","c. 1632","Jn","Jn 19:16-30","Representación barroca de la crucifixión.","Christ Crucified Velazquez"),
        new BiblicalArt.Painting("descent","El descendimiento","Rogier van der Weyden","c. 1435","Jn","Jn 19:38-42","El cuerpo de Jesús es bajado de la cruz.","Descent from the Cross Rogier van der Weyden"),
        new BiblicalArt.Painting("noli","Noli me tangere","Fra Angélico","c. 1440","Jn","Jn 20:11-18","Jesús resucitado se encuentra con María Magdalena.","Noli me tangere Fra Angelico"),
        new BiblicalArt.Painting("emmaus","La cena de Emaús","Caravaggio","1601","Lc","Lc 24:28-35","Los discípulos reconocen al Resucitado.","Supper at Emmaus Caravaggio"),
        new BiblicalArt.Painting("thomas","La incredulidad de santo Tomás","Caravaggio","c. 1602","Jn","Jn 20:24-29","Tomás comprueba las señales del Resucitado.","Incredulity of Saint Thomas Caravaggio"),
        new BiblicalArt.Painting("pentecost","Pentecostés","El Greco","1596-1600","Hch","Hch 2:1-13","La venida del Espíritu Santo a los discípulos.","Pentecost El Greco"),
        new BiblicalArt.Painting("paul-conversion","La conversión de san Pablo","Caravaggio","1601","Hch","Hch 9:1-19","La experiencia de Saulo camino de Damasco.","Conversion of Saint Paul Caravaggio"),
        new BiblicalArt.Painting("patmos","San Juan en Patmos","El Bosco","c. 1489","Ap","Ap 1:9-20","Juan recibe la visión del Apocalipsis.","Saint John on Patmos Hieronymus Bosch"),
        new BiblicalArt.Painting("apocalypse","La apertura del quinto sello","El Greco","c. 1608-1614","Ap","Ap 6:9-11","Visión apocalíptica representada por El Greco.","Opening of the Fifth Seal El Greco")
        );
    }
}
