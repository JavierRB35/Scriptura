package com.scriptura.app;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local text-to-speech listening of a passage; nothing leaves the device. */
final class ReaderSpeech {
    interface Listener {
        void onSpeaking(boolean active);
        void onError(String message);
    }
    private TextToSpeech tts;
    private boolean ready=false,wanted=false;
    private Listener listener;

    boolean active(){return tts!=null&&tts.isSpeaking();}

    void toggle(Context context,String text,Listener listener){
        this.listener=listener;
        if(active()){stop();emitState(false);return;}
        wanted=true;
        if(tts==null){
            tts=new TextToSpeech(context.getApplicationContext(),status->{
                ready=status==TextToSpeech.SUCCESS;
                if(!ready){
                    tts=null;wanted=false;
                    listener.onError("La síntesis de voz no está disponible en este dispositivo.");
                    return;
                }
                try{
                    Locale spanish=new Locale("es","ES");
                    if(tts.isLanguageAvailable(spanish)>=TextToSpeech.LANG_AVAILABLE)tts.setLanguage(spanish);
                }catch(Exception ignored){}
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                    @Override public void onStart(String utteranceId){}
                    @Override public void onDone(String utteranceId){
                        if(tts!=null&&!tts.isSpeaking())emitState(false);
                    }
                    @Override public void onError(String utteranceId){
                        if(tts!=null&&!tts.isSpeaking())emitState(false);
                    }
                });
                if(wanted&&tts!=null)speak(text);
            });
        }else if(ready)speak(text);
        else emitState(false);
    }

    private void speak(String text){
        List<String> parts=chunks(text,TextToSpeech.getMaxSpeechInputLength()-200);
        for(int i=0;i<parts.size();i++)
            tts.speak(parts.get(i),i==0?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,null,"scriptura-tts-"+i);
        emitState(true);
    }

    void stop(){
        wanted=false;
        if(tts!=null)tts.stop();
    }

    void shutdown(){
        stop();
        if(tts!=null){tts.shutdown();tts=null;}
        ready=false;
    }

    private void emitState(boolean speaking){
        if(listener!=null){
            try{listener.onSpeaking(speaking);}
            catch(Exception ignored){}
        }
    }

    /** Split long chapters at word boundaries so the engine accepts the whole text. */
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
