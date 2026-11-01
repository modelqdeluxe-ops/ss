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
    public static final long COOLDOWN_MS = 6_000;
    /** Los emotes (el id va por la red: el orden no se cambia; uno nuevo, al final). */
    public static final String[] IDS = {"estandarte", "corona", "trueno", "fiesta", "orbita"};
    public static final String[] NAMES = {"Estandarte", "Corona", "Trueno", "Fiesta", "Órbita"};
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private ClanEmote() {}

    static void ask(ServerPlayer p, int emote) {
        if (emote < 0 || emote >= IDS.length) return;
        if (PadClans.clanIdOf(p.getUUID()) == null) {
            p.displayClientMessage(Component.literal("Los emotes son de los clanes: únete a uno (pad › Clanes).").withStyle(ChatFormatting.RED), true);
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

    /** Para la prueba en el juego: ese emote de ese jugador, sin esperar. */
    public static void testPlay(ServerPlayer p, int emote) {
        LAST.remove(p.getUUID());
        ask(p, emote);
    }
}
