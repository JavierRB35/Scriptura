package com.scriptura.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.text.Html;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

public final class BibleStore extends SQLiteOpenHelper {
    private static final Pattern PARA=Pattern.compile("(?is)<p\\b([^>]*)>(.*?)</p\\s*>");
    private static final Pattern CLASS=Pattern.compile("(?i)class=[\\\"']([^\\\"']+)");
    private static final Pattern MARK=Pattern.compile("(?is)<(?:a|b|span)\\b[^>]*\\bid=[\\\"']tx-([A-Za-z0-9]+)-(\\d+)-(\\d+)(?:m)?[\\\"'][^>]*>");
    private static final Pattern REF=Pattern.compile("(?i)^(.+?)\\s+(\\d+)(?:\\s*[:.,]\\s*(\\d+)(?:\\s*[-–]\\s*(\\d+))?)?$");
    private final Context context;
    public BibleStore(Context c){ super(c,"scriptura.db",null,2);context=c.getApplicationContext();}
    @Override public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE verses(book TEXT NOT NULL,chapter INTEGER NOT NULL,verse INTEGER NOT NULL,text TEXT NOT NULL,PRIMARY KEY(book,chapter,verse))");
        d.execSQL("CREATE INDEX idx_verses_book ON verses(book,chapter)");
        d.execSQL("CREATE TABLE favorites(ref TEXT PRIMARY KEY, created INTEGER NOT NULL)");
        d.execSQL("CREATE TABLE history(ref TEXT PRIMARY KEY, seen INTEGER NOT NULL)");
        d.execSQL("CREATE TABLE reflections(key TEXT PRIMARY KEY, value TEXT NOT NULL, created INTEGER NOT NULL)");
        d.execSQL("CREATE TABLE kv(key TEXT PRIMARY KEY,value TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase d,int old,int now){
        if(old<2) {d.execSQL("CREATE TABLE IF NOT EXISTS kv(key TEXT PRIMARY KEY,value TEXT NOT NULL)");}
    }
    public boolean ready(){ return count()>20000; }
    public int count(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM verses",null)){return c.moveToFirst()?c.getInt(0):0;}}
    public String getMeta(String k){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM kv WHERE key=?",new String[]{k})){return c.moveToFirst()?c.getString(0):"";}
    }
    private void putMeta(SQLiteDatabase d,String key,String value){d.execSQL("INSERT OR REPLACE INTO kv(key,value) VALUES(?,?)",new Object[]{key,value});}
    private static String clean(String html){
        String t=Html.fromHtml(html,Html.FROM_HTML_MODE_LEGACY).toString();
        return t.replace("\uFEFF","").replace("\u00A0"," ").replaceAll("[\\t\\r\\n]+"," ").replaceAll(" +"," ").trim();
    }
    private static boolean scriptureClass(String cls){
        return cls.matches("(?i)(tx[a-z0-9]*|h6|h7|h9|h13|h21|h22|s)");
    }
    private static final class Part {
        String book;int chapter,verse;StringBuilder body=new StringBuilder();
        void set(String b,int ch,int ve){book=b;chapter=ch;verse=ve;body.setLength(0);}
    }
    private void flush(SQLiteDatabase d,Part p) {
        if(p.book==null)return;
        String value=p.body.toString().replaceAll(" +"," ").trim();
        if(value.length()==0)return;
        d.execSQL("INSERT OR REPLACE INTO verses(book,chapter,verse,text) VALUES(?,?,?,?)",new Object[]{p.book,p.chapter,p.verse,value});
    }
    private void append(Part p,String html,boolean dropNumber){
        if(p.book==null)return;
        String s=clean(html);
        if(dropNumber) s=s.replaceFirst("^\\d+\\s*","");
        if(s.isEmpty())return;
        if(p.body.length()>0)p.body.append(" ");
        p.body.append(s);
    }
    private void processHtml(SQLiteDatabase db,String contents,Part p){
        Matcher paragraphs=PARA.matcher(contents);
        while(paragraphs.find()){
            Matcher cm=CLASS.matcher(paragraphs.group(1));
            if(!cm.find()||!scriptureClass(cm.group(1).split(" ")[0]))continue;
            String content=paragraphs.group(2);
            Matcher marks=MARK.matcher(content);
            int position=0;
            boolean seen=false;
            while(marks.find()){
                if(p.book!=null && !seen && marks.start()>0) {
                    String prefix=content.substring(0,marks.start());
                    // El encabezado del capítulo no forma parte de un versículo.
                    if(!prefix.contains("tx-Cap-"))append(p,prefix,false);
                }
                if(p.book!=null && seen)append(p,content.substring(position,marks.start()),true);
                String code=marks.group(1);
                int ch=Integer.parseInt(marks.group(2));
                int vs=Integer.parseInt(marks.group(3));
                if(!Books.NAMES.containsKey(code)||ch<1||ch>200||vs<1||vs>200){position=marks.end();continue;}
                // Los marcadores de verso tienen una variante 'm' para el texto, junto con anclas en los encabezados.
                flush(db,p);
                p.set(code,ch,vs);
                seen=true;
                position=marks.end();
            }
            if(seen) append(p,content.substring(position),true);
            else if(p.book!=null) append(p,content,false);
        }
    }
    /** Recibe información real de las fases desde el hilo de importación. */
    public interface ImportProgress {
        void onStatus(String status);
    }
    private static void report(ImportProgress progress,String status){
        if(progress!=null)progress.onStatus(status);
    }
    private static boolean isScriptureDocument(String filename){
        if(!filename.matches("(?i).*?/Text/part\\d+(_split_\\d+)?\\.html"))return false;
        int part=Integer.parseInt(filename.replaceAll("(?i).*?/Text/part(\\d+).*","$1"));
        return part>=6&&part<=235;
    }
    public int importEpub(Uri uri) throws Exception {
        return importEpub(uri,null);
    }
    public int importEpub(Uri uri,ImportProgress progress) throws Exception {
        File tmp=new File(context.getFilesDir(),"scriptura-import.tmp");
        File permanent=new File(context.getFilesDir(),"biblia-usuario.epub");
        PrivateEpubSwap swap=new PrivateEpubSwap(permanent);
        boolean committed=false;
        try {
        report(progress,"Copiando el EPUB a la biblioteca privada…");
        long copied=0;
        try(InputStream input=context.getContentResolver().openInputStream(uri); OutputStream output=new FileOutputStream(tmp)){
            if(input==null)throw new IOException("No se pudo abrir el EPUB");
            byte[] buf=new byte[32768];int n;
            while((n=input.read(buf))>=0){copied+=n;if(copied>60_000_000)throw new IOException("EPUB demasiado grande (máximo 60 MB)");output.write(buf,0,n);}
        }
        SQLiteDatabase db=getWritableDatabase();
        int imported=0;
        db.beginTransaction();
        try(ZipFile zip=new ZipFile(tmp)){
            if(zip.getEntry("META-INF/container.xml")==null)throw new IOException("Archivo EPUB no válido");
            db.execSQL("DELETE FROM verses");
            ArrayList<? extends ZipEntry> files=Collections.list(zip.entries());
            int totalDocuments=0;
            for(ZipEntry entry:files)
                if(isScriptureDocument(entry.getName()))totalDocuments++;
            report(progress,"Leyendo los documentos bíblicos (0 de "+totalDocuments+")…");
            Part current=new Part();
            int processed=0;long totalUncompressed=0;
            for(ZipEntry entry:files){
                String filename=entry.getName();
                if(!isScriptureDocument(filename))continue;
                if(entry.getSize()>3_000_000)throw new IOException("EPUB contiene un documento demasiado grande");
                totalUncompressed+=Math.max(entry.getSize(),0);
                if(totalUncompressed>180_000_000)throw new IOException("EPUB excede el límite de descompresión");
                if(++processed>1500)throw new IOException("EPUB excede el límite de documentos");
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                try(InputStream in=zip.getInputStream(entry)){
                    byte[] bytes=new byte[8192];int n;
                    while((n=in.read(bytes))!=-1){buffer.write(bytes,0,n);if(buffer.size()>3_000_000)throw new IOException("Documento EPUB demasiado grande");}
                }
                processHtml(db,buffer.toString("UTF-8"),current);
                if(processed==1||processed%20==0||processed==totalDocuments)
                    report(progress,"Leyendo los documentos bíblicos ("+processed+" de "+totalDocuments+")…");
            }
            flush(db,current);
            report(progress,"Verificando los versículos y los 73 libros…");
            try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM verses",null)){
                imported=c.moveToFirst()?c.getInt(0):0;
            }
            if(imported<25000)throw new IOException("Importación incompleta: solo "+imported+" versículos. Se conserva la biblioteca anterior.");
            try(Cursor c=db.rawQuery("SELECT COUNT(DISTINCT book) FROM verses",null)){
                int books=c.moveToFirst()?c.getInt(0):0;
                if(books!=73)throw new IOException("La edición no ha producido los 73 libros (detectados: "+books+")");
            }
            putMeta(db,"edition","Sagrada Biblia · Universidad de Navarra (archivo privado)");
            // Solo modificar el EPUB guardado después de verificar los 73 libros.
            // El respaldo permite restaurar si SQLite no llega a confirmar.
            report(progress,"Guardando la biblioteca validada…");
            swap.replace(tmp);
            db.setTransactionSuccessful();
        }finally{
            db.endTransaction();
        }
        committed=true;
        swap.finish();
        return imported;
        }catch(Exception failure){
            if(!committed){
                try{swap.rollback();}
                catch(IOException recovery){failure.addSuppressed(recovery);}
            }
            throw failure;
        }finally{
            // No dejar archivos privados temporales después de fallos de lectura o ZIP.
            if(tmp.exists()&&!tmp.delete())
                android.util.Log.w("Scriptura","No se pudo retirar el EPUB temporal después de la importación.");
        }
    }
    public static final class Passage{
        public final String ref,book,text;public final int chapter,first,last;
        Passage(String r,String b,int c,int f,int l,String t){ref=r;book=b;chapter=c;first=f;last=l;text=t;}
    }
    public Passage passage(String query){
        String q=query.trim().replace('：',':');
        Matcher m=REF.matcher(q);
        String book;int chapter=1,first=1,last=200;
        if(m.matches()){
            book=Books.resolve(m.group(1));
            if(book.isEmpty())return null;
            chapter=Integer.parseInt(m.group(2));
            if(m.group(3)!=null){first=Integer.parseInt(m.group(3));last=m.group(4)!=null?Integer.parseInt(m.group(4)):first;}
        }else book=Books.resolve(q);
        if(book.isEmpty()||chapter<1||chapter>200||first>last||last>200)return null;
        StringBuilder out=new StringBuilder();
        int count=0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT verse,text FROM verses WHERE book=? AND chapter=? AND verse BETWEEN ? AND ? ORDER BY verse",new String[]{book,""+chapter,""+first,""+last})){
            while(c.moveToNext()){out.append(c.getInt(0)).append("  ").append(c.getString(1)).append("\n\n");count++;}
        }
        if(count==0)return null;
        String ref=Books.name(book)+" "+chapter+(first!=1||last!=200?":"+first+(first!=last?"-"+last:""):"");
        return new Passage(ref,book,chapter,first,last,out.toString().trim());
    }
    public int chapters(String book){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT MAX(chapter) FROM verses WHERE book=?",new String[]{book})){return c.moveToFirst()?c.getInt(0):0;}
    }
    public List<Passage> searchText(String word,int limit){
        ArrayList<Passage> results=new ArrayList<>();
        String q=word.trim();
        if(q.length()<2)return results;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT book,chapter,verse,text FROM verses WHERE text LIKE ? LIMIT "+Math.min(limit,80),new String[]{"%"+q+"%"})){
            while(c.moveToNext()){
                String b=c.getString(0);int ch=c.getInt(1),v=c.getInt(2);
                results.add(new Passage(Books.name(b)+" "+ch+":"+v,b,ch,v,v,c.getString(3)));
            }
        }
        return results;
    }
    /** El ciclo de cada Testamento visita todos sus libros antes de repetir uno. */
    public Passage random(boolean newTestament,long dayIndex){
        ArrayList<String> codes=new ArrayList<>();
        for(String code:Books.NAMES.keySet())
            if(Books.isNew(code)==newTestament&&chapters(code)>0)codes.add(code);
        if(codes.isEmpty())return null;
        int step=newTestament?11:17;
        if(gcd(step,codes.size())!=1)step=1;
        int index=(int)Math.floorMod(dayIndex*step+(newTestament?5:13),codes.size());
        String book=codes.get(index);
        Random random=new Random(dayIndex*1000003L+index*7919L+(newTestament?31:71));
        int chapter=1+random.nextInt(chapters(book));
        int max=0;
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT MAX(verse) FROM verses WHERE book=? AND chapter=?",
                new String[]{book,""+chapter})){
            if(c.moveToFirst())max=c.getInt(0);
        }
        if(max<1)return passage(Books.name(book)+" "+chapter);
        int start=1+random.nextInt(max);
        Passage hit=passage(Books.name(book)+" "+chapter+":"+start+"-"+Math.min(max,start+6));
        return hit!=null?hit:passage(Books.name(book)+" "+chapter);
    }
    private static int gcd(int a,int b){
        while(b!=0){int t=a%b;a=b;b=t;}return a;
    }
    public ReadingProgress.Position lastReadPosition(){
        return ReadingProgress.fromStored(getReflection(ReadingProgress.KEY));
    }
    public void saveReadingPosition(ReadingProgress.Position position){
        if(position!=null)saveReflection(ReadingProgress.KEY,position.toStored());
    }
    public void record(String ref){
        getWritableDatabase().execSQL("INSERT OR REPLACE INTO history(ref,seen) VALUES(?,?)",new Object[]{ref,System.currentTimeMillis()});
    }
    public boolean isFavorite(String ref){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM favorites WHERE ref=?",new String[]{ref})){return c.moveToFirst();}
    }
    public boolean toggleFavorite(String ref){
        if(isFavorite(ref)){
            getWritableDatabase().execSQL("DELETE FROM favorites WHERE ref=?",new Object[]{ref});return false;
        }
        getWritableDatabase().execSQL("INSERT OR REPLACE INTO favorites(ref,created) VALUES(?,?)",new Object[]{ref,System.currentTimeMillis()});return true;
    }
    public List<String> saved(boolean favorites){
        List<String> ret=new ArrayList<>();
        String table=favorites?"favorites":"history";
        String col=favorites?"created":"seen";
        try(Cursor c=getReadableDatabase().rawQuery("SELECT ref FROM "+table+" ORDER BY "+col+" DESC LIMIT 50",null)){
            while(c.moveToNext())ret.add(c.getString(0));
        }
        return ret;
    }
    public String getReflection(String key){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM reflections WHERE key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):"";}
    }
    public void saveReflection(String key,String text){
        getWritableDatabase().execSQL("INSERT OR REPLACE INTO reflections(key,value,created) VALUES(?,?,?)",new Object[]{key,text,System.currentTimeMillis()});
    }
    /** Las consultas bíblicas comparten la persistencia y las copias de reflections. */
    public void saveBibleQuestion(String question,String answer,
            List<BibleQuestionReferences.Ref> references) throws org.json.JSONException {
        String value=BibleQuestionHistory.encode(question,answer,references);
        saveReflection(BibleQuestionHistory.KEY_PREFIX+UUID.randomUUID().toString(),value);
    }
    public int bibleQuestionCount(){
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM reflections WHERE key LIKE ?",
                new String[]{BibleQuestionHistory.KEY_PREFIX+"%"})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    public List<BibleQuestionHistory.Entry> bibleQuestions(int offset,int limit){
        ArrayList<BibleQuestionHistory.Entry> result=new ArrayList<>();
        int safeOffset=Math.max(0,offset);
        int safeLimit=Math.max(1,Math.min(30,limit));
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT value,created FROM reflections WHERE key LIKE ? "+
                "ORDER BY created DESC,key DESC LIMIT ? OFFSET ?",
                new String[]{BibleQuestionHistory.KEY_PREFIX+"%",
                    String.valueOf(safeLimit),String.valueOf(safeOffset)})){
            while(c.moveToNext()){
                BibleQuestionHistory.Entry entry=BibleQuestionHistory.decode(c.getString(0),c.getLong(1));
                if(entry!=null)result.add(entry);
            }
        }
        return result;
    }
}
