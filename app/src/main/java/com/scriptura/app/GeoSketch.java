package com.scriptura.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Proyección esquemática de puntos. No es una reconstrucción de caminos antiguos. */
public final class GeoSketch extends View {
    private final ArrayList<BibleGeography.Place> places=new ArrayList<>();
    private final Consumer<BibleGeography.Place> onPlace;
    private final boolean connectInOrder;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<float[]> screenPoints=new ArrayList<>();
    private double loX,hiX,loY,hiY;

    public GeoSketch(Context context,List<BibleGeography.Place> stops,boolean connect,
                     Consumer<BibleGeography.Place> onPlace){
        super(context);
        this.places.addAll(stops);
        this.connectInOrder=connect;
        this.onPlace=onPlace;
        setContentDescription("Esquema de "+places.size()+" lugares bíblicos. Hay un listado accesible debajo.");
        setMinimumHeight(dp(270));
        setClickable(true);
    }
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);
        int height=dp(295);
        setMeasuredDimension(width,height);
    }
    private float[] point(BibleGeography.Place p,int w,int h){
        double x=(p.lon-loX)/(hiX-loX);
        double y=(hiY-p.lat)/(hiY-loY);
        int inset=dp(30);
        return new float[]{(float)(inset+x*(w-2*inset)),(float)(inset+y*(h-2*inset))};
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        int w=getWidth(),h=getHeight();
        c.drawColor(Color.rgb(240,245,241));
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));
        paint.setColor(Color.rgb(216,225,218));paint.setPathEffect(null);
        for(int i=1;i<5;i++){
            float x=w*i/5f,y=h*i/5f;
            c.drawLine(x,0,x,h,paint);
            c.drawLine(0,y,w,y,paint);
        }
        if(places.isEmpty())return;
        loX=Double.MAX_VALUE;hiX=-Double.MAX_VALUE;loY=Double.MAX_VALUE;hiY=-Double.MAX_VALUE;
        for(BibleGeography.Place p:places){
            loX=Math.min(loX,p.lon);hiX=Math.max(hiX,p.lon);
            loY=Math.min(loY,p.lat);hiY=Math.max(hiY,p.lat);
        }
        double midX=(loX+hiX)/2,midY=(loY+hiY)/2;
        hiX=Math.max(hiX,midX+0.11);loX=Math.min(loX,midX-0.11);
        hiY=Math.max(hiY,midY+0.11);loY=Math.min(loY,midY-0.11);
        double dx=(hiX-loX)*0.13,dy=(hiY-loY)*0.13;
        loX-=dx;hiX+=dx;loY-=dy;hiY+=dy;
        screenPoints.clear();
        for(BibleGeography.Place p:places)screenPoints.add(point(p,w,h));
        if(connectInOrder && screenPoints.size()>1){
            paint.setColor(Color.rgb(155,123,79));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setPathEffect(new DashPathEffect(new float[]{dp(7),dp(5)},0));
            for(int i=1;i<screenPoints.size();i++){
                float[] before=screenPoints.get(i-1),after=screenPoints.get(i);
                c.drawLine(before[0],before[1],after[0],after[1],paint);
            }
            paint.setPathEffect(null);
        }
        for(int i=0;i<screenPoints.size();i++){
            float[] xy=screenPoints.get(i);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);c.drawCircle(xy[0],xy[1],dp(12),paint);
            paint.setColor(Color.rgb(42,76,66));c.drawCircle(xy[0],xy[1],dp(10),paint);
            paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(11));
            String number=""+(i+1);
            c.drawText(number,xy[0],xy[1]+dp(4),paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(Color.rgb(86,102,94));
        paint.setTextSize(dp(10));
        c.drawText("N ↑  ·  esquema de coordenadas",dp(9),h-dp(10),paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_UP){
            for(int i=screenPoints.size()-1;i>=0;i--){
                float[] point=screenPoints.get(i);
                double dx=e.getX()-point[0],dy=e.getY()-point[1];
                if(dx*dx+dy*dy<dp(24)*dp(24)){
                    performClick();
                    onPlace.accept(places.get(i));
                    return true;
                }
            }
            return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
