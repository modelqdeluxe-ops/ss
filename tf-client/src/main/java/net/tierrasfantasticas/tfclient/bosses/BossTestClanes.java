/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.bosses;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.data.GlobalFlags;
import net.tierrasfantasticas.tfclient.pad.TFPadClient;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.pad.server.PadClanBank;
import net.tierrasfantasticas.tfclient.pad.server.PadClanDuels;
import net.tierrasfantasticas.tfclient.pad.server.PadClanProgress;
import net.tierrasfantasticas.tfclient.pad.server.PadClanWar;
import net.tierrasfantasticas.tfclient.pad.server.PadClans;
import net.tierrasfantasticas.tfclient.pad.server.PadGreen;

/**
 * (1.3.156) La prueba en el juego de los clanes ([prueba-jefes=clanes]): la economía de las Clan Coins (1–2 por
 * recompensa), las misiones, el potenciador, las alianzas, el banco, la tienda del clan, las protecciones del clan y su
 * territorio, el límite de protecciones de la tienda del juego, la guerra con la barra vertical doble, los duelos, la
 * paleta tipo Paint, el ranking de clanes y el cofre del banco; con una captura de cada pantalla.
 */
final class BossTestClanes {
    private BossTestClanes() {}

    private static final String A = "prueba01", B = "prueba02";
    private static final UUID B0 = UUID.nameUUIDFromBytes("tf-prueba-rival".getBytes()), B1 = UUID.nameUUIDFromBytes("tf-prueba-rival-1".getBytes()),
            B2 = UUID.nameUUIDFromBytes("tf-prueba-rival-2".getBytes()), A_MEMBER = UUID.nameUUIDFromBytes("tf-prueba-c".getBytes()),
            NOBODY = UUID.nameUUIDFromBytes("tf-prueba-sin-clan".getBytes());
    private static BlockPos stonePos, chestPos;
    private static long greenBefore;
    private static long[] before;

    private static FakePlayer fake(ServerLevel level, UUID id, String name, ServerPlayer near) {
        FakePlayer f = FakePlayerFactory.get(level, new GameProfile(id, name));
        f.moveTo(near.getX() + 1, near.getY(), near.getZ(), 0F, 0F);
        return f;
    }

    private static String act(ServerPlayer p, String tab, String action) {
        return PadClans.testAction(p, tab, action, "");
    }

    private static int countItem(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.getItem() == item) n += s.getCount();
        return n;
    }

    static void step(int t, MinecraftServer server, ServerLevel level, ServerPlayer player, BossTest.Rep r) {
        Vec3 arena = BossTest.arena();
        UUID me = player.getUUID();
        switch (t) {
            case 1 -> {
                player.teleportTo(level, arena.x, arena.y, arena.z, 0F, 0F);
                PadClans.testRanks(player);
                PadClans.testName(B0, "Rival_Prueba");
                PadClans.testName(B1, "Bea_Rival");
                PadClans.testName(B2, "Bruno_Rival");
                PadClans.testClan(B, "Clan Rival", "RIV", B0, List.of(B1, B2), "#E04848");
                PadClanProgress.testSet(A, 0, 1, 0, 0);
                PadClanProgress.testSet(B, 0, 1, 0, 0);
                PadClanBank.testReset(A);
                PadClanWar.testReset(A, B);
                PadClanDuels.testClear();
                List<String> bad = PadClanProgress.testValidate(server);
                r.event("clanes 1 (objetivos de las misiones y de las guerras, con algo de verdad del juego): "
                        + (bad.isEmpty() ? "todos cuentan (bien)" : "NO cuentan " + bad + " (MAL)"));
                long got = PadClanProgress.testLevelTo(server, A, 5);
                r.event("clanes 2 (subir del nivel 1 al 5): +" + got + " Clan Coins" + (got == 5 ? " (bien: 1 por nivel y 2 en el 5)" : " (MAL: tenían que ser 5)"));
                PadClanProgress.testSet(A, 0, 5, 0, 0);
            }
            case 5 -> {
                greenBefore = PadGreen.balance(me);
                before = PadClanProgress.testStats(A);
                String id = PadClanProgress.testCompleteMission(player);
                long[] after = PadClanProgress.testStats(A);
                long coins = after[0] - before[0], green = PadGreen.balance(me) - greenBefore;
                r.event("clanes 3 (misión del clan «" + id + "» cumplida): Clan Coins +" + coins + ", prestigio +" + (after[3] - before[3]) + ", XP del clan "
                        + before[2] + " → " + after[2] + " (nivel " + after[1] + "), verdes +" + green
                        + (coins >= 1 && coins <= 2 && green == 1 && after[3] > before[3] ? " (bien)" : " (MAL)"));
            }
            case 8 -> {
                PadClanProgress.testBoost(A, true);
                double p0 = PadClanProgress.testFirstProgress(A, 1);
                PadClanProgress.testAddFirst(player, 1, 10);
                double p1 = PadClanProgress.testFirstProgress(A, 1);
                PadClanProgress.testBoost(A, false);
                PadClanProgress.testAddFirst(player, 1, 10);
                double p2 = PadClanProgress.testFirstProgress(A, 1);
                r.event("clanes 4 (potenciador x1,5): 10 con él suman " + (p1 - p0) + ", sin él " + (p2 - p1)
                        + (Math.abs(p1 - p0 - 15) < 0.01 && Math.abs(p2 - p1 - 10) < 0.01 ? " (bien)" : " (MAL)"));
            }
            case 10 -> {
                act(player, "guerra", "alianza.proponer:" + B);
                FakePlayer rival = fake(level, B0, "Rival_Prueba", player);
                act(rival, "guerra", "alianza.aceptar:" + A);
                boolean allied = PadClans.testAllied(A, B) && PadClans.testAllied(B, A);
                PadClanProgress.testCopyMissions(A, B);
                double b0 = PadClanProgress.testFirstProgress(B, 1);
                PadClanProgress.testAddFirst(player, 1, 10);
                double b1 = PadClanProgress.testFirstProgress(B, 1);
                r.event("clanes 5 (alianza propuesta y aceptada): aliados " + allied + "; 10 de un miembro de A suman a B " + (b1 - b0)
                        + (allied && Math.abs(b1 - b0 - 5) < 0.01 ? " (bien: la mitad)" : " (MAL)"));
                PadClanProgress.testSet(A, 60, 5, 0, 120);
                TFPadNet.sendState(player);
            }
            case 12 -> BossTest.openPad = "clanes/miembros";
            case 60 -> BossTest.shot(r, "clanes_1_miembros");
            case 62 -> BossTest.openPad = "clanes/misiones";
            case 110 -> BossTest.shot(r, "clanes_2_misiones");
            case 112 -> {
                // el banco: meter, lo que no entra, sacar y mejorar
                var inv = player.getInventory();
                inv.items.set(0, new ItemStack(Items.COBBLESTONE, 64));
                inv.items.set(1, new ItemStack(Items.COBBLESTONE, 64));
                inv.items.set(2, new ItemStack(Items.DIRT, 20));
                ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                sword.enchant(Enchantments.SHARPNESS, 3);
                inv.items.set(3, sword);
                act(player, "banco", "banco.meter:minecraft:cobblestone");
                act(player, "banco", "banco.meter:minecraft:dirt");
                act(player, "banco", "banco.meter:minecraft:diamond_sword");
                long cob = PadClanBank.testCount(A, "minecraft:cobblestone"), dirt = PadClanBank.testCount(A, "minecraft:dirt"),
                        swordIn = PadClanBank.testCount(A, "minecraft:diamond_sword");
                int left = countItem(player, Items.COBBLESTONE);
                act(player, "banco", "banco.sacar:minecraft:cobblestone");
                long cob2 = PadClanBank.testCount(A, "minecraft:cobblestone");
                int back = countItem(player, Items.COBBLESTONE);
                long cap0 = PadClanBank.testCapacity(A), c0 = PadClanProgress.testStats(A)[0];
                act(player, "banco", "banco.mejorar");
                act(player, "banco", "banco.mejorar");
                long cap1 = PadClanBank.testCapacity(A), c1 = PadClanProgress.testStats(A)[0];
                r.event("clanes 6 (banco): metió piedra " + cob + " y tierra " + dirt + " (le quedan " + left + "), la espada encantada " + swordIn
                        + " (no entra: bien si 0); sacó una pila: banco " + cob2 + ", inventario " + back + "; mejora (dos clics): " + cap0 + " → " + cap1
                        + " por " + (c0 - c1) + " Clan Coins"
                        + (cob == 128 && dirt == 20 && swordIn == 0 && cob2 == 64 && back == 64 && cap1 == cap0 + 5000 && c0 - c1 == 2 ? " (bien)" : " (MAL)"));
                BossTest.openPad = "clanes/banco";
            }
            case 160 -> {
                BossTest.shot(r, "clanes_3_banco");
                long bar = TFPadClient.clanCoinsForTest(), real = PadClanProgress.testStats(A)[0];
                r.event("clanes 6b (la barra de arriba tras pagar la mejora): ¢" + bar + ", el clan tiene " + real + (bar == real ? " (bien)" : " (MAL)"));
            }
            case 162 -> BossTest.openPad = "clanes/banco_reg";
            case 210 -> BossTest.shot(r, "clanes_4_banco_registro");
            case 212 -> {
                // la tienda del clan: 2 protecciones de 50x50 como mucho
                long c0 = PadClanProgress.testStats(A)[0];
                for (int i = 0; i < 3; i++) {
                    act(player, "tienda", "tienda.comprar:proteccion_clan_50x50");
                    act(player, "tienda", "tienda.comprar:proteccion_clan_50x50");
                }
                long c1 = PadClanProgress.testStats(A)[0];
                var item50 = net.tierrasfantasticas.tfclient.claims.item.ClaimItems.itemFor(
                        net.tierrasfantasticas.tfclient.claims.data.ClaimTier.byId("claimstone_clan_50x50"));
                int stones = countItem(player, item50);
                r.event("clanes 7 (tienda del clan: 3 intentos de la protección 50x50, con su doble clic): " + stones + " piedras, " + (c0 - c1)
                        + " Clan Coins" + (stones == 2 && c0 - c1 == 30 ? " (bien: 2 como mucho, 15 cada una)" : " (MAL)"));
                BossTest.openPad = "clanes/tienda";
            }
            case 260 -> BossTest.shot(r, "clanes_5_tienda");
            case 262 -> {
                BossTest.closeScreen = true;
                // la protección del clan: la pone el líder; un miembro raso no
                int x = (int) arena.x + 500, z = (int) arena.z;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                player.teleportTo(level, x + 0.5, y, z - 3.5, 0F, 10F);
                var item50 = net.tierrasfantasticas.tfclient.claims.item.ClaimItems.itemFor(
                        net.tierrasfantasticas.tfclient.claims.data.ClaimTier.byId("claimstone_clan_50x50"));
                ItemStack stone = new ItemStack(item50);
                FakePlayer raso = fake(level, A_MEMBER, "Caro_Prueba", player);
                raso.setItemInHand(InteractionHand.MAIN_HAND, stone.copy());
                BlockPos ground2 = new BlockPos(x + 200, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + 200, z) - 1, z);
                raso.gameMode.useItemOn(raso, level, raso.getMainHandItem(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(ground2), Direction.UP, ground2, false));
                Claim byRaso = ClaimManager.getInstance().getClaimAt(level, ground2.above());
                player.setItemInHand(InteractionHand.MAIN_HAND, stone);
                BlockPos ground = new BlockPos(x, y - 1, z);
                player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false));
                stonePos = ground.above();
                Claim c = ClaimManager.getInstance().getClaimAt(level, stonePos);
                boolean clan = c != null && A.equals(c.getClanId());
                boolean memberIn = c != null && c.isMember(A_MEMBER), rivalOut = c != null && !c.isMember(B1);
                var title = PadClans.territoryTitle(A);
                r.event("clanes 8 (protección del clan 50x50): un miembro raso " + (byRaso == null ? "no puede ponerla (bien)" : "LA PUSO (MAL)")
                        + "; el líder sí: zona del clan " + clan + ", un miembro construye " + memberIn + ", el rival no " + rivalOut + "; al entrar: «"
                        + (title == null ? "" : title.getString()) + "»" + (byRaso == null && clan && memberIn && rivalOut ? " (bien)" : " (MAL)"));
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.teleportTo(level, x + 0.5, y, z - 90.5, 0F, 10F);
            }
            case 290 -> player.teleportTo(level, stonePos.getX() + 0.5, stonePos.getY(), stonePos.getZ() - 6.5, 0F, 15F);
            case 298 -> BossTest.shot(r, "clanes_6_territorio");
            case 310 -> {
                // la tienda del juego: una protección de cada tipo por jugador
                net.tierrasfantasticas.tfclient.shop.TFShopProtections.forget(server, me);
                net.tierrasfantasticas.tfclient.shop.TFShopConfig.Entry e = null;
                for (var cat : net.tierrasfantasticas.tfclient.shop.TFShopConfig.categories.values()) {
                    for (var en : cat.entries()) if (en.itemId().equals("tfclient:proteccion_10x10")) e = en;
                }
                if (e == null) {
                    r.event("clanes 9 (tienda del juego): no hay protección 10x10 en la tienda de prueba");
                } else {
                    net.tierrasfantasticas.tfclient.economy.TFEconomy.give(server, me, player.getGameProfile().getName(), 100_000);
                    boolean one = net.tierrasfantasticas.tfclient.shop.TFShop.buy(player, e, 1);
                    boolean two = net.tierrasfantasticas.tfclient.shop.TFShop.buy(player, e, 1);
                    r.event("clanes 9 (tienda del juego, protección 10x10 dos veces): la 1.ª " + one + ", la 2.ª " + two
                            + (one && !two ? " (bien: una de cada tipo)" : " (MAL)"));
                }
                player.teleportTo(level, arena.x, arena.y, arena.z, 0F, 0F);
            }
            case 320 -> {
                // la guerra: romper la alianza, declararla, aceptarla y a medias
                act(player, "guerra", "alianza.romper:" + B);
                act(player, "guerra", "alianza.romper:" + B);
                String d = PadClanWar.testDeclare(A, B), ac = PadClanWar.testAccept(B, A);
                PadClanWar.testFill(A, 0.62);
                PadClanWar.testFill(B, 0.35);
                r.event("clanes 10 (guerra): alianza rota " + !PadClans.testAllied(A, B) + "; declarar " + (d == null ? "bien" : d) + ", aceptar "
                        + (ac == null ? "bien" : ac) + "; estado " + PadClanWar.testState(A) + "; A " + Math.round(PadClanWar.testProgress(A) * 100) + " %, B "
                        + Math.round(PadClanWar.testProgress(B) * 100) + " %");
                BossTest.openPad = "clanes/guerra";
            }
            case 370 -> BossTest.shot(r, "clanes_7_guerra_pestana");
            case 372 -> BossTest.actPad = "§guerra";
            case 440 -> {
                BossTest.shot(r, "clanes_8_guerra_barra");
                float[] drawn = TFPadClient.warForTest();
                r.event("clanes 11 (la barra vertical doble): página " + TFPadClient.pageForTest() + ", dibuja A " + Math.round(drawn[0] * 100) + " % y B "
                        + Math.round(drawn[1] * 100) + " %" + (Math.abs(drawn[0] - 0.62) < 0.02 && Math.abs(drawn[1] - 0.35) < 0.02 ? " (bien)" : " (MAL)"));
            }
            case 446 -> {
                greenBefore = PadGreen.balance(me);
                before = PadClanProgress.testStats(A);
                PadClanWar.testComplete(player);
                long[] after = PadClanProgress.testStats(A);
                long green = PadGreen.balance(me) - greenBefore;
                r.event("clanes 12 (A cumple todos los objetivos): " + PadClanWar.testState(A) + "; Clan Coins +" + (after[0] - before[0]) + ", prestigio +"
                        + (after[3] - before[3]) + ", verdes +" + green
                        + (PadClanWar.testState(A).equals("acabada/A") && after[0] - before[0] == 2 && after[3] - before[3] == 150 && green == 1 ? " (bien)" : " (MAL)"));
                BossTest.openPad = "clanes/guerra";
            }
            case 490 -> BossTest.actPad = "§guerra";
            case 550 -> BossTest.shot(r, "clanes_9_guerra_ganada");
            case 556 -> {
                BossTest.closeScreen = true;
                // los duelos (con jugadores de mentira de Forge)
                FakePlayer rival = fake(level, B0, "Rival_Prueba", player), mate = fake(level, A_MEMBER, "Caro_Prueba", player),
                        none = fake(level, NOBODY, "Sin_Clan", player);
                String w1 = PadClanDuels.testWhyNot(player, rival), w2 = PadClanDuels.testWhyNot(player, mate), w3 = PadClanDuels.testWhyNot(player, none);
                boolean pvp = GlobalFlags.getInstance().globalPVP;
                GlobalFlags.getInstance().globalPVP = false;
                player.setHealth(20F);
                var src = rival.damageSources().playerAttack(rival);
                LivingHurtEvent control = new LivingHurtEvent(player, src, 5F);
                MinecraftForge.EVENT_BUS.post(control);
                long p0 = PadClanProgress.testStats(B)[3];
                Object duel = PadClanDuels.testStart(rival, player, true);
                LivingHurtEvent hit = new LivingHurtEvent(player, src, 100F);
                MinecraftForge.EVENT_BUS.post(hit);
                long p1 = PadClanProgress.testStats(B)[3];
                Object duel2 = PadClanDuels.testStart(rival, player, true);
                MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(player, src, 100F));
                long p2 = PadClanProgress.testStats(B)[3];
                GlobalFlags.getInstance().globalPVP = pvp;
                r.event("clanes 13 (duelos): rival de otro clan " + (w1 == null ? "se puede (bien)" : "«" + w1 + "» (MAL)") + "; del mismo clan «" + w2
                        + "»; sin clan «" + w3 + "»; con el PvP apagado y sin duelo, el golpe " + (control.isCanceled() ? "no pasa" : "PASA")
                        + "; en duelo pasa " + !hit.isCanceled() + " y deja " + (20F - hit.getAmount()) + " de vida; gana «" + PadClanDuels.testResult(duel)
                        + "», prestigio de su clan +" + (p1 - p0) + "; otra vez la misma pareja: +" + (p2 - p1) + " («" + PadClanDuels.testResult(duel2) + "»)"
                        + (w1 == null && w2 != null && w3 != null && control.isCanceled() && !hit.isCanceled() && Math.abs(20F - hit.getAmount() - 2F) < 0.01
                        && p1 - p0 == 25 && p2 == p1 ? " (bien)" : " (MAL)"));
                PadClanDuels.testClear();
                player.setHealth(20F);
            }
            case 560 -> BossTest.openPad = "clanes/estilo";
            case 610 -> BossTest.shot(r, "clanes_10_estilo");
            case 612 -> BossTest.actPad = "§color:clanes:estilo:55AAFF:PRB";
            case 660 -> {
                BossTest.shot(r, "clanes_11_paleta");
                r.event("clanes 14 (PALETA): página " + TFPadClient.pageForTest() + (TFPadClient.pageForTest().equals("PadColorPicker") ? " (bien)" : " (MAL)"));
            }
            case 662 -> TFPadClient.colorForTest(0x22B14C, false);
            case 700 -> BossTest.shot(r, "clanes_12_paleta_verde");
            case 702 -> TFPadClient.colorForTest(0x22B14C, true);
            case 750 -> {
                String col = PadClans.testColor(A);
                r.event("clanes 15 (GUARDAR en la paleta): el color del clan es " + col + (col.equalsIgnoreCase("#22B14C") ? " (bien)" : " (MAL)")
                        + "; vuelve a " + TFPadClient.pageForTest());
                BossTest.shot(r, "clanes_13_estilo_verde");
            }
            case 752 -> BossTest.openPad = "ranking/clanes";
            case 800 -> BossTest.shot(r, "clanes_14_ranking");
            case 802 -> BossTest.openPad = "clanes/ajustes";
            case 850 -> BossTest.shot(r, "clanes_15_ajustes");
            case 852 -> {
                BossTest.closeScreen = true;
                // el cofre del banco: COFRE lo da; puesto, se abre en el pad
                int had = countItem(player, net.tierrasfantasticas.tfclient.clans.ClanBlocks.BANK_ITEM.get());
                act(player, "banco", "banco.cofre");
                int got = countItem(player, net.tierrasfantasticas.tfclient.clans.ClanBlocks.BANK_ITEM.get()) - had;
                int x = (int) arena.x + 6, z = (int) arena.z + 3;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                player.teleportTo(level, x + 0.5, y, z - 3.5, 0F, 20F);
                BlockPos ground = new BlockPos(x, y - 1, z);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.tierrasfantasticas.tfclient.clans.ClanBlocks.BANK_ITEM.get()));
                player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false));
                chestPos = ground.above();
                boolean placed = level.getBlockState(chestPos).getBlock() == net.tierrasfantasticas.tfclient.clans.ClanBlocks.BANK.get();
                r.event("clanes 16 (el cofre del banco): COFRE da " + got + "; puesto " + placed);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
            case 870 -> BossTest.shot(r, "clanes_16_cofre");
            case 872 -> player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(chestPos), Direction.NORTH, chestPos, false));
            case 930 -> {
                BossTest.shot(r, "clanes_17_cofre_abre_banco");
                r.event("clanes 17 (abrir el cofre): página " + TFPadClient.pageForTest() + (TFPadClient.pageForTest().equals("PadViewPage") ? " (bien)" : " (MAL)"));
            }
            case 940 -> {
                BossTest.closeScreen = true;
                if (chestPos != null) level.removeBlock(chestPos, false);
                if (stonePos != null) {
                    Claim c = ClaimManager.getInstance().getClaimAt(level, stonePos);
                    if (c != null) ClaimManager.getInstance().removeClaim(level, c.getCenter());
                    level.removeBlock(stonePos, false);
                }
                PadClanWar.testReset(A, B);
                PadClans.testRemove(B);
                PadClans.testCleanup(player);
                PadClanDuels.testClear();
            }
            case 950 -> BossTest.next(level);
            default -> { }
        }
    }
}
