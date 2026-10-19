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

    /** Para la prueba en el juego: cuántos efectos llevan un objeto. */
    public static int debugItemCount() {
        int n = 0;
        for (Actor a : ACTORS.values()) if (a.item != null) n++;
        return n;
    }

    /** Para la prueba en el juego: cuántos efectos llevan un objeto, cuántos sin su modelo cargado y uno de ejemplo. */
    public static String debugItems() {
        int n = 0, missing = 0, display = 0;
        String example = "";
        BakedModel miss = Minecraft.getInstance().getModelManager().getMissingModel();
        for (Actor a : ACTORS.values()) {
            if (a.item == null) continue;
            n++;
            if (a.itemDisplay) display++;
            BakedModel m = Minecraft.getInstance().getModelManager().getModel(a.item);
            if (m == null || m == miss) {
                missing++;
                example = a.item + " (sin modelo)";
            } else if (example.isEmpty()) {
                example = a.item + " escala " + (a.def == null ? "?" : a.def.scaleX) + " billboard " + (a.def == null ? "?" : a.def.billboard);
            }
        }
        return n + " con objeto (" + display + " item_display, " + missing + " sin modelo)" + (example.isEmpty() ? "" : "; p. ej. " + example);
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
            // Cabezas, colas/física e IK de ModelEngine: VfxRig (lo mismo hace el servidor con sus mensajes)
            case "seg", "headcfg", "headlock", "headtgt", "jig", "ik", "iktgt", "ikchain" -> {
                if (a.rig != null) a.rig.message(m.key(), m.a(), m.b(), m.c());
                // (para rehacerlo si el modelo cambia en vivo: changeparent, submodel)
                if (!m.key().equals("jig")) a.rigLog.add(new String[] {m.key(), m.a(), m.b(), m.c()});
            }
            // ModelEngine 4: lo de §11.2 de JEFES.md (ver SkillMeg)
            case "medit" -> a.applyEdits(m.b());
            case "anim" -> {
                if (a.layers != null && a.model != null) a.layers.animate(a.model, m.a(), !"0".equals(m.c()), m.b());
            }
            case "root" -> {
                if (a.layers != null && a.model != null) a.layers.rootMessage(a.model, m.a(), m.b());
            }
            case "follow" -> {
                a.follow = (int) parse(m.a(), -1F);
                a.hideHost = "1".equals(m.b());
            }
            case "seeself" -> a.seeSelf = !"0".equals(m.a());
            case "view" -> a.viewRange = parse(m.a(), 0F);
            case "fire" -> {
                String[] p = m.a().split("\\|");
                a.fire = p.length > 1 && "1".equals(p[1]) ? 1 : "0".equals(p[0]) ? -1 : 0;
            }
            case "shadow" -> a.shadowOn = !"0".equals(m.a());
            case "pair" -> a.pair = m.a().isEmpty() ? null : "1".equals(m.a());
            case "lclear" -> {
                if (a.layers != null) a.layers.clear();
            }
            case "freeze" -> {
                if (a.layers != null) a.layers.freeze(a.age - parse(m.a(), 0F));
            }
            // ModelEngine 4 (ver SkillMeg)
            case "lmod" -> {
                if (a.layers == null) return;
                String[] p = m.b().split("\\|", -1);
                int pr = (int) parse(p[0], -1F);
                float sp = p[1].equals("NaN") ? Float.NaN : parse(p[1], Float.NaN);
                Boolean ov = p.length > 5 && !p[5].isEmpty() ? "1".equals(p[5]) : null;
                a.layers.modify(m.a(), pr, a.age, sp, (int) parse(p[2], -1F), (int) parse(p[3], -1F), (int) parse(p[4], -1F), ov);
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
            case "skin" -> a.limbSkin(m.a(), m.b(), m.c());
            case "remap" -> a.remap(m.a());
            case "dt" -> a.displayTransform(m.a(), m.b());
            case "item" -> {
                a.item = m.a().isEmpty() ? null : new ResourceLocation(m.a());
                a.itemScale = parse(m.b(), 1F);
                // Cómo se dibuja: «head» (la bala TRACKING de MythicMobs: un soporte con el objeto en la cabeza),
                // «ground» (ITEM: un objeto suelto que gira), si no como un display (FIXED)
                // y «display» (el objeto de un item_display: sin transformación de objeto, con su billboard y escala)
                a.itemDisplay = "display".equals(m.c());
                a.itemContext = switch (m.c() == null ? "" : m.c()) {
                    case "head" -> ItemDisplayContext.HEAD;
                    case "ground" -> ItemDisplayContext.GROUND;
                    case "display" -> ItemDisplayContext.NONE;
                    default -> ItemDisplayContext.FIXED;
                };
            }
            case "beam" -> a.beam = m.a().isEmpty() ? null : m.a();
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
        if (m.kind() == 7) {
            unmask(level, m);
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

    /** blockunmask: los bloques de blockmask a «radius» del sitio vuelven a ser los de verdad (points 1 = en cubo). */
    private static void unmask(ClientLevel level, SkillNet.Fx m) {
        if (MASKS.isEmpty()) return;
        double r = m.radius();
        boolean cube = m.points() == 1;
        Iterator<Map.Entry<BlockPos, Mask>> it = MASKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Mask> e = it.next();
            BlockPos p = e.getKey();
            double dx = p.getX() + 0.5 - m.x(), dy = p.getY() + 0.5 - m.y(), dz = p.getZ() + 0.5 - m.z();
            boolean in = cube ? Math.abs(dx) <= r + 0.5 && Math.abs(dy) <= r + 0.5 && Math.abs(dz) <= r + 0.5
                    : dx * dx + dy * dy + dz * dz <= (r + 0.5) * (r + 0.5);
            if (!in) continue;
            if (level.getBlockState(p) == e.getValue().shown()) level.setBlock(p, e.getValue().original(), 0);
            it.remove();
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
                // El propio disfraz no tapa la vista en primera persona (ni se ve con seeself=false)
                if (a.follow >= 0 && mc.player != null && a.follow == mc.player.getId()
                        && (mc.options.getCameraType().isFirstPerson() || !a.seeSelf)) continue;
                // pairmodel{hidden=true}: a este jugador no se le enseña
                if (Boolean.TRUE.equals(a.pair)) continue;
                // El modelo de un mob de jefe lo dibuja su entidad (BossRenderer), con su luz
                // (solo el suyo: los vfx{} pegados al jefe se dibujan aquí)
                if (a.follow >= 0 && mc.level.getEntity(a.follow) instanceof net.tierrasfantasticas.tfclient.bosses.BossEntity
                        && follower(a.follow) == a) continue;
                Vec3 at = a.lerp(mc.level, partial);
                // Hasta 128 bloques, o lo que diga model{viewrange;radius}; pairmodel{hidden=false}: siempre
                double far = Boolean.FALSE.equals(a.pair) ? 512 : a.viewRange > 0 ? a.viewRange : 128;
                if (at == null || at.distanceToSqr(cam) > far * far) continue;
                float yaw = a.lerpYaw(mc.level, partial);
                int light = a.boss ? net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level, BlockPos.containing(at.add(0, 0.5, 0))) : FULL_BRIGHT;
                if (a.head != null) renderHead(a, at.subtract(cam), yaw, a.prevPitch + (a.pitch - a.prevPitch) * partial, pose, buffers);
                if (a.hand != null) renderHand(a, at.subtract(cam), yaw, pose, buffers);
                if (a.item != null) renderItem(a, at.subtract(cam), yaw, a.prevPitch + (a.pitch - a.prevPitch) * partial, camera, pose, buffers);
                a.poseOrigin = cam;
                if (a.model != null) a.renderModel(at.subtract(cam), yaw, partial, pose, buffers, light);
                if (a.text != null && !a.text.isEmpty()) renderText(a, at.subtract(cam), yaw, camera, pose, buffers);
                if (a.beam != null) renderBeam(a, at, cam, partial, pose, buffers);
            }
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Skills: error al dibujar los efectos", t);
            ACTORS.clear();
        } finally {
            buffers.endBatch();
        }
    }

    /**
     * enderbeam: un cristal del End (como EndCrystalRenderer, sin base) y su rayo hacia la entidad o el sitio (como
     * EnderDragonRenderer.renderCrystalBeams, el del dragón).
     */
    private static void renderBeam(Actor a, Vec3 at, Vec3 cam, float partial, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (a.crystal == null) {
            a.crystal = new net.minecraft.world.entity.boss.enderdragon.EndCrystal(mc.level, at.x, at.y, at.z);
            a.crystal.setShowBottom(false);
        }
        a.crystal.time = a.age;
        a.crystal.setPos(at.x, at.y, at.z);
        int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
        Vec3 rel = at.subtract(cam);
        mc.getEntityRenderDispatcher().getRenderer(a.crystal).render(a.crystal, 0F, partial, translated(pose, rel), buffers, light);
        pose.popPose();
        Vec3 to = null;
        if (a.beam.startsWith("e:")) {
            net.minecraft.world.entity.Entity e = mc.level.getEntity((int) parse(a.beam.substring(2), -1F));
            if (e != null) to = e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0);
        } else if (a.beam.startsWith("p:")) {
            String[] p = a.beam.substring(2).split(",");
            if (p.length == 3) to = new Vec3(parse(p[0], 0F), parse(p[1], 0F), parse(p[2], 0F));
        }
        if (to == null) return;
        float bob = net.minecraft.client.renderer.entity.EndCrystalRenderer.getY(a.crystal, partial);
        Vec3 d = to.subtract(at);
        pose.pushPose();
        pose.translate(to.x - cam.x, to.y - cam.y, to.z - cam.z);
        net.minecraft.client.renderer.entity.EnderDragonRenderer.renderCrystalBeams((float) -d.x, (float) -d.y + bob, (float) -d.z,
                partial, a.age, pose, buffers, light);
        pose.popPose();
    }

    private static PoseStack translated(PoseStack pose, Vec3 rel) {
        pose.pushPose();
        pose.translate(rel.x, rel.y, rel.z);
        return pose;
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

    /**
     * La bala de un misil de tipo display (la esfera de Astwood): el modelo de ítem, mirando hacia donde va. El objeto de
     * un item_display (1.3.89: las llamas de Ignevar, las partículas del láser del contemplador y del Piglin Mecha), como
     * el DisplayRenderer de Minecraft: el billboard de sus DisplayOptions (CENTER: mirando a la cámara), su traslación y
     * escala, girado 180° y sin transformación de objeto (NONE).
     */
    private static void renderItem(Actor a, Vec3 at, float yaw, float pitch, Camera camera, PoseStack pose, MultiBufferSource buffers) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(a.item);
        if (model == null || model == Minecraft.getInstance().getModelManager().getMissingModel()) return;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        if (a.itemDisplay) {
            SkillDefs.MobDef def = a.def;
            String bb = def == null || def.billboard == null ? "FIXED" : def.billboard.toUpperCase(java.util.Locale.ROOT);
            switch (bb) {
                case "CENTER" -> pose.mulPose(camera.rotation());
                case "VERTICAL" -> pose.mulPose(Axis.YP.rotationDegrees(180F - camera.getYRot()));
                case "HORIZONTAL" -> {
                    pose.mulPose(Axis.YP.rotationDegrees(180F - yaw));
                    pose.mulPose(Axis.XP.rotationDegrees(-camera.getXRot()));
                }
                default -> {
                    pose.mulPose(Axis.YP.rotationDegrees(180F - yaw));
                    pose.mulPose(Axis.XP.rotationDegrees(pitch));
                }
            }
            // (el orden de Minecraft: traslación, giro izquierdo, escala, giro derecho)
            if (def != null) pose.translate(def.transX, def.transY, def.transZ);
            pose.mulPose(a.dtLeft);
            if (def != null) pose.scale(def.scaleX, def.scaleY, def.scaleZ);
            pose.mulPose(a.dtRight);
            pose.mulPose(Axis.YP.rotationDegrees(180F));
            pose.scale(a.itemScale, a.itemScale, a.itemScale);
            Minecraft.getInstance().getItemRenderer().render(STACK, ItemDisplayContext.NONE, false, pose, buffers, FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, model);
            pose.popPose();
            return;
        }
        if (a.itemContext == ItemDisplayContext.GROUND) {
            // Un objeto suelto de Minecraft (ItemEntityRenderer): gira solo, sin seguir la dirección
            pose.mulPose(Axis.YP.rotation((a.age + Minecraft.getInstance().getFrameTime()) / 20F));
        } else {
            pose.mulPose(Axis.YP.rotationDegrees(180F - yaw));
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
        }
        pose.scale(a.itemScale, a.itemScale, a.itemScale);
        Minecraft.getInstance().getItemRenderer().render(STACK, a.itemContext, false, pose, buffers, FULL_BRIGHT,
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
        // pairmodel{hidden=true} y model{viewrange;radius}: no se dibuja (pero es el suyo: tampoco se ve la entidad)
        if (Boolean.TRUE.equals(a.pair)) return true;
        Minecraft mcv = Minecraft.getInstance();
        if (a.viewRange > 0 && !Boolean.FALSE.equals(a.pair)
                && e.distanceToSqr(mcv.gameRenderer.getMainCamera().getPosition()) > (double) a.viewRange * a.viewRange) return true;
        float yaw = a.lerpYaw(e.level() instanceof ClientLevel cl ? cl : Minecraft.getInstance().level, partial);
        // Dónde está el modelo en el mundo (para las colas y la física de ModelEngine, que van en el mundo)
        Vec3 at = e.getPosition(partial);
        a.physWorld = new Matrix4f().translate((float) at.x, (float) at.y, (float) at.z)
                .rotate(Axis.YP.rotationDegrees(180F - yaw)).scale(a.scale / 16F);
        a.poseOrigin = at;
        a.renderModel(Vec3.ZERO, yaw, partial, pose, buffers, light);
        return true;
    }

    /** La sombra de un mob de jefe: la del hueso «shadow» de su modelo (radio, en bloques), o 0. */
    public static float shadowRadius(Entity e) {
        Actor a = follower(e.getId());
        if (a == null || a.model == null || !a.shadowOn || Boolean.TRUE.equals(a.pair)) return 0F;
        return a.model.shadow / 2F * a.scale;
    }

    static {
        SkillMobs.clientFire = e -> {
            Actor a = follower(e.getId());
            return a == null ? 0 : a.fire;
        };
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
        ItemDisplayContext itemContext = ItemDisplayContext.FIXED;
        /** El objeto es el de un item_display (con el billboard y la escala de su mob). */
        boolean itemDisplay;
        /** Giros izquierdo y derecho de un item_display (displaytransformation con cuaternión). */
        final org.joml.Quaternionf dtLeft = new org.joml.Quaternionf(), dtRight = new org.joml.Quaternionf();
        /** enderbeam: el rayo hacia una entidad («e:id») o un sitio («p:x,y,z»), con su cristal del End (solo dibujado). */
        String beam;
        net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal;
        /** displaytransformation del text_display: giro (grados) y desplazamiento. */
        float textRoll;
        float textX, textY, textZ;
        /** Cabezas, colas, física e IK de ModelEngine (VfxRig) y dónde está el modelo en el mundo. */
        net.tierrasfantasticas.tfclient.vfx.VfxRig rig;
        Matrix4f physWorld;
        // ----- ModelEngine: por hueso (setitemmodel, setmodeltag, enchant, billboard, glowbone) y skin de los miembros
        ItemStack[] boneItem;
        String[] boneTag;
        String[] boneTagStyle;
        boolean[] boneTagOn;
        boolean[] boneGlint;
        int[] boneBillboard;
        int[] boneGlow;
        /** modelplayerskin: la skin de cada hueso de jugador (uuid, nombre o «tex:» + base64; null = la de por defecto) y si es slim («» = la de la skin). */
        String[] boneSkin;
        String[] boneSlim;
        /** model{damagetint}: parpadea en rojo al recibir daño (como ModelEngine, sí por defecto). */
        boolean damageTint = true;
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
        /** El modelo sin los cambios en vivo y los mensajes de su VfxRig (para rehacerlo al cambiar el modelo en vivo). */
        String meId;
        final List<String[]> rigLog = new ArrayList<>();
        /** model{seeself} / modeldisguise{seeself}: se ve a sí mismo el jugador disfrazado. */
        boolean seeSelf = true;
        /** model{viewrange;radius}: hasta dónde se ve (bloques; 0 = lo de siempre). */
        float viewRange;
        /** model{renderfire;forcefire}: 1 = fuego siempre, -1 = nunca, 0 = el de Minecraft. */
        int fire;
        /** model{showshadow}. */
        boolean shadowOn = true;
        /** pairmodel: escondido (true) o enseñado siempre (false) a este jugador; null = lo normal. */
        Boolean pair;

        Actor(int id) {
            this.id = id;
        }

        /** Pose de todos los huesos ahora (con sus capas si es de un jefe). */
        void pose(Matrix4f[] mats, float partial) {
            if (layers != null) {
                float headYaw = 0F, headPitch = 0F, body = 0F;
                Vector3f eye = null;
                ClientLevel level = Minecraft.getInstance().level;
                Entity e = follow >= 0 && level != null ? level.getEntity(follow) : null;
                if (e instanceof LivingEntity le) {
                    body = le.yBodyRotO + (le.yBodyRot - le.yBodyRotO) * partial;
                    float head = le.yHeadRotO + (le.yHeadRot - le.yHeadRotO) * partial;
                    headYaw = net.minecraft.util.Mth.wrapDegrees(head - body);
                    headPitch = le.getViewXRot(partial);
                    Vec3 ep = le.getPosition(partial);
                    eye = new Vector3f((float) ep.x, (float) (ep.y + le.getEyeHeight()), (float) ep.z);
                }
                // headtarget, lockmodelhead y la velocidad de la cabeza: VfxRig (igual que el servidor)
                float now = age + partial;
                float[] look = rig.look(headYaw, headPitch, body, eye, entities(partial), now);
                layers.pose(model, now, look[0], look[1], mats, rig.limits());
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

        /**
         * displaytransformation: de un text_display, giro (right_rotation, en grados) o desplazamiento (translation); con
         * cuatro números (un cuaternión, como en Minecraft), el giro izquierdo o derecho de un item_display (ADD los suma
         * componente a componente y se normaliza, como MythicMobs: las llamas de Ignevar salen cada una algo giradas).
         */
        void displayTransform(String what, String value) {
            String[] w = what.split("\\|");
            boolean add = w[0].equals("add");
            String tr = w.length > 1 ? w[1] : "translation";
            String[] v = value.split(",");
            if (tr.contains("rotation") && v.length >= 4) {
                org.joml.Quaternionf q = tr.contains("left") ? dtLeft : dtRight;
                float x = parse(v[0].trim(), 0F), y = parse(v[1].trim(), 0F), z = parse(v[2].trim(), 0F), qw = parse(v[3].trim(), 0F);
                if (add) q.set(q.x + x, q.y + y, q.z + z, q.w + qw);
                else q.set(x, y, z, qw);
                if (q.lengthSquared() < 1e-8F) q.identity();
                else q.normalize();
            } else if (tr.contains("rotation")) {
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
            meId = id;
            model = id == null ? null : model(id);
            rigLog.clear();
            boneSkin = null;
            boneSlim = null;
            boneLight = null;
            rig = model == null ? null : new net.tierrasfantasticas.tfclient.vfx.VfxRig(model);
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
                layers.playSpawn(model, age);
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
                float now = age + partial;
                rig.bend(bones, physWorld, now, b -> layers.moves(model, b, now), entities(partial));
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
            for (int bi = 0; bi < model.bones.length; bi++) {
                if (!visible[bi] || model.bones[bi].removed) continue;
                VfxModel.Bone bone = boneOf(bi);
                if (bone == null) continue;
                float[] qd = quadsOf(bi, bone);
                int qc = qd.length / VfxModel.QUAD;
                if (qc == 0) continue;
                VfxModel src = source[bi];
                int glow = outline != null ? boneGlow[bi] : 0;
                for (int q = 0; q < qc; q++) {
                    int ti = (int) qd[q * VfxModel.QUAD];
                    ResourceLocation rl;
                    if (ti >= 0 && ti < src.tex.length) {
                        rl = texture(src, ti);
                    } else if (layers != null && (ti == -1 || ti == -2 || ti == -4 || ti == -5)) {
                        // Miembros de jugador de ModelEngine (phead_, prarm_..., limb[]): con la skin de modelplayerskin
                        rl = skinFor(bi);
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
                    float[] qd = quadsOf(bi, bone);
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
                    if (bone.shade) n.set(qd[o + 1], qd[o + 2], qd[o + 3]).mul(normal).normalize();
                    else n.set(0, 1, 0).mul(normalView);
                    if (!Float.isFinite(n.x)) n.set(0, 1, 0);
                    // Centro de la cara en la textura: cada esquina se mete un poco hacia él (como hace Minecraft al
                    // hornear los modelos), para que en los bordes no se cuele el píxel de al lado ni, en las texturas
                    // animadas, una raya del fotograma siguiente
                    float[] c = VfxModel.uvCenter(qd, o);
                    // Luz: las texturas emisivas (_e) y las caras que brillan, a plena luz; si no, la del hueso o la del sitio
                    int lit = emissive || ((int) qd[o + 24] & 1) != 0 ? FULL_BRIGHT
                            : boneLight != null && boneLight[bi] >= 0 ? boneLight[bi] : baseLight;
                    for (int k = 0; k < 4; k++) {
                        int p = o + 4 + k * 5;
                        v.set(qd[p], qd[p + 1], qd[p + 2], 1F);
                        full.transform(v);
                        float tu = VfxModel.insetUv(qd[p + 3], c[0]);
                        float tv = (VfxModel.insetUv(qd[p + 4], c[1]) + frame) / frames;
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

        /**
         * setmodeltag: el texto de los huesos de nombre (tag_) como un text_display de Minecraft (TextDisplayRenderer):
         * líneas partidas a «linewidth», alineadas (LEFT, CENTER, RIGHT) y creciendo hacia arriba desde el hueso, con su
         * billboard (CENTER = mirando a la cámara, VERTICAL = solo gira de lado, HORIZONTAL y FIXED = con el hueso).
         */
        private void drawTags(Matrix4f base, PoseStack pose, MultiBufferSource buffers, int light) {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.client.gui.Font font = mc.font;
            Camera cam = mc.gameRenderer.getMainCamera();
            Matrix4f full = new Matrix4f();
            for (int bi = 0; bi < model.bones.length; bi++) {
                if (boneTag[bi] == null || !boneTagOn[bi] || boneTag[bi].isEmpty() || model.bones[bi].removed) continue;
                String[] st = boneTagStyle[bi].split("\\|");
                String[] sv = st[0].split(",");
                float sx = parse(sv[0], 1F), sy = sv.length > 1 ? parse(sv[1], sx) : sx, sz = sv.length > 2 ? parse(sv[2], sx) : sx;
                boolean shadow = st.length > 1 && "1".equals(st[1]);
                boolean through = st.length > 2 && "1".equals(st[2]);
                int bg = st.length > 3 ? (int) parse(st[3], -1F) : -1;
                int op = st.length > 4 ? (int) parse(st[4], -1F) : -1;
                String align = st.length > 5 ? st[5] : "CENTER";
                int lineWidth = st.length > 6 ? Math.max(1, (int) parse(st[6], 200F)) : 200;
                String bill = st.length > 7 ? st[7] : "CENTER";
                full.set(base).mul(bones[bi]);
                Vector3f t = full.getTranslation(new Vector3f());
                Quaternionf view = pose.last().pose().getUnnormalizedRotation(new Quaternionf()).normalize();
                Quaternionf boneRot = full.getUnnormalizedRotation(new Quaternionf()).normalize().rotateY((float) Math.PI);
                Quaternionf rot = switch (bill) {
                    case "FIXED" -> boneRot;
                    case "HORIZONTAL" -> boneRot.rotateX((float) Math.toRadians(cam.getXRot()));
                    case "VERTICAL" -> view.mul(new Quaternionf().rotationY((float) Math.toRadians(-cam.getYRot())));
                    default -> view.mul(cam.rotation());
                };
                Matrix4f m = new Matrix4f().translate(t).rotate(rot).scale(-0.025F * sx, -0.025F * sy, 0.025F * sz);
                List<net.minecraft.util.FormattedCharSequence> lines = font.split(net.minecraft.network.chat.Component.literal(boneTag[bi]), lineWidth);
                int maxW = 0;
                for (net.minecraft.util.FormattedCharSequence l : lines) maxW = Math.max(maxW, font.width(l));
                int lh = font.lineHeight + 1;
                float y = -lines.size() * lh;
                int color = op < 0 ? 0xFFFFFFFF : (Math.max(0, Math.min(255, op)) << 24) | 0xFFFFFF;
                int back = bg == -1 ? (int) (mc.options.getBackgroundOpacity(0.25F) * 255F) << 24 : bg;
                net.minecraft.client.gui.Font.DisplayMode mode = through ? net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH
                        : net.minecraft.client.gui.Font.DisplayMode.NORMAL;
                for (net.minecraft.util.FormattedCharSequence l : lines) {
                    int w = font.width(l);
                    float x = switch (align) {
                        case "LEFT" -> -maxW / 2F;
                        case "RIGHT" -> maxW / 2F - w;
                        default -> -w / 2F;
                    };
                    font.drawInBatch(l, x, y, color, shadow, m, buffers, mode, back, light);
                    y += lh;
                }
            }
        }

        /**
         * changeparent / submodel (mensaje «medit»: todos los cambios, en orden): rehace el modelo como el servidor
         * (VfxModel.derive) y lleva lo de cada hueso (visible, piezas, brillos, objetos, nombres, skins...) al hueso del
         * mismo nombre; las animaciones que suenan siguen; la física y la IK vuelven a empezar con lo que tenían puesto.
         */
        void applyEdits(String joined) {
            VfxModel base = meId == null ? null : model(meId);
            if (base == null || model == null) return;
            VfxModel now = base.derive(joined == null || joined.isEmpty() ? List.of() : List.of(joined.split("\n")));
            VfxModel old = model;
            if (now == old) return;
            int n = now.bones.length;
            int[] from = new int[n];
            for (int i = 0; i < n; i++) from[i] = old.bone(now.bones[i].id);
            VfxModel[] src2 = new VfxModel[n];
            String[] srcBone2 = new String[n];
            boolean[] vis2 = new boolean[n];
            Matrix4f[] mats2 = new Matrix4f[n];
            for (int i = 0; i < n; i++) {
                int j = from[i];
                mats2[i] = new Matrix4f();
                boolean same = j >= 0 && j < old.bones.length;
                src2[i] = same && source[j] != old ? source[j] : now;
                srcBone2[i] = same ? sourceBone[j] : now.bones[i].id;
                vis2[i] = (same ? visible[j] : !now.bones[i].hidden) && !now.bones[i].removed;
            }
            boneLight = remapInts(boneLight, from, n, -1);
            boneBillboard = remapInts(boneBillboard, from, n, 0);
            boneGlow = remapInts(boneGlow, from, n, 0);
            if (boneGlint != null) {
                boolean[] g = new boolean[n];
                for (int i = 0; i < n; i++) g[i] = from[i] >= 0 && from[i] < boneGlint.length && boneGlint[from[i]];
                boneGlint = g;
            }
            if (boneTagOn != null) {
                boolean[] g = new boolean[n];
                for (int i = 0; i < n; i++) g[i] = from[i] >= 0 && from[i] < boneTagOn.length && boneTagOn[from[i]];
                boneTagOn = g;
            }
            boneItem = remap(boneItem, from, n, new ItemStack[n]);
            boneTag = remap(boneTag, from, n, new String[n]);
            boneTagStyle = remap(boneTagStyle, from, n, new String[n]);
            boneSkin = remap(boneSkin, from, n, new String[n]);
            boneSlim = remap(boneSlim, from, n, new String[n]);
            model = now;
            source = src2;
            sourceBone = srcBone2;
            visible = vis2;
            bones = mats2;
            if (layers != null) layers.rebind(old, now);
            rig = new net.tierrasfantasticas.tfclient.vfx.VfxRig(now);
            for (String[] c : rigLog) rig.message(c[0], c[1], c[2], c[3]);
        }

        private static int[] remapInts(int[] arr, int[] from, int n, int def) {
            if (arr == null) return null;
            int[] out = new int[n];
            for (int i = 0; i < n; i++) out[i] = from[i] >= 0 && from[i] < arr.length ? arr[from[i]] : def;
            return out;
        }

        private static <T> T[] remap(T[] arr, int[] from, int n, T[] out) {
            if (arr == null) return null;
            for (int i = 0; i < n; i++) out[i] = from[i] >= 0 && from[i] < arr.length ? arr[from[i]] : null;
            return out;
        }

        /**
         * modelplayerskin{limbs;uuid|username|skin;slim}: «limbs» (separados por comas; vacío = todos los huesos de jugador)
         * con la skin de «who» (uuid, nombre o «tex:» + los datos base64 de la textura) y slim («true», «false» o «» = la de
         * la skin).
         */
        void limbSkin(String limbs, String who, String slim) {
            if (model == null) return;
            int n = model.bones.length;
            if (boneSkin == null) {
                boneSkin = new String[n];
                boneSlim = new String[n];
            }
            boolean[] hit = new boolean[n];
            if (limbs == null || limbs.isBlank()) {
                for (int i = 0; i < n; i++) hit[i] = "limb".equals(model.bones[i].beh) || model.bones[i].phead;
            } else {
                for (String l : limbs.split(",")) {
                    int b = model.bone(l.trim());
                    if (b >= 0) hit[b] = true;
                }
            }
            for (int i = 0; i < n; i++) {
                if (!hit[i]) continue;
                boneSkin[i] = who == null || who.isEmpty() ? null : who;
                boneSlim[i] = slim == null ? "" : slim.toLowerCase(java.util.Locale.ROOT);
            }
        }

        /** La skin de ese hueso de jugador (la de modelplayerskin o la de por defecto). */
        private ResourceLocation skinFor(int bi) {
            String who = boneSkin == null || bi >= boneSkin.length ? null : boneSkin[bi];
            ResourceLocation rl = who == null ? null : skinOf(who);
            return rl != null ? rl : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin();
        }

        /** Las caras de un hueso: las de su skin slim si es un brazo de jugador con skin slim. */
        private float[] quadsOf(int bi, VfxModel.Bone bone) {
            if (bone.slimQuads == null || boneSkin == null || bi >= boneSkin.length) return bone.quads;
            String s = boneSlim[bi];
            boolean slim = "true".equals(s) || !"false".equals(s) && boneSkin[bi] != null && slimOf(boneSkin[bi]);
            return slim ? bone.slimQuads : bone.quads;
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

        /** Los huesos con ese nombre (todos si vacío; con exact=false, los que lo llevan) y, con child, sus hijos. */
        int[] match(String part, boolean exact, boolean child) {
            return net.tierrasfantasticas.tfclient.vfx.VfxRig.match(model, part, exact, child);
        }

        /** Las entidades del mundo para headtarget e iktarget: {x, y, z, alto}. */
        private static net.tierrasfantasticas.tfclient.vfx.VfxRig.Entities entities(float partial) {
            ClientLevel lv = Minecraft.getInstance().level;
            return id -> {
                Entity te = lv == null ? null : lv.getEntity(id);
                if (te == null) return null;
                Vec3 p = te.getPosition(partial);
                return new float[] {(float) p.x, (float) p.y, (float) p.z, te.getBbHeight()};
            };
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

        /** setmodeltag: «escala|sombra|a través|fondo|opacidad|alineación|ancho|billboard\u0001texto» en ese hueso de nombre. */
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

        /** El hueso que se dibuja en el sitio «bi» (el suyo o el de otro modelo, si se cambió con changepart). */
        VfxModel.Bone boneOf(int bi) {
            VfxModel src = source[bi];
            if (src == model && sourceBone[bi].equals(model.bones[bi].id)) return model.bones[bi];
            int b = src.bone(sourceBone[bi]);
            return b < 0 ? null : src.bones[b];
        }
    }

    // ------------------------------------------------------------------------------------------- datos

    // ------------------------------------------------------------------------------------------- skins de modelplayerskin

    /** Skins de «tex:» + base64 ya bajadas (como ModelEngine: se bajan una vez) y si son slim. */
    private static final Map<String, ResourceLocation> TEX_SKINS = new HashMap<>();
    private static final Map<String, Boolean> TEX_SLIM = new HashMap<>();
    private static final Set<String> TEX_ASKED = new HashSet<>();

    /** La skin de un jugador conectado (uuid o nombre) o la de unos datos de textura («tex:» + base64); null si aún no. */
    private static ResourceLocation skinOf(String who) {
        if (who.startsWith("tex:")) {
            String b64 = who.substring(4);
            ResourceLocation rl = TEX_SKINS.get(b64);
            if (rl == null && TEX_ASKED.add(b64)) {
                com.mojang.authlib.GameProfile gp = new com.mojang.authlib.GameProfile(
                        java.util.UUID.nameUUIDFromBytes(b64.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "tfskin");
                gp.getProperties().put("textures", new com.mojang.authlib.properties.Property("textures", b64));
                Minecraft.getInstance().getSkinManager().registerSkins(gp, (type, loc, tex) -> {
                    if (type == com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.SKIN) {
                        TEX_SKINS.put(b64, loc);
                        // «Sin modelo en los datos, CLASSIC» (wiki de ModelEngine)
                        TEX_SLIM.put(b64, "slim".equals(tex.getMetadata("model")));
                    }
                }, false);
            }
            return rl;
        }
        net.minecraft.client.multiplayer.PlayerInfo info = playerInfo(who);
        return info == null ? null : info.getSkinLocation();
    }

    /** ¿La skin de «who» es slim? (la del jugador conectado o la de los datos de textura) */
    private static boolean slimOf(String who) {
        if (who.startsWith("tex:")) return TEX_SLIM.getOrDefault(who.substring(4), false);
        net.minecraft.client.multiplayer.PlayerInfo info = playerInfo(who);
        return info != null && "slim".equals(info.getModelName());
    }

    private static net.minecraft.client.multiplayer.PlayerInfo playerInfo(String who) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return null;
        net.minecraft.client.multiplayer.PlayerInfo info = null;
        try {
            String raw = who.replace("-", "");
            if (raw.length() == 32) {
                info = conn.getPlayerInfo(new java.util.UUID(Long.parseUnsignedLong(raw.substring(0, 16), 16),
                        Long.parseUnsignedLong(raw.substring(16), 16)));
            }
        } catch (NumberFormatException ignored) {
            // no era un uuid: un nombre
        }
        return info != null ? info : conn.getPlayerInfo(who);
    }

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

    /**
     * Todos los modelos de ítem de los efectos (se registran para que el juego los cargue): los de las clases y, desde la
     * 1.3.89, también los de los jefes (antes no: sus objetos de efecto no se cargaban y no se veían, «veo invisibles sus
     * llamas»: las de Ignevar, la bola azul de Astwood y las partículas del láser del Contemplador y del Piglin Mecha).
     */
    static Set<ResourceLocation> itemModels() {
        Set<ResourceLocation> out = new HashSet<>();
        for (Map<String, SkillDefs.ClassDef> defs : List.of(SkillDefs.all(), SkillDefs.bosses(), SkillDefs.fauna())) {
            for (SkillDefs.ClassDef c : defs.values()) {
                for (String m : c.itemModels) {
                    ResourceLocation rl = ResourceLocation.tryParse(m);
                    if (rl != null && rl.getNamespace().equals(TFClient.MOD_ID)) out.add(rl);
                }
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
