package hn.hato.ganadero;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/* Puentes extra para la WebView (se llama desde MainActivity.onCreate, junto al puente "Android"):
   - "Pagos": Google Play Billing. La clase Pagos y la biblioteca de Google van en un segundo dex que se agrega al
     compilar en GitHub Actions (scripts/pagos.sh). Si no está (compilación local), no hay puente y la app lo sabe.
   - "Cripto": comprueba la firma RSA de una compra de Google Play con la llave pública de la app (SHA1withRSA),
     para que la app del vaquero sepa que su licencia salió de una compra real.
   - "Recibido": el archivo .rumentis que abrió el usuario (tocándolo en WhatsApp o compartiéndolo a la app). deIntent
     lo lee del Intent (onCreate y onNewIntent) y avisa a la página, que lo toma con Recibido.tomar(). Si el archivo
     es para la otra app (un .campo que llegó a Rumentis, o al revés), Recibido.pasar() se lo entrega a esa app. */
public class Enlace {
    public static void registrar(Activity a, WebView w) {
        try {
            Class<?> c = Class.forName("hn.hato.ganadero.Pagos");
            Object p = c.getConstructor(Activity.class, WebView.class).newInstance(a, w);
            w.addJavascriptInterface(p, "Pagos");
        } catch (Throwable e) {
            // sin biblioteca de pagos
        }
        try {
            w.addJavascriptInterface(new Cripto(), "Cripto");
        } catch (Throwable e) {
        }
        try {
            w.addJavascriptInterface(new Recibido(a), "Recibido");
        } catch (Throwable e) {
        }
    }

    private static volatile String recibido = null;
    private static final int MAXIMO = 16 * 1024 * 1024;

    /** Si la app se abrió con un archivo (VIEW) o se le compartió uno (SEND), lo guarda para la página. */
    public static void deIntent(Activity a, final WebView w) {
        try {
            Intent i = a.getIntent();
            if (i == null) return;
            String ac = i.getAction();
            Uri u = null;
            if (Intent.ACTION_VIEW.equals(ac)) u = i.getData();
            else if (Intent.ACTION_SEND.equals(ac)) {
                Parcelable p = i.getParcelableExtra(Intent.EXTRA_STREAM);
                if (p instanceof Uri) u = (Uri) p;
                else if (i.getClipData() != null && i.getClipData().getItemCount() > 0) u = i.getClipData().getItemAt(0).getUri();
            }
            if (u == null) return;
            // que no se vuelva a leer al girar la pantalla o volver a la app
            a.setIntent(new Intent(a, a.getClass()));
            InputStream in = a.getContentResolver().openInputStream(u);
            if (in == null) return;
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            try {
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0) {
                    o.write(b, 0, n);
                    if (o.size() > MAXIMO) return;
                }
            } finally {
                in.close();
            }
            recibido = Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
            if (w != null) w.post(new Runnable() { public void run() { try { w.evaluateJavascript("window.archivoRecibido&&archivoRecibido()", null); } catch (Throwable e) { } } });
        } catch (Throwable e) {
        }
    }

    /** La otra app del equipo: hn.hato.ganadero[.prueba] <-> hn.hato.ganadero.vaquero[.prueba]. */
    static String otraApp(String p) {
        String base = "hn.hato.ganadero";
        if (p.startsWith(base + ".vaquero")) return base + p.substring((base + ".vaquero").length());
        if (p.startsWith(base)) return base + ".vaquero" + p.substring(base.length());
        return null;
    }

    public static class Recibido {
        private final Activity a;

        Recibido(Activity a) {
            this.a = a;
        }

        /** Entrega el archivo a la otra app del equipo; false si no está instalada. */
        @JavascriptInterface
        public boolean pasar(String b64) {
            try {
                String otra = otraApp(a.getPackageName());
                if (otra == null) return false;
                boolean aCampo = otra.contains(".vaquero");
                String n = Archivos.escribir(a, aCampo ? "equipo.campo" : "equipo.rumentis", b64);
                if (n == null) return false;
                Uri u = Uri.parse("content://" + a.getPackageName() + ".archivos/" + Uri.encode(n));
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(u, Archivos.tipo(n, null));
                i.setPackage(otra);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                if (i.resolveActivity(a.getPackageManager()) == null) return false;
                a.startActivity(i);
                return true;
            } catch (Throwable e) {
                return false;
            }
        }

        @JavascriptInterface
        public String tomar() {
            String r = recibido;
            recibido = null;
            return r == null ? "" : r;
        }
    }

    public static boolean verificarRsa(String llave, String datos, String firma) {
        try {
            byte[] k = Base64.decode(llave, Base64.DEFAULT);
            PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(k));
            Signature s = Signature.getInstance("SHA1withRSA");
            s.initVerify(pub);
            s.update(datos.getBytes("UTF-8"));
            return s.verify(Base64.decode(firma, Base64.DEFAULT));
        } catch (Throwable e) {
            return false;
        }
    }

    public static class Cripto {
        @JavascriptInterface
        public boolean verificar(String llave, String datos, String firma) {
            return verificarRsa(llave, datos, firma);
        }
    }
}
