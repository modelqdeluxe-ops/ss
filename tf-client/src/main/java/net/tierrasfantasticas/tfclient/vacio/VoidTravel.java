package net.tierrasfantasticas.tfclient.vacio;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.tierrasfantasticas.tfclient.TFClient;
import org.joml.Vector3f;

/**
 * El viaje al vacío y de vuelta, en el servidor. Al elegirlo, durante {@link #LEAVE_TICKS} la luz te rodea: dos
 * espirales de luz dorada y blanca suben a tu alrededor cerrándose, flotas despacio (Levitación), suenan el faro y la
 * amatista y la pantalla se va llenando de luz ({@link TravelFx}); en el último tick, chispas, el sonido del portal
 * del End, y apareces: en el santuario del vacío (en su puerta del este, mirando a la grieta del centro) o donde estabas antes
 * de ir. Al llegar, chispas que se abren y caes despacio. (1.3.89: sin el destello FLASH, una mancha redonda que el dueño
 * contaba entre los «círculos horribles».)
 *
 * <p>Dónde estabas se guarda en los datos del jugador (ForgeData → «TFVoid»: dimensión, x, y, z, giro), y también si ya
 * habló con el caballero (para que la segunda vez solo te salude por tu nombre).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class VoidTravel {
    public static final int LEAVE_TICKS = 64;
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1F, 0.8F, 0.3F), 1.4F);
    private static final DustParticleOptions LIGHT = new DustParticleOptions(new Vector3f(1F, 0.97F, 0.85F), 1.0F);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.62F, 0.3F, 1F), 1.3F);
    private static final Map<UUID, Trip> TRIPS = new HashMap<>();

    private VoidTravel() {}

    private static final class Trip {
        final boolean toVoid;
        int age;

        Trip(boolean toVoid) {
            this.toVoid = toVoid;
        }
    }

    /** ¿No está de viaje? */
    static boolean idle(ServerPlayer p) {
        return !TRIPS.containsKey(p.getUUID());
    }

    private static CompoundTag data(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains("TFVoid")) root.put("TFVoid", new CompoundTag());
        return root.getCompound("TFVoid");
    }

    /** Clic derecho en el caballero: abre su diálogo. */
    static void talk(ServerPlayer p, KnightEntity k) {
        CompoundTag d = data(p);
        String key = k.home() ? "MetHome" : "Met";
        boolean first = !d.getBoolean(key);
        d.putBoolean(key, true);
        String back = k.home() ? backName(p.server, d) : "";
        VoidNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new VoidNet.Talk(k.getId(), p.getGameProfile().getName(), first, k.home(), back));
        p.level().playSound(null, k.getX(), k.getY() + 2, k.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1F, 1.3F);
    }

    /** La prueba en el juego (BossTest): como un clic derecho en el caballero. */
    public static void testTalk(ServerPlayer p, KnightEntity k) {
        talk(p, k);
    }

    /** Lo que eligió en el diálogo. */
    static void choose(ServerPlayer p, int entity, int what) {
        Entity e = p.level().getEntity(entity);
        if (!(e instanceof KnightEntity k) || e.distanceToSqr(p) > 12 * 12 || !idle(p)) return;
        if (what == VoidNet.GO && !k.home() && !TFVoid.isVoid(p.level())) start(p, true);
        else if (what == VoidNet.BACK && k.home() && TFVoid.isVoid(p.level())) start(p, false);
    }

    /** /tf void ir|volver [jugadores] (staff). */
    static int adminSend(CommandSourceStack src, Collection<ServerPlayer> players, boolean toVoid) {
        int n = 0;
        for (ServerPlayer p : players) {
            if (!idle(p) || TFVoid.isVoid(p.level()) == toVoid) continue;
            start(p, toVoid);
            n++;
        }
        int sent = n;
        src.sendSuccess(() -> Component.literal((toVoid ? "Viajan al Void: " : "Vuelven del Void: ") + sent), true);
        return n;
    }

    private static void start(ServerPlayer p, boolean toVoid) {
        if (toVoid && p.server.getLevel(TFVoid.VOID) == null) {
            p.displayClientMessage(Component.literal("El Void no está en este servidor (¿falta TF Client en el servidor?).").withStyle(ChatFormatting.RED), false);
            return;
        }
        if (toVoid) {
            // Dónde estaba, para volver
            CompoundTag d = data(p);
            d.putString("Dim", p.level().dimension().location().toString());
            d.putDouble("X", p.getX());
            d.putDouble("Y", p.getY());
            d.putDouble("Z", p.getZ());
            d.putFloat("Yaw", p.getYRot());
            d.putFloat("Pitch", p.getXRot());
        }
        TRIPS.put(p.getUUID(), new Trip(toVoid));
        VoidNet.fx(p, VoidNet.FX_LEAVE);
        ServerLevel l = p.serverLevel();
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.25F);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1F, 0.8F);
        p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LEAVE_TICKS - 10, 0, false, false));
    }

    static void tick(MinecraftServer server) {
        if (TRIPS.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Trip>> it = TRIPS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Trip> en = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            Trip t = en.getValue();
            if (p == null || !p.isAlive()) {
                if (p != null) VoidNet.fx(p, VoidNet.FX_CANCEL);
                it.remove();
                continue;
            }
            t.age++;
            spiral(p, t.age);
            if (t.age == LEAVE_TICKS / 2) {
                p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 0.7F);
            }
            if (t.age >= LEAVE_TICKS) {
                it.remove();
                arrive(p, t.toVoid);
            }
        }
    }

    /** Partículas en un anillo alrededor del jugador (entre r0 y r1 bloques, a la altura del cuerpo). */
    private static void ring(ServerLevel l, ServerPlayer p, net.minecraft.core.particles.ParticleOptions type, int n, double r0, double r1) {
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = r0 + rnd.nextDouble() * (r1 - r0);
            l.sendParticles(type, p.getX() + Math.cos(a) * r, p.getY() + 0.2 + rnd.nextDouble() * 2.2, p.getZ() + Math.sin(a) * r,
                    1, 0, 0.02, 0, 0.01);
        }
    }

    /**
     * Dos espirales que suben a tu alrededor y se cierran un poco, y chispas en un anillo. (1.3.89: nada a menos de 1,3
     * bloques ni en el centro: las chispas que nacían en el jugador salían pegadas a la cámara, manchas blancas enormes.)
     */
    private static void spiral(ServerPlayer p, int age) {
        ServerLevel l = p.serverLevel();
        float k = age / (float) LEAVE_TICKS;
        double r = 2.1 - 0.8 * k;
        for (int s = 0; s < 2; s++) {
            double a = age * 0.42 + s * Math.PI;
            double y = p.getY() + ((age * 0.09) % 2.6);
            double x = p.getX() + Math.cos(a) * r, z = p.getZ() + Math.sin(a) * r;
            l.sendParticles(s == 0 ? GOLD : LIGHT, x, y, z, 1, 0, 0, 0, 0);
            l.sendParticles(ParticleTypes.END_ROD, x, y + 0.3, z, 1, 0.02, 0.05, 0.02, 0.01);
        }
        if (age % 3 == 0) ring(l, p, GOLD, 4, 1.6, 2.4);
        if (age > LEAVE_TICKS * 0.6) ring(l, p, ParticleTypes.END_ROD, 2, 1.4, 2.0);
        if (age == LEAVE_TICKS - 2) {
            ring(l, p, ParticleTypes.END_ROD, 24, 1.5, 2.2);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.45F, 1.4F);
        }
    }

    private static void arrive(ServerPlayer p, boolean toVoid) {
        MinecraftServer server = p.server;
        ServerLevel dest;
        Vec3 at;
        float yaw, pitch = 0F;
        if (toVoid) {
            dest = server.getLevel(TFVoid.VOID);
            if (dest == null) return;
            at = VoidIsland.arrivePos();
            yaw = VoidIsland.arriveYaw();
        } else {
            CompoundTag d = data(p);
            dest = null;
            if (d.contains("Dim")) {
                ResourceLocation id = ResourceLocation.tryParse(d.getString("Dim"));
                if (id != null) dest = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            }
            if (dest == null || dest.dimension() == TFVoid.VOID) {
                dest = server.overworld();
                BlockPos s = dest.getSharedSpawnPos();
                at = new Vec3(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
                yaw = 0F;
            } else {
                at = new Vec3(d.getDouble("X"), d.getDouble("Y"), d.getDouble("Z"));
                yaw = d.getFloat("Yaw");
                pitch = d.getFloat("Pitch");
            }
        }
        p.removeEffect(MobEffects.LEVITATION);
        p.stopRiding();
        p.teleportTo(dest, at.x, at.y, at.z, yaw, pitch);
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
        VoidNet.fx(p, VoidNet.FX_ARRIVE);
        ServerLevel l = p.serverLevel();
        ring(l, p, toVoid ? VIOLET : GOLD, 36, 1.6, 2.8);
        ring(l, p, ParticleTypes.END_ROD, 18, 1.5, 2.4);
        if (toVoid) ring(l, p, ParticleTypes.REVERSE_PORTAL, 40, 1.6, 3.0);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1F, 1.2F);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 1.0F);
        if (toVoid) {
            p.displayClientMessage(Component.literal("✦ The Void ✦").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
            TFVoid.ensureHomeKnight(l);
        }
    }

    /** Cómo se llama a donde volverías («Tierras Fantásticas · 120, 64, -30»), o "" si no hay. */
    private static String backName(MinecraftServer server, CompoundTag d) {
        if (!d.contains("Dim")) return "";
        String dim = d.getString("Dim");
        String name = switch (dim) {
            case "minecraft:overworld" -> "Tierras Fantásticas";
            case "minecraft:the_nether" -> "el Nether";
            case "minecraft:the_end" -> "el End";
            default -> dim;
        };
        return String.format("%s · %d, %d, %d", name, (int) Math.floor(d.getDouble("X")), (int) Math.floor(d.getDouble("Y")),
                (int) Math.floor(d.getDouble("Z")));
    }

    /** Al morir y reaparecer, lo del vacío (dónde estabas, si ya le conoces) sigue contigo. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.contains("TFVoid")) event.getEntity().getPersistentData().put("TFVoid", old.getCompound("TFVoid").copy());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (TRIPS.remove(event.getEntity().getUUID()) != null && event.getEntity() instanceof ServerPlayer p) {
            p.removeEffect(MobEffects.LEVITATION);
        }
    }

    static boolean inVoid(Level l) {
        return TFVoid.isVoid(l);
    }
}
