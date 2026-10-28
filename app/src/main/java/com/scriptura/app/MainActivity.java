package com.scriptura.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.text.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int PICK_EPUB=1027;
    // Identidad visual v2: valores coincidentes con res/values/scriptura_colors.xml
    private final int DARK=Color.rgb(13,35,56), GOLD=Color.rgb(218,168,92), MUTED=Color.rgb(71,89,105);
    private final int BG=Color.rgb(255,252,246), SURFACE=Color.WHITE, SOFT=Color.rgb(245,231,200);
    private final int OUTLINE=Color.rgb(229,231,232), ACCENT=Color.rgb(149,100,44);
    private BibleStore store;
    private NavarraNotes notes;
    private StudyAnnotations studyAnnotations;
    private HistoricalContextStore historyContext;
    private boolean historicalExpanded=false;
    private NavarraIntroductions introductions;
    private boolean introBusy=false, introAttempted=false;
    private String indexBook="Gn";
    private String indexQuery="";
    private int indexScope=0; // 0=todos, 1=antiguo, 2=nuevo
    private int readerOrigin=1;
    private static final String GUIDE_DONE="primer_uso_v1";
    private GeminiClient ai;
    private LinearLayout column,frame,nav;
    private TextView caption;
    private int page=0;
    private int atlasMode=0;
    private String atlasBook="Gn";
    private BibleGeography.Place atlasPlace;
    private BibleGeography.Journey atlasJourney;
    private BibleStore.Passage selected;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String mood="Tranquilidad";
    private Calendar massDay=Calendar.getInstance();
    private int textSize=18;

    private int dp(float px){return (int)(getResources().getDisplayMetrics().density*px+0.5f);}
    private GradientDrawable bg(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private GradientDrawable outlined(int color,int radius){
        GradientDrawable drawable=bg(color,radius);drawable.setStroke(dp(1),OUTLINE);return drawable;
    }
    private void serif(TextView t){t.setTypeface(Typeface.create("serif",Typeface.NORMAL));}
    private void section(LinearLayout box,String heading,String caption) {
        TextView h=text(heading,20,DARK,false);serif(h);box.addView(h);
        hint(box,caption);addGap(box,4);
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setLineSpacing(dp(4),1.15f);
        return t;
    }
    private void pad(View view,int px){view.setPadding(dp(px),dp(px),dp(px),dp(px));}
    private void addGap(LinearLayout l,int h){
        View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));
    }
    private LinearLayout vertical(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private void button(LinearLayout box,String label,Runnable action,boolean primary){
        TextView b=text(label,15,primary?Color.WHITE:DARK,true);b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(48));b.setPadding(dp(14),dp(12),dp(14),dp(12));
        b.setBackground(primary?bg(DARK,12):outlined(SOFT,12));
        b.setContentDescription(label);b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(9);box.addView(b,p);
    }
    private LinearLayout card(LinearLayout dest){
        LinearLayout l=vertical();pad(l,17);l.setBackground(outlined(SURFACE,16));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(13);
        dest.addView(l,lp);return l;
    }
    private void title(LinearLayout l,String s){
        TextView v=text(s,26,DARK,false);serif(v);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(14);l.addView(v,p);
    }
    private EditText input(LinearLayout container,String hint,boolean multiline){
        EditText e=new EditText(this);e.setTextColor(DARK);e.setTextSize(16);e.setHint(hint);
        e.setSingleLine(!multiline);e.setPadding(dp(14),dp(14),dp(14),dp(14));
        e.setMinHeight(dp(50));e.setHintTextColor(MUTED);e.setBackground(outlined(BG,12));
        if(multiline){e.setMinLines(3);e.setGravity(Gravity.TOP);}
        else{e.setImeOptions(EditorInfo.IME_ACTION_SEARCH);}
        container.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;
    }
    private void hint(LinearLayout l,String s){TextView t=text(s,13,MUTED,false);l.addView(t);}
    private void showError(String s){new AlertDialog.Builder(this).setTitle("Scriptura").setMessage(s).setPositiveButton("Entendido",null).show();}
    private void async(String wait,Callable<String> task, java.util.function.Consumer<String> ok){
        Toast.makeText(this,wait,Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            try{String s=task.call();runOnUiThread(()->ok.accept(s));}
            catch(Exception e){runOnUiThread(()->showError(e.getMessage()==null?e.toString():e.getMessage()));}
        });
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(BG);
        store=new BibleStore(this);notes=new NavarraNotes(this);studyAnnotations=new StudyAnnotations(this);
        introductions=new NavarraIntroductions(this);historyContext=new HistoricalContextStore(this);ai=new GeminiClient(this);
        textSize=getPreferences(0).getInt("font",18);
        if(state!=null){page=state.getInt("page",0);mood=state.getString("mood","Tranquilidad");
            atlasMode=state.getInt("atlasMode",0);atlasBook=state.getString("atlasBook","Gn");
            indexBook=state.getString("indexBook","Gn");
            indexQuery=state.getString("indexQuery","");
            indexScope=state.getInt("indexScope",0);
            readerOrigin=state.getInt("readerOrigin",1);
            String restoredRef=state.getString("selectedRef","");
            if(!restoredRef.isEmpty())selected=store.passage(restoredRef);
            atlasPlace=BibleGeography.place(state.getString("atlasPlace",""));
            atlasJourney=BibleGeography.journey(state.getString("atlasJourney",""));
        }
        if(page==5&&selected==null)page=readerOrigin;
        render();
        if(UpdateChecker.configured(this)){
            UpdateChecker.schedule(this);
            worker.execute(()->{
                UpdateChecker.Result result=UpdateChecker.check(getApplicationContext(),false);
                if(result.available())UpdateChecker.maybeNotify(getApplicationContext(),result.update);
                runOnUiThread(()->{if(!isFinishing()&&page==0)render();});
            });
        }
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putInt("page",page);state.putString("mood",mood);
        state.putInt("atlasMode",atlasMode);state.putString("atlasBook",atlasBook);
        state.putString("indexBook",indexBook);
        state.putString("indexQuery",indexQuery);
        state.putInt("indexScope",indexScope);
        state.putInt("readerOrigin",readerOrigin);
        state.putString("selectedRef",selected==null?"":selected.ref);
        state.putString("atlasPlace",atlasPlace==null?"":atlasPlace.id);
        state.putString("atlasJourney",atlasJourney==null?"":atlasJourney.id);
    }
    private void render(){
        frame=vertical();frame.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(17),dp(12),dp(16),dp(11));head.setBackgroundColor(SURFACE);
        android.widget.ImageView mark=new android.widget.ImageView(this);
        mark.setImageResource(R.drawable.ic_scriptura_foreground);
        mark.setBackground(bg(DARK,12));
        mark.setContentDescription("Libro abierto y luz, símbolo de Scriptura");
        LinearLayout.LayoutParams markParams=new LinearLayout.LayoutParams(dp(48),dp(48));
        markParams.rightMargin=dp(10);head.addView(mark,markParams);
        LinearLayout branding=vertical();
        TextView brand=text("Scriptura",25,DARK,false);serif(brand);branding.addView(brand);
        TextView sub=text("BIBLIA · CONTEXTO · REFLEXIÓN",9,MUTED,true);
        sub.setLetterSpacing(0.08f);branding.addView(sub);
        head.addView(branding,new LinearLayout.LayoutParams(0,-2,1));
        TextView settings=text("⚙",24,DARK,false);
        settings.setGravity(Gravity.CENTER);settings.setContentDescription("Abrir ajustes");
        settings.setFocusable(true);settings.setTooltipText("Ajustes y guía de Scriptura");
        settings.setOnClickListener(v->showSettings());
        head.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));
        frame.addView(head);
        View hairline=new View(this);hairline.setBackgroundColor(OUTLINE);
        frame.addView(hairline,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        column=vertical();column.setPadding(dp(16),dp(17),dp(16),dp(24));
        scroll.addView(column);frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        // Acceso persistente a Mapas, separado de las cinco pestañas existentes.
        TextView mapsFixed=text("⌖  MAPAS BÍBLICOS  ·  Explorar lugares y recorridos",14,DARK,true);
        mapsFixed.setGravity(Gravity.CENTER);
        mapsFixed.setMinHeight(dp(44));
        mapsFixed.setBackground(outlined(SOFT,12));
        mapsFixed.setContentDescription("Abrir Mapas bíblicos");
        mapsFixed.setFocusable(true);
        mapsFixed.setTooltipText("Atlas de lugares y recorridos");
        LinearLayout.LayoutParams fixedParams=new LinearLayout.LayoutParams(-1,dp(44));
        fixedParams.setMargins(dp(13),dp(3),dp(13),dp(5));
        frame.addView(mapsFixed,fixedParams);
        mapsFixed.setOnClickListener(v->openAtlas(0));

        // Cinco pestañas reales. El lector y el atlas son destinos internos.
        nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setBackgroundColor(DARK);
        String[] names={"Inicio","Buscar","Misa","Para mí","Guardados"};
        String[] symbols={"⌂","⌕","✝","♡","★"};
        int active=(page==5||page==7||page==9)?1:(page==6||page==8?0:page);
        for(int i=0;i<names.length;i++){
            final int dest=i;boolean selected=active==i;
            int color=selected?GOLD:Color.rgb(231,236,242);
            LinearLayout item=vertical();item.setGravity(Gravity.CENTER);
            item.setContentDescription(names[i]+(selected?", pestaña seleccionada":""));
            item.setClickable(true);item.setFocusable(true);item.setMinimumHeight(dp(64));
            TextView glyph=text(symbols[i],21,color,selected);glyph.setGravity(Gravity.CENTER);
            TextView label=text(names[i],11,color,selected);label.setGravity(Gravity.CENTER);
            glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(glyph);item.addView(label);
            if(selected)item.setBackground(bg(Color.rgb(29,54,78),10));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(64),1);
            lp.setMargins(dp(2),dp(3),dp(2),dp(3));nav.addView(item,lp);
            item.setOnClickListener(v->{page=dest;render();});
        }
        frame.addView(nav);setContentView(frame);
        switch(page){
            case 0:home();break;case 1:search();break;case 2:mass();break;
            case 3:personal();break;case 4:saved();break;
            case 5:passagePage();break;case 6:atlas();break;
            case 7:bibleIndex();break;case 8:artGallery();break;
            case 9:bookOverview();break;
            default:page=0;home();
        }
    }

    private void home(){
        LinearLayout hero=vertical();hero.setBackground(bg(DARK,17));pad(hero,20);
        LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,-2);
        heroLp.bottomMargin=dp(16);column.addView(hero,heroLp);
        TextView k=text("LEE · EXPLORA · COMPRENDE",11,GOLD,true);
        k.setLetterSpacing(0.08f);hero.addView(k);addGap(hero,9);
        long picture=homeSelectionSeed();
        HomeSceneView scene=new HomeSceneView(this,
            getPreferences(0).getInt("home_image_index",0));
        LinearLayout.LayoutParams sceneParams=new LinearLayout.LayoutParams(-1,dp(150));
        sceneParams.topMargin=dp(9);sceneParams.bottomMargin=dp(7);
        hero.addView(scene,sceneParams);
        TextView title=text("Una palabra para hoy",27,Color.WHITE,false);
        serif(title);hero.addView(title);addGap(hero,7);
        hero.addView(text("Descubre las Escrituras y reflexiona desde la tradición católica.",14,Color.rgb(230,236,242),false));
        addGap(column,2);
        if(!getPreferences(0).getBoolean(GUIDE_DONE,false))welcomeCard();
        UpdateChecker.Update available=UpdateChecker.available(this);
        if(available!=null){
            LinearLayout updateCard=card(column);
            updateCard.addView(text("Nueva versión disponible",18,DARK,true));
            hint(updateCard,"Scriptura "+available.tag+" · Publicada en GitHub");
            button(updateCard,"Ver y descargar actualización",
                    ()->UpdateChecker.openRelease(this,available),true);
            hint(updateCard,"Puedes consultar las novedades de Scriptura e instalar la actualización cuando quieras.");
        }
        // El acceso privado a GitHub no se presenta como requisito de primer uso:
        // queda voluntario dentro de Ajustes → Actualizaciones.
        if(!store.ready()){
            if(getPreferences(0).getBoolean(GUIDE_DONE,false)){
                LinearLayout c=card(column);c.addView(text("Prepara tu biblioteca",20,DARK,true));
                hint(c,"Puedes explorar libros, mapas y arte sin EPUB. Para leer los textos completos y las notas de Navarra, importa tu ejemplar personal.");
                button(c,"Importar mi Biblia EPUB",this::pickEpub,true);
            }
        } else {
            hint(column,"Biblia de Navarra · "+store.count()+" versículos importados");
            long seed=homeSelectionSeed();
            hint(column,"¿Por qué estos pasajes? Scriptura propone uno del Antiguo Testamento y otro del Nuevo para descubrir distintas partes de la Biblia. No son lecturas litúrgicas ni se eligen por tu estado de ánimo.");
            readingPreview("Antiguo Testamento",store.random(false,seed*911+31));
            readingPreview("Nuevo Testamento",store.random(true,seed*911+53));
            button(column,"Ver otros dos pasajes",()->{
                getPreferences(0).edit()
                    .putLong("home_scene_seed",System.nanoTime())
                    .putInt("home_scene_day",homeDayKey())
                    .putInt("home_image_index",
                        (getPreferences(0).getInt("home_image_index",0)+1)%4)
                    .apply();
                render();
            },true);
        }
        LinearLayout c=card(column);
        section(c,"Lecturas de la misa","Referencias bíblicas del calendario litúrgico.");
        hint(c,Liturgy.label(Calendar.getInstance()));
        button(c,"Leer las lecturas de hoy",()->{page=2;massDay=Calendar.getInstance();render();},true);
        c=card(column);section(c,"Biblioteca · 73 libros","Índice con introducciones editoriales de tu ejemplar de Navarra.");
        button(c,"Abrir índice de los 73 libros",()->openIndex(),true);
        c=card(column);section(c,"Arte bíblico","Pintura clásica para comprender la recepción artística de los pasajes.");
        button(c,"Explorar obras pictóricas",()->openGallery(),false);
        c=card(column);section(c,"Lugares bíblicos","Descubre ciudades y recorridos de los relatos.");
        hint(c,"Explora lo que ocurrió en cada lugar y lee los pasajes relacionados.");
        button(c,"Explorar atlas bíblico",()->openAtlas(0),false);
    }
    private int homeDayKey(){
        Calendar c=Calendar.getInstance();
        return c.get(Calendar.YEAR)*1000+c.get(Calendar.DAY_OF_YEAR);
    }
    private long homeSelectionSeed(){
        android.content.SharedPreferences prefs=getPreferences(0);
        int today=homeDayKey();
        if(prefs.getInt("home_scene_day",-1)!=today){
            long seed=System.nanoTime()^(System.currentTimeMillis()<<1);
            prefs.edit().putInt("home_scene_day",today)
                .putLong("home_scene_seed",seed)
                .putInt("home_image_index",(int)Math.floorMod(seed,4)).apply();
            return seed;
        }
        return prefs.getLong("home_scene_seed",today*9381761L);
    }
    private void welcomeCard(){
        LinearLayout welcome=card(column);
        section(welcome,"Empieza a descubrir Scriptura",
            "Puedes explorar mapas, pinturas e índice sin registrarte ni configurar claves.");
        hint(welcome,"1. Importa tu EPUB de Navarra si quieres leer el texto completo y sus notas.");
        hint(welcome,"2. Consulta un libro desde el índice o descubre lugares en Mapas.");
        hint(welcome,"3. Las cinco pestañas inferiores reúnen búsqueda, misa, lecturas y favoritos.");
        if(!store.ready())button(welcome,"Importar mi Biblia (opcional)",()->{
            getPreferences(0).edit().putBoolean(GUIDE_DONE,true).apply();
            pickEpub();
        },true);
        button(welcome,"Explorar los 73 libros",()->{
            getPreferences(0).edit().putBoolean(GUIDE_DONE,true).apply();
            openIndex();
        },false);
        button(welcome,"Entendido · ocultar esta guía",()->{
            getPreferences(0).edit().putBoolean(GUIDE_DONE,true).apply();
            render();
        },false);
    }
    private void showQuickGuide(){
        new AlertDialog.Builder(this).setTitle("Cómo empezar con Scriptura")
            .setMessage("Scriptura funciona en parte sin conexión y sin cuentas.\n\n"+
                "• Inicio: lecturas sugeridas, índice y arte.\n"+
                "• Buscar: capítulos, versículos y palabras (requiere importar tu EPUB).\n"+
                "• Misa: lecturas litúrgicas del día (puede requerir internet).\n"+
                "• Para mí: reflexión por estado de ánimo; Gemini es opcional y requiere permiso.\n"+
                "• Guardados: tus favoritos y lecturas recientes.\n"+
                "• Mapas: acceso fijo a lugares y recorridos.\n\n"+
                "Para leer el texto y las notas de Navarra, importa tu ejemplar desde Ajustes. "+
                "No necesitas token de GitHub ni clave Gemini para explorar la biblioteca.")
            .setNegativeButton("Cerrar",null)
            .setPositiveButton("Mostrar guía en Inicio",(d,w)->{
                getPreferences(0).edit().putBoolean(GUIDE_DONE,false).apply();
                page=0;render();
            }).show();
    }
    private void readingPreview(String label,BibleStore.Passage p){
        LinearLayout c=card(column);c.addView(text(label.toUpperCase(),12,ACCENT,true));
        if(p==null){hint(c,"Importa primero tu Biblia.");return;}
        c.addView(text(p.ref,19,DARK,true));
        String summary=p.text.length()>280?p.text.substring(0,280)+"…":p.text;
        c.addView(text(summary,15,MUTED,false));
        button(c,"Leer y reflexionar",()->open(p),false);
    }
    private void search(){
        title(column,"Buscar en la Biblia");
        if(!store.ready()){
            hint(column,"La búsqueda de versículos y palabras necesita tu ejemplar EPUB de Navarra. Puedes consultar gratis el índice, los mapas y las obras sin importarlo.");
            button(column,"Importar mi Biblia para buscar",this::pickEpub,true);
            button(column,"Explorar el índice de los 73 libros",this::openIndex,false);
            button(column,"Ver obras de arte bíblico",this::openGallery,false);
            return;
        }
        LinearLayout c=card(column);
        EditText term=input(c,"Ej. Juan 3:16-18 · Salmo 23 · Génesis",false);
        term.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        term.setOnEditorActionListener((v,id,event)->{lookup(term.getText().toString());return true;});
        button(c,"Buscar referencia o palabra",()->lookup(term.getText().toString()),true);
        button(c,"Índice de libros y sus introducciones",this::openIndex,false);
        button(c,"Elegir capítulo",this::browseBooks,false);
        button(c,"Galería de arte bíblico",this::openGallery,false);
        title(column,"Consultas recientes");
        for(String ref:store.saved(false)){
            button(column,ref,()->lookup(ref),false);
        }
        hint(column,"Puedes introducir un libro, capítulo, versículo, intervalo o una palabra del texto.");
    }
    private void lookup(String query){
        if(query.trim().isEmpty())return;
        BibleStore.Passage passage=store.passage(query);
        if(passage!=null){open(passage);return;}
        List<BibleStore.Passage> hits=store.searchText(query,35);
        if(hits.isEmpty()){showError("No se ha encontrado la referencia o expresión «"+query+"».");return;}
        column.removeAllViews();title(column,"Coincidencias de «"+query+"»");
        hint(column,"Primeros "+hits.size()+" resultados de la biblioteca local.");
        for(BibleStore.Passage p:hits){
            LinearLayout c=card(column);c.addView(text(p.ref,16,DARK,true));c.addView(text(p.text,14,MUTED,false));
            c.setOnClickListener(v->open(p));
        }
    }
    private void browseBooks(){
        String[] names=Books.NAMES.values().toArray(new String[0]);String[] codes=Books.NAMES.keySet().toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Selecciona un libro")
            .setItems(names,(d,which)->browseChapters(codes[which])).setNegativeButton("Cancelar",null).show();
    }
    private void browseChapters(String book){
        int max=store.chapters(book);if(max==0){showError("Este libro no aparece en la biblioteca importada");return;}
        String[] ch=new String[max];for(int i=0;i<max;i++)ch[i]="Capítulo "+(i+1);
        new AlertDialog.Builder(this).setTitle(Books.name(book))
            .setItems(ch,(d,which)->lookup(Books.name(book)+" "+(which+1))).setNegativeButton("Cancelar",null).show();
    }
    private void open(BibleStore.Passage p){
        if(page!=5)readerOrigin=page;
        // Cada pasaje empieza con el contexto cerrado; el estado no se hereda
        // accidentalmente de la lectura anterior.
        historicalExpanded=false;
        selected=p;store.record(p.ref);page=5;render();
    }
    private void passagePage(){
        if(selected==null){page=1;render();return;}
        BibleStore.Passage p=selected;
        TextView edition=text("TEXTO BÍBLICO · UNIVERSIDAD DE NAVARRA",11,ACCENT,true);column.addView(edition);
        title(column,p.ref);
        LinearLayout c=card(column);c.setBackground(outlined(BG,16));
        TextView bible=text(p.text,textSize,DARK,false);serif(bible);
        bible.setTextIsSelectable(true);
        applyStudyMarks(bible,p);
        addDefinitionAction(bible,p);
        c.addView(bible);
        hint(c,"Mantén pulsado el texto y elige «Anotar» o «Definir». Toca un fragmento resaltado para abrir su nota completa.");
        TextView alternative=text("¿No aparece Definir? Escribir palabra",13,ACCENT,true);
        alternative.setPadding(dp(1),dp(10),dp(1),dp(10));
        alternative.setOnClickListener(v->askForWord(p));
        c.addView(alternative);
        showStudyAnnotations(c,p);
        showHistoricalContext(p);
        c=card(column);
        button(c,store.isFavorite(p.ref)?"★ Quitar de favoritos":"☆ Guardar favorito",
            ()->{store.toggleFavorite(p.ref);render();},false);
        button(c,"Copiar texto",()->{
            ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(
                ClipData.newPlainText(p.ref,p.ref+"\n"+p.text+"\nBiblia de Navarra"));
            Toast.makeText(this,"Texto copiado",Toast.LENGTH_SHORT).show();
        },false);
        button(c,"Compartir pasaje",()->{
            Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT,p.ref+"\n"+p.text+"\nBiblia de Navarra");
            startActivity(Intent.createChooser(i,"Compartir pasaje"));
        },false);
        button(c,"Añadir nota a un versículo",()->manualStudyAnnotation(p),false);
        button(c,"Comentarios de la Biblia de Navarra",()->showNavarraNote(p),false);
        button(c,"✦  Interpretación con Gemini",()->interpret(p,"Explica este pasaje de forma breve y fiel a su contexto.",false),true);
        button(c,"Profundizar o preguntar",()->ask(p),false);
        button(c,"⌖  Lugares de este pasaje",()->showPassagePlaces(p),false);
        button(c,"Atlas del libro "+Books.name(p.book),()->openBookAtlas(p.book),false);
        button(c,"Índice: "+Books.name(p.book),()->openBookInfo(p.book),false);
        java.util.List<BiblicalArt.Painting> related=BiblicalArt.forPassage(p.book,p.chapter);
        if(!related.isEmpty())
            button(c,"Ver obras pictóricas sobre este pasaje",()->openGalleryFor(p.book),false);
        LinearLayout link=card(column);
        link.addView(text("Lectura del contexto",18,DARK,true));
        button(link,"Capítulo anterior",()->openReference(p.book,p.chapter-1),false);
        button(link,"Capítulo siguiente",()->openReference(p.book,p.chapter+1),false);
        hint(link,"Continúa la lectura y descubre el contexto del pasaje.");
    }
    /** Sección desplegable dentro del lector, sin introducir pantallas nuevas. */
    private void showHistoricalContext(BibleStore.Passage passage){
        LinearLayout wrap=card(column);
        java.util.List<HistoricalContextStore.Topic> topics;
        try{
            topics=historyContext.forPassage(passage.book,passage.text);
        }catch(Exception ex){
            hint(wrap,"El catálogo histórico local no está disponible en este momento.");
            return;
        }
        TextView heading=text("▾  Contexto histórico  ·  "+topics.size()+
                (topics.size()==1?" concepto":" conceptos"),19,DARK,true);
        heading.setMinHeight(dp(48));
        heading.setPadding(dp(2),dp(12),dp(2),dp(12));
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setContentDescription("Contexto histórico, "+topics.size()+
                " conceptos. Toca para desplegar o cerrar.");
        wrap.addView(heading);
        LinearLayout details=vertical();
        wrap.addView(details);
        if(topics.isEmpty()){
            hint(details,"No se han detectado expresiones de nuestro catálogo histórico en esta lectura. No se generan explicaciones inventadas.");
        }else{
            hint(details,"Términos presentes en este pasaje. Pulsa cada concepto para ampliar su historia y consultar bibliografía.");
            addGap(details,8);
            for(HistoricalContextStore.Topic topic:topics){
                LinearLayout item=vertical();
                item.setPadding(dp(12),dp(11),dp(12),dp(10));
                item.setBackground(outlined(BG,12));
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
                params.bottomMargin=dp(9);details.addView(item,params);
                TextView term=text("›  "+topic.title,17,DARK,true);
                term.setMinHeight(dp(40));term.setGravity(Gravity.CENTER_VERTICAL);
                item.addView(term);
                item.addView(text(topic.category+" · "+topic.period,12,MUTED,false));
                addGap(item,5);
                item.addView(text(topic.brief,15,DARK,false));
                LinearLayout extra=vertical();
                extra.setPadding(0,dp(10),0,dp(3));
                extra.addView(text(topic.detail,14,DARK,false));
                addGap(extra,6);
                hint(extra,"Alcance: "+topic.certainty);
                HistoricalContextStore.Reference ref=historyContext.reference(topic.source);
                if(ref!=null){
                    TextView link=text("Bibliografía orientativa: "+ref.label,12,ACCENT,false);
                    link.setPadding(0,dp(7),0,dp(7));
                    link.setContentDescription("Abrir fuente bibliográfica: "+ref.label);
                    link.setOnClickListener(v->{
                        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(ref.url)));}
                        catch(Exception e){showError("No se pudo abrir la fuente bibliográfica.");}
                    });
                    extra.addView(link);
                }
                extra.setVisibility(View.GONE);
                item.addView(extra);
                item.setClickable(true);item.setFocusable(true);
                item.setContentDescription("Ampliar explicación de "+topic.title);
                item.setOnClickListener(v->{
                    boolean opened=extra.getVisibility()!=View.VISIBLE;
                    extra.setVisibility(opened?View.VISIBLE:View.GONE);
                    item.setContentDescription((opened?"Cerrar":"Ampliar")+" explicación de "+topic.title);
                });
            }
        }
        details.setVisibility(historicalExpanded?View.VISIBLE:View.GONE);
        heading.setOnClickListener(v->{
            historicalExpanded=!historicalExpanded;
            details.setVisibility(historicalExpanded?View.VISIBLE:View.GONE);
            heading.setText((historicalExpanded?"▴":"▾")+"  Contexto histórico  ·  "+topics.size()+
                (topics.size()==1?" concepto":" conceptos"));
        });
    }

    private void openReference(String book,int chapter){
        if(chapter<1||chapter>store.chapters(book)){showError("No hay más capítulos en ese sentido.");return;}
        BibleStore.Passage p=store.passage(Books.name(book)+" "+chapter);
        if(p!=null)open(p);
    }
    /** Acción Definir añadida al menú nativo; Copiar y Compartir no se consumen. */
    private void addDefinitionAction(TextView bible,BibleStore.Passage passage){
        DefinitionUi.attach(this,bible,ai.configured(),new DefinitionUi.Delegate(){
            @Override public void openExternal(String url){
                try{
                    Intent browser=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
                    browser.addCategory(Intent.CATEGORY_BROWSABLE);
                    startActivity(browser);
                }catch(Exception error){
                    showError("No se pudo abrir el enlace en el navegador.");
                }
            }
            @Override public void explainWithGemini(String word){
                interpret(passage,"Explica la palabra «"+word+
                    "» en el contexto de esta referencia; diferencia historia, sentido literal y teología.",true);
            }
            @Override public void showError(String message){
                MainActivity.this.showError(message);
            }
        },(quote,start,end)->{
            AnnotationAnchors.Anchor anchor=AnnotationAnchors.fromSelection(
                passage.text,start,end);
            if(anchor==null){
                showError("Selecciona texto dentro de un solo versículo. Para una nota general, usa «Añadir nota a un versículo».");
                return;
            }
            editStudyAnnotation(passage,anchor,null);
        });
    }

    /** Annotations belong to a precise verse in the user-imported EPUB. */
    private static String annotationType(StudyAnnotations.Item item){
        if(StudyAnnotations.ATLAS.equals(item.kind))return "Atlas bíblico";
        if(StudyAnnotations.MAP.equals(item.kind))return "Mapa";
        if(StudyAnnotations.WEB.equals(item.kind))return "Web";
        return "Nota";
    }
    private void applyStudyMarks(TextView bible,BibleStore.Passage passage){
        java.util.List<StudyAnnotations.Item> all=
            studyAnnotations.forChapter(passage.book,passage.chapter);
        android.text.SpannableString marked=new android.text.SpannableString(passage.text);
        final java.util.ArrayList<StudyAnnotations.Item> visible=new java.util.ArrayList<>();
        final java.util.ArrayList<AnnotationAnchors.Range> ranges=new java.util.ArrayList<>();
        for(StudyAnnotations.Item item:all){
            AnnotationAnchors.Range range=AnnotationAnchors.inDisplay(
                passage.text,item.verse,item.offset,item.quote);
            if(range==null)continue;
            int color=(StudyAnnotations.MAP.equals(item.kind)||StudyAnnotations.ATLAS.equals(item.kind))?Color.rgb(207,236,216):
                StudyAnnotations.WEB.equals(item.kind)?Color.rgb(208,229,252):
                Color.rgb(255,234,174);
            marked.setSpan(new android.text.style.BackgroundColorSpan(color),
                range.start,range.end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            marked.setSpan(new android.text.style.UnderlineSpan(),
                range.start,range.end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            visible.add(item);ranges.add(range);
        }
        bible.setText(marked,TextView.BufferType.SPANNABLE);
        if(visible.isEmpty())return;
        final float[] initial={0f,0f};
        final int slop=ViewConfiguration.get(this).getScaledTouchSlop();
        bible.setOnTouchListener((view,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                initial[0]=event.getX();initial[1]=event.getY();
                return false; // Do not interfere with native long-press selection.
            }
            if(event.getActionMasked()!=MotionEvent.ACTION_UP)return false;
            if(event.getEventTime()-event.getDownTime()>ViewConfiguration.getLongPressTimeout()/2
                ||Math.abs(event.getX()-initial[0])>slop
                ||Math.abs(event.getY()-initial[1])>slop)return false;
            int offset=bible.getOffsetForPosition(event.getX(),event.getY());
            java.util.ArrayList<StudyAnnotations.Item> touched=new java.util.ArrayList<>();
            for(int i=0;i<ranges.size();i++)
                if(offset>=ranges.get(i).start&&offset<ranges.get(i).end)
                    touched.add(visible.get(i));
            if(touched.isEmpty())return false;
            if(touched.size()==1)showStudyAnnotation(passage,touched.get(0));
            else{
                String[] labels=new String[touched.size()];
                for(int i=0;i<labels.length;i++)
                    labels[i]=annotationType(touched.get(i))+" · versículo "+touched.get(i).verse;
                new AlertDialog.Builder(this).setTitle("Anotaciones de este fragmento")
                    .setItems(labels,(dialog,index)->showStudyAnnotation(passage,touched.get(index)))
                    .setNegativeButton("Cerrar",null).show();
            }
            return true;
        });
    }
    private void showStudyAnnotations(LinearLayout parent,BibleStore.Passage passage){
        java.util.ArrayList<StudyAnnotations.Item> visible=new java.util.ArrayList<>();
        for(StudyAnnotations.Item item:studyAnnotations.forChapter(passage.book,passage.chapter))
            if(item.verse>=passage.first&&item.verse<=passage.last)visible.add(item);
        if(visible.isEmpty())return;
        addGap(parent,12);
        parent.addView(text("MIS ANOTACIONES · "+visible.size(),12,ACCENT,true));
        for(StudyAnnotations.Item item:visible){
            String label="✎  "+annotationType(item)+" · v. "+item.verse;
            if(!item.body.trim().isEmpty()){
                String snippet=item.body.trim().replace('\n',' ');
                label+=" · "+(snippet.length()>65?snippet.substring(0,65)+"…":snippet);
            }
            TextView marker=text(label,14,DARK,true);
            marker.setBackground(outlined(SURFACE,9));marker.setPadding(dp(10),dp(12),dp(10),dp(12));
            marker.setMinHeight(dp(48));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.topMargin=dp(7);parent.addView(marker,lp);
            marker.setOnClickListener(v->showStudyAnnotation(passage,item));
        }
    }
    private void manualStudyAnnotation(BibleStore.Passage passage){
        java.util.List<Integer> numbers=AnnotationAnchors.verseNumbers(passage.text);
        if(numbers.isEmpty()){showError("No se han encontrado versículos en esta lectura.");return;}
        String[] labels=new String[numbers.size()];
        for(int i=0;i<labels.length;i++)labels[i]="Versículo "+numbers.get(i);
        new AlertDialog.Builder(this).setTitle("¿Qué versículo quieres anotar?")
            .setItems(labels,(dialog,which)->editStudyAnnotation(passage,
                new AnnotationAnchors.Anchor(numbers.get(which),0,""),null))
            .setNegativeButton("Cancelar",null).show();
    }
    private static boolean studyLinkIsSafe(String value){
        try{
            Uri url=Uri.parse(value);
            String scheme=url.getScheme();
            return ("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme))
                &&url.getHost()!=null&&!url.getHost().isEmpty()
                &&url.getUserInfo()==null;
        }catch(RuntimeException ex){return false;}
    }
    private void openStudyUrl(String url){
        if(!studyLinkIsSafe(url)){showError("Este enlace no es una URL web válida.");return;}
        try{
            Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
        }catch(RuntimeException ex){showError("No hay ninguna aplicación disponible para abrir el enlace.");}
    }
    private void showStudyAnnotation(BibleStore.Passage passage,StudyAnnotations.Item item){
        LinearLayout body=vertical();pad(body,16);
        TextView ref=text(Books.name(item.book)+" "+item.chapter+":"+item.verse,13,ACCENT,true);
        body.addView(ref);
        if(!item.quote.isEmpty()){
            addGap(body,10);
            TextView q=text("«"+item.quote+"»",15,DARK,false);
            serif(q);q.setTextIsSelectable(true);body.addView(q);
        }
        addGap(body,12);
        String description=item.body.trim().isEmpty()?
            (StudyAnnotations.ATLAS.equals(item.kind)?"Mapa bíblico de este libro":
                StudyAnnotations.MAP.equals(item.kind)?"Mapa vinculado":"Enlace vinculado")
            :item.body;
        TextView full=text(description,16,DARK,false);
        full.setTextIsSelectable(true);body.addView(full);
        ScrollView scroll=new ScrollView(this);
        scroll.addView(body);
        AlertDialog popup=new AlertDialog.Builder(this)
            .setTitle(annotationType(item)+" · Scriptura")
            .setView(scroll).setNegativeButton("Cerrar",null).create();
        if(StudyAnnotations.ATLAS.equals(item.kind)){
            addGap(body,8);
            button(body,"Abrir atlas de "+Books.name(item.book),()->{
                popup.dismiss();openBookAtlas(item.book);
            },true);
        }
        if(!item.url.isEmpty()){
            addGap(body,8);
            button(body,StudyAnnotations.MAP.equals(item.kind)?"Abrir mapa":"Abrir página web",
                ()->openStudyUrl(item.url),true);
        }
        addGap(body,8);
        button(body,"Editar anotación",()->{
            popup.dismiss();
            editStudyAnnotation(passage,new AnnotationAnchors.Anchor(item.verse,item.offset,item.quote),item);
        },false);
        button(body,"Eliminar anotación",()->{
            new AlertDialog.Builder(this).setTitle("Eliminar anotación")
                .setMessage("Esta nota se borrará del dispositivo. ¿Continuar?")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Eliminar",(d,w)->{
                    studyAnnotations.delete(item.id);popup.dismiss();render();
                }).show();
        },false);
        popup.show();
    }
    private void editStudyAnnotation(BibleStore.Passage passage,AnnotationAnchors.Anchor anchor,
                                     StudyAnnotations.Item editing){
        LinearLayout content=vertical();pad(content,17);
        content.addView(text(Books.name(passage.book)+" "+passage.chapter+":"+anchor.verse,
            15,ACCENT,true));
        if(!anchor.quote.isEmpty()){
            addGap(content,8);
            String quote=anchor.quote.length()>320?anchor.quote.substring(0,320)+"…":anchor.quote;
            TextView fragment=text("«"+quote+"»",14,MUTED,false);
            content.addView(fragment);
        }
        addGap(content,12);
        content.addView(text("Tipo de anotación",13,DARK,true));
        Spinner type=new Spinner(this);
        String[] choices={"Nota escrita","Mapa externo (URL)","Página web","Atlas de Scriptura"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        type.setAdapter(adapter);
        content.addView(type);
        EditText note=input(content,"Escribe aquí tu anotación completa",true);
        if(editing!=null)note.setText(editing.body);
        addGap(content,8);
        EditText url=input(content,"https://... enlace a mapa o web",false);
        if(editing!=null)url.setText(editing.url);
        int initial=editing==null?0:StudyAnnotations.MAP.equals(editing.kind)?1:
            StudyAnnotations.WEB.equals(editing.kind)?2:
            StudyAnnotations.ATLAS.equals(editing.kind)?3:0;
        type.setSelection(initial);
        url.setVisibility(initial==1||initial==2?View.VISIBLE:View.GONE);
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> v,View view,int i,long id){
                url.setVisibility(i==1||i==2?View.VISIBLE:View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> v){}
        });
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog popup=new AlertDialog.Builder(this)
            .setTitle(editing==null?"Nueva anotación":"Editar anotación")
            .setView(scroll).setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar",null).create();
        popup.setOnShowListener(ignored->popup.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(v->{
                int selection=type.getSelectedItemPosition();
                String kind=selection==1?StudyAnnotations.MAP:
                    selection==2?StudyAnnotations.WEB:
                    selection==3?StudyAnnotations.ATLAS:StudyAnnotations.NOTE;
                String comment=note.getText().toString().trim();
                String link=(selection==1||selection==2)?url.getText().toString().trim():"";
                if(selection==0&&comment.isEmpty()){
                    note.setError("Escribe el contenido de la nota");return;
                }
                if((selection==1||selection==2)&&!studyLinkIsSafe(link)){
                    url.setError("Introduce una URL http/https válida");return;
                }
                try{
                    studyAnnotations.save(editing==null?0:editing.id,
                        passage.book,passage.chapter,anchor.verse,anchor.offset,
                        anchor.quote,kind,comment,link);
                    popup.dismiss();
                    Toast.makeText(this,"Anotación guardada",Toast.LENGTH_SHORT).show();
                    render();
                }catch(RuntimeException error){
                    showError("No se pudo guardar la anotación: "+error.getMessage());
                }
            }));
        popup.show();
    }

    private void askForWord(BibleStore.Passage passage){
        EditText entry=new EditText(this);
        entry.setSingleLine(true);
        entry.setHint("Palabra bíblica");
        entry.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout form=vertical();
        form.setPadding(dp(20),dp(4),dp(20),dp(6));
        form.addView(entry);
        new AlertDialog.Builder(this).setTitle("Definir una palabra")
            .setMessage("Puedes consultar una palabra sin usar la selección de texto.")
            .setView(form)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Definir",(dialog,button)->{
                String term=entry.getText().toString().trim();
                DefinitionUi.show(this,term,ai.configured(),new DefinitionUi.Delegate(){
                    @Override public void openExternal(String url){
                        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))
                            .addCategory(Intent.CATEGORY_BROWSABLE));}
                        catch(Exception error){showError("No se pudo abrir el navegador.");}
                    }
                    @Override public void explainWithGemini(String word){
                        interpret(passage,"Explica la palabra «"+word+
                            "» en el contexto de este pasaje con prudencia histórica.",true);
                    }
                    @Override public void showError(String message){MainActivity.this.showError(message);}
                });
            }).show();
    }

    private void showNavarraNote(BibleStore.Passage p){
        String note=notes.get(p.book,p.chapter,p.first);
        if(note.isEmpty()){
            showError("No se ha encontrado una nota específica para el primer versículo seleccionado. La edición conserva comentarios en otras referencias y libros.");
            return;
        }
        TextView body=text(note,16,DARK,false);body.setTextIsSelectable(true);pad(body,18);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("Comentario de Navarra · "+p.ref)
            .setView(scroll).setPositiveButton("Cerrar",null).show();
    }
    private void ask(BibleStore.Passage p){
        EditText edit=new EditText(this);edit.setHint("¿Qué quieres comprender o cómo quieres aplicarlo?");edit.setMinLines(3);
        new AlertDialog.Builder(this).setTitle("Conversar sobre "+p.ref)
            .setView(edit).setNegativeButton("Cancelar",null)
            .setPositiveButton("Consultar",(d,w)->interpret(p,edit.getText().toString().trim(),true)).show();
    }
    private void interpret(BibleStore.Passage p,String question,boolean custom){
        if(!ai.configured()){showError("Para generar explicaciones configura tu clave gratuita de Gemini en ⚙ Ajustes.");return;}
        String cacheKey="explain:"+ai.model()+":"+p.ref;
        if(!custom){
            String existing=store.getReflection(cacheKey);
            if(!existing.isEmpty()){showExplanation(p,existing,true);return;}
        }
        String detail=custom&&question.isEmpty()?"Profundiza en el significado":question;
        new AlertDialog.Builder(this).setTitle("Consulta externa")
            .setMessage("Se enviará a Gemini el pasaje seleccionado, el fragmento disponible del comentario de Navarra y tu pregunta, si la has escrito. No se envía el EPUB completo. El nivel gratuito puede usar las consultas para mejorar los productos de Google. ¿Continuar?")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Enviar",(dialog,w)->async("Consultando Gemini...",()->ai.explain(p.ref,p.text,"Traducción personal: Biblia de Navarra. Comentario de la edición: "+notes.get(p.book,p.chapter,p.first),detail),out->{
                if(!custom)store.saveReflection(cacheKey,out);
                showExplanation(p,out,false);
            })).show();
    }
    private void showExplanation(BibleStore.Passage p,String text,boolean cache){
        TextView body=text(text,16,DARK,false);body.setTextIsSelectable(true);pad(body,18);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("Reflexión · "+p.ref+(cache?" (guardada)":""))
            .setView(scroll).setPositiveButton("Cerrar",null)
            .setNeutralButton("Preguntar más",(d,w)->ask(p)).show();
    }
    private void mass(){
        title(column,"Lecturas de la misa");
        hint(column,"Calendario litúrgico de España · fuente externa basada en el leccionario de la CEE");
        LinearLayout c=card(column);
        c.addView(text(Liturgy.label(massDay),18,DARK,true));
        button(c,"Cambiar fecha",()->new DatePickerDialog(this,(v,y,m,d)->{
            massDay=Calendar.getInstance();massDay.set(y,m,d);render();
        },massDay.get(Calendar.YEAR),massDay.get(Calendar.MONTH),massDay.get(Calendar.DAY_OF_MONTH)).show(),false);
        button(c,"Volver a hoy",()->{massDay=Calendar.getInstance();render();},false);
        button(c,"Consultar referencias de este día",this::loadMass,true);
        button(c,"Abrir lecturas completas",()->openMassWebsite(Liturgy.link(massDay)),false);
        hint(column,"El texto litúrgico oficial pertenece a la CEE. La web consultada es un proyecto independiente, no un servicio oficial de la CEE. Las referencias pueden variar por celebraciones locales; compruébalas en caso de duda.");
        String previous=store.getReflection("mass:"+Liturgy.link(massDay));
        if(!previous.isEmpty()){
            try{presentMass(new JSONObject(previous));}catch(Exception ex){hint(column,"No se pudo leer la última consulta guardada");}
        }
    }
    private void loadMass(){
        Calendar date=(Calendar)massDay.clone();
        async("Consultando el calendario...",()->{
            Liturgy.Day m=Liturgy.download(date);
            JSONObject json=new JSONObject();json.put("title",m.title);json.put("date",m.date);json.put("url",m.url);
            JSONArray a=new JSONArray();for(Liturgy.Reading r:m.readings)a.put(new JSONObject().put("type",r.label).put("ref",r.reference));
            json.put("readings",a);
            return json.toString();
        },out->{
            store.saveReflection("mass:"+Liturgy.link(date),out);
            if(Liturgy.link(date).equals(Liturgy.link(massDay)))render();
        });
    }
    private void presentMass(JSONObject json) throws Exception{
        LinearLayout c=card(column);c.addView(text(json.optString("title","Celebración"),19,DARK,true));
        hint(c,"Fuente: lecturasdeldia.org · referencias del leccionario español");
        JSONArray readings=json.getJSONArray("readings");
        for(int i=0;i<readings.length();i++){
            JSONObject x=readings.getJSONObject(i);
            String type=x.optString("type"),ref=x.optString("ref");
            LinearLayout box=card(column);box.addView(text(type,13,ACCENT,true));
            box.addView(text(ref,17,DARK,true));
            String local=Liturgy.toLocalQuery(ref);
            if(local.isEmpty())hint(box,"Cita litúrgica compuesta: consulta el texto exacto en la web enlazada.");
            if(store.ready()&&!local.isEmpty()){
                BibleStore.Passage p=store.passage(local);
                if(p!=null)button(box,"Leer en mi Biblia de Navarra",()->open(p),false);
            }
            if(type.equals("Evangelio")&&store.ready()){
                BibleStore.Passage p=local.isEmpty()?null:store.passage(local);
                if(p!=null)button(box,"Reflexionar sobre el Evangelio",()->interpret(p,"Explica el Evangelio de la misa y su mensaje para la vida cotidiana.",false),true);
            }
        }
    }
    private void openMassWebsite(String url){
        try {
            Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
            startActivity(i);
        }catch(Exception e){showError("No se pudo abrir la web: "+e.getMessage());}
    }
    private void personal(){
        title(column,"Una lectura para ti");
        hint(column,"Selecciona cómo te encuentras. La selección inicial es local; la interpretación por Gemini es opcional.");
        LinearLayout c=card(column);
        String[] moods={"Tranquilidad","Preocupación","Tristeza","Gratitud","Alegría","Dudas","Miedo","Esperanza"};
        for(int i=0;i<moods.length;i+=2){
            LinearLayout line=new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for(int j=i;j<Math.min(i+2,moods.length);j++){
                final String option=moods[j];
                boolean chosen=option.equals(mood);
                TextView item=text((chosen?"● ":"○ ")+option,14,chosen?Color.WHITE:DARK,chosen);
                item.setGravity(Gravity.CENTER);item.setMinHeight(dp(48));
                item.setPadding(dp(5),dp(10),dp(5),dp(10));
                item.setBackground(chosen?bg(DARK,11):outlined(SOFT,11));
                item.setContentDescription(option+(chosen?", seleccionado":""));
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);
                params.setMargins(dp(3),dp(4),dp(3),dp(4));line.addView(item,params);
                item.setOnClickListener(v->{mood=option;render();});
            }
            c.addView(line);
        }
        c=card(column);
        c.addView(text("Tu situación (opcional)",16,DARK,true));
        EditText story=input(c,"Puedes explicar cómo te sientes; no se guarda en el historial.",true);
        String reference=selection(mood);
        BibleStore.Passage p=store.ready()?store.passage(reference):null;
        if(p!=null){
            c.addView(text("Lectura propuesta · "+p.ref,17,DARK,true));
            c.addView(text(p.text,15,MUTED,false));
            button(c,"Leer pasaje completo",()->open(p),false);
            button(c,"✦ Interpretar según mi situación",()->{
                String own=story.getText().toString().trim();
                interpret(p,"Estado de ánimo: "+mood+". Situación personal: "+own+
                    ". Explica qué puede aportar el pasaje, sin atribuir mensajes personalizados a Dios.",true);
            },true);
        }else{
            hint(c,"Para proponerte un pasaje basado en tu estado de ánimo, importa antes tu Biblia EPUB. No hace falta configurar Gemini para leerlo.");
            button(c,"Importar mi Biblia EPUB",this::pickEpub,true);
        }
        button(c,"Descubrir otro texto aleatorio",()->{
            if(!store.ready()){showError("Importa la Biblia primero");return;}
            BibleStore.Passage next=store.random(new Random().nextBoolean(),System.nanoTime());
            if(next!=null)open(next);
        },false);
    }
    private String selection(String mood){
        switch(mood){
            case "Preocupación":return "Mateo 6:25-34";
            case "Tristeza":return "Salmos 34:18-20";
            case "Gratitud":return "Salmos 100";
            case "Alegría":return "Filipenses 4:4-9";
            case "Dudas":return "Marcos 9:23-24";
            case "Miedo":return "Isaías 41:10";
            case "Esperanza":return "Romanos 8:31-39";
            default:return "Salmos 23";
        }
    }
    private void saved(){
        title(column,"Tus lecturas");
        if(!store.ready()){
            hint(column,"Tus favoritos e historial estarán disponibles cuando importes tu ejemplar personal de la Biblia. Importarlo no exige registrarte.");
            button(column,"Importar mi Biblia EPUB",this::pickEpub,true);
            button(column,"Explorar índice de libros",this::openIndex,false);
            return;
        }
        section(column,"★ Favoritos","Pasajes que decidiste guardar.");
        java.util.List<String> favorites=store.saved(true);
        if(favorites.isEmpty())hint(column,"Todavía no has guardado ningún pasaje.");
        for(String ref:favorites)button(column,ref,()->lookup(ref),false);
        addGap(column,24);
        section(column,"Historial","Tus consultas recientes.");
        java.util.List<String> history=store.saved(false);
        if(history.isEmpty())hint(column,"Las lecturas que abras aparecerán aquí.");
        for(String ref:history)button(column,ref,()->lookup(ref),false);
    }
    private void pickEpub(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/epub+zip","application/zip","application/octet-stream"});
        startActivityForResult(intent,PICK_EPUB);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_EPUB&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            async("Importando y verificando 73 libros...",()->{
                int n=store.importEpub(uri);
                int comments=0,intros=0;
                StringBuilder warnings=new StringBuilder();
                try{comments=notes.importFromPrivateEpub();}
                catch(Exception error){
                    android.util.Log.w("Scriptura","Error al extraer comentarios: "+error.getClass().getSimpleName());
                    warnings.append("\n\nLos comentarios no se han podido actualizar; la Biblia sí está importada.");
                }
                try{intros=introductions.importPrivateEpub();}
                catch(Exception error){
                    android.util.Log.w("Scriptura","Error al extraer introducciones: "+error.getClass().getSimpleName());
                    warnings.append("\n\nLas introducciones no se han podido actualizar.");
                }
                return n+" versículos, "+comments+" comentarios y "+intros+
                    " introducciones extraídas del EPUB personal."+warnings;
            },msg->{new AlertDialog.Builder(this).setTitle("Biblioteca preparada")
                .setMessage(msg).setPositiveButton("Continuar",(d,w)->{page=0;render();}).show();});
        }
    }

    private void openIndex(){page=7;render();}
    private void openBookInfo(String book){indexBook=book;page=9;render();}
    private void openGallery(){galleryBook="";galleryOffset=0;page=8;render();}
    private void openGalleryFor(String book){page=8;galleryBook=book;galleryOffset=0;render();}
    private String galleryBook="";
    private int galleryOffset=0;

    /**
     * El libro privado no se lee ni se descarga de Internet.
     * Se reconstruye el índice editorial una sola vez desde el EPUB ya importado.
     */
    private void ensureIntroductions(){ensureIntroductions(false);}
    private void ensureIntroductions(boolean force){
        if(introBusy)return;
        if(!force && (introductions.count()>0||introAttempted))return;
        File check=new File(getFilesDir(),"biblia-usuario.epub");
        if(!check.isFile())return;
        introBusy=true;introAttempted=true;
        worker.execute(()->{
            try{
                introductions.importPrivateEpub();
                runOnUiThread(()->{
                    introBusy=false;
                    if(!isFinishing()&&(page==7||page==9))render();
                });
            }catch(Exception error){
                android.util.Log.w("Scriptura","Extracción editorial no disponible",error);
                runOnUiThread(()->{introBusy=false;
                    if(!isFinishing()&&(page==7||page==9))
                        Toast.makeText(this,"No se pudo extraer la introducción del EPUB. Puedes reintentarlo.",Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    private void fillIndex(LinearLayout target,String query,int scope){
        target.removeAllViews();
        int total=0;
        boolean lastNew=false,foundSection=false;
        for(String code:Books.NAMES.keySet()){
            boolean isNew=Books.isNew(code);
            if(!BookIndexFilter.matches(code,query,scope))continue;
            if(!foundSection||lastNew!=isNew){
                TextView heading=text(isNew?"Nuevo Testamento":"Antiguo Testamento",17,DARK,true);
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
                params.topMargin=dp(12);params.bottomMargin=dp(7);
                target.addView(heading,params);
                foundSection=true;lastNew=isNew;
            }
            final String book=code;total++;
            LinearLayout entry=card(target);
            TextView name=text(Books.name(book),17,DARK,true);
            entry.addView(name);
            String intro=introductions.textFor(book);
            if(!intro.isEmpty()){
                TextView sample=text(intro,13,MUTED,false);
                sample.setMaxLines(2);entry.addView(sample);
            }
            TextView hint=text("Ver introducción, capítulos, mapas y arte",12,ACCENT,false);
            entry.addView(hint);
            entry.setClickable(true);entry.setFocusable(true);
            entry.setContentDescription(Books.name(book)+": abrir información del libro");
            entry.setOnClickListener(v->openBookInfo(book));
        }
        if(total==0)hint(target,"No hay libros que coincidan. Prueba con otro nombre.");
    }
    private void bibleIndex(){
        title(column,"Índice de la Biblia");
        hint(column,"Busca un libro entre los 73 del canon católico. Toca su nombre para abrir la introducción, capítulos, lugares y obras.");
        ensureIntroductions();
        if(introBusy)hint(column,"Preparando las introducciones de tu EPUB privado…");
        if(introductions.count()==0){
            hint(column,"Sin un EPUB importado puedes explorar los nombres, mapas y obras. Las introducciones editoriales son opcionales.");
            if(!store.ready())button(column,"Importar mi Biblia de Navarra",this::pickEpub,false);
            else button(column,"Reintentar extracción de introducciones",()->{
                introAttempted=false;ensureIntroductions(true);
            },false);
        }else{
            hint(column,introductions.count()+" introducciones encontradas en tu ejemplar privado.");
        }
        LinearLayout filter=card(column);
        EditText edit=input(filter,"Filtrar por libro: Génesis, Juan, Salmos…",false);
        edit.setSingleLine(true);
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        edit.setText(indexQuery);
        LinearLayout selectors=new LinearLayout(this);
        selectors.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels={"Todos","Antiguo","Nuevo"};
        LinearLayout resultList=vertical();
        for(int i=0;i<labels.length;i++){
            final int selection=i;
            TextView toggle=text(labels[i],13,indexScope==i?Color.WHITE:DARK,true);
            toggle.setGravity(Gravity.CENTER);
            toggle.setMinHeight(dp(46));
            toggle.setBackground(indexScope==i?bg(DARK,10):outlined(SOFT,10));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(46),1);
            params.setMargins(dp(2),dp(10),dp(2),dp(5));
            selectors.addView(toggle,params);
            toggle.setContentDescription("Filtrar libros: "+labels[i]);
            toggle.setFocusable(true);
            toggle.setOnClickListener(v->{
                indexScope=selection;
                for(int j=0;j<selectors.getChildCount();j++){
                    TextView item=(TextView)selectors.getChildAt(j);
                    item.setTextColor(indexScope==j?Color.WHITE:DARK);
                    item.setBackground(indexScope==j?bg(DARK,10):outlined(SOFT,10));
                }
                fillIndex(resultList,indexQuery,indexScope);
            });
        }
        filter.addView(selectors);
        column.addView(resultList);
        fillIndex(resultList,indexQuery,indexScope);
        edit.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence t,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence text,int start,int before,int count){
                indexQuery=text.toString();
                fillIndex(resultList,indexQuery,indexScope);
            }
            @Override public void afterTextChanged(android.text.Editable text){}
        });
    }
    private void bookOverview(){
        String code=indexBook;
        title(column,Books.name(code));
        button(column,"Volver al índice",this::openIndex,false);
        ensureIntroductions();
        LinearLayout block=card(column);
        section(block,"Introducción editorial","Extraída de tu ejemplar personal de la Sagrada Biblia, Universidad de Navarra.");
        String introduction=introductions.textFor(code);
        if(introduction.isEmpty()){
            hint(block,introBusy?"Preparando la introducción de tu EPUB…":
                "No se encontró una introducción específica importada. No se generará un resumen ficticio.");
            button(block,"Reintentar extracción",()->{introAttempted=false;ensureIntroductions(true);},false);
        }else{
            String source=introductions.sourceBook(code);
            if(!code.equals(source))hint(block,"Introducción conjunta: "+Books.name(source)+" y "+Books.name(code)+".");
            TextView preview=text(introduction,16,DARK,false);
            serif(preview);preview.setTextIsSelectable(true);block.addView(preview);
            hint(block,"Extracto editorial abreviado; no sustituye la introducción íntegra de la obra.");
        }
        block=card(column);section(block,"Leer la Biblia","Consulta capítulos y pasajes del libro.");
        if(store.ready()){
            button(block,"Seleccionar capítulo",()->browseChapters(code),true);
            button(block,"Comenzar por el capítulo 1",()->lookup(Books.name(code)+" 1"),false);
        }else button(block,"Importar mi ejemplar EPUB",this::pickEpub,false);
        block=card(column);
        section(block,"Lugares y arte","Contexto geográfico y artístico relacionado.");
        button(block,"Mapa de "+Books.name(code),()->openBookAtlas(code),false);
        if(!BiblicalArt.byBook(code).isEmpty())
            button(block,"Obras pictóricas relacionadas",()->openGalleryFor(code),false);
    }
    private void artGallery(){
        title(column,"Arte y Biblia");
        hint(column,"Selección inicial de pintura clásica. Son interpretaciones artísticas posteriores a los relatos bíblicos, no imágenes de los hechos originales.");
        if(!galleryBook.isEmpty()){
            button(column,"Mostrar toda la galería",()->{galleryBook="";galleryOffset=0;render();},false);
            hint(column,"Obras relacionadas con "+Books.name(galleryBook));
        }
        java.util.List<BiblicalArt.Painting> items=galleryBook.isEmpty()?
            BiblicalArt.all():BiblicalArt.byBook(galleryBook);
        if(items.isEmpty())hint(column,"No hay todavía obras catalogadas para este libro.");
        int count=items.size();
        if(galleryOffset>=count)galleryOffset=0;
        int finish=Math.min(count,galleryOffset+6);
        hint(column,count+" obras catalogadas · mostrando "+(count==0?0:galleryOffset+1)+"–"+finish+
            ". Solo se cargan las miniaturas de esta página.");
        if(galleryOffset>0)button(column,"← Obras anteriores",()->{
            galleryOffset=Math.max(0,galleryOffset-6);render();
        },false);
        for(int i=galleryOffset;i<finish;i++){
            BiblicalArt.Painting artwork=items.get(i);
            LinearLayout block=card(column);
            TextView heading=text(artwork.title,20,DARK,false);serif(heading);block.addView(heading);
            hint(block,artwork.author+" · "+artwork.date);
            ImageView illustration=new ImageView(this);
            illustration.setAdjustViewBounds(true);
            illustration.setScaleType(ImageView.ScaleType.FIT_CENTER);
            illustration.setBackground(outlined(SOFT,12));
            illustration.setMinimumHeight(dp(180));
            illustration.setContentDescription("Reproducción artística: "+artwork.title+" de "+artwork.author);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(210));
            params.topMargin=dp(8);block.addView(illustration,params);
            BiblicalArt.thumbnail(this,illustration,artwork);
            hint(block,artwork.description);
            hint(block,"Referencia bíblica: "+artwork.reference);
            hint(block,"Autoría y procedencia de la obra: Wikimedia Commons.");
            button(block,"Ver obra, autoría y derechos",
                ()->{try{startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(BiblicalArt.resolvedFilePage(illustration,artwork))));}
                     catch(Exception error){showError("No se pudo abrir Wikimedia Commons.");}},false);
            button(block,"Leer "+artwork.reference,()->{
                if(store.ready())lookup(artwork.reference);
                else showError("Importa tu Biblia EPUB para abrir el pasaje.");
            },false);
        }
        if(finish<count)button(column,"Siguientes 6 obras →",()->{
            galleryOffset=finish;render();
        },true);
    }

    private void openAtlas(int mode){
        page=6;atlasMode=mode;render();
    }
    private void openBookAtlas(String book){
        atlasBook=book;atlasMode=2;page=6;render();
    }
    private void openJourney(BibleGeography.Journey route){
        atlasJourney=route;atlasMode=3;page=6;render();
    }
    private void openPlace(BibleGeography.Place place){
        atlasPlace=place;atlasMode=4;page=6;render();
    }
    private void atlas(){
        title(column,"Lugares bíblicos");
        hint(column,"Explora los lugares donde transcurren los relatos bíblicos y las etapas que mencionan las Escrituras.");
        if(atlasMode==3 && atlasJourney!=null){journeyScreen(atlasJourney);return;}
        if(atlasMode==4 && atlasPlace!=null){placeScreen(atlasPlace);return;}
        LinearLayout menu=card(column);
        button(menu,"Lugares",()->openAtlas(0),atlasMode==0);
        button(menu,"Recorridos",()->openAtlas(1),atlasMode==1);
        button(menu,"Por libro",()->openAtlas(2),atlasMode==2);
        if(atlasMode==0){atlasPlaces();}
        else if(atlasMode==1){atlasJourneys();}
        else {atlasBookScreen();}
    }
    private void atlasPlaces(){
        LinearLayout searchBox=card(column);
        searchBox.addView(text("Buscar lugares bíblicos",18,DARK,true));
        EditText term=input(searchBox,"Jerusalén, Nazaret, Harán…",false);
        term.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        button(searchBox,"Buscar",()->atlasPlaceResults(term.getText().toString()),true);
        hint(column,"Lugares del Antiguo y Nuevo Testamento");
        atlasPlaceCards(BibleGeography.allPlaces());
    }
    private void atlasPlaceResults(String query){
        atlasMode=0;
        column.removeAllViews();
        title(column,"Resultado de búsqueda");
        button(column,"Volver al atlas",()->openAtlas(0),false);
        java.util.List<BibleGeography.Place> hits=BibleGeography.search(query);
        hint(column,hits.size()+" lugares encontrados.");
        atlasPlaceCards(hits);
    }
    private void atlasPlaceCards(java.util.List<BibleGeography.Place> places){
        if(places.isEmpty()){hint(column,"Todavía no hay ubicaciones catalogadas para esta selección.");return;}
        for(BibleGeography.Place p:places){
            LinearLayout box=card(column);
            box.addView(text(p.name,18,DARK,true));
            hint(box,p.description);
            button(box,"Conocer este lugar",()->openPlace(p),false);
        }
    }
    private void atlasJourneys(){
        hint(column,"Descubre lo que sucedió en cada lugar y lee los relatos correspondientes.");
        for(BibleGeography.Journey j:BibleGeography.allJourneys()){
            LinearLayout box=card(column);
            box.addView(text(j.title,18,DARK,true));
            hint(box,j.person+" · "+j.stops.length+" etapas");
            hint(box,j.summary);
            button(box,"Ver recorrido",()->openJourney(j),false);
        }
    }
    private void atlasBookScreen(){
        LinearLayout box=card(column);
        box.addView(text("Selecciona uno de los 73 libros",17,DARK,true));
        button(box,"Libro actual: "+Books.name(atlasBook),()->{
            String[] labels=Books.NAMES.values().toArray(new String[0]);
            String[] ids=Books.NAMES.keySet().toArray(new String[0]);
            new AlertDialog.Builder(this).setTitle("Atlas por libro").setItems(labels,(d,n)->openBookAtlas(ids[n]))
                .setNegativeButton("Cancelar",null).show();
        },true);
        java.util.List<BibleGeography.Place> places=BibleGeography.placesForBook(atlasBook);
        java.util.List<BibleGeography.Journey> journeys=BibleGeography.journeysForBook(atlasBook);
        if(places.isEmpty()){
            hint(column,"No tenemos aún lugares identificados para este libro. Puedes continuar su lectura en la biblioteca.");
        } else {
            hint(column,"Lugares relacionados con "+Books.name(atlasBook));
            if(places.size()>1){
                LinearLayout mapCard=card(column);
                mapCard.addView(text("Mapa actual de los lugares",17,DARK,true));
                GeoMapView mapView=new GeoMapView(this,places,false,this::openExternalMap);
                mapCard.addView(mapView);
                mapControls(mapCard,mapView);
                hint(mapCard,"Toca un punto para localizarlo en Google Maps.");
            }
            title(column,"Lugares y acontecimientos");
            for(int i=0;i<places.size();i++){
                BibleGeography.Place p=places.get(i);
                LinearLayout detail=card(column);
                detail.addView(text((i+1)+". "+p.name,18,DARK,true));
                hint(detail,p.description);
                renderStudyReferences(detail,p.refs);
                button(detail,"Ubicar en Google Maps",()->openExternalMap(p),false);
            }
        }
        if(!journeys.isEmpty()){
            title(column,"Recorridos relacionados");
            for(BibleGeography.Journey j:journeys)button(column,j.title,()->openJourney(j),false);
        }
    }
    private void journeyScreen(BibleGeography.Journey route){
        button(column,"Volver a recorridos",()->openAtlas(1),false);
        title(column,route.title);
        hint(column,route.summary);
        java.util.List<JourneyStudy.Stage> stages=JourneyStudy.stages(route);
        java.util.List<BibleGeography.Place> stops=BibleGeography.stops(route);
        if(stops.size()>1){
            LinearLayout map=card(column);
            map.addView(text("Lugares del recorrido",18,DARK,true));
            GeoMapView mapView=new GeoMapView(this,stops,true,this::openExternalMap);
            map.addView(mapView);
            mapControls(map,mapView);
            hint(map,"Toca un número para situarlo en Google Maps.");
        }
        title(column,"Etapas y acontecimientos");
        if(!stages.isEmpty()){
            for(int i=0;i<stages.size();i++){
                JourneyStudy.Stage stage=stages.get(i);
                BibleGeography.Place p=stage.place;
                LinearLayout detail=card(column);
                detail.addView(text((i+1)+". "+p.name,19,DARK,true));
                hint(detail,p.modern);
                detail.addView(text(stage.event,16,DARK,false));
                if(p.certainty.equals(BibleGeography.DEBATED) ||
                   p.certainty.equals(BibleGeography.REGION))
                    hint(detail,"La ubicación exacta de este episodio no se conoce con certeza.");
                renderStudyReferences(detail,stage.scripture);
                button(detail,"Situar en Google Maps",()->openExternalMap(p),false);
                button(detail,"Conocer el lugar",()->openPlace(p),false);
            }
        }else{
            for(BibleGeography.Place p:stops){
                LinearLayout detail=card(column);
                detail.addView(text(p.name,18,DARK,true));
                detail.addView(text(p.description,15,DARK,false));
                renderStudyReferences(detail,p.refs);
                button(detail,"Situar en Google Maps",()->openExternalMap(p),false);
            }
        }
    }
    private void placeScreen(BibleGeography.Place p){
        button(column,"Volver a lugares",()->openAtlas(0),false);
        title(column,p.name);
        LinearLayout box=card(column);
        box.addView(text(p.modern,17,DARK,true));
        box.addView(text(p.description,16,DARK,false));
        if(p.certainty.equals(BibleGeography.DEBATED) ||
           p.certainty.equals(BibleGeography.REGION))
            hint(box,"La identificación precisa de este lugar sigue abierta al estudio.");
        GeoMapView mapView=new GeoMapView(this,
            java.util.Collections.singletonList(p),false,this::openExternalMap);
        box.addView(mapView);
        mapControls(box,mapView);
        button(box,"Situar en Google Maps",()->openExternalMap(p),true);
        LinearLayout readings=card(column);
        section(readings,"Relatos que lo mencionan","Abre cada cita para leer el pasaje bíblico.");
        renderStudyReferences(readings,p.refs);
    }
    private void renderStudyReferences(LinearLayout parent,String references){
        java.util.List<BibleStudyReferences.Link> links=BibleStudyReferences.links(references);
        if(links.isEmpty())return;
        for(BibleStudyReferences.Link link:links){
            button(parent,"Leer "+link.label,()->openStudyReference(link.query),false);
        }
    }
    private void openStudyReference(String reference){
        if(!store.ready()){
            new AlertDialog.Builder(this).setTitle("Tu lectura bíblica")
                .setMessage("Para leer "+reference+" con el texto de la Biblia de Navarra, importa tu ejemplar EPUB.")
                .setNegativeButton("Ahora no",null)
                .setPositiveButton("Importar mi Biblia",(d,w)->pickEpub()).show();
            return;
        }
        BibleStore.Passage passage=store.passage(reference);
        if(passage==null){
            showError("No se encontró "+reference+" en el ejemplar importado.");
            return;
        }
        open(passage);
    }
    private void mapControls(LinearLayout box,GeoMapView map){
        LinearLayout zooms=new LinearLayout(this);
        zooms.setOrientation(LinearLayout.HORIZONTAL);
        TextView minus=text("−  Alejar",15,DARK,true);
        TextView plus=text("+  Acercar",15,DARK,true);
        for(TextView control:new TextView[]{minus,plus}){
            control.setGravity(Gravity.CENTER);
            control.setMinHeight(dp(45));
            control.setBackground(outlined(SOFT,11));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);
            params.setMargins(dp(4),dp(8),dp(4),dp(0));
            zooms.addView(control,params);
        }
        minus.setOnClickListener(v->map.zoomOut());
        plus.setOnClickListener(v->map.zoomIn());
        box.addView(zooms);
        button(box,"Actualizar mapa",map::retry,false);
        button(box,"Ver esta zona por satélite en Google Maps",
            ()->openSatellite(map.centerLatitude(),map.centerLongitude(),map.zoomLevel()),false);
        hint(box,"Desliza para explorar, junta o separa dos dedos para ampliar y toca cada lugar para abrirlo en Google Maps.");
    }
    private void openSatellite(double latitude,double longitude,int zoom){
        // URL oficial de Google Maps. No requiere API key ni SDK facturado.
        String link=MapLinks.satellite(latitude,longitude,zoom);
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link)));}
        catch(Exception e){showError("No se pudo abrir la vista satélite: "+e.getMessage());}
    }

    private void openExternalMap(BibleGeography.Place p){
        // Búsqueda por coordenadas: Google Maps muestra el marcador en el lugar elegido.
        String url=MapLinks.point(p.lat,p.lon);
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){showError("No se pudo abrir Google Maps.");}
    }
    private void showPassagePlaces(BibleStore.Passage passage){
        java.util.List<BibleGeography.Place> hits=BibleGeography.mentionedInPassage(passage.book,passage.text);
        if(hits.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Lugares del pasaje")
                .setMessage("No hemos asociado todavía un lugar a este pasaje. Puedes descubrir otros lugares relacionados con el libro.")
                .setPositiveButton("Atlas del libro",(d,w)->openBookAtlas(passage.book))
                .setNegativeButton("Cerrar",null).show();
            return;
        }
        String[] choices=new String[hits.size()];
        for(int i=0;i<hits.size();i++)choices[i]=hits.get(i).name+" · "+hits.get(i).certainty;
        new AlertDialog.Builder(this).setTitle("Lugares mencionados en "+passage.ref)
            .setItems(choices,(d,w)->openPlace(hits.get(w)))
            .setNeutralButton("Atlas del libro",(d,w)->openBookAtlas(passage.book))
            .setNegativeButton("Cerrar",null).show();
    }

    private String currentVersionName(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(Exception ignored){return "desconocida";}
    }
    private void showSettings(){
        LinearLayout box=vertical();box.setPadding(dp(18),dp(12),dp(18),dp(12));
        section(box,"Primeros pasos","Guía rápida disponible en cualquier momento.");
        button(box,"Cómo utilizar Scriptura",this::showQuickGuide,false);
        addGap(box,14);
        section(box,"Tu Biblia","El ejemplar personal se guarda únicamente en este móvil.");
        button(box,"Importar o reemplazar Biblia EPUB",this::pickEpub,false);
        addGap(box,17);
        section(box,"Inteligencia artificial","Solo se consulta Gemini con tu autorización previa.");
        hint(box,"La clave se cifra mediante Android Keystore y no se sube a GitHub.");
        button(box,ai.configured()?"Cambiar clave Gemini":"Introducir clave Gemini",this::configureKey,true);
        button(box,"Cambiar modelo ("+ai.model()+")",this::configureModel,false);
        addGap(box,17);
        section(box,"Lectura","Ajusta la presentación del texto bíblico.");
        hint(box,"Tamaño de letra actual: "+textSize+" sp");
        button(box,"Aumentar tamaño de letra",()->{textSize=Math.min(30,textSize+2);getPreferences(0).edit().putInt("font",textSize).apply();render();},false);
        button(box,"Disminuir tamaño de letra",()->{textSize=Math.max(14,textSize-2);getPreferences(0).edit().putInt("font",textSize).apply();render();},false);
        addGap(box,17);
        section(box,"Avisos","Notificaciones diarias opcionales.");
        button(box,"Configurar recordatorio diario",this::reminderSettings,false);
        addGap(box,17);
        addGap(box,17);
        section(box,"Actualizaciones","Recibir avisos de nuevas APK firmadas.");
        addGap(box,18);
        hint(box,"Comprobación al abrir y periódica cada 12 horas. No se instala nada sin tu consentimiento.");
        hint(box,"El repositorio es privado. Necesitas un token GitHub con acceso SOLO a Scriptura y permiso Contents: Read.");
        button(box,UpdateChecker.configured(this)?"Modificar acceso privado GitHub":"Activar avisos de actualización",
            this::configureUpdateToken,false);
        button(box,"Comprobar actualizaciones ahora",this::checkUpdatesNow,false);
        String updateError=UpdateChecker.lastError(this);
        if(!updateError.isEmpty())hint(box,"Última comprobación: "+updateError);

        section(box,"Información","Scriptura · versión "+currentVersionName());
        hint(box,"Los comentarios de Navarra son editoriales. Las respuestas de Gemini son interpretaciones generadas con IA. No se transmiten datos personales automáticamente.");
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        new AlertDialog.Builder(this).setTitle("Ajustes de Scriptura")
            .setView(scroll).setPositiveButton("Cerrar",null).show();
    }
    /** Token personal opcional y restringido al repo privado. Nunca se registra ni se copia. */
    private void configureUpdateToken(){
        EditText edit=new EditText(this);
        edit.setHint("github_pat_...");
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout wrap=vertical();
        wrap.setPadding(dp(22),dp(10),dp(22),dp(0));
        wrap.addView(edit);
        TextView explanation=text("En GitHub crea un Fine-grained token limitado a JavierRB35/Scriptura, solo Contents: Read, con caducidad. No uses un token con permisos de escritura. Se guarda cifrado en este móvil.",14,MUTED,false);
        wrap.addView(explanation);
        new AlertDialog.Builder(this).setTitle("Acceso a versiones privadas")
            .setView(wrap)
            .setNegativeButton("Cancelar",null)
            .setNeutralButton("Desactivar avisos",(d,w)->{
                UpdateChecker.clearToken(this);
                Toast.makeText(this,"Avisos de versiones desactivados",Toast.LENGTH_SHORT).show();
                render();
            })
            .setPositiveButton("Guardar y activar",(d,w)->{
                try{
                    UpdateChecker.saveToken(this,edit.getText().toString());
                    UpdateChecker.requestNotificationPermission(this);
                    checkUpdatesNow();
                }catch(IllegalArgumentException invalid){
                    showError("El token tiene un formato no reconocido. Copia únicamente la cadena completa desde GitHub, sin comillas.");
                }catch(Exception err){
                    showError("No se pudo cifrar o guardar el token en Android ("+
                        err.getClass().getSimpleName()+"). El formato puede ser correcto. No compartas tu credencial.");
                }
            }).show();
    }
    private void checkUpdatesNow(){
        if(!UpdateChecker.configured(this)){
            showError("Primero configura un token de GitHub restringido a este repositorio para acceder a sus Releases privadas.");
            return;
        }
        Toast.makeText(this,"Consultando versiones de Scriptura...",Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            UpdateChecker.Result result=UpdateChecker.check(getApplicationContext(),true);
            if(result.available())UpdateChecker.maybeNotify(getApplicationContext(),result.update);
            runOnUiThread(()->{
                if(isFinishing())return;
                AlertDialog.Builder dialog=new AlertDialog.Builder(this)
                    .setTitle(result.available()?"Actualización disponible":"Actualizaciones de Scriptura")
                    .setMessage(result.info)
                    .setNegativeButton("Cerrar",(d,w)->{if(page==0)render();});
                if(result.available()){
                    dialog.setPositiveButton("Abrir GitHub",(d,w)->
                        UpdateChecker.openRelease(this,result.update));
                }
                dialog.show();
            });
        });
    }

    private void configureKey(){
        EditText edit=new EditText(this);edit.setHint("Pega tu clave de Google AI Studio");edit.setSingleLine();
        edit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("Clave API de Gemini")
            .setMessage("Consigue una clave en aistudio.google.com/apikey. Solo se utilizará al consultar expresamente Gemini.")
            .setView(edit).setNegativeButton("Cancelar",null)
            .setNeutralButton("Eliminar clave",(d,w)->{
                try{ai.saveKey("");Toast.makeText(this,"Clave eliminada",Toast.LENGTH_SHORT).show();}
                catch(Exception e){showError(e.getMessage());}
            }).setPositiveButton("Guardar",(d,w)->{
                try{ai.saveKey(edit.getText().toString());Toast.makeText(this,"Clave almacenada",Toast.LENGTH_SHORT).show();}
                catch(Exception e){showError(e.getMessage());}
            }).show();
    }
    private void configureModel(){
        EditText edit=new EditText(this);edit.setText(ai.model());edit.setSingleLine();
        new AlertDialog.Builder(this).setTitle("Modelo de Gemini")
            .setMessage("El predeterminado es gemini-3.1-flash-lite. Comprueba en AI Studio qué modelos tienes disponibles en el nivel gratuito.")
            .setView(edit).setPositiveButton("Guardar",(d,w)->{
                try{ai.model(edit.getText().toString().trim());}catch(Exception e){showError(e.getMessage());}
            }).setNegativeButton("Cancelar",null).show();
    }
    private void reminderSettings(){
        String[] options={"Activar aviso diario a las 09:00","Desactivar avisos"};
        new AlertDialog.Builder(this).setTitle("Recordatorio de lectura")
            .setItems(options,(d,w)->{
                if(w==0){Reminders.enable(this);showError("Recordatorio diario configurado. Si Android requiere permiso para notificaciones, concédelo.");}
                else {Reminders.disable(this);Toast.makeText(this,"Recordatorio desactivado",Toast.LENGTH_SHORT).show();}
            }).show();
    }
    @Override public void onBackPressed(){
        if(page==5){
            int previous=readerOrigin;
            page=(previous>=0&&previous<=9&&previous!=5)?previous:1;
            render();
        }
        else if(page==9){page=7;render();}
        else if(page==7||page==8){page=0;render();}
        else if(page==6){
            if(atlasMode==3||atlasMode==4){atlasMode=0;render();}
            else {page=0;render();}
        }
        else if(page!=0){page=0;render();}
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        worker.shutdownNow();
        store.close();notes.close();studyAnnotations.close();introductions.close();if(historyContext!=null)historyContext.close();
        super.onDestroy();
    }
}
