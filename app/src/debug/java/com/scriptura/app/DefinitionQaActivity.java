package com.scriptura.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Únicamente en debug. Prueba de selección sin EPUB protegido. No se distribuye en la APK release. */
public final class DefinitionQaActivity extends Activity {
    private TextView destination;
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(255,252,246));
        root.setPadding(dp(20),dp(45),dp(20),dp(10));
        TextView heading=new TextView(this);heading.setText("LECTOR DE PRUEBA · SOLO DEBUG");
        heading.setTextSize(14);root.addView(heading);
        TextView word=new TextView(this);
        word.setText("Fariseos");
        word.setTextSize(30);word.setTextColor(Color.rgb(13,35,56));
        word.setPadding(dp(8),dp(32),dp(8),dp(32));
        word.setGravity(Gravity.CENTER);
        root.addView(word,new LinearLayout.LayoutParams(-1,dp(160)));
        destination=new TextView(this);
        destination.setText("DESTINO: SIN ABRIR");
        destination.setTextSize(12);
        destination.setContentDescription("DESTINO: SIN ABRIR");
        root.addView(destination);
        setContentView(root);
        android.content.ClipboardManager clipboard=
            (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboard.addPrimaryClipChangedListener(()->{
            android.content.ClipData clip=clipboard.getPrimaryClip();
            if(clip!=null&&clip.getItemCount()>0&&clip.getItemAt(0).getText()!=null){
                String copied=clip.getItemAt(0).getText().toString();
                String result="COPIADO: "+copied;
                destination.setText(result);
                destination.setContentDescription(result);
            }
        });
        DefinitionUi.attach(this,word,false,new DefinitionUi.Delegate(){
            @Override public void openExternal(String url){
                // La misma acción que en MainActivity; interceptada para verificar
                // el Intent exacto sin depender de un navegador en el emulador.
                Intent intent=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url));
                DefinitionQaActivity.this.startActivity(intent);
            }
            @Override public void explainWithGemini(String word){}
            @Override public void showError(String message){
                destination.setText("ERROR: "+message);
            }
        });
    }
    @Override public void startActivity(Intent intent){
        if(intent.getAction()!=null&&intent.getAction().equals(Intent.ACTION_VIEW)
                &&intent.getData()!=null){
            String value="DESTINO: "+intent.getDataString();
            destination.setText(value);
            destination.setContentDescription(value);
            return;
        }
        super.startActivity(intent);
    }
}
