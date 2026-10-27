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
    private final int DARK=Color.rgb(37,60,52), GOLD=Color.rgb(174,122,49), MUTED=Color.rgb(95,110,104);
    private final int BG=Color.rgb(250,249,245), SURFACE=Color.WHITE, SOFT=Color.rgb(239,244,239);
    private BibleStore store;
    private GeminiClient ai;
    private LinearLayout column,frame,nav;
    private TextView caption;
    private int page=0;
    private BibleStore.Passage selected;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String mood="Tranquilidad";
    private Calendar massDay=Calendar.getInstance();
    private int textSize=18;

    private int dp(float px){return (int)(getResources().getDisplayMetrics().density*px+0.5f);}
    private GradientDrawable bg(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        t.setLineSpacing(dp(3),1.13f);
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
        pad(b,13);b.setBackground(bg(primary?DARK:SOFT,14));b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);box.addView(b,p);
    }
    private LinearLayout card(LinearLayout dest){
        LinearLayout l=vertical();pad(l,17);l.setBackground(bg(SURFACE,17));l.setElevation(dp(2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(13);
        dest.addView(l,lp);return l;
    }
    private void title(LinearLayout l,String s){
        TextView v=text(s,24,DARK,true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(14);l.addView(v,p);
    }
    private EditText input(LinearLayout container,String hint,boolean multiline){
        EditText e=new EditText(this);e.setTextColor(DARK);e.setTextSize(16);e.setHint(hint);
        e.setSingleLine(!multiline);e.setPadding(dp(12),dp(12),dp(12),dp(12));
        e.setBackground(bg(SOFT,11));
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
        store=new BibleStore(this);ai=new GeminiClient(this);
        textSize=getPreferences(0).getInt("font",18);
        if(state!=null){page=state.getInt("page",0);mood=state.getString("mood","Tranquilidad");}
        render();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        super.onSaveInstanceState(state);state.putInt("page",page);state.putString("mood",mood);
    }
    private void render(){
        frame=vertical();frame.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20),dp(18),dp(20),dp(12));
        TextView brand=text("✦  Scriptura",23,DARK,true);head.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView settings=text("⚙",25,DARK,false);pad(settings,8);settings.setOnClickListener(v->showSettings());head.addView(settings);
        frame.addView(head);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        column=vertical();column.setPadding(dp(18),dp(8),dp(18),dp(20));
        scroll.addView(column);frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setBackgroundColor(SURFACE);
        String[] names={"Inicio","Buscar","Misa","Para mí","Guardados"};
        for(int i=0;i<names.length;i++){
            final int dest=i;TextView item=text(names[i],12,i==page?DARK:MUTED,i==page);
            item.setGravity(Gravity.CENTER);pad(item,10);
            nav.addView(item,new LinearLayout.LayoutParams(0,dp(54),1));
            item.setOnClickListener(v->{page=dest;render();});
        }
        frame.addView(nav);
        setContentView(frame);
        switch(page){case 0:home();break;case 1:search();break;case 2:mass();break;case 3:personal();break;case 4:saved();break;case 5:passagePage();break;default:home();}
    }
    private void home(){
        TextView k=text("LECTURA Y REFLEXIÓN",12,GOLD,true);column.addView(k);
        title(column,"Una palabra para hoy");
        if(!store.ready()){
            LinearLayout c=card(column);c.addView(text("Prepara tu biblioteca",20,DARK,true));
            hint(c,"Importa tu ejemplar EPUB de la Biblia de Navarra. Su contenido permanecerá únicamente en este dispositivo.");
            button(c,"Importar mi Biblia",this::pickEpub,true);
            hint(c,"También puedes consultar las lecturas de misa por internet sin importar el EPUB.");
        } else {
            hint(column,"Biblia de Navarra · "+store.count()+" versículos importados");
            long seed=(System.currentTimeMillis()/86_400_000L);
            readingPreview("Antiguo Testamento",store.random(false,seed*911+31));
            readingPreview("Nuevo Testamento",store.random(true,seed*911+53));
        }
        LinearLayout c=card(column);
        c.addView(text("⛪  Lecturas de la misa",20,DARK,true));
        hint(c,Liturgy.label(Calendar.getInstance()));
        button(c,"Leer las lecturas de hoy",()->{page=2;massDay=Calendar.getInstance();render();},true);
        c=card(column);c.addView(text("¿Cómo te encuentras hoy?",20,DARK,true));
        hint(c,"Busca una lectura para lo que estás viviendo y reflexiona sobre ella.");
        button(c,"Explorar según mi estado de ánimo",()->{page=3;render();},false);
        c=card(column);c.addView(text("Buscar en los 73 libros",20,DARK,true));
        button(c,"Ir al buscador",()->{page=1;render();},false);
    }
    private void readingPreview(String label,BibleStore.Passage p){
        LinearLayout c=card(column);c.addView(text(label.toUpperCase(),12,GOLD,true));
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
        TextView edition=text("BIBLIA DE LA UNIVERSIDAD DE NAVARRA",11,GOLD,true);column.addView(edition);
        title(column,p.ref);
        LinearLayout c=card(column);TextView bible=text(p.text,textSize,DARK,false);
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
        button(c,"✦  Interpretación con Gemini",()->interpret(p,"Explica este pasaje de forma breve y fiel a su contexto.",false),true);
        button(c,"Profundizar o preguntar",()->ask(p),false);
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
            .setMessage("Se enviará a la API de Gemini el pasaje seleccionado y tu pregunta, si la has escrito. No se envía el EPUB completo. En el nivel gratuito Google puede utilizar estas consultas para mejorar sus productos. ¿Continuar?")
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Enviar",(dialog,w)->async("Consultando Gemini...",()->ai.explain(p.ref,p.text,"Traducción personal: Biblia de Navarra",detail),out->{
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
            LinearLayout box=card(column);box.addView(text(type,13,GOLD,true));
            box.addView(text(ref,17,DARK,true));
            String local=Liturgy.toLocalQuery(ref);
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
        for(String option:moods){
            button(c,(option.equals(mood)?"● ":"○ ")+option,()->{mood=option;render();},false);
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
        column.addView(text("★ Favoritos",18,GOLD,true));addGap(column,8);
        for(String ref:store.saved(true))button(column,ref,()->lookup(ref),false);
        addGap(column,22);
        column.addView(text("Historial",18,DARK,true));addGap(column,8);
        for(String ref:store.saved(false))button(column,ref,()->lookup(ref),false);
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
                int n=store.importEpub(uri);return n+" versículos importados desde el EPUB personal";
            },msg->{new AlertDialog.Builder(this).setTitle("Biblioteca preparada")
                .setMessage(msg).setPositiveButton("Continuar",(d,w)->{page=0;render();}).show();});
        }
    }
    private void showSettings(){
        LinearLayout box=vertical();box.setPadding(dp(20),dp(12),dp(20),dp(4));
        box.addView(text("Cuenta Gemini API",17,DARK,true));
        hint(box,"La clave se cifra con Android Keystore y queda en el móvil. No se sube a GitHub.");
        button(box,ai.configured()?"Cambiar clave Gemini":"Introducir clave Gemini",this::configureKey,true);
        button(box,"Cambiar modelo ("+ai.model()+")",this::configureModel,false);
        button(box,"Importar o reemplazar Biblia EPUB",this::pickEpub,false);
        button(box,"Aumentar tamaño de letra",()->{textSize=Math.min(30,textSize+2);getPreferences(0).edit().putInt("font",textSize).apply();render();},false);
        button(box,"Disminuir tamaño de letra",()->{textSize=Math.max(14,textSize-2);getPreferences(0).edit().putInt("font",textSize).apply();render();},false);
        button(box,"Configurar recordatorio diario",this::reminderSettings,false);
        hint(box,"Versión preliminar 0.2.0 · El texto de Navarra es privado. No se comparten notas personales automáticamente.");
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
    @Override protected void onDestroy(){worker.shutdownNow();store.close();super.onDestroy();}
}
