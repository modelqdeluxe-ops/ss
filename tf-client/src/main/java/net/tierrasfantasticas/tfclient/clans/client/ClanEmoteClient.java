/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.clans.ClanLooks;
import net.tierrasfantasticas.tfclient.clans.ClanTagFx;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * (1.3.159) El emote del clan, «¡Por el clan!» (4,4 s), dibujado en cada TF Client que lo ve:
 * <ol>
 *   <li>0–0,45 s: coge impulso (se agacha, brazos atrás) mientras los brillos se juntan.</li>
 *   <li>0,45–1,05 s: salta dando una vuelta entera con los brazos en V; del pecho sale la insignia del clan (la de ahora,
 *   de ClanLooks: si la cambian, sale la nueva), que sube y crece con un muelle (se pasa y vuelve), girando; estalla una
 *   lluvia de brillos de los colores del clan que caen con gravedad.</li>
 *   <li>Al caer, se aplasta un poco y rebota; una onda de luz recorre el suelo.</li>
 *   <li>1,35–3,45 s: puño en alto (que bombea) y la otra mano en la cadera; encima, la insignia flotando con su halo y
 *   sus rayos girando, la etiqueta del clan con su efecto (arcoíris, degradado…) y una fuente de brillos alrededor.</li>
 *   <li>3,45–4,4 s: la insignia estalla en brillos y vuelve a la postura normal.</li>
 * </ol>
 * Suena la fanfarria del clan (tfclient:clanes.emote, tools/pad/emote_sonido.py) pegada al jugador. Al que lo hace se le
 * pone la cámara de frente (tercera persona) mientras dura y luego vuelve a la que tenía. Todo sale del reloj (la prueba
 * en el juego lo lleva fotograma a fotograma para grabar el vídeo).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class ClanEmoteClient {
    public static final double DURATION = 4.4;
    private static final ResourceLocation SOUND = new ResourceLocation(TFClient.MOD_ID, "clanes.emote");
    private static final ResourceLocation GLOW = new ResourceLocation(TFClient.MOD_ID, "textures/misc/brillo.png");
    private static final ResourceLocation RAY = new ResourceLocation(TFClient.MOD_ID, "textures/misc/rayo.png");
    private static final Map<UUID, Double> ACTIVE = new HashMap<>();
    private static final Set<UUID> PUSHED = new HashSet<>();
    private static final long T0 = System.nanoTime();
    private static CameraType before;
    private static boolean cameraSet;

    /** Para grabar el vídeo en la prueba: el reloj avanza a mano (1/30 s por fotograma). */
    public static volatile boolean fixedClock;
    public static volatile double fixedTime;

    private ClanEmoteClient() {}

    public static double clock() {
        return fixedClock ? fixedTime : (System.nanoTime() - T0) / 1e9;
    }

    /** Llega del servidor: ese jugador hace el emote. */
    public static void start(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ACTIVE.put(uuid, clock());
        Player p = mc.level.getPlayerByUUID(uuid);
        if (p != null) {
            mc.getSoundManager().play(new EntityBoundSoundInstance(SoundEvent.createVariableRangeEvent(SOUND), SoundSource.PLAYERS, 1.0F, 1.0F, p,
                    RandomSource.create().nextLong()));
        }
        if (mc.player != null && uuid.equals(mc.player.getUUID()) && !fixedClock) {
            if (!cameraSet) before = mc.options.getCameraType();
            cameraSet = true;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
    }

    /** Cada tick: quita los que acabaron (y devuelve la cámara). */
    static void tick() {
        double now = clock();
        ACTIVE.values().removeIf(s -> now - s > DURATION);
        Minecraft mc = Minecraft.getInstance();
        if (cameraSet && (mc.player == null || !ACTIVE.containsKey(mc.player.getUUID()))) {
            cameraSet = false;
            if (before != null) mc.options.setCameraType(before);
        }
    }

    /** Cuánto lleva ese jugador en el emote (s), o -1. */
    public static double elapsed(UUID uuid) {
        Double s = ACTIVE.get(uuid);
        if (s == null) return -1;
        double e = clock() - s;
        return e >= 0 && e <= DURATION ? e : -1;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Curvas
    // ---------------------------------------------------------------------------------------------------------------

    static double clamp01(double x) {
        return Math.max(0, Math.min(1, x));
    }

    static double smooth(double a, double b, double x) {
        double t = clamp01((x - a) / (b - a));
        return t * t * (3 - 2 * t);
    }

    static double easeOut(double x) {
        x = clamp01(x);
        return 1 - Math.pow(1 - x, 3);
    }

    /** Un muelle (de 0 a 1, pasándose y volviendo). */
    static double spring(double t) {
        if (t <= 0) return 0;
        return 1 - Math.exp(-5.2 * t) * Math.cos(11.0 * t);
    }

    /** Cuánto manda el emote sobre la postura normal (entra y sale suave). */
    static double weight(double e) {
        return smooth(0, 0.18, e) * (1 - smooth(3.9, DURATION, e));
    }

    /** La altura del salto (bloques). */
    static double jump(double e) {
        if (e < 0.45) return -0.12 * smooth(0, 0.35, e);
        if (e < 1.05) {
            double f = (e - 0.45) / 0.6;
            return 0.85 * 4 * f * (1 - f);
        }
        if (e < 1.35) return -0.07 * Math.sin((e - 1.05) / 0.3 * Math.PI);
        return 0.02 * Math.sin(e * 6.0) * (1 - smooth(3.45, 3.9, e));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // La postura (desde LivingEntityRendererMixin, después de setupAnim)
    // ---------------------------------------------------------------------------------------------------------------

    public static void applyPose(HumanoidModel<?> m, LivingEntity entity) {
        if (!(entity instanceof Player p)) return;
        double e = elapsed(p.getUUID());
        if (e < 0) return;
        double w = weight(e);
        // brazos y piernas por fases (radianes); con «a» se pasa de una a otra
        double ra, rz, la, lz, ly, rl, ll, rlz, llz, hx, bx;
        if (e < 0.45) {
            double a = smooth(0, 0.4, e);
            ra = 0.7 * a; rz = 0.15 * a; la = 0.7 * a; lz = -0.15 * a; ly = 0; rl = -0.25 * a; ll = 0.25 * a; rlz = 0; llz = 0;
            hx = 0.25 * a; bx = 0.3 * a;
        } else if (e < 1.05) {
            double a = smooth(0.45, 0.62, e);
            ra = lerp(0.7, -2.85, a); rz = lerp(0.15, 0.45, a); la = lerp(0.7, -2.85, a); lz = lerp(-0.15, -0.45, a); ly = 0;
            rl = lerp(-0.25, -0.75, a); ll = lerp(0.25, 0.55, a); rlz = 0; llz = 0; hx = lerp(0.25, -0.45, a); bx = lerp(0.3, -0.15, a);
        } else if (e < 1.35) {
            double a = smooth(1.05, 1.35, e);
            ra = -2.85; rz = lerp(0.45, 0.12, a); la = lerp(-2.85, -0.35, a); lz = lerp(-0.45, -0.95, a); ly = lerp(0, 0.35, a);
            rl = lerp(-0.75, 0, a); ll = lerp(0.55, 0, a); rlz = 0.12 * a; llz = -0.12 * a; hx = lerp(-0.45, -0.2, a); bx = lerp(-0.15, 0, a);
        } else {
            double pump = Math.sin((e - 1.35) * 9.0) * (1 - smooth(3.2, 3.45, e));
            ra = -2.9 + 0.22 * pump; rz = 0.12; la = -0.35; lz = -0.95; ly = 0.35; rl = 0; ll = 0; rlz = 0.12; llz = -0.12;
            hx = -0.2 - 0.06 * pump; bx = 0.03 * pump;
        }
        blend(m.rightArm, ra, 0, rz, w);
        blend(m.leftArm, la, ly, lz, w);
        blend(m.rightLeg, rl, 0, rlz, w);
        blend(m.leftLeg, ll, 0, llz, w);
        blend(m.head, hx, m.head.yRot, 0, w);
        blend(m.body, bx, 0, 0, w);
        m.hat.copyFrom(m.head);
        if (m instanceof PlayerModel<?> pm) {
            pm.leftSleeve.copyFrom(pm.leftArm);
            pm.rightSleeve.copyFrom(pm.rightArm);
            pm.leftPants.copyFrom(pm.leftLeg);
            pm.rightPants.copyFrom(pm.rightLeg);
            pm.jacket.copyFrom(pm.body);
        }
    }

    private static void blend(ModelPart part, double x, double y, double z, double w) {
        part.xRot = (float) lerp(part.xRot, x, w);
        part.yRot = (float) lerp(part.yRot, y, w);
        part.zRot = (float) lerp(part.zRot, z, w);
    }

    static double lerp(double a, double b, double f) {
        return a + (b - a) * f;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // El cuerpo entero (salto, vuelta, aplastarse) y los efectos
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        double e = elapsed(p.getUUID());
        if (e < 0) return;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        PUSHED.add(p.getUUID());
        pose.translate(0, jump(e), 0);
        // una vuelta entera en el salto
        double spin = 360 * easeOut((e - 0.45) / 0.6) * (e > 0.45 ? 1 : 0);
        if (spin > 0 && spin < 360) pose.mulPose(Axis.YP.rotationDegrees((float) spin));
        // aplastarse al caer y rebotar (muelle)
        double sq = e > 1.05 && e < 1.6 ? 0.12 * Math.exp(-7 * (e - 1.05)) * Math.cos(18 * (e - 1.05)) : 0;
        pose.scale((float) (1 + sq * 0.6), (float) (1 - sq), (float) (1 + sq * 0.6));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPost(RenderPlayerEvent.Post event) {
        Player p = event.getEntity();
        if (PUSHED.remove(p.getUUID())) event.getPoseStack().popPose();
        double e = elapsed(p.getUUID());
        if (e < 0) return;
        ClanLooks.Look look = ClanLooks.ofPlayer(p.getUUID());
        effects(event.getPoseStack(), event.getMultiBufferSource(), p, look, e);
    }

    /** Los colores del clan para los brillos (los de su efecto, o su color y dorado). */
    private static int[] palette(ClanLooks.Look look) {
        if (look == null) return new int[] {0xFFD24A, 0xFFFFFF, 0xFFB030};
        int[] c = look.fx().colors();
        if (look.fx().mode().equals(ClanTagFx.RAINBOW)) return new int[] {0xFF5050, 0xFFC040, 0x60FF70, 0x50C8FF, 0xB070FF, 0xFFFFFF};
        int[] out = new int[c.length + 2];
        System.arraycopy(c, 0, out, 0, c.length);
        out[c.length] = 0xFFD24A;
        out[c.length + 1] = 0xFFFFFF;
        return out;
    }

    private static void effects(PoseStack pose, MultiBufferSource buffers, Player p, ClanLooks.Look look, double e) {
        Minecraft mc = Minecraft.getInstance();
        long seed = p.getUUID().getLeastSignificantBits();
        int[] pal = palette(look);
        int main = look == null ? 0xFFD24A : look.rgb();
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        // la onda del suelo al caer
        if (e > 1.05 && e < 1.95) {
            double f = (e - 1.05) / 0.9;
            ring(pose, glow, 0.35 + 2.4 * easeOut(f), 0.28 * (1 - f) + 0.05, scale(main, (float) (1 - f) * 0.9F));
        }
        // la insignia: sube del pecho, crece con un muelle y gira; luego flota; al final estalla
        double tau = e - 0.55;
        double grow = spring(tau) * (1 - smooth(3.45, 3.75, e));
        double bobY = 1.25 + 1.75 * easeOut(tau / 0.65) + 0.07 * Math.sin(e * 2.6);
        if (tau > 0 && grow > 0.01) {
            double turn = 360 * (1 - easeOut(tau / 0.9));
            pose.pushPose();
            pose.translate(0, bobY + jump(e) * 0.4, 0);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(-1, 1, 1);
            if (turn > 0.5) pose.mulPose(Axis.YP.rotationDegrees((float) turn));
            float size = (float) (0.95 * grow);
            // halo y rayos que giran detrás
            float halo = (float) (0.55 + 0.15 * Math.sin(e * 5));
            quad(pose, glow, 0, 0, -0.03F, size * 2.1F, size * 2.1F, scale(main, halo));
            quad(pose, glow, 0, 0, -0.025F, size * 1.2F, size * 1.2F, scale(0xFFFFFF, 0.35F));
            VertexConsumer rays = buffers.getBuffer(RenderType.eyes(RAY));
            int n = 14;
            for (int i = 0; i < n; i++) {
                double ang = i * 360.0 / n + e * 28;
                float len = (float) (size * (1.25 + 0.25 * Math.sin(e * 4 + i * 1.7)));
                int col = look == null ? 0xFFD24A : ClanTagFx.colorAt(look.fx(), i, n, e);
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees((float) ang));
                quad(pose, rays, 0, len * 0.55F, -0.02F, size * 0.22F, len * 1.1F, scale(col, 0.75F));
                pose.popPose();
            }
            // la insignia
            if (look != null) {
                VertexConsumer badge = buffers.getBuffer(RenderType.entityTranslucentEmissive(look.badgeTexture()));
                texQuad(pose, badge, 0, 0, 0, size, size);
                // la etiqueta del clan con su efecto, debajo
                String tag = "[" + look.tag() + "]";
                Component text = ClanTagFx.component(tag, look.fx(), e, true);
                Font font = mc.font;
                pose.pushPose();
                pose.translate(0, -size * 0.72F, 0.01F);
                float k = 0.028F * (float) Math.min(1, grow);
                pose.scale(k, -k, k);
                float tw = font.width(text);
                font.drawInBatch(text, -tw / 2F, -4F, 0xFFFFFFFF, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0x60000000,
                        LightTexture.FULL_BRIGHT);
                pose.popPose();
            }
            pose.popPose();
        }
        // los brillos (con gravedad: posición = salida + velocidad·t + ½·g·t²)
        sparkles(pose, glow, mc, seed, pal, e, 0.55, 46, bobY, 4.2, -7.5, 1.7);
        sparkles(pose, glow, mc, seed ^ 0x9E3779B97F4A7C15L, pal, e, 3.6, 40, 3.0, 3.6, -6.0, 1.4);
        fountain(pose, glow, mc, seed, pal, e);
        // al principio, los brillos se juntan hacia el pecho
        if (e < 0.55) {
            RandomSource r = RandomSource.create(seed + 7);
            for (int i = 0; i < 24; i++) {
                double a = r.nextDouble() * Math.PI * 2, rad = 1.6 + r.nextDouble() * 0.8, h = 0.4 + r.nextDouble() * 1.6;
                double f = easeOut(e / 0.55);
                double x = Math.cos(a + f * 2) * rad * (1 - f), z = Math.sin(a + f * 2) * rad * (1 - f), y = lerp(h, 1.25, f);
                billboard(pose, glow, mc, x, y, z, 0.09F, scale(pal[i % pal.length], (float) (0.4 + 0.6 * f)));
            }
        }
    }

    private static void sparkles(PoseStack pose, VertexConsumer vc, Minecraft mc, long seed, int[] pal, double e, double at, int n, double y0,
                                 double speed, double g, double life) {
        double age0 = e - at;
        if (age0 < 0 || age0 > life + 0.4) return;
        RandomSource r = RandomSource.create(seed);
        for (int i = 0; i < n; i++) {
            double th = r.nextDouble() * Math.PI * 2, ph = Math.acos(1 - r.nextDouble() * 1.6);
            double sp = speed * (0.55 + r.nextDouble() * 0.6);
            double vx = Math.sin(ph) * Math.cos(th) * sp, vy = Math.cos(ph) * sp + 1.0, vz = Math.sin(ph) * Math.sin(th) * sp;
            double l = life * (0.6 + r.nextDouble() * 0.5), size = 0.05 + r.nextDouble() * 0.09;
            int col = pal[r.nextInt(pal.length)];
            double a = age0;
            if (a > l) continue;
            double drag = (1 - Math.exp(-1.6 * a)) / 1.6;
            double x = vx * drag, y = y0 + vy * drag + 0.5 * g * a * a * 0.5, z = vz * drag;
            float fade = (float) ((1 - a / l) * (0.65 + 0.35 * Math.sin(e * 22 + i * 1.3)));
            billboard(pose, vc, mc, x, y, z, (float) size, scale(col, fade));
        }
    }

    /** Una fuente de brillos que suben alrededor (1,2 a 3,4 s), 18 por segundo. */
    private static void fountain(PoseStack pose, VertexConsumer vc, Minecraft mc, long seed, int[] pal, double e) {
        double start = 1.2, stop = 3.4, rate = 18, life = 1.3;
        int first = (int) Math.max(0, Math.floor((e - life - start) * rate)), last = (int) Math.floor((Math.min(e, stop) - start) * rate);
        for (int i = first; i <= last; i++) {
            double born = start + i / rate, a = e - born;
            if (a < 0 || a > life) continue;
            RandomSource r = RandomSource.create(seed * 31 + i);
            double ang = r.nextDouble() * Math.PI * 2, rad = 0.7 + r.nextDouble() * 0.5;
            double vy = 2.2 + r.nextDouble() * 1.2, out = 0.25 + r.nextDouble() * 0.3;
            double x = Math.cos(ang) * (rad + out * a), z = Math.sin(ang) * (rad + out * a), y = 0.1 + vy * a - 0.5 * 2.6 * a * a;
            float fade = (float) ((1 - a / life) * (0.7 + 0.3 * Math.sin(e * 18 + i)));
            billboard(pose, vc, mc, x, y, z, (float) (0.05 + r.nextDouble() * 0.07), scale(pal[r.nextInt(pal.length)], fade));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dibujo
    // ---------------------------------------------------------------------------------------------------------------

    private static void billboard(PoseStack pose, VertexConsumer vc, Minecraft mc, double x, double y, double z, float size, int rgb) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        quad(pose, vc, 0, 0, 0, size * 2, size * 2, rgb);
        pose.popPose();
    }

    /** Un cuadrado de luz (sumada: aditiva) de w x h centrado en (x, y, z), por las dos caras. */
    private static void quad(PoseStack pose, VertexConsumer vc, float x, float y, float z, float w, float h, int rgb) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float x0 = x - w / 2, x1 = x + w / 2, y0 = y - h / 2, y1 = y + h / 2;
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        float[][] v = {{x0, y0, 0, 1}, {x1, y0, 1, 1}, {x1, y1, 1, 0}, {x0, y1, 0, 0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                float[] q = v[pass == 0 ? k : 3 - k];
                vc.vertex(m, q[0], q[1], z).color(r, g, b, 255).uv(q[2], q[3]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                        .normal(n, 0, 0, 1).endVertex();
            }
        }
    }

    /** La insignia (con su textura), por las dos caras. */
    private static void texQuad(PoseStack pose, VertexConsumer vc, float x, float y, float z, float w, float h) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float x0 = x - w / 2, x1 = x + w / 2, y0 = y - h / 2, y1 = y + h / 2;
        float[][] v = {{x0, y0, 0, 1}, {x1, y0, 1, 1}, {x1, y1, 1, 0}, {x0, y1, 0, 0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                float[] q = v[pass == 0 ? k : 3 - k];
                vc.vertex(m, q[0], q[1], z).color(255, 255, 255, 255).uv(q[2], q[3]).overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
            }
        }
    }

    /** Un aro de luz en el suelo (radio, grosor), en 40 trozos. */
    private static void ring(PoseStack pose, VertexConsumer vc, double radius, double thick, int rgb) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        int seg = 40;
        float y = 0.04F;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            double ri = radius - thick / 2, ro = radius + thick / 2;
            float[][] v = {
                    {(float) (Math.cos(a0) * ri), (float) (Math.sin(a0) * ri), 0.5F, 0},
                    {(float) (Math.cos(a0) * ro), (float) (Math.sin(a0) * ro), 0.5F, 1},
                    {(float) (Math.cos(a1) * ro), (float) (Math.sin(a1) * ro), 0.5F, 1},
                    {(float) (Math.cos(a1) * ri), (float) (Math.sin(a1) * ri), 0.5F, 0}};
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < 4; k++) {
                    float[] q = v[pass == 0 ? k : 3 - k];
                    vc.vertex(m, q[0], y, q[1]).color(r, g, b, 255).uv(q[2], q[3]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                            .normal(n, 0, 1, 0).endVertex();
                }
            }
        }
    }

    /** El color oscurecido (la luz sumada no usa el alfa: así se apaga). */
    static int scale(int rgb, float k) {
        k = Math.max(0, Math.min(1, k));
        return Math.round((rgb >> 16 & 255) * k) << 16 | Math.round((rgb >> 8 & 255) * k) << 8 | Math.round((rgb & 255) * k);
    }

    /** Para la prueba en el juego: empieza el emote de ese jugador ya (como si llegara del servidor). */
    public static void testStart(UUID uuid) {
        start(uuid);
    }
}
