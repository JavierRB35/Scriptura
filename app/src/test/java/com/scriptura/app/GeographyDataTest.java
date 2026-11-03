package com.scriptura.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class GeographyDataTest {
    private JSONObject catalog()throws Exception{
        Path path=Paths.get("src/main/assets/geografia_catalogo.json");
        if(!Files.exists(path))path=Paths.get("app/src/main/assets/geografia_catalogo.json");
        return new JSONObject(Files.readString(path));
    }
    private GeographyData.Entry place(String id)throws Exception{
        JSONArray a=catalog().getJSONArray("entries");
        for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getString("id").equals(id))return new GeographyData.Entry(a.getJSONObject(i));
        throw new AssertionError("Falta "+id);
    }
    private GeographyData.Analysis empty(){return new GeographyData.Analysis(Collections.emptyList(),"local",false,false);}
    @Test public void catalogueContainsSourcedPlacesAndNeverMapsUnknownCoordinates()throws Exception{
        JSONObject c=catalog();assertEquals(GeographyData.VERSION,c.getString("version"));
        JSONArray rows=c.getJSONArray("entries");assertEquals(1342,rows.length());
        Set<String> ids=new HashSet<>();int located=0;
        for(int i=0;i<rows.length();i++){
            GeographyData.Entry p=new GeographyData.Entry(rows.getJSONObject(i));assertTrue(ids.add(p.id));
            assertTrue(p.source.startsWith("https://www.openbible.info/geo/ancient/"));
            assertFalse(p.description.isEmpty());if(p.located())located++;
        }
        assertEquals(1309,located);
    }
    @Test public void acts16HasAllFifteenPlacesAndSeparatesOriginFromTravel()throws Exception{
        JSONArray rows=catalog().getJSONArray("chapters").getJSONObject(0).getJSONArray("mentions");
        assertEquals(15,rows.length());Set<String> names=new HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject m=rows.getJSONObject(i);names.add(m.getString("name"));
            assertTrue(place(m.getString("place_id")).located());
            if(m.getString("name").equals("Tiatira")){
                assertEquals("procedencia",m.getString("role"));
                assertEquals(14,m.getJSONArray("verses").getInt(0));
            }
        }
        assertTrue(names.containsAll(Arrays.asList("Derbe","Listra","Iconio","Jerusalén","Frigia","Galacia","Asia","Misia","Bitinia","Tróade","Macedonia","Samotracia","Neápolis","Filipos","Tiatira")));
    }
    @Test public void accentsAndBoundariesDoNotCreateSubstringPlaces(){
        assertTrue(GeographyData.contains("Salieron de TROADE.","Tróade"));
        assertFalse(GeographyData.contains("un asiático","Asia"));
        assertFalse(GeographyData.contains("llegó a Romaña","Roma"));
    }
    @Test public void cacheDependsOnWholeChapterAndCatalogVersion(){
        assertNotEquals(GeographyData.cacheKey("Hch",16,"1 Listra"),GeographyData.cacheKey("Hch",16,"1 Listra\n\n2 Iconio"));
        assertNotEquals(GeographyData.cacheKey("Hch",16,"texto"),GeographyData.cacheKey("Hch",17,"texto"));
        assertTrue(GeographyData.cacheKey("Hch",16,"texto").startsWith(GeographyData.VERSION));
    }
    @Test public void unknownAiPlacesAreSavedWithoutTrustingModelCoordinates()throws Exception{
        String response="{\"complete\":true,\"mentions\":[{\"name\":\"Betulia\",\"place_id\":\"\",\"verses\":[1],\"role\":\"escenario\",\"description\":\"Lugar mencionado.\",\"lat\":34,\"lon\":35}]}";
        GeographyData.Analysis a=GeographyData.fromAi(response,"Jdt",1,"1  Betulia aparece en esta prueba.",Collections.emptyList(),empty(),"IA");
        assertEquals(1,a.mentions.size());assertFalse(a.mentions.get(0).place.located());assertFalse(a.reviewed);
        assertEquals(a.json().toString(),GeographyData.Analysis.parse(a.json().toString()).json().toString());
    }
    @Test public void rejectsFabricatedVerseAndWrongCatalogueIdentity()throws Exception{
        GeographyData.Entry p=place("philippi");
        String response="{\"complete\":true,\"mentions\":[{\"name\":\"Tiatira\",\"place_id\":\"philippi\",\"verses\":[14],\"role\":\"origen\",\"description\":\"Procedencia de Lidia.\"}]}";
        try{GeographyData.fromAi(response,"Hch",16,"14 Tiatira",Arrays.asList(p),empty(),"IA");fail();}catch(IllegalArgumentException expected){}
        response=response.replace("philippi","").replace("[14]","[15]");
        try{GeographyData.fromAi(response,"Hch",16,"14 Tiatira",Arrays.asList(p),empty(),"IA");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void aiNeverDropsExistingPlacesAndNeverClaimsEditorialReview()throws Exception{
        GeographyData.Mention m=new GeographyData.Mention(place("philippi"),"Filipos","escenario","Descripción revisada.",Arrays.asList(12),true);
        GeographyData.Analysis base=new GeographyData.Analysis(Arrays.asList(m),"local",false,false);
        GeographyData.Analysis result=GeographyData.fromAi("{\"complete\":true,\"mentions\":[]}","Hch",16,"12 Filipos",Collections.emptyList(),base,"IA");
        assertEquals(1,result.mentions.size());assertFalse(result.reviewed);
    }
    @Test public void geographicalPromptNeverTruncatesAChapterAt9000Characters()throws Exception{
        String scripture="1  "+"a".repeat(10000)+"\n\n2  Tiatira";
        String prompt=GeographyData.prompt("Hch 16",scripture,Collections.emptyList());
        assertTrue(prompt.endsWith(scripture));assertTrue(prompt.contains("2  Tiatira"));
    }
}
