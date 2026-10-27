package com.scriptura.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipFile;

public final class MainActivity extends Activity {
    private static final int REQUEST_EPUB = 1027;
    private TextView importStatus;
    private Button importButton;

    private int dp(int px) {
        return (int) (getResources().getDisplayMetrics().density * px + 0.5f);
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(42), dp(24), dp(24));
        layout.setBackgroundColor(Color.rgb(249, 248, 244));

        TextView title = label("Scriptura", 32, Color.rgb(47, 67, 56));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.addView(title);
        TextView version = label("Versión preliminar 0.1.0", 14, Color.DKGRAY);
        version.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.addView(version);
        layout.addView(label("Tu Biblia católica, en tu teléfono", 20, Color.rgb(47, 67, 56)));
        layout.addView(label("Primera etapa: preparar una biblioteca privada. La búsqueda y las reflexiones con Gemini se incorporarán durante el desarrollo de la serie 0.x.", 16, Color.DKGRAY));

        importButton = new Button(this);
        importButton.setText("Importar mi Biblia EPUB");
        layout.addView(importButton);
        importButton.setOnClickListener(v -> pickEpub());
        importStatus = label("", 16, Color.DKGRAY);
        layout.addView(importStatus);
        layout.addView(label("El EPUB se guarda únicamente en el almacenamiento privado de esta aplicación. Nunca se sube automáticamente a GitHub ni a Gemini.", 14, Color.DKGRAY));
        scroll.addView(layout);
        setContentView(scroll);
        File local = new File(getFilesDir(), "biblia-usuario.epub");
        importStatus.setText(local.isFile() ? "Biblia importada en este dispositivo." : "Todavía no has importado un EPUB.");
    }

    private void pickEpub() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/epub+zip", "application/octet-stream", "application/zip"});
        startActivityForResult(picker, REQUEST_EPUB);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EPUB || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        importButton.setEnabled(false);
        importStatus.setText("Importando y verificando EPUB...");
        new Thread(() -> importEpub(uri)).start();
    }

    private void importEpub(Uri uri) {
        File temporary = new File(getFilesDir(), "biblia-importacion.tmp");
        File destination = new File(getFilesDir(), "biblia-usuario.epub");
        String result;
        try {
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(temporary)) {
                if (in == null) throw new Exception("No se pudo abrir el documento.");
                byte[] buffer = new byte[32768];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            }
            try (ZipFile epub = new ZipFile(temporary)) {
                if (epub.getEntry("META-INF/container.xml") == null)
                    throw new Exception("El archivo no tiene la estructura de un EPUB válido.");
            }
            if (destination.exists() && !destination.delete())
                throw new Exception("No se pudo reemplazar el EPUB anterior.");
            if (!temporary.renameTo(destination))
                throw new Exception("No se pudo guardar el EPUB en el teléfono.");
            result = "EPUB importado correctamente (" + (destination.length() / 1024 / 1024) + " MB).";
        } catch (Exception ex) {
            result = "Error al importar: " + ex.getMessage();
        } finally {
            if (temporary.exists()) temporary.delete();
        }
        final String status = result;
        runOnUiThread(() -> {
            importStatus.setText(status);
            importButton.setEnabled(true);
        });
    }
}
