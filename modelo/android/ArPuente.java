package hn.hato.ganadero;

import android.app.Activity;
import android.content.Intent;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.google.ar.core.ArCoreApk;

import org.json.JSONObject;

import java.lang.ref.WeakReference;

/* Puente "Ar" de Rumentis Beta: la medición con ARCore (la distancia real al animal con la profundidad de la cámara y
   el suelo que reconoce ARCore). Lo registra Enlace si la clase está en la app: la beta se arma con Gradle
   (scripts/pagos.sh con DONANTE=ar), que trae com.google.ar:core.
   JS -> estado(): "listo" | "instalar" (falta Servicios de Google Play para RA; se instala al abrir) | "revisando" | "no"
         abrir(opciones JSON): abre ArActivity (pasos, textos, orientación)
   Java -> window.arResultado(json) con las capturas (ver ArActivity) o {cancelado:true} / {error:"..."} */
public class ArPuente {
    private static WeakReference<WebView> web = new WeakReference<WebView>(null);
    private final Activity act;

    public ArPuente(Activity a, WebView w) {
        act = a;
        web = new WeakReference<WebView>(w);
    }

    @JavascriptInterface
    public String estado() {
        try {
            ArCoreApk.Availability av = ArCoreApk.getInstance().checkAvailability(act);
            if (av.isTransient()) return "revisando";
            if (av == ArCoreApk.Availability.SUPPORTED_INSTALLED) return "listo";
            if (av == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD || av == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED) return "instalar";
            return "no";
        } catch (Throwable e) {
            return "no";
        }
    }

    @JavascriptInterface
    public void abrir(final String opciones) {
        act.runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    Intent i = new Intent(act, ArActivity.class);
                    i.putExtra("o", opciones == null ? "{}" : opciones);
                    act.startActivity(i);
                } catch (Throwable e) {
                    entregar("{\"error\":\"abrir\"}");
                }
            }
        });
    }

    /** El resultado a la página (en el hilo de la WebView). */
    static void entregar(final String json) {
        final WebView w = web.get();
        if (w == null) return;
        w.post(new Runnable() {
            @Override public void run() {
                try {
                    w.evaluateJavascript("window.arResultado&&window.arResultado(" + JSONObject.quote(json) + ")", null);
                } catch (Throwable e) {
                }
            }
        });
    }
}
