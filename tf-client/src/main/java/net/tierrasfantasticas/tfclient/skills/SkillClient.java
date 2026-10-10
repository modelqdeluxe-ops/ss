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
            mc.getSoundManager().play(new SimpleSoundInstance(rl, SoundSource.PLAYERS, m.hs(), m.vs(), RandomSource.create(RANDOM.nextLong()),
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
        if (m.kind() == 6) { // retroceso de la cámara
            if (mc.player != null) mc.player.setXRot(Math.max(-90F, Math.min(90F, mc.player.getXRot() + m.hs())));
            return;
        }
        ParticleOptions opts = particle(m.id(), m.extra(), m.size());
        if (opts == null) return;
        Vec3 at = new Vec3(m.x(), m.y(), m.z());
        switch (m.kind()) {
            case 1 -> { // esfera
                for (int i = 0; i < m.n(); i++) {
                    double u = RANDOM.nextDouble() * 2 - 1, a = RANDOM.nextDouble() * Math.PI * 2, rr = Math.sqrt(1 - u * u);
                    Vec3 d = new Vec3(rr * Math.cos(a), u, rr * Math.sin(a));
                    Vec3 p = at.add(d.scale(m.radius()));
                    level.addParticle(opts, true, p.x, p.y, p.z, d.x * m.speed(), d.y * m.speed(), d.z * m.speed());
                }
            }
            case 2 -> { // anillo
                for (int i = 0; i < m.points(); i++) {
                    double a = Math.PI * 2 * i / Math.max(1, m.points());
                    burst(level, opts, at.add(Math.cos(a) * m.radius(), 0, Math.sin(a) * m.radius()), Math.max(1, m.n()), m.hs(), m.vs(), m.speed());
                }
            }
            case 3 -> { // línea
                Vec3 to = new Vec3(m.x2(), m.y2(), m.z2());
                double len = to.distanceTo(at);
                int steps = (int) Math.min(200, len / Math.max(0.1, m.radius()));
                for (int i = 0; i <= steps; i++) {
                    Vec3 p = at.lerp(to, steps == 0 ? 0 : (double) i / steps);
                    burst(level, opts, p, Math.max(1, m.n()), 0, 0, 0);
                }
            }
            default -> {
                if (m.n() <= 0) {
                    level.addParticle(opts, true, at.x, at.y, at.z, m.hs() * m.speed(), m.vs() * m.speed(), m.hs() * m.speed());
                } else {
                    burst(level, opts, at, m.n(), m.hs(), m.vs(), m.speed());
                }
            }
        }
    }

    /** Como hace Minecraft con las partículas que manda el servidor: desvío y velocidad gaussianos. */
    private static void burst(ClientLevel level, ParticleOptions opts, Vec3 at, int n, double hs, double vs, double sp) {
        for (int i = 0; i < n; i++) {
            level.addParticle(opts, true, at.x + RANDOM.nextGaussian() * hs, at.y + RANDOM.nextGaussian() * vs,
                    at.z + RANDOM.nextGaussian() * hs, RANDOM.nextGaussian() * sp, RANDOM.nextGaussian() * sp, RANDOM.nextGaussian() * sp);
        }
    }

    private static void mask(ClientLevel level, SkillNet.Fx m) {
        ResourceLocation rl = ResourceLocation.tryParse(m.id().contains(":") ? m.id() : "minecraft:" + m.id());
        Block block = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
        if (block == null) return;
        BlockState shown = block.defaultBlockState();
        int r = (int) Math.ceil(m.radius());
        BlockPos c = BlockPos.containing(m.x(), m.y() - 0.5, m.z());
        long until = clientTicks + m.n();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > m.radius() * m.radius()) continue;
                for (int dy = -1; dy <= 0; dy++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    BlockState now = level.getBlockState(p);
                    if (now.isAir() || !now.isSolidRender(level, p)) continue;
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
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTORS.clear();
            MASKS.clear();
            return;
        }
        if (mc.isPaused()) return;
        clientTicks++;
        for (int i = 0; i < left.length; i++) if (left[i] > 0) left[i]--;
        SkillHud.tick();
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

    /** Golpe al aire o a algo (clic izquierdo): se le dice al servidor (auras onSwing de la clase). */
    @SubscribeEvent
    public static void onInteract(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && !classId.isEmpty()) SkillNet.CHANNEL.sendToServer(new SkillNet.Swing());
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
                Vec3 at = a.lerp(mc.level, partial);
                if (at == null || at.distanceToSqr(cam) > 128 * 128) continue;
                float yaw = a.lerpYaw(mc.level, partial);
                if (a.head != null) renderHead(a, at.subtract(cam), yaw, a.prevPitch + (a.pitch - a.prevPitch) * partial, pose, buffers);
                if (a.hand != null) renderHand(a, at.subtract(cam), yaw, pose, buffers);
                if (a.model != null) a.renderModel(at.subtract(cam), yaw, partial, pose, buffers);
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
                new ResourceLocation(TFClient.MOD_ID, "skills_" + a.cls));
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
            pose.translate(def.transX, def.transY, def.transZ);
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

        Actor(int id) {
            this.id = id;
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
            if (model.anims.containsKey("spawn")) play("spawn", 1F);
            else playIdle();
        }

        void play(String name, float sp) {
            if (model == null) return;
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
            float seconds = (age - animStart + partial) / 20F * speed;
            VfxPose.compute(model, anim, seconds, 0F, bones);
            Matrix4f inst = new Matrix4f().translate((float) at.x, (float) at.y, (float) at.z)
                    .rotate(Axis.YP.rotationDegrees(180F - yaw)).scale(scale / 16F);
            Matrix4f base = new Matrix4f(pose.last().pose()).mul(inst);
            Matrix3f normalView = new Matrix3f(pose.last().normal());
            Matrix3f normalBase = new Matrix3f(normalView).mul(new Matrix3f(inst));
            Map<ResourceLocation, List<int[]>> byTex = new HashMap<>();
            for (int bi = 0; bi < model.bones.length; bi++) {
                if (!visible[bi]) continue;
                VfxModel.Bone bone = boneOf(bi);
                if (bone == null || bone.quadCount == 0) continue;
                VfxModel src = source[bi];
                for (int q = 0; q < bone.quadCount; q++) {
                    int ti = (int) bone.quads[q * VfxModel.QUAD];
                    if (ti < 0 || ti >= src.tex.length) continue;
                    byTex.computeIfAbsent(texture(src, ti), k -> new ArrayList<>()).add(new int[]{bi, q, ti});
                }
            }
            Matrix4f full = new Matrix4f();
            Matrix3f normal = new Matrix3f();
            Vector3f n = new Vector3f();
            Vector4f v = new Vector4f();
            for (Map.Entry<ResourceLocation, List<int[]>> e : byTex.entrySet()) {
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentCull(e.getKey()));
                int lastBone = -1;
                for (int[] item : e.getValue()) {
                    int bi = item[0], q = item[1], ti = item[2];
                    VfxModel src = source[bi];
                    VfxModel.Bone bone = boneOf(bi);
                    if (bi != lastBone) {
                        full.set(base).mul(bones[bi]);
                        normal.set(normalBase).mul(new Matrix3f(bones[bi]));
                        lastBone = bi;
                    }
                    int o = q * VfxModel.QUAD;
                    float frames = 1, frame = 0;
                    VfxModel.Tex t = src.tex[ti];
                    if (t.frames() > 1) {
                        frames = t.frames();
                        frame = (age / t.frameTime()) % t.frames();
                    }
                    if (bone.shade) n.set(bone.quads[o + 1], bone.quads[o + 2], bone.quads[o + 3]).mul(normal).normalize();
                    else n.set(0, 1, 0).mul(normalView);
                    if (!Float.isFinite(n.x)) n.set(0, 1, 0);
                    for (int k = 0; k < 4; k++) {
                        int p = o + 4 + k * 5;
                        v.set(bone.quads[p], bone.quads[p + 1], bone.quads[p + 2], 1F);
                        full.transform(v);
                        float tv = (bone.quads[p + 4] + frame) / frames;
                        vc.vertex(v.x, v.y, v.z).color(tr, tg, tb, 1F).uv(bone.quads[p + 3], tv)
                                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(n.x, n.y, n.z).endVertex();
                    }
                }
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

    static VfxModel model(String id) {
        if (id == null || id.isEmpty()) return null;
        return MODELS.computeIfAbsent(id, key -> {
            ResourceLocation rl = new ResourceLocation(TFClient.MOD_ID, "skills/models/" + key + ".json");
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
            float sz = Math.max(0.05F, Math.min(4F, size));
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
