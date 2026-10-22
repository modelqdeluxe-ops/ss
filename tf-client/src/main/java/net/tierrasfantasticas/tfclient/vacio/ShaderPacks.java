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

    private static boolean blendLooked, blendWarned, glLooked;
    private static Method blendLocked, restoreBlend;
    private static Field glBlend, glBlendMode, glBlendOn;
    private static Field[] glBlendFunc;
    private static int glChecks;
    /** Para la prueba en el juego: cuántas veces estaba bloqueada o desajustada al pintar un objeto, y sin el arreglo. */
    public static volatile int blendUnlocks, blendDesyncs;
    public static volatile boolean testNoUnlock;

    /**
     * Objetos encantados como siluetas moradas en el inventario con shaders (1.3.114, el dueño con Oculus + Photon; su
     * captura: los encantados, enteros de morado oscuro 63,30,102). Es el brillo de encantamiento pintado sin su mezcla
     * (la suya suma: SRC_COLOR, ONE); con ONE, ONE_MINUS_SRC_ALPHA y el alfa 1 de su textura, el brillo tapa el objeto.
     * Photon pide a Oculus otra mezcla en casi todos sus programas, también por búfer («blend.gbuffers_X.colortex13 =
     * ONE ONE_MINUS_SRC_ALPHA…»), y Oculus la pone directamente en OpenGL (glBlendFunci/glEnablei) sin que se entere la
     * caché de Minecraft (GlStateManager); al acabar «la deja como estaba» por esa caché, que no ha cambiado, así que no
     * hace nada y el búfer 0 se queda con la mezcla del paquete. Después, el brillo pide la suya, la caché dice que ya está
     * y Minecraft no la pone. Antes de pintar cada objeto en la interfaz con un paquete puesto: (1) si Oculus sigue con la
     * mezcla bloqueada (BlendModeStorage), se suelta; (2) OpenGL vuelve a lo que cree la caché (activada o no y la
     * función, para todos los búferes). La primera vez que no coincidían se apunta en el registro. Sin paquete, nada.
     */
    public static void fixBlendForGui() {
        if (!inUse()) return;
        unlockBlend();
        if (!testNoUnlock) syncBlend();
    }

    private static void unlockBlend() {
        if (!blendLooked) {
            blendLooked = true;
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

    /** La caché de la mezcla de Minecraft (GlStateManager.BLEND), buscada por tipo: los nombres cambian fuera del entorno. */
    private static void lookupGl() {
        glLooked = true;
        try {
            for (Field f : com.mojang.blaze3d.platform.GlStateManager.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType().getName().endsWith("GlStateManager$BlendState")) glBlend = f;
            }
            glBlend.setAccessible(true);
            java.util.List<Field> ints = new java.util.ArrayList<>();
            for (Field f : glBlend.getType().getDeclaredFields()) {
                if (f.getType().getName().endsWith("GlStateManager$BooleanState")) glBlendMode = f;
                else if (f.getType() == int.class) ints.add(f);
            }
            glBlendMode.setAccessible(true);
            for (Field f : glBlendMode.getType().getDeclaredFields()) if (f.getType() == boolean.class) glBlendOn = f;
            glBlendOn.setAccessible(true);
            if (ints.size() == 4) {
                for (Field f : ints) f.setAccessible(true);
                // el orden de los cuatro (srcRgb, dstRgb, srcAlpha, dstAlpha) se comprueba poniendo una mezcla con los
                // cuatro distintos y viendo dónde cae cada uno; luego se deja la que había
                Object state = glBlend.get(null);
                int[] before = new int[4], probe = new int[4], want = {770, 771, 1, 0};
                for (int i = 0; i < 4; i++) before[i] = ints.get(i).getInt(state);
                com.mojang.blaze3d.platform.GlStateManager._blendFuncSeparate(want[0], want[1], want[2], want[3]);
                for (int i = 0; i < 4; i++) probe[i] = ints.get(i).getInt(state);
                Field[] order = new Field[4];
                int[] at = new int[4];
                for (int k = 0; k < 4; k++) {
                    for (int i = 0; i < 4; i++) if (probe[i] == want[k]) at[k] = i;
                    order[k] = ints.get(at[k]);
                }
                if (new java.util.HashSet<>(java.util.Arrays.asList(order)).size() == 4) glBlendFunc = order;
                com.mojang.blaze3d.platform.GlStateManager._blendFuncSeparate(before[at[0]], before[at[1]], before[at[2]], before[at[3]]);
            }
        } catch (Throwable t) {
            glBlend = null;
            TFClient.LOGGER.warn("TF Client: no se encontró la caché de la mezcla de Minecraft: {}", t.toString());
        }
    }

    private static void syncBlend() {
        if (!glLooked) lookupGl();
        if (glBlend == null) return;
        try {
            Object state = glBlend.get(null);
            boolean on = glBlendOn.getBoolean(glBlendMode.get(state));
            int[] fn = glBlendFunc == null ? null : new int[] {glBlendFunc[0].getInt(state), glBlendFunc[1].getInt(state),
                    glBlendFunc[2].getInt(state), glBlendFunc[3].getInt(state)};
            // las primeras veces se mira si OpenGL (el búfer 0) coincide con la caché, para el registro y la prueba
            if (glChecks < 400) {
                glChecks++;
                boolean realOn = org.lwjgl.opengl.GL30.glIsEnabledi(org.lwjgl.opengl.GL11.GL_BLEND, 0);
                int src = org.lwjgl.opengl.GL30.glGetIntegeri(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB, 0);
                int dst = org.lwjgl.opengl.GL30.glGetIntegeri(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB, 0);
                if (realOn != on || (fn != null && (src != fn[0] || dst != fn[1]))) {
                    blendDesyncs++;
                    if (blendDesyncs == 1) {
                        TFClient.LOGGER.info("TF Client: con shaders, la mezcla de OpenGL no era la que cree Minecraft al pintar un objeto "
                                + "(OpenGL {} {}/{}, Minecraft {} {}/{}): se corrige", realOn, src, dst, on,
                                fn == null ? "?" : fn[0], fn == null ? "?" : fn[1]);
                    }
                }
            }
            if (on) org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            else org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
            if (fn != null) org.lwjgl.opengl.GL14.glBlendFuncSeparate(fn[0], fn[1], fn[2], fn[3]);
        } catch (Throwable t) {
            glBlend = null;
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
