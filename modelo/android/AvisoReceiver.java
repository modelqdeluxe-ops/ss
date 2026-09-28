package hn.hato.ganadero;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarma diaria de avisos y reprogramación al encender el teléfono. */
public final class AvisoReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        if ("android.intent.action.BOOT_COMPLETED".equals(a) || "android.intent.action.MY_PACKAGE_REPLACED".equals(a)) { Avisos.programar(c); return; }
        Avisos.revisar(c);
    }
}
