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
 * lo usemos con una tecla que asignemos; animado con físicas, sonido o música y su logo del clan dinámico: si lo cambio,
 * se pone el que cambié; quiero algo chingón») El emote del clan: «¡Por el clan!». Solo para los de un clan (es su
 * insignia y su etiqueta, las de ahora). La tecla (Opciones › Controles › TF Client › Emote del clan, B de fábrica) lo
 * pide; el servidor mira que esté en un clan y que no lo repita antes de 6 s, y lo manda a él y a los que lo ven
 * (ClanEmoteClient lo anima y suena en cada uno).
 */
public final class ClanEmote {
    public static final long COOLDOWN_MS = 6_000;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private ClanEmote() {}

    static void ask(ServerPlayer p) {
        if (PadClans.clanIdOf(p.getUUID()) == null) {
            p.displayClientMessage(Component.literal("El emote es de los clanes: únete a uno (pad › Clanes).").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (p.isSpectator() || p.isSleeping() || !p.isAlive()) return;
        long now = System.currentTimeMillis();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < COOLDOWN_MS) return;
        LAST.put(p.getUUID(), now);
        ClanLooks.dirty();
        ClanNet.sendEmote(p);
    }

    /** Para la prueba en el juego: el emote de ese jugador, sin esperar. */
    public static void testPlay(ServerPlayer p) {
        LAST.remove(p.getUUID());
        ask(p);
    }
}
