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
        store=new BibleStore(this);notes=new NavarraNotes(this);ai=new GeminiClient(this);
        textSize=getPreferences(0).getInt("font",18);
        if(state!=null){page=state.getInt("page",0);mood=state.getString("mood","Tranquilidad");
            atlasMode=state.getInt("atlasMode",0);atlasBook=state.getString("atlasBook","Gn");
            atlasPlace=BibleGeography.place(state.getString("atlasPlace",""));
            atlasJourney=BibleGeography.journey(state.getString("atlasJourney",""));
        }
        render();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putInt("page",page);state.putString("mood",mood);
        state.putInt("atlasMode",atlasMode);state.putString("atlasBook",atlasBook);
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
        settings.setOnClickListener(v->showSettings());
        head.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));
        frame.addView(head);
        View hairline=new View(this);hairline.setBackgroundColor(OUTLINE);
        frame.addView(hairline,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        column=vertical();column.setPadding(dp(16),dp(17),dp(16),dp(24));
        scroll.addView(column);frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        // Cinco pestañas reales. El lector y el atlas son destinos internos.
        nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setBackgroundColor(DARK);
        String[] names={"Inicio","Buscar","Misa","Para mí","Guardados"};
        String[] symbols={"⌂","⌕","✝","♡","★"};
        int active=page==5?1:(page==6?0:page);
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
            default:page=0;home();
        }
    }

    private void home(){
        LinearLayout hero=vertical();hero.setBackground(bg(DARK,17));pad(hero,20);
        LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,-2);
        heroLp.bottomMargin=dp(16);column.addView(hero,heroLp);
        TextView k=text("LEE · EXPLORA · COMPRENDE",11,GOLD,true);
        k.setLetterSpacing(0.08f);hero.addView(k);addGap(hero,9);
        TextView title=text("Una palabra para hoy",27,Color.WHITE,false);
        serif(title);hero.addView(title);addGap(hero,7);
        hero.addView(text("Descubre las Escrituras y reflexiona desde la tradición católica.",14,Color.rgb(230,236,242),false));
        if(!store.ready()){
            LinearLayout c=card(column);c.addView(text("Prepara tu biblioteca",20,DARK,true));
            hint(c,"Importa tu ejemplar EPUB de la Biblia de Navarra. Su contenido permanecerá únicamente en este dispositivo.");
            button(c,"Importar mi Biblia",this::pickEpub,true);
            hint(c,"También puedes consultar las lecturas de misa por internet sin importar el EPUB.");
        } else {
            hint(column,"Biblia de Navarra · "+store.count()+" versículos importados");
            Calendar today=Calendar.getInstance();
            long seed=today.get(Calendar.YEAR)*1000L+today.get(Calendar.DAY_OF_YEAR);
            readingPreview("Antiguo Testamento",store.random(false,seed*911+31));
            readingPreview("Nuevo Testamento",store.random(true,seed*911+53));
        }
        LinearLayout c=card(column);
        section(c,"Lecturas de la misa","Referencias bíblicas del calendario litúrgico.");
        hint(c,Liturgy.label(Calendar.getInstance()));
        button(c,"Leer las lecturas de hoy",()->{page=2;massDay=Calendar.getInstance();render();},true);
        c=card(column);section(c,"¿Cómo te encuentras hoy?","Lecturas para reflexionar sobre lo que vives.");
        hint(c,"Busca una lectura para lo que estás viviendo y reflexiona sobre ella.");
        button(c,"Explorar según mi estado de ánimo",()->{page=3;render();},false);
        c=card(column);section(c,"Buscar en los 73 libros","Consulta referencias, capítulos o palabras.");
        button(c,"Ir al buscador",()->{page=1;render();},false);
        c=card(column);section(c,"Lugares bíblicos","Atlas preliminar, con ubicaciones orientativas.");
        hint(c,"Atlas de ciudades, regiones y recorridos de personajes de la Biblia. Ubicaciones con niveles de certeza.");
        button(c,"Explorar atlas bíblico",()->openAtlas(0),false);
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
        if(!store.ready()){hint(column,"Importa tu Biblia primero desde Ajustes.");return;}
        LinearLayout c=card(column);
        EditText term=input(c,"Ej. Juan 3:16-18 · Salmo 23 · Génesis",false);
        term.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        term.setOnEditorActionListener((v,id,event)->{lookup(term.getText().toString());return true;});
        button(c,"Buscar referencia o palabra",()->lookup(term.getText().toString()),true);
        button(c,"Explorar los 73 libros",this::browseBooks,false);
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
    private void open(BibleStore.Passage p){selected=p;store.record(p.ref);page=5;render();}
    private void passagePage(){
        if(selected==null){page=1;render();return;}
        BibleStore.Passage p=selected;
        TextView edition=text("TEXTO BÍBLICO · UNIVERSIDAD DE NAVARRA",11,ACCENT,true);column.addView(edition);
        title(column,p.ref);
        LinearLayout c=card(column);c.setBackground(outlined(BG,16));
        TextView bible=text(p.text,textSize,DARK,false);serif(bible);
        bible.setTextIsSelectable(true);c.addView(bible);
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
        button(c,"Comentarios de la Biblia de Navarra",()->showNavarraNote(p),false);
        button(c,"✦  Interpretación con Gemini",()->interpret(p,"Explica este pasaje de forma breve y fiel a su contexto.",false),true);
        button(c,"Profundizar o preguntar",()->ask(p),false);
        button(c,"⌖  Lugares de este pasaje",()->showPassagePlaces(p),false);
        button(c,"Atlas del libro "+Books.name(p.book),()->openBookAtlas(p.book),false);
        LinearLayout link=card(column);
        link.addView(text("Lectura del contexto",18,DARK,true));
        button(link,"Capítulo anterior",()->openReference(p.book,p.chapter-1),false);
        button(link,"Capítulo siguiente",()->openReference(p.book,p.chapter+1),false);
        hint(link,"La explicación de Gemini es orientativa y nunca sustituye al texto bíblico.");
    }
    private void openReference(String book,int chapter){
        if(chapter<1||chapter>store.chapters(book)){showError("No hay más capítulos en ese sentido.");return;}
        BibleStore.Passage p=store.passage(Books.name(book)+" "+chapter);
        if(p!=null)open(p);
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
        }else hint(c,"Importa tu Biblia para recibir las lecturas personalizadas.");
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
        if(!store.ready()){hint(column,"Importa tu Biblia desde Ajustes.");return;}
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
                int comments=notes.importFromPrivateEpub();
                return n+" versículos y "+comments+" comentarios importados desde tu EPUB personal";
            },msg->{new AlertDialog.Builder(this).setTitle("Biblioteca preparada")
                .setMessage(msg).setPositiveButton("Continuar",(d,w)->{page=0;render();}).show();});
        }
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
        hint(column,"Atlas preliminar basado en puntos geográficos orientativos. No representa mapas históricos a escala ni caminos reconstruidos.");
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
        hint(column,BibleGeography.placeCount()+" ubicaciones catalogadas. El índice inicial está en ampliación.");
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
            hint(box,p.modern+" · "+p.certainty);
            button(box,"Ver ficha y mapa",()->openPlace(p),false);
        }
    }
    private void atlasJourneys(){
        hint(column,"Los puntos están ordenados según episodios seleccionados, pero las líneas que los unen NO indican caminos antiguos conocidos.");
        for(BibleGeography.Journey j:BibleGeography.allJourneys()){
            LinearLayout box=card(column);
            box.addView(text(j.title,18,DARK,true));
            hint(box,j.person+" · "+j.stops.length+" etapas · "+j.refs);
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
            hint(column,"Aún no hay lugares verificados/catalogados para este libro. El libro sigue disponible en la Biblioteca; el atlas se ampliará sin inventar ubicaciones.");
        } else {
            hint(column,places.size()+" lugares relacionados con "+Books.name(atlasBook)+" (selección inicial).");
            if(places.size()>1){
                LinearLayout mapCard=card(column);
                mapCard.addView(text("Esquema de ubicaciones",17,DARK,true));
                GeoSketch sketch=new GeoSketch(this,places,false,this::openPlace);
                mapCard.addView(sketch);
                hint(mapCard,"Puntos aproximados. No representa fronteras, relieve ni rutas.");
            }
            atlasPlaceCards(places);
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
        hint(column,"Referencia bíblica: "+route.refs);
        java.util.List<BibleGeography.Place> stops=BibleGeography.stops(route);
        if(stops.size()>1){
            LinearLayout map=card(column);map.addView(text("Etapas esquemáticas",17,DARK,true));
            map.addView(new GeoSketch(this,stops,true,this::openPlace));
            hint(map,"Línea de puntos = secuencia narrativa. NO reconstruye el recorrido real ni ofrece distancias o tiempos.");
        }
        for(int i=0;i<stops.size();i++){
            BibleGeography.Place p=stops.get(i);
            LinearLayout card=card(column);
            card.addView(text((i+1)+". "+p.name,17,DARK,true));
            hint(card,p.modern+" · "+p.certainty);
            button(card,"Ver ficha del lugar",()->openPlace(p),false);
        }
    }
    private void placeScreen(BibleGeography.Place p){
        button(column,"Volver a lugares",()->openAtlas(0),false);
        title(column,p.name);
        LinearLayout box=card(column);
        box.addView(text("Localización actual propuesta",15,ACCENT,true));
        box.addView(text(p.modern,17,DARK,true));
        hint(box,"Precisión: "+p.certainty);
        hint(box,p.description);
        hint(box,"Pasajes para consultar: "+p.refs);
        box.addView(new GeoSketch(this,java.util.Collections.singletonList(p),false,place->openExternalMap(place)));
        button(box,"Ver posición en OpenStreetMap",()->openExternalMap(p),true);
        hint(box,"El mapa de OpenStreetMap muestra geografía actual, no reconstrucción histórica. Requiere internet.");
        if(store.ready()){
            String[] tokens=p.refs.split(";");
            if(tokens.length>0){
                String query=tokens[0].trim();
                BibleStore.Passage passage=store.passage(query);
                if(passage!=null)button(box,"Leer "+passage.ref,()->open(passage),false);
            }
        }
    }
    private void openExternalMap(BibleGeography.Place p){
        String lat=String.format(java.util.Locale.US,"%.6f",p.lat);
        String lon=String.format(java.util.Locale.US,"%.6f",p.lon);
        String url="https://www.openstreetmap.org/?mlat="+lat+"&mlon="+lon+"#map=11/"+lat+"/"+lon;
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){showError("No se pudo abrir el mapa: "+e.getMessage());}
    }
    private void showPassagePlaces(BibleStore.Passage passage){
        java.util.List<BibleGeography.Place> hits=BibleGeography.mentionedInPassage(passage.book,passage.text);
        if(hits.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Lugares del pasaje")
                .setMessage("El atlas preliminar todavía no ha identificado un lugar catalogado en este fragmento. Puedes consultar el atlas general del libro.")
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

    private void showSettings(){
        LinearLayout box=vertical();box.setPadding(dp(18),dp(12),dp(18),dp(12));
        section(box,"Tu Biblia","El ejemplar personal se guarda únicamente en este móvil.");
        button(box,"Importar o reemplazar Biblia EPUB",this::pickEpub,false);
        addGap(box,17);
        section(box,"Inteligencia artificial","Solo se consulta Gemini con tu autorización previa.");
        hint(box,"La clave se cifra mediante Android Keystore y no se sube a GitHub.");
        button(box,ai.configured()?"Cambiar clave Gemini":"Introducir clave Gemini",this::configureKey,true);
        button(box,"Cambiar modelo ("+ai.model()+")",this::configureModel,false);
        addGap(box,17);
        section(box,"Lectura","Ajusta la presentación del texto bíblico.");
        button(box,"Aumentar tamaño de letra",()->{textSize=Math.min(30,textSize+2);getPreferences(0).edit().putInt("font",textSize).apply();render();},false);
        button(box,"Disminuir tamaño de letra",()->{textSize=Math.max(14,textSize-2);getPreferences(0).edit().putInt("font",textSize).apply();render();},false);
        addGap(box,17);
        section(box,"Avisos","Notificaciones diarias opcionales.");
        button(box,"Configurar recordatorio diario",this::reminderSettings,false);
        addGap(box,17);
        section(box,"Información","Versión preliminar 0.6.0");
        hint(box,"Los comentarios de Navarra son editoriales. Las respuestas de Gemini son interpretaciones generadas con IA. No se transmiten datos personales automáticamente.");
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        new AlertDialog.Builder(this).setTitle("Ajustes de Scriptura")
            .setView(scroll).setPositiveButton("Cerrar",null).show();
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
        if(page==5){page=1;render();}
        else if(page==6){
            if(atlasMode==3||atlasMode==4){atlasMode=0;render();}
            else {page=0;render();}
        }
        else if(page!=0){page=0;render();}
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){worker.shutdownNow();store.close();notes.close();super.onDestroy();}
}
