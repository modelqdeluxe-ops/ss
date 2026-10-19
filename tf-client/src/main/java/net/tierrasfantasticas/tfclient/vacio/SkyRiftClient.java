package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * La grieta del cielo en el cliente (1.3.93, {@link SkyRift}): la dibuja (shader sky_rift, en un cuadro lejano que mira
 * a la cámara), la hace sonar y temblar, apaga el cielo durante su aparición y da a RiftWarp la distorsión leve de cuando
 * la miras. Está en una dirección fija del cielo ({@link SkyRift#YAW}, {@link SkyRift#PITCH}), como el sol: igual de
 * grande y en el mismo sitio estés donde estés. Sin paquete de shaders se dibuja justo después del cielo (los bloques,
 * las nubes y todo lo demás quedan delante); con paquete, después de su pasada final, solo donde se ve el cielo
 * ({@link AfterShaders}).
 *
 * <p>1.3.95, la grieta del cielo 2.0 (el dueño, de la 1.3.94: «la animación, el sonido de cristal, el tamaño y el diseño
 * de la grieta son una mierda... esmérate»; JEFES.md §3.20 y tf-client/docs/grieta-cielo-2): el shader se dibuja en
 * ángulos de cielo alrededor de su centro (le pasamos sus ejes: {@code RiftF}, {@code RiftR}, {@code RiftU}), así que el
 * cuadro es solo el lienzo (enorme: ~140° de cielo).
 * <ul>
 *   <li>Preludio (12 s): el cielo entero se va apagando (y la niebla con él), un vórtice de tormenta negra y violeta crece
 *   donde va a romperse ({@code Storm}), una sombra y un resplandor que laten ({@code Dark}); latidos cada vez más
 *   rápidos, el retumbar del warden, el lamento del guardián anciano, el chillido del sculk, el Wither y un rugido;
 *   relámpagos lejanos (con grietas que parpadean y truenos) y un temblor que crece.</li>
 *   <li>Se rompe: la telaraña de fracturas se extiende a saltos (con chasquidos de rayo, sin cristal), a los 1,8 s
 *   estalla (fogonazo en estrella, trueno, explosión, el «sonic boom» del warden, onda de choque y trozos de cielo que
 *   salen disparados: {@code Shatter}) y el desgarro se abre del centro a las puntas hasta los 5,5 s (rugido grave del
 *   dragón). El cielo vuelve poco a poco, pero mientras la grieta sigue abierta queda algo apagado, y de vez en cuando
 *   retumba un trueno lejano.</li>
 *   <li>Se sella (cayó un jefe del Void): el desgarro se cierra, fogonazo, y quedan la cicatriz y una tormenta débil.</li>
 *   <li>Se vuelve a abrir (otro sonido: el del Wither al nacer): vuelve la tormenta, estalla otra vez y se abre.</li>
 *   <li>El final: se cierra del todo, gran fogonazo, y desaparece.</li>
 * </ul>
 * La línea de tiempo ({@link #look()}) es la de la vista previa sin juego: tools/void/grieta_cielo_preview.py.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class SkyRiftClient {
    /** El shader (lo registra VoidClient). */
    static ShaderInstance shader;
    private static int state = SkyRift.OFF, fx = -1;
    private static boolean here;
    private static long fxMs;
    private static long sounds;
    /** Abierta: cuándo retumba el siguiente trueno lejano (ms; 0 = sin contar). */
    private static long rumbleAt;
    /** El final empezó con la grieta abierta (si estaba sellada, se cierra desde la cicatriz). */
    private static boolean endFromOpen = true;
    private static boolean failed;
    /** A qué distancia se dibuja el cuadro (sus esquinas, a ~97 bloques: dentro del plano lejano incluso con 2 chunks, 128). */
    private static final double DRAW = 25;
    /**
     * Medio ancho del cuadro (cuadrado), en radianes. El shader se apaga del todo a 1,2 rad del centro: el cuadro tiene
     * que llegar más allá (con 1,1, mirándola de lado se veía su borde recto en el fogonazo).
     */
    private static final double ANG = 1.21;
    /** La dirección de la grieta en el cielo. */
    private static final Vec3 DIR = Vec3.directionFromRotation(SkyRift.PITCH, SkyRift.YAW).normalize();
    /** Lo apagado que queda el cielo mientras la grieta está abierta. */
    private static final float RESIDUAL_DIM = 0.15F;
    /** Los relámpagos del preludio (segundos). */
    private static final float[] FLICKS = {3.6F, 6.4F, 8.7F, 10.3F, 11.4F};
    /** Los saltos de la telaraña de fracturas al romperse: cuándo y hasta dónde (empieza en lo que dejó el preludio). */
    private static final float[] STEP_T = {0F, 0.35F, 0.75F, 1.15F, 1.5F}, STEP_V = {0.3F, 0.45F, 0.62F, 0.8F, 1F};
    private static final float CRACK_PRELUDE = 0.18F;
    /** Cuando estalla (fogonazo, onda de choque, trozos de cielo) y cuando acaba de abrirse. */
    private static final float BREAK = 1.8F, OPENED = 5.5F;
    /** Al volver a abrirse, cuando estalla. */
    private static final float REBREAK = 1.2F;
    /** Para la prueba en el juego: fotogramas dibujados, animaciones recibidas, el temblor y lo apagado más fuertes. */
    static int frames;
    private static int fxCount;
    private static float maxShake, maxDim;

    private SkyRiftClient() {}

    static void receive(VoidNet.Sky m) {
        if (m.fx() == SkyRift.FX_END) endFromOpen = state != SkyRift.CLOSED;
        state = m.state();
        here = m.here();
        if (m.fx() != SkyRift.FX_SYNC) {
            fx = m.fx();
            fxMs = System.currentTimeMillis();
            sounds = 0;
            fxCount++;
        } else {
            fx = -1;
        }
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0F, 1F);
        return t * t * (3 - 2 * t);
    }

    /** Un pico de 0 a 1 y vuelta en «w» segundos alrededor de «at». */
    private static float bump(float t, float at, float w) {
        return Math.max(0F, 1F - Math.abs(t - at) / w);
    }

    /** Segundos desde el último cambio (o mucho, si no hay animación en marcha). */
    private static float since() {
        return fx < 0 ? 1e4F : (System.currentTimeMillis() - fxMs) / 1000F;
    }

    /** Las grietas que avanzan a saltos (cada salto, rápido y suave). */
    private static float steps(float t) {
        float v = CRACK_PRELUDE;
        for (int i = 0; i < STEP_T.length; i++) v += (STEP_V[i] - (i == 0 ? CRACK_PRELUDE : STEP_V[i - 1])) * smooth(STEP_T[i], STEP_T[i] + 0.14F, t);
        return v;
    }

    /** Lo fuerte de los relámpagos del preludio ahora (0..1). */
    private static float flick(float t) {
        float k = 0F;
        for (float f : FLICKS) k = Math.max(k, bump(t, f, 0.18F));
        return k;
    }

    /**
     * Cómo se ve ahora: grietas (la telaraña), abierto (el desgarro), fogonazo, opacidad, sombra (la del preludio),
     * tormenta, trozos de cielo (segundos desde que estalla, o -1) y onda de choque (radio en radios de la grieta, o -1);
     * null si no se ve. Es la línea de tiempo de tools/void/grieta_cielo_preview.py (look).
     */
    private static float[] look() {
        float t = since();
        switch (fx) {
            case SkyRift.FX_PRELUDE -> {
                if (t < 14F) {
                    // la tormenta crece; en cada relámpago, grietas en el centro (cada vez algo más largas), que se quedan
                    float cr = CRACK_PRELUDE * smooth(7F, 12F, t);
                    for (int i = 0; i < FLICKS.length; i++) cr = Math.max(cr, bump(t, FLICKS[i], 0.25F) * (0.12F + 0.04F * i));
                    return new float[] {cr, 0F, 0.45F * flick(t), 1F, 0.95F * smooth(0.5F, 10F, t), smooth(0F, 11.5F, t), -1F, -1F};
                }
            }
            case SkyRift.FX_BURST -> {
                if (t < 10F) {
                    float flash = 0F;
                    for (int i = 1; i < STEP_T.length; i++) flash = Math.max(flash, 0.35F * bump(t, STEP_T[i] + 0.05F, 0.2F));
                    flash = Math.max(flash, t >= BREAK ? 1.6F * (float) Math.exp(-(t - BREAK) * 1.6F) : 0.3F * smooth(1.5F, BREAK, t));
                    float shock = t >= BREAK && t < BREAK + 2.2F ? (t - BREAK) * 5F : -1F;
                    return new float[] {steps(t), smooth(BREAK, OPENED, t), flash, 1F, 0.95F * (1F - smooth(BREAK, 4F, t)), 1F,
                            t >= BREAK ? t - BREAK : -1F, shock};
                }
            }
            case SkyRift.FX_REOPEN -> {
                if (t < 7F) {
                    // desde la cicatriz: vuelve la tormenta, las grietas crecen y estalla otra vez
                    float flash = t >= REBREAK ? 1.3F * (float) Math.exp(-(t - REBREAK) * 1.8F) : 0.3F * smooth(0.9F, REBREAK, t);
                    float shock = t >= REBREAK && t < REBREAK + 2F ? (t - REBREAK) * 5F : -1F;
                    return new float[] {0.35F + 0.65F * smooth(0F, REBREAK, t), smooth(REBREAK, 4F, t), flash, 0.8F + 0.2F * smooth(0F, 0.6F, t),
                            0.6F * (1F - smooth(REBREAK, 3F, t)), 0.25F + 0.75F * smooth(0F, 1.5F, t), t >= REBREAK ? t - REBREAK : -1F, shock};
                }
            }
            case SkyRift.FX_CLOSE -> {
                if (t < 4F) {
                    float flash = 0.8F * (float) Math.exp(-Math.abs(t - 2.3F) * 6F);
                    return new float[] {1F - 0.65F * smooth(0.5F, 2.5F, t), 1F - smooth(0F, 2.2F, t), flash, 1F - 0.2F * smooth(2.3F, 3.5F, t), 0F,
                            1F - 0.75F * smooth(0.5F, 3F, t), -1F, -1F};
                }
            }
            case SkyRift.FX_END -> {
                if (t < 5F) {
                    // (desde como estaba: abierta, o sellada con su cicatriz)
                    float k = 1F - smooth(0.5F, 3F, t);
                    float flash = 1.6F * (float) Math.exp(-Math.abs(t - 2.5F) * 3.5F);
                    return new float[] {(endFromOpen ? 1F : 0.35F) * k, endFromOpen ? 1F - smooth(0F, 2.5F, t) : 0F, flash,
                            (endFromOpen ? 1F : 0.8F) * (1F - smooth(2.6F, 4.5F, t)), 0F, (endFromOpen ? 1F : 0.25F) * (1F - smooth(0.5F, 3.5F, t)),
                            -1F, -1F};
                }
                return null;
            }
            default -> {
            }
        }
        return switch (state) {
            case SkyRift.OPEN -> new float[] {1F, 1F, 0F, 1F, 0F, 1F, -1F, -1F};
            case SkyRift.CLOSED -> new float[] {0.35F, 0F, 0F, 0.8F, 0F, 0.25F, -1F, -1F};
            default -> null;
        };
    }

    /** Lo apagado del cielo (0..1): crece en el preludio, se queda algo mientras está abierta y se va al sellarse. */
    private static float skyDim() {
        if (!here) return 0F;
        float t = since();
        float base = state == SkyRift.OPEN ? RESIDUAL_DIM : 0F;
        float d = switch (fx) {
            // (los relámpagos iluminan un instante el cielo apagado)
            case SkyRift.FX_PRELUDE -> t < 14F ? 0.6F * smooth(0F, 9F, t) - 0.15F * flick(t) : base;
            case SkyRift.FX_BURST -> t < 18F ? 0.6F - (0.6F - RESIDUAL_DIM) * smooth(OPENED, 16F, t) : base;
            case SkyRift.FX_REOPEN -> t < 8F ? RESIDUAL_DIM + 0.3F * smooth(0F, REBREAK, t) * (1F - smooth(2F, 7F, t)) : base;
            case SkyRift.FX_CLOSE, SkyRift.FX_END -> t < 5F ? RESIDUAL_DIM * (1F - smooth(0.5F, 4F, t)) : base;
            default -> base;
        };
        return Mth.clamp(d, 0F, 1F);
    }

    /** ¿Se ve aquí? (mundo de cielo de Overworld) */
    private static boolean visibleHere() {
        Minecraft mc = Minecraft.getInstance();
        return here && mc.level != null && !failed && shader != null
                && mc.level.effects().skyType() == net.minecraft.client.renderer.DimensionSpecialEffects.SkyType.NORMAL;
    }

    /** El temblor de la cámara (RiftWarp lo suma): crece en el preludio (con un golpe en cada relámpago), y al romperse. */
    public static float shake() {
        if (!here || fx < 0) return 0F;
        float t = since();
        float k = switch (fx) {
            case SkyRift.FX_PRELUDE -> t < 13F ? 0.05F + 0.45F * smooth(0F, 12F, t) + 0.35F * flick(t) : 0F;
            case SkyRift.FX_BURST -> t < BREAK ? 0.5F + 0.4F * (steps(t) - steps(t - 0.15F)) / 0.3F
                    : t < 10F ? 2.0F * (float) Math.exp(-(t - BREAK) * 0.9F) : 0F;
            case SkyRift.FX_REOPEN -> t > REBREAK && t < 6F ? 1.4F * (float) Math.exp(-(t - REBREAK) * 1.2F) : 0F;
            case SkyRift.FX_CLOSE, SkyRift.FX_END -> t > 2F && t < 4.5F ? 0.6F * (float) Math.exp(-Math.abs(t - 2.4F) * 2F) : 0F;
            default -> 0F;
        };
        maxShake = Math.max(maxShake, k);
        return k;
    }

    /** Para la prueba en el juego (BossTestClient): la animación en marcha (cuál y cuántas van) y sus segundos. */
    public static int testFx() {
        return fx;
    }

    public static int testFxCount() {
        return fxCount;
    }

    public static float testSince() {
        return since();
    }

    private static boolean once(int bit, boolean when) {
        if (!when || (sounds & 1L << bit) != 0) return false;
        sounds |= 1L << bit;
        return true;
    }

    private static void play(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /** Los latidos del preludio: cada vez más rápidos (de 1,2 s a 0,35 s entre uno y otro). */
    private static final float[] BEATS = new float[18];

    static {
        float at = 0.4F;
        for (int i = 0; i < BEATS.length; i++) {
            BEATS[i] = at;
            at += 1.2F - 0.85F * i / (BEATS.length - 1);
        }
    }

    /**
     * Los sonidos de lo que está pasando (solo en los mundos que la ven). Sin cristal (el dueño se quejó del «sonido de
     * cristal» de la 1.3.94): chasquidos de rayo, truenos, el warden, el dragón y el portal del End.
     */
    private static void sounds() {
        if (!here) return;
        rumble();
        if (fx < 0) return;
        float t = since();
        switch (fx) {
            case SkyRift.FX_PRELUDE -> {
                if (once(0, t >= 0F)) play(SoundEvents.WARDEN_EMERGE, 0.5F, 1F);
                for (int i = 0; i < BEATS.length; i++) if (BEATS[i] < 12F && once(1 + i, t >= BEATS[i])) play(SoundEvents.WARDEN_HEARTBEAT, 0.55F + 0.025F * i, 1F);
                if (once(20, t >= 1.5F)) play(SoundEvents.ELDER_GUARDIAN_CURSE, 0.45F, 0.5F);
                for (int i = 0; i < FLICKS.length; i++) if (once(21 + i, t >= FLICKS[i])) play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5F + 0.04F * i, 0.35F + 0.08F * i);
                if (once(27, t >= 5F)) play(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5F, 0.6F);
                if (once(28, t >= 7.5F)) play(SoundEvents.WITHER_AMBIENT, 0.4F, 0.6F);
                if (once(29, t >= 9.5F)) play(SoundEvents.ENDER_DRAGON_GROWL, 0.45F, 0.6F);
                if (once(30, t >= 11.2F)) play(SoundEvents.WARDEN_ROAR, 0.5F, 0.7F);
            }
            case SkyRift.FX_BURST -> {
                // la carga del warden; en cada salto de las grietas, un chasquido de rayo
                if (once(0, t >= 0F)) play(SoundEvents.WARDEN_SONIC_CHARGE, 0.5F, 1F);
                for (int i = 0; i < STEP_T.length; i++) if (once(1 + i, t >= STEP_T[i])) play(SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5F + 0.05F * i, 0.6F);
                // estalla: trueno, explosión, el «sonic boom» y el portal del End
                if (once(10, t >= BREAK)) {
                    play(SoundEvents.TRIDENT_THUNDER, 0.6F, 1F);
                    play(SoundEvents.GENERIC_EXPLODE, 0.45F, 1F);
                    play(SoundEvents.WARDEN_SONIC_BOOM, 0.5F, 0.8F);
                    play(SoundEvents.END_PORTAL_SPAWN, 0.5F, 1F);
                }
                // mientras se abre, un rugido largo y grave
                if (once(11, t >= 2.2F)) play(SoundEvents.ENDER_DRAGON_DEATH, 0.55F, 0.6F);
                if (once(12, t >= 3F)) play(SoundEvents.PORTAL_TRIGGER, 0.4F, 0.6F);
            }
            case SkyRift.FX_REOPEN -> {
                if (once(0, t >= 0F)) play(SoundEvents.WITHER_SPAWN, 0.8F, 0.55F);
                if (once(1, t >= REBREAK)) {
                    play(SoundEvents.TRIDENT_THUNDER, 0.7F, 1F);
                    play(SoundEvents.END_PORTAL_SPAWN, 0.6F, 0.9F);
                }
            }
            case SkyRift.FX_CLOSE -> {
                if (once(0, t >= 0F)) play(SoundEvents.BEACON_DEACTIVATE, 0.5F, 1F);
                if (once(1, t >= 2.3F)) {
                    play(SoundEvents.CONDUIT_DEACTIVATE, 0.6F, 1F);
                    play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1F, 0.5F);
                }
            }
            case SkyRift.FX_END -> {
                if (once(0, t >= 0F)) play(SoundEvents.BEACON_DEACTIVATE, 0.6F, 1F);
                if (once(1, t >= 2.5F)) {
                    play(SoundEvents.TRIDENT_THUNDER, 1F, 1F);
                    play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1F);
                }
            }
            default -> {
            }
        }
    }

    /** Abierta (y sin animación desde hace 20 s): un trueno lejano cada 30–50 s, para que no se olvide que está ahí. */
    private static void rumble() {
        if (state != SkyRift.OPEN || fx >= 0 && since() < 20F) {
            rumbleAt = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (rumbleAt == 0) {
            rumbleAt = now + java.util.concurrent.ThreadLocalRandom.current().nextLong(30_000, 50_000);
        } else if (now >= rumbleAt) {
            play(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.4F, 0.25F);
            rumbleAt = now + java.util.concurrent.ThreadLocalRandom.current().nextLong(30_000, 50_000);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) sounds();
    }

    /** Al salir del servidor se olvida (el siguiente manda la suya). */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        state = SkyRift.OFF;
        fx = -1;
        here = false;
        rumbleAt = 0;
    }

    /** La niebla se apaga con el cielo (si no, el horizonte se quedaría claro bajo un cielo oscuro). */
    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!visibleHere() || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        float d = skyDim();
        if (d <= 0.001F) return;
        event.setRed(event.getRed() * (1F - 0.7F * d) + 0.02F * d);
        event.setGreen(event.getGreen() * (1F - 0.75F * d));
        event.setBlue(event.getBlue() * (1F - 0.6F * d) + 0.05F * d);
    }

    // ------------------------------------------------------------------------------------------- dibujo

    /**
     * Sin paquete de shaders: justo después del cielo (lo demás queda delante) y, antes de las nubes, la máscara que las
     * pone detrás del desgarro ({@link #renderMask}).
     */
    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (AfterShaders.active()) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            render(event.getPoseStack().last().pose(), event.getProjectionMatrix(), false);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            renderMask(event.getPoseStack().last().pose(), event.getProjectionMatrix(), event.getCamera().getPosition().y);
        }
    }

    /** Con paquete de shaders (AfterShaders): solo donde la escena dejó ver el cielo. */
    static void renderAfterShaders(Matrix4f view, Matrix4f proj) {
        render(view, proj, true);
    }

    /**
     * Los ejes de la grieta (relativos a la cámara, normalizados): su centro, su derecha y su arriba. De pie (su largo
     * hacia arriba); si estuviera muy alta, hacia el norte. El shader mide todo en ángulos alrededor de ellos.
     */
    private static Vec3[] axes() {
        double k = smooth(0.9F, 0.99F, (float) Math.abs(DIR.y));
        Vec3 ref = new Vec3(0, 1 - k, -k).normalize();
        Vec3 right = DIR.cross(ref);
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(DIR).normalize();
        return new Vec3[] {DIR, right, up};
    }

    /** El cuadro (cuadrado): centro, eje «derecha» y eje «arriba» (relativos a la cámara). */
    private static Vec3[] quad(Vec3[] ax) {
        double hw = DRAW * Math.tan(ANG);
        return new Vec3[] {ax[0].scale(DRAW), ax[1].scale(hw), ax[2].scale(hw)};
    }

    private static void render(Matrix4f view, Matrix4f proj, boolean atFar) {
        if (!visibleHere()) return;
        float[] lk = look();
        float dim = skyDim();
        maxDim = Math.max(maxDim, dim);
        if ((lk == null || lk[3] <= 0.001F) && dim <= 0.001F) return;
        PoseStack mv = RenderSystem.getModelViewStack();
        try {
            RenderSystem.backupProjectionMatrix();
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            if (atFar) {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
            } else {
                RenderSystem.disableDepthTest();
            }
            // El cielo apagado: un velo oscuro y violeta sobre todo el cielo (con paquete, solo donde se ve el cielo)
            if (dim > 0.001F) {
                mv.pushPose();
                mv.setIdentity();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.DISTANCE_TO_ORIGIN);
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                float z = atFar ? 0.999999F : 0F;
                float a = Mth.clamp(dim * 0.78F, 0F, 0.85F);
                BufferBuilder b = Tesselator.getInstance().getBuilder();
                b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                b.vertex(-1, -1, z).color(0.05F, 0.01F, 0.09F, a * 0.85F).endVertex();
                b.vertex(1, -1, z).color(0.05F, 0.01F, 0.09F, a * 0.85F).endVertex();
                b.vertex(1, 1, z).color(0.02F, 0F, 0.05F, a).endVertex();
                b.vertex(-1, 1, z).color(0.02F, 0F, 0.05F, a).endVertex();
                BufferUploader.drawWithShader(b.end());
                mv.popPose();
            }
            if (lk != null && lk[3] > 0.001F) {
                drawRift(view, proj, lk, atFar, 0F);
                frames++;
            }
        } catch (Throwable t) {
            failed = true;
            TFClient.LOGGER.error("TF Void: no se pudo dibujar la grieta del cielo: se apaga", t);
        } finally {
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
    }

    /** El cuadro con el shader de la grieta («mask» > 0: solo la máscara de las nubes, a esa altura sobre la cámara). */
    private static void drawRift(Matrix4f view, Matrix4f proj, float[] lk, boolean atFar, float mask) {
        Vec3[] ax = axes();
        Vec3[] q = quad(ax);
        Vec3 c = q[0], r = q[1], u = q[2];
        ShaderInstance sh = shader;
        sh.safeGetUniform("STime").set((System.currentTimeMillis() % 3_600_000L) / 1000F);
        sh.safeGetUniform("Crack").set(lk[0]);
        sh.safeGetUniform("Open").set(lk[1]);
        sh.safeGetUniform("Flash").set(lk[2]);
        sh.safeGetUniform("Alpha").set(lk[3]);
        sh.safeGetUniform("AtFar").set(atFar ? 1F : 0F);
        sh.safeGetUniform("Dark").set(lk[4]);
        sh.safeGetUniform("Storm").set(lk[5]);
        sh.safeGetUniform("Shatter").set(lk[6]);
        sh.safeGetUniform("RiftF").set((float) ax[0].x, (float) ax[0].y, (float) ax[0].z);
        sh.safeGetUniform("RiftR").set((float) ax[1].x, (float) ax[1].y, (float) ax[1].z);
        sh.safeGetUniform("RiftU").set((float) ax[2].x, (float) ax[2].y, (float) ax[2].z);
        sh.safeGetUniform("CloudMask").set(mask);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        mv.mulPoseMatrix(view);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(proj), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.setShader(() -> sh);
        RenderSystem.setShaderTexture(0, VoidSky.PANORAMA);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        vertex(b, c, r, u, -1, -1, 0, 0);
        vertex(b, c, r, u, 1, -1, 1, 0);
        vertex(b, c, r, u, 1, 1, 1, 1);
        vertex(b, c, r, u, -1, 1, 0, 1);
        BufferUploader.drawWithShader(b.end());
        mv.popPose();
    }

    /**
     * (1.3.95) Las nubes de Minecraft, detrás del desgarro. Sin paquete de shaders la grieta se dibuja después del cielo
     * y las nubes (que se dibujan al final) le quedaban delante: en la prueba en el juego, bloques blancos tapaban medio
     * Void. Antes de las nubes (después de los bloques, el cristal y las partículas), donde está el desgarro (y su filo),
     * se escribe sin color la profundidad de un punto 2 bloques antes de la capa de nubes: las nubes de detrás
     * no pasan y lo que hay más cerca (bloques, cristal, lluvia) sigue delante. Si la cámara está en las nubes o por
     * encima, o no hay nubes, no hace nada.
     */
    private static void renderMask(Matrix4f view, Matrix4f proj, double camY) {
        if (!visibleHere()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCloudsType() == net.minecraft.client.CloudStatus.OFF) return;
        float cloud = mc.level.effects().getCloudHeight();
        if (Float.isNaN(cloud)) return;
        // (la base de las nubes, como en LevelRenderer.renderClouds)
        float h = (float) (cloud - camY + 0.33);
        if (h <= 3F) return;
        float[] lk = look();
        if (lk == null || lk[3] < 0.5F || lk[1] <= 0.002F) return;
        try {
            RenderSystem.backupProjectionMatrix();
            RenderSystem.disableBlend();
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.disableCull();
            drawRift(view, proj, lk, false, h);
        } catch (Throwable t) {
            failed = true;
            TFClient.LOGGER.error("TF Void: no se pudo dibujar la máscara de la grieta del cielo: se apaga", t);
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }
    }

    private static void vertex(BufferBuilder b, Vec3 c, Vec3 r, Vec3 u, float sx, float sy, float tu, float tv) {
        b.vertex(c.x + r.x * sx + u.x * sy, c.y + r.y * sx + u.y * sy, c.z + r.z * sx + u.z * sy).uv(tu, tv).endVertex();
    }

    /**
     * La distorsión leve de cuando la miras (RiftWarp): su centro en la pantalla (uv), su radio (alto de la pantalla
     * = 1, en esas unidades), la fuerza (0..1, más al romperse), el fogonazo y la onda de choque (o -1); null si no se ve.
     */
    public static float[] warp(Matrix4f view, Matrix4f proj, float aspect) {
        if (!visibleHere()) return null;
        float[] lk = look();
        if (lk == null || lk[3] <= 0.01F || lk[1] <= 0.01F && lk[2] <= 0.01F && lk[7] < 0F) return null;
        // (su radio: 0,3 rad de cielo desde el centro; el cuadro es mucho más grande que la grieta)
        Vec3[] ax = axes();
        Vec3 center = ax[0].scale(DRAW);
        float[] s0 = project(view, proj, center);
        float[] s1 = project(view, proj, center.add(ax[1].scale(Math.tan(0.3) * DRAW)));
        if (s0 == null || s1 == null) return null;
        if (s0[0] < -0.2F || s0[0] > 1.2F || s0[1] < -0.2F || s0[1] > 1.2F) return null;
        float rad = (float) Math.hypot((s1[0] - s0[0]) * aspect, s1[1] - s0[1]);
        return new float[] {s0[0], s0[1], Mth.clamp(rad, 0.05F, 0.3F), lk[1] * lk[3], lk[2], lk[7]};
    }

    private static float[] project(Matrix4f view, Matrix4f proj, Vec3 rel) {
        Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1F);
        view.transform(v);
        proj.transform(v);
        if (v.w <= 0.05F) return null;
        return new float[] {v.x / v.w * 0.5F + 0.5F, v.y / v.w * 0.5F + 0.5F};
    }

    /** Para la prueba en el juego: lo que ve el cliente. */
    public static String describe() {
        String[] names = {"no ha salido", "abierta", "sellada", "cerrada para siempre"};
        return names[Math.max(0, Math.min(3, state))] + (here ? ", se ve en este mundo" : ", no se ve en este mundo") + ", " + frames
                + " fotogramas dibujada, " + fxCount + " animaciones, temblor máximo " + String.format("%.2f", maxShake)
                + ", cielo apagado como mucho " + String.format("%.2f", maxDim) + (failed ? ", FALLÓ AL DIBUJARSE" : "")
                + (shader == null ? ", SIN SHADER" : "");
    }
}
