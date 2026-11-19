package com.scriptura.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Síntesis de voz Android. La última locución completada permite encadenar capítulos. */
final class ReaderSpeech {
    interface Listener {
        void onSpeaking(boolean active);
        void onError(String message);
        default void onFinished(){}
    }

    private final Handler main=new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ready=false,wanted=false,completionDelivered=false;
    private int sequence=0;
    private String utterancePrefix="",lastUtterance="",pendingText="";
    private Listener listener;

    /** Incluye el breve periodo de inicialización y el tránsito entre capítulos. */
    boolean active(){return wanted;}

    void toggle(Context context,String text,Listener callback){
        if(wanted){stop();return;}
        if(text==null||text.trim().isEmpty()){
            callback.onError("No hay texto para escuchar.");
            return;
        }
        listener=callback;
        pendingText=text;
        wanted=true;
        callback.onSpeaking(true);
        if(tts==null){
            tts=new TextToSpeech(context.getApplicationContext(),status->
                main.post(()->initialized(status)));
        }else if(ready){
            speak(pendingText);
        }
    }

    /** Solo se usa al terminar de narrar el capítulo anterior, sin reiniciar la sesión. */
    void continueWith(String text){
        if(!wanted)return; // El usuario pudo detenerlo mientras se preparaba el siguiente.
        if(text==null||text.trim().isEmpty()){
            stop();
            return;
        }
        pendingText=text;
        if(ready&&tts!=null)speak(text);
    }

    private void initialized(int status){
        ready=status==TextToSpeech.SUCCESS;
        if(!ready){
            if(tts!=null){tts.shutdown();tts=null;}
            boolean wasWanted=wanted;
            Listener callback=listener;
            wanted=false;
            if(wasWanted&&callback!=null){
                callback.onSpeaking(false);
                callback.onError("La síntesis de voz no está disponible en este dispositivo.");
            }
            return;
        }
        try{
            Locale spanish=new Locale("es","ES");
            if(tts.isLanguageAvailable(spanish)>=TextToSpeech.LANG_AVAILABLE)
                tts.setLanguage(spanish);
        }catch(Exception ignored){}
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String utteranceId){}
            @Override public void onDone(String utteranceId){
                main.post(()->finished(utteranceId));
            }
            @Override public void onError(String utteranceId){
                main.post(()->failed(utteranceId));
            }
        });
        if(wanted)speak(pendingText);
    }

    private void speak(String text){
        List<String> parts=chunks(text,TextToSpeech.getMaxSpeechInputLength()-200);
        if(parts.isEmpty()){stop();return;}
        sequence++;
        utterancePrefix="scriptura-tts-"+sequence+"-";
        lastUtterance=utterancePrefix+(parts.size()-1);
        completionDelivered=false;
        for(int i=0;i<parts.size();i++){
            int queued=tts.speak(parts.get(i),
                i==0?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,
                null,utterancePrefix+i);
            if(queued==TextToSpeech.ERROR){
                failed(utterancePrefix+i);
                break;
            }
        }
    }

    private void finished(String id){
        if(!wanted||completionDelivered||!lastUtterance.equals(id))return;
        completionDelivered=true;
        if(listener!=null)listener.onFinished();
    }

    private void failed(String id){
        if(!wanted||!id.startsWith(utterancePrefix))return;
        Listener callback=listener;
        stop();
        if(callback!=null)callback.onError("Android no pudo reproducir el texto seleccionado.");
    }

    void stop(){
        boolean wasWanted=wanted;
        wanted=false;
        completionDelivered=false;
        lastUtterance="";
        utterancePrefix="";
        pendingText="";
        sequence++;
        if(tts!=null)tts.stop();
        if(wasWanted&&listener!=null)listener.onSpeaking(false);
    }

    void shutdown(){
        stop();
        if(tts!=null){tts.shutdown();tts=null;}
        ready=false;
    }

    /** Divide capítulos largos sin cortar palabras, respetando los límites de Android TTS. */
    static List<String> chunks(String text,int limit){
        List<String> out=new ArrayList<>();
        String rest=text==null?"":text.trim();
        if(limit<200)limit=200;
        while(rest.length()>limit){
            int cut=rest.lastIndexOf(' ',limit);
            if(cut<limit/2)cut=limit;
            out.add(rest.substring(0,cut).trim());
            rest=rest.substring(cut).trim();
        }
        if(!rest.isEmpty())out.add(rest);
        return out;
    }
}
