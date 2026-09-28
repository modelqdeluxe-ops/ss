package hn.hato.ganadero;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Actividad invisible: pide al sistema dónde guardar el archivo (Descargas, Drive...) y copia ahí el que la
 * app dejó en caché/compartir. No toca MainActivity.
 */
public class GuardarArchivo extends Activity {
    private static final int PEDIR = 41;
    private String nombre;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        nombre = getIntent().getStringExtra("nombre");
        if (b != null) return; // al girar la pantalla ya se había pedido
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(getIntent().getStringExtra("mime"));
            i.putExtra(Intent.EXTRA_TITLE, nombre);
            startActivityForResult(i, PEDIR);
        } catch (Exception e) {
            Toast.makeText(this, "Este teléfono no permite elegir dónde guardar.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putString("nombre", nombre);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PEDIR && res == RESULT_OK && data != null && data.getData() != null && nombre != null) {
            boolean ok = false;
            InputStream in = null;
            OutputStream out = null;
            try {
                in = new FileInputStream(new File(Archivos.carpeta(this), Archivos.limpiar(nombre)));
                out = getContentResolver().openOutputStream(data.getData(), "w");
                if (out != null) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    ok = true;
                }
            } catch (Exception e) {
                ok = false;
            } finally {
                try { if (in != null) in.close(); } catch (Exception e) { }
                try { if (out != null) out.close(); } catch (Exception e) { }
            }
            Toast.makeText(this, ok ? "Archivo guardado" : "No se pudo guardar el archivo", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
