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
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import net.tierrasfantasticas.tfclient.crates.client.render.CrateBakedModels;
import net.tierrasfantasticas.tfclient.crates.client.render.CrateStyles;
import net.tierrasfantasticas.tfclient.crates.client.scene.CinematicFx;
import net.tierrasfantasticas.tfclient.crates.client.scene.SanctuaryScene;
import net.tierrasfantasticas.tfclient.crates.client.scene.SceneFx;
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
    // (el dueño: «la llave se lanza y le pega al cofre, sale brillito, se mueve el cofre y se abre»): aparece (KEY_APPEAR), se
    // lanza (KEY_THROW), golpea el cofre (KEY_IN), se deshace en chispas; el cofre salta, se encienden las runas
    // (KEY_TURN0..1), tiembla y se abre
    static final int KEY_APPEAR = 20, KEY_THROW = 40, KEY_IN = 66, KEY_TURN0 = 70, KEY_TURN1 = 80;
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
    /** (1.3.103) Ya se dijo en el registro con qué se dibuja (paquete de shaders, shader de la escena). */
    private boolean saidHow;
    /** (1.3.103) El bloque de la crate en el mundo (sus partículas se dibujan en la escena); null si no se sabe. */
    private BlockPos cratePos;
    private final SceneCamera particleCam = new SceneCamera();
    /** Hasta dónde de la crate se dibujan las partículas del mundo (las de sus capas no van más lejos). */
    private static final double PARTICLE_R = 4.5;
    /** Las partículas a menos de esto de la cámara no se dibujan (bloques). */
    private static final double PARTICLE_NEAR = 1.6;
    /** La luz de la crate en la escena (drawCrate): un poco por debajo de la entera, para que no florezca. */
    private static final float[] CRATE_TINT = {0.86F, 0.84F, 0.80F};
    /** Para la prueba: cuántas partículas de la crate se dibujaron como mucho en un fotograma (y en cuántos alguna). */
    private int particlesMax, particleFrames, particlesNow;
    /** Para la prueba en el juego: fotogramas, tiempo medio y peor fotograma de la última cinemática. */
    public static volatile String testStats = "";
    /**
     * (1.3.107) Para la prueba en el juego: lo que no se dibuja (para saber qué pinta las rayas del dueño, una captura
     * sin cada cosa): los efectos (SceneFx), los rayos del sol del post, el post entero, el agua, las plantas.
     */
    public static volatile int testOff;
    public static final int TEST_FX = 1, TEST_RAYS = 2, TEST_POST = 4, TEST_WATER = 8, TEST_PLANTS = 16;
    /** (1.3.113) Sin supermuestreo, sin la claridad ni el FXAA de crate_post, sin tapar rendijas (ScenePost.test*). */
    public static final int TEST_SSAA = 32, TEST_SHARP = 64, TEST_FILL = 128;
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

    /**
     * El santuario quieto detrás de la pantalla de antes de abrir (CratePreOpenScreen): el encuadre de la imagen del dueño,
     * con el agua, las hadas y la luz en movimiento y el ambiente en bucle; sin llave, títulos ni sonidos de la apertura.
     * El reloj corre «antes del tick 0» (todo lo de la apertura empieza en ticks positivos), así que al pulsar ABRIR la
     * cinemática sigue desde el mismo encuadre. Null si el santuario no está o falló en esta sesión.
     */
    public static CrateCinematicScreen idle(CrateConfig cfg) {
        if (faulted || SanctuaryScene.get() == null) return null;
        CrateCinematicScreen s = new CrateCinematicScreen(cfg, 0xFFFFFF, 0, 0, java.util.Collections.emptyList(), null);
        s.idle = true;
        return s;
    }

    /** El bloque de la crate que se abre (para dibujar sus partículas en la escena). */
    public CrateCinematicScreen at(BlockPos pos) {
        cratePos = pos == null ? null : pos.immutable();
        return this;
    }

    /** Dibuja el santuario quieto (ver {@link #idle}); false si no se puede (entonces el fondo de siempre). */
    public boolean renderIdle(GuiGraphics g, int w, int h) {
        if (faulted) return false;
        if (minecraft == null || width != w || height != h) init(Minecraft.getInstance(), w, h);
        render(g, 0, 0, 0.0F);
        return !faulted;
    }

    private static final float IDLE_T0 = -40000.0F;
    private boolean idle;

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
        if (!idle) testStats = String.format("%d fotogramas, %.1f ms de media, el peor %.1f ms, %.1f s; %s", frames, frames == 0 ? 0 : frameSum / frames,
                frameMax, clock() / 20.0F, SanctuaryScene.get() == null ? "sin santuario" : SanctuaryScene.get().stats())
                + String.format("; partículas de la crate: hasta %d a la vez, en %d fotogramas%s", particlesMax, particleFrames,
                        cratePos == null ? " (sin bloque)" : "")
                + "; efectos: " + SceneFx.stats()
                + (post != null ? "; escena a " + post.scale() + "x" : "; sin shader de la escena")
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
        if (idle) return;
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

    /**
     * (1.3.103, el dueño: «el sonido es extremadamente alto, horrible») Los .ogg de la crate están muy altos (de −12 a −18
     * LUFS con picos de −1 dB; los del juego, −20 a −25) y sonaban a 0,75-1,0 en MASTER (forUI: ni el deslizador de
     * música ni el de ambiente los bajaban). Ahora: los efectos al 35 % y en «Bloques» (sin distancia, como antes), la
     * música al 0,40 y el ambiente al 0,35: todo hacia −22/−25 LUFS. (1.3.109, el dueño: «ese sonido horrendo de las
     * crates, odio ese sonido»: los efectos ya no son los sintetizados, son sonidos de Minecraft —encantamiento, brújula
     * imantada, cadena, faro, ancla, botón de madera, orbe de experiencia, subir de nivel y desafío completado—, que van
     * a −20/−25 LUFS como los del juego: al 80 %.) (1.3.112, el dueño: «súbele volumen a la música de fondo de la crate»
     * y «quiero una ambientación completa»: la música al 0,70 y el ambiente —ahora de un minuto, sin campanitas— al 0,45.)
     */
    private static final float SFX = 0.8F, MUSIC_VOL = 0.70F, AMBIENT_VOL = 0.45F;
    /** «Aerie» va a −25 LUFS en sus primeros 18 s (medido; la sintetizada, −12/−18): casi entera. */
    private static final float MC_MUSIC_VOL = 0.9F;

    private void ui(SoundEvent ev, float volume, float pitch) {
        if (minecraft == null) return;
        SoundInstance s = new SimpleSoundInstance(ev.getLocation(), SoundSource.BLOCKS, volume * SFX, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
        minecraft.getSoundManager().play(s);
        playing.add(s);
    }

    /**
     * (1.3.107, el dueño: «la música es horrible; pon esta —Dalarna—, deja de fondo los sonidos de la fuente; 3 minutos y
     * en bucle») la música y el ambiente de la fuente, en bucle y compartidos: empiezan en la pantalla de antes de abrir y
     * siguen en la apertura sin cortarse; cuando ya no se ve ninguna escena de crate se apagan despacio (Track.tick).
     * La música es tfclient:sounds/crates/musica.ogg: (1.3.109) la del dueño, dentro del mod y cifrada (SealedAudio; el
     * repo y las Releases son públicos); si faltara, «Aerie» de Minecraft.
     */
    private static Track music, ambient;
    private static volatile long sceneSeen;

    private void loops() {
        sceneSeen = System.nanoTime();
        if (minecraft == null) return;
        if (ambient == null || ambient.isStopped()) {
            ambient = new Track(tf("ambiente"), SoundSource.AMBIENT, AMBIENT_VOL, true);
            ambient.shared = true;
            minecraft.getSoundManager().play(ambient);
        }
        if (music == null || music.isStopped()) {
            boolean own = minecraft.getResourceManager().getResource(new ResourceLocation("tfclient", "sounds/crates/musica.ogg")).isPresent();
            music = new Track(tf(own ? "musica" : "musica_mc"), SoundSource.MUSIC, own ? MUSIC_VOL : MC_MUSIC_VOL, true);
            music.shared = true;
            minecraft.getSoundManager().play(music);
        }
    }

    /** La música y el ambiente: suenan en todas partes y al final se apagan despacio en vez de cortarse. */
    private static final class Track extends AbstractTickableSoundInstance {
        private final float base;
        private float fade = 1.0F, step;
        /** La música o el ambiente compartidos (loops): se apagan solos cuando ya no hay escena de crate. */
        boolean shared;

        Track(SoundEvent ev, SoundSource src, float volume) {
            this(ev, src, volume, false);
        }

        Track(SoundEvent ev, SoundSource src, float volume, boolean loop) {
            super(ev, src, SoundInstance.createUnseededRandom());
            this.base = volume;
            this.volume = volume * CratePreOpenScreen.audioGain();
            this.looping = loop;
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
            if (shared && step <= 0.0F && System.nanoTime() - sceneSeen > 600_000_000L) fadeOut(30);
            if (step > 0.0F) {
                fade -= step;
                if (fade <= 0.0F) {
                    volume = 0.0F;
                    stop();
                    return;
                }
            }
            // (1.3.112) el volumen de la esquina de la pantalla de antes de abrir (clic: silenciar; rueda: ±10 %)
            volume = base * fade * fade * CratePreOpenScreen.audioGain();
        }
    }

    /** (1.3.113, el dueño: «sonidos por rarezas») uno por rareza: 1 común … 5 mítica. */
    private int tier() {
        return Math.min(5, winnerRarity.ordinal() + 1);
    }

    private void sounds(int t) {
        switch (t) {
            // (la música y el ambiente: loops(), desde la pantalla de antes de abrir)
            case KEY_APPEAR -> ui(tf("llave_aparece"), 0.8F, 1.0F);
            // (1.3.113) los sonidos propios de tools/crates/sfx_crates.py (antes, los del juego: el dueño, «horribles»)
            case KEY_THROW -> ui(tf("llave_lanza"), 0.8F, 1.0F);
            // el golpe: la llave contra la madera del cofre (va dentro del sonido)
            case KEY_IN -> ui(tf("llave_entra"), 0.9F, 1.0F);
            case KEY_TURN0 + 2 -> ui(tf("llave_gira"), 0.6F, 1.0F);
            case RUNES0 + 2 -> ui(tf("runas"), 0.8F, 1.0F);
            case TREMBLE0 -> ui(tf("tiembla"), 0.75F, 1.0F);
            case OPEN -> ui(tf("apertura"), 1.0F, 1.0F);
            case SPIN1 -> ui(tf("parada"), 0.85F, 1.0F);
            case REVEAL -> ui(tf("premio" + tier()), 1.0F, 1.0F);
            case TOTAL - 40 -> {
                for (Track tr : tracks) tr.fadeOut(56);
                if (music != null) music.fadeOut(56);
                if (ambient != null) ambient.fadeOut(56);
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
        float shake = impulse(t, OPEN, 0.07F, 0.16F) + impulse(t, REVEAL, 0.05F, 0.14F) + impulse(t, KEY_IN, 0.05F, 0.3F)
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
        float open = 100.0F + 6.0F * (float)Math.exp(-(s - 8.0F) * 0.22F) * (float)Math.cos((s - 8.0F) * 0.8F);
        // al salir el premio la tapa se cierra despacio (abierta quedaba justo detrás del premio, como un cuadrado de color
        // pegado a él: el dueño, «que no traspase ese brillo al item»)
        return open * (1.0F - smooth(REVEAL - 6, REVEAL + 10, t));
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
        if (t >= KEY_IN && t < KEY_IN + 24) {
            // el golpe de la llave: el cofre da un brinco y se tambalea
            float s2 = t - KEY_IN;
            float e = (float)Math.exp(-s2 * 0.28F);
            hop += 0.10F * e * Math.abs((float)Math.sin(s2 * 0.8F));
            roll += 7.0F * e * (float)Math.sin(s2 * 1.1F);
            yawJ += 5.0F * e * (float)Math.sin(s2 * 0.9F + 0.6F);
            sy *= 1.0F - 0.06F * e * (float)Math.cos(s2 * 1.4F);
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
        VertexConsumer vc = sc.tinted(buf.getBuffer(RenderType.cutout()), CRATE_TINT[0], CRATE_TINT[1], CRATE_TINT[2]);
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
        if (keyStack.isEmpty() || t < KEY_APPEAR + 4 || t > KEY_IN + 10) return;
        Vector3f pos = keyPos(sc, t);
        float yaw = 0.0F, tumble = 0.0F, size = 1.0F;
        if (t < KEY_THROW) {
            // flota donde apareció, girando despacio
            size = smooth(KEY_APPEAR + 4, KEY_APPEAR + 14, t);
            yaw = (t - KEY_APPEAR) * 9.0F;
        } else if (t < KEY_IN) {
            // lanzada: da vueltas de punta a punta en el aire
            float p = (t - KEY_THROW) / (KEY_IN - KEY_THROW);
            yaw = (KEY_THROW - KEY_APPEAR) * 9.0F * (1.0F - p);
            tumble = 1080.0F * p * p;
        } else {
            // tras el golpe: rebota hacia atrás y se deshace en chispas
            float s2 = (t - KEY_IN) / 10.0F;
            tumble = -160.0F * s2;
            size = Math.max(0.0F, 1.0F - s2 * s2);
        }
        if (size <= 0.01F) return;
        pose.pushPose();
        pose.translate(pos.x, pos.y, pos.z);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(tumble));
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
        Vector3f start = keyStart(k);
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
        if (t >= KEY_APPEAR + 4 && t < KEY_IN + 8) {
            // halo que acompaña a la llave y su estela al volar
            Vector3f kp = keyPos(sc, t);
            float pulse = 0.75F + 0.25F * (float)Math.sin(t * 0.35F);
            float fade = t > KEY_IN ? 1.0F - (t - KEY_IN) / 8.0F : 1.0F;
            CinematicFx.glow(vc, v, kp.x, kp.y, kp.z, 0.6F * pulse, r, g, b, 0.45F * fade);
            CinematicFx.glow(vc, v, kp.x, kp.y, kp.z, 0.25F, 1, 1, 1, 0.4F * fade);
            if (t >= KEY_THROW && t < KEY_IN) {
                for (int i = 1; i <= 10; i++) {
                    Vector3f q = keyPos(sc, Math.max(KEY_THROW, t - i * 0.8F));
                    CinematicFx.glow(vc, v, q.x, q.y, q.z, 0.12F * (1.0F - i / 11.0F), mix(r, 1, 0.5F), mix(g, 1, 0.5F), mix(b, 1, 0.5F),
                            0.55F * (1.0F - i / 11.0F));
                }
            }
        }
        // el golpe: destello blanco en el frente del cofre y una explosión de chispas
        float flash = pulseAt(t, KEY_IN, 9.0F);
        if (flash > 0) {
            CinematicFx.glow(vc, v, k.x, k.y, k.z + 0.15F, 1.4F * flash + 0.3F, mix(r, 1, 0.5F), mix(g, 1, 0.5F), mix(b, 1, 0.5F), flash);
            CinematicFx.glow(vc, v, k.x, k.y, k.z + 0.15F, 0.5F, 1, 1, 1, flash);
        }
        if (t >= KEY_IN && t < KEY_IN + 26) {
            CinematicFx.sparkBurst(vc, v, k.x, k.y, k.z + 0.2F, t - KEY_IN, 26.0F, 70, 0.75F, col, 400);
        }
        // las runas del cofre que se encienden (la luz que se escapa por la tapa al temblar va después)
        float rune = pulseAt(t, KEY_TURN1, 12.0F);
        if (rune > 0) {
            CinematicFx.glow(vc, v, k.x, k.y, k.z + 0.05F, 0.6F * rune + 0.2F, mix(r, 1, 0.3F), mix(g, 1, 0.3F), mix(b, 1, 0.3F), rune * 0.5F);
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

    /** Donde aparece la llave: a la derecha del cofre, algo delante y por encima (dentro del cuadro de la cámara). */
    private static Vector3f keyStart(Vector3f k) {
        return new Vector3f(k.x + 1.15F, k.y + 0.45F, k.z + 1.15F);
    }

    /** Dónde está la llave: flotando junto al cofre, volando en arco hasta su frente, y rebotando tras el golpe. */
    private Vector3f keyPos(SanctuaryScene sc, float t) {
        Vector3f k = keyhole(sc);
        Vector3f start = keyStart(k);
        Vector3f hit = new Vector3f(k.x, k.y, k.z + KEY_TIP + 0.05F);
        if (t < KEY_THROW) {
            // (un poco hacia atrás antes de lanzarse: coge impulso)
            float back = smooth(KEY_THROW - 8, KEY_THROW, t);
            return new Vector3f(start.x + 0.15F * back, start.y + (float)Math.sin(t * 0.22F) * 0.06F + 0.12F * back, start.z + 0.2F * back);
        }
        if (t < KEY_IN) {
            float p = (t - KEY_THROW) / (KEY_IN - KEY_THROW);
            p = p * p * (0.6F + 0.4F * p);
            Vector3f from = new Vector3f(start.x + 0.15F, start.y + 0.12F, start.z + 0.2F);
            Vector3f ctrl = new Vector3f((from.x + hit.x) * 0.5F, Math.max(from.y, hit.y) + 0.45F, (from.z + hit.z) * 0.5F);
            return bezier(from, ctrl, hit, p);
        }
        float s2 = (t - KEY_IN) / 10.0F;
        return new Vector3f(hit.x + 0.1F * s2, hit.y + 0.35F * (float)Math.sin(Math.min(1.0F, s2) * Math.PI), hit.z + 0.45F * s2);
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
            // el halo, DETRÁS del premio (el dueño: el brillo pasaba por encima del objeto y lo teñía del color de la rareza:
            // el halo estaba en su centro y el objeto gira, la mitad quedaba detrás), y más suave (su resplandor no lo tapa)
            Vector3f back = new Vector3f(px - eye.x, py - eye.y, pz - eye.z).normalize().mul(0.8F);
            CinematicFx.glow(vc, v, px + back.x, py + back.y, pz + back.z, 1.2F + 0.5F * pulseAt(t, REVEAL, 12), wr, wg, wb,
                    0.30F * pow + 0.6F * pulseAt(t, REVEAL, 10));
            CinematicFx.glow(vc, v, px + back.x, py + back.y, pz + back.z, 0.5F, 1, 1, 1, 0.15F * pow + 0.5F * pulseAt(t, REVEAL, 8));
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
        // detrás del premio (más lejos de la cámara: el premio gira y, a 0,3, su mitad quedaba detrás de los rayos)
        Vector3f back = new Vector3f(px - eye.x, py - eye.y, pz - eye.z).normalize().mul(0.8F);
        float s = t - REVEAL;
        float out = t > TOTAL - 20 ? 1.0F - (t - (TOTAL - 20)) / 20.0F : 1.0F;
        float pow = smooth(0, 8, s) * out * (0.35F + 0.08F * winnerRarity.ordinal());
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
        // (al acabar la apertura —sus últimos 2 s— la música y el ambiente se apagan: no se vuelven a poner)
        if (idle || t < TOTAL - 40) loops();
        if (idle) {
            // (el reloj vuelve atrás cada media hora para no acercarse al tick 0, donde empieza la apertura)
            t = IDLE_T0 + t % 36000.0F;
        } else {
            int soundTick = (int)Math.floor(t);
            while (lastSoundTick < soundTick) {
                lastSoundTick++;
                sounds(lastSoundTick);
            }
            reelClicks(t);
        }
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
        if (!idle) overlay(g, sc, t);
    }

    /** El cielo de respaldo (si el shader no carga) y la bruma del santuario: verde azulada oscura (lo del fondo se pierde
     *  un poco en ella). */
    private static final float[] SKY_TOP = {0.55F, 0.80F, 0.85F}, SKY_HORIZON = {0.90F, 0.92F, 0.80F}, FOG = {0.10F, 0.20F, 0.24F};
    static final float FOG_START = 10.0F, FOG_END = 55.0F;

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
        boolean pp = ScenePost.ready() && !postFailed && (testOff & TEST_POST) == 0;
        ScenePost.testScale1 = (testOff & TEST_SSAA) != 0;
        ScenePost.testNoSharp = (testOff & TEST_SHARP) != 0;
        ScenePost.testNoFill = (testOff & TEST_FILL) != 0;
        if (!saidHow) {
            // (1.3.103, el dueño: «la escena no me mostraba los efectos») con qué se dibuja, una vez por cinemática
            saidHow = true;
            LogUtils.getLogger().info("[TF Crates] Cinemática: paquete de shaders {}, shader de la escena {}{}",
                    net.tierrasfantasticas.tfclient.vacio.ShaderPacks.inUse() ? "sí" : "no", pp ? "sí" : "no",
                    postFailed ? " (falló antes)" : ScenePost.ready() ? "" : " (no cargó)");
        }
        if (pp) {
            try {
                if (post == null) post = new ScenePost();
                ScenePost.frame();
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
            if ((testOff & TEST_PLANTS) == 0) sc.drawPlants(pose, buf, t / 20.0F, gust);
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
            if ((testOff & TEST_WATER) == 0 && !(pp && sc.drawWaterFx(view, proj, eye, t))) sc.drawWater(view, proj);
            worldParticles(view, eye, sc, t);
            float surge = pulseAt(t, OPEN, 50.0F) + pulseAt(t, REVEAL, 40.0F);

            // los pétalos de la apertura y del premio
            // (1.3.106) los efectos, como el agua: con su VertexBuffer y sus shaders (SceneFx); por el lote del juego no le
            // salían al dueño
            if ((testOff & TEST_FX) == 0) {
                VertexConsumer soft = SceneFx.begin(SceneFx.Kind.SOFT, buf);
                CinematicFx.petals(soft, fv, sc, t, t - OPEN, t >= OPEN ? 1.0F : 0.0F);
                if (t >= REVEAL) CinematicFx.petals(soft, fv, sc, t, t - REVEAL, 0.8F);
                float excite = pulseAt(t, OPEN, 60.0F) + pulseAt(t, REVEAL, 60.0F);
                SceneFx.end(SceneFx.Kind.SOFT, buf);

                // la luz que suma: las luces mágicas, las hadas, las luciérnagas, la niebla, la cascada, el sol, el polvo, el
                // agua que brilla, las runas, el pilar, la llave, los premios
                RenderSystem.depthMask(false);
                float open = smooth(OPEN - 2, OPEN + 12, t);
                float tintAmt = 0.35F * open * (0.6F + 0.4F * winnerRarity.ordinal() / 4.0F);
                VertexConsumer glow = SceneFx.begin(SceneFx.Kind.GLOW, buf);
                CinematicFx.mist(glow, fv, sc, t);
                CinematicFx.lightHalos(glow, fv, sc, t);
                CinematicFx.waterfallLights(glow, fv, sc, t);
                CinematicFx.fireflies(glow, fv, t);
                CinematicFx.fairies(glow, fv, sc, t, excite);
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
                // (al salir el premio el pilar se apaga del todo: es una caja de luz y, detrás del premio, se veía como un
                // cuadrado de color pegado a él; el dueño: «que no traspase ese brillo al item»)
                float pillar = smooth(OPEN, OPEN + 8, t) * (t >= REVEAL ? 0.3F * (1.0F - smooth(REVEAL, REVEAL + 8, t))
                        : t >= RING0 ? 1.0F - 0.7F * smooth(RING0, RING1, t) : 1.0F);
                if (t > TOTAL - 20) pillar *= Math.max(0.0F, (TOTAL - t) / 20.0F) * 0.5F + 0.5F;
                CinematicFx.pillar(glow, fv, c[0], c[1] + crateH * 0.75F, c[2], 9.0F, pillar, t, rarityColor);
                CinematicFx.shockwave(glow, fv, c[0], sc.anchor("pool")[1] + 0.03F, c[2], t - OPEN, 30.0F, 8.0F, rarityColor);
                keyLights(glow, fv, sc, t);
                itemLights(glow, fv, sc, t, eye);
                SceneFx.end(SceneFx.Kind.GLOW, buf);
                VertexConsumer rays = SceneFx.begin(SceneFx.Kind.RAYS, buf);
                itemRays(rays, fv, sc, t, eye);
                SceneFx.end(SceneFx.Kind.RAYS, buf);
            }
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
                // los rayos salen del hueco de las copas (donde brilla el sol en la imagen del dueño: arriba en el centro),
                // no del sol, que queda muy por encima de la pantalla (allí los rayos ya no llegaban a verse)
                float[] spot = sc.anchor("sunspot");
                Vector4f sp = new Vector4f(spot[0], spot[1], spot[2], 1.0F);
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
                float bloomK = 0.7F + 0.1F * smooth(OPEN, OPEN + 20, t) + 0.25F * burst;
                float rayK = 0.9F + 0.1F * smooth(OPEN, OPEN + 20, t) + 0.25F * burst;
                float[] cc = sc.anchor("crate");
                float focus = new Vector3f(cc[0], cc[1] + 0.6F, cc[2]).distance(eye);
                // (el dueño: «más contraste, más oscuro, que se note el resplandor de los faroles»)
                post.finish(su, sv, vis, bloomK, (testOff & TEST_RAYS) != 0 ? 0.0F : rayK, 1.35F, focus, 0.05F, 220.0F, t);
            } catch (Throwable e) {
                postFail(e);
                mc.getMainRenderTarget().bindWrite(true);
            }
        }
    }

    /**
     * (1.3.103) Las partículas de la crate: las capas del editor (reposo, anticipación, apertura, premio y final) y los
     * acentos, que CrateBlockEntity suelta en el mundo junto al bloque de verdad, al ritmo de la escena. Mientras dura la
     * escena el mundo no se dibuja (LevelRendererMixin), así que no se veían (el dueño: «por algún motivo la escena no me
     * los mostraba»). Aquí se dibujan las del juego que están a menos de PARTICLE_R de la crate, con una cámara puesta en
     * el mundo donde estaría la de la escena si la crate del santuario fuera el bloque: salen alrededor de la crate y los
     * bloques del santuario las tapan.
     */
    private static Boolean particleMods;

    /** ¿Está Oculus, Embeddium o uno de sus parientes (cambian cómo se dibujan las partículas)? */
    private static boolean particleMods() {
        if (particleMods == null) {
            net.minecraftforge.fml.ModList ml = net.minecraftforge.fml.ModList.get();
            particleMods = ml != null && java.util.stream.Stream.of("oculus", "embeddium", "rubidium", "iris", "sodium", "magnesium")
                    .anyMatch(ml::isLoaded);
        }
        return particleMods;
    }

    private void worldParticles(Matrix4f view, Vector3f eye, SanctuaryScene sc, float t) {
        Minecraft mc = Minecraft.getInstance();
        if (cratePos == null || mc.level == null) return;
        // (1.3.106) con Oculus o Embeddium, no: en la escena salían como cuadrados blancos (la prueba con Oculus +
        // Embeddium, aun sin paquete de shaders). Los efectos de la escena (los de serie, SceneFx) sí salen
        if (particleMods()) return;
        float[] c = sc.anchor("crate");
        double ox = cratePos.getX() + 0.5 - c[0], oy = cratePos.getY() - c[1], oz = cratePos.getZ() + 0.5 - c[2];
        // (1.3.104, el dueño: «más suaves en la escena»: que no tapen la crate ni el premio) lo que no se tapa, en el
        // mundo: {x, y, z, radio}. La crate; en la ruleta, el premio que pasa por delante; y el premio al salir.
        List<double[]> keep = new ArrayList<>();
        keep.add(new double[] {c[0] + ox, c[1] + crateH * 0.5 + oy, c[2] + oz, Math.max(0.65, crateH * 0.6)});
        float front = (float) Math.atan2(eye.x - c[0], eye.z - c[2]);
        float cy = ringY(sc);
        if (t >= RING0 && t < REVEAL) {
            keep.add(new double[] {c[0] + Math.sin(front) * RING_R + ox, cy + oy, c[2] + Math.cos(front) * RING_R + oz, 0.5});
        }
        if (t >= REVEAL - 10) {
            keep.add(new double[] {c[0] + Math.sin(front) * 1.1 + ox, cy + 0.15 + oy, c[2] + Math.cos(front) * 1.1 + oz, 0.7});
        }
        Vector3f f = new Vector3f(-view.m02(), -view.m12(), -view.m22()).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-f.x, f.z));
        float pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1.0F, Math.min(1.0F, f.y))));
        Vec3 camAt = new Vec3(eye.x + ox, eye.y + oy, eye.z + oz);
        particleCam.place(camAt, yaw, pitch);
        PoseStack ps = new PoseStack();
        ps.mulPose(view.getNormalizedRotation(new Quaternionf()));
        Vec3 center = Vec3.atBottomCenterOf(cratePos).add(0.0, 0.8, 0.0);
        Frustum near = new Frustum(new Matrix4f(), new Matrix4f()) {
            @Override
            public boolean isVisible(AABB box) {
                Vec3 p = box.getCenter();
                // (las que pasan pegadas a la cámara, no: en los primeros planos tapaban la crate, como los discos de
                // luz que ya se apagan cerca de ella en CinematicFx.nearFade)
                boolean in = p.distanceToSqr(center) < PARTICLE_R * PARTICLE_R && p.distanceToSqr(camAt) > PARTICLE_NEAR * PARTICLE_NEAR
                        && !covers(camAt, p, keep);
                if (in) particlesNow++;
                return in;
            }
        };
        particlesNow = 0;
        // con la luz de la crate de la escena (CRATE_TINT, por debajo del umbral del resplandor): con la del mundo, de día a
        // tope, florecían y salían blancas en vez de su color
        RenderSystem.setShaderColor(CRATE_TINT[0], CRATE_TINT[1], CRATE_TINT[2], 1.0F);
        try {
            mc.particleEngine.render(ps, mc.renderBuffers().bufferSource(), mc.gameRenderer.lightTexture(), particleCam,
                    mc.getFrameTime(), near);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        particlesMax = Math.max(particlesMax, particlesNow);
        if (particlesNow > 0) particleFrames++;
    }

    /** ¿Está p entre la cámara y algo de «keep» (dentro de su radio visto desde la cámara, y no por detrás)? */
    private static boolean covers(Vec3 cam, Vec3 p, List<double[]> keep) {
        double wx = p.x - cam.x, wy = p.y - cam.y, wz = p.z - cam.z;
        for (double[] k : keep) {
            double vx = k[0] - cam.x, vy = k[1] - cam.y, vz = k[2] - cam.z;
            double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (len < 1e-6) continue;
            double along = (wx * vx + wy * vy + wz * vz) / len;
            if (along <= 0.0 || along > len + k[3]) continue;
            double px = wx - vx / len * along, py = wy - vy / len * along, pz = wz - vz / len * along;
            // (el radio, a la distancia de la partícula: lo que tapa visto desde la cámara)
            double r = k[3] * along / len;
            if (px * px + py * py + pz * pz < r * r) return true;
        }
        return false;
    }

    /** La cámara de las partículas (Camera no deja ponerla a mano desde fuera). */
    private static final class SceneCamera extends Camera {
        void place(Vec3 at, float yaw, float pitch) {
            setRotation(yaw, pitch);
            setPosition(at);
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
            // (sobrio: el dueño, «el mensaje de abajo es demasiado exagerado»: sin caja ni rayas, sin negrita ni estrellas;
            // el nombre del premio un poco más grande y la rareza pequeña, en su color)
            int y0 = h - barH - 46;
            // (para que se lea sin caja: un degradado oscuro suave desde abajo)
            int sh = (int)(a * 120) << 24;
            g.fillGradient(0, y0 - 24, w, h - barH, 0x00000000, sh);
            int rc = winnerRarity.rgb() & 0xFFFFFF;
            int soft = (((((rc >> 16) & 255) + 255) / 2) << 16) | (((((rc >> 8) & 255) + 255) / 2) << 8) | (((rc & 255) + 255) / 2);
            drawScaled(g, "¡Has recibido!", w / 2, y0, 0.85F, withAlpha(0xD8D8D8, a * 0.85F));
            drawScaled(g, win.getHoverName().getString(), w / 2, y0 + 11, 1.25F, withAlpha(soft, a));
            drawScaled(g, winnerRarity.displayName(), w / 2, y0 + 25, 0.85F, withAlpha(rc, a * 0.9F));
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
