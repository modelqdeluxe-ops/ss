package net.tierrasfantasticas.tfclient.crates.client.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.tierrasfantasticas.tfclient.crates.client.render.CrateBakedModels;
import net.tierrasfantasticas.tfclient.crates.client.render.CrateStyles;
import net.tierrasfantasticas.tfclient.crates.client.scene.CinematicFx;
import net.tierrasfantasticas.tfclient.crates.client.scene.SanctuaryScene;
import net.tierrasfantasticas.tfclient.crates.client.scene.ScenePost;
import net.tierrasfantasticas.tfclient.crates.config.CrateConfig;
import net.tierrasfantasticas.tfclient.crates.config.Rarity;
import net.tierrasfantasticas.tfclient.crates.crate.CrateCinematicTiming;
import net.tierrasfantasticas.tfclient.crates.item.CrateItems;
import net.tierrasfantasticas.tfclient.crates.registry.ModRegistry;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * La cinemática de apertura de las crates (TF Client 1.3.99): el Santuario de las Hadas en vivo (SanctuaryScene), como
 * la imagen que mandó el dueño («quiero exactamente esto»): un santuario en ruinas de piedra con musgo y enredaderas, la
 * crate en el altar con dos amatistas en medio del estanque turquesa, la cascada alta que baja por la escalera de agua
 * al fondo, las dos cascadas de los lados, los faroles con su cadena, las flores de esporas, las hadas y las chispas, y
 * el haz de sol que entra por el hueco de las copas. Antes: «una escena tipo de un santuario en la naturaleza con fuente
 * de agua, tipo ruinas, con un haz del sol sobre el lugar, una escena completa en vivo».
 *
 * <pre>
 *   0-20    el encuadre de la imagen del dueño (de frente, el altar con la crate y la cascada al fondo), que se acerca:
 *           el título de la crate y «✦ Santuario de las Hadas ✦»
 *   20-66   la llave: aparece en un remolino de luz, vuela hasta la cerradura, se alinea y entra
 *   70-80   gira; el anillo de runas se enciende sobre el agua y los chorros brillan del color de la rareza
 *   84-102  la crate tiembla, la luz se escapa por la tapa
 *   102     ¡se abre! la llave se deshace en chispas, la tapa salta, un pilar de luz sube, onda, ráfaga de pétalos
 *   110-252 la ruleta: los premios suben de la crate y giran en un anillo encima; frena en el premio
 *   258     el premio: destello, rayos, se planta delante; el nombre y la rareza
 *   360     fin (CrateCinematicTiming.TOTAL_TICKS; el servidor da el premio a los 362)
 * </pre>
 * La imagen pasa por ScenePost (el cielo del atardecer, el resplandor, los rayos de sol, los colores); si sus shaders no
 * cargan, se dibuja directa con el cielo en 2D. Si el bosque no se puede cargar o dibujar, sale la de siempre
 * (CrateCinematicClassic).
 */
public class CrateCinematicScreen extends Screen {
    private static final int TOTAL = CrateCinematicTiming.TOTAL_TICKS;
    static final int KEY_APPEAR = 20, KEY_ALIGN = 56, KEY_IN = 66, KEY_TURN0 = 70, KEY_TURN1 = 80;
    static final int RUNES0 = 74, RUNES1 = 96, TREMBLE0 = 84, OPEN = 102;
    static final int RING0 = 110, RING1 = 136, SPIN0 = 128, SPIN1 = 252;
    static final int REVEAL = CrateCinematicTiming.REVEAL_TICK;
    private static final int LANDING = 74, SLOTS = 12;
    private static final float RING_R = 1.6F;
    private static boolean faulted, postFailed;

    private final CrateConfig cfg;
    private final int rarityColor;
    private final Rarity winnerRarity;
    private final int winnerIndex;
    private final List<ItemStack> candidates;
    private final int[] candidateRarities;
    private final ItemStack keyStack;
    private int ticks;
    private boolean finished;
    private long openNanos;
    private int lastSoundTick = -1;
    private int lastReelIndex = -1;
    private int[] strip;
    private final List<SoundInstance> playing = new ArrayList<>();
    private final List<Track> tracks = new ArrayList<>();
    private ScenePost post;
    /** Para la prueba en el juego: fotogramas, tiempo medio y peor fotograma de la última cinemática. */
    public static volatile String testStats = "";
    private int frames;
    private double frameSum, frameMax;
    private long lastFrame;

    // la crate
    private boolean geom;
    private boolean cine;
    private BakedModel base, lid;
    private BlockState crateState;
    private float[] hinge;
    private float scale, centerX, centerZ, minY, crateH, frontZ, keyY;
    private boolean keyFlat;

    public CrateCinematicScreen(CrateConfig cfg, int rarityColor, int winnerRarity, int winnerIndex, List<ItemStack> candidates,
                                int[] candidateRarities) {
        super(Component.literal("Cinemática de cofre"));
        this.cfg = cfg == null ? new CrateConfig() : cfg;
        this.rarityColor = rarityColor & 0xFFFFFF;
        Rarity[] rv = Rarity.values();
        this.winnerRarity = rv[Math.max(0, Math.min(rv.length - 1, winnerRarity))];
        this.candidates = candidates;
        this.winnerIndex = candidates != null && !candidates.isEmpty() ? Math.max(0, Math.min(candidates.size() - 1, winnerIndex)) : 0;
        this.candidateRarities = candidateRarities == null ? new int[0] : candidateRarities;
        ItemStack key;
        try {
            key = this.cfg.uniqueKeyEnabled ? CrateItems.buildUniqueKey(this.cfg) : CrateItems.buildKey();
        } catch (Throwable t) {
            key = ItemStack.EMPTY;
        }
        this.keyStack = key;
    }

    /** La de siempre si el santuario falló en esta sesión (o no hay), si no esta. */
    public static Screen create(CrateConfig cfg, int rarityColor, int winnerRarity, int winnerIndex, List<ItemStack> candidates,
                                int[] candidateRarities) {
        if (faulted || SanctuaryScene.get() == null) {
            return new CrateCinematicClassic(cfg, rarityColor, winnerRarity, winnerIndex, candidates, candidateRarities);
        }
        return new CrateCinematicScreen(cfg, rarityColor, winnerRarity, winnerIndex, candidates, candidateRarities);
    }

    @Override
    protected void init() {
        SanctuaryScene sc = SanctuaryScene.get();
        if (sc != null) {
            try {
                sc.ensureBuilt();
            } catch (Throwable t) {
                fault(t);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticks++;
        // los avisos del juego (recetas, logros) no salen encima de la escena
        if (minecraft != null) minecraft.getToasts().clear();
        if (ticks >= TOTAL && !finished) {
            finished = true;
            onClose();
        }
    }

    private boolean canSkip() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(2);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return canSkip();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 32 || key == 257 || key == 256) && canSkip()) {
            onClose();
            return true;
        }
        return key == 256 || super.keyPressed(key, scan, mods);
    }

    /** El reloj de la cinemática, en ticks (para las capturas de la prueba en el juego). */
    public float clock() {
        return openNanos == 0L ? 0.0F : (float)((System.nanoTime() - openNanos) / 5.0E7);
    }

    @Override
    public void removed() {
        testStats = String.format("%d fotogramas, %.1f ms de media, el peor %.1f ms, %.1f s; %s", frames, frames == 0 ? 0 : frameSum / frames,
                frameMax, clock() / 20.0F, SanctuaryScene.get() == null ? "sin santuario" : SanctuaryScene.get().stats())
                + (faulted ? " (FALLÓ: la de siempre)" : "");
        Minecraft mc = Minecraft.getInstance();
        for (SoundInstance s : playing) mc.getSoundManager().stop(s);
        playing.clear();
        // la música y el ambiente no se cortan: se apagan despacio
        for (Track tr : tracks) tr.fadeOut(16);
        tracks.clear();
        if (post != null) {
            post.close();
            post = null;
        }
    }

    private void fault(Throwable t) {
        if (faulted) return;
        faulted = true;
        LogUtils.getLogger().error("[TF Crates] La cinemática del santuario falló: sigo con la de siempre", t);
        Minecraft mc = Minecraft.getInstance();
        int elapsed = ticks;
        mc.tell(() -> {
            if (mc.screen == this) {
                mc.setScreen(elapsed < 200 ? new CrateCinematicClassic(cfg, rarityColor, winnerRarity.ordinal(), winnerIndex, candidates,
                        candidateRarities) : null);
            }
        });
    }

    // ------------------------------------------------------------------------------------------------- sonidos
    private static SoundEvent tf(String name) {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation("tfclient", "crates." + name));
    }

    private void ui(SoundEvent ev, float volume, float pitch) {
        if (minecraft == null) return;
        SoundInstance s = SimpleSoundInstance.forUI(ev, pitch, volume);
        minecraft.getSoundManager().play(s);
        playing.add(s);
    }

    private void track(String name, SoundSource src, float volume) {
        if (minecraft == null) return;
        Track s = new Track(tf(name), src, volume);
        minecraft.getSoundManager().play(s);
        tracks.add(s);
    }

    /** La música y el ambiente: suenan en todas partes y al final se apagan despacio en vez de cortarse. */
    private static final class Track extends AbstractTickableSoundInstance {
        private final float base;
        private float fade = 1.0F, step;

        Track(SoundEvent ev, SoundSource src, float volume) {
            super(ev, src, SoundInstance.createUnseededRandom());
            this.base = volume;
            this.volume = volume;
            this.looping = false;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = this.y = this.z = 0.0;
        }

        void fadeOut(int ticks) {
            float st = 1.0F / Math.max(1, ticks);
            if (step <= 0.0F || st > step) step = st;
        }

        @Override
        public void tick() {
            if (step <= 0.0F) return;
            fade -= step;
            if (fade <= 0.0F) {
                volume = 0.0F;
                stop();
                return;
            }
            volume = base * fade * fade;
        }
    }

    private int tier() {
        int o = winnerRarity.ordinal();
        return o <= 1 ? 1 : o == 2 ? 2 : 3;
    }

    private void sounds(int t) {
        switch (t) {
            case 0 -> {
                track("ambiente", SoundSource.AMBIENT, 0.8F);
                track("musica", SoundSource.MUSIC, 0.9F);
            }
            case KEY_APPEAR -> ui(tf("llave_aparece"), 0.8F, 1.0F);
            case KEY_IN -> {
                ui(tf("llave_entra"), 0.9F, 1.0F);
                ui(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.35F, 1.7F);
            }
            case KEY_TURN0 + 2 -> {
                ui(tf("llave_gira"), 0.9F, 1.0F);
                ui(SoundEvents.LEVER_CLICK, 0.45F, 0.75F);
            }
            case RUNES0 + 2 -> ui(tf("runas"), 0.8F, 1.0F);
            case TREMBLE0 -> ui(tf("tiembla"), 0.75F, 1.0F);
            case OPEN -> {
                ui(tf("apertura"), 1.0F, 1.0F);
                ui(SoundEvents.CHEST_OPEN, 0.55F, 0.75F);
            }
            case SPIN1 -> ui(tf("parada"), 0.85F, 1.0F);
            case REVEAL -> ui(tf("premio" + tier()), 1.0F, 1.0F);
            case TOTAL - 40 -> {
                for (Track tr : tracks) tr.fadeOut(56);
            }
            default -> {}
        }
    }

    private void reelClicks(float t) {
        if (t < SPIN0 || t >= SPIN1 || candidates == null || candidates.isEmpty()) return;
        int idx = (int)Math.floor(reelPos(t));
        if (lastReelIndex < 0) {
            lastReelIndex = idx;
        } else if (idx > lastReelIndex) {
            lastReelIndex = idx;
            float p = (t - SPIN0) / (float)(SPIN1 - SPIN0);
            ui(tf("tic"), 0.42F + 0.25F * p, 0.92F + p * 0.32F);
        }
    }

    // ------------------------------------------------------------------------------------------------ la ruleta
    private static float reelFrac(float p) {
        p = Math.max(0.0F, Math.min(1.0F, p));
        float fastTime = 0.42F, fastShare = 0.72F;
        if (p <= fastTime) return fastShare * (p / fastTime);
        float x = 1.0F - (p - fastTime) / (1.0F - fastTime);
        return fastShare + (1.0F - fastShare) * (1.0F - x * x * x);
    }

    private float reelPos(float t) {
        return reelFrac((t - SPIN0) / (float)(SPIN1 - SPIN0)) * LANDING;
    }

    private void ensureStrip() {
        if (strip != null) return;
        int n = candidates == null ? 0 : candidates.size();
        if (n == 0) {
            strip = new int[0];
            return;
        }
        int len = LANDING + SLOTS + 2;
        strip = new int[len];
        Random rnd = new Random(24301L + winnerIndex * 31L + n * 131L);
        int prev = -1;
        for (int i = 0; i < len; i++) {
            int v = rnd.nextInt(n);
            if (n > 1 && v == prev) v = (v + 1 + rnd.nextInt(n - 1)) % n;
            strip[i] = v;
            prev = v;
        }
        strip[LANDING] = winnerIndex;
        if (n > 1) {
            if (strip[LANDING - 1] == winnerIndex) strip[LANDING - 1] = (winnerIndex + 1) % n;
            if (strip[LANDING + 1] == winnerIndex) strip[LANDING + 1] = (winnerIndex + 1) % n;
        }
    }

    // ------------------------------------------------------------------------------------------------ la cámara
    private static float[] catmull(float[] k0, float[] k1, float[] k2, float[] k3, int off, float t) {
        float[] out = new float[3];
        float t2 = t * t, t3 = t2 * t;
        for (int i = 0; i < 3; i++) {
            float p0 = k0[off + i], p1 = k1[off + i], p2 = k2[off + i], p3 = k3[off + i];
            out[i] = 0.5F * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
        }
        return out;
    }

    /** Ojo, mira y campo de visión en t (la ruta de santuario.py, igual que su vista previa), con los temblores. */
    private float[] camera(SanctuaryScene sc, float t) {
        List<float[]> keys = sc.camera;
        int i = 0;
        while (i < keys.size() - 2 && keys.get(i + 1)[0] <= t) i++;
        float[] k1 = keys.get(i), k2 = keys.get(i + 1);
        float[] k0 = keys.get(Math.max(0, i - 1)), k3 = keys.get(Math.min(keys.size() - 1, i + 2));
        float u = Math.max(0.0F, Math.min(1.0F, (t - k1[0]) / Math.max(1e-6F, k2[0] - k1[0])));
        u = u * u * (3 - 2 * u) * 0.35F + u * 0.65F;
        float[] eye = catmull(k0, k1, k2, k3, 1, u), at = catmull(k0, k1, k2, k3, 4, u);
        float fov = k1[7] + (k2[7] - k1[7]) * u;
        // cámara en mano (muy poco) y los golpes de la apertura y del premio
        float hand = 0.018F;
        eye[0] += (float)Math.sin(t * 0.071F) * hand;
        eye[1] += (float)Math.sin(t * 0.053F + 1.3F) * hand;
        at[0] += (float)Math.sin(t * 0.047F + 0.4F) * hand * 0.6F;
        float shake = impulse(t, OPEN, 0.07F, 0.16F) + impulse(t, REVEAL, 0.05F, 0.14F) + impulse(t, KEY_IN, 0.012F, 0.4F)
                + (t >= TREMBLE0 && t < OPEN ? 0.008F * (t - TREMBLE0) / (OPEN - TREMBLE0) : 0.0F);
        if (shake > 0) {
            eye[0] += (float)Math.sin(t * 2.7F) * shake;
            eye[1] += (float)Math.cos(t * 3.3F) * shake * 0.7F;
            at[0] += (float)Math.sin(t * 2.1F + 1.0F) * shake * 0.5F;
        }
        if (t >= REVEAL && t < REVEAL + 8) fov -= 3.0F * (1.0F - (t - REVEAL) / 8.0F);
        return new float[] {eye[0], eye[1], eye[2], at[0], at[1], at[2], fov};
    }

    private static float impulse(float t, float at, float amp, float decay) {
        if (t < at) return 0.0F;
        return amp * (float)Math.exp(-(t - at) * decay);
    }

    // ------------------------------------------------------------------------------------------------- la crate
    private void ensureGeom() {
        if (geom) return;
        geom = true;
        CrateStyles.Style style = CrateStyles.get(cfg.styleId);
        cine = style != null && style.isCinematic();
        crateState = ((Block)ModRegistry.CRATE_BLOCK.get()).defaultBlockState();
        base = CrateBakedModels.baseModel(cfg);
        lid = CrateBakedModels.lidModel(cfg);
        hinge = CrateBakedModels.hingeFor(cfg);
        float s0 = CrateBakedModels.scaleFor(cfg) * Math.max(0.05F, cfg.sizeScale);
        float[] bb = bounds(base, lid, crateState);
        float[] foot = bounds(base, null, crateState);
        centerX = (foot[0] + foot[1]) * 0.5F;
        centerZ = (foot[4] + foot[5]) * 0.5F;
        minY = bb[2];
        float h = Math.max(0.05F, (bb[3] - bb[2]) * s0);
        float w = Math.max(foot[1] - foot[0], foot[5] - foot[4]) * s0;
        float fit = 1.0F;
        if (h > 1.5F) fit = 1.5F / h;
        if (w * fit > 1.7F) fit = 1.7F / w;
        if (h * fit < 0.8F) fit = 0.8F / h;
        scale = s0 * fit;
        crateH = (bb[3] - bb[2]) * scale;
        frontZ = (cine ? foot[5] - centerZ : centerZ - foot[4]) * scale;
        float hy = (hinge[1] - minY) * scale - 0.05F;
        keyY = Math.max(crateH * 0.28F, Math.min(crateH * 0.7F, hy));
        try {
            BakedModel km = Minecraft.getInstance().getItemRenderer().getModel(keyStack, null, null, 0);
            keyFlat = !km.isGui3d();
        } catch (Throwable ignored) {
            keyFlat = false;
        }
    }

    /** minX, maxX, minY, maxY, minZ, maxZ de los modelos (en unidades del modelo). */
    private static float[] bounds(BakedModel a, BakedModel b, BlockState state) {
        float[] r = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        for (BakedModel m : new BakedModel[] {a, b}) {
            if (m == null) continue;
            RandomSource rnd = RandomSource.create(42L);
            List<BakedQuad> quads = new ArrayList<>(m.getQuads(state, null, rnd));
            for (Direction d : Direction.values()) quads.addAll(m.getQuads(state, d, rnd));
            for (BakedQuad q : quads) {
                int[] v = q.getVertices();
                int stride = v.length / 4;
                for (int i = 0; i < 4; i++) {
                    float x = Float.intBitsToFloat(v[i * stride]), y = Float.intBitsToFloat(v[i * stride + 1]),
                            z = Float.intBitsToFloat(v[i * stride + 2]);
                    r[0] = Math.min(r[0], x);
                    r[1] = Math.max(r[1], x);
                    r[2] = Math.min(r[2], y);
                    r[3] = Math.max(r[3], y);
                    r[4] = Math.min(r[4], z);
                    r[5] = Math.max(r[5], z);
                }
            }
        }
        if (r[0] > r[1]) return new float[] {0, 1, 0, 1, 0, 1};
        return r;
    }

    /** Ángulo de la tapa (grados): cerrada, con golpecitos al temblar, salta al abrirse y se queda abierta. */
    private static float lidAngle(float t) {
        if (t < TREMBLE0) return 0.0F;
        if (t < OPEN) {
            float k = (t - TREMBLE0) / (OPEN - TREMBLE0);
            float pops = Math.max(0.0F, (float)Math.sin(t * 1.9F)) * Math.max(0.0F, (float)Math.sin(t * 0.7F));
            return pops * (2.0F + 7.0F * k);
        }
        float s = t - OPEN;
        if (s < 8.0F) {
            float p = s / 8.0F;
            float c1 = 1.70158F, c3 = c1 + 1.0F;
            return 106.0F * (1.0F + c3 * (float)Math.pow(p - 1, 3) + c1 * (float)Math.pow(p - 1, 2));
        }
        return 100.0F + 6.0F * (float)Math.exp(-(s - 8.0F) * 0.22F) * (float)Math.cos((s - 8.0F) * 0.8F);
    }

    private void drawCrate(PoseStack pose, MultiBufferSource buf, SanctuaryScene sc, float t) {
        float[] c = sc.anchor("crate");
        float hop = 0.0F, yawJ = 0.0F, roll = 0.0F, sy = 1.0F;
        if (t >= TREMBLE0 && t < OPEN) {
            float k = (t - TREMBLE0) / (OPEN - TREMBLE0);
            yawJ = (float)Math.sin(t * 2.3F) * 2.2F * k;
            roll = (float)Math.sin(t * 3.1F + 1.0F) * 1.6F * k;
            hop = Math.max(0.0F, (float)Math.sin(t * 1.9F)) * 0.035F * k;
            sy = 1.0F - 0.07F * smooth(OPEN - 6, OPEN, t);
        } else if (t >= OPEN) {
            float s = t - OPEN;
            sy = 1.0F + 0.10F * (float)Math.exp(-s * 0.3F) * (float)Math.cos(s * 0.9F);
            hop = 0.12F * (float)Math.exp(-s * 0.35F) * Math.abs((float)Math.sin(s * 0.55F));
        }
        float sxz = 1.0F / (float)Math.sqrt(sy);
        pose.pushPose();
        pose.translate(c[0], c[1] + hop, c[2]);
        pose.mulPose(Axis.YP.rotationDegrees((cine ? 0.0F : 180.0F) + yawJ));
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.scale(scale * sxz, scale * sy, scale * sxz);
        pose.translate(-centerX, -minY, -centerZ);
        ModelBlockRenderer mr = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        // (el dueño: «no abuses del bloom a que se vea borrosa la crate»: un poco por debajo de la luz entera, no florece)
        VertexConsumer vc = sc.tinted(buf.getBuffer(RenderType.cutout()), 0.86F, 0.84F, 0.80F);
        mr.renderModel(pose.last(), vc, crateState, base, 1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, RenderType.cutout());
        if (lid != null) {
            float deg = lidAngle(t);
            float pivotZ = cine ? 1.0F - hinge[2] : hinge[2];
            pose.pushPose();
            pose.translate(hinge[0], hinge[1], pivotZ);
            pose.mulPose(Axis.XP.rotationDegrees(cine ? -deg : deg));
            pose.translate(-hinge[0], -hinge[1], -pivotZ);
            mr.renderModel(pose.last(), vc, crateState, lid, 1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, RenderType.cutout());
            pose.popPose();
        }
        pose.popPose();
    }

    // -------------------------------------------------------------------------------------------------- la llave
    /** Dónde está la cerradura (en el mundo de la escena). */
    private Vector3f keyhole(SanctuaryScene sc) {
        float[] c = sc.anchor("crate");
        return new Vector3f(c[0], c[1] + keyY, c[2] + frontZ + 0.01F);
    }

    private static final float KEY_SCALE = 0.95F;
    /** La punta de la llave, delante del centro del modelo (las llaves 3D de Fantastic Crates van a lo largo de z). */
    private static final float KEY_TIP = 0.44F * KEY_SCALE;

    private void drawKey(PoseStack pose, MultiBufferSource buf, SanctuaryScene sc, float t) {
        if (keyStack.isEmpty() || t < KEY_APPEAR + 4 || t > OPEN + 6) return;
        Vector3f k = keyhole(sc);
        Vector3f align = new Vector3f(k.x, k.y, k.z + KEY_TIP + 0.55F);
        Vector3f start = new Vector3f(k.x + 1.3F, k.y + 0.9F, k.z + 1.7F);
        Vector3f pos;
        float yaw, turn = 0.0F, size = 1.0F;
        if (t < KEY_ALIGN) {
            float p = smooth(KEY_APPEAR + 4, KEY_ALIGN, t);
            Vector3f ctrl = new Vector3f(k.x + 0.7F, k.y + 1.2F, k.z + 1.4F);
            pos = bezier(start, ctrl, align, p);
            pos.y += (float)Math.sin(t * 0.22F) * 0.06F * (1.0F - p);
            yaw = (1.0F - smooth(0.55F, 1.0F, p)) * (float)Math.sin(t * 0.09F) * 120.0F;
            size = smooth(KEY_APPEAR + 4, KEY_APPEAR + 14, t);
        } else if (t < KEY_IN) {
            float p = (t - KEY_ALIGN) / (KEY_IN - KEY_ALIGN);
            p = p * p;
            pos = new Vector3f(align).lerp(new Vector3f(k.x, k.y, k.z + KEY_TIP - 0.22F), p);
            yaw = 0.0F;
        } else {
            pos = new Vector3f(k.x, k.y, k.z + KEY_TIP - 0.22F);
            yaw = 0.0F;
            turn = 90.0F * smooth(KEY_TURN0, KEY_TURN1, t);
            if (t > OPEN) size = Math.max(0.0F, 1.0F - (t - OPEN) / 6.0F);
        }
        pose.pushPose();
        pose.translate(pos.x, pos.y, pos.z);
        pose.mulPose(Axis.ZP.rotationDegrees(-turn));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        if (keyFlat) {
            pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(-45.0F));
        }
        pose.scale(KEY_SCALE * size, KEY_SCALE * size, KEY_SCALE * size);
        Minecraft.getInstance().getItemRenderer().renderStatic(keyStack, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, pose, buf, Minecraft.getInstance().level, 0);
        pose.popPose();
    }

    private static Vector3f bezier(Vector3f a, Vector3f b, Vector3f c, float t) {
        float u = 1 - t;
        return new Vector3f(a).mul(u * u).add(new Vector3f(b).mul(2 * u * t)).add(new Vector3f(c).mul(t * t));
    }

    /** La luz de la llave: el remolino en que aparece, su halo, la estela, el destello al girar y las chispas al deshacerse. */
    private void keyLights(VertexConsumer vc, CinematicFx.View v, SanctuaryScene sc, float t) {
        Vector3f k = keyhole(sc);
        Vector3f start = new Vector3f(k.x + 1.3F, k.y + 0.9F, k.z + 1.7F);
        int col = rarityColor;
        float r = ((col >> 16) & 255) / 255.0F, g = ((col >> 8) & 255) / 255.0F, b = (col & 255) / 255.0F;
        // el remolino: chispas que se juntan donde aparece la llave
        if (t >= KEY_APPEAR - 6 && t < KEY_APPEAR + 16) {
            float p = (t - (KEY_APPEAR - 6)) / 22.0F;
            for (int i = 0; i < 36; i++) {
                float ang = i * 0.7F + p * 9.0F, rad = (1.0F - p) * (0.6F + (i % 5) * 0.12F);
                float y = (float)Math.sin(i * 1.3F) * (1.0F - p) * 0.5F;
                CinematicFx.glow(vc, v, start.x + (float)Math.cos(ang) * rad, start.y + y, start.z + (float)Math.sin(ang) * rad, 0.05F,
                        mix(r, 1, 0.4F), mix(g, 1, 0.4F), mix(b, 1, 0.4F), (float)Math.sin(p * Math.PI) * 0.9F);
            }
            CinematicFx.glow(vc, v, start.x, start.y, start.z, 0.6F * (float)Math.sin(p * Math.PI), r, g, b, 0.6F);
        }
        if (t >= KEY_APPEAR + 4 && t < OPEN + 2) {
            // halo que acompaña a la llave
            Vector3f kp = keyPos(sc, t);
            float pulse = 0.75F + 0.25F * (float)Math.sin(t * 0.35F);
            CinematicFx.glow(vc, v, kp.x, kp.y, kp.z, 0.75F * pulse, r, g, b, 0.55F);
            CinematicFx.glow(vc, v, kp.x, kp.y, kp.z, 0.3F, 1, 1, 1, 0.45F);
            if (t < KEY_ALIGN) {
                for (int i = 1; i <= 8; i++) {
                    Vector3f q = keyPos(sc, t - i * 1.6F);
                    CinematicFx.glow(vc, v, q.x, q.y, q.z, 0.1F * (1.0F - i / 9.0F), mix(r, 1, 0.5F), mix(g, 1, 0.5F), mix(b, 1, 0.5F),
                            0.5F * (1.0F - i / 9.0F));
                }
            }
        }
        // el clic: destello en la cerradura al entrar y al girar, y la luz que se escapa por la tapa al temblar
        float flash = Math.max(pulseAt(t, KEY_IN, 7.0F), pulseAt(t, KEY_TURN1, 10.0F));
        if (flash > 0) {
            CinematicFx.glow(vc, v, k.x, k.y, k.z + 0.05F, 0.9F * flash + 0.2F, mix(r, 1, 0.3F), mix(g, 1, 0.3F), mix(b, 1, 0.3F),
                    flash * 0.8F);
        }
        if (t >= TREMBLE0 && t < OPEN + 4) {
            float[] c = sc.anchor("crate");
            float k2 = smooth(TREMBLE0, OPEN, t) * (t < OPEN ? 1.0F : 1.0F - (t - OPEN) / 4.0F);
            float top = c[1] + crateH * 0.9F;
            for (int i = 0; i < 12; i++) {
                float a = i / 12.0F * 6.2832F;
                float leak = 0.5F + 0.5F * (float)Math.sin(t * 1.7F + i * 2.1F);
                CinematicFx.glow(vc, v, c[0] + (float)Math.cos(a) * 0.45F * scale * 1.6F, top, c[2] + (float)Math.sin(a) * 0.4F * scale * 1.6F,
                        0.22F, r, g, b, k2 * leak * 0.6F);
            }
            CinematicFx.glow(vc, v, c[0], top, c[2], 0.9F * k2, r, g, b, k2 * 0.5F);
        }
        if (t >= OPEN && t < OPEN + 20) {
            Vector3f kp = new Vector3f(k.x, k.y, k.z + 0.2F);
            CinematicFx.sparkBurst(vc, v, kp.x, kp.y, kp.z, t - OPEN, 20.0F, 40, 0.5F, col, 200);
        }
    }

    private Vector3f keyPos(SanctuaryScene sc, float t) {
        Vector3f k = keyhole(sc);
        Vector3f align = new Vector3f(k.x, k.y, k.z + KEY_TIP + 0.55F);
        Vector3f start = new Vector3f(k.x + 1.3F, k.y + 0.9F, k.z + 1.7F);
        if (t < KEY_ALIGN) {
            float p = smooth(KEY_APPEAR + 4, KEY_ALIGN, t);
            Vector3f pos = bezier(start, new Vector3f(k.x + 0.7F, k.y + 1.2F, k.z + 1.4F), align, p);
            pos.y += (float)Math.sin(t * 0.22F) * 0.06F * (1.0F - p);
            return pos;
        }
        if (t < KEY_IN) {
            float p = (t - KEY_ALIGN) / (KEY_IN - KEY_ALIGN);
            return new Vector3f(align).lerp(new Vector3f(k.x, k.y, k.z + KEY_TIP - 0.22F), p * p);
        }
        return new Vector3f(k.x, k.y, k.z + KEY_TIP - 0.22F);
    }

    private static float pulseAt(float t, float at, float len) {
        if (t < at || t > at + len) return 0.0F;
        float p = (t - at) / len;
        return (1.0F - p) * (1.0F - p);
    }

    // -------------------------------------------------------------------------------------------- los premios
    /** El centro del anillo de la ruleta. */
    private float ringY(SanctuaryScene sc) {
        return sc.anchor("crate")[1] + crateH + 0.85F;
    }

    private void drawItems(PoseStack pose, MultiBufferSource buf, SanctuaryScene sc, float t, Vector3f eye) {
        if (candidates == null || candidates.isEmpty() || t < RING0) return;
        ensureStrip();
        float[] c = sc.anchor("crate");
        float cy = ringY(sc);
        float form = smooth(RING0, RING1, t);
        float pos = t < SPIN0 ? 0.0F : reelPos(Math.min(t, SPIN1));
        int baseIdx = (int)Math.floor(pos);
        float frac = pos - baseIdx;
        float reveal = t >= REVEAL ? smooth(REVEAL, REVEAL + 10, t) : 0.0F;
        // el frente del anillo mira a la cámara
        float front = (float)Math.atan2(eye.x - c[0], eye.z - c[2]);
        for (int k = -SLOTS / 2; k < SLOTS / 2; k++) {
            int si = baseIdx + k;
            if (si < 0 || si >= strip.length) continue;
            ItemStack st = candidates.get(strip[si]);
            if (st == null || st.isEmpty()) continue;
            float rel = k - frac;
            float ang = front + rel * (float)(Math.PI * 2 / SLOTS);
            float facing = (float)Math.cos(rel * Math.PI * 2 / SLOTS);          // 1 delante, -1 detrás
            float vis = smooth(-0.55F, 0.35F, facing);
            boolean winnerSlot = si == LANDING && t >= SPIN1 - 1;
            if (reveal > 0 && !winnerSlot) vis *= 1.0F - reveal;
            // al formarse, cada premio sube de la crate a su sitio (escalonado)
            float stagger = Math.max(0.0F, Math.min(1.0F, form * 1.6F - (k + SLOTS / 2) / (float)SLOTS * 0.6F));
            float rad = RING_R * stagger;
            float x = c[0] + (float)Math.sin(ang) * rad, z = c[2] + (float)Math.cos(ang) * rad;
            float y = mix(c[1] + crateH * 0.6F, cy, stagger) + (float)Math.sin(t * 0.12F + k) * 0.04F;
            float s = 0.62F * vis * (0.7F + 0.5F * Math.max(0.0F, facing) * Math.max(0.0F, facing));
            if (winnerSlot) {
                // el premio: al frente, luego al centro, delante del pilar, grande, girando despacio
                float rp = smooth(REVEAL, REVEAL + 14, t);
                x = mix(x, c[0] + (float)Math.sin(front) * 1.1F, rp);
                z = mix(z, c[2] + (float)Math.cos(front) * 1.1F, rp);
                y = mix(y, cy + 0.15F, rp) + (float)Math.sin(t * 0.1F) * 0.06F * rp;
                float pulse = t < REVEAL ? 1.0F + 0.12F * (float)Math.sin((t - SPIN1) * 0.8F) : 1.0F;
                s = mix(0.74F, 1.15F, rp) * pulse;
            }
            if (s <= 0.01F) continue;
            pose.pushPose();
            pose.translate(x, y, z);
            float yaw = (float)Math.atan2(eye.x - x, eye.z - z);
            pose.mulPose(Axis.YP.rotation(yaw));
            if (winnerSlot && t >= REVEAL) pose.mulPose(Axis.YP.rotationDegrees((float)Math.sin((t - REVEAL) * 0.035F) * 25.0F));
            pose.scale(s, s, s);
            Minecraft.getInstance().getItemRenderer().renderStatic(st, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose, buf, Minecraft.getInstance().level, si);
            pose.popPose();
        }
    }

    /** La luz de la ruleta: el halo del que está al frente, el aro, y la explosión del premio. */
    private void itemLights(VertexConsumer vc, CinematicFx.View v, SanctuaryScene sc, float t, Vector3f eye) {
        if (candidates == null || candidates.isEmpty() || t < RING0) return;
        float[] c = sc.anchor("crate");
        float cy = ringY(sc);
        float front = (float)Math.atan2(eye.x - c[0], eye.z - c[2]);
        float form = smooth(RING0, RING1, t);
        int col = winnerRarity.rgb() & 0xFFFFFF;
        int ringCol = rarityColor;
        float r = ((ringCol >> 16) & 255) / 255.0F, g = ((ringCol >> 8) & 255) / 255.0F, b = (ringCol & 255) / 255.0F;
        float fade = t >= REVEAL ? 1.0F - smooth(REVEAL, REVEAL + 12, t) : 1.0F;
        // el aro por donde pasan y un halo detrás de cada premio (los oscuros, como un lingote de netherita, se leen)
        CinematicFx.ring(vc, v, c[0], cy - 0.35F, c[2], RING_R * form, 0.05F, 72, r, g, b, 0.35F * form * fade);
        if (t < REVEAL + 10) {
            float posR = t < SPIN0 ? 0.0F : reelPos(Math.min(t, SPIN1));
            float fr = posR - (float)Math.floor(posR);
            for (int k = -SLOTS / 2; k < SLOTS / 2; k++) {
                float rel = k - fr;
                float facing = (float)Math.cos(rel * Math.PI * 2 / SLOTS);
                float vis = smooth(-0.2F, 0.6F, facing) * form * fade;
                if (vis <= 0.02F) continue;
                float ang = front + rel * (float)(Math.PI * 2 / SLOTS);
                float stagger = Math.max(0.0F, Math.min(1.0F, form * 1.6F - (k + SLOTS / 2) / (float)SLOTS * 0.6F));
                float hx = c[0] + (float)Math.sin(ang) * RING_R * stagger, hz = c[2] + (float)Math.cos(ang) * RING_R * stagger;
                Vector3f back = new Vector3f(hx - eye.x, 0, hz - eye.z).normalize().mul(0.25F);
                CinematicFx.glow(vc, v, hx + back.x, cy, hz + back.z, 0.42F + 0.15F * facing, mix(r, 1, 0.55F), mix(g, 1, 0.55F),
                        mix(b, 1, 0.55F), 0.32F * vis);
            }
        }
        // el halo del frente
        float fx = c[0] + (float)Math.sin(front) * RING_R, fz = c[2] + (float)Math.cos(front) * RING_R;
        float spin = t < SPIN1 && t >= SPIN0 ? 1.0F - reelFrac((t - SPIN0) / (float)(SPIN1 - SPIN0)) : 0.0F;
        float settle = t >= SPIN1 && t < REVEAL ? 0.5F + 0.5F * (float)Math.sin((t - SPIN1) * 0.9F) : 0.0F;
        CinematicFx.glow(vc, v, fx, cy, fz, (0.55F + settle * 0.25F) * form, r, g, b, (0.35F + 0.25F * settle) * form * fade);
        CinematicFx.glow(vc, v, fx, cy - 0.55F, fz, 0.18F * form, 1, 1, 1, 0.5F * form * fade * (0.6F + 0.4F * spin));
        // el premio
        if (t >= REVEAL) {
            float s = t - REVEAL;
            float px = c[0] + (float)Math.sin(front) * 1.1F, pz = c[2] + (float)Math.cos(front) * 1.1F, py = cy + 0.15F;
            float wr = ((col >> 16) & 255) / 255.0F, wg = ((col >> 8) & 255) / 255.0F, wb = (col & 255) / 255.0F;
            float in = smooth(0, 10, s);
            float out = t > TOTAL - 20 ? 1.0F - (t - (TOTAL - 20)) / 20.0F : 1.0F;
            float pow = in * out * (0.7F + 0.3F * (float)Math.sin(s * 0.15F));
            CinematicFx.glow(vc, v, px, py, pz, 1.1F + 0.6F * pulseAt(t, REVEAL, 12), wr, wg, wb, 0.55F * pow + pulseAt(t, REVEAL, 10));
            CinematicFx.glow(vc, v, px, py, pz, 0.45F, 1, 1, 1, 0.35F * pow + pulseAt(t, REVEAL, 8));
            CinematicFx.sparkBurst(vc, v, px, py, pz, s, 34.0F, 70 + winnerRarity.ordinal() * 20, 0.8F, col, 300);
            CinematicFx.shockwave(vc, v, c[0], sc.anchor("pool")[1] + 0.03F, c[2], s, 26.0F, 9.0F, col);
            // destellos que siguen brillando alrededor
            for (int i = 0; i < 14 + winnerRarity.ordinal() * 4; i++) {
                float a = i * 2.39996F + s * 0.02F;
                float rr = 0.7F + (i % 4) * 0.22F;
                float tw = 0.5F + 0.5F * (float)Math.sin(s * 0.3F + i * 1.7F);
                CinematicFx.glow(vc, v, px + (float)Math.cos(a) * rr, py + (float)Math.sin(a * 1.3F) * 0.6F, pz + (float)Math.sin(a) * rr * 0.5F,
                        0.05F, mix(wr, 1, 0.5F), mix(wg, 1, 0.5F), mix(wb, 1, 0.5F), tw * pow * 0.9F);
            }
        }
    }

    private void itemRays(VertexConsumer vc, CinematicFx.View v, SanctuaryScene sc, float t, Vector3f eye) {
        if (t < REVEAL || candidates == null || candidates.isEmpty()) return;
        float[] c = sc.anchor("crate");
        float front = (float)Math.atan2(eye.x - c[0], eye.z - c[2]);
        float px = c[0] + (float)Math.sin(front) * 1.1F, pz = c[2] + (float)Math.cos(front) * 1.1F, py = ringY(sc) + 0.15F;
        // detrás del premio (un poco más lejos de la cámara)
        Vector3f back = new Vector3f(px - eye.x, py - eye.y, pz - eye.z).normalize().mul(0.3F);
        float s = t - REVEAL;
        float out = t > TOTAL - 20 ? 1.0F - (t - (TOTAL - 20)) / 20.0F : 1.0F;
        float pow = smooth(0, 8, s) * out * (0.55F + 0.15F * winnerRarity.ordinal());
        CinematicFx.rays(vc, v, px + back.x, py + back.y, pz + back.z, t, pow, 2.6F + 0.4F * winnerRarity.ordinal(),
                winnerRarity.rgb() & 0xFFFFFF);
    }

    // ------------------------------------------------------------------------------------------------ el render
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        if (openNanos == 0L) openNanos = now;
        if (lastFrame != 0L) {
            double ms = (now - lastFrame) / 1.0E6;
            frames++;
            frameSum += ms;
            if (frames > 3) frameMax = Math.max(frameMax, ms);
        }
        lastFrame = now;
        float t = (float)((now - openNanos) / 5.0E7);
        int soundTick = (int)Math.floor(t);
        while (lastSoundTick < soundTick) {
            lastSoundTick++;
            sounds(lastSoundTick);
        }
        reelClicks(t);
        SanctuaryScene sc = SanctuaryScene.get();
        if (sc == null || faulted) {
            g.fill(0, 0, width, height, 0xFF000000);
            return;
        }
        try {
            ensureGeom();
            sc.ensureBuilt();
            renderScene(g, sc, t);
        } catch (Throwable e) {
            fault(e);
            g.fill(0, 0, width, height, 0xFF000000);
            return;
        }
        overlay(g, sc, t);
    }

    /** El cielo de respaldo (si el shader no carga) y la bruma del santuario: verde azulada oscura (lo del fondo se pierde
     *  un poco en ella). */
    private static final float[] SKY_TOP = {0.55F, 0.80F, 0.85F}, SKY_HORIZON = {0.90F, 0.92F, 0.80F}, FOG = {0.10F, 0.19F, 0.20F};
    static final float FOG_START = 18.0F, FOG_END = 80.0F;

    private void renderScene(GuiGraphics g, SanctuaryScene sc, float t) {
        Minecraft mc = Minecraft.getInstance();
        float[] cam = camera(sc, t);
        Vector3f eye = new Vector3f(cam[0], cam[1], cam[2]);
        Matrix4f view = new Matrix4f().lookAt(eye, new Vector3f(cam[3], cam[4], cam[5]), new Vector3f(0, 1, 0));
        float aspect = (float)mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        Matrix4f proj = new Matrix4f().setPerspective((float)Math.toRadians(cam[6]), aspect, 0.05F, 220.0F);
        Vector3f right = new Vector3f(view.m00(), view.m10(), view.m20());
        Vector3f up = new Vector3f(view.m01(), view.m11(), view.m21());

        // 1) el cielo: con el shader, el atardecer en la textura de la escena; si no, el de siempre en 2D
        boolean pp = ScenePost.ready() && !postFailed;
        if (pp) {
            try {
                if (post == null) post = new ScenePost();
                post.ensure(mc.getWindow().getWidth(), mc.getWindow().getHeight());
                post.begin();
                Vector3f fwd = new Vector3f(-view.m02(), -view.m12(), -view.m22());
                float tanY = (float)Math.tan(Math.toRadians(cam[6]) * 0.5);
                post.sky(right, up, fwd, tanY * aspect, tanY, sc.sunDir(), t);
            } catch (Throwable e) {
                postFail(e);
                pp = false;
                mc.getMainRenderTarget().bindWrite(true);
            }
        }
        if (!pp) {
            sky(g, sc, view, proj, eye, t);
            g.flush();
        }

        // 2) la escena en 3D
        Matrix4f oldProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting oldSort = RenderSystem.getVertexSorting();
        Matrix3f oldInv = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        float[] fogColor = RenderSystem.getShaderFogColor().clone();
        FogShape fogShape = RenderSystem.getShaderFogShape();
        // los objetos (la llave, los premios) a la imagen principal también con gráficos «Fabulosos» (si no, irían a la
        // capa de objetos del mundo, que en una pantalla nadie compone)
        boolean panoramic = mc.gameRenderer.isPanoramicMode();
        mc.gameRenderer.setPanoramicMode(true);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(proj, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.setInverseViewRotationMatrix(new Matrix3f());
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.setShaderFogStart(FOG_START);
        RenderSystem.setShaderFogEnd(FOG_END);
        RenderSystem.setShaderFogColor(FOG[0], FOG[1], FOG[2]);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
        for (ShaderInstance sh : new ShaderInstance[] {GameRenderer.getRendertypeSolidShader(), GameRenderer.getRendertypeCutoutShader(),
                GameRenderer.getRendertypeCutoutMippedShader(), GameRenderer.getRendertypeTranslucentShader()}) {
            if (sh != null && sh.CHUNK_OFFSET != null) sh.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
        }
        Lighting.setupLevel(view);
        try {
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            PoseStack pose = new PoseStack();
            pose.mulPose(view.getNormalizedRotation(new Quaternionf()));
            pose.translate(-eye.x, -eye.y, -eye.z);
            CinematicFx.View fv = new CinematicFx.View(pose.last().pose(), eye, right, up);

            sc.drawOpaque(view, proj);
            float gust = pulseAt(t, OPEN, 40.0F) + pulseAt(t, REVEAL, 30.0F) * 0.6F;
            sc.drawPlants(pose, buf, t / 20.0F, gust);
            buf.endBatch(RenderType.cutout());
            buf.endBatch(RenderType.cutoutMipped());
            buf.endBatch(RenderType.solid());
            drawCrate(pose, buf, sc, t);
            buf.endBatch(RenderType.cutout());
            drawKey(pose, buf, sc, t);
            drawItems(pose, buf, sc, t, eye);
            buf.endBatch();
            if (pp) post.rebind();

            // el agua (el estanque, las charcas, la cascada y las fuentes: la dibuja el juego), translúcida
            if (!(pp && sc.drawWaterFx(view, proj, eye, t))) sc.drawWater(view, proj);
            float surge = pulseAt(t, OPEN, 50.0F) + pulseAt(t, REVEAL, 40.0F);

            // los pétalos de la apertura y del premio, y las alas de las hadas
            VertexConsumer soft = buf.getBuffer(CinematicFx.softType());
            CinematicFx.petals(soft, fv.m, sc, t, t - OPEN, t >= OPEN ? 1.0F : 0.0F);
            if (t >= REVEAL) CinematicFx.petals(soft, fv.m, sc, t, t - REVEAL, 0.8F);
            float excite = pulseAt(t, OPEN, 60.0F) + pulseAt(t, REVEAL, 60.0F);
            CinematicFx.fairies(soft, fv, sc, t, excite, true);
            buf.endBatch(CinematicFx.softType());

            // la luz que suma: las luces mágicas, las hadas, las luciérnagas, la niebla, la cascada, el sol, el polvo, el
            // agua que brilla, las runas, el pilar, la llave, los premios
            RenderSystem.depthMask(false);
            float open = smooth(OPEN - 2, OPEN + 12, t);
            float tintAmt = 0.35F * open * (0.6F + 0.4F * winnerRarity.ordinal() / 4.0F);
            VertexConsumer glow = buf.getBuffer(CinematicFx.glowType());
            CinematicFx.mist(glow, fv, sc, t);
            CinematicFx.lightHalos(glow, fv, sc, t);
            CinematicFx.waterfallLights(glow, fv, sc, t);
            CinematicFx.fireflies(glow, fv, t);
            CinematicFx.fairies(glow, fv, sc, t, excite, false);
            // el haz baja mientras gira la ruleta y en el premio (que no tape los premios)
            float ring = smooth(RING0, RING1, t) * (t >= REVEAL + 40 ? 1.0F - 0.5F * smooth(REVEAL + 40, REVEAL + 70, t) : 1.0F);
            float shaft = (1.0F + open * 0.5F) * (1.0F - 0.45F * ring);
            CinematicFx.sunShafts(glow, fv, sc, t, shaft, rarityColor, tintAmt);
            CinematicFx.motes(glow, fv, sc, t, shaft);
            float runeLit = smooth(RUNES0, RUNES1, t) * (t > REVEAL + 50 ? Math.max(0.3F, 1.0F - (t - REVEAL - 50) / 40.0F) : 1.0F);
            float glowWater = smooth(KEY_TURN0, KEY_TURN1 + 6, t) * (0.6F + 0.4F * pulseAt(t, OPEN, 40.0F) + 0.4F);
            CinematicFx.streamLights(glow, fv, sc, t, Math.min(1.0F, glowWater), rarityColor, surge);
            CinematicFx.runes(glow, fv, sc, t, runeLit, pulseAt(t, OPEN, 24.0F), rarityColor);
            float[] c = sc.anchor("crate");
            float pillar = smooth(OPEN, OPEN + 8, t) * (t >= REVEAL ? 0.25F + 0.4F * pulseAt(t, REVEAL, 24.0F)
                    : t >= RING0 ? 1.0F - 0.7F * smooth(RING0, RING1, t) : 1.0F);
            if (t > TOTAL - 20) pillar *= Math.max(0.0F, (TOTAL - t) / 20.0F) * 0.5F + 0.5F;
            CinematicFx.pillar(glow, fv, c[0], c[1] + crateH * 0.75F, c[2], 9.0F, pillar, t, rarityColor);
            CinematicFx.shockwave(glow, fv, c[0], sc.anchor("pool")[1] + 0.03F, c[2], t - OPEN, 30.0F, 8.0F, rarityColor);
            keyLights(glow, fv, sc, t);
            itemLights(glow, fv, sc, t, eye);
            buf.endBatch(CinematicFx.glowType());
            VertexConsumer rays = buf.getBuffer(CinematicFx.raysType());
            itemRays(rays, fv, sc, t, eye);
            buf.endBatch(CinematicFx.raysType());
            RenderSystem.depthMask(true);
        } finally {
            mc.gameRenderer.setPanoramicMode(panoramic);
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(oldProj, oldSort);
            RenderSystem.setInverseViewRotationMatrix(oldInv);
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
            RenderSystem.setShaderFogShape(fogShape);
            Lighting.setupFor3DItems();
            RenderSystem.depthMask(true);
            // la profundidad de la escena se queda como está (el shader la lee para la profundidad de campo; antes se
            // borraba aquí, todo parecía lejos y la imagen entera salía desenfocada): se borra la de la pantalla
            if (pp) mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }

        // 3) el resplandor, los rayos de sol y los colores, a la pantalla
        if (pp) {
            try {
                Vector3f sun = sc.sunDir();
                Vector4f sp = new Vector4f(eye.x + sun.x * 1000.0F, eye.y + sun.y * 1000.0F, eye.z + sun.z * 1000.0F, 1.0F);
                view.transform(sp);
                proj.transform(sp);
                float su = 0.5F, sv = 0.5F, vis = 0.0F;
                if (sp.w > 1e-4F) {
                    su = sp.x / sp.w * 0.5F + 0.5F;
                    sv = sp.y / sp.w * 0.5F + 0.5F;
                    float d = (float)Math.sqrt((su - 0.5F) * (su - 0.5F) + (sv - 0.5F) * (sv - 0.5F));
                    vis = Math.max(0.0F, Math.min(1.0F, 1.3F - 0.42F * d));
                }
                // (el dueño: «que el resplandor también salga del agua… no abuses tanto del bloom a que se vea borrosa la
                // crate»: florece lo que pasa del umbral, el agua y las luces; la crate, no)
                float burst = pulseAt(t, OPEN, 36.0F) + pulseAt(t, REVEAL, 40.0F);
                float bloomK = 0.5F + 0.1F * smooth(OPEN, OPEN + 20, t) + 0.25F * burst;
                float rayK = 0.7F + 0.1F * smooth(OPEN, OPEN + 20, t) + 0.25F * burst;
                float[] cc = sc.anchor("crate");
                float focus = new Vector3f(cc[0], cc[1] + 0.6F, cc[2]).distance(eye);
                post.finish(su, sv, vis, bloomK, rayK, 1.55F, focus, 0.05F, 220.0F);
            } catch (Throwable e) {
                postFail(e);
                mc.getMainRenderTarget().bindWrite(true);
            }
        }
    }

    /** Si el shader falla, la cinemática sigue sin él (directa a la pantalla, con el cielo en 2D). */
    private void postFail(Throwable e) {
        if (postFailed) return;
        postFailed = true;
        LogUtils.getLogger().error("[TF Crates] El shader de la cinemática falló: sigo sin resplandor", e);
        if (post != null) {
            post.close();
            post = null;
        }
    }

    /** Si la imagen pasa por el shader (que pone los rayos del sol y la viñeta él mismo). */
    private boolean postActive() {
        return post != null && !postFailed && ScenePost.ready();
    }

    /** El cielo de respaldo (sin el shader): un degradado de la noche. */
    private void sky(GuiGraphics g, SanctuaryScene sc, Matrix4f view, Matrix4f proj, Vector3f eye, float t) {
        int w = width, h = height;
        // la altura del horizonte en pantalla
        Vector3f fwd = new Vector3f(-view.m02(), 0, -view.m22());
        if (fwd.lengthSquared() < 1e-6F) fwd.set(0, 0, -1);
        fwd.normalize();
        float[] hz = project(view, proj, new Vector3f(eye).add(new Vector3f(fwd).mul(1000.0F)), w, h);
        int hy = hz == null ? h / 2 : (int)Math.max(-h, Math.min(2 * h, hz[1]));
        int top = argb(SKY_TOP, 1.0F), hor = argb(SKY_HORIZON, 1.0F);
        if (hy > 0) g.fillGradient(0, 0, w, Math.min(h, hy), top, hor);
        if (hy < h) g.fillGradient(0, Math.max(0, hy), w, h, hor, argb(FOG, 1.0F));
    }

    private static float[] project(Matrix4f view, Matrix4f proj, Vector3f p, int w, int h) {
        Vector4f v = new Vector4f(p, 1.0F);
        view.transform(v);
        proj.transform(v);
        if (v.w <= 1e-4F) return null;
        float nx = v.x / v.w, ny = v.y / v.w;
        return new float[] {(nx * 0.5F + 0.5F) * w, (1.0F - (ny * 0.5F + 0.5F)) * h};
    }

    private static int argb(float[] c, float a) {
        return ((int)(a * 255) << 24) | ((int)(c[0] * 255) << 16) | ((int)(c[1] * 255) << 8) | (int)(c[2] * 255);
    }

    // ---------------------------------------------------------------------------------------------- lo de encima
    private void overlay(GuiGraphics g, SanctuaryScene sc, float t) {
        int w = width, h = height;
        boolean pp = postActive();
        if (!pp) {
            // viñeta suave (con el shader la pone él)
            g.fillGradient(0, 0, w, h / 5, 0x55000000, 0x00000000);
            g.fillGradient(0, h - h / 5, w, h, 0x00000000, 0x55000000);
        }
        // destellos a pantalla completa: al abrirse y en el premio
        // (con el shader, el resplandor ya hace de destello: el blanco a pantalla completa es más suave y más corto)
        float flash = pp ? pulseAt(t, OPEN, 8.0F) * 0.28F + pulseAt(t, REVEAL, 9.0F) * 0.38F
                : pulseAt(t, OPEN, 10.0F) * 0.55F + pulseAt(t, REVEAL, 14.0F) * 0.85F;
        if (flash > 0.01F) {
            int a = (int)(Math.min(1.0F, flash) * 255);
            g.fill(0, 0, w, h, (a << 24) | 0xFFF8E8);
        }
        // fundido de entrada y de salida
        float fade = t < 10 ? 1.0F - t / 10.0F : t > TOTAL - 12 ? (t - (TOTAL - 12)) / 12.0F : 0.0F;
        if (fade > 0.01F) g.fill(0, 0, w, h, ((int)(Math.min(1.0F, fade) * 255) << 24));
        // franjas de cine
        // (finas: la imagen del dueño va a pantalla entera)
        float bars = Math.min(1.0F, 0.6F + t / 20.0F);
        int barH = (int)(h * 0.035F * bars);
        g.fill(0, 0, w, barH, 0xFF000000);
        g.fill(0, h - barH, w, h, 0xFF000000);
        // el título de la crate, al llegar
        if (t >= 6 && t < 58) {
            float a = smooth(6, 16, t) * (1.0F - smooth(44, 58, t));
            String name = cfg.displayName == null || cfg.displayName.isEmpty() ? "Crate " + cfg.rarity.displayName()
                    : cfg.displayName.replace('&', '§');
            drawScaled(g, "§l" + stripColor(name), w / 2, (int)(h * 0.16F), 2.0F, withAlpha(cfg.rarity.rgb(), a));
            drawScaled(g, "✦ Santuario de las Hadas ✦", w / 2, (int)(h * 0.16F) + 24, 1.0F, withAlpha(0xF3E6C8, a * 0.9F));
        }
        // el premio
        if (t >= REVEAL + 6 && candidates != null && !candidates.isEmpty()) {
            ItemStack win = candidates.get(winnerIndex);
            float a = smooth(REVEAL + 6, REVEAL + 18, t) * (t > TOTAL - 14 ? Math.max(0.0F, (TOTAL - t) / 14.0F) : 1.0F);
            int y0 = h - barH - 64;
            int boxW = Math.min(w - 40, 320);
            int bg = (int)(a * 150) << 24;
            g.fillGradient(w / 2 - boxW / 2, y0 - 6, w / 2 + boxW / 2, y0 + 52, bg, bg);
            int rc = winnerRarity.rgb() & 0xFFFFFF;
            g.fill(w / 2 - boxW / 2, y0 - 6, w / 2 + boxW / 2, y0 - 5, withAlpha(rc, a));
            g.fill(w / 2 - boxW / 2, y0 + 51, w / 2 + boxW / 2, y0 + 52, withAlpha(rc, a));
            drawScaled(g, "§7¡Has recibido!", w / 2, y0, 1.0F, withAlpha(0xFFFFFF, a));
            drawScaled(g, "§l" + win.getHoverName().getString(), w / 2, y0 + 13, 1.6F, withAlpha(rc, a));
            String stars = "★".repeat(winnerRarity.ordinal() + 1);
            drawScaled(g, stars + "  " + winnerRarity.displayName() + "  " + stars, w / 2, y0 + 36, 1.0F, withAlpha(rc, a));
        }
        if (canSkip() && t > 20 && t < TOTAL - 20) {
            g.drawString(font, "§8[Espacio] saltar", w - font.width("[Espacio] saltar") - 6, h - barH - 12, 0xFFFFFFFF, false);
        }
    }

    private void drawScaled(GuiGraphics g, String s, int cx, int y, float scale, int argb) {
        if (((argb >>> 24) & 255) < 5) return;
        PoseStack p = g.pose();
        p.pushPose();
        p.translate(cx, y, 400);
        p.scale(scale, scale, 1.0F);
        g.drawString(font, s, -font.width(s) / 2, 0, argb, true);
        p.popPose();
    }

    private static String stripColor(String s) {
        return s.replaceAll("§.", "");
    }

    private static int withAlpha(int rgb, float a) {
        return ((int)(Math.max(0.0F, Math.min(1.0F, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    static float smooth(float a, float b, float x) {
        float t = Math.max(0.0F, Math.min(1.0F, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
