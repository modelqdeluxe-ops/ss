/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.bosses;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.tierrasfantasticas.tfclient.clans.ClanBannerBlockEntity;
import net.tierrasfantasticas.tfclient.clans.ClanBlocks;
import net.tierrasfantasticas.tfclient.clans.ClanEmote;
import net.tierrasfantasticas.tfclient.clans.ClanLooks;
import net.tierrasfantasticas.tfclient.claims.data.GlobalFlags;
import net.tierrasfantasticas.tfclient.pad.server.PadClanBank;
import net.tierrasfantasticas.tfclient.pad.server.PadClanConfig;
import net.tierrasfantasticas.tfclient.pad.server.PadClanDuels;
import net.tierrasfantasticas.tfclient.pad.server.PadClanProgress;
import net.tierrasfantasticas.tfclient.pad.server.PadClanWar;
import net.tierrasfantasticas.tfclient.pad.server.PadClans;
import net.tierrasfantasticas.tfclient.pad.server.PadShop;
import net.tierrasfantasticas.tfclient.server.TFBotPlayer;
import net.tierrasfantasticas.tfclient.util.TFNames;

/**
 * (1.3.159) La prueba en el juego de lo que pidió el dueño al ver los clanes de la 1.3.158 ([prueba-jefes=clanes2]):
 * nombres en español y el buscador de la tienda, el banco compacto, el fondo del pad en todas las apps (Limón, un PNG con
 * detalle fino y el del tema), la guerra con su objetivo PvP (con el noqueo de Revivir: solo cuenta el remate), el reto de
 * duelo con sus botones (pulsados como con el ratón), el cofre (uno por clan, lo quita el líder y le vuelve, brilla de
 * noche), el estandarte (la insignia de ahora, de día, de noche y al cambiarla), el editor de la etiqueta (arcoíris,
 * degradado, onda), la etiqueta animada encima de la cabeza y en el TAB y el emote del clan (grabado en vídeo).
 */
final class BossTestClanes2 {
    private BossTestClanes2() {}

    private static final String A = "prueba01", B = "prueba02";
    private static ServerPlayer rival1, rival2, mate;
    private static BlockPos chestPos, bannerPos, vanillaPos;
    private static String newBadge = "";
    private static boolean pvpBefore;

    private static String act(ServerPlayer p, String tab, String action) {
        return PadClans.testAction(p, tab, action, "");
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.getItem() == item) n += s.getCount();
        return n;
    }

    private static BlockPos ground(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Pone al jugador en from mirando a to (los ojos, a 1,62 del suelo). */
    private static void look(ServerPlayer p, ServerLevel level, Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - (from.y + 1.62), dz = to.z - from.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.teleportTo(level, from.x, from.y, from.z, yaw, pitch);
    }

    private static void place(ServerPlayer p, ServerLevel level, BlockPos at, ItemStack stack) {
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos below = at.below();
        p.gameMode.useItemOn(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false));
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    static void step(int t, MinecraftServer server, ServerLevel level, ServerPlayer player, BossTest.Rep r) {
        Vec3 arena = BossTest.arena();
        int ax = (int) arena.x, az = (int) arena.z;
        switch (t) {
            case 1 -> {
                player.teleportTo(level, ax + 0.5, ground(level, ax, az).getY(), az + 0.5, 0F, 0F);
                PadClans.testRanks(player);
                PadClanProgress.testSet(A, 400, 5, 0, 100);
                PadClanBank.testReset(A);
                PadClanWar.testReset(A, B);
                PadClanDuels.testClear();
                BlockPos g1 = ground(level, ax + 3, az + 8), g2 = ground(level, ax - 3, az + 8), g3 = ground(level, ax, az + 4);
                rival1 = TFBotPlayer.spawnTarget(server, level, "Rival_Uno", g1.getX() + 0.5, g1.getY(), g1.getZ() + 0.5);
                rival2 = TFBotPlayer.spawnTarget(server, level, "Rival_Dos", g2.getX() + 0.5, g2.getY(), g2.getZ() + 0.5);
                mate = TFBotPlayer.spawnTarget(server, level, "Socio_Clan", g3.getX() + 0.5, g3.getY(), g3.getZ() + 0.5);
                if (rival1 == null || rival2 == null || mate == null) {
                    r.event("clanes2 0: no salieron los jugadores de prueba (MAL)");
                    return;
                }
                PadClans.testClan(B, "Clan Rival", "RIV", rival1.getUUID(), List.of(rival2.getUUID()), "#E04848");
                PadClans.testJoin(A, mate.getUUID());
                // nombres en español
                String cob = TFNames.es(new ItemStack(Items.COBBLESTONE)), sword = TFNames.es(new ItemStack(Items.DIAMOND_SWORD)),
                        log = TFNames.es(new ItemStack(Items.OAK_LOG));
                boolean find = TFNames.matches(new ItemStack(Items.DIAMOND_SWORD), "espada dia") && TFNames.matches(new ItemStack(Items.DIAMOND_SWORD), "diamond");
                r.event("clanes2 1 (nombres en español): «" + cob + "», «" + sword + "», «" + log + "»; buscar «espada dia» y «diamond» la encuentran " + find
                        + (cob.equals("Piedra labrada") && sword.equals("Espada de diamante") && find ? " (bien)" : " (MAL)"));
                r.event("clanes2 2 (misiones más exigentes y clanes.json de antes): " + PadClanConfig.testMigrate());
            }
            case 3 -> {
                String all = PadShop.testSearch(player, ""), swords = PadShop.testSearch(player, "espada"), wood = PadShop.testSearch(player, "madera");
                r.event("clanes2 3 (tienda › Todos los objetos en venta): todos " + all + "; «espada»: " + swords + "; «madera»: " + wood
                        + (swords.contains("Espada") ? " (bien)" : " (MAL)"));
                PadShop.testSearch(player, "");
            }
            case 5 -> BossTest.openPad = "tienda/todos";
            case 50 -> BossTest.shot(r, "clanes2_01_tienda_todos");
            case 52 -> BossTest.actPad = "§type:espada";
            case 100 -> BossTest.shot(r, "clanes2_02_tienda_buscar_espada");
            case 102 -> {
                var inv = player.getInventory();
                inv.items.set(0, new ItemStack(Items.COBBLESTONE, 64));
                inv.items.set(1, new ItemStack(Items.OAK_LOG, 32));
                inv.items.set(2, new ItemStack(Items.DIRT, 20));
                inv.items.set(3, new ItemStack(Items.IRON_INGOT, 12));
                act(player, "banco", "banco.meter:minecraft:cobblestone");
                act(player, "banco", "banco.meter:minecraft:oak_log");
                inv.items.set(0, new ItemStack(Items.COBBLESTONE, 64));
                inv.items.set(4, new ItemStack(Items.BREAD, 16));
                inv.items.set(5, new ItemStack(Items.TORCH, 40));
                BossTest.openPad = "clanes/banco";
            }
            case 150 -> BossTest.shot(r, "clanes2_03_banco_compacto");
            // el fondo del pad en todas las apps
            case 152 -> {
                BossTest.closeScreen = true;
                BossTest.wallpaper = "c:limon";
            }
            case 156 -> BossTest.openPad = "";
            case 200 -> BossTest.shot(r, "clanes2_04_fondo_limon_portada");
            case 202 -> BossTest.openPad = "clanes/miembros";
            case 250 -> BossTest.shot(r, "clanes2_05_fondo_limon_clanes");
            case 252 -> BossTest.openPad = "tienda";
            case 300 -> BossTest.shot(r, "clanes2_06_fondo_limon_tienda");
            case 302 -> {
                BossTest.closeScreen = true;
                BossTest.wallpaper = "f:prueba_fondo.png";
            }
            case 306 -> BossTest.openPad = "";
            case 360 -> BossTest.shot(r, "clanes2_07_fondo_png_portada");
            case 362 -> BossTest.openPad = "misiones";
            case 410 -> BossTest.shot(r, "clanes2_08_fondo_png_misiones");
            case 412 -> {
                BossTest.closeScreen = true;
                BossTest.wallpaper = "tema";
            }
            // la guerra con su objetivo PvP
            case 420 -> {
                String d = PadClanWar.testDeclare(A, B), ac = PadClanWar.testAccept(B, A);
                String objs = PadClanWar.testObjectives(A);
                long pvp = objs.split("\\|")[0].chars().filter(ch -> ch == ',').count() + 1;
                boolean onePvp = objs.contains("pvp:pvp"), killing = objs.replace("pvp:pvp", "").contains(":matar");
                r.event("clanes2 4 (guerra con PvP): declarar " + (d == null ? "bien" : d) + ", aceptar " + (ac == null ? "bien" : ac) + "; objetivos " + objs
                        + " (" + pvp + ")" + (onePvp && !killing ? " (bien: uno PvP y ninguno más de matar)" : " (MAL)"));
                pvpBefore = GlobalFlags.getInstance().globalPVP;
                GlobalFlags.getInstance().globalPVP = true;
                rival2.teleportTo(level, player.getX() + 1.5, player.getY(), player.getZ() + 1.5, 0F, 0F);
            }
            case 424 -> {
                // con Revivir el primer golpe noquea (no cuenta) y el segundo remata (cuenta)
                rival2.hurt(player.damageSources().playerAttack(player), 1000F);
                r.event("clanes2 5a (golpe 1 a un rival): " + PadClanWar.testObjectives(A));
            }
            case 434 -> {
                rival2.hurt(player.damageSources().playerAttack(player), 1000F);
                String after = PadClanWar.testObjectives(A);
                boolean again = PadClanWar.testKill(player, rival2);
                r.event("clanes2 5b (lo remata): " + after + "; otra vez el mismo enseguida cuenta " + again
                        + (after.endsWith("pvp=1") && !again ? " (bien: 1, y no se puede farmear)" : " (MAL)"));
                GlobalFlags.getInstance().globalPVP = pvpBefore;
            }
            case 440 -> BossTest.openPad = "clanes/guerra";
            case 470 -> BossTest.actPad = "§guerra";
            case 520 -> BossTest.shot(r, "clanes2_09_guerra_pvp");
            // el reto de duelo con sus botones
            case 522 -> {
                BossTest.closeScreen = true;
                rival1.teleportTo(level, player.getX() + 2, player.getY(), player.getZ() + 2, 0F, 0F);
                PadClanDuels.testChallenge(rival1, player);
            }
            case 526 -> BossTest.chatClick = "ACEPTAR";
            case 540 -> BossTest.shot(r, "clanes2_10_duelo_botones");
            case 542 -> {
                boolean in = PadClanDuels.testInDuel(player.getUUID());
                r.event("clanes2 6 (reto de duelo con botones): " + BossTest.clientSaw + "; duelo empezado " + in + (in ? " (bien)" : " (MAL)"));
                PadClanDuels.testClear();
                BossTest.closeScreen = true;
            }
            // el cofre: uno por clan, lo quita el líder (vuelve) y nadie más, brilla de noche
            case 546 -> {
                PadClanBank.testReset(A);
                player.getInventory().clearContent();
                Item chest = ClanBlocks.BANK_ITEM.get();
                act(player, "banco", "banco.cofre");
                int one = count(player, chest);
                act(player, "banco", "banco.cofre");
                int still = count(player, chest);
                player.teleportTo(level, ax + 0.5, ground(level, ax, az).getY(), az + 0.5, 0F, 18F);
                chestPos = ground(level, ax, az + 3);
                place(player, level, chestPos, new ItemStack(chest));
                boolean placed = level.getBlockState(chestPos).getBlock() == ClanBlocks.BANK.get();
                act(player, "banco", "banco.cofre");
                int afterPlace = count(player, chest);
                rival1.gameMode.destroyBlock(chestPos);
                boolean rivalCant = level.getBlockState(chestPos).getBlock() == ClanBlocks.BANK.get();
                mate.gameMode.destroyBlock(chestPos);
                boolean mateCant = level.getBlockState(chestPos).getBlock() == ClanBlocks.BANK.get();
                player.gameMode.destroyBlock(chestPos);
                boolean gone = level.getBlockState(chestPos).isAir();
                int back = count(player, chest);
                r.event("clanes2 7 (el cofre, uno por clan): COFRE da " + one + ", otra vez " + still + "; puesto " + placed + ", COFRE con él puesto deja "
                        + afterPlace + "; un rival no lo quita " + rivalCant + ", un miembro raso no " + mateCant + ", el líder sí " + gone
                        + " y le vuelve: " + back + (one == 1 && still == 1 && placed && afterPlace == 0 && rivalCant && mateCant && gone && back == 1
                        ? " (bien)" : " (MAL)"));
                place(player, level, chestPos, new ItemStack(chest));
                vanillaPos = chestPos.east(2);
                level.setBlock(vanillaPos, Blocks.CHEST.defaultBlockState(), 3);
                look(player, level, new Vec3(chestPos.getX() + 1.5, chestPos.getY(), chestPos.getZ() - 2.7), new Vec3(chestPos.getX() + 1.5, chestPos.getY() + 0.5,
                        chestPos.getZ() + 0.5));
                BossTest.hideGui = true;
            }
            case 580 -> BossTest.shot(r, "clanes2_11_cofre_dia");
            case 582 -> BossTest.command(server, "time set 18000");
            case 610 -> BossTest.shot(r, "clanes2_12_cofre_noche");
            // el estandarte del clan
            case 612 -> {
                BossTest.command(server, "time set 6000");
                act(player, "tienda", "tienda.comprar:estandarte");
                act(player, "tienda", "tienda.comprar:estandarte");
                int got = count(player, ClanBlocks.BANNER_ITEM.get());
                bannerPos = chestPos.west(3);
                look(player, level, new Vec3(bannerPos.getX() + 0.5, bannerPos.getY(), bannerPos.getZ() - 1.5), new Vec3(bannerPos.getX() + 0.5, bannerPos.getY(),
                        bannerPos.getZ() + 0.5));
                place(player, level, bannerPos, new ItemStack(ClanBlocks.BANNER_ITEM.get()));
                String clan = level.getBlockEntity(bannerPos) instanceof ClanBannerBlockEntity be ? be.clan() : "(no está)";
                ClanLooks.Look look = ClanLooks.clan(A);
                r.event("clanes2 8 (estandarte de la tienda del clan): compré " + got + "; puesto, del clan «" + clan + "»; el cliente ve la insignia «"
                        + (look == null ? "?" : look.badge()) + "»" + (got == 1 && A.equals(clan) && look != null ? " (bien)" : " (MAL)"));
                look(player, level, new Vec3(bannerPos.getX() + 1.6, bannerPos.getY(), bannerPos.getZ() - 3.4), new Vec3(bannerPos.getX() + 0.5, bannerPos.getY() + 1.5,
                        bannerPos.getZ() + 0.5));
            }
            case 650 -> BossTest.shot(r, "clanes2_13_estandarte");
            case 652 -> act(player, "estilo", "tagfx:arcoiris||3");
            case 700 -> BossTest.shot(r, "clanes2_14_estandarte_arcoiris");
            case 702 -> {
                newBadge = PadClanProgress.badges().get(Math.min(60, PadClanProgress.badges().size() - 1)).id();
                act(player, "estilo", "insignia:" + newBadge);
            }
            case 760 -> {
                BossTest.shot(r, "clanes2_15_estandarte_insignia_nueva");
                ClanLooks.Look look = ClanLooks.clan(A);
                r.event("clanes2 9 (cambiar la insignia): «" + newBadge + "»; el estandarte ve «" + (look == null ? "?" : look.badge()) + "»"
                        + (look != null && newBadge.equals(look.badge()) ? " (bien)" : " (MAL)"));
            }
            case 762 -> BossTest.command(server, "time set 18000");
            case 790 -> BossTest.shot(r, "clanes2_16_estandarte_noche");
            case 792 -> {
                BossTest.command(server, "time set 6000");
                rival1.gameMode.destroyBlock(bannerPos);
                boolean stays = level.getBlockState(bannerPos).getBlock() == ClanBlocks.BANNER.get();
                player.gameMode.destroyBlock(bannerPos);
                boolean gone = level.getBlockState(bannerPos).isAir();
                int back = count(player, ClanBlocks.BANNER_ITEM.get());
                r.event("clanes2 10 (quitar el estandarte): un rival no " + stays + "; el líder sí " + gone + " y le vuelve " + back
                        + (stays && gone && back == 1 ? " (bien)" : " (MAL)"));
                place(player, level, bannerPos, new ItemStack(ClanBlocks.BANNER_ITEM.get()));
            }
            // el editor de la etiqueta
            case 798 -> BossTest.hideGui = false;
            case 800 -> BossTest.openPad = "clanes/estilo";
            case 840 -> BossTest.shot(r, "clanes2_17_estilo");
            case 842 -> BossTest.actPad = "§color:clanes:estilo:55AAFF:PRB:arcoiris||3";
            case 880 -> BossTest.shot(r, "clanes2_18_editor_arcoiris");
            case 882 -> net.tierrasfantasticas.tfclient.pad.TFPadClient.tagFxForTest("degradado|FF3355,FFD24A,33AAFF|2", false);
            case 920 -> BossTest.shot(r, "clanes2_19_editor_degradado");
            case 922 -> net.tierrasfantasticas.tfclient.pad.TFPadClient.tagFxForTest("onda|FF3355,33AAFF|4", true);
            case 960 -> {
                String fx = PadClans.testFx(A);
                Component tag = PadClans.tagOf(player.getUUID());
                long colors = tag == null ? 0 : tag.toFlatList().stream().map(c -> c.getStyle().getColor()).distinct().count();
                r.event("clanes2 11 (editor de la etiqueta, GUARDAR en onda): el clan queda «" + fx + "»; la etiqueta del chat tiene " + colors + " colores"
                        + (fx.startsWith("onda|FF3355,33AAFF|4") && colors > 2 ? " (bien)" : " (MAL)"));
            }
            // la etiqueta animada encima de la cabeza y en el TAB (el plugin TAB la pone en un equipo, como aquí)
            case 962 -> {
                BossTest.closeScreen = true;
                act(player, "estilo", "tagfx:arcoiris||4");
                var sb = server.getScoreboard();
                PlayerTeam team = sb.getPlayerTeam("tfprueba");
                if (team == null) team = sb.addPlayerTeam("tfprueba");
                team.setPlayerSuffix(Component.literal(" [PRB]"));
                sb.addPlayerToTeam(mate.getScoreboardName(), team);
                // a un lado del cofre y del estandarte: el compañero a 4 bloques, mirando al jugador
                BlockPos g = ground(level, ax - 6, az + 3);
                player.teleportTo(level, ax - 5.5, g.getY(), az - 0.5, 0F, 6F);
                mate.teleportTo(level, ax - 5.5, g.getY(), az + 3.5, 180F, 0F);
                BossTest.hideGui = true;
            }
            case 1000 -> BossTest.shot(r, "clanes2_20_etiqueta_cabeza_1");
            case 1006 -> BossTest.shot(r, "clanes2_21_etiqueta_cabeza_2");
            case 1008 -> {
                BossTest.hideGui = false;
                BossTest.tabList = true;
            }
            case 1030 -> BossTest.shot(r, "clanes2_22_tab");
            case 1032 -> BossTest.tabList = false;
            // el banco: METER › (lo que llevas, en casillas)
            case 1034 -> {
                var inv = player.getInventory();
                inv.items.set(0, new ItemStack(Items.COBBLESTONE, 64));
                inv.items.set(1, new ItemStack(Items.OAK_PLANKS, 48));
                inv.items.set(2, new ItemStack(Items.IRON_INGOT, 9));
                inv.items.set(3, new ItemStack(Items.WHEAT, 30));
                inv.items.set(4, new ItemStack(Items.TORCH, 24));
                ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
                named.setHoverName(Component.literal("Espada de Dev"));
                inv.items.set(5, named);
                BossTest.openPad = "clanes/banco_meter";
            }
            case 1076 -> BossTest.shot(r, "clanes2_23_banco_meter");
            case 1078 -> BossTest.closeScreen = true;
            // el emote del clan, grabado en vídeo (de frente, en tercera persona, al atardecer)
            case 1080 -> {
                BossTest.command(server, "time set 12800");
                BlockPos g = ground(level, ax + 6, az - 6);
                player.teleportTo(level, g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 0F, 0F);
                mate.teleportTo(level, g.getX() + 40.5, g.getY(), g.getZ() + 0.5, 0F, 0F);
                BossTest.camera = "frente";
                BossTest.hideGui = true;
            }
            case 1100 -> BossTest.video = "emote";
            case 1104 -> ClanEmote.testPlay(player);
            case 1140 -> r.event("clanes2 12 (emote del clan): " + (BossTest.video == null ? "grabado en vídeo (video_emote.mp4)" : "NO SE GRABÓ (MAL)"));
            case 1142 -> {
                BossTest.camera = "primera";
                BossTest.hideGui = false;
                BossTest.command(server, "time set 6000");
                if (chestPos != null) level.removeBlock(chestPos, false);
                if (vanillaPos != null) level.removeBlock(vanillaPos, false);
                if (bannerPos != null) level.removeBlock(bannerPos, false);
                var sb = server.getScoreboard();
                PlayerTeam team = sb.getPlayerTeam("tfprueba");
                if (team != null) sb.removePlayerTeam(team);
                PadClanWar.testReset(A, B);
                for (ServerPlayer bot : new ServerPlayer[] {rival1, rival2, mate}) if (bot != null) TFBotPlayer.removeTarget(bot);
                PadClans.testRemove(B);
                PadClans.testCleanup(player);
            }
            case 1150 -> BossTest.next(level);
            default -> { }
        }
    }
}
