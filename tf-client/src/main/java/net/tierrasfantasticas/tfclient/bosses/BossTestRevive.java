/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.bosses;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.claims.data.GlobalFlags;
import net.tierrasfantasticas.tfclient.pad.server.TFReload;
import net.tierrasfantasticas.tfclient.revive.DownManager;
import net.tierrasfantasticas.tfclient.revive.DownState;
import net.tierrasfantasticas.tfclient.revive.TFRevive;
import net.tierrasfantasticas.tfclient.server.TFBotPlayer;

/**
 * (1.3.157) La prueba en el juego de revivir ([prueba-jefes=revivir]): el Revive Mod del dueño dentro del TF Client y el
 * remate. Con «dianas» (jugadores de prueba que reciben daño, {@link TFBotPlayer#spawnTarget}) delante del jugador:
 * <ol>
 *   <li>un zombi «mata» a la diana A: queda noqueada (viva, tumbada, con la barra) y el zombi no la sigue;</li>
 *   <li>con mobsCanFinish = false, el zombi no le hace nada;</li>
 *   <li>con playersCanFinish = false, el jugador tampoco;</li>
 *   <li>con el PvP del servidor apagado (TF Claims) y playersCanFinish = true, el golpe del jugador no llega;</li>
 *   <li>se cambia el archivo config/tfclient/revivir.json a mano (mobsCanFinish true) y /tf reload: el zombi la remata;</li>
 *   <li>la diana B, noqueada por el jugador, rematada por el jugador de un espadazo de verdad (Player.attack): muere,
 *   el asesino es el jugador y ya no está noqueada;</li>
 *   <li>la diana C, noqueada, la revive el jugador (como con clic derecho) en reviveTimeTicks: vuelve con reviveHealth;</li>
 *   <li>/revive kill (forceDeath) y el propio jugador noqueado con su pantalla (captura del HUD y la barra).</li>
 * </ol>
 */
final class BossTestRevive {
    private BossTestRevive() {}

    private static final List<ServerPlayer> TARGETS = new ArrayList<>();
    private static ServerPlayer a, b, c, d;
    private static Zombie zombie;
    private static int reviveStart;
    private static boolean cDownAtStart;
    private static String fileBefore;

    private static ServerPlayer target(MinecraftServer server, ServerLevel level, String name, double dx, double dz) {
        Vec3 arena = BossTest.arena();
        ServerPlayer p = TFBotPlayer.spawnTarget(server, level, name, arena.x + dx, arena.y, arena.z + dz);
        if (p != null) {
            TARGETS.add(p);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        }
        return p;
    }

    private static String state(ServerPlayer p) {
        if (p == null) return "(no hay diana)";
        return (p.isAlive() ? "viva" : "muerta") + ", " + (DownManager.isDown(p) ? "noqueada" : "no noqueada") + ", vida " + Math.round(p.getHealth());
    }

    /** Un golpe de verdad (con su origen), sin la espera de invulnerabilidad de Minecraft entre golpes. */
    private static boolean hit(ServerPlayer victim, net.minecraft.world.damagesource.DamageSource src, float amount) {
        victim.invulnerableTime = 0;
        return victim.hurt(src, amount);
    }

    /** Al entrar, un jugador es invulnerable 60 ticks (spawnInvulnerableTime): las dianas se ponen antes de golpearlas. */
    private static final int READY = 80;

    static void step(int t, MinecraftServer server, ServerLevel level, ServerPlayer player, BossTest.Rep r) {
        Vec3 arena = BossTest.arena();
        switch (t) {
            case 1 -> {
                reviveStart = 0;
                cDownAtStart = false;
                TARGETS.clear();
                player.teleportTo(level, arena.x, arena.y, arena.z, 0F, 10F);
                r.event("revivir 0 (el revivir del TF Client está encendido): " + (TFRevive.active() ? "sí (bien)" : "NO (MAL)"));
                try {
                    Path f = TFRevive.testPath();
                    fileBefore = f != null && Files.exists(f) ? Files.readString(f) : null;
                } catch (Exception e) {
                    fileBefore = null;
                }
                TFRevive.testSet(true, false);
                r.event("revivir 0b (config por defecto, " + TFRevive.FILE + "): playersCanFinish " + TFRevive.getConfig().playersCanFinish
                        + ", mobsCanFinish " + TFRevive.getConfig().mobsCanFinish + ", downTimeSeconds " + TFRevive.getConfig().downTimeSeconds);
                a = target(server, level, "Diana_A", -1.5, 3);
                b = target(server, level, "Diana_B", 0, 2.2);
                // (lejos de B: el barrido de la espada al rematar a B también la remataba)
                c = target(server, level, "Diana_C", -2.2, -1);
                d = target(server, level, "Diana_D", 3, 3);
                zombie = EntityType.ZOMBIE.create(level);
                if (zombie != null) {
                    zombie.moveTo(arena.x - 3, arena.y, arena.z + 4, 0F, 0F);
                    zombie.setNoAi(true);
                    zombie.setPersistenceRequired();
                    zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(zombie.blockPosition()), MobSpawnType.COMMAND, null, null);
                    level.addFreshEntity(zombie);
                }
            }
            case READY -> {
                if (a == null || b == null || c == null || d == null || zombie == null) {
                    r.event("revivir 1: NO se pudieron poner las dianas o el zombi (MAL)");
                    return;
                }
                hit(a, a.damageSources().mobAttack(zombie), 1000F);
                zombie.setTarget(a);
                boolean ok = a.isAlive() && DownManager.isDown(a);
                r.event("revivir 1 (un zombi le hace 1000 de daño a la diana A): " + state(a)
                        + (ok ? " (bien: noqueada, no muerta)" : " (MAL)") + "; el zombi la sigue: " + (zombie.getTarget() == a ? "sí (MAL)" : "no (bien)"));
            }
            case READY + 30 -> BossTest.shot(r, "revivir_1_noqueada");
            case READY + 40 -> {
                boolean took = hit(a, a.damageSources().mobAttack(zombie), 10F);
                boolean ok = a.isAlive() && DownManager.isDown(a);
                r.event("revivir 2 (mobsCanFinish false: el zombi le pega 10): " + state(a) + ", golpe " + (took ? "aceptado" : "cancelado")
                        + (ok ? " (bien: no la remata)" : " (MAL)"));
            }
            case READY + 45 -> {
                TFRevive.testSet(false, false);
                boolean took = hit(a, a.damageSources().playerAttack(player), 10F);
                boolean ok = a.isAlive() && DownManager.isDown(a);
                r.event("revivir 3 (playersCanFinish false: el jugador le pega 10): " + state(a) + ", golpe " + (took ? "aceptado" : "cancelado")
                        + (ok ? " (bien: no la remata)" : " (MAL)"));
                TFRevive.testSet(true, false);
            }
            case READY + 50 -> {
                GlobalFlags.getInstance().globalPVP = false;
                hit(a, a.damageSources().playerAttack(player), 10F);
                GlobalFlags.getInstance().globalPVP = true;
                boolean ok = a.isAlive() && DownManager.isDown(a);
                r.event("revivir 4 (PvP del servidor apagado, playersCanFinish true: el jugador le pega 10): " + state(a)
                        + (ok ? " (bien: las protecciones mandan)" : " (MAL: la remató sin PvP)"));
            }
            case READY + 55 -> {
                // como el dueño: cambia el archivo a mano y /tf reload
                Path f = TFRevive.testPath();
                String how;
                try {
                    String json = Files.readString(f).replace("\"mobsCanFinish\": false", "\"mobsCanFinish\": true");
                    Files.writeString(f, json);
                    TFReload.Result res = TFReload.all();
                    how = "recargado: " + res.done().contains("revivir") + (res.problems().isEmpty() ? "" : ", avisos " + res.problems());
                } catch (Exception e) {
                    how = "error " + e;
                }
                boolean cfgOk = TFRevive.getConfig().mobsCanFinish && TFRevive.getConfig().playersCanFinish;
                hit(a, a.damageSources().mobAttack(zombie), 10F);
                boolean ok = !a.isAlive() && !DownManager.isDown(a);
                r.event("revivir 5 (archivo con mobsCanFinish true y /tf reload; " + how + "; config: mobs " + TFRevive.getConfig().mobsCanFinish
                        + "): el zombi le pega 10: " + state(a) + (cfgOk && ok ? " (bien: la remata)" : " (MAL)"));
                TFRevive.testSet(true, false);
            }
            case READY + 60 -> {
                hit(b, b.damageSources().playerAttack(player), 1000F);
                hit(c, c.damageSources().mobAttack(zombie), 1000F);
                hit(d, d.damageSources().mobAttack(zombie), 1000F);
                r.event("revivir 6 (el jugador «mata» a la diana B; un zombi a C y a D): B " + state(b) + ", C " + state(c) + ", D " + state(d)
                        + (DownManager.isDown(b) && DownManager.isDown(c) && DownManager.isDown(d) ? " (bien: las tres noqueadas)" : " (MAL)"));
            }
            case READY + 100 -> {
                // un espadazo de verdad, como en el juego
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
                b.invulnerableTime = 0;
                player.attack(b);
                boolean ok = !b.isAlive() && !DownManager.isDown(b);
                ServerPlayer killer = b.getKillCredit() instanceof ServerPlayer k ? k : null;
                r.event("revivir 7 (playersCanFinish true: el jugador remata a B de un espadazo): " + state(b)
                        + ", asesino " + (killer == null ? "ninguno" : killer.getGameProfile().getName())
                        + (ok && killer == player ? " (bien)" : " (MAL)"));
            }
            case READY + 105 -> {
                reviveStart = t;
                cDownAtStart = DownManager.isDown(c);
                r.event("revivir 8 (el jugador empieza a revivir a C, " + state(c) + ", a " + String.format("%.1f", player.distanceTo(c))
                        + " bloques; reviveTimeTicks " + TFRevive.getConfig().reviveTimeTicks + ")");
            }
            case READY + 230 -> {
                DownManager.forceDeath(d, d.damageSources().genericKill());
                r.event("revivir 10 (/revive kill a D): " + state(d) + (!d.isAlive() && !DownManager.isDown(d) ? " (bien)" : " (MAL)"));
            }
            case READY + 240 -> {
                // el jugador de la prueba noqueado: su pantalla (la barra «Desangrándose» y las teclas)
                DownManager.knockDown(player, player.damageSources().generic());
                r.event("revivir 11 (el jugador noqueado): " + (DownManager.isDown(player) ? "noqueado (bien)" : "NO (MAL)"));
            }
            case READY + 290 -> BossTest.shot(r, "revivir_2_pantalla_noqueado");
            case READY + 330 -> {
                DownManager.revive(player);
                r.event("revivir 12 (el jugador revivido): " + (DownManager.isDown(player) ? "sigue noqueado (MAL)" : "de pie (bien)"));
            }
            // las teclas: la fila de Ajustes del pad y su pantalla
            case READY + 340 -> BossTest.openPad = "ajustes";
            case READY + 390 -> BossTest.shot(r, "revivir_4_ajustes_pad");
            case READY + 400 -> {
                BossTest.closeScreen = true;
                BossTest.openMenu = "revivir-teclas";
            }
            case READY + 440 -> BossTest.shot(r, "revivir_5_pantalla_teclas");
            case READY + 450 -> {
                BossTest.closeScreen = true;
                for (ServerPlayer p : TARGETS) TFBotPlayer.removeTarget(p);
                TARGETS.clear();
                if (zombie != null) zombie.discard();
                try {
                    if (fileBefore != null) Files.writeString(TFRevive.testPath(), fileBefore);
                    TFRevive.reloadConfig();
                } catch (Exception ignored) {
                    // (la prueba deja la config como estaba)
                }
            }
            case READY + 460 -> BossTest.next(level);
            default -> {
            }
        }
        // revivir a C como con clic derecho: la ventana del que revive se renueva mientras mantiene
        if (c != null && reviveStart > 0 && t >= reviveStart && t < reviveStart + 120 && DownManager.isDown(c)) {
            DownState st = DownManager.get(c);
            if (st != null) st.reviverWindow.put(player.getUUID(), 12);
            if (t == reviveStart + 40) BossTest.shot(r, "revivir_3_reviviendo");
        }
        if (c != null && reviveStart > 0 && t > reviveStart && !DownManager.isDown(c) && c.getPersistentData().getInt("tf_prueba_revivida") == 0) {
            c.getPersistentData().putInt("tf_prueba_revivida", t);
            int took = t - reviveStart;
            boolean ok = cDownAtStart && c.isAlive() && Math.abs(c.getHealth() - TFRevive.getConfig().reviveHealth) < 0.01F;
            r.event("revivir 9 (C revivida en " + took + " ticks): " + state(c) + (ok && took <= TFRevive.getConfig().reviveTimeTicks + 3 ? " (bien)" : " (MAL)"));
        }
    }
}
