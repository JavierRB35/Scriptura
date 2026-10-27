package com.scriptura.app;

import org.junit.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

public class BibleGeographyTest {
    @Test public void everyRouteHasExistingStops() {
        assertTrue(BibleGeography.journeyCount()>=8);
        for(BibleGeography.Journey route:BibleGeography.allJourneys()){
            assertNotNull(route.id);
            assertTrue(route.stops.length>=2);
            assertEquals(route.stops.length,BibleGeography.stops(route).size());
            for(String id:route.stops)assertNotNull(route.title+": "+id,BibleGeography.place(id));
        }
    }
    @Test public void placesAreUniqueAndWithinGeographicBounds() {
        assertTrue(BibleGeography.placeCount()>=30);
        HashSet<String> ids=new HashSet<>();
        for(BibleGeography.Place p:BibleGeography.allPlaces()){
            assertTrue(ids.add(p.id));
            assertTrue(p.lat>=-90 && p.lat<=90);
            assertTrue(p.lon>=-180 && p.lon<=180);
            assertFalse(p.refs.isEmpty());
            assertFalse(p.certainty.isEmpty());
        }
    }
    @Test public void atlasIsRelatedToCorrectBooksAndPassages() {
        assertEquals(73,Books.NAMES.size());
        assertTrue(BibleGeography.placesForBook("Gn").size()>3);
        assertTrue(BibleGeography.placesForBook("Jn").size()>3);
        assertTrue(BibleGeography.journeysForBook("Hch").size()>1);
        assertTrue(BibleGeography.search("Nazaret").stream().anyMatch(p->p.id.equals("nazareth")));
        assertTrue(BibleGeography.mentionedInPassage("Lc","Jesús volvió a Nazaret.").stream().anyMatch(p->p.id.equals("nazareth")));
    }
    @Test public void uncertainLocationsAreNotPresentedAsExact() {
        assertEquals(BibleGeography.DEBATED,BibleGeography.place("sinai").certainty);
        assertEquals(BibleGeography.DEBATED,BibleGeography.place("ur").certainty);
        assertEquals(BibleGeography.REGION,BibleGeography.place("egypt").certainty);
    }
}
