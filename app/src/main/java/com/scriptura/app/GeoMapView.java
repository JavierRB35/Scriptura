package com.scriptura.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Cartografía real en Android nativo, sin SDK de Google ni API key.
 * Descarga exclusivamente las teselas OSM VISIBLES al navegar.
 * Política: https://operations.osmfoundation.org/policies/tiles/
 * No predescarga, no mapas offline masivos. Cache mínimo 7 días.
 */
public final class GeoMapView extends View {
    private static final int TILE=256, MIN_ZOOM=2, MAX_ZOOM=17;
    private static final long CACHE_MS=7L*24*60*60*1000;
    private static final ExecutorService NETWORK=Executors.newFixedThreadPool(2);
    private static final String AGENT="ScripturaAndroid/0.6 (+https://github.com/JavierRB35/Scriptura)";
    private final ArrayList<BibleGeography.Place> places=new ArrayList<>();
    private final HashMap<String,Bitmap> images=new HashMap<>();
    private final Set<String> pending=new HashSet<>();
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Consumer<BibleGeography.Place> onPlace;
    private final boolean route;
    private final ScaleGestureDetector scaleGesture;
    private final File cache;
    private int zoom=6, loaded=0;
    private double lat=32.5, lon=35.1;
    private boolean fitted=false, moved=false, failure=false;
    private float previousX,previousY,downX,downY;
    private final ArrayList<float[]> markerPoints=new ArrayList<>();
    private String status="Cargando mapa...";

    public GeoMapView(Context context,List<BibleGeography.Place> data,boolean join,
                      Consumer<BibleGeography.Place> selected){
        super(context);
        places.addAll(data);
        route=join;onPlace=selected;
        cache=new File(context.getCacheDir(),"scriptura-osm-visible-tiles");
        setMinimumHeight(dp(310));setClickable(true);
        setContentDescription("Mapa geográfico interactivo con "+places.size()+
                " lugares bíblicos. Usa la lista de etapas para accesibilidad.");
        scaleGesture=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){
                if(d.getScaleFactor()>1.16f){zoomIn();return true;}
                if(d.getScaleFactor()<0.86f){zoomOut();return true;}
                return true;
            }
        });
    }
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private static double clamp(double n,double a,double b){return Math.min(b,Math.max(a,n));}
    private static double worldX(double longitude,int z){
        return (longitude+180.0)/360.0*TILE*(1<<z);
    }
    private static double worldY(double latitude,int z){
        double rad=Math.toRadians(clamp(latitude,-85.051129,85.051129));
        return (1-(Math.log(Math.tan(rad)+1/Math.cos(rad))/Math.PI))/2*TILE*(1<<z);
    }
    private static double worldToLongitude(double x,int z){return x/(TILE*(1<<z))*360.0-180;}
    private static double worldToLatitude(double y,int z){
        double n=Math.PI-2*Math.PI*y/(TILE*(1<<z));
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }
    private double worldSize(){return TILE*(1<<zoom);}
    private float[] screen(BibleGeography.Place p){
        double w=worldSize();
        double dX=worldX(p.lon,zoom)-worldX(lon,zoom);
        if(dX>w/2)dX-=w;
        if(dX<-w/2)dX+=w;
        return new float[]{(float)(getWidth()/2+dX),
            (float)(getHeight()/2+worldY(p.lat,zoom)-worldY(lat,zoom))};
    }
    private void fitPlaces(){
        if(places.isEmpty())return;
        double minX=180,maxX=-180,minY=85,maxY=-85;
        for(BibleGeography.Place p:places){
            minX=Math.min(minX,p.lon);maxX=Math.max(maxX,p.lon);
            minY=Math.min(minY,p.lat);maxY=Math.max(maxY,p.lat);
        }
        lon=(minX+maxX)/2;lat=(minY+maxY)/2;
        if(places.size()==1){zoom=11;return;}
        int width=Math.max(dp(180),getWidth()-dp(75));
        int height=Math.max(dp(180),getHeight()-dp(75));
        for(int z=12;z>=MIN_ZOOM;z--){
            double dx=worldX(maxX,z)-worldX(minX,z);
            double dy=worldY(minY,z)-worldY(maxY,z);
            if(dx<=width && dy<=height){zoom=z;break;}
        }
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        if(!fitted&&w>0&&h>0){fitPlaces();fitted=true;}
    }
    @Override protected void onMeasure(int wSpec,int hSpec){
        setMeasuredDimension(MeasureSpec.getSize(wSpec),dp(315));
    }
    public void zoomIn(){if(zoom<MAX_ZOOM){zoom++;invalidate();}}
    public void zoomOut(){if(zoom>MIN_ZOOM){zoom--;invalidate();}}
    public void retry(){failure=false;status="Reintentando cargar el mapa...";invalidate();}
    public double centerLatitude(){return lat;}
    public double centerLongitude(){return lon;}
    public int zoomLevel(){return zoom;}

    private String id(int z,int x,int y){return z+"-"+x+"-"+y;}
    private File cacheFile(int z,int x,int y){return new File(cache,id(z,x,y)+".png");}
    private void request(final int z,final int x,final int y){
        final String id=id(z,x,y);
        synchronized(images){
            if(images.containsKey(id)||pending.contains(id))return;
            pending.add(id);
        }
        NETWORK.execute(()->{
            Bitmap bitmap=null;
            boolean error=false;
            File file=cacheFile(z,x,y);
            try{
                // Las entradas sólo se crean para teselas que el usuario está viendo.
                if(file.isFile()&&file.length()>50
                        &&System.currentTimeMillis()-file.lastModified()<CACHE_MS){
                    bitmap=BitmapFactory.decodeFile(file.getAbsolutePath());
                }
                if(bitmap==null){
                    URL url=new URL("https://tile.openstreetmap.org/"+z+"/"+x+"/"+y+".png");
                    HttpURLConnection connection=(HttpURLConnection)url.openConnection();
                    connection.setConnectTimeout(9000);connection.setReadTimeout(12000);
                    connection.setRequestProperty("User-Agent",AGENT);
                    connection.setRequestProperty("Accept","image/png");
                    try{
                        if(connection.getResponseCode()!=200){
                            throw new IllegalStateException("Tile HTTP "+connection.getResponseCode());
                        }
                        int contentLength=connection.getContentLength();
                        if(contentLength>650000)throw new IllegalStateException("Tile demasiado grande");
                        byte[] bytes;
                        try(InputStream input=connection.getInputStream();
                            java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream()){
                            byte[] chunk=new byte[4096];int n;
                            while((n=input.read(chunk))!=-1){
                                buffer.write(chunk,0,n);
                                if(buffer.size()>650000)throw new IllegalStateException("Tile demasiado grande");
                            }
                            bytes=buffer.toByteArray();
                        }
                        bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                        if(bitmap!=null){
                            // Se mantiene la caché al menos siete días; no se precarga
                            // ninguna zona fuera del área visible.
                            cache.mkdirs();
                            try(FileOutputStream stream=new FileOutputStream(file)){
                                stream.write(bytes);
                            }
                        }
                    }finally{connection.disconnect();}
                }
            }catch(Exception ex){error=true;}
            final Bitmap ready=bitmap;final boolean failed=error;
            post(()->{
                synchronized(images){
                    pending.remove(id);
                    if(ready!=null){
                        if(images.size()>100)images.clear();
                        images.put(id,ready);loaded++;
                    }
                }
                if(failed)failure=true;
                invalidate();
            });
        });
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        int width=getWidth(),height=getHeight();
        canvas.drawColor(Color.rgb(236,238,233));
        if(width<10||height<10)return;
        final int count=1<<zoom;
        double centerX=worldX(lon,zoom),centerY=worldY(lat,zoom);
        int startX=(int)Math.floor((centerX-width/2.0)/TILE);
        int endX=(int)Math.floor((centerX+width/2.0)/TILE);
        int startY=(int)Math.floor((centerY-height/2.0)/TILE);
        int endY=(int)Math.floor((centerY+height/2.0)/TILE);
        boolean hasAny=false;
        int requests=0;
        for(int y=startY;y<=endY;y++){
            if(y<0||y>=count)continue;
            for(int x=startX;x<=endX;x++){
                int mapped=((x%count)+count)%count;
                float left=(float)(x*TILE-centerX+width/2.0);
                float top=(float)(y*TILE-centerY+height/2.0);
                String key=id(zoom,mapped,y);
                Bitmap image;
                synchronized(images){image=images.get(key);}
                if(image==null){
                    File file=cacheFile(zoom,mapped,y);
                    if(file.isFile()){
                        image=BitmapFactory.decodeFile(file.getAbsolutePath());
                        if(image!=null) synchronized(images){images.put(key,image);}
                    }
                }
                if(image!=null&&!image.isRecycled()){
                    hasAny=true;
                    pen.setStyle(Paint.Style.FILL);
                    pen.setAlpha(255);
                    canvas.drawBitmap(image,null,new RectF(left,top,left+TILE,top+TILE),pen);
                }else if(requests++<24){
                    pen.setColor(Color.rgb(223,229,224));
                    pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(dp(1));
                    canvas.drawRect(left,top,left+TILE,top+TILE,pen);
                    request(zoom,mapped,y);
                }
            }
        }
        markerPoints.clear();
        for(BibleGeography.Place p:places)markerPoints.add(screen(p));
        if(route&&markerPoints.size()>1){
            pen.setStyle(Paint.Style.STROKE);pen.setColor(Color.rgb(146,76,32));
            pen.setStrokeWidth(dp(3));
            pen.setPathEffect(new DashPathEffect(new float[]{dp(8),dp(5)},0));
            for(int i=1;i<markerPoints.size();i++){
                float[] a=markerPoints.get(i-1),b=markerPoints.get(i);
                canvas.drawLine(a[0],a[1],b[0],b[1],pen);
            }
            pen.setPathEffect(null);
        }
        for(int i=0;i<markerPoints.size();i++){
            float[] m=markerPoints.get(i);
            if(m[0]<-35||m[0]>width+35||m[1]<-35||m[1]>height+35)continue;
            pen.setStyle(Paint.Style.FILL);
            pen.setColor(Color.WHITE);canvas.drawCircle(m[0],m[1],dp(12),pen);
            pen.setColor(Color.rgb(13,35,56));canvas.drawCircle(m[0],m[1],dp(10),pen);
            pen.setColor(Color.WHITE);pen.setTextAlign(Paint.Align.CENTER);
            pen.setTextSize(dp(11));
            canvas.drawText(String.valueOf(i+1),m[0],m[1]+dp(4),pen);
        }
        // Atribución visible y legible incluso cuando fallan las teselas.
        pen.setStyle(Paint.Style.FILL);
        pen.setColor(Color.argb(238,255,255,255));
        canvas.drawRoundRect(new RectF(dp(4),height-dp(32),width-dp(4),height-dp(3)),dp(5),dp(5),pen);
        pen.setTextAlign(Paint.Align.LEFT);pen.setTextSize(dp(11));
        pen.setColor(Color.rgb(19,49,72));
        canvas.drawText("© OpenStreetMap contributors · mapa actual",dp(9),height-dp(13),pen);
        if(!hasAny){
            pen.setColor(Color.rgb(13,35,56));pen.setTextAlign(Paint.Align.CENTER);
            pen.setTextSize(dp(13));
            canvas.drawText(failure?"Sin conexión o teselas no disponibles":
                "Cargando cartografía actual...",width/2f,height/2f,pen);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        scaleGesture.onTouchEvent(event);
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downX=previousX=event.getX();downY=previousY=event.getY();moved=false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if(!scaleGesture.isInProgress()&&event.getPointerCount()==1){
                    float dx=event.getX()-previousX,dy=event.getY()-previousY;
                    if(Math.abs(event.getX()-downX)+Math.abs(event.getY()-downY)>dp(8))moved=true;
                    double x=worldX(lon,zoom)-dx,y=worldY(lat,zoom)-dy;
                    double max=worldSize();
                    x=((x%max)+max)%max;
                    y=clamp(y,0.1,max-0.1);
                    lon=worldToLongitude(x,zoom);
                    lat=worldToLatitude(y,zoom);
                    invalidate();
                }
                previousX=event.getX();previousY=event.getY();
                return true;
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                if(!moved&&!scaleGesture.isInProgress()){
                    for(int i=markerPoints.size()-1;i>=0;i--){
                        float[] p=markerPoints.get(i);
                        if(Math.hypot(event.getX()-p[0],event.getY()-p[1])<dp(26)){
                            performClick();
                            onPlace.accept(places.get(i));
                            return true;
                        }
                    }
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
