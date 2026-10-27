package com.scriptura.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReleaseVersionsTest {
    @Test public void ignoresOlderReleases(){
        assertFalse(ReleaseVersions.isNewer("v0.4.0-build.11","0.5.0-build.12"));
        assertFalse(ReleaseVersions.isNewer("v0.5.0-build.12","0.5.0-build.12"));
    }
    @Test public void recognizesNewerBuildAndVersion(){
        assertTrue(ReleaseVersions.isNewer("v0.5.0-build.15","0.5.0-build.12"));
        assertTrue(ReleaseVersions.isNewer("v0.6.0-build.17","0.5.0-build.12"));
        assertFalse(ReleaseVersions.isNewer("v0.4.9-build.500","0.5.0-build.12"));
    }
    @Test public void rejectsMalformedAndDangerousTags(){
        assertNull(ReleaseVersions.parse("refs/tags/v0.6.0-build.17"));
        assertNull(ReleaseVersions.parse("v0.5.0-build.123.apk"));
        assertNull(ReleaseVersions.parse("v0.5.0-build.9999999999999999"));
        assertFalse(ReleaseVersions.isNewer("garbage","0.5.0-build.12"));
    }
    @Test public void comparesOfflineDevelopmentBuild(){
        assertTrue(ReleaseVersions.isNewer("v0.5.0-build.13","0.5.0"));
        assertFalse(ReleaseVersions.isNewer("v0.5.0-build.13","0.6.0"));
    }
}
