package com.scriptura.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Chapter study stays over the reader, preserving its exact scroll and selection. */
final class ChapterPlacesDialog {
    interface Generator { AiRouter.Result generate(String prompt)throws Exception; }
    private final Activity activity;
    private final BibleStore.Passage chapter;
    private final GeographyStore store;
    private final Generator generator;
    private final boolean aiConfigured,backupAllowed;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final int foreground,muted,background;
    private final LinearLayout body;
    private final AlertDialog dialog;
    private GeographyData.Analysis analysis;
    private GeoMapView map;
    private boolean busy=false;
    private String failure="";

    static SharedPreferences preferences(Context context){return context.getSharedPreferences("scriptura_geography",Context.MODE_PRIVATE);}
    static boolean automatic(Context context){return preferences(context).getBoolean("automatic_ai",false);}
    static void disableAutomatic(Context context){preferences(context).edit().putBoolean("automatic_ai",false).putBoolean("choice_made",true).apply();}

    ChapterPlacesDialog(Activity activity,BibleStore.Passage chapter,Generator generator,
                        boolean aiConfigured,boolean backupAllowed,int foreground,int muted,int background){
        this.activity=activity;this.chapter=chapter;this.generator=generator;
        this.aiConfigured=aiConfigured;this.backupAllowed=backupAllowed;
        this.foreground=foreground;this.muted=muted;this.background=background;
        store=new GeographyStore(activity);
        body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(10),dp(16),dp(16));
        body.setBackgroundColor(background);
        ScrollView scroll=new ScrollView(activity);scroll.addView(body);
        dialog=new AlertDialog.Builder(activity).setTitle("Lugares · "+Books.name(chapter.book)+" "+chapter.chapter)
            .setView(scroll).setPositiveButton("Volver a la lectura",null).create();
        dialog.setOnDismissListener(d->{
            worker.execute(store::close);worker.shutdown();
        });
    }
    void show(){
        dialog.show();label("Consultando los lugares guardados…",16,foreground);body.addView(new ProgressBar(activity));
        worker.execute(()->{
            try{
                GeographyData.Analysis result=store.load(chapter.book,chapter.chapter,chapter.text);
                activity.runOnUiThread(()->{
                    if(!visible())return;
                    analysis=result;render();
                    if(!result.complete&&result.origin.startsWith("Catálogo local")&&aiConfigured){
                        if(automatic(activity))query();
                        else if(!preferences(activity).getBoolean("choice_made",false))authorize();
                    }
                });
            }catch(Exception error){activity.runOnUiThread(()->{
                if(!visible())return;body.removeAllViews();label("No se pudo consultar el catálogo local. "+message(error),15,foreground);
            });}
        });
    }
    void dismiss(){if(dialog.isShowing())dialog.dismiss();}
    private boolean visible(){return !activity.isDestroyed()&&!activity.isFinishing()&&dialog.isShowing();}
    private int dp(int n){return (int)(n*activity.getResources().getDisplayMetrics().density+0.5f);}
    private void label(String value,int size,int color){
        TextView text=new TextView(activity);text.setText(value);text.setTextSize(size);text.setTextColor(color);
        text.setPadding(0,dp(6),0,dp(6));body.addView(text);
    }
    private void button(String title,Runnable action){
        Button b=new Button(activity);b.setText(title);b.setAllCaps(false);b.setMinHeight(dp(48));
        b.setEnabled(!busy);b.setOnClickListener(v->action.run());body.addView(b);
    }
    private void render(){
        body.removeAllViews();map=null;
        label(analysis.mentions.size()+" lugares · capítulo completo",18,foreground);
        label(analysis.reviewed?"Contexto del capítulo revisado en el catálogo.":analysis.origin.startsWith("Catálogo local")?
            "Identificación local parcial: puede faltar algún lugar.":
            analysis.origin+". Puede contener errores u omisiones.",13,muted);
        if(!failure.isEmpty())label(failure,14,foreground);
        if(busy){label("Completando los lugares con IA…",15,foreground);body.addView(new ProgressBar(activity));}
        ArrayList<BibleGeography.Place> points=new ArrayList<>();
        for(GeographyData.Mention m:analysis.mentions)if(m.place.located())points.add(m.place.point(m.name));
        if(!points.isEmpty()){
            map=new GeoMapView(activity,points,false,p->{
                for(GeographyData.Mention m:analysis.mentions)if(m.place.id.equals(p.id)){
                    if(map!=null)map.focusPlace(p.id);showDetail(m);break;
                }
            });
            body.addView(map);final GeoMapView current=map;
            LinearLayout controls=new LinearLayout(activity);
            for(String title:new String[]{"− Alejar","+ Acercar"}){
                Button b=new Button(activity);b.setText(title);b.setAllCaps(false);
                b.setOnClickListener(v->{if(title.startsWith("−"))current.zoomOut();else current.zoomIn();});
                controls.addView(b,new LinearLayout.LayoutParams(0,-2,1));
            }
            body.addView(controls);
            label("Toca un número para conocer el lugar. Las regiones tienen un marcador orientativo; no se dibuja una ruta supuesta.",13,muted);
        }else if(!analysis.mentions.isEmpty())label("Los lugares identificados aún no tienen una ubicación verificable.",14,muted);
        else label(analysis.origin.startsWith("Catálogo local")?
            "El catálogo local no ha identificado lugares. Esto no significa que el capítulo no los mencione.":
            "La consulta no ha identificado lugares en este capítulo.",14,foreground);
        int marker=0;
        for(GeographyData.Mention m:analysis.mentions){
            String number=m.place.located()?++marker+". ":"";
            button(number+m.name+" · "+m.role,()->{
                if(map!=null&&m.place.located())map.focusPlace(m.place.id);
                showDetail(m);
            });
            label(m.description,15,foreground);
            label(reference(m),13,muted);
            if(!m.place.located())label("Sin marcador: ubicación pendiente de verificar.",13,muted);
        }
        if(!analysis.reviewed){
            button(analysis.origin.startsWith("Catálogo local")?"Completar con IA":"Revisar con IA",()->{
                if(!aiConfigured){new AlertDialog.Builder(activity).setMessage("Configura tu clave Gemini en Ajustes para completar la geografía con IA.").setPositiveButton("Entendido",null).show();return;}
                if(automatic(activity))query();else authorize();
            });
        }
        label("Datos geográficos: OpenBible.info · CC BY 4.0. Nombres españoles: Wikidata · CC0 y catálogo editorial de Scriptura.",12,muted);
        button("Fuentes y alcance del catálogo",()->new AlertDialog.Builder(activity).setTitle("Fuentes geográficas")
            .setMessage("1.342 fichas de OpenBible.info, con referencias de 66 libros. No acredita cobertura completa de los 73 libros de la edición ni de sus distintas numeraciones. Las fichas básicas se generan a partir de metadatos: no son 1.342 estudios históricos revisados.\n\nLas coordenadas son propuestas modernas; las regiones se sitúan mediante un punto orientativo. Las alternativas pueden consultarse en la fuente de cada lugar.\n\nLos resultados de IA se guardan en el teléfono y se distinguen del catálogo. La IA no puede modificar las coordenadas documentadas.")
            .setPositiveButton("Cerrar",null).setNeutralButton("Licencia CC BY 4.0",(d,w)->external("https://creativecommons.org/licenses/by/4.0/")).show());
    }
    private String reference(GeographyData.Mention m){
        StringBuilder s=new StringBuilder(chapter.book+" "+chapter.chapter+":");
        for(int n:m.verses){if(s.charAt(s.length()-1)!=':')s.append(", ");s.append(n);}
        return s.toString();
    }
    private void showDetail(GeographyData.Mention m){
        String info=m.description+"\n\n"+reference(m)+"\n\n"+m.place.modern+"\n"+
            (m.place.located()?m.place.certainty:"Sin ubicación verificada")+"\n\n"+m.place.description;
        AlertDialog.Builder b=new AlertDialog.Builder(activity).setTitle(m.name).setMessage(info).setPositiveButton("Cerrar",null);
        if(m.place.located())b.setNeutralButton("Google Maps",(d,w)->external(MapLinks.point(m.place.lat,m.place.lon)));
        if(m.place.source.startsWith("https://www.openbible.info/geo/ancient/"))
            b.setNegativeButton("Consultar fuente",(d,w)->external(m.place.source));
        b.show();
    }
    private void external(String url){
        try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception error){new AlertDialog.Builder(activity).setMessage("No se pudo abrir el enlace.").setPositiveButton("Cerrar",null).show();}
    }
    private void authorize(){
        new AlertDialog.Builder(activity).setTitle("Completar lugares con IA")
            .setMessage("Al abrir los lugares de un capítulo, Scriptura consultará primero los datos guardados. Si faltan, podrá enviar a Gemini el texto completo de ese capítulo, su referencia y los nombres candidatos del catálogo. "
                +(backupAllowed?"Si Gemini falla por cuota o disponibilidad, podrá usarse el respaldo Groq que autorizaste. ":"")
                +"No se envían el EPUB completo, tus notas ni el historial. El resultado se guarda en este teléfono. Puedes desactivar estas consultas automáticas en Ajustes. ¿Permitirlas al seleccionar «Lugares del capítulo»?")
            .setNegativeButton("Usar catálogo local",(d,w)->preferences(activity).edit().putBoolean("choice_made",true).apply())
            .setPositiveButton("Permitir y consultar",(d,w)->{
                preferences(activity).edit().putBoolean("automatic_ai",true).putBoolean("choice_made",true).apply();
                if(visible())query();
            }).show();
    }
    private void query(){
        if(busy||!visible())return;
        busy=true;failure="";render();final GeographyData.Analysis baseline=analysis;
        worker.execute(()->{
            try{
                if(!automatic(activity))throw new IllegalStateException("Las consultas geográficas automáticas están desactivadas.");
                List<GeographyData.Entry> candidates=store.candidates(chapter.book,chapter.chapter,chapter.text);
                String prompt=GeographyData.prompt(chapter.ref,chapter.text,candidates);
                AiRouter.Result response=generator.generate(prompt);
                GeographyData.Analysis result=GeographyData.fromAi(response.text,chapter.book,chapter.chapter,chapter.text,
                    candidates,baseline,response.provider+" · "+response.model);
                store.save(chapter.book,chapter.chapter,chapter.text,result);
                activity.runOnUiThread(()->{if(visible()){busy=false;analysis=result;render();}});
            }catch(Exception error){activity.runOnUiThread(()->{if(visible()){
                busy=false;failure="No se pudo completar con IA. Se conservan los datos locales. "+message(error);render();
            }});}
        });
    }
    private static String message(Exception error){return error.getMessage()==null?"Vuelve a intentarlo más tarde.":error.getMessage();}
}
