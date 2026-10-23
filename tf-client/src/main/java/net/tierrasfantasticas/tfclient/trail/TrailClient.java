/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.trail;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Estelas de arma (1.3.67), la idea de Afterswing (MIT) rehecha para Forge 1.20.1: mientras un brazo da un golpe (o
 * sacas el arma), en cada fotograma se apunta dónde está la punta de la hoja y dónde su base; la estela es la cinta
 * que une esos puntos, que se apaga y se estrecha con la edad. Los puntos salen de ItemInHandRenderer.renderItem
 * (mixin): en primera persona se guardan tal cual (el espacio de la mano) y se dibujan al acabar las manos; en tercera,
 * se pasan al mundo y se dibujan después de las partículas. La forma de la hoja (punta y empuñadura) y el color
 * automático se sacan del modelo del objeto (1.3.74: de su forma 3D si la tiene, ver readBlade), así sirven también las
 * armas de otros mods.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class TrailClient {
    /** Un punto de la estela: la punta (t) y la base (b), cuándo (nanos), su tramo y una semilla para las chispas. */
    record Sample(double tx, double ty, double tz, double bx, double by, double bz, long at, int segment, int seed) {}

    /** La estela de un brazo. */
    static final class Trail {
        final ArrayDeque<Sample> samples = new ArrayDeque<>();
        int color = 0xFFFFFF;
        float strength = 1;
        long frame = -10;
        int segment;
        long seen;

        void add(double tx, double ty, double tz, double bx, double by, double bz, long now, long frameNo) {
            // un hueco (no se apuntó en el fotograma anterior): tramo nuevo, para no unir un golpe con el siguiente
            if (frameNo > frame + 1) segment++;
            if (frameNo == frame && !samples.isEmpty()) samples.pollLast();
            samples.add(new Sample(tx, ty, tz, bx, by, bz, now, segment, (int) (now >>> 10) * 31 + segment * 7919));
            while (samples.size() > MAX_SAMPLES) samples.pollFirst();
            frame = frameNo;
            seen = now;
        }
    }

    private static final int MAX_SAMPLES = 96, MAX_POINTS = 200;
    private static final Map<Long, Trail> WORLD = new HashMap<>();
    private static final Trail[] FIRST = {new Trail(), new Trail()};
    private static long frame;
    /** Entre el cielo y el fin de las entidades: solo ahí cuenta lo que se dibuja en tercera persona (no el inventario). */
    private static boolean worldPass;

    // el jugador de este cliente: si su golpe fue un ataque (no picar un bloque ni usar algo), su fuerza y el sacar el arma
    private static boolean armed;
    private static float attackStrength = 1;
    private static long pullOutUntil;
    private static Item pullOutItem;
    private static Item lastMain;
    private static int lastSlot = -1;

    private TrailClient() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Eventos
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        TrailSettings.load();
        frame++;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            lastMain = null;
            lastSlot = -1;
            return;
        }
        if (!mc.player.swinging) armed = false;
        // sacar el arma: cambió lo de la mano (otra casilla u otro objeto) y lo nuevo deja estela
        ItemStack main = mc.player.getMainHandItem();
        int slot = mc.player.getInventory().selected;
        if (lastSlot >= 0 && (slot != lastSlot || main.getItem() != lastMain) && !main.isEmpty() && allowed(main)) {
            pullOutUntil = Util.getMillis() + 340;
            pullOutItem = main.getItem();
        }
        lastMain = main.getItem();
        lastSlot = slot;
        // las de los que ya no se ven
        long now = Util.getNanos();
        WORLD.values().removeIf(t -> now - t.seen > 3_000_000_000L);
    }

    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // picar un bloque no es un golpe
        armed = mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK;
        if (armed) attackStrength = mc.player.getAttackStrengthScale(0.5F);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        WORLD.clear();
        for (Trail t : FIRST) t.samples.clear();
        CATEGORY.clear();
        BLADES.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            worldPass = true;
            return;
        }
        if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            worldPass = false;
            return;
        }
        if (stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES || WORLD.isEmpty() || !TrailSettings.enabled) return;
        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate(), up = new Vector3f(camera.getUpVector());
        Matrix4f m = event.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        long now = Util.getNanos();
        try {
            for (Trail t : WORLD.values()) draw(t, buffers, m, cam.x, cam.y, cam.z, right, up, now, 1F);
        } catch (Throwable e) {
            TFClient.LOGGER.error("TF Client: error al dibujar las estelas", e);
            WORLD.clear();
        } finally {
            buffers.endBatch();
        }
    }

    /** Al acabar de dibujar las manos (mixin): las estelas de primera persona, en el mismo espacio que la mano. */
    public static void renderFirstPerson(MultiBufferSource.BufferSource buffers) {
        if (!TrailSettings.enabled || (FIRST[0].samples.isEmpty() && FIRST[1].samples.isEmpty())) return;
        long now = Util.getNanos();
        Matrix4f m = new Matrix4f();
        try {
            for (Trail t : FIRST) draw(t, buffers, m, 0, 0, 0, new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), now, 1F);
        } catch (Throwable e) {
            TFClient.LOGGER.error("TF Client: error al dibujar las estelas", e);
            for (Trail t : FIRST) t.samples.clear();
        } finally {
            buffers.endBatch();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Captura (mixin en ItemInHandRenderer.renderItem)
    // ---------------------------------------------------------------------------------------------------------------

    /** Si algo falla al apuntar (un arma rara de otro mod), se avisa una vez y las estelas se apagan esta sesión. */
    private static boolean broken;

    public static void capture(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, boolean left, PoseStack pose) {
        if (broken || !TrailSettings.enabled || entity == null || stack.isEmpty()) return;
        try {
            doCapture(entity, stack, ctx, left, pose);
        } catch (Throwable e) {
            broken = true;
            TFClient.LOGGER.error("TF Client: las estelas de arma fallaron y se apagan hasta reiniciar", e);
        }
    }

    private static void doCapture(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, boolean left, PoseStack pose) {
        boolean first = ctx.firstPerson();
        if (!first && ctx != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && ctx != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) return;
        if (!first && !worldPass) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean self = entity == mc.player;
        if (!first && self && mc.options.getCameraType().isFirstPerson()) return; // ya la lleva la primera persona
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (!self) {
            if (!TrailSettings.others) return;
            int d = TrailSettings.othersDistance;
            if (d > 0 && entity.distanceToSqr(cam) > (double) d * d) return;
        }
        HumanoidArm arm = left ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        InteractionHand hand = arm == entity.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (!active(entity, hand, self, stack)) return;
        if (!allowed(stack)) return;
        BakedModel model = mc.getItemRenderer().getModel(stack, entity.level(), entity, entity.getId() + ctx.ordinal());
        Blade blade = blade(model);
        // la misma transformación que hará el juego al dibujar el objeto (la del modelo para esa mano)
        PoseStack ps = new PoseStack();
        ps.last().pose().set(pose.last().pose());
        ps.last().normal().set(pose.last().normal());
        ForgeHooksClient.handleCameraTransforms(ps, model, ctx, left);
        ps.translate(-0.5F, -0.5F, -0.5F);
        Matrix4f m = ps.last().pose();
        TrailProfile profile = TrailProfile.current();
        float w = Math.min(1.25F, TrailSettings.width / 100F * profile.widthScale());
        Vector3f tip = m.transformPosition(blade.tx, blade.ty, blade.tz, new Vector3f());
        Vector3f base = m.transformPosition(blade.along(w), new Vector3f());
        long now = Util.getNanos();
        Trail t;
        if (first) {
            t = FIRST[hand.ordinal()];
            t.add(tip.x, tip.y, tip.z, base.x, base.y, base.z, now, frame);
        } else {
            Matrix3f inv = RenderSystem.getInverseViewRotationMatrix();
            inv.transform(tip);
            inv.transform(base);
            double tx = tip.x + cam.x, ty = tip.y + cam.y, tz = tip.z + cam.z;
            // si no cae junto a su dueño, no es el mundo (el muñeco del inventario, otra cámara): fuera
            Vec3 at = entity.getPosition(mc.getFrameTime());
            double dx = tx - at.x, dy = ty - (at.y + entity.getBbHeight() / 2), dz = tz - at.z;
            double lim = 2.5 + entity.getBbHeight() + entity.getBbWidth();
            if (dx * dx + dy * dy + dz * dz > lim * lim) return;
            t = WORLD.computeIfAbsent(((long) entity.getId() << 1) | hand.ordinal(), k -> new Trail());
            t.add(tx, ty, tz, base.x + cam.x, base.y + cam.y, base.z + cam.z, now, frame);
        }
        t.color = colorOf(stack, blade);
        t.strength = self && TrailSettings.cooldown && TrailSettings.attackOnly && armed ? 0.3F + 0.7F * attackStrength : 1F;
    }

    private static boolean active(LivingEntity e, InteractionHand hand, boolean self, ItemStack stack) {
        boolean swing = TrailSettings.swing && e.swinging && (e.swingingArm == null ? hand == InteractionHand.MAIN_HAND : e.swingingArm == hand);
        if (!self) return swing;
        if (swing && TrailSettings.attackOnly && !armed) swing = false;
        boolean pull = TrailSettings.pullOut && hand == InteractionHand.MAIN_HAND && stack.getItem() == pullOutItem
                && Util.getMillis() < pullOutUntil;
        return swing || pull;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Qué deja estela, la forma de la hoja y el color
    // ---------------------------------------------------------------------------------------------------------------

    private static final Map<Item, Integer> CATEGORY = new IdentityHashMap<>();
    private static final int NONE = 0, SWORD = 1, AXE = 2, PICKAXE = 3, SHOVEL = 4, HOE = 5, OTHER = 6;

    static boolean allowed(ItemStack s) {
        int c = CATEGORY.computeIfAbsent(s.getItem(), i -> category(s));
        return switch (c) {
            case SWORD -> TrailSettings.swords;
            case AXE -> TrailSettings.axes;
            case PICKAXE -> TrailSettings.pickaxes;
            case SHOVEL -> TrailSettings.shovels;
            case HOE -> TrailSettings.hoes;
            case OTHER -> TrailSettings.others_tools;
            default -> false;
        };
    }

    private static int category(ItemStack s) {
        Item i = s.getItem();
        if (i instanceof TridentItem) return NONE;
        if (i instanceof SwordItem || s.is(ItemTags.SWORDS)) return SWORD;
        if (i instanceof AxeItem || s.is(ItemTags.AXES)) return AXE;
        if (i instanceof PickaxeItem || s.is(ItemTags.PICKAXES)) return PICKAXE;
        if (i instanceof ShovelItem || s.is(ItemTags.SHOVELS)) return SHOVEL;
        if (i instanceof HoeItem || s.is(ItemTags.HOES)) return HOE;
        try {
            // otras armas (lanzas, martillos de otros mods): lo que pega más fuerte que la mano
            if (s.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE)) return OTHER;
        } catch (Exception ignored) {
        }
        return NONE;
    }

    /**
     * La punta (t) y la base de la hoja (g, la guarda) en las coordenadas del modelo (las de un bloque: 0 a 1, y hacia
     * arriba; un modelo 3D puede salirse, el espadón llega a 2), y el color.
     */
    record Blade(float tx, float ty, float tz, float gx, float gy, float gz, int color) {
        /** El punto de la hoja a esa fracción, de la punta (0) a la guarda (1). */
        Vector3f along(float f) {
            return new Vector3f(tx + (gx - tx) * f, ty + (gy - ty) * f, tz + (gz - tz) * f);
        }
    }

    private static final Map<BakedModel, Blade> BLADES = new IdentityHashMap<>();
    private static final Blade DEFAULT_BLADE = new Blade(0.94F, 0.94F, 0.5F, 0.3F, 0.3F, 0.5F, 0);
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /** La hoja del modelo con el que se dibuja el objeto (el de sus «overrides» si los tiene). */
    static Blade blade(BakedModel model) {
        if (model == null) return DEFAULT_BLADE;
        return BLADES.computeIfAbsent(model, k -> {
            try {
                return readBlade(model);
            } catch (Exception e) {
                return DEFAULT_BLADE;
            }
        });
    }

    /**
     * Un objeto plano (los de Minecraft: la textura con un poco de grosor) se mira en su textura (readFlat). Uno con
     * modelo 3D propio (las armas del servidor: el espadón mide dos bloques) se mira en su forma de verdad (readShape):
     * con la textura salía la estela a media hoja y del tamaño de una espada normal.
     */
    private static Blade readBlade(BakedModel model) {
        List<Vector3f> points = new ArrayList<>();
        RandomSource random = RandomSource.create(42L);
        boolean flat = true;
        for (Direction side : SIDES) {
            for (BakedQuad q : model.getQuads(null, side, random, ModelData.EMPTY, null)) {
                int[] v = q.getVertices();
                int stride = v.length / 4;
                for (int i = 0; i < 4; i++) {
                    Vector3f p = new Vector3f(Float.intBitsToFloat(v[i * stride]), Float.intBitsToFloat(v[i * stride + 1]),
                            Float.intBitsToFloat(v[i * stride + 2]));
                    points.add(p);
                    if (p.z < 0.43F || p.z > 0.57F || p.x < -0.01F || p.x > 1.01F || p.y < -0.01F || p.y > 1.01F) flat = false;
                }
            }
        }
        if (!flat && !points.isEmpty()) {
            Blade b = readShape(model, points);
            if (b != null) return b;
        }
        return readFlat(model);
    }

    /**
     * La hoja de un modelo 3D. Se mira como lo lleva un jugador en tercera persona (la mano en el origen; las armas
     * apuntan hacia arriba): la punta es lo más lejos de la mano por encima de ella (así una guadaña cogida por el medio
     * no toma el pie del mango por la punta) y la empuñadura, el otro extremo en esa misma línea; la hoja empieza a un
     * tercio, como en las planas. Probado con los 552 modelos de armas 3D de TF Client.
     */
    private static Blade readShape(BakedModel model, List<Vector3f> points) {
        Matrix4f hand = handSpace(model, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
        if (hand == null) hand = handSpace(model, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
        if (hand == null) return null;
        List<Vector3f> held = new ArrayList<>(points.size());
        boolean up = false;
        for (Vector3f p : points) {
            Vector3f h = hand.transformPosition(p, new Vector3f());
            held.add(h);
            if (h.y > 0) up = true;
        }
        Vector3f tip = null;
        float far = -1;
        for (Vector3f h : held) {
            if (up && h.y <= 0) continue;
            float d = h.lengthSquared();
            if (d > far) {
                far = d;
                tip = h;
            }
        }
        if (tip == null || far < 1e-6F) return null;
        Vector3f axis = new Vector3f(tip).normalize();
        float back = 0;
        for (Vector3f h : held) back = Math.min(back, h.dot(axis));
        Vector3f end = new Vector3f(axis).mul(back);
        Vector3f guard = new Vector3f(tip).sub(end).mul(0.32F).add(end);
        Matrix4f inv = hand.invert(new Matrix4f());
        Vector3f t = inv.transformPosition(tip, new Vector3f()), g = inv.transformPosition(guard, new Vector3f());
        return new Blade(t.x, t.y, t.z, g.x, g.y, g.z, textureColor(model, Float.NEGATIVE_INFINITY));
    }

    /** Del modelo a la mano que lo sostiene (lo que hace el juego al dibujarlo así), o null si lo aplasta del todo. */
    private static Matrix4f handSpace(BakedModel model, ItemDisplayContext ctx) {
        PoseStack ps = new PoseStack();
        ForgeHooksClient.handleCameraTransforms(ps, model, ctx, false);
        ps.translate(-0.5F, -0.5F, -0.5F);
        Matrix4f m = new Matrix4f(ps.last().pose());
        return Math.abs(m.determinant()) < 1e-9F ? null : m;
    }

    /**
     * Del modelo a los 16 px del objeto en una pantalla (y hacia abajo), como lo pinta GuiGraphics.renderItem con su
     * posición «gui»: la vista previa del pad pone la estela donde se ve la hoja.
     */
    static Matrix4f guiSpace(BakedModel model) {
        PoseStack ps = new PoseStack();
        ps.translate(8F, 8F, 0F);
        ps.scale(16F, -16F, 16F);
        ForgeHooksClient.handleCameraTransforms(ps, model, ItemDisplayContext.GUI, false);
        ps.translate(-0.5F, -0.5F, -0.5F);
        return new Matrix4f(ps.last().pose());
    }

    /**
     * Un objeto plano: en su textura, la punta es el píxel más arriba a la derecha y la empuñadura el más abajo a la
     * izquierda (así se dibujan las armas en Minecraft); la hoja empieza a un tercio. El color, el de la mitad de arriba
     * (la hoja), dando más peso a lo vivo y claro.
     */
    private static Blade readFlat(BakedModel model) {
        if (model == null) return DEFAULT_BLADE;
        TextureAtlasSprite sprite = model.getParticleIcon(ModelData.EMPTY);
        SpriteContents c = sprite.contents();
        NativeImage img = c.getOriginalImage();
        int w = c.width(), h = c.height();
        if (img == null || w <= 0 || h <= 0) return DEFAULT_BLADE;
        int best = Integer.MIN_VALUE, worst = Integer.MAX_VALUE, bx = w - 1, by = 0, hx = 0, hy = h - 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((img.getPixelRGBA(x, y) >>> 24) < 40) continue;
                int d = x - y;
                if (d > best) {
                    best = d;
                    bx = x;
                    by = y;
                }
                if (d < worst) {
                    worst = d;
                    hx = x;
                    hy = y;
                }
            }
        }
        if (best == Integer.MIN_VALUE) return DEFAULT_BLADE;
        float tx = (bx + 0.5F) / w, ty = 1 - (by + 0.5F) / h, ex = (hx + 0.5F) / w, ey = 1 - (hy + 0.5F) / h;
        float gx = ex + (tx - ex) * 0.32F, gy = ey + (ty - ey) * 0.32F;
        return new Blade(tx, ty, 0.5F, gx, gy, 0.5F, textureColor(model, worst + (best - worst) * 0.45F));
    }

    /** El color de la textura del modelo (con x − y desde «mid»: en las planas, solo la hoja), dando más peso a lo vivo y claro. */
    private static int textureColor(BakedModel model, float mid) {
        SpriteContents c = model.getParticleIcon(ModelData.EMPTY).contents();
        NativeImage img = c.getOriginalImage();
        int w = c.width(), h = c.height();
        if (img == null || w <= 0 || h <= 0) return 0;
        double r = 0, g = 0, b = 0, sum = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = img.getPixelRGBA(x, y);
                if ((p >>> 24) < 40 || x - y < mid) continue;
                int pr = p & 255, pg = (p >> 8) & 255, pb = (p >> 16) & 255;
                int max = Math.max(pr, Math.max(pg, pb)), min = Math.min(pr, Math.min(pg, pb));
                double weight = (max / 255.0) * (0.25 + (max == 0 ? 0 : (max - min) / (double) max));
                r += pr * weight;
                g += pg * weight;
                b += pb * weight;
                sum += weight;
            }
        }
        return sum <= 0 ? 0 : liven((int) (r / sum), (int) (g / sum), (int) (b / sum));
    }

    /** Un color de la textura, más vivo y claro (las estelas son luz); los grises, un blanco frío. */
    private static int liven(int r, int g, int b) {
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        if (hsb[1] < 0.14F) return 0xE6EEFF;
        return java.awt.Color.HSBtoRGB(hsb[0], Math.min(1F, hsb[1] * 1.35F + 0.1F), Math.max(0.88F, hsb[2])) & 0xFFFFFF;
    }

    /** El color de la estela: el elegido o, en automático, el del material (o el de la textura) con un toque si tiene encantamientos. */
    static int colorOf(ItemStack stack, Blade blade) {
        if (!TrailSettings.autoColor) return TrailSettings.COLORS[Math.max(0, Math.min(TrailSettings.COLORS.length - 1, TrailSettings.color))];
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        int c;
        if (id.contains("netherite")) c = 0xB07CFF;
        else if (id.contains("diamond")) c = 0x58F4E6;
        else if (id.contains("golden") || id.contains("gold")) c = 0xFFD54E;
        else if (id.contains("iron")) c = 0xE8F0FF;
        else if (id.contains("stone")) c = 0xC9CED6;
        else if (id.contains("wooden") || id.contains("wood")) c = 0xE6B878;
        else c = blade.color != 0 ? blade.color : 0xA8D8FF;
        if (stack.isEnchanted()) c = mix(c, 0xC98BFF, 0.35F);
        return c;
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dibujo (también lo usa la vista previa del pad)
    // ---------------------------------------------------------------------------------------------------------------

    /** Lo que vive cada trozo de estela, en nanos. */
    static long lifetime() {
        return (long) (TrailSettings.length * 50_000_000L * TrailProfile.current().lengthScale());
    }

    /** Quita lo viejo; true si queda algo. */
    static boolean prune(Trail t, long now) {
        long life = lifetime();
        while (!t.samples.isEmpty() && now - t.samples.peekFirst().at() > life) t.samples.pollFirst();
        return !t.samples.isEmpty();
    }

    // los puntos de un tramo, ya suavizados (reutilizados entre fotogramas: nada de basura)
    private static final double[] PT = new double[MAX_POINTS * 6];
    private static final float[] AGE = new float[MAX_POINTS];

    /**
     * Dibuja una estela: m lleva las coordenadas (menos ox, oy, oz) a la pantalla; right y up, las direcciones de la
     * cámara para las chispas y los píxeles. alphaScale multiplica la opacidad (la vista previa).
     */
    static void draw(Trail t, MultiBufferSource buffers, Matrix4f m, double ox, double oy, double oz, Vector3f right, Vector3f up, long now,
                     float alphaScale) {
        if (!prune(t, now) || t.samples.size() < 2) return;
        TrailProfile profile = TrailProfile.current();
        long life = lifetime();
        int blend = TrailSettings.blend;
        boolean additive = blend == 0;
        float global = TrailSettings.opacity / 100F * t.strength * alphaScale * (blend == 2 ? 1.7F : 1F)
                * (additive && TrailSettings.reducedFlash ? 0.7F : 1F);
        List<Sample> seg = new ArrayList<>();
        Iterator<Sample> it = t.samples.iterator();
        Sample prev = null;
        while (true) {
            Sample s = it.hasNext() ? it.next() : null;
            if (s == null || (prev != null && s.segment() != prev.segment())) {
                if (!seg.isEmpty()) drawSegment(seg, profile, t.color, global, additive, buffers, m, ox, oy, oz, right, up, now, life);
                seg.clear();
                if (s == null) break;
            }
            seg.add(s);
            prev = s;
        }
    }

    private static void drawSegment(List<Sample> seg, TrailProfile profile, int color, float global, boolean additive, MultiBufferSource buffers,
                                    Matrix4f m, double ox, double oy, double oz, Vector3f right, Vector3f up, long now, long life) {
        int n = smooth(seg, now, life);
        float time = (now % 100_000_000_000L) / 1e9F;
        int index = 0;
        for (TrailProfile.Layer layer : profile.layers()) {
            switch (layer.type()) {
                case RIBBON -> {
                    if (n >= 2) ribbon(layer, n, color, global, additive, buffers, m, ox, oy, oz, time);
                }
                case SPARKS -> sparks(layer, index, seg, color, global, additive, buffers, m, ox, oy, oz, right, up, now, life);
                case PIXELS -> pixels(layer, seg, color, global, additive, buffers, m, ox, oy, oz, right, up, now, life);
            }
            index++;
        }
        // el filo brillante (ajuste aparte): una línea fina y clara por el borde de la punta
        if (TrailSettings.core > 0 && n >= 2) {
            float k = TrailSettings.core / 100F * (TrailSettings.reducedFlash ? 0.5F : 1F);
            TrailProfile.Layer core = new TrailProfile.Layer(TrailProfile.Type.RIBBON, 0, 0.07F, k, 0, 1.6F, 0, 1.2F,
                    TrailSettings.reducedFlash ? 0.3F : 0.75F, null, 1, 0, 0, 0, 0, 0, 0, 0);
            ribbon(core, n, color, global, additive, buffers, m, ox, oy, oz, time);
        }
    }

    /** Pasa el tramo a PT/AGE, suavizado con Catmull-Rom (salvo con poco detalle). Devuelve cuántos puntos hay. */
    private static int smooth(List<Sample> seg, long now, long life) {
        int raw = seg.size();
        int k = TrailSettings.lowDetail || raw < 3 ? 1 : Math.max(1, Math.min(4, (MAX_POINTS - 1) / Math.max(1, raw - 1)));
        int n = 0;
        for (int i = 0; i < raw - 1 && n < MAX_POINTS - 1; i++) {
            Sample p0 = seg.get(Math.max(0, i - 1)), p1 = seg.get(i), p2 = seg.get(i + 1), p3 = seg.get(Math.min(raw - 1, i + 2));
            for (int j = 0; j < k && n < MAX_POINTS - 1; j++) {
                float u = j / (float) k;
                int o = n * 6;
                PT[o] = cr(p0.tx(), p1.tx(), p2.tx(), p3.tx(), u);
                PT[o + 1] = cr(p0.ty(), p1.ty(), p2.ty(), p3.ty(), u);
                PT[o + 2] = cr(p0.tz(), p1.tz(), p2.tz(), p3.tz(), u);
                PT[o + 3] = cr(p0.bx(), p1.bx(), p2.bx(), p3.bx(), u);
                PT[o + 4] = cr(p0.by(), p1.by(), p2.by(), p3.by(), u);
                PT[o + 5] = cr(p0.bz(), p1.bz(), p2.bz(), p3.bz(), u);
                AGE[n] = age(p1.at() + (long) ((p2.at() - p1.at()) * u), now, life);
                n++;
            }
        }
        Sample last = seg.get(raw - 1);
        int o = n * 6;
        PT[o] = last.tx();
        PT[o + 1] = last.ty();
        PT[o + 2] = last.tz();
        PT[o + 3] = last.bx();
        PT[o + 4] = last.by();
        PT[o + 5] = last.bz();
        AGE[n] = age(last.at(), now, life);
        return n + 1;
    }

    private static double cr(double p0, double p1, double p2, double p3, float u) {
        double u2 = u * u, u3 = u2 * u;
        return 0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }

    private static float age(long at, long now, long life) {
        return Math.max(0F, Math.min(1F, (now - at) / (float) life));
    }

    /** La opacidad de una capa a esa edad (0 lo nuevo, 1 lo que se va). */
    private static float alpha(TrailProfile.Layer l, float age, float global) {
        float fp = switch (TrailSettings.fade) {
            case 1 -> 0.6F;
            case 2 -> 1F;
            case 3 -> 3F;
            default -> l.fadePower();
        };
        float k = (float) Math.pow(1 - age, Math.max(0.05F, fp));
        float a = l.alphaTo() + (l.alphaFrom() - l.alphaTo()) * k;
        a = Math.max(a, TrailSettings.minOpacity / 100F * l.alphaFrom());
        a *= Math.min(1F, (1 - age) / 0.12F); // la punta de la cola se va sin saltos
        return Math.max(0F, Math.min(1F, a * global));
    }

    /** El color de una capa (brillo y blanco) como r, g, b de 0 a 1. */
    private static float[] rgb(TrailProfile.Layer l, int color) {
        float bright = TrailSettings.reducedFlash ? Math.min(1F, l.brightness()) : l.brightness();
        float white = TrailSettings.reducedFlash ? l.whiten() * 0.5F : l.whiten();
        float[] c = {((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F};
        for (int i = 0; i < 3; i++) {
            float v = Math.min(1F, c[i] * bright);
            c[i] = v + (1 - v) * white;
        }
        return c;
    }

    private static RenderType type(TrailProfile.Layer l, boolean additive) {
        if (l.texture() != null) return TrailRenderTypes.textured(l.texture(), additive);
        return additive ? TrailRenderTypes.GLOW : TrailRenderTypes.TRANSLUCENT;
    }

    private static void ribbon(TrailProfile.Layer l, int n, int color, float global, boolean additive, MultiBufferSource buffers, Matrix4f m,
                               double ox, double oy, double oz, float time) {
        VertexConsumer vc = buffers.getBuffer(type(l, additive));
        boolean tex = l.texture() != null;
        float[] c = rgb(l, color);
        float span = l.to() - l.from();
        float scroll = time * l.textureScroll();
        float[] prev = null;
        for (int i = 0; i < n; i++) {
            int o = i * 6;
            float age = AGE[i];
            float taper = l.taperPower() <= 0 ? 1F : (float) Math.pow(1 - age, l.taperPower());
            float f0 = l.from(), f1 = l.from() + span * taper;
            double tx = PT[o], ty = PT[o + 1], tz = PT[o + 2], dx = PT[o + 3] - tx, dy = PT[o + 4] - ty, dz = PT[o + 5] - tz;
            float[] cur = {
                    (float) (tx + dx * f0 - ox), (float) (ty + dy * f0 - oy), (float) (tz + dz * f0 - oz),
                    (float) (tx + dx * f1 - ox), (float) (ty + dy * f1 - oy), (float) (tz + dz * f1 - oz),
                    alpha(l, age, global), age * l.textureRepeat() - scroll};
            if (prev != null && (prev[6] > 0.002F || cur[6] > 0.002F)) {
                vertex(vc, m, prev[0], prev[1], prev[2], c, prev[6], tex, prev[7], 0);
                vertex(vc, m, prev[3], prev[4], prev[5], c, prev[6], tex, prev[7], 1);
                vertex(vc, m, cur[3], cur[4], cur[5], c, cur[6], tex, cur[7], 1);
                vertex(vc, m, cur[0], cur[1], cur[2], c, cur[6], tex, cur[7], 0);
            }
            prev = cur;
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float[] c, float a, boolean tex, float u, float v) {
        vc.vertex(m, x, y, z).color(c[0], c[1], c[2], a);
        if (tex) vc.uv(u, v);
        vc.endVertex();
    }

    private static int hash(int x) {
        x ^= x >>> 16;
        x *= 0x7FEB352D;
        x ^= x >>> 15;
        x *= 0x846CA68B;
        x ^= x >>> 16;
        return x;
    }

    private static double bladeLength(Sample s) {
        double dx = s.bx() - s.tx(), dy = s.by() - s.ty(), dz = s.bz() - s.tz();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Chispas que se escapan del camino (siempre las mismas para cada punto: la semilla). */
    private static void sparks(TrailProfile.Layer l, int index, List<Sample> seg, int color, float global, boolean additive,
                               MultiBufferSource buffers, Matrix4f m, double ox, double oy, double oz, Vector3f right, Vector3f up, long now,
                               long life) {
        VertexConsumer vc = buffers.getBuffer(type(l, additive));
        float[] c = rgb(l, color);
        int step = TrailSettings.lowDetail ? 2 : 1;
        for (int i = 0; i < seg.size(); i += step) {
            Sample s = seg.get(i);
            float age = age(s.at(), now, life);
            float a = alpha(l, age, global);
            if (a <= 0.01F) continue;
            double len = bladeLength(s), scale = Math.max(0.3, len / 0.55);
            for (int j = 0; j < l.perSample(); j++) {
                int h = hash(s.seed() * 31 + j * 7919 + index * 104_729);
                if ((h & 0xFFFF) / 65535F > l.density()) continue;
                float along = l.from() + (l.to() - l.from()) * (((h >>> 16) & 0xFFFF) / 65535F);
                int h2 = hash(h);
                float rx = ((h2 & 0x3FF) / 1023F - 0.5F) * 2, ry = (((h2 >>> 10) & 0x3FF) / 1023F - 0.5F) * 2,
                        rz = (((h2 >>> 20) & 0x3FF) / 1023F - 0.5F) * 2;
                double drift = l.drift() * age * scale;
                double x = s.tx() + (s.bx() - s.tx()) * along + rx * drift + up.x() * drift * 0.6;
                double y = s.ty() + (s.by() - s.ty()) * along + ry * drift + up.y() * drift * 0.6;
                double z = s.tz() + (s.bz() - s.tz()) * along + rz * drift + up.z() * drift * 0.6;
                float size = (float) (l.size() * (1 - 0.6F * age) * scale);
                square(vc, m, (float) (x - ox), (float) (y - oy), (float) (z - oz), size, right, up, c, a);
            }
        }
    }

    /** Cuadritos a lo ancho de la hoja que bajan de tamaño por saltos (steps) y se apagan. */
    private static void pixels(TrailProfile.Layer l, List<Sample> seg, int color, float global, boolean additive, MultiBufferSource buffers,
                               Matrix4f m, double ox, double oy, double oz, Vector3f right, Vector3f up, long now, long life) {
        VertexConsumer vc = buffers.getBuffer(type(l, additive));
        float[] c = rgb(l, color);
        int cells = Math.max(1, l.cells()), steps = Math.max(1, l.steps());
        Sample last = null;
        for (Sample s : seg) {
            double len = bladeLength(s);
            double cell = len * (l.to() - l.from()) / cells;
            // con muchos fotogramas, un cuadrito cada casi su tamaño (no cientos encima unos de otros)
            if (last != null) {
                double dx = s.tx() - last.tx(), dy = s.ty() - last.ty(), dz = s.tz() - last.tz();
                if (dx * dx + dy * dy + dz * dz < cell * cell * 0.6) continue;
            }
            last = s;
            float age = age(s.at(), now, life);
            float q = (float) Math.ceil((1 - age) * steps) / steps;
            float a = (float) Math.ceil(alpha(l, age, global) * steps) / steps;
            if (q <= 0 || a <= 0.01F) continue;
            for (int k = 0; k < cells; k++) {
                float f = l.from() + (l.to() - l.from()) * (k + 0.5F) / cells;
                double x = s.tx() + (s.bx() - s.tx()) * f, y = s.ty() + (s.by() - s.ty()) * f, z = s.tz() + (s.bz() - s.tz()) * f;
                square(vc, m, (float) (x - ox), (float) (y - oy), (float) (z - oz), (float) (cell * 0.5 * q), right, up, c, a);
            }
        }
    }

    private static void square(VertexConsumer vc, Matrix4f m, float x, float y, float z, float s, Vector3f right, Vector3f up, float[] c,
                               float a) {
        float rx = right.x() * s, ry = right.y() * s, rz = right.z() * s, ux = up.x() * s, uy = up.y() * s, uz = up.z() * s;
        vertex(vc, m, x - rx - ux, y - ry - uy, z - rz - uz, c, a, false, 0, 0);
        vertex(vc, m, x + rx - ux, y + ry - uy, z + rz - uz, c, a, false, 0, 0);
        vertex(vc, m, x + rx + ux, y + ry + uy, z + rz + uz, c, a, false, 0, 0);
        vertex(vc, m, x - rx + ux, y - ry + uy, z - rz + uz, c, a, false, 0, 0);
    }
}
