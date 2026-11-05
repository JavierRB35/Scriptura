package com.scriptura.app;

/** Display mode IDs are stable because they are stored in preferences. */
public final class DisplayModes {
    public static final int NORMAL=0,COMFORT=1,BATTERY=2,SYSTEM=3;
    private DisplayModes(){}
    public static int normalize(int mode){
        return mode==COMFORT||mode==BATTERY||mode==SYSTEM?mode:NORMAL;
    }
    public static String label(int mode){
        switch(normalize(mode)){
            case COMFORT:return "Lectura cómoda";
            case BATTERY:return "Ahorro de batería · OLED";
            case SYSTEM:return "Automático · según el sistema";
            default:return "Normal";
        }
    }
}
