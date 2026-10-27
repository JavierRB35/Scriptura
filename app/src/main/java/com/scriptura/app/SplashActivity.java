package com.scriptura.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Presentación oficial de Scriptura. No bloquea la apertura por depender de GitHub:
 * la consulta se inicia durante el splash y puede terminar tras entrar en Inicio.
 */
public final class SplashActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13,35,56));
        getWindow().setNavigationBarColor(Color.rgb(13,35,56));

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(27),dp(26),dp(27),dp(32));
        GradientDrawable backdrop=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(7,25,42),Color.rgb(13,35,56),Color.rgb(29,57,75)});
        root.setBackground(backdrop);

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.ic_scriptura_foreground);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Scriptura, libro abierto iluminado");
        root.addView(logo,new LinearLayout.LayoutParams(dp(230),dp(230)));

        TextView brand=new TextView(this);
        brand.setText("Scriptura");
        brand.setTextSize(40);brand.setTextColor(Color.rgb(249,229,187));
        brand.setGravity(Gravity.CENTER);
        brand.setTypeface(Typeface.create("serif",Typeface.NORMAL));
        root.addView(brand,new LinearLayout.LayoutParams(-1,-2));

        TextView motto=new TextView(this);
        motto.setText("LEE · EXPLORA · COMPRENDE");
        motto.setLetterSpacing(.14f);
        motto.setTextColor(Color.rgb(218,168,92));
        motto.setTextSize(11);
        motto.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mottoLp=new LinearLayout.LayoutParams(-1,-2);
        mottoLp.topMargin=dp(10);root.addView(motto,mottoLp);

        TextView check=new TextView(this);
        check.setText(UpdateChecker.configured(this)
            ?"Consultando nuevas versiones sin detener tu lectura…"
            :"Tu Biblia, mapas y arte · Sin registro obligatorio");
        check.setTextColor(Color.rgb(220,225,232));
        check.setTextSize(12);check.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams checkLp=new LinearLayout.LayoutParams(-1,-2);
        checkLp.topMargin=dp(34);root.addView(check,checkLp);
        setContentView(root);

        // La clave de GitHub está cifrada en Android Keystore. Nunca pedirla
        // desde el splash, ni detener el arranque si hay problemas de red.
        if(UpdateChecker.configured(this)){
            new Thread(()->{
                UpdateChecker.schedule(getApplicationContext());
                UpdateChecker.Result result=UpdateChecker.check(getApplicationContext(),false);
                if(result.available())UpdateChecker.maybeNotify(getApplicationContext(),result.update);
            },"scriptura-splash-updates").start();
        }
        handler.postDelayed(()->{
            if(isFinishing()||isDestroyed())return;
            startActivity(new Intent(this,MainActivity.class));
            finish();
        },1200);
    }
}
