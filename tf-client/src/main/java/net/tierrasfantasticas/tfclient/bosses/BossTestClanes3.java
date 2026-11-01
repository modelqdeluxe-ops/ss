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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import net.tierrasfantasticas.tfclient.clans.ClanBlocks;
import net.tierrasfantasticas.tfclient.clans.ClanEmote;
import net.tierrasfantasticas.tfclient.clans.ClanLooks;
import net.tierrasfantasticas.tfclient.pad.server.PadClanProgress;
import net.tierrasfantasticas.tfclient.pad.server.PadClanWar;
import net.tierrasfantasticas.tfclient.pad.server.PadClans;
import net.tierrasfantasticas.tfclient.pad.server.PadZoneMusic;
import net.tierrasfantasticas.tfclient.server.TFBotPlayer;

/**
 * (1.3.160) Prueba «clanes3»: lo que pidió el dueño tras ver la 1.3.159.
 * <ol>
 *   <li>Escudo de guerra (tienda del clan): con él nadie le declara la guerra y se anulan las declaraciones sin aceptar.</li>
 *   <li>La pestaña de guerra simétrica (captura para medir) y con el escudo.</li>
 *   <li>La etiqueta arcoíris en el chat: el jugador escribe y se miran los colores de la etiqueta en dos instantes.</li>
 *   <li>La rueda de los 5 emotes y un vídeo de cada emote (de frente, al atardecer).</li>
 *   <li>El estandarte sin corchetes (arcoíris y de un color).</li>
 *   <li>La música de una protección: dentro se oye (el servidor se lo cuenta y el cliente la pone), fuera se para.</li>
 * </ol>
 */
final class BossTestClanes3 {
    private BossTestClanes3() {}

    private static final String A = "prueba01", B = "prueba02";
    /** Una canción de verdad para la zona (el ogg de un emote de la 1.3.159, en un commit fijo del repo). */
    private static final String SONG = "https://raw.githubusercontent.com/modelqdeluxe-ops/ss/aef616efe956510fe8fe1785ebdabe41d221ae72/"
            + "tf-client/src/main/resources/assets/tfclient/sounds/clanes/emote.ogg";
    private static ServerPlayer rival;
    private static BlockPos bannerPos, zoneCenter;
    private static Claim zone;

    private static String act(ServerPlayer p, String tab, String action) {
        return PadClans.testAction(p, tab, action, "");
    }

    private static BlockPos ground(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

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
                PadClanWar.testReset(A, B);
                PadClanWar.testUnshield(A);
                BlockPos g = ground(level, ax + 3, az + 8);
                rival = TFBotPlayer.spawnTarget(server, level, "Rival_Uno", g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
                if (rival == null) {
                    r.event("clanes3 0: no salió el jugador de prueba (MAL)");
                    return;
                }
                PadClans.testClan(B, "Clan Rival", "RIV", rival.getUUID(), List.of(), "#E04848");
                act(player, "estilo", "tagfx:arcoiris||4");
            }
            // 1. el escudo de guerra
            case 3 -> {
                String d1 = PadClanWar.testDeclare(B, A);
                int before = PadClanWar.testIncoming(A);
                act(player, "tienda", "tienda.comprar:escudo_24h");
                act(player, "tienda", "tienda.comprar:escudo_24h");
                long left = PadClanWar.testShieldLeft(A);
                int after = PadClanWar.testIncoming(A);
                String d2 = PadClanWar.testDeclare(B, A);
                boolean ok = d1 == null && before == 1 && left > 23 * 3_600_000L && left <= 24 * 3_600_000L && after == 0 && d2 != null && d2.contains("escudo");
                r.event("clanes3 1 (escudo de guerra 24 h): declaración antes " + (d1 == null ? "hecha" : d1) + " (" + before + " sin aceptar); comprado: quedan "
                        + Math.round(left / 360_000.0) / 10.0 + " h; sin aceptar ahora " + after + "; declararle otra: «" + d2 + "»" + (ok ? " (bien)" : " (MAL)"));
                BossTest.openPad = "clanes/tienda";
            }
            case 50 -> BossTest.actPad = "§scroll:600";
            case 90 -> BossTest.shot(r, "clanes3_01_tienda_escudos");
            case 92 -> BossTest.openPad = "clanes/guerra";
            case 130 -> BossTest.shot(r, "clanes3_02_guerra_con_escudo");
            // 2. la guerra simétrica
            case 132 -> {
                BossTest.closeScreen = true;
                PadClanWar.testUnshield(A);
                PadClanWar.testReset(A, B);
                String d = PadClanWar.testDeclare(A, B), ac = PadClanWar.testAccept(B, A);
                PadClanWar.testFill(A, 0.45);
                PadClanWar.testFill(B, 0.3);
                r.event("clanes3 2 (guerra para la captura): declarar " + (d == null ? "bien" : d) + ", aceptar " + (ac == null ? "bien" : ac));
                BossTest.openPad = "clanes/guerra";
            }
            case 160 -> BossTest.actPad = "§guerra";
            case 210 -> BossTest.shot(r, "clanes3_03_guerra_simetrica");
            case 212 -> {
                BossTest.closeScreen = true;
                PadClanWar.testReset(A, B);
            }
            // 3. la etiqueta arcoíris al escribir en el chat
            case 220 -> {
                player.refreshDisplayName();
                BossTest.chatSay = "¡Hola, clan!";
            }
            case 250 -> BossTest.shot(r, "clanes3_04_chat_a");
            case 262 -> BossTest.shot(r, "clanes3_05_chat_b");
            case 264 -> BossTest.chatCheck = true;
            case 270 -> {
                String saw = BossTest.clientSaw;
                r.event("clanes3 3 (etiqueta arcoíris en el chat): " + saw + (saw.contains("se anima true") ? " (bien)" : " (MAL)"));
                BossTest.closeScreen = true;
            }
            // 4. la rueda de los emotes
            case 280 -> BossTest.emoteWheel = 2;
            case 320 -> BossTest.shot(r, "clanes3_06_rueda_emotes");
            case 322 -> BossTest.emoteWheel = -2;
            // 5. el estandarte sin corchetes
            case 330 -> {
                act(player, "tienda", "tienda.comprar:estandarte");
                act(player, "tienda", "tienda.comprar:estandarte");
                bannerPos = ground(level, ax - 14, az + 12);
                place(player, level, bannerPos, new ItemStack(ClanBlocks.BANNER_ITEM.get()));
                boolean placed = level.getBlockState(bannerPos).getBlock() == ClanBlocks.BANNER.get();
                ClanLooks.Look l = ClanLooks.clan(A);
                r.event("clanes3 4 (estandarte): puesto " + placed + "; la etiqueta que dibuja: «" + (l == null ? "?" : l.tag()) + "» (sin corchetes)"
                        + (placed ? " (bien)" : " (MAL)"));
                look(player, level, new Vec3(bannerPos.getX() + 0.5, bannerPos.getY(), bannerPos.getZ() - 2.6), new Vec3(bannerPos.getX() + 0.5, bannerPos.getY() + 1.7,
                        bannerPos.getZ() + 0.5));
                BossTest.hideHud = true;
            }
            case 370 -> BossTest.shot(r, "clanes3_07_estandarte_arcoiris");
            case 372 -> act(player, "estilo", "col:#2E7DD8");
            case 410 -> BossTest.shot(r, "clanes3_08_estandarte_un_color");
            case 412 -> {
                act(player, "estilo", "tagfx:arcoiris||4");
                if (bannerPos != null) level.removeBlock(bannerPos, false);
                BossTest.hideHud = false;
            }
            // 6. la música de una protección
            case 420 -> {
                zoneCenter = ground(level, ax + 60, az - 60);
                player.teleportTo(level, zoneCenter.getX() + 0.5, zoneCenter.getY(), zoneCenter.getZ() + 0.5, 0F, 0F);
            }
            case 430 -> {
                zone = ClaimManager.getInstance().createClaim(level, zoneCenter, player, ClaimTier.byId("claimstone_64x64"));
                if (zone == null) {
                    r.event("clanes3 5 (música de la zona): no se pudo crear la zona (MAL)");
                    return;
                }
                PadZoneMusic.testSet(player, zone, SONG, "Canción de prueba", 4650);
                PadZoneMusic.Song s = PadZoneMusic.of(zone);
                r.event("clanes3 5a (poner música en la zona): " + (s == null ? "no quedó puesta (MAL)" : "«" + s.title() + "», volumen " + s.volume() + " % (bien)"));
            }
            case 444 -> {
                UUID heard = PadZoneMusic.testHeard(player);
                r.event("clanes3 5b (dentro de la zona): el servidor le cuenta la zona " + (zone != null && zone.getClaimId().equals(heard))
                        + (zone != null && zone.getClaimId().equals(heard) ? " (bien)" : " (MAL)"));
            }
            case 446 -> BossTest.zoneCheck = true;
            case 540 -> BossTest.zoneCheck = true;
            case 544 -> {
                String z = BossTest.clientZone;
                r.event("clanes3 5c (el cliente, dentro, 5 s después): " + z + (zone != null && z.startsWith(zone.getClaimId().toString()) && z.endsWith("sonando")
                        ? " (bien)" : zone != null && z.startsWith(zone.getClaimId().toString()) ? " (la tiene, pero no suena: ¿no se pudo bajar el link?)" : " (MAL)"));
                // fuera de la zona (40 bloques más allá del borde)
                player.teleportTo(level, zoneCenter.getX() + zone.getRadius() + 40.5, zoneCenter.getY() + 1, zoneCenter.getZ() + 0.5, 0F, 0F);
            }
            case 570 -> {
                UUID heard = PadZoneMusic.testHeard(player);
                BossTest.zoneCheck = true;
                r.event("clanes3 5d (fuera de la zona): el servidor le cuenta " + (heard == null ? "ninguna" : heard) + (heard == null ? " (bien)" : " (MAL)"));
            }
            case 600 -> {
                String z = BossTest.clientZone;
                r.event("clanes3 5e (el cliente, fuera): " + z + (z.equals("ninguna") ? " (bien)" : " (MAL)"));
                player.teleportTo(level, zoneCenter.getX() + 0.5, zoneCenter.getY(), zoneCenter.getZ() + 0.5, 0F, 0F);
                if (zone != null) BossTest.openPad = "protecciones/z:" + zone.getClaimId() + ":u";
            }
            case 650 -> BossTest.shot(r, "clanes3_09_zona_musica");
            case 652 -> {
                if (zone != null) BossTest.actPad = "§zonamusica:" + zone.getClaimId() + ":Zona de prueba";
            }
            case 690 -> BossTest.shot(r, "clanes3_10_musica_elegir");
            case 692 -> {
                BossTest.closeScreen = true;
                if (zone != null) {
                    PadZoneMusic.testClear(zone);
                    ClaimManager.getInstance().removeClaim(level, zone.getCenter());
                }
            }
            // 7. los 5 emotes, en vídeo (de frente, al atardecer)
            case 700 -> {
                BossTest.command(server, "time set 12800");
                BlockPos g = ground(level, ax + 6, az - 6);
                player.teleportTo(level, g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 0F, 12F);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                if (rival != null) rival.teleportTo(level, g.getX() + 40.5, g.getY(), g.getZ() + 0.5, 0F, 0F);
                BossTest.camera = "frente";
                BossTest.hideHud = true;
            }
            case 720, 740, 760, 780, 800 -> BossTest.video = "emote_" + ClanEmote.IDS[(t - 720) / 20];
            case 724, 744, 764, 784, 804 -> ClanEmote.testPlay(player, (t - 724) / 20);
            case 830 -> {
                r.event("clanes3 6 (los 5 emotes): " + (BossTest.video == null ? "grabados en vídeo (video_emote_<nombre>.mp4)" : "NO SE GRABARON (MAL)"));
                BossTest.camera = "primera";
                BossTest.hideHud = false;
                BossTest.command(server, "time set 6000");
            }
            case 840 -> {
                PadClanWar.testReset(A, B);
                PadClanWar.testUnshield(A);
                if (rival != null) TFBotPlayer.removeTarget(rival);
                PadClans.testRemove(B);
                PadClans.testCleanup(player);
            }
            case 850 -> BossTest.next(level);
            default -> { }
        }
    }
}
