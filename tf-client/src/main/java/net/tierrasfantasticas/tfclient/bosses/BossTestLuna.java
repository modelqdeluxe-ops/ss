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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.luna.BloodMobs;
import net.tierrasfantasticas.tfclient.luna.BloodMoon;
import net.tierrasfantasticas.tfclient.luna.client.BloodEyesLayer;
import net.tierrasfantasticas.tfclient.luna.client.BloodMoonClient;
import net.tierrasfantasticas.tfclient.luna.client.BloodMoonScene;
import net.tierrasfantasticas.tfclient.server.TFBotPlayer;

/**
 * (1.3.175) Prueba «luna»: la Luna de Sangre. Con un solo jugador no sale (y la cuenta vuelve a 10); con tres (dos
 * dianas) sale al anochecer con la escena (capturas: la luna que nace, el título, los ojos que se encienden, las
 * explicaciones); después, los ojos rojos de los mobs, el cielo rojo con la luna, el daño mágico (un Élite contra una
 * armadura de diamante frente a un zombi normal), que nadie duerme, los mobs extra, y al amanecer todo se va.
 */
final class BossTestLuna {
    private BossTestLuna() {}

    private static ServerPlayer a, b;
    private static final List<Mob> mobs = new ArrayList<>();
    private static int t0 = -1;
    private static Mob elite, normal;

    private static Vec3 base() {
        return BossTest.arena();
    }

    private static Mob mob(ServerLevel level, EntityType<? extends Mob> type, double x, double y, double z, float yaw) {
        Mob m = type.create(level);
        if (m == null) return null;
        m.moveTo(x, y, z, yaw, 0F);
        m.setYHeadRot(yaw);
        m.yBodyRot = yaw;
        m.setNoAi(true);
        m.setPersistenceRequired();
        level.addFreshEntity(m);
        mobs.add(m);
        return m;
    }

    private static void armor(ServerPlayer p) {
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        // (las dianas no hacen su tick: la armadura no llega sola a sus atributos; 1.ª prueba: los dos golpes, 3,6)
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            var mods = p.getItemBySlot(slot).getAttributeModifiers(slot);
            p.getAttributes().removeAttributeModifiers(mods);
            p.getAttributes().addTransientAttributeModifiers(mods);
        }
        p.setHealth(p.getMaxHealth());
        p.invulnerableTime = 0;
    }

    static void step(int t, MinecraftServer server, ServerLevel level, ServerPlayer player, BossTest.Rep r) {
        Vec3 c = base();
        double x = c.x, y = c.y + 1, z = c.z;
        if (t == 1) {
            BossTest.command(server, "time set 6000");
            BossTest.command(server, "difficulty normal");
            player.teleportTo(level, x, y, z, -90F, 0F);
            BossTest.command(server, "tf luna noches 1");
        }
        // 1) un solo jugador: no sale y la cuenta vuelve a empezar
        if (t == 5) BossTest.command(server, "time set 12900");
        if (t == 30) BossTest.command(server, "time set 13100");
        if (t == 55) {
            boolean ok = !BloodMoon.active() && BloodMoon.lastDusk.startsWith("no sale") && BloodMoon.nightsLeft() == BloodMoon.config().cadaNoches;
            r.event("luna 1 (1 jugador, toca la luna): «" + BloodMoon.lastDusk + "», activa " + BloodMoon.active() + ", faltan " + BloodMoon.nightsLeft()
                    + (ok ? " (bien: no sale y la cuenta vuelve a empezar)" : " (MAL)"));
        }
        // 2) tres jugadores (dos dianas): sale al anochecer, con la escena
        if (t == 60) {
            BossTest.command(server, "time set 6000");
            a = TFBotPlayer.spawnTarget(server, level, "Lunatica", x + 3, y, z + 2);
            b = TFBotPlayer.spawnTarget(server, level, "Sangrienta", x + 3, y, z - 2);
            // (mientras se graba el vídeo, la luna sigue y sus mobs las mataban: invulnerables hasta la prueba del daño)
            if (a != null) a.setInvulnerable(true);
            if (b != null) b.setInvulnerable(true);
            BossTest.command(server, "tf luna noches 1");
            player.teleportTo(level, x, y, z, -90F, 0F);
        }
        if (t == 70) BossTest.command(server, "time set 12900");
        if (t == 95) {
            // (1.3.176) el vídeo de cómo empieza (lo graba el cliente en cuanto se abre la escena)
            BossTest.video = "luna_inicio";
            BossTest.command(server, "time set 13100");
        }
        if (t0 < 0 && t >= 96 && t < 140 && BloodMoon.active()) {
            t0 = t;
            r.event("luna 2 (3 jugadores, " + BloodMoon.online(server) + " conectados): «" + BloodMoon.lastDusk + "», activa " + BloodMoon.active()
                    + ", el cliente: luna " + BloodMoonClient.active() + ", escena abierta " + BloodMoonScene.opened
                    + (BloodMoon.active() && BloodMoonClient.active() && BloodMoonScene.opened >= 1 ? " (bien)" : " (MAL)"));
        }
        if (t == 140 && t0 < 0) {
            r.event("luna 2 (3 jugadores): NO salió: «" + BloodMoon.lastDusk + "» (MAL)");
            t0 = t;
        }
        if (t0 < 0) return;
        int s = t - t0;
        // (las capturas de la escena, si no se grabó el vídeo: con él, la escena ya pasó)
        boolean scene = BloodMoonScene.running();
        switch (s) {
            case 45 -> { if (scene) BossTest.shot(r, "escena_1_nace_la_luna"); }
            case 100 -> { if (scene) BossTest.shot(r, "escena_2_titulo"); }
            case 182 -> { if (scene) BossTest.shot(r, "escena_3_primeros_ojos"); }
            case 248 -> { if (scene) BossTest.shot(r, "escena_4_ojos_y_texto"); }
            case 296 -> { if (scene) BossTest.shot(r, "escena_5_vuelve_el_mundo"); }
            case 345 -> {
                r.event("luna 3 (la escena): abierta " + BloodMoonScene.opened + ", acabada " + BloodMoonScene.finished + ", fotogramas de los mobs "
                        + BloodMoonScene.castRendered + ", sigue " + BloodMoonScene.running()
                        + (BloodMoonScene.finished >= 1 && BloodMoonScene.castRendered > 20 && !BloodMoonScene.running() ? " (bien)" : " (MAL)"));
                // mobs delante del jugador, quietos, mirándolo
                player.teleportTo(level, x, y, z, -90F, 8F);
                Mob z1 = mob(level, EntityType.ZOMBIE, x + 4.5, y, z - 1.4, 90F);
                Mob s1 = mob(level, EntityType.SKELETON, x + 5.2, y, z + 0.1, 90F);
                Mob c1 = mob(level, EntityType.CREEPER, x + 4.6, y, z + 1.6, 90F);
                if (s1 != null) s1.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                if (z1 != null) BloodMobs.force(z1, BloodMobs.Rank.ELITE, BloodMobs.Affix.VAMPIRICO, BloodMobs.Affix.VELOZ);
                if (s1 != null) BloodMobs.force(s1, BloodMobs.Rank.CAMPEON, BloodMobs.Affix.ARDIENTE, BloodMobs.Affix.SOMBRA, BloodMobs.Affix.LIDER);
                BossTest.hideHud = true;
            }
            case 390 -> BossTest.shot(r, "luna_ojos_rojos");
            case 392 -> r.event("luna 4 (ojos rojos): capas pintadas " + BloodEyesLayer.rendered + ", luz de los ojos " + String.format("%.2f", BloodMoonClient.eyes(0F))
                    + ", luna roja dibujada " + BloodMoonClient.moonFrames + " (después de shaders " + BloodMoonClient.moonLate + "), viñeta " + BloodMoonClient.vignetteFrames
                    + ", música puesta " + BloodMoonClient.musicActive() + " (sus sonidos: "
                    + (net.minecraft.client.Minecraft.getInstance().getSoundManager().getSoundEvent(new net.minecraft.resources.ResourceLocation("tfclient", "luna_sangre.musica")) != null ? "sí" : "NO")
                    + "; la de la escena: "
                    + (net.minecraft.client.Minecraft.getInstance().getSoundManager().getSoundEvent(new net.minecraft.resources.ResourceLocation("tfclient", "luna_sangre.escena")) != null ? "sí" : "NO") + ")"
                    + (BloodEyesLayer.rendered > 0 && BloodMoonClient.eyes(0F) > 0.9F && BloodMoonClient.moonFrames > 0 && BloodMoonClient.musicActive() ? " (bien)" : " (MAL)"));
            case 395 -> player.teleportTo(level, x, y, z, -90F, -22F);
            case 420 -> BossTest.shot(r, "luna_cielo_rojo");
            case 425 -> {
                BossTest.hideHud = false;
                player.teleportTo(level, x, y, z, 90F, 10F);
            }
            case 440 -> BossTest.shot(r, "luna_con_interfaz");
            // 5) el daño mágico: el mismo golpe de un zombi normal y de uno Élite contra armadura de diamante
            case 450 -> {
                for (Mob m : mobs) m.discard();
                mobs.clear();
                if (a != null && b != null) {
                    a.setInvulnerable(false);
                    b.setInvulnerable(false);
                    a.teleportTo(level, x + 8, y, z + 8, 0F, 0F);
                    b.teleportTo(level, x + 8, y, z - 8, 0F, 0F);
                    armor(a);
                    armor(b);
                    normal = mob(level, EntityType.ZOMBIE, x + 8.8, y, z + 8, 90F);
                    elite = mob(level, EntityType.ZOMBIE, x + 8.8, y, z - 8, 90F);
                    if (normal != null) BloodMobs.force(normal, BloodMobs.Rank.NORMAL);
                    if (elite != null) BloodMobs.force(elite, BloodMobs.Rank.ELITE);
                }
            }
            case 455 -> {
                if (a != null && b != null && normal != null && elite != null) {
                    int before = BloodMobs.magicHits;
                    float ha = a.getHealth(), hb = b.getHealth();
                    a.invulnerableTime = 0;
                    b.invulnerableTime = 0;
                    normal.doHurtTarget(a);
                    elite.doHurtTarget(b);
                    float la = ha - a.getHealth(), lb = hb - b.getHealth();
                    boolean ok = BloodMobs.magicHits == before + 1 && lb > la * 2.5F && lb > 2F;
                    r.event("luna 5 (daño mágico, armadura de diamante): el zombi normal quita " + String.format("%.2f", la) + ", el Élite "
                            + String.format("%.2f", lb) + " (golpes mágicos " + (BloodMobs.magicHits - before) + ")"
                            + (ok ? " (bien: el Élite atraviesa la armadura)" : " (MAL)"));
                }
            }
            // 6) nadie duerme
            case 460 -> {
                if (a != null) {
                    var res = a.startSleepInBed(a.blockPosition());
                    boolean blocked = res.left().isPresent();
                    r.event("luna 6 (dormir): " + (blocked ? "no le deja (" + res.left().get() + ")" : "SE DUERME") + (blocked ? " (bien)" : " (MAL)"));
                }
            }
            case 470 -> {
                int extra = 0;
                for (Entity e : level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(80), m -> BloodMobs.isMarked(m))) extra++;
                r.event("luna 7 (mobs extra): el evento puso " + BloodMoon.spawned + ", con la marca de la luna cerca " + extra
                        + (BloodMoon.spawned > 0 ? " (bien)" : " (MAL)"));
            }
            // 8) amanece: se acaba y todo vuelve
            case 475 -> BossTest.command(server, "time set 23300");
            case 505 -> {
                int left = 0;
                for (Entity e : level.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(80), m -> BloodMobs.isMarked(m))) left++;
                boolean named = elite != null && elite.isAlive() && elite.hasCustomName();
                boolean ok = !BloodMoon.active() && !BloodMoonClient.active() && left == 0 && !named;
                r.event("luna 8 (amanece): activa " + BloodMoon.active() + ", el cliente " + BloodMoonClient.active() + ", quedan con la marca " + left
                        + ", el Élite " + (elite == null ? "-" : elite.isAlive() ? (named ? "sigue con nombre" : "vuelve a ser normal") : "no está")
                        + ", faltan " + BloodMoon.nightsLeft() + " noches" + (ok ? " (bien)" : " (MAL)"));
            }
            case 515 -> BossTest.shot(r, "luna_amanece");
            case 530 -> {
                if (normal != null) normal.discard();
                if (elite != null) elite.discard();
                if (a != null) TFBotPlayer.removeTarget(a);
                if (b != null) TFBotPlayer.removeTarget(b);
                a = b = null;
                t0 = -1;
                BossTest.command(server, "time set 6000");
                BossTest.next(level);
            }
            default -> {
            }
        }
    }
}
