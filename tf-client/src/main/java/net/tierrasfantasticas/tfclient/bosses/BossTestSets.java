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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.items.TFItemTypes;

/**
 * (1.3.162) Prueba «sets», corta: las 5 crates nuevas del dueño (Dríada, Primavera, Draco Imperial, Maldición Oni y
 * Reliquias del Abismo; el dueño: «no quiero errores, ningún error de textura, las alas deben colocarse bien en la espalda,
 * altura correcta»). Por cada set: cuántos objetos hay y si todos tienen modelo y textura que cargue el juego, y el
 * jugador con las alas puestas (en el pecho), el casco y un arma, de espaldas y de frente, y con la armadura entera.
 */
final class BossTestSets {
    private BossTestSets() {}

    // (1.3.163) las 5 siguientes; las de la 1.3.162 eran dryadia, spring, drakegoat, onicurse y deepsea
    static final String[] SETS = {"dragonstone", "leviathan", "aceruin", "violet", "runemoth"};
    private static final int EACH = 110;

    private static Item item(String set, String slug) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tfclient", set + "_" + slug));
        return it == null || it == net.minecraft.world.item.Items.AIR ? null : it;
    }

    private static Item first(String set, String... slugs) {
        for (String s : slugs) {
            Item it = item(set, s);
            if (it != null) return it;
        }
        return null;
    }

    private static String key(Item it) {
        String path = ForgeRegistries.ITEMS.getKey(it).getPath();
        var set = TFItemTypes.setOf(it);
        return set == null ? "" : set.id() + "/" + path.substring(set.id().length() + 1);
    }

    private static void undress(ServerPlayer player) {
        player.getInventory().clearContent();
        for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            player.setItemSlot(s, ItemStack.EMPTY);
        }
    }

    static void step(int t, MinecraftServer server, ServerLevel level, ServerPlayer player, BossTest.Rep r) {
        Vec3 arena = BossTest.arena();
        int ax = (int) arena.x, az = (int) arena.z;
        if (t == 1) {
            BlockPos g = new BlockPos(ax, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ax, az), az);
            player.teleportTo(level, g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 0F, 8F);
            BossTest.command(server, "time set 6000");
            BossTest.hideHud = true;
            return;
        }
        int k = (t - 10) / EACH, at = (t - 10) % EACH;
        if (t >= 10 && k < SETS.length) {
            String set = SETS[k];
            switch (at) {
                case 0 -> {
                    undress(player);
                    // cuántos objetos tiene el set y cuáles no están registrados
                    int n = 0;
                    StringBuilder slugs = new StringBuilder();
                    for (Item it : ForgeRegistries.ITEMS) {
                        var s = TFItemTypes.setOf(it);
                        if (s != null && s.id().equals(set)) {
                            n++;
                            slugs.append(ForgeRegistries.ITEMS.getKey(it).getPath().substring(set.length() + 1)).append(' ');
                        }
                    }
                    Item chest = item(set, "armor_chestplate"), legs = item(set, "armor_leggings"), feet = item(set, "armor_boots");
                    Item head = first(set, "hat", "helmet", "crown", "armor_helmet");
                    Item wings = first(set, "wings", "wing", "backpack");
                    Item weapon = first(set, "sword", "big_sword", "great_sword", "battle_axe");
                    boolean worn = wings instanceof TFItemTypes.Cosmetic c && c.wornModel() != null;
                    r.event("set " + set + ": " + n + " objetos (" + slugs.toString().trim() + "); armadura " + (chest != null && legs != null && feet != null)
                            + ", cabeza " + (head == null ? "-" : key(head)) + ", alas " + (wings == null ? "-" : key(wings)) + " (modelo puesto " + worn
                            + "), arma " + (weapon == null ? "-" : key(weapon))
                            + (n >= 16 && chest != null && head != null && wings != null && worn && weapon != null ? " (bien)" : " (MAL)"));
                    // 1: las alas puestas (en el pecho, como las lleva un jugador), el casco del set, las grebas, las botas y el arma
                    if (wings != null) player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(wings));
                    if (legs != null) player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(legs));
                    if (feet != null) player.setItemSlot(EquipmentSlot.FEET, new ItemStack(feet));
                    if (head != null) player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(head));
                    if (weapon != null) player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(weapon));
                    BossTest.camera = "espalda";
                    BossTest.fov = 34;
                }
                case 30 -> BossTest.shot(r, "sets_" + (k + 1) + "_" + set + "_1_alas_espalda");
                case 32 -> BossTest.camera = "frente";
                case 60 -> BossTest.shot(r, "sets_" + (k + 1) + "_" + set + "_2_alas_frente");
                case 62 -> {
                    // 2: la armadura entera (peto y casco de armadura)
                    Item chest = item(set, "armor_chestplate"), helm = item(set, "armor_helmet");
                    if (chest != null) player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));
                    if (helm != null) player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helm));
                }
                case 90 -> BossTest.shot(r, "sets_" + (k + 1) + "_" + set + "_3_armadura_frente");
                case 92 -> {
                    // 4: todos los objetos del set en el inventario (que ninguno salga como el cubo morado y negro)
                    int slot = 9;
                    for (Item it : ForgeRegistries.ITEMS) {
                        var st = TFItemTypes.setOf(it);
                        if (st != null && st.id().equals(set) && slot < 36) player.getInventory().setItem(slot++, new ItemStack(it));
                    }
                    BossTest.openInventory = true;
                }
                case 104 -> BossTest.shot(r, "sets_" + (k + 1) + "_" + set + "_4_inventario");
                case 106 -> BossTest.closeScreen = true;
                default -> { }
            }
            return;
        }
        if (k == SETS.length && at == 0) {
            undress(player);
            BossTest.camera = "primera";
            BossTest.fov = 0;
            BossTest.hideHud = false;
        }
        if (k == SETS.length && at == 10) BossTest.next(level);
    }
}
