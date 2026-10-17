package net.tierrasfantasticas.tfclient.bosses;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * El cielo de las grietas (1.3.86, lo pidió el dueño: «que la grieta haga que el cielo se tone como el del End, con
 * nebulosas y estrellas fugaces, que combine con la música»). Cerca de una grieta el cielo se funde (despacio, ~5 s) con
 * el vacío: un panorama de nebulosas moradas (tools/bosses/rift_sky.py) en una esfera que gira muy despacio, nubes de
 * nebulosa que laten, estrellas que titilan y estrellas fugaces; la niebla, el color del cielo y las nubes de Minecraft
 * se tiñen del mismo morado (RiftSkyMixin) y suena el zumbido de la grieta.
 * Cuánto: con la grieta lista o recargando, entero a 20 bloques y nada a 64; despertando o en pelea, entero hasta 80
 * bloques y nada a 128. Solo en el cliente: no cambia nada del mundo.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class RiftSky {
    private static final ResourceLocation SKY = new ResourceLocation(TFClient.MOD_ID, "textures/environment/grieta_cielo.png");
    private static final ResourceLocation CLOUD = new ResourceLocation(TFClient.MOD_ID, "textures/environment/grieta_nube.png");
    private static final ResourceLocation STAR = new ResourceLocation(TFClient.MOD_ID, "textures/environment/grieta_estrella.png");
    /** El color al que van la niebla y el cielo de Minecraft (y las nubes, algo más claras). */
    public static final Vec3 FOG = new Vec3(0.045, 0.008, 0.085), CLOUDS = new Vec3(0.30, 0.13, 0.45);

    private static float strength, prevStrength;
    private static RiftEntity nearest;
    private static Ambient ambient;
    private static int ticks, nextMeteor = 40;
    private static final List<Meteor> METEORS = new ArrayList<>();
    private static final Star[] STARS = new Star[700];
    private static final Nebula[] NEBULAS = new Nebula[7];

    static {
        Random r = new Random(1717);
        for (int i = 0; i < STARS.length; i++) {
            Vector3f d = randomDir(r, -0.15F);
            float size = 0.25F + (float) Math.pow(r.nextFloat(), 3) * 1.1F;
            float hue = r.nextFloat();
            // blancas, lavanda y alguna rosada o azulada
            float cr = hue < 0.6F ? 1F : hue < 0.8F ? 0.85F : 1F, cg = hue < 0.6F ? 0.95F : hue < 0.8F ? 0.8F : 0.75F, cb = 1F;
            STARS[i] = new Star(d, size, cr, cg, cb, 0.6F + r.nextFloat() * 2.4F, r.nextFloat() * 6.28F);
        }
        for (int i = 0; i < NEBULAS.length; i++) {
            NEBULAS[i] = new Nebula(randomDir(r, 0.05F), 26F + r.nextFloat() * 34F, r.nextFloat() * 360F,
                    (r.nextFloat() - 0.5F) * 3F, r.nextFloat() * 6.28F, 0.3F + r.nextFloat() * 0.3F);
        }
    }

    private RiftSky() {}

    /** Cuánto cielo de grieta hay ahora (0..1), para el mixin del color del cielo y las nubes. */
    public static float strength(float partial) {
        return Mth.lerp(partial, prevStrength, strength);
    }

    /** Mezcla un color de Minecraft con el del vacío según la fuerza de ahora. */
    public static Vec3 tint(Vec3 c, Vec3 to, float partial) {
        float s = strength(partial);
        return s <= 0.001F ? c : c.lerp(to, s * 0.92);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        prevStrength = strength;
        if (mc.level == null || mc.player == null) {
            strength = prevStrength = 0;
            nearest = null;
            METEORS.clear();
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        float target = 0;
        RiftEntity near = null;
        double nearD = Double.MAX_VALUE;
        for (RiftEntity e : mc.level.getEntitiesOfClass(RiftEntity.class, mc.player.getBoundingBox().inflate(128))) {
            double d = e.distanceTo(mc.player);
            int st = e.state();
            float t = st == RiftEntity.WAKING || st == RiftEntity.FIGHT ? 1F - smooth(80, 128, d) : 1F - smooth(20, 64, d);
            target = Math.max(target, t);
            if (d < nearD) {
                nearD = d;
                near = e;
            }
        }
        strength += Mth.clamp(target - strength, -0.01F, 0.01F);
        nearest = near;
        // El zumbido del vacío, en la grieta más cercana
        if (near != null && nearD < 30) {
            if (ambient == null || ambient.isStopped() || ambient.rift != near || !mc.getSoundManager().isActive(ambient)) {
                if (ambient != null) ambient.end();
                ambient = new Ambient(near);
                mc.getSoundManager().play(ambient);
            }
        } else if (ambient != null) {
            ambient.end();
            ambient = null;
        }
        // Estrellas fugaces
        for (Iterator<Meteor> it = METEORS.iterator(); it.hasNext(); ) {
            Meteor me = it.next();
            if (++me.age > me.life) it.remove();
        }
        if (strength > 0.25F && --nextMeteor <= 0) {
            Random r = new Random(ticks * 31L + 7);
            METEORS.add(Meteor.random(r));
            if (r.nextInt(5) == 0) METEORS.add(Meteor.random(r)); // a veces dos a la vez
            nextMeteor = 12 + r.nextInt(50);
        }
    }

    private static float smooth(double a, double b, double x) {
        double t = Mth.clamp((x - a) / (b - a), 0, 1);
        return (float) (t * t * (3 - 2 * t));
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float s = strength((float) event.getPartialTick()) * 0.92F;
        if (s <= 0.001F) return;
        event.setRed(Mth.lerp(s, event.getRed(), (float) FOG.x));
        event.setGreen(Mth.lerp(s, event.getGreen(), (float) FOG.y));
        event.setBlue(Mth.lerp(s, event.getBlue(), (float) FOG.z));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        float partial = event.getPartialTick();
        float s = strength(partial);
        if (s <= 0.001F) return;
        try {
            draw(event.getPoseStack(), s, partial);
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Jefes: error al dibujar el cielo de la grieta", t);
            strength = prevStrength = 0;
        }
    }

    // ------------------------------------------------------------------------------------------- dibujo

    private static void draw(PoseStack pose, float s, float partial) {
        Minecraft mc = Minecraft.getInstance();
        float time = (mc.level.getGameTime() % 2400000L) + partial;
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        pose.pushPose();
        // El vacío gira muy despacio (una vuelta cada ~17 min) con el eje algo inclinado
        pose.mulPose(Axis.ZP.rotationDegrees(18F));
        pose.mulPose(Axis.YP.rotationDegrees(time * 0.018F));
        Matrix4f m = pose.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();

        // 1. El panorama de nebulosas, encima del cielo de Minecraft
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SKY);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int lonN = 48, latN = 24;
        int a = (int) (s * 255);
        for (int i = 0; i < latN; i++) {
            float v0 = i / (float) latN, v1 = (i + 1) / (float) latN;
            for (int j = 0; j < lonN; j++) {
                float u0 = j / (float) lonN, u1 = (j + 1) / (float) lonN;
                sphere(buf, m, u0, v0, a);
                sphere(buf, m, u0, v1, a);
                sphere(buf, m, u1, v1, a);
                sphere(buf, m, u1, v0, a);
            }
        }
        BufferUploader.drawWithShader(buf.end());

        // 2. Nubes de nebulosa que laten (luz que suma)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderTexture(0, CLOUD);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Nebula n : NEBULAS) {
            float pulse = n.alpha * (0.65F + 0.35F * Mth.sin(time * 0.012F + n.phase));
            billboard(buf, m, n.dir, 92F, n.size, n.angle + time * n.spin * 0.01F, 0.72F, 0.42F, 1F, pulse * s);
        }
        BufferUploader.drawWithShader(buf.end());

        // 3. Estrellas que titilan
        RenderSystem.setShaderTexture(0, STAR);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Star st : STARS) {
            float tw = 0.55F + 0.45F * Mth.sin(time * 0.05F * st.speed + st.phase);
            billboard(buf, m, st.dir, 90F, st.size * (0.8F + 0.4F * tw), 0F, st.r, st.g, st.b, tw * s);
        }
        // y la cabeza de las fugaces
        for (Meteor me : METEORS) {
            float k = (me.age + partial) / me.life;
            Vector3f head = me.at(k);
            billboard(buf, m, head, 89F, 2.2F * me.size, 0F, 0.95F, 0.85F, 1F, me.fade(k) * s);
        }
        BufferUploader.drawWithShader(buf.end());

        // 4. Las estelas de las estrellas fugaces (blanco violeta que se apaga hacia atrás)
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Meteor me : METEORS) {
            float k = (me.age + partial) / me.life;
            float fade = me.fade(k) * s;
            int seg = 14;
            for (int i = 0; i < seg; i++) {
                float k0 = k - me.tail * i / seg, k1 = k - me.tail * (i + 1) / seg;
                Vector3f p0 = me.at(k0).mul(88F), p1 = me.at(k1).mul(88F);
                Vector3f side = new Vector3f(p0).cross(new Vector3f(p1).sub(p0)).normalize();
                float w0 = 0.32F * me.size * (1F - i / (float) seg), w1 = 0.32F * me.size * (1F - (i + 1) / (float) seg);
                float a0 = fade * (1F - i / (float) seg), a1 = fade * (1F - (i + 1) / (float) seg);
                float g0 = 0.75F + 0.25F * (1 - i / (float) seg);
                quadColor(buf, m, p0, p1, side, w0, w1, g0, a0, a1);
            }
        }
        BufferUploader.drawWithShader(buf.end());

        pose.popPose();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /** Un punto de la esfera del panorama (las mismas cuentas que rift_sky.py: u = longitud, v = latitud). */
    private static void sphere(BufferBuilder buf, Matrix4f m, float u, float v, int a) {
        double lon = u * Math.PI * 2, lat = (0.5 - v) * Math.PI;
        float x = (float) (Math.cos(lat) * Math.cos(lon)) * 100F, y = (float) Math.sin(lat) * 100F,
                z = (float) (Math.cos(lat) * Math.sin(lon)) * 100F;
        buf.vertex(m, x, y, z).uv(u, v).color(255, 255, 255, a).endVertex();
    }

    /** Un cuadrado de cara a la cámara en la dirección «dir», a «dist», de lado «size», girado «angle» grados. */
    private static void billboard(BufferBuilder buf, Matrix4f m, Vector3f dir, float dist, float size, float angle,
                                  float r, float g, float b, float alpha) {
        if (alpha <= 0.003F) return;
        Vector3f c = new Vector3f(dir).mul(dist);
        Vector3f up = Math.abs(dir.y) > 0.95F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f u = new Vector3f(dir).cross(up).normalize();
        Vector3f w = new Vector3f(u).cross(dir).normalize();
        float ca = Mth.cos(angle * Mth.DEG_TO_RAD) * size * 0.5F, sa = Mth.sin(angle * Mth.DEG_TO_RAD) * size * 0.5F;
        Vector3f ax = new Vector3f(u).mul(ca).add(new Vector3f(w).mul(sa));
        Vector3f ay = new Vector3f(w).mul(ca).sub(new Vector3f(u).mul(sa));
        int ai = (int) (Mth.clamp(alpha, 0, 1) * 255), ri = (int) (r * 255), gi = (int) (g * 255), bi = (int) (b * 255);
        buf.vertex(m, c.x - ax.x - ay.x, c.y - ax.y - ay.y, c.z - ax.z - ay.z).uv(0, 0).color(ri, gi, bi, ai).endVertex();
        buf.vertex(m, c.x - ax.x + ay.x, c.y - ax.y + ay.y, c.z - ax.z + ay.z).uv(0, 1).color(ri, gi, bi, ai).endVertex();
        buf.vertex(m, c.x + ax.x + ay.x, c.y + ax.y + ay.y, c.z + ax.z + ay.z).uv(1, 1).color(ri, gi, bi, ai).endVertex();
        buf.vertex(m, c.x + ax.x - ay.x, c.y + ax.y - ay.y, c.z + ax.z - ay.z).uv(1, 0).color(ri, gi, bi, ai).endVertex();
    }

    private static void quadColor(BufferBuilder buf, Matrix4f m, Vector3f p0, Vector3f p1, Vector3f side, float w0, float w1,
                                  float g, float a0, float a1) {
        int ia0 = (int) (Mth.clamp(a0, 0, 1) * 255), ia1 = (int) (Mth.clamp(a1, 0, 1) * 255);
        int r = 235, gg = (int) (190 * g), b = 255;
        buf.vertex(m, p0.x + side.x * w0, p0.y + side.y * w0, p0.z + side.z * w0).color(r, gg, b, ia0).endVertex();
        buf.vertex(m, p0.x - side.x * w0, p0.y - side.y * w0, p0.z - side.z * w0).color(r, gg, b, ia0).endVertex();
        buf.vertex(m, p1.x - side.x * w1, p1.y - side.y * w1, p1.z - side.z * w1).color(180, 90, 255, ia1).endVertex();
        buf.vertex(m, p1.x + side.x * w1, p1.y + side.y * w1, p1.z + side.z * w1).color(180, 90, 255, ia1).endVertex();
    }

    private static Vector3f randomDir(Random r, float minY) {
        while (true) {
            Vector3f v = new Vector3f(r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1);
            float l = v.length();
            if (l > 0.05F && l <= 1F && v.y / l >= minY) return v.div(l);
        }
    }

    // ------------------------------------------------------------------------------------------- piezas

    private record Star(Vector3f dir, float size, float r, float g, float b, float speed, float phase) {}

    private record Nebula(Vector3f dir, float size, float angle, float spin, float phase, float alpha) {}

    /** Una estrella fugaz: va de «from» girando hacia «to» (en la esfera) en «life» ticks, con su estela detrás. */
    private static final class Meteor {
        final Vector3f from, toward;
        final float arc, tail, size;
        final int life;
        int age;

        private Meteor(Vector3f from, Vector3f toward, float arc, float tail, float size, int life) {
            this.from = from;
            this.toward = toward;
            this.arc = arc;
            this.tail = tail;
            this.size = size;
            this.life = life;
        }

        static Meteor random(Random r) {
            Vector3f from = randomDir(r, 0.25F);
            // hacia dónde: perpendicular a donde está, algo hacia abajo (caen)
            Vector3f any = new Vector3f(r.nextFloat() - 0.5F, -0.6F - r.nextFloat() * 0.6F, r.nextFloat() - 0.5F);
            Vector3f toward = any.sub(new Vector3f(from).mul(any.dot(from))).normalize();
            return new Meteor(from, toward, 0.35F + r.nextFloat() * 0.45F, 0.35F + r.nextFloat() * 0.25F,
                    0.7F + r.nextFloat() * 0.8F, 14 + r.nextInt(16));
        }

        /** La dirección cuando va por «k» (0..1) de su recorrido. */
        Vector3f at(float k) {
            float ang = arc * Mth.clamp(k, -1F, 1F);
            return new Vector3f(from).mul(Mth.cos(ang)).add(new Vector3f(toward).mul(Mth.sin(ang)));
        }

        /** Aparece rápido y se apaga al final. */
        float fade(float k) {
            return Mth.clamp(k * 6F, 0, 1) * Mth.clamp((1F - k) * 3F, 0, 1);
        }
    }

    /** El zumbido del vacío: en bucle, en la grieta, mientras estés cerca. */
    private static final class Ambient extends AbstractTickableSoundInstance {
        final RiftEntity rift;
        private boolean ending;

        Ambient(RiftEntity rift) {
            super(BossSounds.RIFT_AMBIENT, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.rift = rift;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.x = rift.getX();
            this.y = rift.getY() + 3;
            this.z = rift.getZ();
        }

        void end() {
            ending = true;
        }

        @Override
        public void tick() {
            if (rift.isRemoved()) {
                stop();
                return;
            }
            // entra y sale suave
            volume = ending ? volume - 0.05F : Math.min(1F, volume + 0.04F);
            if (volume <= 0F) stop();
        }
    }
}
