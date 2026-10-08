package net.tierrasfantasticas.tfclient.vfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Los VFX en el cliente: recibe del servidor qué efecto lanzar y dónde, y sigue su línea de tiempo (fx.json) tick a
 * tick: aparecen los modelos animados, suenan los sonidos y salen las partículas. Los modelos se dibujan después de
 * los bloques translúcidos con su textura (como ModelEngine: con brillo propio y la cara trasera oculta).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class VfxClient {
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    private static final Map<String, Optional<VfxModel>> MODELS = new HashMap<>();
    private static Map<String, JsonObject> fxDefs;
    private static final List<Running> RUNNING = new ArrayList<>();
    /** Entidades que no se dibujan durante unos ticks (el cuerpo que sustituye el efecto de kill). */
    private static final Map<Integer, Integer> HIDDEN = new HashMap<>();
    private static final int MAX_RUNNING = 64;

    // Lo que lleva equipado el jugador y sus cooldowns (lo manda el servidor; ya no se enseña en la barra)
    static String hudKill;
    static String hudPack;
    static int[] hudCooldowns = new int[0];
    static int[] hudTotals = new int[0];

    private VfxClient() {}

    // ------------------------------------------------------------------------------------------- datos

    static VfxModel model(String id) {
        return MODELS.computeIfAbsent(id, key -> {
            ResourceLocation rl = new ResourceLocation(TFClient.MOD_ID, "vfx/models/" + key + ".json");
            try {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
                if (res.isEmpty()) {
                    TFClient.LOGGER.warn("TF VFX: no existe el modelo {}", rl);
                    return Optional.empty();
                }
                try (InputStream in = res.get().open()) {
                    return Optional.of(VfxModel.read(in));
                }
            } catch (Exception e) {
                TFClient.LOGGER.error("TF VFX: no se pudo leer el modelo {}", rl, e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static JsonObject fx(String id) {
        if (fxDefs == null) {
            fxDefs = new HashMap<>();
            ResourceLocation rl = new ResourceLocation(TFClient.MOD_ID, "vfx/fx.json");
            try {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
                if (res.isPresent()) {
                    try (InputStream in = res.get().open()) {
                        JsonObject all = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                        for (Map.Entry<String, JsonElement> e : all.entrySet()) fxDefs.put(e.getKey(), e.getValue().getAsJsonObject());
                    }
                }
            } catch (Exception e) {
                TFClient.LOGGER.error("TF VFX: no se pudo leer {}", rl, e);
            }
        }
        return fxDefs.get(id);
    }

    // ------------------------------------------------------------------------------------------- mensajes

    static void play(VfxNet.Play msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        JsonObject def = fx(msg.fx());
        if (def == null) {
            TFClient.LOGGER.warn("TF VFX: efecto desconocido {}", msg.fx());
            return;
        }
        if (RUNNING.size() >= MAX_RUNNING) RUNNING.remove(0);
        RUNNING.add(new Running(def, msg));
    }

    static void state(VfxNet.State msg) {
        hudKill = msg.kill();
        hudPack = msg.pack();
        hudCooldowns = msg.cooldowns().clone();
        hudTotals = msg.totals().clone();
    }

    /** El cliente baja los cooldowns del indicador a la vez que el servidor (para que la barra se mueva sola). */
    private static void tickHud() {
        for (int i = 0; i < hudCooldowns.length; i++) if (hudCooldowns[i] > 0) hudCooldowns[i]--;
    }

    // ------------------------------------------------------------------------------------------- eventos

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!RUNNING.isEmpty()) RUNNING.clear();
            HIDDEN.clear();
            return;
        }
        if (mc.isPaused()) return;
        tickHud();
        HIDDEN.replaceAll((id, t) -> t - 1);
        HIDDEN.values().removeIf(t -> t <= 0);
        Iterator<Running> it = RUNNING.iterator();
        while (it.hasNext()) {
            Running r = it.next();
            try {
                if (!r.tick(mc.level)) it.remove();
            } catch (Throwable t) {
                TFClient.LOGGER.error("TF VFX: error en el efecto, se quita", t);
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            RUNNING.clear();
            HIDDEN.clear();
        }
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (event.getEntity() == VfxActor.rendering) return; // es el efecto de kill dibujándola
        if (!HIDDEN.isEmpty() && HIDDEN.containsKey(event.getEntity().getId())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || RUNNING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        float partial = event.getPartialTick();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        try {
            for (Running r : RUNNING) {
                for (Instance inst : r.instances) {
                    if (!inst.alive) continue;
                    inst.render(r, mc.level, pose, buffers, cam, partial);
                }
            }
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF VFX: error al dibujar", t);
            RUNNING.clear();
        } finally {
            buffers.endBatch();
        }
    }

    // ------------------------------------------------------------------------------------------- efectos

    /** Un efecto en marcha: su línea de tiempo y los modelos que ha sacado. */
    private static final class Running {
        final VfxNet.Play msg;
        final JsonArray events;
        final int duration;
        final List<Instance> instances = new ArrayList<>();
        final List<Repeater> repeaters = new ArrayList<>();
        final RandomSource random;
        final ResourceLocation skin;
        /** La víctima (si el cliente la tiene): en los efectos de kill hace ella la animación. */
        final net.minecraft.world.entity.LivingEntity actor;
        int age = -1;
        int next;

        Running(JsonObject def, VfxNet.Play msg) {
            this.msg = msg;
            this.events = def.getAsJsonArray("ev");
            this.duration = def.has("dur") ? def.get("dur").getAsInt() : 40;
            this.random = RandomSource.create(msg.seed());
            this.skin = skinOf(msg.skin());
            ClientLevel level = Minecraft.getInstance().level;
            Entity victim = msg.victim() >= 0 && level != null ? level.getEntity(msg.victim()) : null;
            this.actor = victim instanceof net.minecraft.world.entity.LivingEntity living ? living : null;
        }

        /** Avanza un tick. false = terminó. */
        boolean tick(ClientLevel level) {
            age++;
            for (Instance inst : instances) inst.tick(this, level);
            while (next < events.size()) {
                JsonObject ev = events.get(next).getAsJsonObject();
                if (ev.get("t").getAsInt() > age) break;
                next++;
                start(level, ev);
            }
            Iterator<Repeater> it = repeaters.iterator();
            while (it.hasNext()) {
                Repeater rep = it.next();
                if (rep.nextAge == age) {
                    rep.count++;
                    spawnParticles(level, rep.ev, rep.owner, rep.count);
                    rep.left--;
                    rep.nextAge += rep.every;
                }
                if (rep.left <= 0) it.remove();
            }
            boolean anyAlive = instances.stream().anyMatch(i -> i.alive);
            return age < duration || anyAlive || !repeaters.isEmpty();
        }

        void start(ClientLevel level, JsonObject ev) {
            start(level, ev, null);
        }

        /** Lanza un evento; owner = el modelo que lo lanza (temporizadores del modelo), para sus huesos y su posición. */
        void start(ClientLevel level, JsonObject ev, Instance owner) {
            if (ev.has("model")) {
                VfxModel m = model(ev.get("model").getAsString());
                if (m != null) instances.add(new Instance(this, m, ev, level));
            } else if (ev.has("sound")) {
                Vec3 at = owner != null ? owner.origin : anchor(level, ev);
                float vol = ev.has("vol") ? ev.get("vol").getAsFloat() : 1F;
                float pitch = 1F;
                if (ev.has("pitch") && ev.get("pitch").isJsonArray()) {
                    JsonArray pr = ev.getAsJsonArray("pitch");
                    float lo = pr.get(0).getAsFloat(), hi = pr.get(1).getAsFloat();
                    pitch = lo + random.nextFloat() * (hi - lo);
                } else if (ev.has("pitch")) {
                    pitch = ev.get("pitch").getAsFloat();
                }
                ResourceLocation rl = ResourceLocation.tryParse(ev.get("sound").getAsString());
                if (rl != null) {
                    Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(rl, SoundSource.PLAYERS, vol, pitch,
                            RandomSource.create(random.nextLong()), false, 0, SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false));
                }
            } else if (ev.has("part") && ev.has("orbit")) {
                int ticks = Math.max(1, ev.has("ticks") ? ev.get("ticks").getAsInt() : 20);
                Repeater orbit = new Repeater(ev, ticks, 1, age + 1, owner);
                spawnParticles(level, ev, owner, 0);
                repeaters.add(orbit);
            } else if (ev.has("part")) {
                spawnParticles(level, ev, owner, 0);
                int rep = ev.has("rep") ? ev.get("rep").getAsInt() : 0;
                if (rep > 0) {
                    int every = Math.max(1, ev.has("every") ? ev.get("every").getAsInt() : 1);
                    repeaters.add(new Repeater(ev, rep, every, age + every, owner));
                }
            } else if (ev.has("hide")) {
                int id = "victim".equals(ev.get("hide").getAsString()) ? msg.victim() : msg.caster();
                if (id >= 0) HIDDEN.merge(id, ev.has("ticks") ? ev.get("ticks").getAsInt() : 20, Math::max);
            } else if (ev.has("state")) {
                String key = ev.has("inst") ? ev.get("inst").getAsString() : null;
                for (Instance inst : instances) {
                    if (key == null || key.equals(inst.key)) inst.setAnim(ev.get("state").getAsString(),
                            ev.has("speed") ? ev.get("speed").getAsFloat() : 1F);
                }
            }
        }

        /** Punto de referencia del efecto: el origen, o quien lanza / la víctima si sigue ahí. */
        Vec3 anchor(ClientLevel level, JsonObject ev) {
            Vec3 base = new Vec3(msg.x(), msg.y(), msg.z());
            String at = ev.has("at") ? ev.get("at").getAsString() : "origin";
            int id = at.equals("caster") ? msg.caster() : at.equals("victim") ? msg.victim() : -1;
            if (id >= 0) {
                Entity e = level.getEntity(id);
                if (e != null) base = e.position();
            }
            return base.add(offset(ev, msg.yaw()));
        }

        void spawnParticles(ClientLevel level, JsonObject ev, Instance owner, int step) {
            ParticleOptions opts = particle(ev);
            if (opts == null) return;
            if (ev.has("orbit")) {
                // Punto que da vueltas alrededor de quien lanza (orbital de MythicMobs)
                Vec3 c = anchor(level, ev);
                double period = Math.max(1, num(ev, "period", 20));
                double a = Math.PI * 2 * (num(ev, "start", 0) + step) / period, r = num(ev, "orbit", 1);
                Vec3 at = c.add(Math.cos(a) * r, num(ev, "oy", 0), Math.sin(a) * r);
                burst(level, opts, at, Math.max(1, (int) num(ev, "n", 1)), num(ev, "hs", 0), num(ev, "vs", 0), num(ev, "sp", 0));
                return;
            }
            Vec3 at;
            String bone = ev.has("bone") ? ev.get("bone").getAsString() : null;
            Vec3 boneAt = null;
            if (bone != null) {
                if (owner != null) {
                    int b = owner.model.bone(bone);
                    if (b >= 0 && owner.alive) boneAt = owner.boneWorld(this, level, b, 1F);
                } else {
                    boneAt = bonePosition(level, ev.has("inst") ? ev.get("inst").getAsString() : null, bone);
                }
                if (boneAt == null) return; // el hueso ya no está (el modelo se quitó)
            }
            Vec3 base = owner != null && bone == null ? owner.origin : null;
            at = boneAt != null ? boneAt.add(0, num(ev, "y", 0), 0)
                    : base != null ? base.add(0, num(ev, "y", 0), 0) : anchor(level, ev).add(0, num(ev, "y", 0), 0);
            double sphere = num(ev, "sphere", 0);
            if (sphere > 0) {
                // Esfera de partículas (particlesphere de MythicMobs): puntos repartidos por la superficie
                int count = Math.max(1, (int) num(ev, "n", 10));
                for (int i = 0; i < count; i++) {
                    double u = random.nextDouble() * 2 - 1, a = random.nextDouble() * Math.PI * 2;
                    double rr = Math.sqrt(1 - u * u);
                    level.addParticle(opts, true, at.x + rr * Math.cos(a) * sphere, at.y + u * sphere, at.z + rr * Math.sin(a) * sphere, 0, 0, 0);
                }
                return;
            }
            int n = Math.max(0, (int) num(ev, "n", 10));
            double hs = num(ev, "hs", 0), vs = num(ev, "vs", 0), sp = num(ev, "sp", 0);
            double ring = num(ev, "ring", 0);
            int points = (int) num(ev, "points", 0);
            if (ring > 0 && points > 0) {
                // Anillo de partículas alrededor del punto (particlering de MythicMobs)
                for (int p = 0; p < points; p++) {
                    double a = Math.PI * 2 * p / points;
                    Vec3 c = at.add(Math.cos(a) * ring, 0, Math.sin(a) * ring);
                    burst(level, opts, c, Math.max(1, n), hs, vs, sp);
                }
                return;
            }
            if (n == 0) {
                level.addParticle(opts, at.x, at.y, at.z, hs * sp, vs * sp, hs * sp);
                return;
            }
            burst(level, opts, at, n, hs, vs, sp);
        }

        /** Igual que hace Minecraft con las partículas que manda el servidor: desvío y velocidad gaussianos. */
        void burst(ClientLevel level, ParticleOptions opts, Vec3 at, int n, double hs, double vs, double sp) {
            for (int i = 0; i < n; i++) {
                double x = at.x + random.nextGaussian() * hs;
                double y = at.y + random.nextGaussian() * vs;
                double z = at.z + random.nextGaussian() * hs;
                level.addParticle(opts, true, x, y, z, random.nextGaussian() * sp, random.nextGaussian() * sp,
                        random.nextGaussian() * sp);
            }
        }

        Vec3 bonePosition(ClientLevel level, String key, String bone) {
            for (int i = instances.size() - 1; i >= 0; i--) {
                Instance inst = instances.get(i);
                if (!inst.alive || (key != null && !key.equals(inst.key))) continue;
                int b = inst.model.bone(bone);
                if (b >= 0) return inst.boneWorld(this, level, b, 1F);
            }
            return null;
        }
    }

    private static final class Repeater {
        final JsonObject ev;
        int left;
        final int every;
        int nextAge;
        int count;
        final Instance owner;

        Repeater(JsonObject ev, int left, int every, int nextAge, Instance owner) {
            this.ev = ev;
            this.left = left;
            this.every = every;
            this.nextAge = nextAge;
            this.owner = owner;
        }
    }

    /** Un modelo animado dentro de un efecto. */
    private static final class Instance {
        final VfxModel model;
        final String key;
        final int spawnAge;
        final int life;
        final boolean useSkin;
        final double hop;
        final float extraYaw;
        /** A quién sigue el modelo: 0 nadie, 1 quien lanza, 2 la víctima. */
        final int follow;
        final boolean followYaw;
        final boolean usePitch;
        final float scale;
        /** El mob de verdad hace de muñeco (efectos de kill). */
        final boolean actorMode;
        final JsonObject ev;
        final boolean[] visible;
        final VfxModel[] source;
        final List<JsonObject> swaps = new ArrayList<>();
        final List<JsonObject> vis = new ArrayList<>();
        final List<JsonObject> states = new ArrayList<>();
        final List<JsonObject> timers = new ArrayList<>();
        final List<JsonObject> tints = new ArrayList<>();
        final List<JsonObject> vels = new ArrayList<>();
        /** Color que multiplica la textura (tint de ModelEngine). */
        float tr = 1F, tg = 1F, tb = 1F;
        /** Movimiento (proyectiles): bloques por tick hacia delante, caída por tick y desplazamiento acumulado. */
        double vel;
        final double grav;
        final boolean aim;
        Vec3 travel = Vec3.ZERO;
        double fall;
        final Matrix4f[] bones;
        VfxModel.Anim anim;
        float speed;
        int animStart;
        int age;
        boolean alive = true;
        Vec3 origin;
        Vec3 prevOrigin;
        float yaw;
        float prevYaw;

        Instance(Running r, VfxModel model, JsonObject ev, ClientLevel level) {
            this.model = model;
            this.key = ev.has("key") ? ev.get("key").getAsString() : ev.get("model").getAsString();
            this.spawnAge = r.age;
            this.life = ev.has("life") ? ev.get("life").getAsInt() : 40;
            this.useSkin = ev.has("skin") && ev.get("skin").getAsBoolean();
            this.hop = num(ev, "hop", 0);
            this.extraYaw = (float) num(ev, "yaw", 0);
            String f = ev.has("follow") ? ev.get("follow").getAsString() : "";
            this.follow = f.equals("caster") ? 1 : f.equals("victim") ? 2 : 0;
            this.followYaw = ev.has("followYaw") && ev.get("followYaw").getAsBoolean();
            this.usePitch = ev.has("pitch") && ev.get("pitch").getAsBoolean();
            boolean wantsActor = ev.has("actor") && ev.get("actor").getAsBoolean();
            this.actorMode = wantsActor && model.figures != null && r.actor != null;
            float s = (float) num(ev, "scale", 1);
            if (actorMode) {
                // El efecto a la medida del mob: más pequeño con una gallina, más grande con un ghast
                s *= Math.max(0.6F, Math.min(3.0F, r.actor.getBbHeight() / 1.8F));
            }
            this.scale = s;
            this.ev = ev;
            this.visible = new boolean[model.bones.length];
            this.source = new VfxModel[model.bones.length];
            for (int i = 0; i < visible.length; i++) {
                visible[i] = !model.bones[i].hidden;
                source[i] = model;
            }
            if (ev.has("swap")) ev.getAsJsonArray("swap").forEach(e -> swaps.add(e.getAsJsonObject()));
            if (ev.has("vis")) ev.getAsJsonArray("vis").forEach(e -> vis.add(e.getAsJsonObject()));
            if (ev.has("states")) ev.getAsJsonArray("states").forEach(e -> states.add(e.getAsJsonObject()));
            if (ev.has("timers")) ev.getAsJsonArray("timers").forEach(e -> timers.add(e.getAsJsonObject()));
            if (ev.has("tints")) ev.getAsJsonArray("tints").forEach(e -> tints.add(e.getAsJsonObject()));
            if (ev.has("vels")) ev.getAsJsonArray("vels").forEach(e -> vels.add(e.getAsJsonObject()));
            this.vel = num(ev, "vel", 0);
            this.grav = num(ev, "grav", 0);
            this.aim = ev.has("aim") && ev.get("aim").getAsBoolean();
            this.bones = new Matrix4f[model.bones.length];
            for (int i = 0; i < bones.length; i++) bones[i] = new Matrix4f();
            setAnim(ev.has("anim") ? ev.get("anim").getAsString() : null, (float) num(ev, "speed", 1));
            this.yaw = r.msg.yaw() + extraYaw;
            this.prevYaw = yaw;
            this.origin = place(r, level);
            this.prevOrigin = origin;
        }

        void setAnim(String name, float speed) {
            this.anim = name == null ? null : model.anims.get(name);
            this.speed = speed;
            this.animStart = age;
        }

        Vec3 place(Running r, ClientLevel level) {
            Vec3 base = new Vec3(r.msg.x(), r.msg.y(), r.msg.z());
            int id = follow == 1 ? r.msg.caster() : follow == 2 ? r.msg.victim() : -1;
            if (id >= 0) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    base = e.position();
                    if (followYaw) yaw = e.getYRot() + extraYaw;
                }
            }
            Vec3 p = base.add(offset(ev, followYaw ? yaw - extraYaw : r.msg.yaw())).add(travel);
            if (hop > 0) {
                // El cuerpo da un pequeño salto hacia delante (leap de MythicMobs) en los primeros 10 ticks
                double s = Math.min(1.0, age / 10.0);
                double rad = Math.toRadians(r.msg.yaw());
                p = p.add(-Math.sin(rad) * hop * s, 4 * 0.35 * s * (1 - s), Math.cos(rad) * hop * s);
            }
            return p;
        }

        void tick(Running r, ClientLevel level) {
            if (!alive) return;
            age++;
            if (age > life) {
                alive = false;
                return;
            }
            for (JsonObject s : swaps) {
                if (s.get("t").getAsInt() == age) {
                    int b = model.bone(s.get("bone").getAsString());
                    VfxModel other = model(s.get("model").getAsString());
                    if (b >= 0 && other != null) source[b] = other;
                }
            }
            for (JsonObject v : vis) {
                if (v.get("t").getAsInt() == age) {
                    int b = model.bone(v.get("bone").getAsString());
                    if (b >= 0) setVisible(b, v.has("show") && v.get("show").getAsBoolean(), !v.has("children") || v.get("children").getAsBoolean());
                }
            }
            for (JsonObject st : states) {
                if (st.get("t").getAsInt() == age) setAnim(st.get("anim").getAsString(), (float) num(st, "speed", 1));
            }
            for (JsonObject tn : tints) {
                if (tn.get("t").getAsInt() == age) {
                    Vector3f c = color(tn, "c");
                    tr = c.x;
                    tg = c.y;
                    tb = c.z;
                }
            }
            for (JsonObject vl : vels) if (vl.get("t").getAsInt() == age) vel = num(vl, "vel", 0);
            if (vel != 0 || grav != 0 || !vels.isEmpty()) {
                double yawRad = Math.toRadians(r.msg.yaw()), pitchRad = aim ? Math.toRadians(r.msg.pitch()) : 0;
                double cp = Math.cos(pitchRad);
                fall += grav;
                travel = travel.add(-Math.sin(yawRad) * cp * vel, -Math.sin(pitchRad) * vel - fall, Math.cos(yawRad) * cp * vel);
            }
            prevOrigin = origin;
            prevYaw = yaw;
            origin = place(r, level);
            for (JsonObject tm : timers) {
                int every = Math.max(1, tm.get("every").getAsInt());
                if (age % every == 0) r.start(level, tm.getAsJsonObject("ev"), this);
            }
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

        float seconds(float partial) {
            return (age - animStart + partial) / 20F * speed;
        }

        Vec3 lerpOrigin(float partial) {
            return new Vec3(prevOrigin.x + (origin.x - prevOrigin.x) * partial, prevOrigin.y + (origin.y - prevOrigin.y) * partial,
                    prevOrigin.z + (origin.z - prevOrigin.z) * partial);
        }

        Matrix4f transform(Vec3 at, float partial) {
            float y = prevYaw + (yaw - prevYaw) * partial;
            Matrix4f m = new Matrix4f().translate((float) at.x, (float) at.y, (float) at.z);
            m.rotate(Axis.YP.rotationDegrees(180F - y));
            m.scale(scale / 16F);
            return m;
        }

        Vec3 boneWorld(Running r, ClientLevel level, int b, float partial) {
            VfxPose.compute(model, anim, seconds(partial), usePitch ? r.msg.pitch() : 0F, bones);
            Matrix4f m = transform(lerpOrigin(partial), partial).mul(bones[b]);
            Vector4f p = m.transform(new Vector4f(0, 0, 0, 1));
            return new Vec3(p.x, p.y, p.z);
        }

        void render(Running r, ClientLevel level, PoseStack pose, MultiBufferSource buffers, Vec3 cam, float partial) {
            VfxPose.compute(model, anim, seconds(partial), usePitch ? r.msg.pitch() : 0F, bones);
            Vec3 at = lerpOrigin(partial).subtract(cam);
            Matrix4f inst = transform(at, partial);
            Matrix4f base = new Matrix4f(pose.last().pose()).mul(inst);
            Matrix3f normalView = new Matrix3f(pose.last().normal());
            Matrix3f normalBase = new Matrix3f(normalView).mul(new Matrix3f(inst));
            ResourceLocation skin = useSkin ? r.skin : null;
            if (actorMode) {
                float yy = prevYaw + (yaw - prevYaw) * partial;
                Matrix4f rotScale = new Matrix4f().rotate(Axis.YP.rotationDegrees(180F - yy)).scale(scale / 16F);
                VfxActor.render(r.actor, model, pose.last().pose(), pose.last().normal(), inst, rotScale, bones, visible, yy, partial,
                        buffers);
            }
            Matrix4f full = new Matrix4f();
            Matrix3f normal = new Matrix3f();
            Vector3f n = new Vector3f();
            Vector4f v = new Vector4f();
            // Por texturas: así cada una va en su lote y se ordena de atrás hacia delante
            Map<ResourceLocation, List<int[]>> byTex = new HashMap<>();
            for (int bi = 0; bi < model.bones.length; bi++) {
                if (!visible[bi]) continue;
                VfxModel src = source[bi];
                VfxModel.Bone bone = src == model ? model.bones[bi] : boneIn(src, model.bones[bi].id);
                if (bone == null || bone.quadCount == 0) continue;
                boolean skinHead = bone.phead && skin != null;
                boolean hasOwn = false;
                for (int q = 0; q < bone.quadCount; q++) if (bone.quads[q * VfxModel.QUAD] >= 0) hasOwn = true;
                for (int q = 0; q < bone.quadCount; q++) {
                    int ti = (int) bone.quads[q * VfxModel.QUAD];
                    if (ti == -3) continue; // el muñeco con reparto de skin: solo para las miniaturas de la web
                    // Con el mob de verdad haciendo la animación, el muñeco no se dibuja
                    if (actorMode && (((int) bone.quads[q * VfxModel.QUAD + 24]) & VfxModel.FLAG_ACTOR) != 0) continue;
                    ResourceLocation tex;
                    if (ti >= 0) {
                        if (skinHead) continue; // con skin, la cabeza va con la skin
                        tex = texture(src, ti);
                    } else if (skinHead) {
                        tex = skin;
                    } else if (!hasOwn && ti == -1) {
                        tex = DefaultPlayerSkin.getDefaultSkin(); // cabeza sin textura y sin skin: la de Steve
                    } else {
                        continue;
                    }
                    byTex.computeIfAbsent(tex, k -> new ArrayList<>()).add(new int[]{bi, q, ti});
                }
            }
            for (Map.Entry<ResourceLocation, List<int[]>> e : byTex.entrySet()) {
                VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentCull(e.getKey()));
                int lastBone = -1;
                for (int[] item : e.getValue()) {
                    int bi = item[0], q = item[1], ti = item[2];
                    VfxModel src = source[bi];
                    VfxModel.Bone bone = src == model ? model.bones[bi] : boneIn(src, model.bones[bi].id);
                    if (bi != lastBone) {
                        full.set(base).mul(bones[bi]);
                        normal.set(normalBase).mul(new Matrix3f(bones[bi]));
                        lastBone = bi;
                    }
                    int o = q * VfxModel.QUAD;
                    float frames = 1, frame = 0;
                    if (ti >= 0) {
                        VfxModel.Tex t = src.tex[ti];
                        if (t.frames() > 1) {
                            frames = t.frames();
                            frame = (age / t.frameTime()) % t.frames();
                        }
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
    }

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    private static ResourceLocation texture(VfxModel m, int ti) {
        return TEXTURES.computeIfAbsent(m.tex[ti].path(), ResourceLocation::new);
    }

    private static VfxModel.Bone boneIn(VfxModel m, String id) {
        int i = m.bone(id);
        return i < 0 ? null : m.bones[i];
    }

    // ------------------------------------------------------------------------------------------- ayudas

    /** Desplazamiento de un evento (delante, a la derecha, arriba) según el giro del lanzamiento. */
    static Vec3 offset(JsonObject ev, float yaw) {
        double fwd = num(ev, "fwd", 0), side = num(ev, "side", 0), up = num(ev, "up", 0);
        if (fwd == 0 && side == 0 && up == 0) return Vec3.ZERO;
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double rx = -Math.cos(rad), rz = -Math.sin(rad);
        return new Vec3(fx * fwd + rx * side, up, fz * fwd + rz * side);
    }

    static double num(JsonObject o, String key, double def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : def;
    }

    private static ResourceLocation skinOf(UUID uuid) {
        if (uuid == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return null;
        PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(uuid);
    }

    private static final Map<JsonObject, Optional<ParticleOptions>> PARTICLES = new java.util.IdentityHashMap<>();

    private static ParticleOptions particle(JsonObject ev) {
        return PARTICLES.computeIfAbsent(ev, VfxClient::parseParticle).orElse(null);
    }

    private static Optional<ParticleOptions> parseParticle(JsonObject ev) {
        try {
            String id = ev.get("part").getAsString();
            ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
            ParticleType<?> type = rl == null ? null : BuiltInRegistries.PARTICLE_TYPE.get(rl);
            if (type == null) return Optional.empty();
            float size = (float) num(ev, "size", 1);
            if (type == ParticleTypes.DUST) return Optional.of(new DustParticleOptions(color(ev, "color"), Math.max(0.01F, Math.min(4F, size))));
            if (type == ParticleTypes.DUST_COLOR_TRANSITION) {
                return Optional.of(new DustColorTransitionOptions(color(ev, "color"), color(ev, "color2"), Math.max(0.01F, Math.min(4F, size))));
            }
            if (type == ParticleTypes.BLOCK || type == ParticleTypes.FALLING_DUST) {
                ResourceLocation b = ResourceLocation.tryParse(ev.has("block") ? ev.get("block").getAsString() : "minecraft:stone");
                Block block = b == null ? null : ForgeRegistries.BLOCKS.getValue(b);
                if (block == null) return Optional.empty();
                @SuppressWarnings("unchecked")
                ParticleType<BlockParticleOption> bt = (ParticleType<BlockParticleOption>) type;
                return Optional.of(new BlockParticleOption(bt, block.defaultBlockState()));
            }
            return type instanceof SimpleParticleType simple ? Optional.of(simple) : Optional.empty();
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF VFX: partícula no válida {}", ev, e);
            return Optional.empty();
        }
    }

    private static Vector3f color(JsonObject ev, String key) {
        String hex = ev.has(key) ? ev.get(key).getAsString().replace("#", "") : "ffffff";
        int c;
        try {
            c = Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            c = 0xFFFFFF;
        }
        return new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F);
    }

    /** Quita los modelos y efectos cargados (al recargar recursos). */
    public static void clearCache() {
        MODELS.clear();
        fxDefs = null;
        PARTICLES.clear();
        TEXTURES.clear();
    }

    static Set<String> loadedModels() {
        return new HashSet<>(MODELS.keySet());
    }
}
