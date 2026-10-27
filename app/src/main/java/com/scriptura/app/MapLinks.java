package com.scriptura.app;

import java.util.Locale;

/** URLs universales de Google Maps; no requieren SDK, API key ni facturación. */
public final class MapLinks {
    private MapLinks(){}
    public static String point(double latitude,double longitude){
        return String.format(Locale.US,
            "https://www.google.com/maps/search/?api=1&query=%.6f%%2C%.6f",
            latitude,longitude);
    }
    public static String satellite(double latitude,double longitude,int zoom){
        String center=String.format(Locale.US,"%.6f%%2C%.6f",latitude,longitude);
        return "https://www.google.com/maps/@?api=1&map_action=map&center="+center+
            "&zoom="+Math.max(3,Math.min(19,zoom))+"&basemap=satellite";
    }
}