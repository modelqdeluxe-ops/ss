package hn.hato.ganadero;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Archivos que arma la app (PDF del informe para el banco, certificados de lote): se guardan en la caché de
 * la app y se comparten con un content:// de ProveedorArchivos, o se guardan donde el usuario elija
 * (GuardarArchivo). La app los manda en base64 porque el puente de JavaScript solo pasa texto.
 */
public final class Archivos {
    static final String AUTORIDAD = "hn.hato.ganadero.archivos";
    private Archivos() {}

    /** Solo nombres simples: letras, números, punto, guion y guion bajo. */
    static String limpiar(String nombre) {
        String n = nombre == null ? "" : nombre.replaceAll("[^A-Za-z0-9._-]", "_");
        while (n.startsWith(".")) n = n.substring(1);
        if (n.isEmpty()) n = "rumentis";
        return n.length() > 120 ? n.substring(n.length() - 120) : n;
    }

    static File carpeta(Context c) {
        File d = new File(c.getCacheDir(), "compartir");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Escribe el archivo; devuelve el nombre limpio o null si falló. */
    static String escribir(Context c, String nombre, String b64) {
        String n = limpiar(nombre);
        FileOutputStream o = null;
        try {
            byte[] datos = Base64.decode(b64, Base64.DEFAULT);
            // los archivos viejos se borran: la caché no crece
            File[] viejos = carpeta(c).listFiles();
            if (viejos != null) for (File f : viejos) if (System.currentTimeMillis() - f.lastModified() > 86400000L) f.delete();
            o = new FileOutputStream(new File(carpeta(c), n));
            o.write(datos);
            return n;
        } catch (Exception e) {
            return null;
        } finally {
            if (o != null) try { o.close(); } catch (Exception e) { }
        }
    }

    static String tipo(String nombre, String mime) {
        if (mime != null && mime.length() > 0) return mime;
        int i = nombre.lastIndexOf('.');
        String t = i >= 0 ? MimeTypeMap.getSingleton().getMimeTypeFromExtension(nombre.substring(i + 1).toLowerCase()) : null;
        return t != null ? t : "application/octet-stream";
    }

    /** Abre el menú de compartir del teléfono (WhatsApp, correo, Drive...). */
    public static boolean compartir(final Activity a, String nombre, String b64, final String mime, final String titulo) {
        final String n = escribir(a, nombre, b64);
        if (n == null) return false;
        a.runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    Uri u = Uri.parse("content://" + AUTORIDAD + "/" + Uri.encode(n));
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType(tipo(n, mime));
                    i.putExtra(Intent.EXTRA_STREAM, u);
                    i.putExtra(Intent.EXTRA_SUBJECT, titulo == null ? n : titulo);
                    i.setClipData(ClipData.newRawUri(n, u));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent ch = Intent.createChooser(i, titulo == null || titulo.length() == 0 ? "Compartir" : titulo);
                    ch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    a.startActivity(ch);
                } catch (Exception e) {
                    Toast.makeText(a, "No se pudo compartir el archivo.", Toast.LENGTH_LONG).show();
                }
            }
        });
        return true;
    }

    /** Lo abre con la app de PDF del teléfono; si no hay ninguna, abre el menú de compartir. */
    public static boolean abrir(final Activity a, String nombre, String b64, final String mime) {
        final String n = escribir(a, nombre, b64);
        if (n == null) return false;
        a.runOnUiThread(new Runnable() {
            @Override public void run() {
                Uri u = Uri.parse("content://" + AUTORIDAD + "/" + Uri.encode(n));
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(u, tipo(n, mime));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    a.startActivity(i);
                } catch (Exception e) {
                    try {
                        Intent s = new Intent(Intent.ACTION_SEND);
                        s.setType(tipo(n, mime));
                        s.putExtra(Intent.EXTRA_STREAM, u);
                        s.setClipData(ClipData.newRawUri(n, u));
                        s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        Intent ch = Intent.createChooser(s, "Abrir con");
                        ch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        a.startActivity(ch);
                    } catch (Exception e2) {
                        Toast.makeText(a, "No hay una app para abrir el archivo.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        return true;
    }

    /** Pide al usuario dónde guardarlo (Descargas, Drive...) y lo copia ahí. */
    public static boolean guardar(final Activity a, String nombre, String b64, final String mime) {
        final String n = escribir(a, nombre, b64);
        if (n == null) return false;
        a.runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    Intent i = new Intent(a, GuardarArchivo.class);
                    i.putExtra("nombre", n);
                    i.putExtra("mime", tipo(n, mime));
                    a.startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(a, "No se pudo guardar el archivo.", Toast.LENGTH_LONG).show();
                }
            }
        });
        return true;
    }

}
