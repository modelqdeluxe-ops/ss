package hn.hato.ganadero;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import org.json.JSONObject;

/** Widget de la pantalla de inicio: cabezas, lotes, tareas de hoy y el aviso más importante de Rumi. */
public final class RumiWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { pintar(c, m, ids); }

    private static int id(Context c, String n) { return c.getResources().getIdentifier(n, "id", c.getPackageName()); }

    private static PendingIntent abrir(Context c, String destino, int req) {
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra("ir", destino);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 100 + req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void pintar(Context c, AppWidgetManager m, int[] ids) {
        int lay = c.getResources().getIdentifier("rumi_widget", "layout", c.getPackageName());
        if (lay == 0) return;
        JSONObject d;
        try { d = new JSONObject(c.getSharedPreferences("rumi_widget", Context.MODE_PRIVATE).getString("datos", "{}")); } catch (Exception e) { d = new JSONObject(); }
        for (int w : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), lay);
            v.setTextViewText(id(c, "w_titulo"), d.optString("finca", "Rumentis"));
            v.setTextViewText(id(c, "w_grande"), d.optString("grande", "Abre Rumentis"));
            v.setTextViewText(id(c, "w_sub"), d.optString("sub", ""));
            v.setTextViewText(id(c, "w_tareas"), d.optString("tareas", ""));
            v.setTextViewText(id(c, "w_rumi"), d.optString("rumi", ""));
            v.setTextViewText(id(c, "w_act"), d.optString("act", ""));
            v.setTextViewText(id(c, "w_anotar"), d.optString("anotar", "+ Anotar"));
            v.setOnClickPendingIntent(id(c, "w_raiz"), abrir(c, "#hoy", 0));
            v.setOnClickPendingIntent(id(c, "w_anotar"), abrir(c, "#registrar", 1));
            v.setOnClickPendingIntent(id(c, "w_rumi"), abrir(c, d.optString("rumiIr", "#analisis"), 2));
            try { m.updateAppWidget(w, v); } catch (Exception e) { }
        }
    }
}
