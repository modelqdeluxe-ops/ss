package hn.hato.ganadero;

import android.app.Activity;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/* Puentes extra para la WebView (se llama desde MainActivity.onCreate, junto al puente "Android"):
   - "Pagos": Google Play Billing. La clase Pagos y la biblioteca de Google van en un segundo dex que se agrega al
     compilar en GitHub Actions (scripts/pagos.sh). Si no está (compilación local), no hay puente y la app lo sabe.
   - "Cripto": comprueba la firma RSA de una compra de Google Play con la llave pública de la app (SHA1withRSA),
     para que la app del vaquero sepa que su licencia salió de una compra real. */
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
