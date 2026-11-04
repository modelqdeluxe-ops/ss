/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.bosses;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.items.TFMaceClient;
import net.tierrasfantasticas.tfclient.server.TFBotPlayer;

/**
 * (1.3.173, el dueño: «la maza no hace ni tiene el sonido ni la animación de la maza vanilla») Prueba «maza»: los
 * sonidos de la 1.21 bajados de Mojang (están, Minecraft los encuentra y cada golpe pide el suyo) y la explosión de polvo
 * del golpe aplastante: un jugador bot cae con un martillo sobre un gólem y la cámara (el jugador de la prueba) lo mira
 * de lado.
 */
final class BossTestMaza {
    private BossTestMaza() {}

    private static ServerPlayer smasher;
    private static Mob golem;
    private static BlockPos g;

    /** El bot, cayendo desde fall bloques sobre el gólem, le pega (golpe aplastante). */
    private static void smash(ServerLevel level, float fall) {
        if (smasher == null || golem == null) return;
        golem.invulnerableTime = 0;
        golem.setHealth(golem.getMaxHealth());
        smasher.teleportTo(level, golem.getX() + 0.6, golem.getY() + golem.getBbHeight() + 0.3, golem.getZ(), 90F, 60F);
        smasher.setOnGround(false);
        smasher.fallDistance = fall;
        smasher.attack(golem);
    }

    static void step(int t, MinecraftServer server, ServerLevel level, ServerPlayer player, BossTest.Rep r) {
        Vec3 arena = BossTest.arena();
        int ax = (int) arena.x, az = (int) arena.z;
        switch (t) {
            case 1 -> {
                BossTest.command(server, "time set 6000");
                player.teleportTo(level, ax + 0.5, arena.y + 1, az + 0.5, 0F, 0F);
            }
            case 10 -> {
                g = new BlockPos(ax, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ax, az), az);
                // el suelo de piedra, para que el polvo se vea bien sobre la hierba
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        if (dx * dx + dz * dz <= 26) level.setBlockAndUpdate(g.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    }
                }
                golem = EntityType.IRON_GOLEM.create(level);
                if (golem != null) {
                    golem.moveTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 0F, 0F);
                    golem.setNoAi(true);
                    golem.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
                    level.addFreshEntity(golem);
                }
                smasher = TFBotPlayer.spawnTarget(server, level, "Martillero", g.getX() + 2.5, g.getY(), g.getZ() + 0.5);
                if (smasher != null) smasher.setItemInHand(InteractionHand.MAIN_HAND,
                        new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tfclient", "valentine_hammer"))));
                // la cámara: de lado, a 9 bloques, un poco desde arriba
                player.teleportTo(level, g.getX() + 0.5, g.getY() + 2.5, g.getZ() - 8.5, 0F, 14F);
                BossTest.hideHud = true;
                // los sonidos de la 1.21: bajados, y Minecraft los encuentra (en el paquete de recursos) y los da a sus eventos
                var mc = net.minecraft.client.Minecraft.getInstance();
                StringBuilder files = new StringBuilder();
                int found = 0;
                for (String f : new String[] {"item/mace/smash_ground1", "item/mace/smash_ground_heavy", "item/mace/smash_air1", "entity/wind_charge/wind_burst1"}) {
                    boolean ok = mc.getResourceManager().getResource(new ResourceLocation("minecraft", "sounds/" + f + ".ogg")).isPresent();
                    if (ok) found++;
                    files.append(f).append(ok ? " sí, " : " NO, ");
                }
                var ev = mc.getSoundManager().getSoundEvent(new ResourceLocation("tfclient", "maza.golpe_suelo"));
                r.event("maza 1 (los sonidos de la 1.21, bajados de Mojang): listos " + TFMaceClient.available + "; en los recursos: " + files
                        + "evento maza.golpe_suelo " + (ev != null ? "con sus sonidos" : "SIN SONIDOS")
                        + (TFMaceClient.available && found == 4 && ev != null ? " (bien)" : " (MAL)"));
            }
            case 70 -> smash(level, 3.0F);
            case 73 -> BossTest.shot(r, "maza_01_polvo_3_ticks");
            case 80 -> BossTest.shot(r, "maza_02_polvo_10_ticks");
            case 82 -> r.event("maza 2 (golpe aplastante, cayendo 3 bloques, el gólem en el suelo): el cliente recibió " + TFMaceClient.testSmashes
                    + " golpe(s), sonó «" + TFMaceClient.testSound + "», salieron " + TFMaceClient.testParticles + " partículas de polvo (la 1.21: 750)"
                    + (TFMaceClient.testSmashes == 1 && "maza.golpe_suelo".equals(TFMaceClient.testSound) && TFMaceClient.testParticles >= 700 ? " (bien)" : " (MAL)"));
            case 130 -> smash(level, 7.0F);
            case 133 -> BossTest.shot(r, "maza_03_polvo_fuerte");
            case 142 -> r.event("maza 3 (cayendo 7 bloques): sonó «" + TFMaceClient.testSound + "»"
                    + ("maza.golpe_suelo_fuerte".equals(TFMaceClient.testSound) ? " (bien: el fuerte)" : " (MAL)"));
            case 150 -> {
                // el gólem en el aire: el sonido «en el aire» y sin polvo
                if (golem != null) {
                    golem.setNoGravity(true);
                    golem.teleportTo(golem.getX(), golem.getY() + 4, golem.getZ());
                    golem.setOnGround(false);
                }
            }
            case 160 -> smash(level, 3.0F);
            case 170 -> {
                r.event("maza 4 (el gólem en el aire): sonó «" + TFMaceClient.testSound + "», polvo " + TFMaceClient.testParticles
                        + ("maza.golpe_aire".equals(TFMaceClient.testSound) && TFMaceClient.testParticles == 0 ? " (bien)" : " (MAL)"));
                if (golem != null) golem.discard();
                if (smasher != null) smasher.discard();
                BossTest.hideHud = false;
            }
            case 180 -> BossTest.next(level);
            default -> {
            }
        }
    }
}
