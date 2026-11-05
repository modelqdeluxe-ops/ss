/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.luna.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.tierrasfantasticas.tfclient.TFClient;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * La escena de la Luna de Sangre, para todos los que la ven a la vez (16 s; el dueño: «cuando se active la luna de
 * sangre quiero que salte una escena para todos con el nacimiento de la luna, una musiquita y un título o explicación
 * diciendo la luna de sangre se muestra, los mobs hostiles son más agresivos y peligrosos (escena de mobs tipo zombie o
 * esqueletos activándose los ojos rojos brillantes), fin de la escena, y comienza»). Los tiempos van con la música
 * ({@code tools/luna/musica_luna.py}, escena.ogg):
 * <ul>
 *   <li>0–7,5 s: la cámara sube por encima del jugador (y del terreno) y mira al este: la luna roja nace por el
 *       horizonte y el cielo se va tiñendo; a los 3 s, el título.</li>
 *   <li>7,5–14 s: en la oscuridad, dos esqueletos y dos zombis con la cabeza gacha; a los 8,6 / 9,6 / 10,6 / 11,6 s
 *       (los golpes de la música) a cada uno se le encienden los ojos rojos y levanta la cabeza. Las explicaciones.</li>
 *   <li>14–16 s: vuelve el mundo; la noche empieza (la música en bucle, los ojos de los mobs de verdad).</li>
 * </ul>
 * Mientras dura, el servidor no deja que los mobs hagan daño. Con Esc se salta.
 */
@OnlyIn(Dist.CLIENT)
public final class BloodMoonScene extends Screen {
    public static final int TOTAL = 320, PHASE_B = 150, PHASE_C = 280;
    private static final float FOV = 40F;
    static final int[] IGNITE = {172, 192, 212, 232};

    private static BloodMoonScene current;
    /** Mientras se pintan los cuerpos (primera pasada): los ojos, después y sin oscurecer. */
    static boolean suppressEyes;
    /** Para la prueba en el juego. */
    public static int opened, finished, castRendered;
    /**
     * Para grabar el vídeo de la prueba: el reloj de la escena a mano (en ticks; -1 = el de verdad). Con él, cada
     * fotograma del vídeo es 1/30 s de escena aunque la máquina de la prueba vaya lenta.
     */
    public static float fixedTime = -1F;

    private int tick;
    private boolean hidGui;
    private Vec3 cam = Vec3.ZERO;
    private final List<Mob> cast = new ArrayList<>();
    private final Map<Entity, Integer> igniteAt = new IdentityHashMap<>();
    private SoundInstance sound;

    private BloodMoonScene() {
        super(Component.literal("Luna de Sangre"));
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (current != null || mc.player == null || mc.level == null) return;
        mc.setScreen(new BloodMoonScene());
    }

    public static void close() {
        Minecraft mc = Minecraft.getInstance();
        if (current != null && mc.screen == current) mc.setScreen(null);
        current = null;
    }

    public static boolean running() {
        return current != null;
    }

    public static float time(float partial) {
        return current == null ? 0F : current.now(partial);
    }

    private float now(float partial) {
        return fixedTime >= 0F ? fixedTime : tick + partial;
    }

    @Override
    protected void init() {
        if (current == this) return;
        current = this;
        opened++;
        Minecraft mc = Minecraft.getInstance();
        hidGui = mc.options.hideGui;
        mc.options.hideGui = true;
        // la cámara: encima del jugador y por encima del terreno, para ver el horizonte
        Vec3 eye = mc.player.getEyePosition();
        int surface = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(eye.x), Mth.floor(eye.z));
        cam = new Vec3(eye.x, Math.max(eye.y, surface + 2.0) + 5.0, eye.z);
        mc.getMusicManager().stopPlaying();
        sound = new SimpleSoundInstance(BloodMoonClient.SCENE.getLocation(), SoundSource.RECORDS, 1F, 1F, RandomSource.create(), false, 0,
                SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        mc.getSoundManager().play(sound);
        buildCast(mc);
    }

    private void buildCast(Minecraft mc) {
        EntityType<?>[] types = {EntityType.SKELETON, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SKELETON};
        double[] xs = {-2.15, -0.72, 0.72, 2.15}, zs = {0.45, 0.0, 0.05, 0.4};
        // el orden en que se encienden: los dos del centro, luego los de los lados
        int[] order = {2, 0, 1, 3};
        for (int i = 0; i < types.length; i++) {
            Entity e = types[i].create(mc.level);
            if (!(e instanceof Mob m)) continue;
            m.setPos(xs[i], 0, zs[i]);
            m.setOldPosAndRot();
            m.setYRot(0F);
            m.yBodyRot = m.yBodyRotO = 0F;
            m.yHeadRot = m.yHeadRotO = (i - 1.5F) * -6F;
            m.setXRot(28F);
            m.xRotO = 28F;
            if (types[i] == EntityType.SKELETON) m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            if (i == 2) m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            m.setAggressive(true);
            cast.add(m);
            igniteAt.put(m, IGNITE[order[i]]);
        }
    }

    @Override
    public void tick() {
        tick++;
        if (now(0F) >= TOTAL) onClose();
    }

    /** La pose de los actores en el instante t (la cabeza se levanta al encenderse sus ojos). */
    private void pose(float t) {
        for (Mob m : cast) {
            m.tickCount = (int) t;
            int at = igniteAt.getOrDefault(m, 999);
            float pitch = Mth.lerp(ease((t - at) / 6F), 28F, -4F);
            m.setXRot(pitch);
            m.xRotO = pitch;
            m.yHeadRotO = m.yHeadRot;
        }
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = hidGui;
        if (sound != null && now(0F) < TOTAL) mc.getSoundManager().stop(sound);
        if (current == this) current = null;
        finished++;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return now(0F) > 20;
    }

    // ------------------------------------------------------------------------------------------------- lo que ve el mundo

    /** Lo rojo del cielo durante la escena. */
    static float skyAmount() {
        if (current == null) return 0F;
        float t = current.now(0F);
        // (1.3.176) desde el primer fotograma un poco: la luna ya sale roja (en el vídeo se veía blanca medio segundo)
        return Mth.clamp(0.12F + t / 125F, 0F, 1F);
    }

    static float ease(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3 - 2 * x);
    }

    /** La luna que nace: de 3° bajo el horizonte a 17° sobre él (luego, la de verdad). */
    static float moonTimeOfDay(float real, float partial) {
        if (current == null) return real;
        float t = current.now(partial) / PHASE_B;
        if (t >= 1F) return real;
        double el = Mth.lerp(ease(t), Math.toRadians(-3), Math.toRadians(17));
        double theta = Math.acos(-Math.sin(el));
        return (float) (theta / (Math.PI * 2));
    }

    /** ¿Manda la escena en la cámara? (la primera parte; mixin.luna.CameraLunaMixin) */
    public static boolean cameraActive() {
        return current != null && current.now(0F) < PHASE_B + 2;
    }

    public static Vec3 cameraPos(float partial) {
        float t = time(partial) / PHASE_B;
        return current.cam.add(0, ease(t) * 1.5, 0);
    }

    public static float cameraYaw(float partial) {
        // al este (la luna sale por ahí), con un giro muy lento
        return -90F + (ease(time(partial) / PHASE_B) - 0.5F) * 6F;
    }

    /** (1.3.177) Mirando al cielo (el dueño: «solo quiero una escena del cielo con la luna de sangre»). */
    public static float cameraPitch(float partial) {
        return Mth.lerp(ease(time(partial) / PHASE_B), -14F, -19F);
    }

    /** La luz de los ojos de un actor de la escena (o -1 si no es de la escena). */
    static float eyesOf(Entity e) {
        if (current == null) return -1F;
        Integer at = current.igniteAt.get(e);
        if (at == null) return -1F;
        float t = current.now(0F) - at;
        if (t < 0) return 0F;
        // fogonazo y se queda
        return t < 4 ? t / 4F * 1.6F : Math.max(1F, 1.6F - (t - 4) * 0.08F);
    }

    static boolean isCast(Entity e) {
        return current != null && current.igniteAt.containsKey(e);
    }

    // ------------------------------------------------------------------------------------------------- dibujar

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        float t = now(partial);
        int w = width, h = height;
        if (t >= PHASE_B && t < PHASE_C + 6) {
            drawCastScene(g, t, partial);
        }
        // fundidos a negro: al cambiar de parte y al volver al mundo
        float black = 0F;
        if (t > PHASE_B - 12 && t < PHASE_B) black = (t - (PHASE_B - 12)) / 12F;
        else if (t >= PHASE_B && t < PHASE_B + 10) black = 1F - (t - PHASE_B) / 10F;
        else if (t >= PHASE_C - 10 && t < PHASE_C) black = (t - (PHASE_C - 10)) / 10F;
        else if (t >= PHASE_C) black = 1F - Mth.clamp((t - PHASE_C) / 30F, 0F, 1F);
        if (black > 0.001F) g.fill(0, 0, w, h, (int) (Mth.clamp(black, 0F, 1F) * 255) << 24);
        // franjas de cine
        float bars = t < PHASE_C ? Mth.clamp(t / 14F, 0F, 1F) : 1F - Mth.clamp((t - PHASE_C) / 30F, 0F, 1F);
        int bh = (int) (h * 0.11F * ease(bars));
        if (bh > 0) {
            g.fill(0, 0, w, bh, 0xFF000000);
            g.fill(0, h - bh, w, h, 0xFF000000);
        }
        texts(g, t, w, h);
    }

    /** (1.3.177) Los textos son imágenes con letra Cinzel (tools/luna/texturas_luna.py), no la de Minecraft estirada. */
    static final net.minecraft.resources.ResourceLocation TITLE = BloodMoonClient.tf("textures/gui/luna/titulo.png"),
            SUBTITLE = BloodMoonClient.tf("textures/gui/luna/subtitulo.png");
    static final net.minecraft.resources.ResourceLocation[] LINES = {BloodMoonClient.tf("textures/gui/luna/linea1.png"),
            BloodMoonClient.tf("textures/gui/luna/linea2.png"), BloodMoonClient.tf("textures/gui/luna/linea3.png")};
    /** Tamaño de cada imagen (px): título, subtítulo y las tres líneas. */
    static final int[][] SIZES = {{1228, 194}, {986, 83}, {1087, 77}, {952, 77}, {1291, 74}};

    private void texts(GuiGraphics g, float t, int w, int h) {
        // el título, a los 3 s, arriba (sobre el cielo), y lo de debajo
        float a1 = Mth.clamp((t - 60) / 18F, 0F, 1F) * (1F - Mth.clamp((t - (PHASE_B - 14)) / 10F, 0F, 1F));
        if (a1 > 0.02F) {
            float rise = (1F - ease(Mth.clamp((t - 60) / 30F, 0F, 1F))) * 6F;
            image(g, TITLE, SIZES[0], w / 2F, h * 0.20F + rise, w * 0.52F, a1);
            float a2 = Mth.clamp((t - 92) / 16F, 0F, 1F) * a1;
            if (a2 > 0.02F) image(g, SUBTITLE, SIZES[1], w / 2F, h * 0.20F + w * 0.52F * 194F / 1228F * 0.55F + 10, w * 0.44F, a2);
        }
        int[] at = {160, 205, 245};
        float lineH = w * 0.50F * 77F / 1087F;
        for (int i = 0; i < 3; i++) {
            float a = Mth.clamp((t - at[i]) / 12F, 0F, 1F) * (1F - Mth.clamp((t - (PHASE_C - 4)) / 10F, 0F, 1F));
            float width = w * 0.50F * SIZES[2 + i][0] / 1087F;
            if (a > 0.02F) image(g, LINES[i], SIZES[2 + i], w / 2F, h * 0.76F + (i - 1) * lineH * 0.85F, width, a);
        }
    }

    /** Una imagen centrada en (cx, cy), de este ancho, con transparencia (suavizada al escalar). */
    private static void image(GuiGraphics g, net.minecraft.resources.ResourceLocation tex, int[] size, float cx, float cy, float width, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().getTexture(tex).setFilter(true, false);
        float height = width * size[1] / size[0];
        PoseStack p = g.pose();
        p.pushPose();
        p.translate(cx - width / 2F, cy - height / 2F, 0F);
        p.scale(width / size[0], height / size[1], 1F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, Mth.clamp(alpha, 0F, 1F));
        g.blit(tex, 0, 0, 0, 0F, 0F, size[0], size[1], size[0], size[1]);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        p.popPose();
    }

    private void big(GuiGraphics g, String text, float cx, float y, float scale, int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255), 4, 255);
        PoseStack p = g.pose();
        p.pushPose();
        p.translate(cx, y, 0);
        p.scale(scale, scale, 1F);
        int tw = font.width(text);
        g.drawString(font, text, -tw / 2, 0, (a << 24) | (rgb & 0xFFFFFF), true);
        p.popPose();
    }

    /** La capa roja oscura de la segunda parte: un cuadro delante de la cámara, sin profundidad. */
    private static void darken(float aspect) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float z = -0.2F, hh = 0.2F * (float) Math.tan(Math.toRadians(FOV / 2)) * 1.5F, hw = hh * aspect;
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        b.vertex(-hw, -hh, z).color(42, 0, 4, 122).endVertex();
        b.vertex(hw, -hh, z).color(42, 0, 4, 122).endVertex();
        b.vertex(hw, hh, z).color(42, 0, 4, 122).endVertex();
        b.vertex(-hw, hh, z).color(42, 0, 4, 122).endVertex();
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** La segunda parte: el fondo oscuro con la luna, los cuatro mobs y sus ojos. */
    private void drawCastScene(GuiGraphics g, float t, float partial) {
        Minecraft mc = Minecraft.getInstance();
        int w = width, h = height;
        // el fondo: negro con un resplandor rojo arriba (la luna detrás)
        g.fillGradient(0, 0, w, h, 0xFF120203, 0xFF030000);
        float glow = 0.55F + 0.1F * Mth.sin(t * 0.08F);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(1F, 1F, 1F, glow);
        int hs = (int) (h * 0.9F);
        g.blit(BloodMoonClient.HALO, w / 2 - hs / 2, (int) (-h * 0.18F), 0, 0F, 0F, hs, hs, hs, hs);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.defaultBlendFunc();
        g.flush();
        pose(t);
        // los mobs, con perspectiva
        float k = (t - PHASE_B) / (PHASE_C - PHASE_B);
        double camZ = Mth.lerp(ease(k), 7.6, 6.2), camY = 1.2;
        float pitch = 2F;
        Matrix4f proj = new Matrix4f().setPerspective((float) Math.toRadians(FOV), (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight()), 0.05F, 60F);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        Lighting.setupLevel(new Matrix4f(pose.last().pose()));
        pose.translate(0, -camY, -camZ);
        boolean shadows = mc.options.entityShadows().get();
        d.setRenderShadow(false);
        try {
            // 1) los cuerpos, a media luz roja (sin los ojos)
            suppressEyes = true;
            int body = LightTexture.pack(9, 6);
            for (Mob m : cast) d.render(m, m.getX(), m.getY(), m.getZ(), 0F, partial, pose, buffers, body);
            buffers.endBatch();
            suppressEyes = false;
            // 2) una capa roja oscura encima de todo, sin escribir profundidad (si la escribiera, taparía los ojos:
            // 1.ª prueba, los ojos de la escena salían a 71 de rojo y los del mundo a 255)
            darken((float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight()));
            // 3) solo los ojos, encima (invisibles y sin lo que llevan en la mano: solo queda la capa de los ojos)
            for (Mob m : cast) {
                ItemStack held = m.getMainHandItem();
                m.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                m.setInvisible(true);
                d.render(m, m.getX(), m.getY(), m.getZ(), 0F, partial, pose, buffers, LightTexture.FULL_BRIGHT);
                buffers.endBatch();
                m.setInvisible(false);
                m.setItemSlot(EquipmentSlot.MAINHAND, held);
            }
            castRendered++;
        } catch (Throwable e) {
            TFClient.LOGGER.error("TF Luna de Sangre: no se pudo dibujar la escena de los ojos", e);
        } finally {
            suppressEyes = false;
            d.setRenderShadow(shadows);
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            Lighting.setupFor3DItems();
            RenderSystem.disableDepthTest();
        }
    }
}
