package net.tierrasfantasticas.tfclient.anim;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Punchy dentro de TF Client (1.3.69). Las animaciones en primera persona de Punchy Guys Studios van en el jar tal
 * cual (jar-in-jar, META-INF/jarjar), con su permiso por escrito (META-INF/licenses/punchy-PERMISO.txt). TF Client no
 * toca su código: cambia sus ajustes desde el pad (Efectos › ANIMACIONES) con la propia API de Punchy, la misma que usa
 * su pantalla de ajustes (PunchyConfig.snapshot → cambiar → applyAndSave → la velocidad), que los guarda en
 * config/punchy/punchy_config.json. Va por reflexión: si el jugador tiene Punchy suelto de otra versión (Forge usa ese),
 * lo que no exista no sale y nada revienta.
 */
public final class PunchyBridge {
    public static final String MOD_ID = "punchy";
    public static final String MODRINTH = "https://modrinth.com/mod/punchy-fpa";
    private static final String INSPECT_KEY = "key.punchy.inspect";

    private static Class<?> config, data, animations;
    private static boolean tried, ok, warned;

    private PunchyBridge() {}

    public static boolean available() {
        if (!tried) {
            tried = true;
            try {
                if (ModList.get() != null && ModList.get().isLoaded(MOD_ID)) {
                    config = Class.forName("punchy.config.PunchyConfig");
                    data = Class.forName("punchy.config.PunchyConfig$Data");
                    try {
                        animations = Class.forName("punchy.client.animation.PunchyAnimationManager");
                    } catch (Throwable ignored) {
                        animations = null;
                    }
                    ok = true;
                }
            } catch (Throwable e) {
                ok = false;
                TFClient.LOGGER.warn("TF Client: Punchy está cargado pero no se encuentran sus ajustes", e);
            }
        }
        return ok;
    }

    /** La versión de Punchy que se está usando (la de TF Client o la suelta), o "". */
    public static String version() {
        try {
            return ModList.get().getModContainerById(MOD_ID).map(c -> c.getModInfo().getVersion().toString()).orElse("");
        } catch (Throwable e) {
            return "";
        }
    }

    /** Una copia de los ajustes de ahora (o null si no hay Punchy). */
    public static Object read() {
        if (!available()) return null;
        try {
            return config.getMethod("snapshot").invoke(null);
        } catch (Throwable e) {
            warn(e);
            return null;
        }
    }

    /** Los ajustes de fábrica de Punchy (o null). */
    public static Object defaults() {
        if (!available()) return null;
        try {
            return data.getConstructor().newInstance();
        } catch (Throwable e) {
            warn(e);
            return null;
        }
    }

    public static boolean has(String field) {
        return field(field) != null;
    }

    public static boolean bool(Object snap, String field, boolean def) {
        Field f = field(field);
        try {
            return snap == null || f == null || f.getType() != boolean.class ? def : f.getBoolean(snap);
        } catch (Throwable e) {
            return def;
        }
    }

    public static float num(Object snap, String field, float def) {
        Field f = field(field);
        try {
            return snap == null || f == null || f.getType() != float.class ? def : f.getFloat(snap);
        } catch (Throwable e) {
            return def;
        }
    }

    public static void set(Object snap, String field, Object value) {
        Field f = field(field);
        if (snap == null || f == null) return;
        try {
            if (f.getType() == boolean.class) f.setBoolean(snap, (Boolean) value);
            else if (f.getType() == float.class) f.setFloat(snap, ((Number) value).floatValue());
        } catch (Throwable e) {
            warn(e);
        }
    }

    /**
     * Cambia los ajustes y los aplica al momento: lo mismo que hace la pantalla de Punchy al guardar (applyAndSave
     * guarda el archivo; la velocidad de las animaciones se le pasa aparte).
     */
    public static boolean write(Consumer<Object> edit) {
        Object snap = read();
        if (snap == null) return false;
        try {
            edit.accept(snap);
            config.getMethod("applyAndSave", data).invoke(null, snap);
            if (animations != null) {
                float speed = (Float) config.getMethod("getAnimationSpeed").invoke(null);
                animations.getMethod("setAnimationSpeed", float.class).invoke(null, speed);
            }
            return true;
        } catch (Throwable e) {
            warn(e);
            return false;
        }
    }

    /** La pantalla de ajustes de Punchy (lista negra, packs de animaciones...), o null. */
    public static Screen configScreen(Screen parent) {
        try {
            return ModList.get().getModContainerById(MOD_ID)
                    .flatMap(c -> ConfigScreenHandler.getScreenFactoryFor(c.getModInfo()))
                    .map(f -> f.apply(Minecraft.getInstance(), parent)).orElse(null);
        } catch (Throwable e) {
            warn(e);
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // La tecla de inspeccionar (Punchy la registra en la I): sin tecla, nunca se inspecciona
    // ---------------------------------------------------------------------------------------------------------------

    public static KeyMapping inspectKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return null;
        for (KeyMapping k : mc.options.keyMappings) if (INSPECT_KEY.equals(k.getName())) return k;
        return null;
    }

    /** Quita la tecla o le vuelve a poner la de fábrica, y lo guarda en las opciones (se ve en Controles). */
    public static void toggleInspect() {
        KeyMapping k = inspectKey();
        if (k == null) return;
        k.setKey(k.isUnbound() ? k.getDefaultKey() : InputConstants.UNKNOWN);
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static Field field(String name) {
        if (!available()) return null;
        try {
            return data.getField(name);
        } catch (Throwable e) {
            return null;
        }
    }

    private static void warn(Throwable e) {
        if (warned) return;
        warned = true;
        TFClient.LOGGER.warn("TF Client: no se pudieron cambiar los ajustes de Punchy", e);
    }
}
