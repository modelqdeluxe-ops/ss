package net.tierrasfantasticas.tfclient.vacio;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * El Portal del Alba (1.3.96; el dueño: «después del bioma viene la isla principal de la grieta; para volver al santuario
 * debe haber un portalito»): el arco de cuarzo y oro junto a la plaza de la grieta (tools/void/isla_alba.py,
 * {@link VoidIsland#portalPos}). Quien cruza su velo de luz (lo dibuja el cliente, {@link SanctuaryFx}) ve un fundido
 * dorado ({@link VoidLightClient}) y a los {@link #DELAY} ticks está en el santuario, a la entrada, mirando al templo. De
 * ida no hay portal: al santuario se llega del mundo y a la grieta se va cruzando el Páramo.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class VoidPortal {
    /** Del paso por el velo al santuario (el fundido está en lo más blanco). */
    static final int DELAY = 14;
    private static final Map<UUID, Long> PENDING = new HashMap<>(), COOLDOWN = new HashMap<>();
    /** Para la prueba en el juego: viajes hechos. */
    static int trips;

    private VoidPortal() {}

    /** ¿Está ese jugador en el velo del portal? (5 de ancho, 8 de alto, en el plano del arco) */
    static boolean inside(double x, double y, double z) {
        BlockPos p = VoidIsland.portalPos();
        if (p == null) return false;
        boolean alongX = VoidIsland.portalFacing() % 2 == 0;
        double across = alongX ? x - (p.getX() + 0.5) : z - (p.getZ() + 0.5);
        double through = alongX ? z - (p.getZ() + 0.5) : x - (p.getX() + 0.5);
        return Math.abs(across) <= 2.5 && Math.abs(through) <= 0.7 && y >= p.getY() - 0.5 && y <= p.getY() + 7.5;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerLevel level = event.getServer().getLevel(TFVoid.VOID);
        if (level == null || VoidIsland.portalPos() == null) return;
        long now = level.getGameTime();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            UUID id = p.getUUID();
            Long at = PENDING.get(id);
            if (at != null) {
                if (now >= at) {
                    PENDING.remove(id);
                    COOLDOWN.put(id, now + 80);
                    go(level, p);
                }
                continue;
            }
            Long cool = COOLDOWN.get(id);
            if (cool != null && now < cool) continue;
            if (!inside(p.getX(), p.getY(), p.getZ()) || !VoidTravel.idle(p)) continue;
            PENDING.put(id, now + DELAY);
            VoidLight.event(p, VoidLight.EV_PORTAL);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1F, 1.3F);
            level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 1.0, 0.6, 0.08);
        }
    }

    /** Al santuario, a la entrada, mirando al templo. */
    static void go(ServerLevel level, ServerPlayer p) {
        Vec3 a = VoidIsland.arrivePos();
        p.teleportTo(level, a.x, a.y, a.z, VoidIsland.arriveYaw(), 0F);
        p.fallDistance = 0;
        level.playSound(null, a.x, a.y, a.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.5F);
        level.sendParticles(ParticleTypes.END_ROD, a.x, a.y + 1, a.z, 50, 0.5, 1.0, 0.5, 0.1);
        trips++;
    }
}
