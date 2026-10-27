package com.scriptura.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import java.util.Random;

/**
 * Cuatro ilustraciones originales guardadas en la APK; nunca dependen de la red.
 * El modo se elige entre escenas bíblicas, no entre cuatro copias del logotipo.
 */
public final class HomeSceneView extends View {
    private final int scene;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int GOLD=Color.rgb(237,186,100);
    private static final int NIGHT=Color.rgb(9,32,51);
    public HomeSceneView(Context context,int selection){
        super(context);
        scene=Math.floorMod(selection,4);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        String[] descriptions={
            "Paisaje de colinas con olivares y camino antiguo al amanecer",
            "Libro abierto y lámpara de aceite junto a una ventana",
            "Barca sobre el lago al amanecer",
            "Vista artística de una ciudad bíblica entre colinas al atardecer"};
        setContentDescription(descriptions[scene]+". Ilustración evocadora, no reconstrucción histórica.");
    }
    private void fill(Paint paint,int color){paint.reset();paint.setAntiAlias(true);paint.setStyle(Paint.Style.FILL);paint.setColor(color);}
    private int rgb(String hex){return Color.parseColor(hex);}
    private void rectangle(Canvas c,float a,float b,float d,float e,int color){
        fill(p,color);c.drawRect(a,b,d,e,p);
    }
    private void circle(Canvas c,float x,float y,float r,int color){
        fill(p,color);c.drawCircle(x,y,r,p);
    }
    private void poly(Canvas c,int color,float... coords){
        Path path=new Path();path.moveTo(coords[0],coords[1]);
        for(int i=2;i<coords.length;i+=2)path.lineTo(coords[i],coords[i+1]);
        path.close();fill(p,color);c.drawPath(path,p);
    }
    private void line(Canvas c,int color,float width,float... xy){
        fill(p,color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);
        Path path=new Path();path.moveTo(xy[0],xy[1]);
        for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);
        c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
    }
    private void background(Canvas c,int top,int bottom){
        fill(p,top);
        p.setShader(new LinearGradient(0,0,0,480,top,bottom,Shader.TileMode.CLAMP));
        c.drawRect(0,0,1000,480,p);p.setShader(null);
    }
    private void mountains(Canvas c,boolean sea){
        poly(c,rgb("#34556C"),0,290,120,230,245,281,400,206,575,290,750,235,1000,274,1000,480,0,480);
        poly(c,rgb("#213F55"),0,329,165,295,280,327,490,266,660,334,841,274,1000,320,1000,480,0,480);
        if(sea){
            rectangle(c,0,323,1000,480,rgb("#193C5E"));
            for(int i=0;i<16;i++)line(c,rgb(i%2==0?"#C99A61":"#577C89"),1+(i%2),
                30+((i*67)%190),335+i*8,950-((i*53)%290),335+i*8);
        }else poly(c,rgb("#172F38"),0,345,220,313,400,364,680,304,1000,349,1000,480,0,480);
    }
    private void drawTree(Canvas c,int x,int y,float size){
        c.save();c.translate(x,y);c.scale(size,size);
        line(c,rgb("#271F1A"),16,0,130,0,25,-30,-5);
        line(c,rgb("#271F1A"),11,0,60,32,10,65,-7);
        circle(c,-31,-15,42,rgb("#415A3F"));
        circle(c,22,-29,51,rgb("#486145"));
        circle(c,67,-5,39,rgb("#4C6B4E"));
        circle(c,0,5,35,rgb("#627046"));
        c.restore();
    }
    private void landscape(Canvas c){
        background(c,rgb("#17354B"),rgb("#D98A55"));
        circle(c,748,128,52,rgb("#FFD9A2"));
        mountains(c,false);
        poly(c,rgb("#916B47"),460,313,560,312,730,480,345,480);
        poly(c,rgb("#B9936A"),490,314,540,317,626,480,408,480);
        drawTree(c,115,238,1.2f);drawTree(c,845,298,0.75f);
    }
    private void book(Canvas c){
        background(c,rgb("#122B40"),rgb("#A9713E"));
        rectangle(c,0,372,1000,480,rgb("#4C3226"));
        poly(c,rgb("#654633"),100,376,920,376,964,442,47,441);
        circle(c,750,142,59,rgb("#E9A35B"));
        poly(c,rgb("#E9D3A3"),166,213,330,179,475,222,475,405,316,359,152,388);
        poly(c,rgb("#F5E4BB"),475,222,625,177,818,206,832,381,633,360,475,405);
        poly(c,rgb("#C4A26E"),462,207,476,210,480,400,465,400);
        for(int i=0;i<10;i++){
            line(c,rgb("#B69C76"),2,220,244+i*12,421,235+i*13);
            line(c,rgb("#B69C76"),2,536,230+i*13,764,242+i*11);
        }
        rectangle(c,92,290,120,369,rgb("#A27538"));
        circle(c,106,285,24,GOLD);
        line(c,rgb("#FFECBC"),5,105,265,106,244);
    }
    private void boat(Canvas c){
        background(c,rgb("#0F344D"),rgb("#E29D63"));
        circle(c,750,169,67,rgb("#FFDF9F"));
        mountains(c,true);
        poly(c,rgb("#5B3425"),180,328,665,328,576,370,271,365);
        poly(c,rgb("#CFAD7C"),380,321,390,116,565,312);
        poly(c,rgb("#EAD3A7"),384,121,200,304,375,319);
        line(c,rgb("#352D23"),7,384,115,384,325);
        line(c,rgb("#2E2920"),4,201,306,384,116,570,315);
        line(c,rgb("#D7AE7F"),5,180,330,664,330);
    }
    private void city(Canvas c){
        background(c,rgb("#0B3047"),rgb("#BC7046"));
        circle(c,790,165,53,rgb("#F5C789"));
        mountains(c,false);
        rectangle(c,0,340,1000,480,rgb("#352E32"));
        int[] heights={83,108,68,116,90,140,83,100,71,117,75,89};
        for(int i=0;i<heights.length;i++){
            int x=35+i*83,h=heights[i];
            rectangle(c,x,350-h,x+68,377,rgb(i%3==0?"#9F795E":"#BB9069"));
            for(int j=0;j<3;j++){
                rectangle(c,x+15+j*19,365-h+20,x+21+j*19,370-h+34,rgb("#4C3B33"));
                rectangle(c,x+18+j*19,369-h+28,x+22+j*19,369-h+37,rgb("#F3C880"));
            }
        }
        rectangle(c,467,167,515,344,rgb("#AF8C65"));
        poly(c,rgb("#805C48"),459,168,492,130,525,168);
        rectangle(c,482,151,500,171,rgb("#E7BE81"));
        poly(c,rgb("#3A3435"),0,415,240,370,650,410,1000,364,1000,480,0,480);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth()/1000f,getHeight()/480f);
        switch(scene){
            case 0:landscape(canvas);break;
            case 1:book(canvas);break;
            case 2:boat(canvas);break;
            default:city(canvas);break;
        }
        canvas.restore();
    }
    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
            (int)(getResources().getDisplayMetrics().density*150));
    }
}