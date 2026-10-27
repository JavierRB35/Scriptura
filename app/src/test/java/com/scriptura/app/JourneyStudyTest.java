package com.scriptura.app;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;

public final class JourneyStudyTest {
    @Test public void everyStageCorrespondsToMapMarker(){
        for(BibleGeography.Journey route:BibleGeography.allJourneys()){
            assertTrue(route.title,JourneyStudy.covers(route));
            List<JourneyStudy.Stage> list=JourneyStudy.stages(route);
            assertEquals(route.stops.length,list.size());
            for(int i=0;i<list.size();i++){
                JourneyStudy.Stage stage=list.get(i);
                assertEquals(route.stops[i],stage.place.id);
                assertTrue(stage.event.length()>25);
                assertFalse(stage.scripture.isEmpty());
                assertFalse(BibleStudyReferences.links(stage.scripture).isEmpty());
            }
        }
    }
    @Test public void samePlaceCanHaveDistinctEvents(){
        List<JourneyStudy.Stage> abraham=JourneyStudy.stages(BibleGeography.journey("abraham"));
        List<JourneyStudy.Stage> jacob=JourneyStudy.stages(BibleGeography.journey("jacob"));
        assertFalse(abraham.get(0).event.equals(jacob.get(0).event));
        List<JourneyStudy.Stage> paul=JourneyStudy.stages(BibleGeography.journey("pablo-1"));
        assertEquals("antioch",paul.get(0).place.id);
        assertEquals("antioch",paul.get(paul.size()-1).place.id);
        assertFalse(paul.get(0).event.equals(paul.get(paul.size()-1).event));
    }
    @Test public void linksSupportShortenedReferencesAndChapterRanges(){
        List<BibleStudyReferences.Link> refs=BibleStudyReferences.links("Gn 11:31; 12:4; 27:43");
        assertEquals(3,refs.size());
        assertEquals("Gn 12:4",refs.get(1).query);
        assertEquals("Gn 27:43",refs.get(2).query);
        assertEquals("Mc 11",BibleStudyReferences.links("Mc 11-16").get(0).query);
        assertEquals("Gn 11:27",BibleStudyReferences.links("Gn 11:27–13:18").get(0).query);
        assertEquals("Jn 2:1-11",BibleStudyReferences.links("Jn 2:1-11").get(0).query);
        assertEquals(2,BibleStudyReferences.links("Jn 19:16-30; Jn 20:1-18").size());
    }
    @Test public void everyPointUrlOpensGoogleWithCoordinates(){
        String point=MapLinks.point(32.748,35.342);
        assertTrue(point.startsWith("https://www.google.com/maps/search/?api=1&query="));
        assertTrue(point.contains("32.748000%2C35.342000"));
        assertFalse(point.contains("openstreetmap"));
        assertTrue(MapLinks.satellite(32.748,35.342,11).contains("basemap=satellite"));
    }
}