package com.scriptura.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Menú de selección nativo + explicación navegable. Utilizado también en pruebas Android. */
public final class DefinitionUi {
    private static final int DEFINE_ACTION_ID=0x5C120;
    private static final int NAVY=Color.rgb(13,35,56);
    private static final int GOLD=Color.rgb(149,100,44);
    private static final int MUTED=Color.rgb(71,89,105);
    private DefinitionUi(){}

    public interface Delegate {
        void openExternal(String url);
        void explainWithGemini(String word);
        void showError(String message);
    }
    public interface SelectionHandler {
        void onAnnotate(String quote,int start,int end);
    }
    private static final int ANNOTATE_ACTION_ID=0x5C121;
    public static void attach(Activity activity,TextView textView,boolean geminiEnabled,Delegate delegate){
        attach(activity,textView,geminiEnabled,delegate,null);
    }
    public static void attach(Activity activity,TextView textView,boolean geminiEnabled,
                              Delegate delegate,SelectionHandler selectionHandler){
        // No reemplazar ni consumir las acciones nativas Copiar/Compartir/Seleccionar todo.
        textView.setTextIsSelectable(true);
        // Algunos TextView no se marcan como long-clickable por el motor de
        // selección; sin este ajuste la pulsación nunca lanza ActionMode.
        textView.setLongClickable(true);
        textView.setCustomSelectionActionModeCallback(new ActionMode.Callback(){
            private void ensureDefine(Menu menu){
                if(menu.findItem(DEFINE_ACTION_ID)==null){
                    MenuItem define=menu.add(Menu.NONE,DEFINE_ACTION_ID,Menu.CATEGORY_SECONDARY,"Definir");
                    define.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                }
            }
            @Override public boolean onCreateActionMode(ActionMode mode,Menu menu){
                ensureDefine(menu);
                if(selectionHandler!=null&&menu.findItem(ANNOTATE_ACTION_ID)==null){
                    MenuItem annotate=menu.add(Menu.NONE,ANNOTATE_ACTION_ID,Menu.CATEGORY_SECONDARY,"Anotar");
                    annotate.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                }
                return true;
            }
            @Override public boolean onPrepareActionMode(ActionMode mode,Menu menu){
                ensureDefine(menu);
                return true;
            }
            @Override public boolean onActionItemClicked(ActionMode mode,MenuItem item){
                if(item.getItemId()==ANNOTATE_ACTION_ID && selectionHandler!=null){
                    final int start=textView.getSelectionStart(),end=textView.getSelectionEnd();
                    if(start<0||end<=start)return true;
                    String selected=textView.getText().subSequence(start,end).toString();
                    mode.finish();
                    selectionHandler.onAnnotate(selected,start,end);
                    return true;
                }
                if(item.getItemId()!=DEFINE_ACTION_ID)return false;
                final String selected=WordMeaning.selection(textView.getText().toString(),
                    textView.getSelectionStart(),textView.getSelectionEnd());
                if(selected==null){
                    delegate.showError("Selecciona una sola palabra para consultar su significado.");
                    return true;
                }
                mode.finish();
                show(activity,selected,geminiEnabled,delegate);
                return true;
            }
            @Override public void onDestroyActionMode(ActionMode mode){}
        });
    }

    public static void show(Activity activity,String word,boolean geminiEnabled,Delegate delegate){
        if(!WordMeaning.oneWord(word)){
            delegate.showError("La consulta debe contener una sola palabra.");
            return;
        }
        int pad=dp(activity,16);
        int maxHeight=(int)(activity.getResources().getDisplayMetrics().heightPixels*.68f);
        LinearLayout content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad,pad,pad,pad);
        content.setBackgroundColor(Color.WHITE);
        addText(activity,content,word,22,NAVY,true,0);
        String local=WordMeaning.local(word);
        addText(activity,content,local!=null?local:
            "Este término no figura todavía en el glosario local. Puedes consultar las fuentes externas que aparecen abajo.",
            15,NAVY,false,9);
        addText(activity,content,local!=null?
            "Glosario orientativo de Scriptura. El sentido varía según el pasaje.":
            "Las consultas externas solo envían la palabra elegida; requieren conexión.",
            12,MUTED,false,8);
        final ScrollView scroll=new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(activity)
            .setTitle("Definición y fuentes")
            .setView(scroll)
            .setNegativeButton("Cerrar",(d,w)->d.dismiss())
            .create();
        String catholic=WordMeaning.catholicArticleUrl(word);
        if(catholic!=null){
            addLink(activity,content,"Enciclopedia Católica · Abrir artículo",catholic,dialog,delegate);
        } else {
            addText(activity,content,"Enciclopedia Católica: no hay un artículo verificado para este término.",12,MUTED,false,10);
        }
        addLink(activity,content,"Wikipedia · Buscar «"+word+"»",
            WordMeaning.wikipediaSearchUrl(word),dialog,delegate);
        addLink(activity,content,"Wikcionario · Buscar «"+word+"»",
            WordMeaning.wiktionarySearchUrl(word),dialog,delegate);
        if(geminiEnabled){
            TextView ai=addText(activity,content,"✦ Explicar esta palabra en su pasaje con Gemini",14,GOLD,true,12);
            ai.setMinHeight(dp(activity,48));
            ai.setGravity(Gravity.CENTER_VERTICAL);
            ai.setOnClickListener(v->{dialog.dismiss();delegate.explainWithGemini(word);});
        }
        // Medida segura: nunca acceder a LayoutParams sin comprobar que existe.
        // El contenido es desplazable incluso cuando la ventana se limita a
        // una fracción de la altura del teléfono.
        dialog.setOnShowListener(ignored->{
            try{
                int width=(int)(activity.getResources().getDisplayMetrics().widthPixels*.91f);
                android.view.ViewGroup.LayoutParams lp=scroll.getLayoutParams();
                if(lp!=null){
                    lp.height=Math.min(maxHeight,
                        Math.max(dp(activity,240),content.getMeasuredHeight()));
                    scroll.setLayoutParams(lp);
                }
                if(dialog.getWindow()!=null)dialog.getWindow().setLayout(width,-2);
            }catch(RuntimeException error){
                android.util.Log.e("ScripturaDefinir","No se pudo ajustar el tamaño del globo",error);
            }
        });
        try{dialog.show();}
        catch(RuntimeException error){
            android.util.Log.e("ScripturaDefinir","No se pudo mostrar la definición",error);
            delegate.showError("No se pudo abrir el globo de la definición.");
        }
    }
    private static TextView addText(Activity activity,LinearLayout dest,String text,int sp,
                                    int color,boolean bold,int space){
        TextView label=new TextView(activity);
        label.setText(text);label.setTextSize(sp);label.setTextColor(color);
        if(bold)label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(activity,space);
        dest.addView(label,lp);
        return label;
    }
    private static void addLink(Activity activity,LinearLayout content,String label,String uri,
                                AlertDialog dialog,Delegate delegate){
        TextView link=addText(activity,content,label,15,GOLD,true,12);
        link.setMinHeight(dp(activity,48));
        link.setGravity(Gravity.CENTER_VERTICAL);
        link.setContentDescription(label);
        link.setOnClickListener(v->{
            dialog.dismiss();
            delegate.openExternal(uri);
        });
    }
    private static int dp(Activity a,float n){
        return (int)(a.getResources().getDisplayMetrics().density*n+0.5f);
    }
}
