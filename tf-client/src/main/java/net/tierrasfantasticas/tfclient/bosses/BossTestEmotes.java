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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.clans.ClanEmote;
import net.tierrasfantasticas.tfclient.pad.server.PadClanProgress;
import net.tierrasfantasticas.tfclient.pad.server.PadClans;

/**
 * (1.3.160) Prueba «emotes», corta (el dueño: «no hagas test tan largos»): los emotes de pago y los efectos arcoíris
 * de la etiqueta, bloqueados hasta comprarlos en la tienda del clan; la cuadrícula de emotes; el editor de la etiqueta con
 * los estilos de arcoíris; y un vídeo de cada emote nuevo. Con «[shaders]» en el commit (el dueño: «que los efectos se
 * vean con shaders también»), los 15 emotes en pocos fotogramas cada uno (con shaders la máquina va a 1-2 por segundo).
 * OJO: el workflow solo sale si el push cambia algo dentro de tf-client/ (un commit vacío no la lanza).
 */
final class BossTestEmotes {
    private BossTestEmotes() {}

    private static final String A = "prueba01";
    private static final boolean SHADERS = System.getenv("TFCLIENT_PRUEBA_SHADERS") != null;

    private static String act(ServerPlayer p, String tab, String action) {
        return PadClans.testAction(p, tab, action, "");
    }

    private static BlockPos ground(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    static void step(int t, MinecraftServer server, ServerLevel level, ServerPlayer player, BossTest.Rep r) {
        Vec3 arena = BossTest.arena();
        int ax = (int) arena.x, az = (int) arena.z;
        int first = SHADERS ? 0 : ClanEmote.FREE, count = ClanEmote.IDS.length - first;
        int vStart = 200, vGap = 20, vEnd = vStart + count * vGap;
        if (t >= vStart && t < vEnd) {
            int k = (t - vStart) / vGap, at = (t - vStart) % vGap;
            if (at == 0) BossTest.video = "emote_" + ClanEmote.IDS[first + k];
            if (at == 4) ClanEmote.testPlay(player, first + k);
            return;
        }
        switch (t) {
            case 1 -> {
                player.teleportTo(level, ax + 0.5, ground(level, ax, az).getY(), az + 0.5, 0F, 0F);
                PadClans.testRanks(player);
                PadClanProgress.testSet(A, 400, 5, 0, 100);
                ClanEmote.testUnlockAll(player, false);
                // sin comprar: los de pago y el arcoíris, bloqueados
                boolean free = ClanEmote.testOwns(player, 0), paid = ClanEmote.testOwns(player, 7);
                act(player, "estilo", "tagfx:arcoiris||4");
                String fx = PadClans.testFx(A);
                r.event("emotes 1 (sin comprar): los gratis " + free + ", uno de pago " + paid + "; guardar arcoíris deja «" + fx + "»"
                        + (free && !paid && !fx.startsWith("arcoiris") ? " (bien: bloqueados)" : " (MAL)"));
                BossTest.openPad = "clanes/estilo";
            }
            case 30 -> BossTest.actPad = "§color:clanes:estilo:55AAFF:PRB:solido|55AAFF|2:0";
            case 60 -> BossTest.shot(r, "emotes_01_editor_arcoiris_bloqueado");
            // comprar en la tienda del clan: tres emotes y los efectos arcoíris
            case 62 -> {
                BossTest.closeScreen = true;
                for (String key : new String[] {"emote_fenix", "emote_tornado", "emote_meteoro", "efectos_arcoiris"}) {
                    act(player, "tienda", "tienda.comprar:" + key);
                    act(player, "tienda", "tienda.comprar:" + key);
                }
                boolean fenix = ClanEmote.testOwns(player, 5), torn = ClanEmote.testOwns(player, 6), met = ClanEmote.testOwns(player, 8),
                        ven = ClanEmote.testOwns(player, 7);
                act(player, "estilo", "tagfx:arcoiris||4|26");
                String fx = PadClans.testFx(A);
                r.event("emotes 2 (tienda del clan): Fénix " + fenix + ", Tornado " + torn + ", Meteoro " + met + " (Ventisca sin comprar " + !ven
                        + "); arcoíris estilo 26 guardado «" + fx + "»" + (fenix && torn && met && !ven && fx.startsWith("arcoiris") && fx.endsWith("|26")
                        ? " (bien)" : " (MAL)"));
                BossTest.openPad = "clanes/tienda";
            }
            case 90 -> BossTest.actPad = "§scroll:2000";
            case 120 -> BossTest.shot(r, "emotes_02_tienda_emotes");
            case 122 -> BossTest.openPad = "clanes/estilo";
            case 150 -> BossTest.actPad = "§color:clanes:estilo:55AAFF:PRB:arcoiris|55AAFF|4|26:1";
            case 180 -> BossTest.shot(r, "emotes_03_editor_estilos");
            case 182 -> {
                BossTest.closeScreen = true;
                BossTest.emoteWheel = 7;
            }
            case 196 -> BossTest.shot(r, "emotes_04_cuadricula");
            case 198 -> {
                BossTest.emoteWheel = -2;
                // los vídeos: todos los emotes del clan, de frente y al atardecer
                ClanEmote.testUnlockAll(player, true);
                BossTest.command(server, "time set 12800");
                BlockPos g = ground(level, ax + 6, az - 6);
                player.teleportTo(level, g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 0F, 12F);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                BossTest.camera = "frente";
                BossTest.hideHud = true;
            }
            default -> {
                if (t == vEnd + 10) {
                    r.event("emotes 3 (" + count + " emotes" + (SHADERS ? ", con shaders, en fotogramas sueltos" : "") + "): "
                            + (BossTest.video == null ? "grabados (video_emote_<nombre>.mp4)" : "NO SE GRABARON (MAL)")
                            + "; efectos dibujados después del paquete en " + net.tierrasfantasticas.tfclient.clans.client.ClanEmoteClient.lateFrames + " fotogramas");
                    BossTest.camera = "primera";
                    BossTest.hideHud = false;
                    BossTest.command(server, "time set 6000");
                    PadClans.testCleanup(player);
                }
                if (t == vEnd + 20) BossTest.next(level);
            }
        }
    }
}
