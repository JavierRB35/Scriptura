package com.scriptura.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

public final class GeminiClient {
    private static final String STORE="scriptura_config";
    private static final String ALIAS="scriptura_gemini_aes_v1";
    private final Context context;
    public GeminiClient(Context c){context=c.getApplicationContext();}
    private SharedPreferences prefs(){return context.getSharedPreferences(STORE,Context.MODE_PRIVATE);}
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(ALIAS))return (SecretKey)ks.getKey(ALIAS,null);
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return gen.generateKey();
    }
    public void saveKey(String raw) throws Exception {
        String value=raw.trim();
        if(value.isEmpty()){prefs().edit().remove("api_encrypted").apply();return;}
        if(value.length()>400||value.contains(" "))throw new Exception("Formato de clave no válido");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] iv=cipher.getIV();
        byte[] enc=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        prefs().edit().putString("api_encrypted",Base64.encodeToString(iv,Base64.NO_WRAP)+"."+Base64.encodeToString(enc,Base64.NO_WRAP)).apply();
    }
    private String readKey() throws Exception {
        String data=prefs().getString("api_encrypted","");
        if(data.isEmpty())throw new Exception("Configura tu clave Gemini en Ajustes");
        String[] parts=data.split("\\.",-1);
        if(parts.length!=2)throw new Exception("Clave cifrada dañada: vuelve a guardarla");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    public boolean configured(){return !prefs().getString("api_encrypted","").isEmpty();}
    public String model(){return prefs().getString("model","gemini-3.1-flash-lite");}
    public void model(String name){
        if(!name.matches("[a-zA-Z0-9._-]{3,80}"))throw new IllegalArgumentException("Modelo no válido");
        prefs().edit().putString("model",name).apply();
    }
    public static String getText(String urlString) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(urlString).openConnection();
        conn.setConnectTimeout(12000);conn.setReadTimeout(20000);
        conn.setRequestProperty("Accept","text/html,application/json");
        try{
            int code=conn.getResponseCode();if(code!=200)throw new IOException("HTTP "+code);
            return read(conn.getInputStream(),2_000_000);
        }finally{conn.disconnect();}
    }
    private static String read(InputStream stream,int max) throws IOException {
        if(stream==null)return "";
        try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[4096];int n;
            while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>max)throw new IOException("Respuesta demasiado grande");}
            return out.toString("UTF-8");
        }
    }
    public String explain(String ref,String scripture,String context,String question) throws Exception {
        return generate(BibleAiPrompt.build(ref,scripture,context,question),420,false);
    }
    String geography(String prompt)throws Exception{return generate(prompt,8192,true);}
    private String generate(String prompt,int maxTokens,boolean structured)throws Exception{
        String api=readKey();
        String m=model();
        if(!m.matches("[a-zA-Z0-9._-]{3,80}"))throw new Exception("Modelo no válido");
        JSONObject payload=new JSONObject();
        payload.put("contents",new JSONArray().put(new JSONObject().put("role","user").put("parts",
            new JSONArray().put(new JSONObject().put("text",prompt)))));
        JSONObject config=new JSONObject().put("temperature",structured?0.1:0.35).put("maxOutputTokens",maxTokens);
        if(structured)config.put("responseMimeType","application/json");
        payload.put("generationConfig",config);
        URL endpoint=new URL("https://generativelanguage.googleapis.com/v1beta/models/"+m+":generateContent");
        HttpURLConnection con=(HttpURLConnection)endpoint.openConnection();
        con.setRequestMethod("POST");con.setDoOutput(true);
        con.setInstanceFollowRedirects(false);
        con.setConnectTimeout(15000);con.setReadTimeout(35000);
        con.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        con.setRequestProperty("x-goog-api-key",api);
        try {
            byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=con.getOutputStream()){out.write(bytes);}
            int status=con.getResponseCode();
            String data=read(status>=200&&status<300?con.getInputStream():con.getErrorStream(),1_000_000);
            // Un 429/5xx puede carecer de JSON válido; aun así debe activar el respaldo.
            if(status==429)throw new AiRecoverableException("Cuota gratuita de Gemini agotada (HTTP 429).");
            if(status>=500&&status<=599)throw new AiRecoverableException(
                "Gemini no está disponible temporalmente (HTTP "+status+").");
            JSONObject json=new JSONObject(data);
            if(status!=200){
                String message=json.optJSONObject("error")==null?"Sin detalles":json.getJSONObject("error").optString("message");
                if(status==401||status==403)throw new IOException("Clave API sin acceso. Comprueba AI Studio. "+message);
                throw new IOException("Gemini HTTP "+status+": "+message);
            }
            JSONArray candidates=json.optJSONArray("candidates");
            if(candidates==null||candidates.length()==0)throw new IOException("Gemini no ha devuelto una respuesta de texto");
            if(structured&&!"STOP".equals(candidates.getJSONObject(0).optString("finishReason")))
                throw new IOException("La respuesta geográfica no ha terminado. Se conserva la información local.");
            JSONObject content=candidates.getJSONObject(0).optJSONObject("content");
            if(content==null)throw new IOException("Respuesta sin contenido");
            JSONArray parts=content.optJSONArray("parts");if(parts==null)throw new IOException("Respuesta vacía");
            StringBuilder answer=new StringBuilder();
            for(int i=0;i<parts.length();i++){
                String text=parts.getJSONObject(i).optString("text","");
                if(!text.isEmpty())answer.append(text).append("\n");
            }
            if(answer.length()==0)throw new IOException("Gemini no ha generado texto");
            return answer.toString().trim();
        }catch(java.net.SocketTimeoutException timeout){
            throw new AiRecoverableException("Gemini ha agotado el tiempo de espera.",timeout);
        }finally{con.disconnect();}
    }
}
