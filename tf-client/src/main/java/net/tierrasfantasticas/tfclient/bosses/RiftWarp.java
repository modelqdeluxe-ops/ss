package net.tierrasfantasticas.tfclient.bosses;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.vacio.AfterShaders;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * La distorsión del espacio-tiempo de las grietas (1.3.91; el dueño: «al romper el cristal de la grieta, que desate su
 * poder y se distorsione el espacio-tiempo hasta matar al boss, y se cierre la grieta; que el cielo o el agujero negro
 * se distorsionen»). Cada fotograma, después de dibujar el mundo (con o sin paquete de shaders: {@code
 * GameRendererLateMixin}), si hay una grieta activa cerca se copia la imagen y se vuelve a pintar deformada con el shader
 * rift_warp (lente gravitatoria con anillo de Einstein, remolino, ondas, onda de choque, aberración cromática y destello
 * del color de la grieta; ver el .fsh).
 *
 * <p>Cómo va, según el estado de la grieta ({@link #profile}):
 * <ul>
 *   <li>Despertando: la lente y el remolino crecen con el cristal que se agrieta; ondas; la cámara empieza a temblar.</li>
 *   <li>Al estallar (empieza la pelea): destello, una onda de choque que barre la pantalla y un golpe de temblor.</li>
 *   <li>Toda la pelea: lente y remolino fuertes alrededor del desgarro abierto, ondas que salen, un temblor suave.</li>
 *   <li>Al morir el jefe: la lente tira cada vez más fuerte y el remolino se acelera mientras se cierra (30 ticks); al
 *   juntarse, una onda de choque hacia dentro y un fogonazo; luego nada.</li>
 * </ul>
 * Se nota a menos de ~48 bloques y se apaga del todo a 144. En The Void, además, el agujero negro reacciona ({@link
 * #rage()}: VoidSky). Lo usa también el viaje al Void ({@link #travel}).
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class RiftWarp {
    static ShaderInstance shader;
    private static TextureTarget copy;
    private static boolean failed;
    private static float shake, rage, travel, travelSwirl;
    private static boolean rageMother;
    /** Para la prueba en el juego: fotogramas deformados. */
    public static int frames;

    private RiftWarp() {}

    /** Una grieta vista desde la cámara. */
    private static final class Src {
        float x, y, r, lens, swirl, shock = -1F, wave, flash, tr, tg, tb, weight;
    }

    /** El viaje al Void: deformación en el centro de la pantalla (0..1) y cuánto gira (TravelFx, cada fotograma). */
    public static void travel(float strength, float swirl) {
        travel = strength;
        travelSwirl = swirl;
    }

    /** Lo que se enfurece el agujero negro del Void (0..1) por la grieta activa más cercana. */
    public static float rage() {
        return rage;
    }

    /** ¿Es una grieta madre la que lo enfurece (carmesí)? */
    public static boolean rageMother() {
        return rageMother;
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0F, 1F);
        return t * t * (3 - 2 * t);
    }

    /** lente, remolino, onda de choque (radio en R, −1 = no), ondas, destello, temblor, furia del cielo. */
    static float[] profile(RiftEntity e, float partial) {
        int st = e.state();
        float since = e.clientAge - e.stateSince + partial;
        float t = e.clientAge + partial;
        switch (st) {
            case RiftEntity.WAKING -> {
                float k = Mth.clamp(1F - (e.left() - partial) / RiftEntity.WAKE_TICKS, 0F, 1F);
                return new float[] {0.1F + 0.3F * k * k, 0.6F * k, -1F, 0.01F + 0.035F * k, 0F, 0.12F + 0.7F * k * k, 0.3F + 0.5F * k};
            }
            case RiftEntity.FIGHT -> {
                float shock = since < 45F ? since / 9F : -1F;
                float flash = since < 8F ? 1.4F * (1F - since / 8F) : 0F;
                float sh = since < 24F ? 2.4F * (1F - since / 24F) : 0.1F;
                // (la pelea dura minutos: la lente se queda en algo que deja pelear; el golpe fuerte es al estallar)
                float burst = 1F - smooth(0F, 30F, since);
                return new float[] {0.32F + 0.3F * burst + 0.06F * Mth.sin(t * 0.17F), 0.7F + 0.6F * burst + 0.2F * Mth.sin(t * 0.05F),
                        shock, 0.02F, flash, Math.max(sh, 0.1F), 1F};
            }
            case RiftEntity.COOLDOWN -> {
                if (e.prevState != RiftEntity.FIGHT || since > 60F) return null;
                float c = smooth(0F, 30F, since);
                float gone = smooth(30F, 42F, since);
                float lens = (0.55F + 1.6F * c) * (1F - gone);
                float swirl = (1.1F + 3F * c) * (1F - gone);
                float shock = since >= 24F && since <= 36F ? 3.2F * (1F - (since - 24F) / 12F) : -1F;
                float bump = Math.max(0F, 1F - Math.abs(since - 30F) / 4F);
                return new float[] {lens, swirl, shock, 0.03F * (1F - gone), 1.6F * bump, 0.3F * (1F - gone) + 1.8F * bump,
                        1F - smooth(30F, 60F, since)};
            }
            default -> {
                return null;
            }
        }
    }

    /** Proyecta un punto del mundo (relativo a la cámara) a pantalla: x, y en uv; null si está detrás. */
    private static float[] project(Matrix4f view, Matrix4f proj, Vec3 rel) {
        Vector4f v = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1F);
        view.transform(v);
        proj.transform(v);
        if (v.w <= 0.05F) return null;
        return new float[] {v.x / v.w * 0.5F + 0.5F, v.y / v.w * 0.5F + 0.5F};
    }

    /** Después de dibujar el mundo: si hay algo que deformar, se deforma. */
    public static void apply(float partial) {
        Minecraft mc = Minecraft.getInstance();
        shake = 0F;
        float rageNow = 0F;
        boolean mother = false;
        if (failed || shader == null || mc.level == null) {
            rage = 0F;
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        float aspect = main.width / (float) Math.max(1, main.height);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Matrix4f view = AfterShaders.view(), proj = AfterShaders.projection();
        List<Src> list = new ArrayList<>();
        float near = 0F;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof RiftEntity e)) continue;
            float[] pr = profile(e, partial);
            if (pr == null) continue;
            double sc = e.scale();
            Vec3 c = new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()) + 11.5 * sc,
                    Mth.lerp(partial, e.zo, e.getZ()));
            double dist = c.distanceTo(cam);
            float att = (float) Mth.clamp(1 - (dist - 48 * sc) / 96, 0, 1);
            if (att <= 0F) continue;
            float shakeAtt = (float) Mth.clamp(1 - (dist - 24 * sc) / 64, 0, 1);
            shake = Math.max(shake, pr[5] * shakeAtt);
            if (net.tierrasfantasticas.tfclient.vacio.TFVoid.isVoid(mc.level) && pr[6] * att > rageNow) {
                rageNow = pr[6] * att;
                mother = e.kind() == RiftEntity.MOTHER;
            }
            near = Math.max(near, att * Mth.clamp(pr[0], 0F, 1F));
            float[] s0 = project(view, proj, c.subtract(cam));
            float[] s1 = project(view, proj, c.add(0, 10 * sc, 0).subtract(cam));
            if (s0 == null || s1 == null) continue;
            Src s = new Src();
            s.x = s0[0];
            s.y = s0[1];
            float rawR = (float) Math.hypot((s1[0] - s0[0]) * aspect, s1[1] - s0[1]);
            if (rawR < 0.01F) continue;
            // De cerca la grieta llena la pantalla: la lente no pasa de un radio y se suaviza (se pelea a su lado); lo que
            // se siente entonces es la ondulación, la aberración y la viñeta
            s.r = Math.min(rawR, 0.28F);
            if (rawR > 0.28F) att *= (float) Math.pow(0.28F / rawR, 1.2);
            s.lens = pr[0] * att;
            s.swirl = pr[1] * att;
            s.shock = pr[2];
            s.wave = pr[3] * att;
            s.flash = pr[4] * att;
            org.joml.Vector3f col = e.color();
            s.tr = col.x;
            s.tg = col.y;
            s.tb = col.z;
            s.weight = s.lens + s.flash + (s.shock >= 0 ? 0.5F : 0F);
            list.add(s);
        }
        rage = rageNow;
        rageMother = mother;
        if (travel > 0.001F) {
            Src s = new Src();
            s.x = 0.5F;
            s.y = 0.5F;
            s.r = 0.22F;
            s.lens = 0.5F * travel;
            s.swirl = travelSwirl;
            s.wave = 0.04F * travel;
            s.tr = 0.95F;
            s.tg = 0.85F;
            s.tb = 1F;
            s.weight = 10F;
            list.add(s);
            near = Math.max(near, travel);
            // (la realidad se rasga: también tiembla)
            shake = Math.max(shake, 0.6F * travel);
        }
        // La grieta del cielo (1.3.93): al mirarla, una distorsión leve (más al romperse); y su temblor
        shake = Math.max(shake, net.tierrasfantasticas.tfclient.vacio.SkyRiftClient.shake());
        float[] sky = net.tierrasfantasticas.tfclient.vacio.SkyRiftClient.warp(view, proj, aspect);
        if (sky != null) {
            Src s = new Src();
            s.x = sky[0];
            s.y = sky[1];
            s.r = sky[2];
            s.lens = 0.07F * sky[3] + 0.25F * sky[4];
            s.swirl = 0.18F * sky[3];
            s.wave = 0.004F * sky[3];
            s.flash = 0.5F * sky[4];
            // (al romperse, la onda de choque barre la pantalla)
            s.shock = sky[5];
            s.tr = 0.75F;
            s.tg = 0.45F;
            s.tb = 1F;
            s.weight = s.lens + s.flash + (s.shock >= 0 ? 0.5F : 0F);
            list.add(s);
            near = Math.max(near, 0.1F * sky[3] + 0.3F * sky[4]);
        }
        if (list.isEmpty() && near <= 0.001F) return;
        list.sort((a, b) -> Float.compare(b.weight, a.weight));
        try {
            draw(main, list, near, mc);
            frames++;
        } catch (Throwable t) {
            failed = true;
            TFClient.LOGGER.error("TF Jefes: no se pudo deformar la imagen (distorsión de las grietas): se apaga", t);
        }
    }

    private static void draw(RenderTarget main, List<Src> list, float near, Minecraft mc) {
        if (copy == null || copy.width != main.width || copy.height != main.height) {
            if (copy != null) copy.destroyBuffers();
            copy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            copy.setFilterMode(GL11.GL_LINEAR);
        }
        // la imagen del mundo, a la copia
        GlStateManager._glBindFramebuffer(0x8CA8, main.frameBufferId);   // GL_READ_FRAMEBUFFER
        GlStateManager._glBindFramebuffer(0x8CA9, copy.frameBufferId);   // GL_DRAW_FRAMEBUFFER
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, copy.width, copy.height, GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST);
        main.bindWrite(true);
        ShaderInstance sh = shader;
        float time = (System.nanoTime() % 3_600_000_000_000L) / 1e9F;
        for (int i = 0; i < 2; i++) {
            Src s = i < list.size() ? list.get(i) : null;
            if (s == null) {
                sh.safeGetUniform("Src" + i).set(0.5F, 0.5F, 0.2F, 0F);
                sh.safeGetUniform("Fx" + i).set(0F, -1F, 0F, 0F);
                sh.safeGetUniform("Tint" + i).set(0F, 0F, 0F, 0F);
            } else {
                sh.safeGetUniform("Src" + i).set(s.x, s.y, s.r, s.lens);
                sh.safeGetUniform("Fx" + i).set(s.swirl, s.shock, s.wave, s.flash);
                sh.safeGetUniform("Tint" + i).set(s.tr, s.tg, s.tb, 1F);
            }
        }
        sh.safeGetUniform("Glob").set(0.004F * near, 0.006F * near, 0.55F * near, 0F);
        sh.safeGetUniform("ScreenSize").set((float) main.width, (float) main.height);
        sh.safeGetUniform("WarpTime").set(time);
        RenderSystem.setShader(() -> sh);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        b.vertex(-1, -1, 0).endVertex();
        b.vertex(1, -1, 0).endVertex();
        b.vertex(1, 1, 0).endVertex();
        b.vertex(-1, 1, 0).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** El temblor de la cámara (al despertar, al estallar, en la pelea y al cerrarse). */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float a = Math.min(shake, 3F) * 0.9F;
        if (a <= 0.01F) return;
        double t = (System.nanoTime() % 3_600_000_000_000L) / 1e9;
        event.setYaw(event.getYaw() + a * (float) (Math.sin(t * 31) * 0.6 + Math.sin(t * 17.3) * 0.4));
        event.setPitch(event.getPitch() + a * (float) (Math.sin(t * 27 + 1.3) * 0.6 + Math.sin(t * 13.1 + 2) * 0.4));
        event.setRoll(event.getRoll() + a * 0.7F * (float) Math.sin(t * 21 + 4.1));
    }
}
