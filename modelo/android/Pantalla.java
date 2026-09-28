package hn.hato.ganadero;

import android.app.Activity;
import android.os.Build;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;
import java.lang.reflect.Method;

/* La app va a la tasa de refresco más alta del teléfono (90, 120, 144 Hz…): muchos teléfonos dejan las apps a 60 Hz
   si no lo piden. Se elige el modo de pantalla con la misma resolución y más cuadros por segundo, y se le pide a la
   ventana. Se llama al crear la actividad y al volver a ella (algunos fabricantes lo reinician).
   Display.Mode y preferredDisplayModeId (Android 6+) se usan por reflexión para compilar con el SDK de siempre. */
public class Pantalla {
    public static void maxima(Activity a) {
        try {
            Window w = a.getWindow();
            Display d = a.getWindowManager().getDefaultDisplay();
            if (w == null || d == null) return;
            WindowManager.LayoutParams lp = w.getAttributes();
            float tasa = d.getRefreshRate();
            int modo = -1;
            if (Build.VERSION.SDK_INT >= 23) {
                Object actual = Display.class.getMethod("getMode").invoke(d);
                Object[] modos = (Object[]) Display.class.getMethod("getSupportedModes").invoke(d);
                Class<?> M = actual.getClass();
                Method ancho = M.getMethod("getPhysicalWidth"), alto = M.getMethod("getPhysicalHeight"), hz = M.getMethod("getRefreshRate"), id = M.getMethod("getModeId");
                int aw = (Integer) ancho.invoke(actual), ah = (Integer) alto.invoke(actual);
                Object mejor = actual;
                for (Object m : modos) {
                    if ((Integer) ancho.invoke(m) == aw && (Integer) alto.invoke(m) == ah
                            && (Float) hz.invoke(m) > (Float) hz.invoke(mejor) + 0.5f) mejor = m;
                }
                tasa = (Float) hz.invoke(mejor);
                modo = (Integer) id.invoke(mejor);
            } else {
                for (float f : d.getSupportedRefreshRates()) if (f > tasa) tasa = f;
            }
            boolean cambia = lp.preferredRefreshRate != tasa;
            lp.preferredRefreshRate = tasa;
            if (modo >= 0) {
                java.lang.reflect.Field campo = WindowManager.LayoutParams.class.getField("preferredDisplayModeId");
                if (campo.getInt(lp) != modo) { campo.setInt(lp, modo); cambia = true; }
            }
            if (cambia) w.setAttributes(lp);
        } catch (Throwable e) {
            // si el teléfono no deja elegir, sigue con su tasa normal
        }
    }
}
