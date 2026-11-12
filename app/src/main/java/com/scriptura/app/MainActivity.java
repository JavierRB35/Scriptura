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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
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
    private static final int PICK_EXISTING_BACKUP=2033;
    // Paleta variable: el modo estándar conserva los colores originales de Scriptura.
    private int DARK=Color.rgb(13,35,56), GOLD=Color.rgb(218,168,92), MUTED=Color.rgb(71,89,105);
    private int BG=Color.rgb(255,252,246), SURFACE=Color.WHITE, SOFT=Color.rgb(245,231,200);
    private int OUTLINE=Color.rgb(229,231,232), ACCENT=Color.rgb(149,100,44);
    private int NAV_BG=Color.rgb(13,35,56);
    private int displayMode=DisplayModes.NORMAL;
    private int resolvedMode=DisplayModes.NORMAL;
    private int restoredScrollY=-1;
    private int lastRenderedPage=-1;
    private final Deque<Integer> backStack=new ArrayDeque<>();
    private String searchResultsQuery="";
    private boolean readerToolsExpanded=false;
    private boolean aiSessionConsent=false;
    private boolean massAutoLoading=false;
    private boolean speechActive=false;
    private ReaderSpeech speech;
    private TextView speechLabel,fontLabel;
    private String pendingExport=null;
    private static final int PICK_EXPORT=2034;

    /** El modo «Automático» resuelve al tema claro u oscuro real del sistema. */
    private int resolveMode(int mode){
        if(mode!=DisplayModes.SYSTEM)return DisplayModes.normalize(mode);
        int ui=getResources().getConfiguration().uiMode
            &android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return ui==android.content.res.Configuration.UI_MODE_NIGHT_YES?
            DisplayModes.BATTERY:DisplayModes.NORMAL;
    }

    private void updatePalette(){
        if(resolvedMode==DisplayModes.COMFORT){
            DARK=Color.rgb(62,49,37);
            GOLD=Color.rgb(236,190,107);
            MUTED=Color.rgb(111,91,70);
            BG=Color.rgb(248,239,217);
            SURFACE=Color.rgb(255,248,232);
            SOFT=Color.rgb(242,226,193);
            OUTLINE=Color.rgb(218,197,159);
            ACCENT=Color.rgb(117,83,39);
            NAV_BG=Color.rgb(35,50,50);
        }else if(resolvedMode==DisplayModes.BATTERY){
            DARK=Color.rgb(227,230,225);
            GOLD=Color.rgb(232,193,123);
            MUTED=Color.rgb(166,173,165);
            BG=Color.BLACK;
            SURFACE=Color.rgb(13,13,13);
            SOFT=Color.rgb(24,24,24);
            OUTLINE=Color.rgb(65,65,65);
            ACCENT=Color.rgb(220,178,111);
            NAV_BG=Color.rgb(6,6,6);
        }
    }
    private BibleStore store;
    private NavarraNotes notes;
    private UpdateInstaller updateInstaller;
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
    private GroqClient groq;
    private AiRouter aiRouter;
    private ChapterPlacesDialog chapterPlaces;
    private TextView groqKeyButton, groqFallbackButton, groqFallbackStatus;
    private LinearLayout column,frame,nav;
    private ScrollView readingScroll;
    private TextView readingBible;
    private BibleStore.Passage readingPassage;
    private Runnable readingProgressTask;
    private boolean readingHasScrolled=false, readingProgressTrackingActive=false;
    private String returnToPassageRef="";
    private int returnToVerse=-1;
    private TextView caption;
    private int page=0;
    private int atlasMode=0;
    private boolean atlasReturnToStudy=false;
    private String atlasBook="Gn";
    private BibleGeography.Place atlasPlace;
    private BibleGeography.Journey atlasJourney;
    private BibleStore.Passage selected;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private boolean epubImportInProgress=false;
    private AlertDialog epubImportDialog;
    private TextView epubImportStep,epubImportDetail;
    private int epubPreviousOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
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
        b.setBackground(primary?bg(NAV_BG,12):outlined(SOFT,12));
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
    /**
     * Presenta un diálogo como hoja inferior: acciones contextuales sobre el
     * texto a un pulgar de distancia, con esquinas superiores redondeadas.
     */
    private AlertDialog showAsBottomSheet(AlertDialog.Builder builder){
        AlertDialog dialog=builder.create();
        styleAsBottomSheet(dialog);
        return dialog;
    }
    private void styleAsBottomSheet(AlertDialog dialog){
        dialog.show();
        android.view.Window window=dialog.getWindow();
        if(window!=null){
            GradientDrawable sheet=new GradientDrawable();
            sheet.setColor(SURFACE);
            sheet.setCornerRadii(new float[]{dp(22),dp(22),dp(22),dp(22),0,0,0,0});
            window.setBackgroundDrawable(sheet);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
    private void async(String wait,Callable<String> task, java.util.function.Consumer<String> ok){
        Toast.makeText(this,wait,Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            try{String s=task.call();runOnUiThread(()->ok.accept(s));}
            catch(Exception e){runOnUiThread(()->showError(e.getMessage()==null?e.toString():e.getMessage()));}
        });
    }
    @Override public void onCreate(Bundle state){
        displayMode=DisplayModes.normalize(getSharedPreferences("scriptura_display",
            MODE_PRIVATE).getInt("display_mode",DisplayModes.NORMAL));
        resolvedMode=resolveMode(displayMode);
        if(resolvedMode==DisplayModes.BATTERY)setTheme(R.style.Theme_ScripturaNight);
        super.onCreate(state);
        updatePalette();
        try{ScripturaBackup.recoverInterruptedRestore(this);}
        catch(java.io.IOException recovery){
            showError("No se pudo recuperar una restauración interrumpida. Los datos no se han modificado: "+recovery.getMessage());
            finish();return;
        }
        getWindow().setStatusBarColor(NAV_BG);
        getWindow().setNavigationBarColor(BG);
        if(Build.VERSION.SDK_INT>=26)
            getWindow().getDecorView().setSystemUiVisibility(resolvedMode==DisplayModes.BATTERY?
                0:View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        store=new BibleStore(this);notes=new NavarraNotes(this);studyAnnotations=new StudyAnnotations(this);
        updateInstaller=new UpdateInstaller(this);
        introductions=new NavarraIntroductions(this);historyContext=new HistoricalContextStore(this);
        ai=new GeminiClient(this);groq=new GroqClient(this);
        aiRouter=new AiRouter(
            request->request.geography?ai.geography(request.question):ai.explain(request.reference,request.scripture,request.context,request.question),
            request->request.geography?groq.geography(request.question):groq.explain(request.reference,request.scripture,request.context,request.question));
        textSize=getPreferences(0).getInt("font",18);
        if(state!=null){page=state.getInt("page",0);mood=state.getString("mood","Tranquilidad");
            atlasMode=state.getInt("atlasMode",0);atlasBook=state.getString("atlasBook","Gn");
            atlasReturnToStudy=state.getBoolean("atlasReturnToStudy",false);
            indexBook=state.getString("indexBook","Gn");
            indexQuery=state.getString("indexQuery","");
            indexScope=state.getInt("indexScope",0);
            readerOrigin=state.getInt("readerOrigin",1);
            restoredScrollY=state.getInt("readingScrollY",-1);
            String restoredRef=state.getString("selectedRef","");
            if(!restoredRef.isEmpty())selected=store.passage(restoredRef);
            atlasPlace=BibleGeography.place(state.getString("atlasPlace",""));
            atlasJourney=BibleGeography.journey(state.getString("atlasJourney",""));
        }
        if(page==5&&selected==null)page=readerOrigin;
        render();
        ScripturaBackup.backupAsync(this,false,(result,error)->{
            // Check after this attempt, not against failures accumulated before it finishes.
            if(error!=null&&ScripturaBackup.failureCount(this)>=3&&!isFinishing()&&!isDestroyed())
                Toast.makeText(this,"Copia automática fallida. Consulta el error en Ajustes.",Toast.LENGTH_LONG).show();
        });
        if(UpdateInstaller.ACTION_UPDATE.equals(getIntent().getAction())){
            UpdateChecker.Update target=UpdateChecker.available(this);
            UpdateChecker.markPrompted(this,target);
            setIntent(new Intent(this,MainActivity.class));
            frame.post(()->updateInstaller.start(target));
        }
        UpdateChecker.schedule(this);
        worker.execute(()->{
            UpdateChecker.Result result=UpdateChecker.check(getApplicationContext(),false);
            if(result.available())UpdateChecker.maybeNotify(getApplicationContext(),result.update);
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                if(page==0)render();
                if(!UpdateInstaller.ACTION_UPDATE.equals(getIntent().getAction()))
                    promptUpdate(result.update,false);
            });
        });
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        if(UpdateInstaller.ACTION_UPDATE.equals(intent.getAction())){
            UpdateChecker.Update available=UpdateChecker.available(this);
            UpdateChecker.markPrompted(this,available);
            setIntent(new Intent(this,MainActivity.class));
            updateInstaller.start(available);
        }
    }
    @Override protected void onPause(){
        flushReadingProgress();
        super.onPause();
    }
    @Override protected void onResume(){
        super.onResume();
        if(updateInstaller!=null)updateInstaller.onResume();
        if(!UpdateInstaller.ACTION_UPDATE.equals(getIntent().getAction())){
            UpdateChecker.Update update=UpdateChecker.available(this);
            if(update!=null&&frame!=null)frame.post(()->promptUpdate(update,false));
        }
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putInt("page",page);state.putString("mood",mood);
        state.putInt("atlasMode",atlasMode);state.putString("atlasBook",atlasBook);
        state.putBoolean("atlasReturnToStudy",atlasReturnToStudy);
        state.putString("indexBook",indexBook);
        state.putString("indexQuery",indexQuery);
        state.putInt("indexScope",indexScope);
        state.putInt("readerOrigin",readerOrigin);
        if(page==5&&readingScroll!=null)
            state.putInt("readingScrollY",readingScroll.getScrollY());
        state.putString("selectedRef",selected==null?"":selected.ref);
        state.putString("atlasPlace",atlasPlace==null?"":atlasPlace.id);
        state.putString("atlasJourney",atlasJourney==null?"":atlasJourney.id);
    }
    /** Accesos directos únicamente de Inicio. El resto de vistas no cambia. */
    private void discoveryTabs(){
        String[] labels={"Hoy","Misa","Libros","Arte","Mapas"};
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(10),dp(5),dp(10),dp(5));
        bar.setBackgroundColor(SURFACE);
        for(int i=0;i<labels.length;i++){
            final int destination=i;
            TextView tab=text(labels[i],12,i==0?DARK:MUTED,i==0);
            tab.setGravity(Gravity.CENTER);
            tab.setMinHeight(dp(48));
            tab.setSingleLine(true);
            tab.setBackground(i==0?bg(SOFT,10):bg(SURFACE,10));
            tab.setContentDescription(i==0?
                "Renovar las lecturas de hoy":"Abrir sección "+labels[i]);
            tab.setFocusable(true);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT,1);
            p.topMargin=dp(5);p.bottomMargin=dp(5);
            p.leftMargin=dp(2);p.rightMargin=dp(2);
            bar.addView(tab,p);
            tab.setOnClickListener(v->{
                switch(destination){
                    // Hoy renueva las propuestas sin cambiar de pantalla.
                    case 0:
                        getPreferences(0).edit()
                            .putLong("home_scene_seed",System.nanoTime())
                            .putInt("home_scene_day",homeDayKey())
                            .apply();
                        render();
                        break;
                    case 1:massDay=Calendar.getInstance();go(2);break;
                    case 2:openIndex();break;
                    case 3:openGallery();break;
                    case 4:atlasReturnToStudy=false;openAtlas(0);break;
                    default:break;
                }
            });
        }
        frame.addView(bar);
        View divider=new View(this);
        divider.setBackgroundColor(OUTLINE);
        frame.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    }

    private void render(){
        flushReadingProgress();
        String previousPassageRef=readingPassage==null?null:readingPassage.ref;
        boolean explicitVerseJump=page==5&&selected!=null&&returnToVerse>0
            &&selected.ref.equals(returnToPassageRef);
        if(speech!=null&&speechActive&&page!=5){speech.stop();speechActive=false;}
        readingBible=null;
        readingPassage=null;
        readingProgressTask=null;
        readingProgressTrackingActive=false;
        speechLabel=null;
        int previousPage=lastRenderedPage;
        int previousScrollY=readingScroll==null?-1:readingScroll.getScrollY();
        frame=vertical();frame.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(17),dp(12),dp(16),dp(11));head.setBackgroundColor(SURFACE);
        android.widget.ImageView mark=new android.widget.ImageView(this);
        mark.setImageResource(R.drawable.ic_scriptura_foreground);
        mark.setBackground(bg(NAV_BG,12));
        mark.setContentDescription("Libro abierto y luz, símbolo de Scriptura");
        LinearLayout.LayoutParams markParams=new LinearLayout.LayoutParams(dp(48),dp(48));
        markParams.rightMargin=dp(10);head.addView(mark,markParams);
        LinearLayout branding=vertical();
        TextView brand=text("Scriptura",25,DARK,false);serif(brand);branding.addView(brand);
        TextView sub=text("BIBLIA · CONTEXTO · REFLEXIÓN",9,MUTED,true);
        sub.setLetterSpacing(0.08f);branding.addView(sub);
        head.addView(branding,new LinearLayout.LayoutParams(0,-2,1));
        android.widget.ImageView settings=new android.widget.ImageView(this);
        settings.setImageResource(R.drawable.ic_action_settings);
        settings.setColorFilter(DARK);
        settings.setPadding(dp(13),dp(13),dp(13),dp(13));
        settings.setContentDescription("Abrir ajustes");
        settings.setFocusable(true);settings.setTooltipText("Ajustes y guía de Scriptura");
        settings.setOnClickListener(v->showSettings());
        head.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));
        frame.addView(head);
        View hairline=new View(this);hairline.setBackgroundColor(OUTLINE);
        frame.addView(hairline,new LinearLayout.LayoutParams(-1,dp(1)));
        if(page==0)discoveryTabs();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        readingScroll=scroll;
        column=vertical();column.setPadding(dp(16),dp(17),dp(16),dp(24));
        scroll.addView(column);frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        // En el lector muestra los lugares del capítulo; fuera de él conserva el atlas general.
        boolean readingChapter=page==5&&selected!=null;
        TextView mapsFixed=text(readingChapter?
            "LUGARES DEL CAPÍTULO  ·  "+Books.name(selected.book)+" "+selected.chapter:
            "MAPAS BÍBLICOS  ·  Explorar lugares y recorridos",13,DARK,true);
        mapsFixed.setGravity(Gravity.CENTER);
        mapsFixed.setMinHeight(dp(44));
        mapsFixed.setBackground(outlined(SOFT,12));
        mapsFixed.setContentDescription(readingChapter?
            "Ver lugares de "+Books.name(selected.book)+" "+selected.chapter:"Abrir Mapas bíblicos");
        mapsFixed.setFocusable(true);
        mapsFixed.setTooltipText(readingChapter?"Lugares del capítulo":"Atlas de lugares y recorridos");
        LinearLayout.LayoutParams fixedParams=new LinearLayout.LayoutParams(-1,dp(44));
        fixedParams.setMargins(dp(13),dp(3),dp(13),dp(5));
        if(page!=0)frame.addView(mapsFixed,fixedParams);
        mapsFixed.setOnClickListener(v->{
            if(page==5&&selected!=null)showPassagePlaces(selected);
            else {atlasReturnToStudy=false;openAtlas(0);}
        });

        // Cinco pestañas reales. El lector y el atlas son destinos internos.
        nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setBackgroundColor(NAV_BG);
        String[] names={"Inicio","Biblia","Misa","Para mí","Guardados"};
        int[] glyphs={R.drawable.ic_tab_home,R.drawable.ic_tab_bible,R.drawable.ic_tab_mass,
            R.drawable.ic_tab_heart,R.drawable.ic_tab_star};
        int active=(page==5||page==7||page==9||page==10)?1:(page==6||page==8?0:page);
        for(int i=0;i<names.length;i++){
            final int dest=i;boolean selected=active==i;
            int color=selected?GOLD:Color.rgb(231,236,242);
            LinearLayout item=vertical();item.setGravity(Gravity.CENTER);
            item.setContentDescription(names[i]+(selected?", pestaña seleccionada":""));
            item.setClickable(true);item.setFocusable(true);item.setMinimumHeight(dp(64));
            android.widget.ImageView glyph=new android.widget.ImageView(this);
            glyph.setImageResource(glyphs[i]);glyph.setColorFilter(color);
            glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams glyphParams=new LinearLayout.LayoutParams(dp(22),dp(22));
            glyphParams.bottomMargin=dp(3);item.addView(glyph,glyphParams);
            TextView label=text(names[i],11,color,selected);label.setGravity(Gravity.CENTER);
            label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(label);
            if(selected)item.setBackground(bg(resolvedMode==DisplayModes.BATTERY?
                Color.rgb(38,38,38):Color.rgb(29,54,78),10));
            // Altura flexible con mínimo: el texto no se recorta con fuentes grandes.
            item.setPadding(dp(2),dp(3),dp(2),dp(3));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT,1);
            nav.addView(item,lp);
            item.setOnClickListener(v->{atlasReturnToStudy=false;
                backStack.clear();searchResultsQuery="";page=dest;render();});
        }
        frame.addView(nav);setContentView(frame);
        // Transición sutil sólo al cambiar de página; los re-render de la misma
        // pantalla (estado de ánimo, fecha) no deben parpadear.
        if(previousPage!=-1&&previousPage!=page){
            frame.setAlpha(0f);
            frame.animate().alpha(1f).setDuration(130).start();
        }
        switch(page){
            case 0:home();break;case 1:search();break;case 2:mass();break;
            case 3:personal();break;case 4:saved();break;
            case 5:passagePage();break;case 6:atlas();break;
            case 7:bibleIndex();break;case 8:artGallery();break;
            case 9:bookOverview();break;case 10:myStudyMarks();break;
            default:page=0;home();
        }
        // Una actualización de la misma página (estado de ánimo, fecha de Misa,
        // favoritos) conserva el desplazamiento; navegar a otra página empieza arriba.
        if(page==5&&restoredScrollY>=0&&readingScroll!=null){
            int y=restoredScrollY;restoredScrollY=-1;
            ScrollView currentScroll=readingScroll;
            currentScroll.post(()->{
                if(page==5&&readingScroll==currentScroll)currentScroll.scrollTo(0,y);
            });
        }else if(previousScrollY>0&&readingScroll!=null&&
                ReadingProgress.shouldRestoreScroll(previousPage==page,page==5,
                    previousPassageRef,selected==null?null:selected.ref,explicitVerseJump)){
            int y=previousScrollY;
            ScrollView currentScroll=readingScroll;
            currentScroll.post(()->{
                if(readingScroll==currentScroll)currentScroll.scrollTo(0,y);
            });
        }
        lastRenderedPage=page;
        // Enable tracking only after any programmatic scroll restoration.
        if(page==5&&readingScroll!=null){
            ScrollView currentScroll=readingScroll;
            currentScroll.post(()->{
                if(page==5&&readingScroll==currentScroll)readingProgressTrackingActive=true;
            });
        }
    }

    /**
     * Navegación con memoria: cada destino interno apila la página actual para
     * que Atrás devuelva al contexto de origen, no siempre a Inicio.
     */
    private void go(int destination){
        if(destination!=page)backStack.push(page);
        page=destination;
        if(destination==1)searchResultsQuery="";
        render();
    }

    /**
     * Inicio es exclusivamente el descubrimiento diario. Misa, Biblioteca, Arte
     * y Mapas son destinos directos de la barra superior; no se apilan aquí.
     */
    private void home(){
        homeSelectionSeed();
        TextView eyebrow=text("LECTURA DEL DÍA",11,ACCENT,true);
        eyebrow.setLetterSpacing(0.08f);column.addView(eyebrow);
        addGap(column,4);
        TextView heading=text("Dos pasajes para hoy",24,DARK,false);
        serif(heading);column.addView(heading);
        hint(column,"Un pasaje del Antiguo y otro del Nuevo Testamento. No son lecturas de misa.");
        addGap(column,13);

        if(!getPreferences(0).getBoolean(GUIDE_DONE,false))welcomeCard();
        UpdateChecker.Update available=UpdateChecker.available(this);
        if(available!=null){
            LinearLayout updateCard=card(column);
            updateCard.addView(text("Nueva versión disponible",17,DARK,true));
            hint(updateCard,"Scriptura "+available.tag+" · APK oficial firmada");
            button(updateCard,"Actualizar",()->{
                UpdateChecker.markPrompted(this,available);
                updateInstaller.start(available);
            },true);
        }
        if(!store.ready()){
            LinearLayout c=card(column);
            c.addView(text("Prepara tu biblioteca",19,DARK,true));
            hint(c,"Importa tu EPUB personal de Navarra para leer los pasajes completos. Los libros, las obras y los mapas ya se pueden explorar desde arriba.");
            button(c,"Importar mi Biblia EPUB",this::pickEpub,true);
            return;
        }
        // La acción más frecuente de una app de lectura: volver a donde se quedó.
        ReadingProgress.Position lastPosition=store.lastReadPosition();
        if(lastPosition!=null){
            LinearLayout continueCard=card(column);
            TextView continueEyebrow=text("SEGUIR LEYENDO",11,ACCENT,true);
            continueEyebrow.setLetterSpacing(0.08f);
            continueCard.addView(continueEyebrow);
            continueCard.addView(text(Books.name(lastPosition.book)+" "
                +lastPosition.chapter+":"+lastPosition.verse,19,DARK,true));
            button(continueCard,"Continuar donde lo dejé",this::continueReading,true);
        }
        long seed=homeSelectionSeed();
        readingPreview("Antiguo Testamento",store.random(false,seed*911+31));
        readingPreview("Nuevo Testamento",store.random(true,seed*911+53));
        button(column,"Mostrar otros dos pasajes",()->{
            getPreferences(0).edit()
                .putLong("home_scene_seed",System.nanoTime())
                .putInt("home_scene_day",homeDayKey())
                .apply();
            render();
        },false);
        addGap(column,9);
        hint(column,"Explora más desde las secciones superiores: Misa, Libros, Arte y Mapas.");
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
    /** Ayuda inicial breve; la guía completa sigue disponible en Ajustes. */
    private void welcomeCard(){
        LinearLayout welcome=card(column);
        welcome.addView(text("Bienvenido a Scriptura",17,DARK,true));
        hint(welcome,"Explora cada sección desde la barra superior. La guía completa está en Ajustes.");
        if(!store.ready())button(welcome,"Importar mi Biblia (opcional)",()->{
            getPreferences(0).edit().putBoolean(GUIDE_DONE,true).apply();
            pickEpub();
        },false);
        button(welcome,"Entendido",()->{
            getPreferences(0).edit().putBoolean(GUIDE_DONE,true).apply();
            render();
        },false);
    }
    private void showQuickGuide(){
        new AlertDialog.Builder(this).setTitle("Cómo empezar con Scriptura")
            .setMessage("Scriptura funciona en parte sin conexión y sin cuentas.\n\n"+
                "• Inicio: lecturas sugeridas, índice y arte.\n"+
                "• Biblia: lectura de capítulos, referencias y búsqueda (requiere importar tu EPUB).\n"+
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
    /** Tarjeta compacta: todo el bloque abre el lector, sin botones gigantes. */
    private void readingPreview(String label,BibleStore.Passage p){
        LinearLayout c=card(column);
        c.setPadding(dp(15),dp(13),dp(15),dp(13));
        c.addView(text(label.toUpperCase(Locale.ROOT),11,ACCENT,true));
        if(p==null){hint(c,"No hay un pasaje disponible.");return;}
        TextView reference=text(p.ref,19,DARK,true);
        c.addView(reference);
        TextView excerpt=text(p.text,14,MUTED,false);
        excerpt.setMaxLines(3);
        excerpt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(excerpt);
        addGap(c,7);
        c.addView(text("Leer y reflexionar  ›",13,ACCENT,true));
        c.setClickable(true);
        c.setFocusable(true);
        c.setContentDescription(label+", "+p.ref+". Leer y reflexionar");
        c.setOnClickListener(v->open(p));
    }
    private void search(){
        searchResultsQuery="";
        title(column,"Leer y buscar en la Biblia");
        if(store.ready()){
            ReadingProgress.Position last=store.lastReadPosition();
            if(last!=null){
                button(column,"↪ Continuar lectura · "+Books.name(last.book)+" "
                    +last.chapter+":"+last.verse,this::continueReading,true);
            }
        }
        if(!store.ready()){
            hint(column,"La búsqueda de versículos y palabras necesita tu ejemplar EPUB de Navarra. Puedes consultar gratis el índice, los mapas y las obras sin importarlo.");
            button(column,"Importar mi Biblia para leer",this::pickEpub,true);
            button(column,"Explorar el índice de los 73 libros",this::openIndex,false);
            button(column,"Ver obras de arte bíblico",this::openGallery,false);
            button(column,"Mis subrayados y anotaciones",()->go(10),false);
            return;
        }
        LinearLayout c=card(column);
        EditText term=input(c,"Ej. Juan 3:16-18 · Salmo 23 · Génesis",false);
        term.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        term.setOnEditorActionListener((v,id,event)->{lookup(term.getText().toString());return true;});
        button(c,"Buscar referencia o palabra",()->lookup(term.getText().toString()),true);
        button(c,"Índice de libros y sus introducciones",this::openIndex,false);
        button(c,"Elegir capítulo",this::openIndex,false);
        button(c,"Galería de arte bíblico",this::openGallery,false);
        button(c,"Mis subrayados y anotaciones",()->go(10),false);
        hint(column,"El historial de consultas está en la pestaña Guardados. Puedes introducir un libro, capítulo, versículo, intervalo o una palabra del texto.");
    }
    /** Vista de consulta sobre la base de anotaciones existente, sin alterar sus registros. */
    private void myStudyMarks(){
        title(column,"Mis subrayados y anotaciones");
        hint(column,"Tus textos marcados y notas, ordenados por libros del Antiguo al Nuevo Testamento. Pulsa una entrada para volver al versículo en la Biblia.");
        final List<StudyAnnotations.Item> items;
        try{
            items=AnnotationLibrary.ordered(studyAnnotations.all());
        }catch(RuntimeException error){
            showError("No se pudieron cargar tus subrayados y anotaciones.");
            return;
        }
        if(items.isEmpty()){
            LinearLayout empty=card(column);
            hint(empty,"Todavía no tienes subrayados ni anotaciones. Abre un capítulo, mantén pulsado un fragmento y elige «Subrayar» o «Anotar».");
            button(empty,"Volver a la Biblia",()->go(1),false);
            return;
        }
        // Índice compacto: solo libros con registros, en el mismo orden que la lista.
        java.util.LinkedHashMap<String,Integer> bookCounts=AnnotationLibrary.countsByBook(items);
        final ScrollView indexScroll=readingScroll;
        final java.util.Map<String,TextView> bookHeadings=new java.util.HashMap<>();
        LinearLayout indexCard=card(column);
        indexCard.addView(text("Índice por libros",18,DARK,true));
        hint(indexCard,"Pulsa un libro para ir a sus subrayados y anotaciones.");
        LinearLayout indexRow=null;
        int position=0;
        for(java.util.Map.Entry<String,Integer> book:bookCounts.entrySet()){
            if(position%2==0){
                indexRow=new LinearLayout(this);
                indexRow.setOrientation(LinearLayout.HORIZONTAL);
                indexCard.addView(indexRow);
            }
            final String bookCode=book.getKey();
            String bookName=Books.name(bookCode);
            TextView shortcut=text(bookName+" ("+book.getValue()+")",14,ACCENT,true);
            shortcut.setGravity(Gravity.CENTER_VERTICAL);
            shortcut.setMinHeight(dp(48));
            shortcut.setPadding(dp(10),dp(9),dp(10),dp(9));
            shortcut.setBackground(outlined(SOFT,10));
            shortcut.setFocusable(true);
            shortcut.setContentDescription("Ir a "+bookName+", "+book.getValue()+" subrayados y anotaciones");
            LinearLayout.LayoutParams shortcutParams=new LinearLayout.LayoutParams(0,-2,1);
            shortcutParams.setMargins(dp(3),dp(4),dp(3),dp(4));
            indexRow.addView(shortcut,shortcutParams);
            shortcut.setOnClickListener(v->{
                TextView heading=bookHeadings.get(bookCode);
                if(heading==null)return;
                indexScroll.post(()->{
                    if(page==10&&readingScroll==indexScroll)
                        indexScroll.smoothScrollTo(0,Math.max(0,heading.getTop()-dp(12)));
                });
            });
            position++;
        }
        String currentBook="";
        boolean oldHeading=false,newHeading=false;
        for(StudyAnnotations.Item item:items){
            boolean newTestament=Books.isNew(item.book);
            if(newTestament&&!newHeading){
                addGap(column,12);
                section(column,"Nuevo Testamento","Evangelios, escritos apostólicos y Apocalipsis.");
                newHeading=true;
            }else if(!newTestament&&!oldHeading){
                addGap(column,12);
                section(column,"Antiguo Testamento","Libros anteriores al Nuevo Testamento.");
                oldHeading=true;
            }
            if(!item.book.equals(currentBook)){
                currentBook=item.book;
                addGap(column,10);
                TextView bookHeading=text(Books.name(item.book),18,DARK,true);
                column.addView(bookHeading);
                bookHeadings.put(item.book,bookHeading);
            }
            LinearLayout entry=card(column);
            entry.setPadding(dp(15),dp(13),dp(15),dp(13));
            String source=Books.name(item.book)+" "+item.chapter+":"+
                verseRange(item.verse,item.endVerse);
            entry.addView(text("FUENTE · "+source,13,ACCENT,true));
            entry.addView(text(annotationType(item),12,MUTED,true));
            String excerpt=item.quote.trim();
            if(excerpt.isEmpty()&&store.ready()){
                String query=Books.name(item.book)+" "+item.chapter+":"+item.verse+
                    (item.endVerse>item.verse?"-"+item.endVerse:"");
                BibleStore.Passage sourcePassage=store.passage(query);
                if(sourcePassage!=null)excerpt=sourcePassage.text.trim();
            }
            if(!excerpt.isEmpty()){
                addGap(entry,8);
                String preview=excerpt.length()>320?excerpt.substring(0,320)+"…":excerpt;
                TextView quote=text("«"+preview+"»",15,DARK,false);
                serif(quote);
                entry.addView(quote);
            }
            if(!item.body.trim().isEmpty()){
                addGap(entry,8);
                TextView note=text("Anotación: "+item.body,14,DARK,false);
                entry.addView(note);
            }
            if(!item.url.isEmpty())
                hint(entry,"Contiene un enlace asociado; puedes consultarlo al abrir la anotación.");
            addGap(entry,8);
            entry.addView(text("Leer en la Biblia  ›",13,ACCENT,true));
            entry.setClickable(true);
            entry.setFocusable(true);
            entry.setContentDescription(annotationType(item)+". Fuente: "+source+". Abrir en la Biblia");
            entry.setOnClickListener(v->openSavedMark(item));
        }
    }

    private void openSavedMark(StudyAnnotations.Item item){
        if(!store.ready()){
            showError("Para abrir el texto original, importa tu ejemplar EPUB de la Biblia.");
            return;
        }
        BibleStore.Passage chapter=store.passage(Books.name(item.book)+" "+item.chapter);
        if(chapter==null){
            showError("No se encuentra ese capítulo en tu Biblia importada.");
            return;
        }
        open(chapter,item.verse);
    }

    private void lookup(String query){
        if(query.trim().isEmpty())return;
        BibleStore.Passage passage=store.passage(query);
        if(passage!=null){open(passage);return;}
        List<BibleStore.Passage> hits=store.searchText(query,35);
        if(hits.isEmpty()){showError("No se ha encontrado la referencia o expresión «"+query+"».");return;}
        searchResultsQuery=query;
        column.removeAllViews();title(column,"Coincidencias de «"+query+"»");
        hint(column,"Primeros "+hits.size()+" resultados de la biblioteca local.");
        for(BibleStore.Passage p:hits){
            LinearLayout c=card(column);c.addView(text(p.ref,16,DARK,true));c.addView(text(p.text,14,MUTED,false));
            c.setOnClickListener(v->open(p));
        }
    }
    /** Los 73 libros se eligen en el índice con filtro y secciones, no en una lista plana. */
    private void browseBooks(){
        openIndex();
    }
    /** Rejilla compacta de capítulos: Salmos ya no exige desplazar 150 filas. */
    private void browseChapters(String book){
        int max=store.chapters(book);if(max==0){showError("Este libro no aparece en la biblioteca importada");return;}
        if(max<=15){
            String[] ch=new String[max];for(int i=0;i<max;i++)ch[i]="Capítulo "+(i+1);
            new AlertDialog.Builder(this).setTitle(Books.name(book))
                .setItems(ch,(d,which)->lookup(Books.name(book)+" "+(which+1))).setNegativeButton("Cancelar",null).show();
            return;
        }
        LinearLayout content=vertical();pad(content,14);
        content.addView(text("Elige un capítulo del 1 al "+max,14,MUTED,false));
        addGap(content,8);
        LinearLayout grid=vertical();
        int columns=5;
        final AlertDialog[] holder=new AlertDialog[1];
        for(int start=1;start<=max;start+=columns){
            LinearLayout row=new LinearLayout(this);
            for(int n=start;n<start+columns&&n<=max;n++){
                final int chapter=n;
                TextView cell=text(String.valueOf(n),15,DARK,true);
                cell.setGravity(Gravity.CENTER);
                cell.setBackground(outlined(BG,10));
                cell.setContentDescription("Capítulo "+chapter+" de "+Books.name(book));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);
                p.setMargins(dp(3),dp(3),dp(3),dp(3));
                row.addView(cell,p);
                cell.setOnClickListener(v->{
                    if(holder[0]!=null)holder[0].dismiss();
                    lookup(Books.name(book)+" "+chapter);
                });
            }
            grid.addView(row);
        }
        ScrollView gridScroll=new ScrollView(this);
        gridScroll.addView(grid,new LinearLayout.LayoutParams(-1,-2));
        content.addView(gridScroll,new LinearLayout.LayoutParams(-1,dp(330)));
        holder[0]=new AlertDialog.Builder(this).setTitle(Books.name(book))
            .setView(content).setNegativeButton("Cancelar",null).create();
        holder[0].show();
    }
    private void continueReading(){
        ReadingProgress.Position position=store.lastReadPosition();
        if(position==null){showError("No hay ninguna posición de lectura guardada.");return;}
        BibleStore.Passage chapter=store.passage(Books.name(position.book)+" "+position.chapter);
        if(chapter==null){
            showError("No se encuentra ese capítulo. Comprueba tu Biblia importada.");
            return;
        }
        open(chapter,position.verse);
    }
    private void open(BibleStore.Passage p){open(p,-1);}
    private void open(BibleStore.Passage p,int targetVerse){
        if(page!=5){
            readerOrigin=page;
            backStack.push(page);
        }
        // Cada pasaje empieza con el contexto cerrado; el estado no se hereda
        // accidentalmente de la lectura anterior.
        historicalExpanded=false;
        searchResultsQuery="";
        selected=p;store.record(p.ref);page=5;
        returnToPassageRef=targetVerse>0?p.ref:"";
        returnToVerse=targetVerse>0?targetVerse:-1;
        render();
    }
    /**
     * Flechas de lectura junto al título y al pie del texto: los controles
     * no quedan enterrados bajo las acciones de estudio o favoritos.
     */
    private void chapterNavigation(LinearLayout parent,BibleStore.Passage passage,boolean afterText){
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,-2);
        if(afterText)row.topMargin=dp(16);
        parent.addView(bar,row);

        int chapter=passage.chapter;
        boolean hasPrevious=chapter>1;
        int totalChapters=store.chapters(passage.book);
        boolean hasNext=chapter<totalChapters;
        TextView previous=chapterArrow("←","Capítulo anterior",passage.book,chapter-1,hasPrevious);
        TextView next=chapterArrow("→","Capítulo siguiente",passage.book,chapter+1,hasNext);
        bar.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));

        String label=afterText?"Capítulo "+chapter:passage.ref;
        TextView heading=text(label,afterText?14:26,DARK,afterText);
        if(totalChapters>0){
            // Mostrar el total en pequeño en la misma línea, sin otra fila de cabecera.
            int end=label.indexOf(':');
            if(end<0)end=label.length();
            String total="/"+totalChapters;
            SpannableString count=new SpannableString(
                label.substring(0,end)+total+label.substring(end));
            count.setSpan(new RelativeSizeSpan(0.55f),end,end+total.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            heading.setText(count);
            heading.setContentDescription(passage.ref+", capítulo "+chapter+
                " de "+totalChapters);
        }
        if(!afterText)serif(heading);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(dp(4),0,dp(4),0);
        bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));
        // Control de tipografía junto al capítulo: no hay que ir a Ajustes ni buscarlo.
        if(!afterText){
            TextView typeChip=text("Aa",15,DARK,true);
            typeChip.setGravity(Gravity.CENTER);
            typeChip.setMinHeight(dp(48));
            typeChip.setPadding(dp(9),0,dp(9),0);
            typeChip.setBackground(outlined(SOFT,12));
            typeChip.setContentDescription("Ajustar tamaño y aspecto de la lectura");
            LinearLayout.LayoutParams chipParams=new LinearLayout.LayoutParams(-2,dp(48));
            chipParams.leftMargin=dp(8);
            bar.addView(typeChip,chipParams);
            typeChip.setOnClickListener(v->showTypographyPanel());
        }
    }

    private TextView chapterArrow(String symbol,String action,String book,int target,boolean available){
        TextView arrow=text(symbol,29,available?DARK:MUTED,true);
        arrow.setGravity(Gravity.CENTER);
        arrow.setMinHeight(dp(48));
        arrow.setBackground(outlined(available?SOFT:BG,12));
        arrow.setContentDescription(available?action+", "+Books.name(book)+" "+target:
            "No hay "+action.toLowerCase(java.util.Locale.ROOT));
        arrow.setEnabled(available);
        arrow.setFocusable(available);
        arrow.setAlpha(available?1f:0.35f);
        if(available)arrow.setOnClickListener(v->{
            BibleStore.Passage next=store.passage(Books.name(book)+" "+target);
            if(next==null){showError("No se ha encontrado el capítulo seleccionado.");return;}
            // Finish tracking the previous chapter before moving the bookmark.
            flushReadingProgress();
            int first=AnnotationAnchors.firstVerse(next.text);
            if(first>0)store.saveReadingPosition(new ReadingProgress.Position(book,target,first));
            open(next);
        });
        return arrow;
    }

    private void passagePage(){
        if(selected==null){page=1;render();return;}
        BibleStore.Passage p=selected;
        TextView edition=text("TEXTO BÍBLICO · UNIVERSIDAD DE NAVARRA",11,ACCENT,true);column.addView(edition);
        chapterNavigation(column,p,false);
        button(column,"✦ Interpretación con IA",()->interpret(p,
            "Explica este pasaje de forma breve y fiel a su contexto.",false),true);
        addGap(column,6);
        LinearLayout c=card(column);c.setBackground(outlined(BG,16));
        TextView bible=text(p.text,textSize,DARK,false);serif(bible);
        bible.setTextIsSelectable(true);
        applyStudyMarks(bible,p);
        addDefinitionAction(bible,p);
        c.addView(bible);
        readingBible=bible;
        readingPassage=p;
        final ScrollView currentScroll=readingScroll;
        readingProgressTask=()->{
            if(page==5&&readingScroll==currentScroll)saveVisibleReadingPosition(false);
        };
        currentScroll.setOnScrollChangeListener((v,sx,sy,oldX,oldY)->{
            if(!readingProgressTrackingActive||page!=5||
                    readingScroll!=currentScroll||sy==oldY)return;
            readingHasScrolled=true;
            currentScroll.removeCallbacks(readingProgressTask);
            currentScroll.postDelayed(readingProgressTask,550);
        });
        returnToSavedVerse(bible,p);
        chapterNavigation(c,p,true);
        // Jerarquía clara: acciones rápidas compactas, IA destacada y el resto
        // agrupado en «Más herramientas» para no enterrar el avance de lectura.
        LinearLayout quick=new LinearLayout(this);
        quick.setGravity(Gravity.CENTER_VERTICAL);
        boolean favorite=store.isFavorite(p.ref);
        quick.addView(quickAction(R.drawable.ic_tab_star,favorite?"Guardado":"Favorito",
            favorite?GOLD:MUTED,()->{store.toggleFavorite(p.ref);render();}));
        quick.addView(quickAction(R.drawable.ic_action_copy,"Copiar",MUTED,()->{
            ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(
                ClipData.newPlainText(p.ref,p.ref+"\n"+p.text+"\nBiblia de Navarra"));
            Toast.makeText(this,"Texto copiado",Toast.LENGTH_SHORT).show();
        }));
        quick.addView(quickAction(R.drawable.ic_action_share,"Compartir",MUTED,()->{
            Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT,p.ref+"\n"+p.text+"\nBiblia de Navarra");
            startActivity(Intent.createChooser(i,"Compartir pasaje"));
        }));
        LinearLayout speechItem=quickAction(R.drawable.ic_action_volume,
            speechActive?"Detener":"Escuchar",speechActive?GOLD:MUTED,this::toggleSpeech);
        speechLabel=(TextView)speechItem.getChildAt(1);
        quick.addView(speechItem);
        quick.addView(quickAction(R.drawable.ic_action_more,"Más",MUTED,()->{
            readerToolsExpanded=!readerToolsExpanded;
            render();
        }));
        c.addView(quick,new LinearLayout.LayoutParams(-1,-2));
        if(readerToolsExpanded){
            LinearLayout tools=card(column);
            hint(tools,"Más herramientas de estudio para este pasaje");
            button(tools,"Marcar aquí mi punto de lectura",()->{
                if(saveVisibleReadingPosition(true))
                    Toast.makeText(this,"Punto de lectura guardado",Toast.LENGTH_SHORT).show();
                else showError("No se ha podido determinar el versículo visible.");
            },false);
            button(tools,"Añadir nota por versículos",()->manualStudyAnnotation(p),false);
            button(tools,"Comentarios de la Biblia de Navarra",()->showNavarraNote(p),false);
            button(tools,"Profundizar o preguntar",()->ask(p),false);
            button(tools,"Lugares del capítulo",()->showPassagePlaces(p),false);
            button(tools,"Atlas del libro "+Books.name(p.book),()->openBookAtlas(p.book),false);
            button(tools,"Índice: "+Books.name(p.book),()->openBookInfo(p.book),false);
            java.util.List<BiblicalArt.Painting> related=BiblicalArt.forPassage(p.book,p.chapter);
            if(!related.isEmpty())
                button(tools,"Ver obras pictóricas sobre este pasaje",()->openGalleryFor(p.book),false);
        }
        hint(c,"Mantén pulsado el texto y elige «Subrayar», «Anotar» o «Definir». Los subrayados se guardan sin nota; toca uno para quitarlo.");
        TextView alternative=text("¿No aparece Definir? Escribir palabra",13,ACCENT,true);
        alternative.setPadding(dp(1),dp(10),dp(1),dp(10));
        alternative.setOnClickListener(v->askForWord(p));
        c.addView(alternative);
        showStudyAnnotations(c,p);
        showHistoricalContext(p);
    }

    /** Acción compacta con icono vectorial y etiqueta, alineada con la barra inferior. */
    private LinearLayout quickAction(int icon,String label,int tint,Runnable action){
        LinearLayout item=vertical();item.setGravity(Gravity.CENTER);
        item.setClickable(true);item.setFocusable(true);item.setMinimumHeight(dp(54));
        android.widget.ImageView glyph=new android.widget.ImageView(this);
        glyph.setImageResource(icon);glyph.setColorFilter(tint);
        glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        item.addView(glyph,new LinearLayout.LayoutParams(dp(21),dp(21)));
        TextView lab=text(label,11,MUTED,false);lab.setGravity(Gravity.CENTER);
        lab.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        item.addView(lab);
        item.setContentDescription(label);
        item.setBackground(outlined(SURFACE,12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.setMargins(dp(3),dp(8),dp(3),dp(2));
        item.setLayoutParams(lp);
        item.setOnClickListener(v->action.run());
        return item;
    }

    /** Escucha local del pasaje con el motor de voz de Android; nada sale del teléfono. */
    private void toggleSpeech(){
        if(readingPassage==null)return;
        if(speech==null)speech=new ReaderSpeech();
        speech.toggle(this,readingPassage.text,new ReaderSpeech.Listener(){
            @Override public void onSpeaking(boolean active){
                runOnUiThread(()->{
                    speechActive=active;
                    if(speechLabel!=null)speechLabel.setText(active?"Detener":"Escuchar");
                });
            }
            @Override public void onError(String message){
                runOnUiThread(()->{speechActive=false;showError(message);});
            }
        });
    }

    /**
     * Panel de tipografía con vista previa y aspecto: el ajuste de lectura vive
     * junto al texto, no enterrado en Ajustes.
     */
    private void showTypographyPanel(){
        LinearLayout content=vertical();pad(content,18);
        content.addView(text("Tipografía de lectura",19,DARK,true));
        addGap(content,10);
        TextView preview=text("En el principio creó Dios el cielo y la tierra.",
            textSize,DARK,false);
        serif(preview);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(6),dp(12),dp(6),dp(12));
        preview.setBackground(outlined(BG,12));
        content.addView(preview);
        addGap(content,12);
        LinearLayout sizes=new LinearLayout(this);
        sizes.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus=text("A −",18,DARK,true);
        TextView current=text(textSize+" sp",14,MUTED,false);
        TextView plus=text("A +",18,DARK,true);
        for(TextView control:new TextView[]{minus,plus}){
            control.setGravity(Gravity.CENTER);
            control.setMinHeight(dp(46));
            control.setBackground(outlined(SOFT,12));
        }
        current.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams minusParams=new LinearLayout.LayoutParams(0,-2,1);
        LinearLayout.LayoutParams currentParams=new LinearLayout.LayoutParams(0,-2,1);
        LinearLayout.LayoutParams plusParams=new LinearLayout.LayoutParams(0,-2,1);
        minusParams.setMargins(dp(3),0,dp(3),0);
        currentParams.setMargins(dp(3),0,dp(3),0);
        plusParams.setMargins(dp(3),0,dp(3),0);
        sizes.addView(minus,minusParams);sizes.addView(current,currentParams);sizes.addView(plus,plusParams);
        minus.setOnClickListener(v->adjustTextSize(-2,preview,current));
        plus.setOnClickListener(v->adjustTextSize(2,preview,current));
        content.addView(sizes);
        addGap(content,12);
        content.addView(text("Aspecto",15,DARK,true));
        addGap(content,6);
        String[] modes={"Normal","Lectura cómoda","Ahorro de batería · OLED","Automático · sistema"};
        for(int i=0;i<modes.length;i+=2){
            LinearLayout line=new LinearLayout(this);
            for(int j=i;j<Math.min(i+2,modes.length);j++){
                final int mode=j;
                boolean chosen=displayMode==mode;
                TextView chip=text(modes[j],13,chosen?Color.WHITE:DARK,chosen);
                chip.setGravity(Gravity.CENTER);chip.setMinHeight(dp(46));
                chip.setPadding(dp(6),dp(6),dp(6),dp(6));
                chip.setBackground(chosen?bg(NAV_BG,11):outlined(SOFT,11));
                chip.setContentDescription("Aspecto "+modes[j]+(chosen?", seleccionado":""));
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);
                params.setMargins(dp(3),dp(4),dp(3),dp(4));
                line.addView(chip,params);
                chip.setOnClickListener(v->{
                    if(mode==displayMode)return;
                    getSharedPreferences("scriptura_display",MODE_PRIVATE).edit()
                        .putInt("display_mode",DisplayModes.normalize(mode)).apply();
                    // recreate() conserva página y desplazamiento por onSaveInstanceState.
                    recreate();
                });
            }
            content.addView(line);
        }
        showAsBottomSheet(new AlertDialog.Builder(this)
            .setTitle("Leer con comodidad").setView(content)
            .setNegativeButton("Listo",null));
    }

    private void adjustTextSize(int delta,TextView preview,TextView current){
        textSize=Math.max(14,Math.min(30,textSize+delta));
        getPreferences(0).edit().putInt("font",textSize).apply();
        if(preview!=null)preview.setTextSize(textSize);
        if(current!=null)current.setText(textSize+" sp");
        if(fontLabel!=null)fontLabel.setText("Tamaño de letra actual: "+textSize+" sp");
        // Efecto inmediato en el texto abierto, sin perder la posición de lectura.
        if(readingBible!=null)readingBible.setTextSize(textSize);
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
                showError("Selecciona texto de uno o varios versículos del mismo capítulo, o usa «Añadir nota por versículos».");
                return;
            }
            editStudyAnnotation(passage,anchor,null);
        },(quote,start,end)->{
            AnnotationAnchors.Anchor anchor=AnnotationAnchors.fromSelection(
                passage.text,start,end);
            if(anchor==null){
                showError("Para subrayar, selecciona texto de uno o varios versículos del mismo capítulo.");
                return;
            }
            try{
                studyAnnotations.save(0,passage.book,passage.chapter,anchor.verse,anchor.endVerse,
                    anchor.offset,anchor.quote,StudyAnnotations.UNDERLINE,"","");
                Toast.makeText(this,"Texto subrayado",Toast.LENGTH_SHORT).show();
                redrawAtVerse(anchor.verse);
            }catch(RuntimeException error){
                showError("No se pudo guardar el subrayado.");
            }
        },(quote,start,end)->interpretSelectedFragment(passage,start,end));
    }

    /** Mantiene exactamente la misma petición con ambos proveedores, si el respaldo está autorizado. */
    private AiRouter.Result explainWithFallback(String ref,String scripture,String context,String question)
            throws Exception {
        return aiRouter.explain(new AiRouter.Request(ref,scripture,context,question),
            groq.fallbackEnabled(),ai.model(),GroqClient.MODEL);
    }

    /** Consulta puntual con texto acotado; no redibuja el lector ni modifica el EPUB. */
    private void interpretSelectedFragment(BibleStore.Passage passage,int start,int end){
        GeminiSelection.Fragment fragment=GeminiSelection.fromSelection(
            passage.text,Books.name(passage.book),passage.chapter,start,end);
        if(fragment==null){
            showError("Selecciona texto bíblico válido (hasta 1.200 caracteres) para interpretarlo.");
            return;
        }
        if(!ai.configured()){
            showError("Configura primero tu clave de Gemini en ⚙ Ajustes.");
            return;
        }
        final ScrollView originalScroll=readingScroll;
        final int savedY=originalScroll==null?0:originalScroll.getScrollY();
        Runnable proceed=()->{
                LinearLayout content=vertical();
                pad(content,16);
                content.addView(text(fragment.reference,14,ACCENT,true));
                TextView quoteText=text("«"+fragment.quote+"»",15,DARK,false);
                serif(quoteText);
                content.addView(quoteText);
                addGap(content,15);
                TextView explanation=text("Consultando inteligencia artificial…",16,DARK,false);
                explanation.setTextIsSelectable(true);
                content.addView(explanation);
                ScrollView modalScroll=new ScrollView(this);
                modalScroll.addView(content);
                AlertDialog result=new AlertDialog.Builder(this)
                    .setTitle("Interpretación · "+fragment.reference)
                    .setView(modalScroll)
                    .setPositiveButton("Cerrar",null)
                    .create();
                result.setOnDismissListener(ignored->{
                    // No render(); la lectura original permanece tal cual y se repone su scroll.
                    if(page==5&&selected!=null&&selected.ref.equals(passage.ref)&&
                            originalScroll!=null&&readingScroll==originalScroll)
                        originalScroll.post(()->{
                            if(readingScroll==originalScroll)originalScroll.scrollTo(0,savedY);
                        });
                });
                styleAsBottomSheet(result);
                worker.execute(()->{
                    try{
                        String answer=explainWithFallback(fragment.reference,fragment.context,
                            "Texto de lectura bíblica personal, sin notas privadas.",
                            GeminiSelection.question(fragment)).displayText();
                        runOnUiThread(()->{
                            if(!isFinishing()&&!isDestroyed()&&result.isShowing())
                                explanation.setText(answer);
                        });
                    }catch(Exception error){
                        runOnUiThread(()->{
                            if(!isFinishing()&&!isDestroyed()&&result.isShowing())
                                explanation.setText("No se ha podido generar la interpretación. "
                                    + "Comprueba la conexión, tus claves y las cuotas disponibles.");
                        });
                    }
                });
        };
        if(aiSessionConsent){proceed.run();return;}
        LinearLayout consent=vertical();consent.setPadding(dp(22),dp(10),dp(22),dp(4));
        consent.addView(text("Se enviará a Gemini el fragmento seleccionado, su referencia y parte "
            +"del texto cercano para contextualizarlo. "
            +(groq.fallbackEnabled() ?
                "Si Gemini falla, estos mismos datos podrán enviarse automáticamente a Groq, "
                +"tal como autorizaste en Ajustes. " : "")
            +"No se envían el EPUB completo ni las notas personales.",15,DARK,false));
        addGap(consent,10);
        android.widget.CheckBox remember=new android.widget.CheckBox(this);
        remember.setText("No volver a preguntar durante esta sesión");
        remember.setTextSize(14);remember.setTextColor(DARK);
        consent.addView(remember);
        new AlertDialog.Builder(this)
            .setTitle("Interpretar selección con IA")
            .setView(consent)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Consultar",(d,w)->{
                if(remember.isChecked())aiSessionConsent=true;
                proceed.run();
            }).show();
    }

    /**
     * Consultations alone never replace the bookmark: a deliberate scroll,
     * chapter change, or manual mark is required.
     */
    private boolean saveVisibleReadingPosition(boolean force){
        if((!force&&!readingHasScrolled)||readingBible==null||readingPassage==null||
                readingScroll==null||readingBible.getLayout()==null)return false;
        int[] textTop=new int[2],scrollTop=new int[2];
        readingBible.getLocationOnScreen(textTop);
        readingScroll.getLocationOnScreen(scrollTop);
        int relativeY=Math.max(0,scrollTop[1]+dp(24)-textTop[1]);
        relativeY=Math.min(relativeY,Math.max(0,readingBible.getLayout().getHeight()-1));
        int visibleLine=readingBible.getLayout().getLineForVertical(relativeY);
        int charOffset=readingBible.getLayout().getLineStart(visibleLine);
        int verse=ReadingProgress.verseAtOffset(readingPassage.text,charOffset);
        if(verse<1&&readingPassage.first==readingPassage.last)verse=readingPassage.first;
        if(verse<1)return false;
        ReadingProgress.Position current=new ReadingProgress.Position(
            readingPassage.book,readingPassage.chapter,verse);
        ReadingProgress.Position last=store.lastReadPosition();
        if(!current.equals(last))store.saveReadingPosition(current);
        readingHasScrolled=false;
        return true;
    }
    private void flushReadingProgress(){
        if(readingProgressTask!=null&&readingScroll!=null)
            readingScroll.removeCallbacks(readingProgressTask);
        saveVisibleReadingPosition(false);
    }
    /** Reload the passage while returning to the actual verse, not the chapter heading. */
    private void redrawAtVerse(int verse){
        if(selected==null||page!=5)return;
        returnToPassageRef=selected.ref;
        returnToVerse=verse;
        render();
    }
    private void returnToSavedVerse(TextView bible,BibleStore.Passage passage){
        if(returnToVerse<1||!passage.ref.equals(returnToPassageRef))return;
        final int verse=returnToVerse;
        returnToVerse=-1;
        returnToPassageRef="";
        final ScrollView scroll=readingScroll;
        if(scroll==null)return;
        scroll.post(()->{
            if(page!=5||selected==null||!selected.ref.equals(passage.ref)
                    ||readingScroll!=scroll||bible.getLayout()==null)return;
            int offset=AnnotationAnchors.verseStart(passage.text,verse);
            if(offset<0)return;
            int line=bible.getLayout().getLineForOffset(offset);
            android.graphics.Rect rect=new android.graphics.Rect(
                0,bible.getLayout().getLineTop(line),
                bible.getWidth(),bible.getLayout().getLineBottom(line));
            scroll.offsetDescendantRectToMyCoords(bible,rect);
            scroll.scrollTo(0,Math.max(0,scroll.getScrollY()+rect.top-dp(88)));
        });
    }

    /** Annotations belong to a verse or verse range in the user-imported EPUB. */
    private static String verseRange(int start,int end){
        return start==end?String.valueOf(start):start+"–"+end;
    }
    private static String annotationType(StudyAnnotations.Item item){
        if(StudyAnnotations.UNDERLINE.equals(item.kind))return "Subrayado";
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
                passage.text,item.verse,item.endVerse,item.offset,item.quote);
            if(range==null)continue;
            if(!StudyAnnotations.UNDERLINE.equals(item.kind)){
                int color;
                if(displayMode==DisplayModes.BATTERY)
                    color=(StudyAnnotations.MAP.equals(item.kind)||StudyAnnotations.ATLAS.equals(item.kind))?
                        Color.rgb(30,61,41):StudyAnnotations.WEB.equals(item.kind)?
                        Color.rgb(29,48,73):Color.rgb(82,62,27);
                else
                    color=(StudyAnnotations.MAP.equals(item.kind)||StudyAnnotations.ATLAS.equals(item.kind))?
                        Color.rgb(207,236,216):StudyAnnotations.WEB.equals(item.kind)?
                        Color.rgb(208,229,252):Color.rgb(255,234,174);
                marked.setSpan(new android.text.style.BackgroundColorSpan(color),
                    range.start,range.end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
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
                    labels[i]=annotationType(touched.get(i))+" · versículos "+
                        verseRange(touched.get(i).verse,touched.get(i).endVerse);
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
            if(item.verse<=passage.last&&item.endVerse>=passage.first)visible.add(item);
        if(visible.isEmpty())return;
        addGap(parent,12);
        parent.addView(text("MIS SUBRAYADOS Y NOTAS · "+visible.size(),12,ACCENT,true));
        for(StudyAnnotations.Item item:visible){
            String label=(StudyAnnotations.UNDERLINE.equals(item.kind)?"﹏  ":"✎  ")
                +annotationType(item)+" · v. "+verseRange(item.verse,item.endVerse);
            if(StudyAnnotations.UNDERLINE.equals(item.kind)&&!item.quote.isEmpty()){
                String excerpt=item.quote.replace('\n',' ');
                label+=" · "+(excerpt.length()>60?excerpt.substring(0,60)+"…":excerpt);
            }
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
        String[] starts=new String[numbers.size()];
        for(int i=0;i<starts.length;i++)starts[i]="Versículo "+numbers.get(i);
        new AlertDialog.Builder(this).setTitle("¿Dónde empieza la anotación?")
            .setItems(starts,(dialog,which)->{
                int startVerse=numbers.get(which);
                String[] ends=new String[numbers.size()-which];
                for(int i=0;i<ends.length;i++)
                    ends[i]=i==0?"Solo el versículo "+startVerse:
                        "Hasta el versículo "+numbers.get(which+i);
                new AlertDialog.Builder(this).setTitle("¿Dónde termina la anotación?")
                    .setItems(ends,(d,choice)->editStudyAnnotation(passage,
                        new AnnotationAnchors.Anchor(startVerse,
                            numbers.get(which+choice),0,""),null))
                    .setNegativeButton("Cancelar",null).show();
            })
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
        TextView ref=text(Books.name(item.book)+" "+item.chapter+":"+verseRange(item.verse,item.endVerse),13,ACCENT,true);
        body.addView(ref);
        if(!item.quote.isEmpty()){
            addGap(body,10);
            TextView q=text("«"+item.quote+"»",15,DARK,false);
            serif(q);q.setTextIsSelectable(true);body.addView(q);
        }
        addGap(body,12);
        String description=item.body.trim().isEmpty()?
            (StudyAnnotations.UNDERLINE.equals(item.kind)?"Fragmento subrayado. Puedes quitar este subrayado sin modificar el texto bíblico.":
                StudyAnnotations.ATLAS.equals(item.kind)?"Mapa bíblico de este libro":
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
                popup.dismiss();atlasReturnToStudy=true;openBookAtlas(item.book);
            },true);
        }
        if(!item.url.isEmpty()){
            addGap(body,8);
            button(body,StudyAnnotations.MAP.equals(item.kind)?"Abrir mapa":"Abrir página web",
                ()->openStudyUrl(item.url),true);
        }
        addGap(body,8);
        if(!StudyAnnotations.UNDERLINE.equals(item.kind)){
            button(body,"Editar anotación",()->{
                popup.dismiss();
                editStudyAnnotation(passage,new AnnotationAnchors.Anchor(item.verse,item.endVerse,item.offset,item.quote),item);
            },false);
        }
        button(body,StudyAnnotations.UNDERLINE.equals(item.kind)?"Quitar subrayado":"Eliminar anotación",()->{
            new AlertDialog.Builder(this)
                .setTitle(StudyAnnotations.UNDERLINE.equals(item.kind)?"Quitar subrayado":"Eliminar anotación")
                .setMessage(StudyAnnotations.UNDERLINE.equals(item.kind)
                    ?"Se quitará el subrayado de este fragmento. ¿Continuar?"
                    :"Esta nota se borrará del dispositivo. ¿Continuar?")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("Eliminar",(d,w)->{
                    studyAnnotations.delete(item.id);popup.dismiss();redrawAtVerse(item.verse);
                }).show();
        },false);
        styleAsBottomSheet(popup);
    }
    private void editStudyAnnotation(BibleStore.Passage passage,AnnotationAnchors.Anchor anchor,
                                     StudyAnnotations.Item editing){
        LinearLayout content=vertical();pad(content,17);
        content.addView(text(Books.name(passage.book)+" "+passage.chapter+":"+verseRange(anchor.verse,anchor.endVerse),
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
                        passage.book,passage.chapter,anchor.verse,anchor.endVerse,anchor.offset,
                        anchor.quote,kind,comment,link);
                    popup.dismiss();
                    Toast.makeText(this,"Anotación guardada",Toast.LENGTH_SHORT).show();
                    redrawAtVerse(anchor.verse);
                }catch(RuntimeException error){
                    showError("No se pudo guardar la anotación: "+error.getMessage());
                }
            }));
        styleAsBottomSheet(popup);
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
        styleAsBottomSheet(new AlertDialog.Builder(this).setTitle("Definir una palabra")
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
            }).create());
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
        styleAsBottomSheet(new AlertDialog.Builder(this).setTitle("Conversar sobre "+p.ref)
            .setView(edit).setNegativeButton("Cancelar",null)
            .setPositiveButton("Consultar",(d,w)->interpret(p,edit.getText().toString().trim(),true)).create());
    }
    private void interpret(BibleStore.Passage p,String question,boolean custom){
        if(!ai.configured()){showError("Para generar explicaciones configura tu clave Gemini en ⚙ Ajustes.");return;}
        String cacheKey="explain:"+ai.model()+":"+p.ref;
        if(!custom){
            String existing=store.getReflection(cacheKey);
            if(!existing.isEmpty()){showExplanation(p,existing,true);return;}
        }
        String detail=custom&&question.isEmpty()?"Profundiza en el significado":question;
        Runnable send=()->async("Consultando IA...",()->explainWithFallback(
            p.ref,p.text,"Traducción personal: Biblia de Navarra. Comentario de la edición: "
            +notes.get(p.book,p.chapter,p.first),detail).displayText(),out->{
            if(!custom)store.saveReflection(cacheKey,out);
            showExplanation(p,out,false);
        });
        // El consentimiento completo se pide una vez por sesión; el detalle
        // permanente queda documentado en Ajustes.
        if(aiSessionConsent){send.run();return;}
        LinearLayout consent=vertical();consent.setPadding(dp(22),dp(10),dp(22),dp(4));
        TextView message=text("Se enviará a Gemini el pasaje seleccionado, el fragmento disponible "
            +"del comentario de Navarra y tu pregunta, si la has escrito. "
            +(groq.fallbackEnabled() ?
                "Si Gemini falla, los mismos datos podrán enviarse automáticamente a Groq "
                +"según el consentimiento que activaste en Ajustes. " : "")
            +"No se envía el EPUB completo. Cada proveedor trata la consulta según sus "
            +"condiciones y nivel de servicio.",15,DARK,false);
        consent.addView(message);
        addGap(consent,10);
        android.widget.CheckBox remember=new android.widget.CheckBox(this);
        remember.setText("No volver a preguntar durante esta sesión");
        remember.setTextSize(14);remember.setTextColor(DARK);
        consent.addView(remember);
        new AlertDialog.Builder(this).setTitle("Consulta externa")
            .setView(consent)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Enviar",(dialog,w)->{
                if(remember.isChecked())aiSessionConsent=true;
                send.run();
            }).show();
    }
    private void showExplanation(BibleStore.Passage p,String text,boolean cache){
        TextView body=text(text,16,DARK,false);body.setTextIsSelectable(true);pad(body,18);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        styleAsBottomSheet(new AlertDialog.Builder(this)
            .setTitle("Reflexión · "+p.ref+(cache?" (guardada)":""))
            .setView(scroll).setPositiveButton("Cerrar",null)
            .setNeutralButton("Preguntar más",(d,w)->ask(p)).create());
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
        button(c,"Consultar referencias de este día",()->loadMass(true),true);
        button(c,"Abrir lecturas completas",()->openMassWebsite(Liturgy.link(massDay)),false);
        hint(column,"El texto litúrgico oficial pertenece a la CEE. La web consultada es un proyecto independiente, no un servicio oficial de la CEE. Las referencias pueden variar por celebraciones locales; compruébalas en caso de duda.");
        String previous=store.getReflection("mass:"+Liturgy.link(massDay));
        if(!previous.isEmpty()){
            try{presentMass(new JSONObject(previous));}catch(Exception ex){hint(column,"No se pudo leer la última consulta guardada");}
        }else if(!massAutoLoading){
            // Al abrir Misa, el día se consulta solo: la caché primero, la red después.
            massAutoLoading=true;
            loadMass(false);
        }
    }
    private void loadMass(boolean manual){
        Calendar date=(Calendar)massDay.clone();
        if(manual)Toast.makeText(this,"Consultando el calendario...",Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            try{
                Liturgy.Day m=Liturgy.download(date);
                JSONObject json=new JSONObject();json.put("title",m.title);json.put("date",m.date);json.put("url",m.url);
                JSONArray a=new JSONArray();for(Liturgy.Reading r:m.readings)a.put(new JSONObject().put("type",r.label).put("ref",r.reference));
                json.put("readings",a);
                String out=json.toString();
                runOnUiThread(()->{
                    massAutoLoading=false;
                    store.saveReflection("mass:"+Liturgy.link(date),out);
                    if(Liturgy.link(date).equals(Liturgy.link(massDay)))render();
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    // Sin reintentos automáticos en bucle: el usuario decide cuándo reintentar.
                    if(manual)massAutoLoading=false;
                    if(isFinishing()||isDestroyed())return;
                    if(manual)showError("No se pudo consultar el calendario: "+
                        (error.getMessage()==null?"inténtalo más tarde.":error.getMessage()));
                    else hint(column,"No se pudieron consultar hoy las lecturas automáticamente. Pulsa «Consultar referencias de este día» para reintentarlo.");
                });
            }
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
                item.setBackground(chosen?bg(NAV_BG,11):outlined(SOFT,11));
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
    /**
     * Cada estado de ánimo rota entre varios pasajes según el día: la tercera
     * visita ya no muestra siempre el mismo texto.
     */
    private String[] moodOptions(String mood){
        switch(mood){
            case "Preocupación":return new String[]{"Mateo 6:25-34","Filipenses 4:6-7","1 Pedro 5:6-7"};
            case "Tristeza":return new String[]{"Salmos 34:18-20","Salmos 42","Juan 16:20-22"};
            case "Gratitud":return new String[]{"Salmos 100","Salmos 103:1-5","1 Tesalonicenses 5:16-18"};
            case "Alegría":return new String[]{"Filipenses 4:4-9","Isaías 12","Juan 15:9-12"};
            case "Dudas":return new String[]{"Marcos 9:23-24","Juan 20:24-29","Mateo 14:28-31"};
            case "Miedo":return new String[]{"Isaías 41:10","Salmos 27:1-3","Deuteronomio 31:6"};
            case "Esperanza":return new String[]{"Romanos 8:31-39","Jeremías 29:11-14","Lamentaciones 3:22-26"};
            default:return new String[]{"Salmos 23","Mateo 11:28-30","Juan 14:27"};
        }
    }
    private String selection(String mood){
        String[] options=moodOptions(mood);
        int day=Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        return options[Math.floorMod(day,options.length)];
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
        if(epubImportInProgress){
            Toast.makeText(this,"Ya se está importando una Biblia.",Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/epub+zip","application/zip","application/octet-stream"});
        startActivityForResult(intent,PICK_EPUB);
    }
    /** Indicador persistente; el procesado se ejecuta siempre fuera del hilo UI. */
    private void showEpubImportDialog(){
        LinearLayout content=vertical();
        content.setPadding(dp(22),dp(8),dp(22),dp(12));
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar spinner=new ProgressBar(this);
        row.addView(spinner,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout labels=vertical();
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.leftMargin=dp(13);
        row.addView(labels,lp);
        epubImportStep=text("Paso 1 de 3 · Texto bíblico",16,DARK,true);
        labels.addView(epubImportStep);
        epubImportDetail=text("Abriendo el archivo EPUB…",14,MUTED,false);
        labels.addView(epubImportDetail);
        content.addView(row);
        addGap(content,14);
        hint(content,"El proceso continúa en segundo plano. Mantén Scriptura abierta; al terminar aparecerá el resultado.");
        epubImportDialog=new AlertDialog.Builder(this)
            .setTitle("Preparando tu Biblia")
            .setView(content).create();
        epubImportDialog.setCancelable(false);
        epubImportDialog.setCanceledOnTouchOutside(false);
        epubImportDialog.show();
    }
    private void setEpubImportStatus(String step,String detail){
        runOnUiThread(()->{
            if(!epubImportInProgress||isFinishing()||isDestroyed())return;
            if(epubImportStep!=null)epubImportStep.setText(step);
            if(epubImportDetail!=null)epubImportDetail.setText(detail);
        });
    }
    private void finishEpubImport(boolean success,String message){
        if(!epubImportInProgress)return;
        epubImportInProgress=false;
        ScripturaBackup.setImporting(false);
        if(success)ScripturaBackup.backupAsync(this,true);
        if(epubImportDialog!=null){
            epubImportDialog.dismiss();
            epubImportDialog=null;
        }
        epubImportStep=null;epubImportDetail=null;
        if(isFinishing()||isDestroyed())return;
        // Conserva la orientación hasta que se haya leído el resultado, para
        // que una rotación física no cierre prematuramente este diálogo.
        new AlertDialog.Builder(this)
            .setTitle(success?"Biblioteca preparada":"No se pudo importar la Biblia")
            .setMessage(message)
            .setPositiveButton("Entendido",(dialog,which)->{
                if(success){page=0;render();}
                setRequestedOrientation(epubPreviousOrientation);
            }).show();
    }
    private void beginEpubImport(Uri uri){
        if(epubImportInProgress){
            Toast.makeText(this,"Ya se está importando una Biblia.",Toast.LENGTH_SHORT).show();
            return;
        }
        epubImportInProgress=true;
        ScripturaBackup.setImporting(true);
        epubPreviousOrientation=getRequestedOrientation();
        // Evita que girar el teléfono destruya la actividad a mitad de una
        // transacción de SQLite. No cancela ni interrumpe la importación.
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        showEpubImportDialog();
        try{
            worker.execute(()->{
                try{
                    int verses=store.importEpub(uri,detail->
                        setEpubImportStatus("Paso 1 de 3 · Texto bíblico",detail));
                    setEpubImportStatus("Paso 2 de 3 · Comentarios",
                        "Extrayendo los comentarios de tu EPUB privado…");
                    int comments=0,intros=0;
                    StringBuilder warnings=new StringBuilder();
                    try{comments=notes.importFromPrivateEpub(detail->
                        setEpubImportStatus("Paso 2 de 3 · Comentarios",detail));}
                    catch(Exception error){
                        android.util.Log.w("Scriptura","Error al extraer comentarios: "+
                            error.getClass().getSimpleName());
                        warnings.append("\n\nNo se han podido actualizar los comentarios; la Biblia sí está importada.");
                    }
                    setEpubImportStatus("Paso 3 de 3 · Introducciones",
                        "Preparando las introducciones de los libros…");
                    try{intros=introductions.importPrivateEpub(detail->
                        setEpubImportStatus("Paso 3 de 3 · Introducciones",detail));}
                    catch(Exception error){
                        android.util.Log.w("Scriptura","Error al extraer introducciones: "+
                            error.getClass().getSimpleName());
                        warnings.append("\n\nNo se han podido actualizar las introducciones.");
                    }
                    String summary=verses+" versículos, "+comments+" comentarios y "+
                        intros+" introducciones extraídas del EPUB personal."+warnings;
                    runOnUiThread(()->finishEpubImport(true,summary));
                }catch(Exception error){
                    android.util.Log.e("Scriptura","Importación de EPUB fallida: "+
                        error.getClass().getSimpleName());
                    String reason=error.getMessage()==null||error.getMessage().trim().isEmpty()?
                        "Error al procesar el archivo EPUB.":error.getMessage();
                    runOnUiThread(()->finishEpubImport(false,reason+
                        "\n\nSi existía una biblioteca anterior, se ha conservado."));
                }
            });
        }catch(RejectedExecutionException error){
            finishEpubImport(false,
                "No se pudo iniciar la importación. Cierra y vuelve a abrir Scriptura.");
        }
    }
    /** Exporta todas las anotaciones a un archivo de texto legible elegido por el usuario. */
    private void exportAnnotations(){
        worker.execute(()->{
            try{
                java.util.List<StudyAnnotations.Item> items=studyAnnotations.all();
                if(items.isEmpty()){
                    runOnUiThread(()->showError("Todavía no hay subrayados ni notas que exportar."));
                    return;
                }
                String content=AnnotationExport.build(items);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    pendingExport=content;
                    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TITLE,"anotaciones-scriptura.txt");
                    try{startActivityForResult(intent,PICK_EXPORT);}
                    catch(ActivityNotFoundException error){
                        showError("Android no dispone de un selector de archivos compatible.");
                    }
                });
            }catch(Exception error){
                runOnUiThread(()->showError("No se pudieron preparar las anotaciones: "+
                    (error.getMessage()==null?error.toString():error.getMessage())));
            }
        });
    }
    private void writeAnnotationExport(Uri uri){
        if(pendingExport==null)return;
        try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){
            if(out==null)throw new java.io.IOException("Destino no disponible");
            out.write(pendingExport.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Toast.makeText(this,"Anotaciones exportadas",Toast.LENGTH_LONG).show();
        }catch(Exception error){
            showError("No se pudo escribir el archivo de anotaciones: "+
                (error.getMessage()==null?error.toString():error.getMessage()));
        }finally{
            pendingExport=null;
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_EXPORT){
            if(result==RESULT_OK&&data!=null&&data.getData()!=null)
                writeAnnotationExport(data.getData());
            else pendingExport=null;
            return;
        }
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==PICK_EPUB)beginEpubImport(data.getData());
        else if(request==ScripturaBackup.PICK_DESTINATION||request==PICK_EXISTING_BACKUP){
            try{
                ScripturaBackup.chooseDestination(this,data.getData(),data.getFlags(),
                    this::showBackupResult);
                Toast.makeText(this,"Destino elegido. Creando y verificando la copia…",Toast.LENGTH_SHORT).show();
            }catch(Exception error){showError("No se pudo configurar la copia: "+error.getMessage());}
        }else if(request==ScripturaBackup.PICK_RESTORE)confirmRestore(data.getData());
    }

    private void openIndex(){go(7);}
    private void openBookInfo(String book){indexBook=book;go(9);}
    private void openGallery(){galleryBook="";galleryOffset=0;go(8);}
    private void openGalleryFor(String book){galleryBook=book;galleryOffset=0;go(8);}
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
            toggle.setBackground(indexScope==i?bg(NAV_BG,10):outlined(SOFT,10));
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
                    item.setBackground(indexScope==j?bg(NAV_BG,10):outlined(SOFT,10));
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
        if(page!=6)backStack.push(page);
        page=6;atlasMode=mode;render();
    }
    private void openBookAtlas(String book){
        if(page!=6)backStack.push(page);
        atlasBook=book;atlasMode=2;page=6;render();
    }
    private void openJourney(BibleGeography.Journey route){
        if(page!=6)backStack.push(page);
        atlasJourney=route;atlasMode=3;page=6;render();
    }
    private void openPlace(BibleGeography.Place place){
        if(page!=6)backStack.push(page);
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
        BibleStore.Passage chapter=store.passage(Books.name(passage.book)+" "+passage.chapter);
        if(chapter==null){showError("No se pudo recuperar el capítulo completo de tu Biblia.");return;}
        if(chapterPlaces!=null)chapterPlaces.dismiss();
        chapterPlaces=new ChapterPlacesDialog(this,chapter,prompt->aiRouter.explain(
            new AiRouter.Request(chapter.ref,chapter.text,"",prompt,true),
            groq.fallbackEnabled(),ai.model(),GroqClient.MODEL),
            ai.configured(),groq.fallbackEnabled(),DARK,MUTED,BG);
        chapterPlaces.show();
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
        button(box,"Exportar subrayados y notas (texto)",this::exportAnnotations,false);
        addGap(box,17);
        section(box,"Copia de seguridad","Un único archivo en la ubicación que elijas. Se actualiza al abrir, solo si hay cambios.");
        long last=ScripturaBackup.lastSuccess(this);
        hint(box,last>0?"Última copia válida: "+java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(last)):
            ScripturaBackup.configured(this)?"Destino configurado, pero aún no hay una copia verificada.":
            "Sin copia todavía. Elige primero un archivo de destino.");
        if(!ScripturaBackup.lastError(this).isEmpty())
            hint(box,"Último error: "+ScripturaBackup.lastError(this));
        button(box,"Elegir ubicación del archivo",this::chooseBackupLocation,false);
        button(box,"Crear copia ahora",()->{
            if(!ScripturaBackup.configured(this)){chooseBackupLocation();return;}
            ScripturaBackup.backupAsync(this,true,this::showBackupResult);
            Toast.makeText(this,"Creando y verificando la copia…",Toast.LENGTH_SHORT).show();
        },false);
        button(box,"Restaurar desde un archivo",this::pickRestoreFile,false);
        hint(box,"La copia incluye tus bases de datos, anotaciones, Biblia importada y ajustes de lectura. Las claves privadas no se exportan.");
        addGap(box,17);
        section(box,"Inteligencia artificial",
            "Gemini es el proveedor principal. Groq se usa únicamente como respaldo autorizado.");
        hint(box,"Las claves se cifran mediante Android Keystore y nunca se suben a GitHub.");
        button(box,ai.configured()?"Cambiar clave Gemini":"Introducir clave Gemini",this::configureKey,true);
        button(box,"Cambiar modelo ("+ai.model()+")",this::configureModel,false);
        button(box,"Lugares del capítulo · gestionar IA",()->{
            boolean enabled=ChapterPlacesDialog.automatic(this);
            new AlertDialog.Builder(this).setTitle("IA para los lugares")
                .setMessage(enabled?"Las consultas automáticas están activadas. Solo se consulta al seleccionar los lugares de un capítulo que aún no tenga un análisis guardado.":
                    "Las consultas automáticas están desactivadas. Abre «Lugares del capítulo» en la Biblia y pulsa «Completar con IA» para autorizar el envío del capítulo.")
                .setPositiveButton(enabled?"Desactivar":"Entendido",(d,w)->{
                    if(enabled){ChapterPlacesDialog.disableAutomatic(this);Toast.makeText(this,"Consultas geográficas automáticas desactivadas",Toast.LENGTH_LONG).show();}
                }).setNegativeButton("Cerrar",null).show();
        },false);
        button(box,groq.configured()?"Cambiar clave Groq":"Introducir clave Groq",
            this::configureGroqKey,false);
        groqKeyButton=(TextView)box.getChildAt(box.getChildCount()-1);
        button(box,groq.fallbackEnabled()?"Desactivar respaldo automático Groq":
            "Autorizar y activar respaldo automático Groq",this::toggleGroqFallback,false);
        groqFallbackButton=(TextView)box.getChildAt(box.getChildCount()-1);
        groqFallbackStatus=text("",13,MUTED,false);
        box.addView(groqFallbackStatus);
        refreshGroqSettingsLabels();
        hint(box,"Groq: consulta opcional a api.groq.com. Puedes activar Zero Data "
            +"Retention en la consola de Groq; las cuotas gratuitas tienen límites.");
        addGap(box,17);
        section(box,"Lectura","Ajusta la presentación del texto bíblico.");
        button(box,"Aspecto: "+DisplayModes.label(displayMode),this::chooseDisplayMode,false);
        hint(box,"Normal: colores habituales. Lectura cómoda: papel cálido y contraste suave. "+
            "Ahorro de batería: negro y menos imágenes, especialmente útil en OLED. "+
            "El brillo sigue bajo el control del teléfono.");
        fontLabel=text("Tamaño de letra actual: "+textSize+" sp",13,MUTED,false);
        box.addView(fontLabel);
        button(box,"Aumentar tamaño de letra",()->adjustTextSize(2,null,null),false);
        button(box,"Disminuir tamaño de letra",()->adjustTextSize(-2,null,null),false);
        hint(box,"Consejo: también puedes ajustarlo desde el botón «Aa» junto al título del capítulo.");
        addGap(box,17);
        section(box,"Avisos","Notificaciones diarias opcionales.");
        button(box,"Configurar recordatorio diario",this::reminderSettings,false);
        addGap(box,17);
        addGap(box,17);
        section(box,"Actualizaciones","Recibir avisos de nuevas APK firmadas.");
        addGap(box,18);
        hint(box,"Comprobación al abrir y periódica cada 12 horas. Recibirás un aviso «Actualizar»; la descarga se realiza dentro de Scriptura y Android confirma la instalación.");
        hint(box,"Las actualizaciones públicas funcionan sin token. Solo necesitas una credencial de solo lectura si el repositorio vuelve a ser privado.");
        button(box,UpdateChecker.configured(this)?"Modificar token privado (opcional)":"Configurar token privado (opcional)",
            this::configureUpdateToken,false);
        button(box,"Comprobar actualizaciones ahora",this::checkUpdatesNow,false);
        String updateError=UpdateChecker.lastError(this);
        if(!updateError.isEmpty())hint(box,"Última comprobación: "+updateError);

        section(box,"Información","Scriptura · versión "+currentVersionName());
        hint(box,"Los comentarios de Navarra son editoriales. Las interpretaciones generadas por IA pueden contener errores. No se envían datos sin una consulta iniciada por ti.");
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        new AlertDialog.Builder(this).setTitle("Ajustes de Scriptura")
            .setView(scroll).setPositiveButton("Cerrar",null).show();
    }
    private void showBackupResult(ScripturaBackup.BackupResult result,String error){
        if(isFinishing()||isDestroyed())return;
        if(error!=null){
            showError("La copia no se ha podido completar: "+error+
                "\nLa copia nueva no se ha confirmado. Revisa el error y vuelve a intentarlo.");
        }else if(result==ScripturaBackup.BackupResult.SAVED){
            Toast.makeText(this,"Copia guardada y verificada correctamente",Toast.LENGTH_LONG).show();
        }else if(result==ScripturaBackup.BackupResult.IMPORT_IN_PROGRESS){
            showError("La copia se ha pospuesto porque hay una importación en curso. "+
                "Vuelve a crearla al finalizar la importación.");
        }else if(result==ScripturaBackup.BackupResult.DESTINATION_CHANGED){
            Toast.makeText(this,"Destino cambiado; se usará el nuevo archivo",Toast.LENGTH_SHORT).show();
        }else if(result==ScripturaBackup.BackupResult.UNCHANGED){
            Toast.makeText(this,"La copia ya está actualizada",Toast.LENGTH_SHORT).show();
        }
    }
    private void chooseBackupLocation(){
        // Android AlertDialog shows either a message or list items, not both.
        // Keep both actions visible so the user can create or reuse one backup file.
        new AlertDialog.Builder(this).setTitle("Destino de Scriptura.backup")
            .setItems(new String[]{"Crear archivo en una ubicación","Elegir un archivo ya existente"},(dialog,choice)->{
                Intent intent=new Intent(choice==0?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(choice==0?"application/zip":"*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                if(choice==0)intent.putExtra(Intent.EXTRA_TITLE,"Scriptura.backup");
                try{
                    startActivityForResult(intent,choice==0?ScripturaBackup.PICK_DESTINATION:PICK_EXISTING_BACKUP);
                }catch(ActivityNotFoundException error){
                    showError("Android no dispone de un selector de archivos compatible.");
                }
            }).setNegativeButton("Cancelar",null).show();
    }

    private void pickRestoreFile(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent,ScripturaBackup.PICK_RESTORE);
    }

    private void confirmRestore(Uri uri){
        if(epubImportInProgress||ScripturaBackup.isRunning()){
            showError("Espera a que termine la importación o copia en curso antes de restaurar.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Restaurar Scriptura")
            .setMessage("Se reemplazarán la Biblia, tus anotaciones, marcadores, historial y ajustes con los del archivo elegido. Esta operación no se puede deshacer con una única copia. ¿Continuar?")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Restaurar",(d,w)->{
                ScripturaBackup.setImporting(true); // Blocks automatic backups during restoration.
                int previousOrientation=getRequestedOrientation();
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
                ProgressDialog progress=new ProgressDialog(this);
                progress.setTitle("Restaurando Scriptura");
                progress.setMessage("Verificando la copia completa…");
                progress.setCancelable(false);progress.show();
                worker.execute(()->{
                    try{
                        ScripturaBackup.RestorePlan plan=ScripturaBackup.prepareRestore(this,uri);
                        runOnUiThread(()->{
                            progress.dismiss();
                            try{
                                closeStores();
                                ScripturaBackup.applyRestore(this,plan);
                                ScripturaBackup.setImporting(false);
                                setRequestedOrientation(previousOrientation);
                                if(getSharedPreferences("scriptura_reminders",MODE_PRIVATE).getBoolean("enabled",false))
                                    Reminders.enable(this);
                                else Reminders.disable(this);
                                Toast.makeText(this,"Copia restaurada correctamente",Toast.LENGTH_LONG).show();
                                recreate();
                            }catch(Exception error){
                                ScripturaBackup.setImporting(false);
                                setRequestedOrientation(previousOrientation);
                                showError("No se pudo completar la restauración: "+error.getMessage());
                                recreate(); // Reopens SQLite helpers after a rollback.
                            }
                        });
                    }catch(Exception error){
                        ScripturaBackup.setImporting(false);
                        runOnUiThread(()->{
                            progress.dismiss();
                            setRequestedOrientation(previousOrientation);
                            showError("La copia no se puede restaurar: "+error.getMessage());
                        });
                    }
                });
            }).show();
    }

    /** Token personal opcional y restringido al repo privado. Nunca se registra ni se copia. */
    private void chooseDisplayMode(){
        String[] choices={"Normal","Lectura cómoda · tonos cálidos",
            "Ahorro de batería · fondo negro OLED","Automático · seguir el sistema"};
        new AlertDialog.Builder(this)
            .setTitle("Aspecto y lectura")
            .setSingleChoiceItems(choices,displayMode,(dialog,which)->{
                dialog.dismiss();
                if(which==displayMode)return;
                getSharedPreferences("scriptura_display",MODE_PRIVATE).edit()
                    .putInt("display_mode",DisplayModes.normalize(which)).apply();
                // recreate() conserva el pasaje y su desplazamiento por onSaveInstanceState.
                recreate();
            })
            .setNegativeButton("Cancelar",null).show();
    }

    private void configureUpdateToken(){
        EditText edit=new EditText(this);
        edit.setHint("github_pat_...");
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout wrap=vertical();
        wrap.setPadding(dp(22),dp(10),dp(22),dp(0));
        wrap.addView(edit);
        TextView explanation=text("Ahora Scriptura es público y no necesita token para actualizarse. Solo si vuelve a ser privado, crea un Fine-grained token con permiso Contents: Read limitado a este repositorio. Se guardará cifrado en el móvil.",14,MUTED,false);
        wrap.addView(explanation);
        new AlertDialog.Builder(this).setTitle("Acceso privado opcional")
            .setView(wrap)
            .setNegativeButton("Cancelar",null)
            .setNeutralButton("Usar acceso público",(d,w)->{
                UpdateChecker.clearToken(this);
                Toast.makeText(this,"Acceso público: avisos activos sin token",Toast.LENGTH_SHORT).show();
                checkUpdatesNow();
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
        Toast.makeText(this,"Consultando versiones de Scriptura...",Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            UpdateChecker.Result result=UpdateChecker.check(getApplicationContext(),true);
            if(result.available())UpdateChecker.maybeNotify(getApplicationContext(),result.update);
            runOnUiThread(()->{
                if(isFinishing())return;
                if(result.available())promptUpdate(result.update,true);
                else new AlertDialog.Builder(this)
                    .setTitle("Actualizaciones de Scriptura")
                    .setMessage(result.info)
                    .setPositiveButton("Cerrar",(d,w)->{if(page==0)render();}).show();
            });
        });
    }

    private void promptUpdate(UpdateChecker.Update update,boolean manual){
        if(update==null||(!manual&&!UpdateChecker.shouldPrompt(this,update)))return;
        if(isFinishing()||isDestroyed())return;
        UpdateChecker.markPrompted(this,update);
        new AlertDialog.Builder(this)
            .setTitle("Nueva actualización de Scriptura")
            .setMessage("Versión "+update.tag+" disponible.\n\n"+
                "Pulsa «Actualizar» para descargarla y comprobar su firma. "+
                "Después Android te pedirá confirmar su instalación. "+
                "No necesitas abrir GitHub ni desinstalar Scriptura.")
            .setNegativeButton("Más tarde",null)
            .setPositiveButton("Actualizar",(d,w)->updateInstaller.start(update))
            .show();
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
    private void refreshGroqSettingsLabels(){
        if(groqKeyButton!=null)groqKeyButton.setText(
            groq.configured()?"Cambiar clave Groq":"Introducir clave Groq");
        if(groqFallbackButton!=null)groqFallbackButton.setText(groq.fallbackEnabled()?
            "Desactivar respaldo automático Groq":"Autorizar y activar respaldo automático Groq");
        if(groqFallbackStatus!=null)groqFallbackStatus.setText(groq.fallbackEnabled()?
            "Respaldo Groq activado: solo ante problemas de cuota, disponibilidad o "
            +"tiempo de espera de Gemini.":
            "Respaldo Groq desactivado. Sin consentimiento no se envía ningún pasaje a Groq.");
    }

    private void configureGroqKey(){
        EditText edit=new EditText(this);edit.setHint("Pega tu clave de GroqCloud");edit.setSingleLine();
        edit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("Clave API de Groq")
            .setMessage("Obtén o administra tu clave en console.groq.com/keys. "
                +"La clave se guarda cifrada en este móvil. Guardar una clave nueva "
                +"desactiva el respaldo hasta que vuelvas a autorizarlo.")
            .setView(edit).setNegativeButton("Cancelar",null)
            .setNeutralButton("Eliminar clave",(d,w)->{
                try{
                    groq.saveKey("");refreshGroqSettingsLabels();
                    Toast.makeText(this,"Clave Groq eliminada y respaldo desactivado",
                        Toast.LENGTH_LONG).show();
                }catch(Exception error){showError("No se pudo eliminar la clave Groq.");}
            })
            .setPositiveButton("Guardar",(d,w)->{
                try{
                    if(edit.getText().toString().trim().isEmpty()){
                        showError("Introduce una clave Groq válida o utiliza «Eliminar clave».");
                        return;
                    }
                    groq.saveKey(edit.getText().toString());refreshGroqSettingsLabels();
                    Toast.makeText(this,"Clave Groq cifrada. Activa el respaldo en Ajustes.",
                        Toast.LENGTH_LONG).show();
                }catch(Exception error){showError("No se pudo guardar la clave Groq.");}
            }).show();
    }

    private void toggleGroqFallback(){
        if(groq.fallbackEnabled()){
            groq.setFallbackEnabled(false);refreshGroqSettingsLabels();
            Toast.makeText(this,"Respaldo Groq desactivado",Toast.LENGTH_SHORT).show();
            return;
        }
        if(!groq.configured()){
            showError("Introduce primero tu clave Groq en Ajustes.");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Autorizar respaldo automático con Groq")
            .setMessage("Solo si Gemini falla por cuota, saturación o tiempo de espera, "
                +"Scriptura enviará automáticamente a Groq el mismo pasaje bíblico, "
                +"su referencia, el contexto (que puede incluir comentarios de Navarra) "
                +"y la pregunta escrita. No se enviarán el EPUB completo, tus anotaciones "
                +"privadas, tu historial ni ninguna clave.\n\n"
                +"Los datos se envían a un proveedor distinto de Google. Groq declara "
                +"que puede registrar temporalmente entradas y respuestas por fiabilidad "
                +"o abuso (hasta 30 días; ubicación en EE. UU.). Puedes activar "
                +"Zero Data Retention en su consola.\n\n"
                +"Podrás revocar esta autorización en Ajustes en cualquier momento. "
                +"¿Autorizas activar el respaldo automático?")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Autorizar y activar",(d,w)->{
                try{
                    groq.setFallbackEnabled(true);refreshGroqSettingsLabels();
                    Toast.makeText(this,"Respaldo automático Groq activado",
                        Toast.LENGTH_LONG).show();
                }catch(Exception error){showError("No se pudo activar el respaldo Groq.");}
            }).show();
    }

    private void reminderSettings(){
        String[] options={"Activar aviso diario a las 09:00","Desactivar avisos"};
        new AlertDialog.Builder(this).setTitle("Recordatorio de lectura")
            .setItems(options,(d,w)->{
                if(w==0){
                    Reminders.enable(this);
                    Toast.makeText(this,"Recordatorio diario configurado. Si Android lo pide, concede el permiso de notificaciones.",Toast.LENGTH_LONG).show();
                }
                else {Reminders.disable(this);Toast.makeText(this,"Recordatorio desactivado",Toast.LENGTH_SHORT).show();}
            }).show();
    }
    /**
     * Atrás respeta la pila de navegación: resultados de búsqueda vuelven al
     * formulario, el lector regresa a su origen y el atlas despliega sus niveles.
     */
    @Override public void onBackPressed(){
        if(page==1&&!searchResultsQuery.isEmpty()){
            searchResultsQuery="";
            render();
            return;
        }
        if(!backStack.isEmpty()){
            Integer target=backStack.pop();
            if(target!=null&&target>=0&&target<=10&&target!=page){
                if(target==1)searchResultsQuery="";
                page=target;
                render();
                return;
            }
        }
        if(page==5){
            int previous=readerOrigin;
            page=(previous>=0&&previous<=10&&previous!=5)?previous:1;
            searchResultsQuery="";
            render();
        }
        else if(page==9){page=7;render();}
        else if(page==10){page=1;render();}
        else if(page==7||page==8){page=0;searchResultsQuery="";render();}
        else if(page==6){
            if(atlasMode==3||atlasMode==4){atlasMode=0;render();}
            else if(atlasReturnToStudy&&selected!=null){
                atlasReturnToStudy=false;page=5;render();
            }
            else {page=0;searchResultsQuery="";render();}
        }
        else if(page!=0){page=0;searchResultsQuery="";render();}
        else super.onBackPressed();
    }
    private void closeStores(){
        store.close();notes.close();studyAnnotations.close();introductions.close();
        if(historyContext!=null)historyContext.close();
    }
    @Override protected void onDestroy(){
        if(speech!=null){speech.shutdown();speech=null;}
        if(chapterPlaces!=null)chapterPlaces.dismiss();
        if(epubImportDialog!=null){
            epubImportDialog.dismiss();
            epubImportDialog=null;
        }
        if(epubImportInProgress){
            // Si el sistema destruye la actividad, la tarea debe terminar antes
            // de cerrar SQLite; su transacción protege los datos previos.
            try{worker.execute(this::closeStores);}
            catch(RejectedExecutionException ignored){closeStores();}
            worker.shutdown();
        }else{
            worker.shutdownNow();
            closeStores();
        }
        super.onDestroy();
    }
}
