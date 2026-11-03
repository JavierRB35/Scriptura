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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

/** Cliente Groq opcional: nunca envía pasajes sin autorización explícita vigente. */
final class GroqClient {
    static final String MODEL = "openai/gpt-oss-120b";
    private static final String STORE = "scriptura_groq_config";
    private static final String ALIAS = "scriptura_groq_aes_v1";
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private final Context context;

    GroqClient(Context c) { context = c.getApplicationContext(); }
    private SharedPreferences prefs() {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }
    boolean configured() { return !prefs().getString("api_encrypted", "").isEmpty(); }
    boolean fallbackEnabled() { return configured() && prefs().getBoolean("fallback_enabled", false); }

    void setFallbackEnabled(boolean enabled) {
        if (enabled && !configured()) throw new IllegalStateException("Introduce primero tu clave Groq.");
        prefs().edit().putBoolean("fallback_enabled", enabled).apply();
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return gen.generateKey();
    }

    /** Al introducir otra clave hay que volver a aceptar la transmisión al proveedor. */
    void saveKey(String raw) throws Exception {
        String value = raw.trim();
        if (value.isEmpty()) {
            prefs().edit().remove("api_encrypted").putBoolean("fallback_enabled", false).apply();
            return;
        }
        if (value.length() > 400 || value.contains(" "))
            throw new IllegalArgumentException("Formato de clave Groq no válido");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        String encrypted = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." +
            Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        prefs().edit().putString("api_encrypted", encrypted)
            .putBoolean("fallback_enabled", false).apply();
    }

    private String readKey() throws Exception {
        String data = prefs().getString("api_encrypted", "");
        if (data.isEmpty()) throw new IOException("Configura una clave Groq en Ajustes.");
        String[] parts = data.split("\\.", -1);
        if (parts.length != 2) throw new IOException("Clave Groq cifrada dañada: vuelve a guardarla.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(),
            new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
            StandardCharsets.UTF_8);
    }

    String explain(String reference, String scripture, String contextText, String question) throws Exception {
        return generate(BibleAiPrompt.build(reference,scripture,contextText,question),false);
    }
    String geography(String prompt)throws Exception{return generate(prompt,true);}
    private String generate(String prompt,boolean structured)throws Exception{
        // Revalidar justo antes de enviar: una revocación impide nuevas transmisiones.
        if (!fallbackEnabled()) throw new IOException("El respaldo de Groq no está autorizado.");
        String apiKey = readKey();
        JSONObject payload = new JSONObject();
        payload.put("model", MODEL);
        payload.put("messages", new JSONArray().put(new JSONObject()
            .put("role", "user").put("content",
                prompt)));
        payload.put("temperature", 0.35);
        payload.put("max_completion_tokens",structured?8192:1400);
        if(structured)payload.put("response_format",new JSONObject().put("type","json_object"));
        HttpURLConnection con = (HttpURLConnection) new URL(API_URL).openConnection();
        con.setInstanceFollowRedirects(false); // No reenviar el token en redirecciones.
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setConnectTimeout(12000);
        con.setReadTimeout(30000);
        con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        con.setRequestProperty("Authorization", "Bearer " + apiKey);
        try {
            byte[] request = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = con.getOutputStream()) { out.write(request); }
            int status = con.getResponseCode();
            if (status != 200) {
                if (status == 429) throw new IOException("Cuota de Groq agotada (HTTP 429).");
                if (status == 401 || status == 403)
                    throw new IOException("La clave Groq no tiene acceso (HTTP " + status + ").");
                throw new IOException("Groq no ha podido responder (HTTP " + status + ").");
            }
            JSONObject json = new JSONObject(read(con.getInputStream(), 1_000_000));
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.length() == 0)
                throw new IOException("Groq no ha devuelto una respuesta.");
            if(structured&&!"stop".equals(choices.getJSONObject(0).optString("finish_reason")))
                throw new IOException("La respuesta geográfica no ha terminado. Se conserva la información local.");
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            String answer = message == null ? "" : message.optString("content", "").trim();
            if (answer.isEmpty()) throw new IOException("Groq ha devuelto una respuesta vacía.");
            return answer;
        } finally {
            con.disconnect();
        }
    }

    private static String read(InputStream stream, int max) throws IOException {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > max) throw new IOException("Respuesta de Groq demasiado grande.");
            }
            return out.toString("UTF-8");
        }
    }
}
