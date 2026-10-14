package net.tierrasfantasticas.tfclient.skills;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.vfx.VfxModel;
import net.tierrasfantasticas.tfclient.vfx.VfxPose;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Las skills en el cliente: los efectos que manda el servidor (actores con su modelo en la cabeza o su modelo de
 * ModelEngine animado), las partículas y sonidos, y la barra de la clase (ver {@link SkillHud}). Todo se dibuja a
 * plena luz, como los efectos de los packs.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class SkillClient {
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    private static final Map<Integer, Actor> ACTORS = new HashMap<>();
    private static final Map<String, Optional<VfxModel>> MODELS = new HashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    /** Bloques que se ven cambiados un rato (blockmask): posición → (lo que había, lo que se puso, hasta cuándo). */
    private static final Map<BlockPos, Mask> MASKS = new HashMap<>();
    /** Entidades escondidas (hide): id → hasta cuándo (ticks del cliente). */
    private static final Map<Integer, Long> HIDDEN = new HashMap<>();
    /** A quién se le subió la pose al dibujarlo (montado): para bajarla al terminar. */
    private static final Set<Integer> RAISED = new HashSet<>();
    private static final ItemStack STACK = new ItemStack(Items.PAPER);
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int MAX_ACTORS = 1500;

    // Barra de la clase
    static String classId = "";
    static int[] left = new int[0];
    static int[] total = new int[0];
    /** Cuándo se pulsó cada tecla sin estar lista (para el aviso rojo). */
    static long[] denied = new long[0];
    static long clientTicks;

    private record Mask(BlockState original, BlockState shown, long until) {}

    private SkillClient() {}

    // ------------------------------------------------------------------------------------------- mensajes

    static void state(SkillNet.State m) {
        classId = m.classId();
        left = m.left().clone();
        total = m.total().clone();
        if (denied.length != left.length) denied = new long[left.length];
        SkillHud.serverSelected(m.selected());
    }

    static void spawn(SkillNet.Spawn m) {
        if (Minecraft.getInstance().level == null) return;
        if (ACTORS.size() >= MAX_ACTORS) return;
        Actor a = ACTORS.computeIfAbsent(m.id(), Actor::new);
        a.pos = a.prev = new Vec3(m.x(), m.y(), m.z());
        a.yaw = a.prevYaw = m.yaw();
        a.pitch = a.prevPitch = m.pitch();
        a.head = m.head() == null ? null : new ResourceLocation(m.head());
        a.hand = m.hand() == null ? null : new ResourceLocation(m.hand());
        a.small = m.small();
        a.follow = m.follow();
        a.hideHost = m.hideHost();
        a.cls = m.cls();
        SkillDefs.ClassDef cd = SkillDefs.get(m.cls());
        a.def = cd == null ? null : cd.mob(m.mob());
        a.text = m.text();
        a.scale = m.scale() <= 0 ? 1F : m.scale();
        a.boss = SkillDefs.isBoss(m.cls());
        if (a.boss && a.layers == null) a.layers = new net.tierrasfantasticas.tfclient.vfx.VfxLayers();
        a.setModel(m.me());
        if (m.anim() != null) a.play(m.anim(), 1F);
    }

    static void moves(SkillNet.Moves m) {
        for (int i = 0; i < m.ids().length; i++) {
            Actor a = ACTORS.get(m.ids()[i]);
            if (a == null) continue;
            a.target = new Vec3(m.pos()[i * 3], m.pos()[i * 3 + 1], m.pos()[i * 3 + 2]);
            a.targetYaw = m.rot()[i * 2];
            a.targetPitch = m.rot()[i * 2 + 1];
        }
    }

    static void prop(SkillNet.Prop m) {
        Actor a = ACTORS.get(m.id());
        if (a == null) return;
        switch (m.key()) {
            case "head" -> a.head = m.a().isEmpty() ? null : new ResourceLocation(m.a());
            case "hand" -> a.hand = m.a().isEmpty() ? null : new ResourceLocation(m.a());
            case "me" -> a.setModel(m.a().isEmpty() ? null : m.a());
            case "state" -> a.play(m.a(), parse(m.b(), 1F));
            case "stop" -> {
                if (a.animName != null && a.animName.equalsIgnoreCase(m.a())) a.playIdle();
            }
            case "part" -> {
                VfxModel other = model(m.b());
                if (a.model != null && other != null) {
                    int b = a.model.bone(m.a());
                    if (b >= 0) {
                        a.source[b] = other;
                        a.sourceBone[b] = m.c().isEmpty() ? m.a() : m.c();
                    }
                }
            }
            case "vis" -> {
                if (a.model != null) {
                    int b = a.model.bone(m.a());
                    if (b >= 0) a.setVisible(b, Boolean.parseBoolean(m.b()), !"false".equals(m.c()));
                }
            }
            case "tint" -> {
                Vector3f c = color(m.a(), new Vector3f(1, 1, 1));
                a.tr = c.x;
                a.tg = c.y;
                a.tb = c.z;
            }
            case "remove" -> ACTORS.remove(m.id());
            case "text" -> a.text = m.a().isEmpty() ? null : m.a();
            case "scale" -> a.scale = Math.max(0.01F, parse(m.a(), 1F));
            case "walk" -> a.walk("1".equals(m.a()));
            case "mount" -> {
                a.rider = m.a().isEmpty() ? -1 : (int) parse(m.a(), -1F);
                a.seat = parse(m.b(), 1F);
            }
            // Jefes: animaciones a capas, por defecto, muerte, brillo, remapeo, giro del display y bala de ítem
            case "lstate" -> {
                if (a.layers == null || a.model == null) return;
                String[] p = m.b().split("\\|");
                float sp = p.length > 0 ? parse(p[0], 1F) : 1F;
                int li = p.length > 1 ? (int) parse(p[1], 0F) : 0, lo = p.length > 2 ? (int) parse(p[2], 0F) : 0;
                int pr = p.length > 3 ? (int) parse(p[3], 1F) : 1;
                boolean force = p.length > 4 && "1".equals(p[4]);
                int ago = (int) parse(m.c(), 0F);
                a.layers.play(a.model, m.a(), a.age - ago, sp, li, lo, pr, force);
            }
            case "lstop" -> {
                if (a.layers != null) a.layers.stop(m.a(), a.age, (int) parse(m.b(), -1F));
            }
            case "dstate" -> {
                if (a.layers != null) a.layers.setDefault(m.a(), m.b(), a.age);
            }
            case "death" -> {
                if (a.layers != null && a.model != null) a.layers.death(a.model, a.age);
            }
            case "bright" -> a.bright(m.a(), m.b(), !"0".equals(m.c()));
            case "seg" -> a.segment(m.a(), m.b(), !"0".equals(m.c()));
            // ModelEngine 4 (ver SkillMeg)
            case "lmod" -> {
                if (a.layers == null) return;
                String[] p = m.b().split("\\|", -1);
                int pr = (int) parse(p[0], -1F);
                float sp = p[1].equals("NaN") ? Float.NaN : parse(p[1], Float.NaN);
                Boolean ov = p.length > 5 && !p[5].isEmpty() ? "1".equals(p[5]) : null;
                a.layers.modify(m.a(), pr, a.age, sp, (int) parse(p[2], -1F), (int) parse(p[3], -1F), (int) parse(p[4], -1F), ov);
            }
            case "headcfg" -> a.headConfig(m.a(), m.b(), !"0".equals(m.c()));
            case "headlock" -> {
                if (!m.a().isEmpty()) a.lockHeadPitch = "1".equals(m.a());
                if (!m.b().isEmpty()) a.lockHeadYaw = "1".equals(m.b());
            }
            case "headtgt" -> {
                if ("clear".equals(m.c())) {
                    a.headTargetId = -1;
                    a.headTargetPos = null;
                } else {
                    String[] p = m.b().split("\\|");
                    a.headTargetId = m.a().isEmpty() ? -1 : (int) parse(m.a(), -1F);
                    a.headTargetPos = new Vec3(parse(p[0], 0F), parse(p[1], 0F), parse(p[2], 0F));
                    a.headTargetY = p.length > 3 ? parse(p[3], 0F) : 0F;
                }
            }
            case "jig" -> {
                if (a.physics == null) return;
                String[] p = m.b().split("\\|");
                a.physics.impulse(m.a(), !"0".equals(m.c()), new Vector3f(parse(p[1], 0F), parse(p[2], 0F), parse(p[3], 0F)),
                        parse(p[0], 0F), new Vector3f(parse(p[4], 0F), parse(p[5], 0F), parse(p[6], 0F)));
            }
            case "bitem" -> a.boneItem(m.a(), m.b(), !"0".equals(m.c()));
            case "btag" -> a.boneTag(m.a(), m.b(), "1".equals(m.c()));
            case "btagv" -> {
                for (int b : a.match(m.a(), true, false)) {
                    if (a.boneTagOn != null) a.boneTagOn[b] = "1".equals(m.b());
                }
            }
            case "ench" -> {
                if (a.model == null) return;
                if (a.boneGlint == null) a.boneGlint = new boolean[a.model.bones.length];
                for (int b : a.match(m.a(), m.c().charAt(0) == '1', m.c().length() > 1 && m.c().charAt(1) == '1')) a.boneGlint[b] = "1".equals(m.b());
            }
            case "bill" -> {
                if (a.model == null) return;
                if (a.boneBillboard == null) a.boneBillboard = new int[a.model.bones.length];
                int mode = switch (m.b()) {
                    case "VERTICAL" -> 1;
                    case "HORIZONTAL" -> 2;
                    case "CENTER" -> 3;
                    default -> 0;
                };
                for (int b : a.match(m.a(), m.c().charAt(0) == '1', m.c().length() > 1 && m.c().charAt(1) == '1')) a.boneBillboard[b] = mode;
            }
            case "bglow" -> {
                if (a.model == null) return;
                if (a.boneGlow == null) a.boneGlow = new int[a.model.bones.length];
                String[] p = m.b().split("\\|");
                int color = 0;
                if ("1".equals(p[0])) {
                    try {
                        color = 0xFF000000 | (int) Long.parseLong(p.length > 1 && !p[1].isEmpty() ? p[1] : "FFFFFF", 16);
                    } catch (NumberFormatException bad) {
                        color = 0xFFFFFFFF; // color mal escrito en el pack: blanco
                    }
                }
                for (int b : a.match(m.a(), m.c().charAt(0) == '1', m.c().length() > 1 && m.c().charAt(1) == '1')) a.boneGlow[b] = color;
            }
            case "dtint" -> a.damageTint = "1".equals(m.a());
            case "only" -> a.only(m.a());
            case "vfxvis" -> a.vfxVisible = "1".equals(m.a());
            case "vfxtf" -> {
                Vector3f v = "pos".equals(m.a()) ? a.vfxPos : "rot".equals(m.a()) ? a.vfxRot : a.vfxScale;
                if ("reset".equals(m.b())) {
                    if (v == a.vfxScale) v.set(1, 1, 1);
                    else v.zero();
                    if (v == a.vfxRot) a.vfxWorldRot = false;
                } else {
                    String[] p = m.b().split("\\|");
                    Vector3f n = new Vector3f(parse(p[0], 0F), parse(p[1], 0F), parse(p[2], 0F));
                    if (p.length > 3 && "1".equals(p[3])) v.add(n);
                    else v.set(n);
                    if (v == a.vfxRot) a.vfxWorldRot = p.length > 4 && "1".equals(p[4]);
                }
            }
            case "ik", "iktgt", "ikchain" -> a.ikMessage(m.key(), m.a(), m.b(), m.c());
            case "leash" -> {
                int who = (int) parse(m.a(), -1F);
                if (a.leashes == null) a.leashes = new HashMap<>();
                if ("1".equals(m.c())) a.leashes.put(who, m.b());
                else a.leashes.remove(who);
            }
            case "leashself" -> {
                if (a.selfLeashes == null) a.selfLeashes = new ArrayList<>();
                a.selfLeashes.removeIf(p -> p[0].equalsIgnoreCase(m.a()) && ("0".equals(m.c()) || p[1].equalsIgnoreCase(m.b())));
                if ("1".equals(m.c())) a.selfLeashes.add(new String[] {m.a(), m.b()});
            }
            case "skin" -> {
                a.skin = m.b().isEmpty() ? null : m.b();
                a.skinSlim = "true".equalsIgnoreCase(m.c());
            }
            case "remap" -> a.remap(m.a());
            case "dt" -> a.displayTransform(m.a(), m.b());
            case "item" -> {
                a.item = m.a().isEmpty() ? null : new ResourceLocation(m.a());
                a.itemScale = parse(m.b(), 1F);
            }
            default -> { }
        }
    }

    static void fx(SkillNet.Fx m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        if (m.kind() == 9) {
            ResourceLocation rl = ResourceLocation.tryParse(m.id());
            if (rl == null) return;
            SoundSource source = "h".equals(m.extra()) ? SoundSource.HOSTILE : SoundSource.PLAYERS;
            mc.getSoundManager().play(new SimpleSoundInstance(rl, source, m.hs(), m.vs(), RandomSource.create(RANDOM.nextLong()),
                    false, 0, SoundInstance.Attenuation.LINEAR, m.x(), m.y(), m.z(), false));
            return;
        }
        if (m.kind() == 4) {
            mask(level, m);
            return;
        }
        if (m.kind() == 5) { // esconder / enseñar una entidad
            int id = (int) parse(m.id(), -1F);
            if (m.n() <= 0) HIDDEN.remove(id);
            else HIDDEN.put(id, clientTicks + m.n());
            return;
        }
        if (m.kind() == 6) {
            // Temblor de cámara de un golpe de jefe (las skills de clase ya no lo mandan): solo la vista
            CameraShake.add(m.hs(), m.vs(), m.radius());
            return;
        }
        ParticleOptions opts = particle(m.id(), m.extra(), m.size());
        if (opts == null || particleBudget <= 0) return;
        Vec3 at = new Vec3(m.x(), m.y(), m.z());
        switch (m.kind()) {
            case 1 -> { // esfera
                for (int i = 0, n = Math.min(m.n(), MAX_PER_FX); i < n; i++) {
                    double u = RANDOM.nextDouble() * 2 - 1, a = RANDOM.nextDouble() * Math.PI * 2, rr = Math.sqrt(1 - u * u);
                    Vec3 d = new Vec3(rr * Math.cos(a), u, rr * Math.sin(a));
                    Vec3 p = at.add(d.scale(m.radius()));
                    add(level, opts, p.x, p.y, p.z, d.x * m.speed(), d.y * m.speed(), d.z * m.speed());
                }
            }
            case 2 -> { // anillo: si pide demasiadas, con menos puntos (sigue siendo un anillo entero)
                int per = Math.max(1, Math.min(m.n(), 4));
                int points = Math.max(1, Math.min(m.points(), MAX_PER_FX * 2 / per));
                for (int i = 0; i < points; i++) {
                    double a = Math.PI * 2 * i / points;
                    burst(level, opts, at.add(Math.cos(a) * m.radius(), 0, Math.sin(a) * m.radius()), per, m.hs(), m.vs(), m.speed());
                }
            }
            case 3 -> { // línea
                Vec3 to = new Vec3(m.x2(), m.y2(), m.z2());
                double len = to.distanceTo(at);
                int steps = (int) Math.min(MAX_PER_FX, len / Math.max(0.1, m.radius()));
                for (int i = 0; i <= steps; i++) {
                    Vec3 p = at.lerp(to, steps == 0 ? 0 : (double) i / steps);
                    burst(level, opts, p, Math.max(1, Math.min(m.n(), 2)), 0, 0, 0);
                }
            }
            default -> {
                if (m.n() <= 0) {
                    add(level, opts, at.x, at.y, at.z, m.hs() * m.speed(), m.vs() * m.speed(), m.hs() * m.speed());
                } else {
                    burst(level, opts, at, Math.min(m.n(), MAX_PER_FX), m.hs(), m.vs(), m.speed());
                }
            }
        }
    }

    /** Como hace Minecraft con las partículas que manda el servidor: desvío y velocidad gaussianos. */
    private static void burst(ClientLevel level, ParticleOptions opts, Vec3 at, int n, double hs, double vs, double sp) {
        for (int i = 0; i < n; i++) {
            add(level, opts, at.x + RANDOM.nextGaussian() * hs, at.y + RANDOM.nextGaussian() * vs,
                    at.z + RANDOM.nextGaussian() * hs, RANDOM.nextGaussian() * sp, RANDOM.nextGaussian() * sp, RANDOM.nextGaussian() * sp);
        }
    }

    /**
     * Una partícula, si queda presupuesto en este tick. Sin forzar: Minecraft aplica el ajuste de Partículas del jugador
     * (Todas / Reducidas / Mínimas) y no dibuja las que están lejos.
     */
    private static void add(ClientLevel level, ParticleOptions opts, double x, double y, double z, double vx, double vy, double vz) {
        if (particleBudget <= 0) return;
        particleBudget--;
        level.addParticle(opts, false, x, y, z, vx, vy, vz);
    }

    /** Como mucho, partículas de un solo efecto. */
    private static final int MAX_PER_FX = 48;
    /** Partículas de skills que aún se pueden sacar en este tick (se rellena cada tick según el ajuste de Partículas). */
    private static int particleBudget = 200;

    private static int particlesPerTick(Minecraft mc) {
        return switch (mc.options.particles().get()) {
            case ALL -> 200;
            case DECREASED -> 80;
            case MINIMAL -> 25;
        };
    }

    private static void mask(ClientLevel level, SkillNet.Fx m) {
        ResourceLocation rl = ResourceLocation.tryParse(m.id().contains(":") ? m.id() : "minecraft:" + m.id());
        Block block = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
        if (block == null) return;
        BlockState shown = block.defaultBlockState();
        int r = (int) Math.ceil(m.radius());
        BlockPos c = BlockPos.containing(m.x(), m.y() - 0.5, m.z());
        long until = clientTicks + m.n();
        boolean onlyAir = m.points() == 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > m.radius() * m.radius()) continue;
                for (int dy = onlyAir ? 0 : -1; dy <= (onlyAir ? 1 : 0); dy++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState now = level.getBlockState(p);
                    if (onlyAir ? !now.isAir() && !MASKS.containsKey(p) : now.isAir() || !now.isSolidRender(level, p)) continue;
                    Mask old = MASKS.get(p);
                    BlockState original = old != null ? old.original() : now;
                    MASKS.put(p, new Mask(original, shown, until));
                    level.setBlock(p, shown, 0);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------- tick y dibujo

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (Minecraft.getInstance().level != null) SkillHud.preTick();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTORS.clear();
            MASKS.clear();
            return;
        }
        if (mc.isPaused()) return;
        clientTicks++;
        particleBudget = particlesPerTick(mc);
        for (int i = 0; i < left.length; i++) if (left[i] > 0) left[i]--;
        SkillHud.postTick();
        Iterator<Actor> it = ACTORS.values().iterator();
        while (it.hasNext()) {
            Actor a = it.next();
            a.tick(mc.level);
            if (a.age > 20 * 90 && a.follow < 0) it.remove(); // por si se perdió el «quitar»
        }
        if (!MASKS.isEmpty()) {
            Iterator<Map.Entry<BlockPos, Mask>> mi = MASKS.entrySet().iterator();
            while (mi.hasNext()) {
                Map.Entry<BlockPos, Mask> e = mi.next();
                if (e.getValue().until() > clientTicks) continue;
                if (mc.level.getBlockState(e.getKey()) == e.getValue().shown()) mc.level.setBlock(e.getKey(), e.getValue().original(), 0);
                mi.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ACTORS.clear();
            MASKS.clear();
        }
    }

    /**
     * Quien lleva un modelo encima (disfraz) no se dibuja: se ve el modelo. Quien está escondido (hide) tampoco. Quien
     * va montado en un modelo (el caballo del Invocador) se dibuja subido encima.
     */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        int id = event.getEntity().getId();
        if (!HIDDEN.isEmpty()) {
            Long until = HIDDEN.get(id);
            if (until != null) {
                if (until > clientTicks) {
                    event.setCanceled(true);
                    return;
                }
                HIDDEN.remove(id);
            }
        }
        if (ACTORS.isEmpty()) return;
        for (Actor a : ACTORS.values()) {
            if (a.hideHost && a.follow == id && a.model != null) {
                event.setCanceled(true);
                return;
            }
        }
        for (Actor a : ACTORS.values()) {
            if (a.rider == id) {
                event.getPoseStack().pushPose();
                event.getPoseStack().translate(0, a.seat * a.scale, 0);
                RAISED.add(id);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (RAISED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }

    /** Clic izquierdo (al aire o a algo): lanza la skill elegida o es un golpe normal (ver {@link SkillHud#attack()}). */
    @SubscribeEvent
    public static void onInteract(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && !classId.isEmpty()) SkillHud.attack();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || ACTORS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        float partial = event.getPartialTick();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        try {
            for (Actor a : ACTORS.values()) {
                // El propio disfraz no tapa la vista en primera persona
                if (a.follow >= 0 && mc.player != null && a.follow == mc.player.getId() && mc.options.getCameraType().isFirstPerson()) continue;
                // El modelo de un mob de jefe lo dibuja su entidad (BossRenderer), con su luz
                // (solo el suyo: los vfx{} pegados al jefe se dibujan aquí)
                if (a.follow >= 0 && mc.level.getEntity(a.follow) instanceof net.tierrasfantasticas.tfclient.bosses.BossEntity
                        && follower(a.follow) == a) continue;
                Vec3 at = a.lerp(mc.level, partial);
                if (at == null || at.distanceToSqr(cam) > 128 * 128) continue;
                float yaw = a.lerpYaw(mc.level, partial);
                int light = a.boss ? net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level, BlockPos.containing(at.add(0, 0.5, 0))) : FULL_BRIGHT;
                if (a.head != null) renderHead(a, at.subtract(cam), yaw, a.prevPitch + (a.pitch - a.prevPitch) * partial, pose, buffers);
                if (a.hand != null) renderHand(a, at.subtract(cam), yaw, pose, buffers);
                if (a.item != null) renderItem(a, at.subtract(cam), yaw, a.prevPitch + (a.pitch - a.prevPitch) * partial, pose, buffers);
                a.poseOrigin = cam;
                if (a.model != null) a.renderModel(at.subtract(cam), yaw, partial, pose, buffers, light);
                if (a.text != null && !a.text.isEmpty()) renderText(a, at.subtract(cam), yaw, camera, pose, buffers);
            }
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Skills: error al dibujar los efectos", t);
            ACTORS.clear();
        } finally {
            buffers.endBatch();
        }
    }

    /**
     * El modelo de ítem que lleva en la cabeza un soporte invisible (como en el juego con MythicMobs): mismas
     * transformaciones que el soporte de armadura y la capa de la cabeza, a plena luz.
     */
    private static void renderHead(Actor a, Vec3 at, float yaw, float pitch, PoseStack pose, MultiBufferSource buffers) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(a.head);
        if (model == null || model == Minecraft.getInstance().getModelManager().getMissingModel()) return;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Axis.YP.rotationDegrees(180F - yaw));
        if (a.small) pose.scale(0.5F, 0.5F, 0.5F);
        pose.scale(-1F, -1F, 1F);
        pose.translate(0F, -1.501F, 0F);
        pose.translate(0F, 1F / 16F, 0F);
        if (pitch != 0F) pose.mulPose(new Quaternionf().rotationX((float) Math.toRadians(pitch)));
        pose.translate(0F, -0.25F, 0F);
        pose.mulPose(Axis.YP.rotationDegrees(180F));
        pose.scale(0.625F, -0.625F, -0.625F);
        Minecraft.getInstance().getItemRenderer().render(STACK, ItemDisplayContext.HEAD, false, pose, buffers, FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
    }

    /**
     * Un text_display del pack (settextdisplay): como lo dibuja Minecraft (TextDisplayRenderer): 1/40 de bloque por
     * píxel de la fuente, centrado y creciendo hacia arriba, mirando a la cámara (Billboard CENTER) o fijo, con la
     * escala y el desplazamiento del display. Las letras-imagen del pack van con la fuente de la clase.
     */
    private static void renderText(Actor a, Vec3 at, float yaw, Camera camera, PoseStack pose, MultiBufferSource buffers) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        SkillDefs.ClassDef cd = SkillDefs.get(a.cls);
        net.minecraft.network.chat.MutableComponent text = net.minecraft.network.chat.Component.empty();
        String glyphs = cd == null ? "" : cd.glyphs;
        net.minecraft.network.chat.Style style = net.minecraft.network.chat.Style.EMPTY.withFont(
                new ResourceLocation(TFClient.MOD_ID, (a.boss ? "bosses_" : "skills_") + a.cls));
        StringBuilder plain = new StringBuilder();
        a.text.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            if (!glyphs.isEmpty() && glyphs.contains(ch)) {
                if (plain.length() > 0) {
                    text.append(net.minecraft.network.chat.Component.literal(plain.toString()));
                    plain.setLength(0);
                }
                text.append(net.minecraft.network.chat.Component.literal(ch).withStyle(style));
            } else {
                plain.append(ch);
            }
        });
        if (plain.length() > 0) text.append(net.minecraft.network.chat.Component.literal(plain.toString()));
        SkillDefs.MobDef def = a.def;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        String bb = def == null || def.billboard == null ? "CENTER" : def.billboard.toUpperCase(java.util.Locale.ROOT);
        switch (bb) {
            case "FIXED" -> pose.mulPose(Axis.YP.rotationDegrees(180F - yaw));
            case "VERTICAL" -> pose.mulPose(Axis.YP.rotationDegrees(180F - camera.getYRot()));
            case "HORIZONTAL" -> {
                pose.mulPose(Axis.YP.rotationDegrees(180F - yaw));
                pose.mulPose(Axis.XP.rotationDegrees(-camera.getXRot()));
            }
            default -> pose.mulPose(camera.rotation());
        }
        if (def != null) {
            pose.translate(def.transX + a.textX, def.transY + a.textY, def.transZ + a.textZ);
            if (a.textRoll != 0F) pose.mulPose(Axis.ZP.rotationDegrees(a.textRoll));
            pose.scale(def.scaleX, def.scaleY, def.scaleZ);
        }
        pose.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f m = pose.last().pose();
        int width = font.width(text);
        int height = font.lineHeight + 1;
        font.drawInBatch(text, 1F - width / 2F, -height, 0xFFFFFFFF, false, m, buffers,
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
        pose.popPose();
    }

    /** Lo que lleva en la mano derecha un soporte con los brazos en reposo (como ItemInHandLayer del soporte). */
    private static void renderHand(Actor a, Vec3 at, float yaw, PoseStack pose, MultiBufferSource buffers) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(a.hand);
        if (model == null || model == Minecraft.getInstance().getModelManager().getMissingModel()) return;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Axis.YP.rotationDegrees(180F - yaw));
        if (a.small) pose.scale(0.5F, 0.5F, 0.5F);
        pose.scale(-1F, -1F, 1F);
        pose.translate(0F, -1.501F, 0F);
        pose.translate(-5F / 16F, 2F / 16F, 0F); // hombro derecho del soporte
        pose.mulPose(Axis.XP.rotationDegrees(-90F));
        pose.mulPose(Axis.YP.rotationDegrees(180F));
        pose.translate(1F / 16F, 0.125F, -0.625F);
        Minecraft.getInstance().getItemRenderer().render(STACK, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose, buffers,
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
    }

    /** La bala de un misil de tipo display (la esfera de Astwood): el modelo de ítem, mirando hacia donde va. */
    private static void renderItem(Actor a, Vec3 at, float yaw, float pitch, PoseStack pose, MultiBufferSource buffers) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(a.item);
        if (model == null || model == Minecraft.getInstance().getModelManager().getMissingModel()) return;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Axis.YP.rotationDegrees(180F - yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.scale(a.itemScale, a.itemScale, a.itemScale);
        Minecraft.getInstance().getItemRenderer().render(STACK, ItemDisplayContext.FIXED, false, pose, buffers, FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
    }

    /**
     * El modelo de un mob de jefe, desde su entidad (BossRenderer): donde está la entidad, con su giro y la luz de su
     * sitio. false si aún no le ha llegado el modelo.
     */
    public static boolean renderFollower(Entity e, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        Actor a = follower(e.getId());
        if (a == null || a.model == null) return false;
        float yaw = a.lerpYaw(e.level() instanceof ClientLevel cl ? cl : Minecraft.getInstance().level, partial);
        // Dónde está el modelo en el mundo (para las colas y la física de ModelEngine, que van en el mundo)
        Vec3 at = e.getPosition(partial);
        a.physWorld = new Matrix4f().translate((float) at.x, (float) at.y, (float) at.z)
                .rotate(Axis.YP.rotationDegrees(180F - yaw)).scale(a.scale / 16F);
        a.poseOrigin = at;
        a.renderModel(Vec3.ZERO, yaw, partial, pose, buffers, light);
        return true;
    }

    static {
        SkillMobs.clientGlow = e -> {
            Actor a = follower(e.getId());
            if (a == null || a.boneGlow == null) return false;
            for (int g : a.boneGlow) if (g != 0) return true;
            return false;
        };
    }

    /** El actor que lleva el modelo de esa entidad (o null). */
    private static Actor follower(int entityId) {
        // El modelo del mob (con capas de jefe) antes que un vfx{} que lleve pegado
        Actor any = null;
        for (Actor a : ACTORS.values()) {
            if (a.follow != entityId || a.model == null) continue;
            if (a.layers != null) return a;
            if (any == null) any = a;
        }
        return any;
    }

    /** Las cajas de golpe por piezas del modelo de un mob de jefe, en el mundo (para apuntarle). */
    public static List<net.minecraft.world.phys.AABB> hitboxes(Entity e) {
        Actor a = follower(e.getId());
        if (a == null || a.model == null || net.tierrasfantasticas.tfclient.vfx.VfxHitboxes.bones(a.model).length == 0) return null;
        Matrix4f[] mats = new Matrix4f[a.model.bones.length];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        a.pose(mats, 0F);
        Matrix4f w = new Matrix4f().translate((float) e.getX(), (float) e.getY(), (float) e.getZ())
                .rotate(Axis.YP.rotationDegrees(180F - (e instanceof LivingEntity le ? le.yBodyRot : e.getYRot()))).scale(a.scale / 16F);
        return net.tierrasfantasticas.tfclient.vfx.VfxHitboxes.boxes(a.model, mats, w, a.hiddenMask());
    }

    // ------------------------------------------------------------------------------------------- actor

    private static final class Actor {
        final int id;
        Vec3 pos = Vec3.ZERO;
        Vec3 prev = Vec3.ZERO;
        Vec3 target;
        float yaw;
        float prevYaw;
        float pitch;
        float prevPitch;
        Float targetYaw;
        Float targetPitch;
        ResourceLocation head;
        ResourceLocation hand;
        boolean small;
        int follow = -1;
        boolean hideHost;
        int age;
        // ModelEngine
        VfxModel model;
        VfxModel[] source;
        String[] sourceBone;
        boolean[] visible;
        Matrix4f[] bones;
        VfxModel.Anim anim;
        String animName;
        float speed = 1F;
        int animStart;
        float tr = 1F, tg = 1F, tb = 1F;
        String cls = "";
        SkillDefs.MobDef def;
        String text;
        float scale = 1F;
        boolean walking;
        int rider = -1;
        float seat = 1F;
        // ----- Jefes
        /** De un jefe: animaciones a capas, luz del sitio y brillo por pieza. */
        boolean boss;
        net.tierrasfantasticas.tfclient.vfx.VfxLayers layers;
        /** Luz de cada hueso puesta con brightness (-1 = la del sitio) y la de todo el modelo. */
        int[] boneLight;
        int wholeLight = -1;
        ResourceLocation item;
        float itemScale = 1F;
        /** displaytransformation del text_display: giro (grados) y desplazamiento. */
        float textRoll;
        float textX, textY, textZ;
        /** Colas, segmentos y física de ModelEngine (seg_, tl_, jig_) y dónde está el modelo en el mundo. */
        net.tierrasfantasticas.tfclient.vfx.VfxPhysics physics;
        Matrix4f physWorld;
        /** segment{} que llegó antes que el modelo (o para reponer al cambiarlo): parte, valores, exacto. */
        final List<Object[]> segCfg = new ArrayList<>();
        // ----- ModelEngine: cabezas (headconfig, lockmodelhead, headtarget)
        float[] headLimits;
        float headSpeed = -1F;
        boolean lockHeadYaw, lockHeadPitch;
        int headTargetId = -1;
        Vec3 headTargetPos;
        float headTargetY;
        float smoothYaw, smoothPitch, lastHeadT = Float.NaN;
        // ----- ModelEngine: por hueso (setitemmodel, setmodeltag, enchant, billboard, glowbone) y skin de los miembros
        ItemStack[] boneItem;
        String[] boneTag;
        String[] boneTagStyle;
        boolean[] boneTagOn;
        boolean[] boneGlint;
        int[] boneBillboard;
        int[] boneGlow;
        String skin;
        boolean skinSlim;
        /** model{damagetint}: parpadea en rojo al recibir daño (como ModelEngine, sí por defecto). */
        boolean damageTint = true;
        /** Cinemática inversa de ModelEngine (cadenas de Blockbench, ikchain, iktarget, ik) y lo que llegó antes. */
        net.tierrasfantasticas.tfclient.vfx.VfxIk ik;
        final List<String[]> ikCmds = new ArrayList<>();
        // ----- vfx{} de ModelEngine: solo un hueso, visible o no, y su posición/giro/escala propios
        String onlyBone;
        boolean vfxVisible = true;
        final Vector3f vfxPos = new Vector3f(), vfxRot = new Vector3f(), vfxScale = new Vector3f(1, 1, 1);
        boolean vfxWorldRot;
        // ----- leash / leashself de ModelEngine: entidad atada → hueso de correa que la sujeta; y {origen, destino}
        Map<Integer, String> leashes;
        List<String[]> selfLeashes;
        /** Qué sitio del mundo es el origen de la pila de dibujo (la cámara, o la entidad que lo dibuja). */
        Vec3 poseOrigin;

        Actor(int id) {
            this.id = id;
        }

        /** Pose de todos los huesos ahora (con sus capas si es de un jefe). */
        void pose(Matrix4f[] mats, float partial) {
            if (layers != null) {
                float headYaw = 0F, headPitch = 0F;
                ClientLevel level = Minecraft.getInstance().level;
                Entity e = follow >= 0 && level != null ? level.getEntity(follow) : null;
                if (e instanceof LivingEntity le) {
                    float body = le.yBodyRotO + (le.yBodyRot - le.yBodyRotO) * partial;
                    float head = le.yHeadRotO + (le.yHeadRot - le.yHeadRotO) * partial;
                    headYaw = net.minecraft.util.Mth.wrapDegrees(head - body);
                    headPitch = le.getViewXRot(partial);
                    // headtarget: las cabezas miran a ese objetivo (o sitio), no a donde mira la entidad
                    Vec3 tp = null;
                    if (headTargetId >= 0 && level != null) {
                        Entity te = level.getEntity(headTargetId);
                        if (te != null) tp = te.getPosition(partial).add(0, te.getBbHeight() * 0.85 + headTargetY, 0);
                    }
                    if (tp == null) tp = headTargetPos;
                    if (tp != null) {
                        Vec3 eye = le.getPosition(partial).add(0, le.getEyeHeight(), 0);
                        Vec3 d = tp.subtract(eye);
                        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                        headYaw = net.minecraft.util.Mth.wrapDegrees(yaw - body);
                        headPitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
                    }
                }
                if (lockHeadYaw) headYaw = 0F;
                if (lockHeadPitch) headPitch = 0F;
                // Velocidad de giro de la cabeza (h[speed=]_ o headconfig{speed}): grados por tick como mucho
                float sp = headSpeed >= 0F ? headSpeed : model.headSpeed();
                float now = age + partial;
                if (sp > 0F && !Float.isNaN(lastHeadT)) {
                    float dt = Math.max(0F, Math.min(5F, now - lastHeadT));
                    smoothYaw += net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(headYaw - smoothYaw), -sp * dt, sp * dt);
                    smoothPitch += net.minecraft.util.Mth.clamp(headPitch - smoothPitch, -sp * dt, sp * dt);
                    headYaw = smoothYaw;
                    headPitch = smoothPitch;
                } else {
                    smoothYaw = headYaw;
                    smoothPitch = headPitch;
                }
                lastHeadT = now;
                layers.pose(model, now, headYaw, headPitch, mats, headLimits != null ? headLimits : model.headLimits());
                return;
            }
            float seconds = (age - animStart + partial) / 20F * speed;
            VfxPose.compute(model, anim, seconds, 0F, mats);
        }

        /** Los huesos escondidos (para las cajas de golpe de las piezas). */
        boolean[] hiddenMask() {
            boolean[] h = new boolean[model.bones.length];
            for (int i = 0; i < h.length; i++) h[i] = !visible[i] && model.bones[i].hitbox == null;
            return h;
        }

        /** brightness{b;s;p;em}: la luz de una pieza (o de las que la llevan en el nombre) o de todo el modelo. */
        void bright(String part, String value, boolean exact) {
            if (model == null) return;
            String[] v = value.split("\\|");
            int b = (int) parse(v[0], 15F), sk = v.length > 1 ? (int) parse(v[1], b) : b;
            int light = b < 0 ? -1 : LightTexture.pack(Math.min(15, b), Math.min(15, Math.max(0, sk)));
            if (part == null || part.isEmpty()) {
                wholeLight = light;
                return;
            }
            if (boneLight == null) {
                boneLight = new int[model.bones.length];
                java.util.Arrays.fill(boneLight, -1);
            }
            String want = part.toLowerCase(java.util.Locale.ROOT);
            for (int i = 0; i < model.bones.length; i++) {
                String id = model.bones[i].id.toLowerCase(java.util.Locale.ROOT);
                boolean hit = exact ? id.equals(want) : id.contains(want);
                if (!hit) continue;
                boneLight[i] = light;
                // Lo de debajo de esa pieza, también (ModelEngine pinta así las estelas y runas con sus hijos)
                for (int j = 0; j < model.bones.length; j++) {
                    for (int p = model.bones[j].parent; p >= 0; p = model.bones[p].parent) if (p == i) {
                        boneLight[j] = light;
                        break;
                    }
                }
            }
        }

        /** remapmodel: cada hueso se ve con el del mismo nombre de otro modelo (o vuelve al suyo). */
        void remap(String other) {
            if (model == null) return;
            VfxModel m = model(other);
            if (m == null) return;
            for (int i = 0; i < model.bones.length; i++) {
                String id = model.bones[i].id;
                if (m == model || m.bone(id) < 0) {
                    source[i] = model;
                    sourceBone[i] = id;
                } else {
                    source[i] = m;
                    sourceBone[i] = id;
                }
            }
        }

        /** displaytransformation de un text_display: giro (right_rotation) o desplazamiento (translation). */
        void displayTransform(String what, String value) {
            String[] w = what.split("\\|");
            boolean add = w[0].equals("add");
            String tr = w.length > 1 ? w[1] : "translation";
            String[] v = value.split(",");
            if (tr.contains("rotation")) {
                float deg = parse(v[0].trim(), 0F);
                textRoll = add ? textRoll + deg : deg;
            } else if (tr.contains("translation") && v.length >= 3) {
                float x = parse(v[0].trim(), 0F), y = parse(v[1].trim(), 0F), z = parse(v[2].trim(), 0F);
                textX = add ? textX + x : x;
                textY = add ? textY + y : y;
                textZ = add ? textZ + z : z;
            }
        }

        /** Un esbirro que echa a andar o se para: «walk» o la de reposo, si no está con otra animación. */
        void walk(boolean now) {
            walking = now;
            if (model == null) return;
            boolean free = animName == null || animName.equalsIgnoreCase("idle") || animName.equalsIgnoreCase("walk");
            if (!free) return;
            if (now && model.anims.containsKey("walk")) play("walk", 1F);
            else playIdle();
        }

        void setModel(String id) {
            model = id == null ? null : model(id);
            boneLight = null;
            physics = null;
            segCfg.clear();
            ik = null;
            ikCmds.clear();
            headLimits = null;
            headSpeed = -1F;
            lockHeadYaw = lockHeadPitch = false;
            headTargetId = -1;
            headTargetPos = null;
            lastHeadT = Float.NaN;
            boneItem = null;
            boneTag = null;
            boneTagStyle = null;
            boneTagOn = null;
            boneGlint = null;
            boneBillboard = null;
            boneGlow = null;
            leashes = null;
            selfLeashes = null;
            if (layers != null) layers.clear();
            if (model == null) return;
            int n = model.bones.length;
            source = new VfxModel[n];
            sourceBone = new String[n];
            visible = new boolean[n];
            bones = new Matrix4f[n];
            for (int i = 0; i < n; i++) {
                source[i] = model;
                sourceBone[i] = model.bones[i].id;
                visible[i] = !model.bones[i].hidden;
                bones[i] = new Matrix4f();
            }
            // Como ModelEngine: si tiene animación de aparecer, primero esa; luego la de reposo
            if (layers != null) {
                if (model.anims.containsKey("spawn")) layers.play(model, "spawn", age, 1F, 0, 0, 0, true);
                return;
            }
            if (model.anims.containsKey("spawn")) play("spawn", 1F);
            else playIdle();
        }

        void play(String name, float sp) {
            if (model == null) return;
            if (layers != null) {
                layers.play(model, name, age, sp, 0, 0, 1, true);
                return;
            }
            VfxModel.Anim a = model.anims.get(name);
            if (a == null) {
                for (Map.Entry<String, VfxModel.Anim> e : model.anims.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(name)) {
                        a = e.getValue();
                        name = e.getKey();
                    }
                }
            }
            if (a == null) return;
            anim = a;
            animName = name;
            speed = sp <= 0 ? 1F : sp;
            animStart = age;
        }

        void playIdle() {
            anim = null;
            animName = null;
            if (model == null) return;
            if (model.anims.containsKey("idle")) play("idle", 1F);
        }

        void setVisible(int b, boolean show, boolean children) {
            visible[b] = show;
            if (!children) return;
            for (int i = 0; i < model.bones.length; i++) {
                for (int p = model.bones[i].parent; p >= 0; p = model.bones[p].parent) {
                    if (p == b) {
                        visible[i] = show;
                        break;
                    }
                }
            }
        }

        void tick(ClientLevel level) {
            age++;
            prev = pos;
            prevYaw = yaw;
            prevPitch = pitch;
            if (target != null) {
                pos = target;
                target = null;
            }
            if (targetYaw != null) {
                yaw = targetYaw;
                targetYaw = null;
            }
            if (targetPitch != null) {
                pitch = targetPitch;
                targetPitch = null;
            }
            // Una animación de una vez que ya acabó vuelve a la de reposo (o a andar, si va andando)
            if (anim != null && anim.loop == 0 && (age - animStart) / 20F * speed > anim.length + 0.05F) {
                playIdle();
                if (walking) walk(true);
            }
            if (layers != null && model != null) {
                // Un mob de jefe anda si su entidad se mueve (la animación de andar o la de reposo, por defecto)
                Entity e = follow >= 0 ? level.getEntity(follow) : null;
                if (e != null) {
                    double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
                    layers.setWalking(dx * dx + dz * dz > 0.0009 && !(e instanceof LivingEntity le && le.isDeadOrDying()));
                } else {
                    layers.setWalking(walking);
                }
                layers.update(model, age);
                layers.prune(age);
            }
        }

        Vec3 lerp(ClientLevel level, float partial) {
            if (follow >= 0) {
                Entity e = level.getEntity(follow);
                if (e == null) return null;
                return e.getPosition(partial);
            }
            return prev.lerp(pos, partial);
        }

        float lerpYaw(ClientLevel level, float partial) {
            if (follow >= 0) {
                Entity e = level.getEntity(follow);
                if (e instanceof LivingEntity le) return le.yBodyRotO + (le.yBodyRot - le.yBodyRotO) * partial;
                if (e != null) return e.getViewYRot(partial);
            }
            float d = yaw - prevYaw;
            while (d > 180F) d -= 360F;
            while (d < -180F) d += 360F;
            return prevYaw + d * partial;
        }

        void renderModel(Vec3 at, float yaw, float partial, PoseStack pose, MultiBufferSource buffers) {
            renderModel(at, yaw, partial, pose, buffers, FULL_BRIGHT);
        }

        /** light: la luz del sitio (los de las clases, a plena luz; los de los jefes, con la del mundo). */
        void renderModel(Vec3 at, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
            if (!vfxVisible) return;
            pose(bones, partial);
            if (layers != null && physWorld != null) {
                if (ik == null) {
                    ik = new net.tierrasfantasticas.tfclient.vfx.VfxIk(model);
                    for (String[] c : ikCmds) ikCommand(c);
                }
                if (ik.active()) {
                    ClientLevel lv = Minecraft.getInstance().level;
                    float now = age + partial;
                    ik.apply(bones, physWorld, now, b -> layers.moves(model, b, now), id2 -> {
                        Entity te = lv == null ? null : lv.getEntity(id2);
                        if (te == null) return null;
                        Vec3 p = te.getPosition(partial);
                        return new Vector3f((float) p.x, (float) (p.y + te.getBbHeight() * 0.5), (float) p.z);
                    });
                }
                if (physics == null) {
                    physics = new net.tierrasfantasticas.tfclient.vfx.VfxPhysics(model);
                    for (Object[] c : segCfg) {
                        String part = (String) c[0];
                        if (part != null && part.startsWith("\u0000jig:")) physics.configureJig(part.substring(5), castCfg(c[1]), (Boolean) c[2]);
                        else physics.configure(part, castCfg(c[1]), (Boolean) c[2]);
                    }
                }
                if (physics.active()) physics.apply(bones, physWorld, age + partial);
            }
            Minecraft mc = Minecraft.getInstance();
            int baseLight = wholeLight >= 0 ? wholeLight : light;
            Matrix4f inst = new Matrix4f().translate((float) at.x, (float) at.y, (float) at.z);
            // vfxpos (bloques) y vfxrot (grados; con neworigin, en los ejes del mundo) de ModelEngine
            inst.translate(vfxPos);
            if (vfxWorldRot && vfxRot.lengthSquared() > 0) inst.rotateZYX((float) Math.toRadians(vfxRot.z), (float) Math.toRadians(vfxRot.y), (float) Math.toRadians(vfxRot.x));
            inst.rotate(Axis.YP.rotationDegrees(180F - yaw));
            if (!vfxWorldRot && vfxRot.lengthSquared() > 0) inst.rotateZYX((float) Math.toRadians(vfxRot.z), (float) Math.toRadians(vfxRot.y), (float) Math.toRadians(vfxRot.x));
            inst.scale(scale / 16F).scale(vfxScale);
            Matrix4f base = new Matrix4f(pose.last().pose()).mul(inst);
            Matrix3f normalView = new Matrix3f(pose.last().normal());
            Matrix3f normalBase = new Matrix3f(normalView).mul(new Matrix3f(inst));
            // glowbone: con el contorno de Minecraft solo esos huesos (los demás, al dibujo normal)
            net.minecraft.client.renderer.OutlineBufferSource outline = buffers instanceof net.minecraft.client.renderer.OutlineBufferSource ob
                    && boneGlow != null ? ob : null;
            MultiBufferSource plain = outline != null ? mc.renderBuffers().bufferSource() : buffers;
            // damagetint: rojo mientras dura el golpe (el mismo «overlay» que los mobs de Minecraft)
            int overlay = OverlayTexture.NO_OVERLAY;
            if (layers != null && damageTint && follow >= 0 && mc.level != null
                    && mc.level.getEntity(follow) instanceof LivingEntity hurt && hurt.hurtTime > 0) {
                overlay = OverlayTexture.pack(0, OverlayTexture.v(true));
            }
            Map<String, List<int[]>> byTex = new HashMap<>();
            Map<String, ResourceLocation> texOf = new HashMap<>();
            Map<String, Integer> glowOf = new HashMap<>();
            ResourceLocation skinTex = null;
            for (int bi = 0; bi < model.bones.length; bi++) {
                if (!visible[bi]) continue;
                VfxModel.Bone bone = boneOf(bi);
                if (bone == null || bone.quadCount == 0) continue;
                VfxModel src = source[bi];
                int glow = outline != null ? boneGlow[bi] : 0;
                for (int q = 0; q < bone.quadCount; q++) {
                    int ti = (int) bone.quads[q * VfxModel.QUAD];
                    ResourceLocation rl;
                    if (ti >= 0 && ti < src.tex.length) {
                        rl = texture(src, ti);
                    } else if (layers != null && (ti == -1 || ti == -2 || ti == -4 || ti == -5)) {
                        // Miembros de jugador de ModelEngine (phead_, prarm_..., limb[]): con la skin de modelplayerskin
                        if (skinTex == null) skinTex = skinTexture();
                        rl = skinTex;
                    } else {
                        continue;
                    }
                    String key = rl + (glow != 0 ? "#" + glow : "");
                    texOf.putIfAbsent(key, rl);
                    glowOf.putIfAbsent(key, glow);
                    byTex.computeIfAbsent(key, k -> new ArrayList<>()).add(new int[]{bi, q, ti});
                }
            }
            Matrix4f full = new Matrix4f();
            Matrix3f normal = new Matrix3f();
            Vector3f n = new Vector3f();
            Vector4f v = new Vector4f();
            for (Map.Entry<String, List<int[]>> e : byTex.entrySet()) {
                int glow = glowOf.get(e.getKey());
                if (glow != 0) outline.setColor((glow >> 16) & 255, (glow >> 8) & 255, glow & 255, 255);
                VertexConsumer vc = (glow != 0 ? outline : plain).getBuffer(RenderType.entityTranslucentCull(texOf.get(e.getKey())));
                int lastBone = -1;
                for (int[] item : e.getValue()) {
                    int bi = item[0], q = item[1], ti = item[2];
                    VfxModel src = source[bi];
                    VfxModel.Bone bone = boneOf(bi);
                    if (bi != lastBone) {
                        boneMatrix(bi, base, pose, full);
                        normal.set(normalBase).mul(new Matrix3f(bones[bi]));
                        lastBone = bi;
                    }
                    int o = q * VfxModel.QUAD;
                    float frames = 1, frame = 0;
                    boolean emissive = false;
                    if (ti >= 0) {
                        VfxModel.Tex t = src.tex[ti];
                        emissive = t.emissive();
                        if (t.frames() > 1) {
                            frames = t.frames();
                            frame = (age / t.frameTime()) % t.frames();
                        }
                    }
                    if (bone.shade) n.set(bone.quads[o + 1], bone.quads[o + 2], bone.quads[o + 3]).mul(normal).normalize();
                    else n.set(0, 1, 0).mul(normalView);
                    if (!Float.isFinite(n.x)) n.set(0, 1, 0);
                    // Centro de la cara en la textura: cada esquina se mete un poco hacia él (como hace Minecraft al
                    // hornear los modelos), para que en los bordes no se cuele el píxel de al lado ni, en las texturas
                    // animadas, una raya del fotograma siguiente
                    float[] c = VfxModel.uvCenter(bone.quads, o);
                    // Luz: las texturas emisivas (_e) y las caras que brillan, a plena luz; si no, la del hueso o la del sitio
                    int lit = emissive || ((int) bone.quads[o + 24] & 1) != 0 ? FULL_BRIGHT
                            : boneLight != null && boneLight[bi] >= 0 ? boneLight[bi] : baseLight;
                    for (int k = 0; k < 4; k++) {
                        int p = o + 4 + k * 5;
                        v.set(bone.quads[p], bone.quads[p + 1], bone.quads[p + 2], 1F);
                        full.transform(v);
                        float tu = VfxModel.insetUv(bone.quads[p + 3], c[0]);
                        float tv = (VfxModel.insetUv(bone.quads[p + 4], c[1]) + frame) / frames;
                        vc.vertex(v.x, v.y, v.z).color(tr, tg, tb, 1F).uv(tu, tv)
                                .overlayCoords(overlay).uv2(lit).normal(n.x, n.y, n.z).endVertex();
                    }
                }
            }
            if (boneGlint != null) drawGlint(base, pose, plain);
            if (boneItem != null) drawItems(base, pose, plain, baseLight);
            if (boneTag != null) drawTags(base, pose, plain, baseLight);
            if (leashes != null || selfLeashes != null) drawLeashes(inst, pose, plain, partial);
        }

        /** Dónde está ahora un hueso, en el espacio de la pila de dibujo (inst: del modelo a ese espacio). */
        private Vec3 bonePoint(Matrix4f inst, int b) {
            Vector4f p = new Matrix4f(inst).mul(bones[b]).transform(new Vector4f(0, 0, 0, 1));
            return new Vec3(p.x, p.y, p.z);
        }

        /** El hueso de correa (l_ sin cubos) con ese nombre o, sin nombre, el primero que haya; -1 si no es de correa. */
        private int leashBone(String part) {
            if (part != null && !part.isEmpty()) {
                int b = model.bone(part);
                return b >= 0 && "leash".equals(model.bones[b].beh) ? b : -1;
            }
            for (int i = 0; i < model.bones.length; i++) if ("leash".equals(model.bones[i].beh)) return i;
            return -1;
        }

        /**
         * leash y leashself: la correa de Minecraft (MobRenderer.renderLeash) desde el hueso de correa hasta donde Minecraft
         * ata a un mob (su «leash offset», girado con su cuerpo) o hasta el otro hueso.
         */
        private void drawLeashes(Matrix4f inst, PoseStack pose, MultiBufferSource buffers, float partial) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || poseOrigin == null) return;
            if (leashes != null) {
                for (Map.Entry<Integer, String> e : leashes.entrySet()) {
                    Entity t = level.getEntity(e.getKey());
                    int b = leashBone(e.getValue());
                    if (t == null || b < 0 || !visible[b]) continue;
                    float body = t instanceof LivingEntity le ? net.minecraft.util.Mth.lerp(partial, le.yBodyRotO, le.yBodyRot) : t.getViewYRot(partial);
                    double ang = Math.toRadians(body) + Math.PI / 2;
                    Vec3 off = t.getLeashOffset(partial);
                    double ox = Math.cos(ang) * off.z + Math.sin(ang) * off.x, oz = Math.sin(ang) * off.z - Math.cos(ang) * off.x;
                    Vec3 tied = t.getPosition(partial).add(ox, off.y, oz).subtract(poseOrigin);
                    rope(pose, buffers, tied, bonePoint(inst, b), level, poseOrigin);
                }
            }
            if (selfLeashes != null) {
                for (String[] p : selfLeashes) {
                    int s = leashBone(p[0]), d = leashBone(p[1]);
                    if (s < 0 || d < 0 || s == d || !visible[s] || !visible[d]) continue;
                    rope(pose, buffers, bonePoint(inst, d), bonePoint(inst, s), level, poseOrigin);
                }
            }
        }

        /**
         * Una correa como la de Minecraft (MobRenderer.renderLeash y addVertexPair): del extremo atado «tied» a quien la
         * sujeta «holder» (los dos en el espacio de la pila), 24 tramos que cuelgan, dos tiras cruzadas de color cuerda y la
         * luz de cada punta repartida a lo largo.
         */
        private static void rope(PoseStack pose, MultiBufferSource buffers, Vec3 tied, Vec3 holder, ClientLevel level, Vec3 origin) {
            float dx = (float) (holder.x - tied.x), dy = (float) (holder.y - tied.y), dz = (float) (holder.z - tied.z);
            float flat = dx * dx + dz * dz;
            float k = (flat < 1e-8F ? 1F / 1e-4F : net.minecraft.util.Mth.invSqrt(flat)) * 0.025F / 2F;
            float sx = dz * k, sz = dx * k;
            if (flat < 1e-8F) sx = 0.0125F;
            BlockPos pa = BlockPos.containing(tied.add(origin)), pb = BlockPos.containing(holder.add(origin));
            int ba = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pa);
            int bb = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pb);
            int sa = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pa);
            int sb = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pb);
            VertexConsumer vc = buffers.getBuffer(RenderType.leash());
            pose.pushPose();
            pose.translate(tied.x, tied.y, tied.z);
            Matrix4f m = pose.last().pose();
            for (int i = 0; i <= 24; i++) ropePair(vc, m, dx, dy, dz, ba, bb, sa, sb, 0.025F, 0.025F, sx, sz, i, false);
            for (int i = 24; i >= 0; i--) ropePair(vc, m, dx, dy, dz, ba, bb, sa, sb, 0.025F, 0F, sx, sz, i, true);
            pose.popPose();
        }

        private static void ropePair(VertexConsumer vc, Matrix4f m, float dx, float dy, float dz, int blockA, int blockB, int skyA,
                                     int skyB, float w, float lift, float sx, float sz, int i, boolean back) {
            float f = i / 24F;
            int light = LightTexture.pack((int) net.minecraft.util.Mth.lerp(f, blockA, blockB), (int) net.minecraft.util.Mth.lerp(f, skyA, skyB));
            float shade = i % 2 == (back ? 1 : 0) ? 0.7F : 1F;
            float r = 0.5F * shade, g = 0.4F * shade, b = 0.3F * shade;
            float x = dx * f;
            float y = dy > 0F ? dy * f * f : dy - dy * (1F - f) * (1F - f);
            float z = dz * f;
            vc.vertex(m, x - sx, y + lift, z + sz).color(r, g, b, 1F).uv2(light).endVertex();
            vc.vertex(m, x + sx, y + w - lift, z - sz).color(r, g, b, 1F).uv2(light).endVertex();
        }

        /** La matriz con la que se dibuja un hueso (con billboard: mirando a la cámara). */
        void boneMatrix(int bi, Matrix4f base, PoseStack pose, Matrix4f out) {
            out.set(base).mul(bones[bi]);
            int mode = boneBillboard == null ? 0 : boneBillboard[bi];
            if (mode == 0) return;
            Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
            Vector3f t = out.getTranslation(new Vector3f());
            Vector3f sc = out.getScale(new Vector3f());
            Quaternionf view = pose.last().pose().getUnnormalizedRotation(new Quaternionf()).normalize();
            Quaternionf q = switch (mode) {
                case 1 -> new Quaternionf().rotationY((float) Math.toRadians(-cam.getYRot()));
                case 2 -> new Quaternionf().rotationX((float) Math.toRadians(cam.getXRot()));
                default -> new Quaternionf(cam.rotation());
            };
            out.identity().translate(t).rotate(view.mul(q)).scale(sc);
        }

        /** enchant: el brillo de encantado encima de esos huesos. */
        private void drawGlint(Matrix4f base, PoseStack pose, MultiBufferSource buffers) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityGlintDirect());
            Matrix4f full = new Matrix4f();
            Vector4f v = new Vector4f();
            for (int bi = 0; bi < model.bones.length; bi++) {
                if (!boneGlint[bi] || !visible[bi]) continue;
                VfxModel.Bone bone = boneOf(bi);
                if (bone == null) continue;
                boneMatrix(bi, base, pose, full);
                for (int q = 0; q < bone.quadCount; q++) {
                    int o = q * VfxModel.QUAD;
                    for (int k = 0; k < 4; k++) {
                        int p = o + 4 + k * 5;
                        v.set(bone.quads[p], bone.quads[p + 1], bone.quads[p + 2], 1F);
                        full.transform(v);
                        vc.vertex(v.x, v.y, v.z).uv(bone.quads[p + 3], bone.quads[p + 4]).endVertex();
                    }
                }
            }
        }

        /** setitemmodel: el objeto en su hueso (ih_ en la cabeza, ir_ mano derecha, il_ izquierda), a tamaño de bloque. */
        private void drawItems(Matrix4f base, PoseStack pose, MultiBufferSource buffers, int light) {
            Minecraft mc = Minecraft.getInstance();
            Matrix4f full = new Matrix4f();
            for (int bi = 0; bi < model.bones.length; bi++) {
                ItemStack st = boneItem[bi];
                if (st == null || st.isEmpty() || !visible[bi]) continue;
                String beh = model.bones[bi].beh;
                ItemDisplayContext dc = "item_head".equals(beh) ? ItemDisplayContext.HEAD
                        : "item_right".equals(beh) ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                        : "item_left".equals(beh) ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.FIXED;
                boneMatrix(bi, base, pose, full);
                pose.pushPose();
                pose.last().pose().set(full).scale(16F);
                pose.last().normal().set(new Matrix3f(full).normal());
                mc.getItemRenderer().renderStatic(st, dc, light, OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, id);
                pose.popPose();
            }
        }

        /** setmodeltag: el texto de los huesos de nombre (tag_), mirando a la cámara. */
        private void drawTags(Matrix4f base, PoseStack pose, MultiBufferSource buffers, int light) {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.client.gui.Font font = mc.font;
            Camera cam = mc.gameRenderer.getMainCamera();
            Matrix4f full = new Matrix4f();
            for (int bi = 0; bi < model.bones.length; bi++) {
                if (boneTag[bi] == null || !boneTagOn[bi] || boneTag[bi].isEmpty()) continue;
                String[] st = boneTagStyle[bi].split("\\|");
                float sc = parse(st[0], 1F);
                boolean shadow = st.length > 1 && "1".equals(st[1]);
                boolean through = st.length > 2 && "1".equals(st[2]);
                int bg = st.length > 3 ? (int) parse(st[3], -1F) : -1;
                int op = st.length > 4 ? (int) parse(st[4], -1F) : -1;
                full.set(base).mul(bones[bi]);
                Vector3f t = full.getTranslation(new Vector3f());
                Quaternionf view = pose.last().pose().getUnnormalizedRotation(new Quaternionf()).normalize();
                Matrix4f m = new Matrix4f().translate(t).rotate(view.mul(cam.rotation())).scale(-0.025F * sc, -0.025F * sc, 0.025F * sc);
                net.minecraft.network.chat.Component text = net.minecraft.network.chat.Component.literal(boneTag[bi]);
                float x = -font.width(text) / 2F;
                int color = op < 0 ? 0xFFFFFFFF : (Math.max(0, Math.min(255, op)) << 24) | 0xFFFFFF;
                int back = bg == -1 ? (int) (mc.options.getBackgroundOpacity(0.25F) * 255F) << 24 : bg;
                font.drawInBatch(text, x, 0, color, shadow, m, buffers,
                        through ? net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH : net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                        back, light);
            }
        }

        /** La skin de modelplayerskin (uuid o nombre de un jugador conectado) o la de por defecto. */
        private ResourceLocation skinTexture() {
            Minecraft mc = Minecraft.getInstance();
            var conn = mc.getConnection();
            if (skin != null && conn != null) {
                net.minecraft.client.multiplayer.PlayerInfo info = null;
                try {
                    String raw = skin.replace("-", "");
                    if (raw.length() == 32) {
                        java.util.UUID u = new java.util.UUID(Long.parseUnsignedLong(raw.substring(0, 16), 16),
                                Long.parseUnsignedLong(raw.substring(16), 16));
                        info = conn.getPlayerInfo(u);
                    }
                } catch (NumberFormatException ignored) {
                    // no era un uuid: un nombre
                }
                if (info == null) info = conn.getPlayerInfo(skin);
                if (info != null) return info.getSkinLocation();
            }
            return net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin();
        }

        /** vfx{p}: solo se ve ese hueso (y lo que cuelga de él); vacío = todo. */
        void only(String part) {
            onlyBone = part == null || part.isEmpty() ? null : part;
            if (model == null) return;
            int b = onlyBone == null ? -1 : model.bone(onlyBone);
            for (int i = 0; i < model.bones.length; i++) {
                boolean in = b < 0;
                for (int p = i; p >= 0 && !in; p = model.bones[p].parent) if (p == b) in = true;
                visible[i] = in && !model.bones[i].hidden;
            }
        }

        /** Un mensaje de IK (ik, iktgt, ikchain): se guarda por si cambia el modelo y se aplica si ya hay IK. */
        void ikMessage(String key, String a1, String b1, String c1) {
            String[] cmd = {key, a1 == null ? "" : a1, b1 == null ? "" : b1, c1 == null ? "" : c1};
            ikCmds.add(cmd);
            if (ik != null) ikCommand(cmd);
        }

        private void ikCommand(String[] c) {
            switch (c[0]) {
                case "ik" -> {
                    Map<String, Float> v = new HashMap<>();
                    for (String kv : c[2].split(";")) {
                        int eq = kv.indexOf('=');
                        if (eq > 0) v.put(kv.substring(0, eq), parse(kv.substring(eq + 1), 0F));
                    }
                    ik.configure(c[1], v, !"0".equals(c[3]));
                    if (physics != null) physics.configureJig(c[1], v, !"0".equals(c[3]));
                    else segCfg.add(new Object[] {"\u0000jig:" + c[1], v, !"0".equals(c[3])});
                }
                case "iktgt" -> {
                    boolean clear = c[3].startsWith("1");
                    boolean exact = c[3].length() < 2 || c[3].charAt(1) == '1';
                    if (clear) {
                        ik.target(c[1], exact, -1, null, 0F, true);
                    } else {
                        String[] p = c[2].split("\\|");
                        ik.target(c[1], exact, (int) parse(p[0], -1F), new Vector3f(parse(p[1], 0F), parse(p[2], 0F), parse(p[3], 0F)),
                                parse(p[4], 0F), false);
                    }
                }
                case "ikchain" -> {
                    String[] p = c[2].split("\\|", -1);
                    if ("1".equals(c[3])) ik.removeChain(p[0]);
                    else ik.addChain(c[1], p[0], p.length > 1 ? p[1] : "");
                }
                default -> { }
            }
        }

        /** Los huesos con ese nombre (todos si vacío; con exact=false, los que lo llevan) y, con child, sus hijos. */
        int[] match(String part, boolean exact, boolean child) {
            if (model == null) return new int[0];
            int n = model.bones.length;
            boolean[] hit = new boolean[n];
            String want = part == null ? "" : part.toLowerCase(java.util.Locale.ROOT);
            if (want.isEmpty()) {
                java.util.Arrays.fill(hit, true);
            } else if (exact) {
                int b = model.bone(part);
                if (b >= 0) hit[b] = true;
            } else {
                for (int i = 0; i < n; i++) hit[i] = model.bones[i].id.toLowerCase(java.util.Locale.ROOT).contains(want);
            }
            if (child && !want.isEmpty()) {
                for (int i = 0; i < n; i++) if (!hit[i] && model.bones[i].parent >= 0 && hit[model.bones[i].parent]) hit[i] = true;
            }
            int c = 0;
            for (boolean h : hit) if (h) c++;
            int[] out = new int[c];
            c = 0;
            for (int i = 0; i < n; i++) if (hit[i]) out[c++] = i;
            return out;
        }

        /** headconfig: «minYaw|maxYaw|minPitch|maxPitch|speed» («» = no tocar) para esas cabezas (todas si vacío). */
        void headConfig(String part, String cfg, boolean exact) {
            if (model == null) return;
            if (headLimits == null) headLimits = model.headLimits().clone();
            String[] p = cfg.split("\\|", -1);
            for (int b : match(part, exact, false)) {
                if (!model.bones[b].head) continue;
                for (int k = 0; k < 4; k++) if (k < p.length && !p[k].isEmpty()) headLimits[b * 4 + k] = parse(p[k], Float.NaN);
            }
            if (p.length > 4 && !p[4].isEmpty()) headSpeed = parse(p[4], 0F);
        }

        /** setitemmodel: «id|data|color|enchant» en ese hueso de objeto (vacío = quitarlo). */
        void boneItem(String part, String v, boolean exact) {
            if (model == null) return;
            if (boneItem == null) boneItem = new ItemStack[model.bones.length];
            String[] p = v.split("\\|", -1);
            ItemStack st = ItemStack.EMPTY;
            ResourceLocation rl = p[0].isEmpty() ? null : ResourceLocation.tryParse(p[0]);
            if (rl != null && ForgeRegistries.ITEMS.containsKey(rl)) {
                st = new ItemStack(ForgeRegistries.ITEMS.getValue(rl));
                int data = p.length > 1 ? (int) parse(p[1], 0F) : 0;
                if (data != 0) st.getOrCreateTag().putInt("CustomModelData", data);
                if (p.length > 2 && !p[2].isEmpty()) {
                    try {
                        st.getOrCreateTagElement("display").putInt("color", Integer.parseInt(p[2].replace("#", ""), 16));
                    } catch (NumberFormatException ignored) {
                        // color mal escrito: sin color
                    }
                }
                if (p.length > 3 && "1".equals(p[3])) st.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
            }
            for (int b : match(part, exact, false)) boneItem[b] = st;
        }

        /** setmodeltag: «escala|sombra|a través|fondo|opacidad\u0001texto» en ese hueso de nombre. */
        void boneTag(String part, String v, boolean visible) {
            if (model == null) return;
            int n = model.bones.length;
            if (boneTag == null) {
                boneTag = new String[n];
                boneTagStyle = new String[n];
                boneTagOn = new boolean[n];
            }
            int cut = v.indexOf('\u0001');
            String style = cut < 0 ? "1|0|1|-1|-1" : v.substring(0, cut);
            String text = cut < 0 ? v : v.substring(cut + 1);
            for (int b : match(part, true, false)) {
                boneTag[b] = text.replace('&', '\u00a7');
                boneTagStyle[b] = style;
                boneTagOn[b] = visible;
            }
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Float> castCfg(Object o) {
            return (Map<String, Float>) o;
        }

        /** segment{}: «part» vacío = todo el modelo; «cfg» = clave=valor;... (ver SkillModels.segment). */
        void segment(String part, String cfg, boolean exact) {
            Map<String, Float> v = new HashMap<>();
            for (String kv : cfg.split(";")) {
                int eq = kv.indexOf('=');
                if (eq > 0) v.put(kv.substring(0, eq), parse(kv.substring(eq + 1), 0F));
            }
            segCfg.add(new Object[] {part.isEmpty() ? null : part, v, exact});
            if (physics != null) physics.configure(part.isEmpty() ? null : part, v, exact);
        }

        /** El hueso que se dibuja en el sitio «bi» (el suyo o el de otro modelo, si se cambió con changepart). */
        VfxModel.Bone boneOf(int bi) {
            VfxModel src = source[bi];
            if (src == model && sourceBone[bi].equals(model.bones[bi].id)) return model.bones[bi];
            int b = src.bone(sourceBone[bi]);
            return b < 0 ? null : src.bones[b];
        }
    }

    // ------------------------------------------------------------------------------------------- datos

    static VfxModel model(String id) {
        if (id == null || id.isEmpty()) return null;
        return MODELS.computeIfAbsent(id, key -> {
            ResourceLocation rl = new ResourceLocation(TFClient.MOD_ID, SkillDefs.modelDir(key) + key + ".json");
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(rl);
                if (res.isEmpty()) {
                    TFClient.LOGGER.warn("TF Skills: no existe el modelo {}", rl);
                    return Optional.empty();
                }
                try (var in = res.get().open()) {
                    return Optional.of(VfxModel.read(in));
                }
            } catch (Exception e) {
                TFClient.LOGGER.error("TF Skills: no se pudo leer el modelo {}", rl, e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static ResourceLocation texture(VfxModel m, int ti) {
        return TEXTURES.computeIfAbsent(m.tex[ti].path(), ResourceLocation::new);
    }

    /** Los nombres de partícula de Bukkit/MythicMobs → los de Minecraft 1.20.1. */
    private static final Map<String, String> PARTICLE_NAMES = Map.ofEntries(
            Map.entry("reddust", "dust"), Map.entry("redstone", "dust"), Map.entry("smoke_normal", "smoke"),
            Map.entry("smoke_large", "large_smoke"), Map.entry("explosion_large", "explosion"),
            Map.entry("explosion_huge", "explosion_emitter"), Map.entry("explosion_normal", "poof"),
            Map.entry("spell_witch", "witch"), Map.entry("spell_instant", "instant_effect"), Map.entry("spell", "effect"),
            Map.entry("spell_mob", "entity_effect"), Map.entry("spell_mob_ambient", "ambient_entity_effect"),
            Map.entry("enchantment_table", "enchant"), Map.entry("water_drop", "rain"), Map.entry("totem", "totem_of_undying"),
            Map.entry("fireworks_spark", "firework"), Map.entry("villager_happy", "happy_villager"),
            Map.entry("villager_angry", "angry_villager"), Map.entry("drip_water", "dripping_water"),
            Map.entry("drip_lava", "dripping_lava"), Map.entry("water_splash", "splash"), Map.entry("water_wake", "fishing"),
            Map.entry("suspended", "underwater"), Map.entry("suspended_depth", "underwater"), Map.entry("crit_magic", "enchanted_hit"),
            Map.entry("magic_crit", "enchanted_hit"), Map.entry("mob_appearance", "elder_guardian"), Map.entry("slime", "item_slime"),
            Map.entry("snowball", "item_snowball"), Map.entry("snow_shovel", "item_snowball"), Map.entry("block_crack", "block"),
            Map.entry("blockcrack", "block"), Map.entry("block_dust", "block"), Map.entry("item_crack", "item"),
            Map.entry("iconcrack", "item"), Map.entry("town_aura", "mycelium"), Map.entry("footstep", "poof"),
            Map.entry("damage_indicator", "damage_indicator"), Map.entry("sweep", "sweep_attack"), Map.entry("bubble_column_up", "bubble_column_up"));

    private static final Map<String, Optional<ParticleOptions>> PARTICLES = new HashMap<>();

    static ParticleOptions particle(String rawName, String extra, float size) {
        String key = rawName + "|" + extra + "|" + size;
        return PARTICLES.computeIfAbsent(key, k -> parseParticle(rawName, extra, size)).orElse(null);
    }

    private static Optional<ParticleOptions> parseParticle(String rawName, String extra, float size) {
        try {
            String name = rawName.toLowerCase(java.util.Locale.ROOT).replace("minecraft:", "");
            name = PARTICLE_NAMES.getOrDefault(name, name);
            ResourceLocation rl = ResourceLocation.tryParse("minecraft:" + name);
            ParticleType<?> type = rl == null ? null : BuiltInRegistries.PARTICLE_TYPE.get(rl);
            if (type == null || !BuiltInRegistries.PARTICLE_TYPE.containsKey(rl)) {
                TFClient.LOGGER.debug("TF Skills: partícula desconocida {}", rawName);
                return Optional.empty();
            }
            // El polvo dura más cuanto más grande es (hasta 8 s flotando con tamaño 4): como mucho 2
            float sz = Math.max(0.05F, Math.min(2F, size));
            if (type == ParticleTypes.DUST) return Optional.of(new DustParticleOptions(color(extra, new Vector3f(1, 0, 0)), sz));
            if (type == ParticleTypes.DUST_COLOR_TRANSITION) {
                String[] c = extra == null ? new String[0] : extra.split(">");
                return Optional.of(new DustColorTransitionOptions(color(c.length > 0 ? c[0] : null, new Vector3f(1, 1, 1)),
                        color(c.length > 1 ? c[1] : null, new Vector3f(1, 1, 1)), sz));
            }
            if (type == ParticleTypes.BLOCK || type == ParticleTypes.FALLING_DUST || type == ParticleTypes.BLOCK_MARKER) {
                String b = extra == null || extra.isBlank() ? "stone" : extra.toLowerCase(java.util.Locale.ROOT);
                ResourceLocation brl = ResourceLocation.tryParse(b.contains(":") ? b : "minecraft:" + b);
                Block block = brl == null ? null : ForgeRegistries.BLOCKS.getValue(brl);
                if (block == null) block = net.minecraft.world.level.block.Blocks.STONE;
                @SuppressWarnings("unchecked")
                ParticleType<BlockParticleOption> bt = (ParticleType<BlockParticleOption>) type;
                return Optional.of(new BlockParticleOption(bt, block.defaultBlockState()));
            }
            if (type == ParticleTypes.ITEM) {
                String it = extra == null || extra.isBlank() ? "stone" : extra.toLowerCase(java.util.Locale.ROOT);
                ResourceLocation irl = ResourceLocation.tryParse(it.contains(":") ? it : "minecraft:" + it);
                var item = irl == null ? null : ForgeRegistries.ITEMS.getValue(irl);
                if (item == null) item = Items.STONE;
                return Optional.of(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(item)));
            }
            return type instanceof SimpleParticleType simple ? Optional.of(simple) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static Vector3f color(String s, Vector3f def) {
        if (s == null || s.isBlank()) return def;
        String t = s.trim().replace("#", "");
        try {
            if (t.contains(",")) {
                String[] p = t.split(",");
                return new Vector3f(Float.parseFloat(p[0]) / 255F, Float.parseFloat(p[1]) / 255F, Float.parseFloat(p[2]) / 255F);
            }
            int c = Integer.parseInt(t, 16);
            return new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F);
        } catch (Exception e) {
            return def;
        }
    }

    private static float parse(String s, float def) {
        try {
            return Float.parseFloat(s);
        } catch (Exception e) {
            return def;
        }
    }

    /** Todos los modelos de ítem de los efectos (se registran para que el juego los cargue). */
    static Set<ResourceLocation> itemModels() {
        Set<ResourceLocation> out = new HashSet<>();
        for (SkillDefs.ClassDef c : SkillDefs.all().values()) {
            for (String m : c.itemModels) {
                ResourceLocation rl = ResourceLocation.tryParse(m);
                if (rl != null && rl.getNamespace().equals(TFClient.MOD_ID)) out.add(rl);
            }
        }
        return out;
    }

    /** Al recargar recursos. */
    public static void clearCache() {
        MODELS.clear();
        PARTICLES.clear();
        TEXTURES.clear();
    }
}
