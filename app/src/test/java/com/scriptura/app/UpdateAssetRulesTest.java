package com.scriptura.app;

import org.junit.Test;
import static org.junit.Assert.*;

public final class UpdateAssetRulesTest {
    private static final String SHA="sha256:20c4a4b0b3d9b60dc39624319b17d0103ede701bba7548f5193294dc4e370317";
    @Test public void acceptsOfficialReleaseAsset(){
        assertTrue(UpdateAssetRules.valid("v0.7.6-build.127",685364994,150586,
            "Scriptura-0.7.6-build.127.apk",SHA,127));
        assertEquals(SHA.substring(7),UpdateAssetRules.digestHex(SHA));
    }
    @Test public void rejectsWrongAssetAndUnsignedMetadata(){
        assertFalse(UpdateAssetRules.valid("v0.7.6-build.127",1,150586,"other.apk",SHA,127));
        assertFalse(UpdateAssetRules.valid("v0.7.6-build.127",1,150586,
            "Scriptura-0.7.6-build.127.apk","",127));
        assertFalse(UpdateAssetRules.valid("v0.7.6-build.127",0,150586,
            "Scriptura-0.7.6-build.127.apk",SHA,127));
        assertFalse(UpdateAssetRules.valid("v0.7.6-build.127",1,-10,
            "Scriptura-0.7.6-build.127.apk",SHA,127));
        assertFalse(UpdateAssetRules.valid("v0.7.6-build.127",1,150586,
            "Scriptura-0.7.6-build.127.apk",SHA,126));
        assertFalse(UpdateAssetRules.valid("v0.7.6-build.127",1,150586,
            "Scriptura-0.7.6-build.127.apk",SHA,(int)Integer.MAX_VALUE));
    }
    @Test public void rejectsInvalidOrStaleTag(){
        assertFalse(UpdateAssetRules.valid("v0.7.6",1,150586,"Scriptura-0.7.6.apk",SHA,1));
        assertFalse(UpdateAssetRules.valid("v0.7.6-build.0",1,150586,
            "Scriptura-0.7.6-build.0.apk",SHA,0));
        assertFalse(UpdateAssetRules.valid(null,1,150586,"Scriptura.apk",SHA,1));
    }
    @Test public void publicReleaseUrlIsFixedAndCannotInjectOtherSources(){
        assertEquals(
            "https://github.com/JavierRB35/Scriptura/releases/download/v0.8.0-build.176/Scriptura-0.8.0-build.176.apk",
            UpdateAssetRules.publicDownloadUrl("JavierRB35/Scriptura",
                "v0.8.0-build.176","Scriptura-0.8.0-build.176.apk"));
        String[][] invalid={
            {"other/repo","v0.8.0-build.176","Scriptura-0.8.0-build.176.apk"},
            {"JavierRB35/Scriptura","../latest","Scriptura-../latest.apk"},
            {"JavierRB35/Scriptura","v0.8.0-build.176","other.apk"},
            {"JavierRB35/Scriptura","v0.8.0-build.176","Scriptura-0.8.0-build.176.apk/../other.apk"}
        };
        for(String[] attempt:invalid){
            try{
                UpdateAssetRules.publicDownloadUrl(attempt[0],attempt[1],attempt[2]);
                fail("La URL pública aceptó un origen no autorizado");
            }catch(IllegalArgumentException expected){}
        }
    }

    @Test public void downloadEndpointCannotBeControlledExternally(){
        assertEquals("https://api.github.com/repos/JavierRB35/Scriptura/releases/assets/123",
            UpdateAssetRules.assetApiUrl("JavierRB35/Scriptura",123));
        try{
            UpdateAssetRules.assetApiUrl("attacker/repo",123);
            fail("External repository accepted");
        }catch(IllegalArgumentException expected){}
    }
}
