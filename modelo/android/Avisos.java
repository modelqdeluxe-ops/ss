package hn.hato.ganadero;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.WebView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Avisos de Rumi en el teléfono. La app (JavaScript) arma una lista de avisos con su fecha y prioridad;
 * aquí se guarda y una alarma diaria muestra como máximo UN aviso al día, el más importante que toque.
 * Nada de avisos seguidos: si ya se avisó hoy, se espera a mañana. Se activan y desactivan en Configuración.
 */
public final class Avisos {
    static final String PREF = "rumi_avisos";
    static final String CANAL = "rumi";
    static String ir = null; // a dónde abrir la app al tocar un aviso o el widget

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    private static String hoy() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    /** Guarda la lista de avisos que arma la app: [{id,f,t,x,p,ir}] */
    public static void guardar(Context c, String json) {
        sp(c).edit().putString("cola", json == null ? "[]" : json).apply();
        programar(c);
    }

    public static void activar(Context c, boolean si, int hora) {
        sp(c).edit().putBoolean("activo", si).putInt("hora", hora < 0 || hora > 23 ? 7 : hora).apply();
        programar(c);
    }

    public static boolean permiso(Context c) {
        if (Build.VERSION.SDK_INT < 33) return true;
        return c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }

    public static void pedirPermiso(Activity a) {
        if (Build.VERSION.SDK_INT >= 33 && !permiso(a)) {
            try { a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 16); } catch (Exception e) { }
        }
    }

    /** "si", "no" o "sin-permiso" */
    public static String estado(Context c) {
        if (!sp(c).getBoolean("activo", false)) return "no";
        return permiso(c) ? "si" : "sin-permiso";
    }

    public static int hora(Context c) { return sp(c).getInt("hora", 7); }

    private static PendingIntent alarma(Context c) {
        Intent i = new Intent(c, AvisoReceiver.class);
        i.setAction("hn.hato.ganadero.AVISO");
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Alarma diaria a la hora elegida (inexacta: el teléfono la agrupa con otras y no gasta batería). */
    public static void programar(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = alarma(c);
        am.cancel(pi);
        if (!sp(c).getBoolean("activo", false)) return;
        Calendar k = Calendar.getInstance();
        k.set(Calendar.HOUR_OF_DAY, hora(c));
        k.set(Calendar.MINUTE, 5);
        k.set(Calendar.SECOND, 0);
        if (k.getTimeInMillis() <= System.currentTimeMillis()) k.add(Calendar.DAY_OF_MONTH, 1);
        try { am.setInexactRepeating(AlarmManager.RTC_WAKEUP, k.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi); } catch (Exception e) { }
    }

    private static void canal(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CANAL) != null) return;
        NotificationChannel ch = new NotificationChannel(CANAL, "Avisos de Rumi", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Un aviso al día, solo lo importante de tu engorde");
        nm.createNotificationChannel(ch);
    }

    /** Lo llama la alarma: muestra el aviso más importante que toque hoy, si no se ha avisado hoy. */
    public static void revisar(Context c) {
        SharedPreferences p = sp(c);
        if (!p.getBoolean("activo", false) || !permiso(c)) return;
        String h = hoy();
        if (h.equals(p.getString("ultimo", ""))) return;
        try {
            JSONArray cola = new JSONArray(p.getString("cola", "[]"));
            JSONObject env = new JSONObject(p.getString("enviados", "{}"));
            JSONObject mejor = null;
            for (int i = 0; i < cola.length(); i++) {
                JSONObject o = cola.getJSONObject(i);
                String f = o.optString("f", h);
                if (f.compareTo(h) > 0) continue;               // todavía no toca
                String hasta = o.optString("hasta", "");
                if (hasta.length() > 0 && hasta.compareTo(h) < 0) continue; // ya pasó
                if (env.has(o.optString("id"))) continue;       // ya se avisó
                if (mejor == null || o.optDouble("p", 0) > mejor.optDouble("p", 0)) mejor = o;
            }
            if (mejor == null) return;
            mostrar(c, mejor.optString("t"), mejor.optString("x"), mejor.optString("ir", "#hoy"));
            env.put(mejor.optString("id"), h);
            // se olvidan los enviados de hace más de 60 días
            JSONObject limpio = new JSONObject();
            java.util.Iterator<String> it = env.keys();
            Calendar lim = Calendar.getInstance(); lim.add(Calendar.DAY_OF_MONTH, -60);
            String l = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(lim.getTime());
            while (it.hasNext()) { String k = it.next(); String v = env.optString(k); if (v.compareTo(l) >= 0) limpio.put(k, v); }
            p.edit().putString("enviados", limpio.toString()).putString("ultimo", h).apply();
        } catch (Exception e) { }
    }

    public static void mostrar(Context c, String titulo, String texto, String destino) {
        if (!permiso(c)) return;
        canal(c);
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra("ir", destino == null ? "#hoy" : destino);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 2, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CANAL) : new Notification.Builder(c);
        int ic = c.getResources().getIdentifier("ic_aviso", "drawable", c.getPackageName());
        b.setSmallIcon(ic != 0 ? ic : android.R.drawable.ic_dialog_info)
         .setContentTitle(titulo)
         .setContentText(texto)
         .setStyle(new Notification.BigTextStyle().bigText(texto))
         .setContentIntent(pi)
         .setAutoCancel(true);
        if (Build.VERSION.SDK_INT >= 21) b.setColor(0xFFF0BF33);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) try { nm.notify(7, b.build()); } catch (Exception e) { }
    }

    /** Datos del widget que manda la app y actualización de los widgets en la pantalla de inicio. */
    public static void widget(Context c, String json) {
        c.getSharedPreferences("rumi_widget", Context.MODE_PRIVATE).edit().putString("datos", json == null ? "{}" : json).apply();
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, RumiWidget.class));
            if (ids != null && ids.length > 0) RumiWidget.pintar(c, m, ids);
        } catch (Exception e) { }
    }

    /** Al abrir la app desde un aviso o el widget: guarda a dónde ir y avisa a la página si ya está cargada. */
    public static void deIntent(Activity a, final WebView w) {
        try {
            Intent i = a.getIntent();
            String d = i == null ? null : i.getStringExtra("ir");
            if (d == null) return;
            i.removeExtra("ir");
            ir = d;
            if (w != null) w.post(new Runnable() { public void run() { try { w.evaluateJavascript("window.avisoIr&&avisoIr()", null); } catch (Exception e) { } } });
        } catch (Exception e) { }
    }

    public static String tomarIr() { String d = ir; ir = null; return d == null ? "" : d; }
}
