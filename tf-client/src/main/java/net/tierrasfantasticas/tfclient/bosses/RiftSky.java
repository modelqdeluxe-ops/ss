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
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * El ambiente del vacío. 1.3.86-1.3.87: el cielo de las grietas (nebulosas, estrellas, fugaces, un sol negro) salía al
 * acercarse a una. 1.3.88 (el dueño: «el sol me sigue, quítalo; el cielo será de la dimensión, ya no al acercarse a la
 * grieta»): el cielo es el de la dimensión The Void ({@code vacio.VoidSky}, con el agujero negro) y aquí queda lo que
 * le acompaña allí: las estrellas fugaces (una estela con textura que sigue su arco, a veces un bólido grande y lento),
 * la niebla morada, la música siniestra (grieta.musica, en «Discos» como la de los jefes: entra en 5 s, se va en 2 al
 * despertar una grieta o al sonar la de un jefe y vuelve después) y el zumbido de las grietas cercanas (en cualquier
 * dimensión). Solo en el cliente: no cambia nada del mundo.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class RiftSky {
    private static final ResourceLocation STAR = new ResourceLocation(TFClient.MOD_ID, "textures/environment/grieta_estrella.png");
    private static final ResourceLocation STREAK = new ResourceLocation(TFClient.MOD_ID, "textures/environment/grieta_fugaz.png");
    /** El color al que van la niebla y el cielo de Minecraft (y las nubes, algo más claras). */
    public static final Vec3 FOG = new Vec3(0.045, 0.012, 0.085), CLOUDS = new Vec3(0.30, 0.13, 0.45);

    private static float strength, prevStrength;
    private static Ambient ambient;
    private static RiftMusic music;
    /** ¿Toca la música del vacío ahora? (aunque no haya sonido: la prueba de jefes del CI no tiene tarjeta) */
    private static boolean musicWanted;
    private static int ticks, nextMeteor = 60;
    private static final List<Meteor> METEORS = new ArrayList<>();

    /** ¿Suena la música del vacío? (la normal de Minecraft espera: MusicManagerMixin). */
    public static boolean musicActive() {
        return music != null && !music.isStopped();
    }

    /** ¿Debería sonar? (en el vacío, sin pelea cerca y sin la música de un jefe) */
    public static boolean musicWanted() {
        return musicWanted;
    }

    private RiftSky() {}

    /** Cuánto ambiente del vacío hay (0..1): 1 en The Void, 0 fuera. Para el mixin del color del cielo y las nubes. */
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
            METEORS.clear();
            if (music != null) mc.getSoundManager().stop(music);
            music = null;
            musicWanted = false;
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        boolean inVoid = net.tierrasfantasticas.tfclient.vacio.TFVoid.isVoid(mc.level);
        // Al cambiar de dimensión no hay fundido (la pantalla de carga ya lo tapa)
        strength = prevStrength = inVoid ? 1F : 0F;
        RiftEntity near = null;
        double nearD = Double.MAX_VALUE;
        boolean battle = false;
        for (RiftEntity e : mc.level.getEntitiesOfClass(RiftEntity.class, mc.player.getBoundingBox().inflate(128))) {
            double d = e.distanceTo(mc.player);
            int st = e.state();
            if ((st == RiftEntity.WAKING || st == RiftEntity.FIGHT) && d < 128) battle = true;
            if (d < nearD) {
                nearD = d;
                near = e;
            }
        }
        // La música siniestra: en el vacío y sin pelea; se va al despertar una grieta y vuelve al callarse la del jefe
        boolean wantMusic = inVoid && !battle && !net.tierrasfantasticas.tfclient.client.TFBossMusic.isActive();
        musicWanted = wantMusic;
        // (si el motor de sonido la ha soltado sin pararla, p. ej. al recargar los sonidos, se vuelve a poner)
        if (music != null && !music.isStopped() && music.age > 40 && !mc.getSoundManager().isActive(music)) music = null;
        if (music != null && !music.isStopped()) music.age++;
        if (wantMusic) {
            if (music == null || music.isStopped()) {
                music = new RiftMusic();
                mc.getSoundManager().play(music);
                mc.getMusicManager().stopPlaying();
            } else {
                music.leaving = false;
            }
        } else if (music != null && !music.isStopped()) {
            music.leaving = true;
        }
        // El zumbido del vacío, en la grieta más cercana
        if (near != null && nearD < 40) {
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
        if (inVoid && --nextMeteor <= 0) {
            Random r = new Random(ticks * 31L + 7);
            METEORS.add(Meteor.random(r, r.nextInt(6) == 0));
            if (r.nextInt(6) == 0) METEORS.add(Meteor.random(r, false)); // a veces dos a la vez
            nextMeteor = 50 + r.nextInt(130);
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float s = strength((float) event.getPartialTick()) * 0.92F;
        if (s <= 0.001F) return;
        event.setRed(Mth.lerp(s, event.getRed(), (float) FOG.x));
        event.setGreen(Mth.lerp(s, event.getGreen(), (float) FOG.y));
        event.setBlue(Mth.lerp(s, event.getBlue(), (float) FOG.z));
    }

    // ------------------------------------------------------------------------------------------- dibujo

    /** Las estrellas fugaces (las dibuja el cielo del vacío, encima del agujero negro). */
    public static void drawMeteors(PoseStack pose, float partial) {
        if (METEORS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float time = (mc.level.getGameTime() % 2400000L) + partial;
        float s = 1F;
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(18F));
        pose.mulPose(Axis.YP.rotationDegrees(time * 0.018F));
        Matrix4f m = pose.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        // La estela con textura que sigue su arco, cabeza brillante y cola que se apaga
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderTexture(0, STREAK);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Meteor me : METEORS) {
            float k = (me.age + partial) / me.life;
            float fade = me.fade(k) * s;
            if (fade <= 0.003F) continue;
            int seg = 16;
            for (int i = 0; i < seg; i++) {
                float k0 = k - me.tail * i / seg, k1 = k - me.tail * (i + 1) / seg;
                Vector3f p0 = me.at(k0).mul(88F), p1 = me.at(k1).mul(88F);
                Vector3f side = new Vector3f(p0).cross(new Vector3f(p1).sub(p0)).normalize();
                streak(buf, m, p0, p1, side, me.size * 0.55F, 1F - i / (float) seg, 1F - (i + 1) / (float) seg, me, fade);
            }
        }
        BufferUploader.drawWithShader(buf.end());
        // y el resplandor de la cabeza de los bólidos
        RenderSystem.setShaderTexture(0, STAR);
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Meteor me : METEORS) {
            if (!me.bolide) continue;
            float k = (me.age + partial) / me.life;
            billboard(buf, m, me.at(k), 87F, 3.2F * me.size, 0F, me.r, me.g, me.b, me.fade(k) * s * 0.8F);
        }
        BufferUploader.drawWithShader(buf.end());
        pose.popPose();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
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

    /** Un trozo de la estela entre p0 (u0, más cerca de la cabeza) y p1 (u1): la textura pone la forma. */
    private static void streak(BufferBuilder buf, Matrix4f m, Vector3f p0, Vector3f p1, Vector3f side, float w, float u0,
                               float u1, Meteor me, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255), r = (int) (me.r * 255), g = (int) (me.g * 255), b = (int) (me.b * 255);
        buf.vertex(m, p0.x + side.x * w, p0.y + side.y * w, p0.z + side.z * w).uv(u0, 0).color(r, g, b, a).endVertex();
        buf.vertex(m, p0.x - side.x * w, p0.y - side.y * w, p0.z - side.z * w).uv(u0, 1).color(r, g, b, a).endVertex();
        buf.vertex(m, p1.x - side.x * w, p1.y - side.y * w, p1.z - side.z * w).uv(u1, 1).color(r, g, b, a).endVertex();
        buf.vertex(m, p1.x + side.x * w, p1.y + side.y * w, p1.z + side.z * w).uv(u1, 0).color(r, g, b, a).endVertex();
    }

    private static Vector3f randomDir(Random r, float minY) {
        while (true) {
            Vector3f v = new Vector3f(r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1);
            float l = v.length();
            if (l > 0.05F && l <= 1F && v.y / l >= minY) return v.div(l);
        }
    }

    // ------------------------------------------------------------------------------------------- piezas

    /**
     * Una estrella fugaz: va de «from» girando hacia «toward» (en la esfera) en «life» ticks, con su estela detrás. Las
     * normales son finas, rápidas y blanco lavanda; los bólidos, gruesos, lentos, rosa violeta y con resplandor.
     */
    private static final class Meteor {
        final Vector3f from, toward;
        final float arc, tail, size, r, g, b;
        final boolean bolide;
        final int life;
        int age;

        private Meteor(Vector3f from, Vector3f toward, float arc, float tail, float size, int life, boolean bolide) {
            this.from = from;
            this.toward = toward;
            this.arc = arc;
            this.tail = tail;
            this.size = size;
            this.life = life;
            this.bolide = bolide;
            this.r = bolide ? 1F : 0.92F;
            this.g = bolide ? 0.62F : 0.86F;
            this.b = 1F;
        }

        static Meteor random(Random r, boolean bolide) {
            Vector3f from = randomDir(r, 0.3F);
            // hacia dónde: perpendicular a donde está, algo hacia abajo (caen)
            Vector3f any = new Vector3f(r.nextFloat() - 0.5F, -0.6F - r.nextFloat() * 0.6F, r.nextFloat() - 0.5F);
            Vector3f toward = any.sub(new Vector3f(from).mul(any.dot(from))).normalize();
            if (bolide) return new Meteor(from, toward, 0.5F + r.nextFloat() * 0.3F, 0.3F + r.nextFloat() * 0.1F,
                    2.2F + r.nextFloat() * 0.8F, 45 + r.nextInt(20), true);
            return new Meteor(from, toward, 0.3F + r.nextFloat() * 0.35F, 0.4F + r.nextFloat() * 0.2F,
                    0.8F + r.nextFloat() * 0.6F, 16 + r.nextInt(12), false);
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

    /** La música siniestra de la grieta: en bucle, sin posición, con fundidos (como la de los jefes: TFBossMusic). */
    private static final class RiftMusic extends AbstractTickableSoundInstance {
        private static final float VOLUME = 0.6F;
        private static final int FADE_IN = 100, FADE_OUT = 40;
        boolean leaving;
        int age;
        private float fade;

        RiftMusic() {
            super(BossSounds.RIFT_MUSIC, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001F;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            fade = leaving ? fade - 1F / FADE_OUT : Math.min(1F, fade + 1F / FADE_IN);
            if (fade <= 0F) {
                stop();
                return;
            }
            this.volume = Math.max(0.001F, fade * fade * VOLUME);
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
