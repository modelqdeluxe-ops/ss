/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.tierrasfantasticas.tfclient.pad.server.PadClans;

/**
 * (1.3.159, el dueño: «quiero un emote exclusivo del clan, con animación, sonido, y que muestre la insignia del clan, que
 * lo usemos con una tecla que asignemos») El emote del clan. (1.3.160, el dueño: «el emote no me gusta, necesito más de
 * 1, mínimo 5 distintos, ninguno igual ni música igual») Cinco: Estandarte, Corona, Trueno, Fiesta y Órbita, cada uno
 * con su animación, sus efectos y su música (ClanEmotes en el cliente). Solo para los de un clan (es su insignia y su
 * etiqueta, las de ahora). La tecla (Opciones › Controles › TF Client › Emotes del clan, B de fábrica) abre la rueda
 * para elegir; el servidor mira que esté en un clan y que no lo repita antes de 6 s, y lo manda a él y a los que lo ven.
 */
public final class ClanEmote {
    public static final long COOLDOWN_MS = 7_000;
    /**
     * (1.3.161, el dueño: «el sonido, el volumen y la animación son para el jugador y la persona cercana a él; nadie que no
     * esté cerca suyo lo podrá ver ni escuchar») a cuántos bloques se manda (y el sonido se apaga a los 16: sounds.json).
     */
    public static final double RANGE = 24;
    /**
     * Los emotes (el número va por la red: el orden no se cambia; uno nuevo, al final). Los FREE primeros los tiene todo
     * clan; (1.3.160, el dueño: «mete muchos más emotes, que sean comprables en la tienda del clan») los demás se compran
     * en Clanes › TIENDA (oferta «emote» de clanes.json) y son del clan entero.
     */
    public static final String[] IDS = {"estandarte", "corona", "trueno", "fiesta", "orbita",
            "fenix", "tornado", "ventisca", "meteoro", "fuegos", "espadas", "aura", "corazones", "concierto", "portal"};
    public static final String[] NAMES = {"Estandarte", "Corona", "Trueno", "Fiesta", "Órbita",
            "Fénix", "Tornado", "Ventisca", "Meteoro", "Fuegos artificiales", "Espadas", "Aura", "Corazones", "Concierto", "Portal"};
    /** Lo que se ve en la rueda y en la tienda (un objeto del juego). */
    public static final String[] ICONS = {"minecraft:white_banner", "minecraft:golden_helmet", "minecraft:lightning_rod", "minecraft:firework_rocket",
            "minecraft:ender_eye", "minecraft:blaze_powder", "minecraft:feather", "minecraft:snowball", "minecraft:fire_charge", "minecraft:firework_star",
            "minecraft:iron_sword", "minecraft:glowstone_dust", "minecraft:pink_dye", "minecraft:note_block", "minecraft:ender_pearl"};
    public static final int FREE = 5;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    /** El número del emote con ese id, o -1. */
    public static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }

    /** ¿Tiene el clan ese emote? (los FREE primeros, siempre; los demás, si los compró) */
    public static boolean owns(com.google.gson.JsonObject clan, int emote) {
        if (emote < 0 || emote >= IDS.length) return false;
        if (emote < FREE) return true;
        if (clan == null || !clan.has("emotes") || !clan.get("emotes").isJsonArray()) return false;
        for (com.google.gson.JsonElement e : clan.getAsJsonArray("emotes")) {
            if (e.isJsonPrimitive() && IDS[emote].equals(e.getAsString())) return true;
        }
        return false;
    }

    /** Se lo da al clan (desde la tienda del clan). */
    public static void unlock(com.google.gson.JsonObject clan, int emote) {
        if (clan == null || emote < FREE || emote >= IDS.length || owns(clan, emote)) return;
        com.google.gson.JsonArray a = clan.has("emotes") && clan.get("emotes").isJsonArray() ? clan.getAsJsonArray("emotes") : new com.google.gson.JsonArray();
        a.add(IDS[emote]);
        clan.add("emotes", a);
        ClanLooks.dirty();
    }

    /** Los que tiene el clan, como bits (el bit i, el emote i): va a los TF Client con ClanLooks. */
    public static int mask(com.google.gson.JsonObject clan) {
        int m = 0;
        for (int i = 0; i < IDS.length; i++) if (owns(clan, i)) m |= 1 << i;
        return m;
    }

    private ClanEmote() {}

    static void ask(ServerPlayer p, int emote) {
        if (emote < 0 || emote >= IDS.length) return;
        String clanId = PadClans.clanIdOf(p.getUUID());
        if (clanId == null) {
            p.displayClientMessage(Component.literal("Los emotes son de los clanes: únete a uno (pad › Clanes).").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!owns(PadClans.clanForEvents(clanId), emote)) {
            p.displayClientMessage(Component.literal("«" + NAMES[emote] + "» se compra en pad › Clanes › TIENDA.").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (p.isSpectator() || p.isSleeping() || !p.isAlive()) return;
        long now = System.currentTimeMillis();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < COOLDOWN_MS) return;
        LAST.put(p.getUUID(), now);
        ClanLooks.dirty();
        ClanNet.sendEmote(p, emote);
    }

    /** Para la prueba en el juego: ¿lo puede hacer? (como al pedirlo) */
    public static boolean testOwns(ServerPlayer p, int emote) {
        String id = PadClans.clanIdOf(p.getUUID());
        return id != null && owns(PadClans.clanForEvents(id), emote);
    }

    /** Para la prueba: el clan de p tiene todos los emotes (o ninguno de pago con all = false). */
    public static void testUnlockAll(ServerPlayer p, boolean all) {
        String id = PadClans.clanIdOf(p.getUUID());
        com.google.gson.JsonObject c = id == null ? null : PadClans.clanForEvents(id);
        if (c == null) return;
        c.remove("emotes");
        if (all) for (int i = FREE; i < IDS.length; i++) unlock(c, i);
        // (y los efectos arcoíris de la etiqueta, que también se compran)
        if (all) c.addProperty("arcoiris", true);
        else c.remove("arcoiris");
        ClanLooks.dirty();
    }

    /** Para la prueba en el juego: ese emote de ese jugador, sin esperar. */
    public static void testPlay(ServerPlayer p, int emote) {
        LAST.remove(p.getUUID());
        ask(p, emote);
    }
}
