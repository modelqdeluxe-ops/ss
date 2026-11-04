/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.bosses;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.items.TFThrownTrident;
import net.tierrasfantasticas.tfclient.server.TFBotPlayer;

/**
 * (1.3.171, el dueño: «¿viste si todo ok con el modelo de la lanza al lanzar? que no sea demasiado grande, que no tenga
 * errores de tamaño») Prueba «lanzas»: en fila, de pie y con la punta a la misma altura, el tridente de Minecraft lanzado
 * y las lanzas lanzadas de los sets (las más grandes, las más pequeñas y las de escala rara), y al final un jugador
 * (1,8 bloques), para medir en las capturas su tamaño frente al tridente y al jugador. (1.3.172) Todas clavadas de pie
 * en el suelo (lanzadas hacia abajo): deben medir lo mismo que el tridente de Minecraft.
 */
final class BossTestLanzas {
    private BossTestLanzas() {}

    /** De izquierda a derecha (mirando al este, la z crece hacia la derecha). */
    static final String[] SPEARS = {"shadow_trident", "violet_trident", "darkloyal_trident", "void_trident", "ender_trident", "runemoth_trident",
            "dryadia_trident", "deepsea_trident", "valentine_trident"};
    private static final List<Entity> SPAWNED = new ArrayList<>();
    private static ServerPlayer holder;

    /** (1.3.172) Lanzada recta hacia abajo desde 6 bloques: se clava de pie en el suelo (la punta abajo). */
    private static void drop(Entity e, double x, double ground, double z) {
        // ya mirando abajo (xRot −90: el proyectil gira poco a poco hacia donde va y se clavaría a medio girar)
        e.moveTo(x, ground + 6, z, 0F, -90F);
        e.setDeltaMovement(new Vec3(0, -2.5, 0));
    }

    static void step(int t, MinecraftServer server, ServerLevel level, ServerPlayer player, BossTest.Rep r) {
        Vec3 arena = BossTest.arena();
        int ax = (int) arena.x, az = (int) arena.z;
        switch (t) {
            case 1 -> {
                BlockPos g = new BlockPos(ax, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ax, az), az);
                BossTest.command(server, "time set 6000");
                BossTest.hideHud = true;
                double ground = g.getY(), x = g.getX() + 0.5;
                double z = g.getZ() + 0.5 - 10.0;
                // el tridente de Minecraft
                ThrownTrident vanilla = EntityType.TRIDENT.create(level);
                if (vanilla != null) {
                    drop(vanilla, x, ground, z);
                    level.addFreshEntity(vanilla);
                    SPAWNED.add(vanilla);
                }
                StringBuilder order = new StringBuilder("tridente de Minecraft");
                for (String id : SPEARS) {
                    z += 2.0;
                    Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tfclient", id));
                    if (it == null || it == Items.AIR) continue;
                    TFThrownTrident s = new TFThrownTrident(level, player, new ItemStack(it));
                    drop(s, x, ground, z);
                    level.addFreshEntity(s);
                    SPAWNED.add(s);
                    order.append(", ").append(id.replace("_trident", ""));
                }
                // un jugador (1,8 bloques) con la de Valentine en la mano, al lado de la de Valentine lanzada
                holder = TFBotPlayer.spawnTarget(server, level, "Lancero", x, g.getY(), z + 1.6);
                if (holder != null) {
                    holder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tfclient", "valentine_trident"))));
                    holder.teleportTo(level, x, g.getY(), z + 1.6, 90F, 0F);
                    order.append(", jugador con la de Valentine en la mano");
                }
                r.event("lanzas 1 (de izquierda a derecha, clavadas de pie en el suelo): " + order);
                player.teleportTo(level, x - 12.0, g.getY(), g.getZ() + 0.5, -90F, 0F);
            }
            case 60 -> BossTest.shot(r, "lanzas_01_fila");
            case 62 -> {
                if (holder == null) return;
                // de cerca: la de Valentine lanzada y la misma en la mano
                player.teleportTo(level, holder.getX() - 4.5, holder.getY(), holder.getZ() - 0.8, -90F, -14F);
            }
            case 100 -> BossTest.shot(r, "lanzas_02_mano_y_lanzada");
            case 102 -> {
                SPAWNED.forEach(Entity::discard);
                SPAWNED.clear();
                if (holder != null) holder.discard();
                BossTest.hideHud = false;
            }
            case 110 -> BossTest.next(level);
            default -> {
            }
        }
    }
}
