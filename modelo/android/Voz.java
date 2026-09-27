package hn.hato.ganadero;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.Locale;
import java.util.Set;

/** Lectura en voz alta con la mejor voz en español instalada en el teléfono. */
public final class Voz implements TextToSpeech.OnInitListener {
    private static Voz uno;
    private final Context ctx;
    private TextToSpeech tts;
    private boolean listo;
    private String pendiente, pendVoz;
    private String elegida;

    private Voz(Context c) { ctx = c.getApplicationContext(); tts = new TextToSpeech(ctx, this); }

    private static synchronized Voz de(Context c) { if (uno == null) uno = new Voz(c); return uno; }

    @Override public void onInit(int estado) {
        synchronized (this) {
            listo = estado == TextToSpeech.SUCCESS;
            if (listo) {
                try { tts.setLanguage(new Locale("es", "US")); } catch (Exception e) { }
                if (pendiente != null) { String t = pendiente, v = pendVoz; pendiente = null; decir(t, v); }
            }
        }
    }

    /** Puntaje de una voz: calidad, que esté instalada, acento latinoamericano. */
    private static int puntaje(Voice v) {
        Locale l = v.getLocale();
        if (l == null || !"es".equals(l.getLanguage())) return -1;
        Set<String> f = v.getFeatures();
        if (f != null && f.contains("notInstalled")) return -1;
        int p = v.getQuality();
        String c = l.getCountry();
        if ("US".equals(c) || "MX".equals(c) || "419".equals(c)) p += 60;
        else if ("ES".equals(c)) p -= 20;
        else if (c != null && c.length() > 0) p += 30;
        if (v.isNetworkConnectionRequired()) p -= 40; // funciona sin internet
        String n = v.getName() == null ? "" : v.getName().toLowerCase(Locale.ROOT);
        if (n.contains("local")) p += 10;
        return p;
    }

    private Voice buscar(String nombre) {
        Set<Voice> vs;
        try { vs = tts.getVoices(); } catch (Exception e) { return null; }
        if (vs == null) return null;
        Voice mejor = null; int mp = -1;
        for (Voice v : vs) {
            if (nombre != null && nombre.equals(v.getName())) return v;
            int p = puntaje(v);
            if (p > mp) { mp = p; mejor = v; }
        }
        return mejor;
    }

    private synchronized void decir(String texto, String nombre) {
        if (!listo) { pendiente = texto; pendVoz = nombre; return; }
        try {
            Voice v = buscar(nombre);
            if (v != null && (elegida == null || !elegida.equals(v.getName()))) { tts.setVoice(v); elegida = v.getName(); }
            tts.setSpeechRate(1.0f);
            tts.setPitch(1.0f);
            int max = 3900;
            try { max = Math.min(3900, TextToSpeech.getMaxSpeechInputLength()); } catch (Exception e) { }
            String resto = texto;
            int cola = TextToSpeech.QUEUE_FLUSH, i = 0;
            while (resto.length() > 0) {
                String parte;
                if (resto.length() <= max) { parte = resto; resto = ""; }
                else {
                    int corte = resto.lastIndexOf(". ", max);
                    if (corte < max / 2) corte = resto.lastIndexOf(' ', max);
                    if (corte <= 0) corte = max;
                    parte = resto.substring(0, corte + 1); resto = resto.substring(corte + 1).trim();
                }
                Bundle b = new Bundle();
                tts.speak(parte, cola, b, "rumi" + (i++));
                cola = TextToSpeech.QUEUE_ADD;
            }
        } catch (Exception e) { }
    }

    public static void hablar(Context c, String texto, String voz) {
        if (texto == null) return;
        de(c).decir(texto, voz == null || voz.length() == 0 ? null : voz);
    }

    public static void callar(Context c) {
        Voz v = de(c);
        synchronized (v) { v.pendiente = null; try { v.tts.stop(); } catch (Exception e) { } }
    }

    public static boolean hablando(Context c) {
        try { return de(c).tts.isSpeaking(); } catch (Exception e) { return false; }
    }

    /** Voces en español instaladas, ordenadas de mejor a peor, como JSON. */
    public static String lista(Context c) {
        Voz z = de(c);
        StringBuilder sb = new StringBuilder("{\"listo\":").append(z.listo).append(",\"voces\":[");
        try {
            Set<Voice> vs = z.tts.getVoices();
            java.util.ArrayList<Voice> ls = new java.util.ArrayList<>();
            if (vs != null) for (Voice v : vs) if (puntaje(v) >= 0) ls.add(v);
            java.util.Collections.sort(ls, (a, b) -> puntaje(b) - puntaje(a));
            boolean primero = true;
            for (Voice v : ls) {
                if (!primero) sb.append(',');
                primero = false;
                sb.append("{\"n\":").append(org.json.JSONObject.quote(v.getName()))
                  .append(",\"p\":").append(org.json.JSONObject.quote(v.getLocale().getCountry()))
                  .append(",\"q\":").append(v.getQuality())
                  .append(",\"red\":").append(v.isNetworkConnectionRequired())
                  .append('}');
            }
        } catch (Exception e) { }
        sb.append("],\"motor\":");
        try { sb.append(org.json.JSONObject.quote(String.valueOf(z.tts.getDefaultEngine()))); } catch (Exception e) { sb.append("\"\""); }
        return sb.append('}').toString();
    }

    /** Abre los ajustes de texto a voz del teléfono (para instalar voces de mejor calidad). */
    public static void ajustes(Context c) {
        try {
            Intent i = new Intent("com.android.settings.TTS_SETTINGS");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            try {
                Intent i = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
            } catch (Exception e2) { }
        }
    }
}
