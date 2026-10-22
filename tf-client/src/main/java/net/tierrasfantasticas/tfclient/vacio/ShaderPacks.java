package net.tierrasfantasticas.tfclient.vacio;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * El Void con un paquete de shaders (Oculus, el Iris de Forge). 1.3.89, el dueño: «con shaders el Void se jode
 * completamente, con cualquier shader» (su captura: cielo azul con nubes).
 *
 * <p>Oculus elige qué programas del paquete usa cada dimensión por su id (Iris.getCurrentDimension = el id del mundo,
 * no sus «efectos»): world0 para el mundo normal, world-1 el Nether, world1 el End, y cualquier otra, si el paquete
 * no la nombra en su dimension.properties, la del mundo normal (sol, cielo azul, nubes). Aquí, al estar en el Void, se
 * le dice al paquete cargado que tfclient:void use la carpeta de su End (la misma que minecraft:the_end) y se rehace
 * su pipeline: el Void se ve como el End de ese paquete (oscuro, sin sol ni nubes) y el cielo lo dibuja él
 * ({@link VoidEffects} deja el cielo del End de Minecraft, que el paquete sustituye por el suyo).
 *
 * <p>Por reflexión (Oculus no es dependencia: si no está, no pasa nada): 1.6.x tiene sus clases en net.coderbot.iris y
 * 1.7/1.8 en net.irisshaders.iris, con los mismos nombres (Iris.getCurrentPack, ShaderPack.dimensionMap y overrides,
 * DimensionId.END, NamespacedId, PipelineManager.destroyPipeline; comprobado con javap en los jar de Modrinth). Si
 * algo no cuadra, se avisa una vez en el registro y se deja como está.
 */
@OnlyIn(Dist.CLIENT)
public final class ShaderPacks {
    private static boolean looked, failed;
    private static Object api;
    private static Method inUse, shadowPass, getCurrentPack, getPipelineManager, destroyPipeline;
    private static Field dimensionMap, overrides, endId;
    private static Constructor<?> namespacedId;
    /** El paquete al que ya se le dijo lo del Void (Oculus crea otro al cambiar de paquete o recargar). */
    private static Object patched;
    /** Lo último que pasó (para la prueba en el juego). */
    static String status = "sin Oculus";

    private ShaderPacks() {}

    private static void lookup() {
        if (looked) return;
        looked = true;
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            inUse = apiClass.getMethod("isShaderPackInUse");
            shadowPass = apiClass.getMethod("isRenderingShadowPass");
            status = "Oculus sin reconocer";
        } catch (Throwable t) {
            return; // sin Oculus
        }
        for (String base : new String[] {"net.irisshaders.iris", "net.coderbot.iris"}) {
            try {
                Class<?> iris = Class.forName(base + ".Iris");
                Class<?> pack = Class.forName(base + ".shaderpack.ShaderPack");
                Class<?> pm = Class.forName(base + ".pipeline.PipelineManager");
                Class<?> ns = Class.forName(base + ".shaderpack.materialmap.NamespacedId");
                getCurrentPack = iris.getMethod("getCurrentPack");
                getPipelineManager = iris.getMethod("getPipelineManager");
                destroyPipeline = pm.getMethod("destroyPipeline");
                dimensionMap = pack.getDeclaredField("dimensionMap");
                dimensionMap.setAccessible(true);
                overrides = pack.getDeclaredField("overrides");
                overrides.setAccessible(true);
                endId = Class.forName(base + ".shaderpack.DimensionId").getField("END");
                namespacedId = ns.getConstructor(String.class, String.class);
                TFClient.LOGGER.info("TF Void: Oculus encontrado ({})", base);
                status = "Oculus (" + base + ")";
                return;
            } catch (Throwable ignored) {
                getCurrentPack = null;
            }
        }
        failed = true;
        TFClient.LOGGER.warn("TF Void: Oculus está, pero no se reconocen sus clases: el Void usará los programas del mundo normal del paquete de shaders");
    }

    /** ¿Hay un paquete de shaders puesto? */
    public static boolean inUse() {
        lookup();
        if (api == null) return false;
        try {
            return (Boolean) inUse.invoke(api);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean blendLooked, blendWarned;
    private static Method blendLocked, restoreBlend;
    /** Para la prueba en el juego: cuántas veces estaba bloqueada al pintar un objeto, y sin el arreglo. */
    public static volatile int blendUnlocks;
    public static volatile boolean testNoUnlock;

    /**
     * Objetos encantados como siluetas moradas en el inventario con shaders (1.3.114, el dueño con Oculus + Photon; su
     * captura: los encantados, enteros de morado oscuro 63,30,102). Es el brillo de encantamiento pintado sin mezcla: Oculus
     * «bloquea» la mezcla (BlendModeStorage) mientras usa un programa del paquete que la cambia, y si ese bloqueo sigue
     * puesto al pintar la interfaz, el enableBlend del brillo se ignora y el brillo tapa el objeto. Antes de pintar cada
     * objeto en la interfaz, si sigue bloqueada, se suelta (lo mismo que hace Oculus al acabar con ese programa). Sin
     * Oculus o sin paquete no hace nada.
     */
    public static void unlockBlendForGui() {
        if (!blendLooked) {
            blendLooked = true;
            lookup();
            if (api == null) return;
            for (String base : new String[] {"net.irisshaders.iris", "net.coderbot.iris"}) {
                try {
                    Class<?> c = Class.forName(base + ".gl.blending.BlendModeStorage");
                    blendLocked = c.getMethod("isBlendLocked");
                    restoreBlend = c.getMethod("restoreBlend");
                    break;
                } catch (Throwable ignored) {
                    blendLocked = null;
                }
            }
        }
        if (blendLocked == null) return;
        try {
            if (!(Boolean) blendLocked.invoke(null)) return;
            blendUnlocks++;
            if (testNoUnlock) return;
            restoreBlend.invoke(null);
            if (!blendWarned) {
                blendWarned = true;
                TFClient.LOGGER.info("TF Client: Oculus dejó la mezcla bloqueada al pintar la interfaz; se suelta para que el brillo de "
                        + "encantamiento no tape los objetos");
            }
        } catch (Throwable t) {
            blendLocked = null;
        }
    }

    /** ¿Está dibujando ahora la pasada de sombras del paquete? (los jefes y la grieta se dibujan también en ella) */
    public static boolean shadowPass() {
        if (api == null || shadowPass == null) return false;
        try {
            return (Boolean) shadowPass.invoke(api);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Cada tick estando en el Void: que el paquete cargado use su End para esta dimensión. */
    static void tick() {
        lookup();
        if (failed || getCurrentPack == null) return;
        try {
            Optional<?> current = (Optional<?>) getCurrentPack.invoke(null);
            if (current == null || current.isEmpty()) return;
            Object pack = current.get();
            if (pack == patched) return;
            patched = pack;
            @SuppressWarnings("unchecked")
            Map<Object, String> map = (Map<Object, String>) dimensionMap.get(pack);
            String folder = map == null ? null : map.get(endId.get(null));
            if (folder == null) {
                TFClient.LOGGER.info("TF Void: el paquete de shaders no tiene End: se queda con sus programas de siempre");
                status = "el paquete no tiene End";
                return;
            }
            Object ours = namespacedId.newInstance(TFClient.MOD_ID, "void");
            if (folder.equals(map.get(ours))) return;
            try {
                map.put(ours, folder);
            } catch (UnsupportedOperationException e) {
                Map<Object, String> copy = new HashMap<>(map);
                copy.put(ours, folder);
                dimensionMap.set(pack, copy);
            }
            ((Map<?, ?>) overrides.get(pack)).remove(ours);
            // El pipeline del Void (si ya se hizo con los programas del mundo normal) se rehace en el siguiente fotograma
            Object pm = getPipelineManager.invoke(null);
            if (pm != null) destroyPipeline.invoke(pm);
            TFClient.LOGGER.info("TF Void: el paquete de shaders usa su End («{}») en el Void", folder);
            status = "el Void usa «" + folder + "» del paquete";
        } catch (Throwable t) {
            failed = true;
            status = "fallo: " + t;
            TFClient.LOGGER.warn("TF Void: no se pudo decir al paquete de shaders que el Void es como el End", t);
        }
    }
}
