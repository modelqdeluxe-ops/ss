package net.tierrasfantasticas.tfclient.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Título especial de los menús con fondo propio. El servidor manda un título traducible «tfclient.gui.menu» con el
 * fondo y los dos textos (cinta y cartel); el TF Client lo reconoce y dibuja el fondo con esos textos en vez del título.
 */
public final class TFMenuStyle {
    public static final String KEY = "tfclient.gui.menu";

    private TFMenuStyle() {}

    public static Component title(String background, String ribbon, String sign) {
        return Component.translatable(KEY, background, ribbon, sign);
    }

    /** {fondo, cinta, cartel} o null si no es un menú con fondo propio. */
    public static String[] parse(Component title) {
        if (!(title.getContents() instanceof TranslatableContents tr) || !KEY.equals(tr.getKey())) return null;
        Object[] args = tr.getArgs();
        String[] out = new String[3];
        for (int i = 0; i < 3; i++) {
            Object arg = i < args.length ? args[i] : "";
            out[i] = arg instanceof Component c ? c.getString() : String.valueOf(arg);
        }
        return out[0].matches("[a-z0-9_]{1,40}") ? out : null;
    }
}
