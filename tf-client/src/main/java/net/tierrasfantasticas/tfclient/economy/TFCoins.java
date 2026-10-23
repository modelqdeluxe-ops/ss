/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.items.TFItems;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.pad.server.PadGreen;

/**
 * Las monedas como objeto (tfclient:fantastic_coin, la de oro, y tfclient:fantastic_coin_green, la verde) son solo su
 * icono: el saldo de verdad lo lleva el servidor ({@link TFEconomy} para las de oro, {@link PadGreen} para las verdes).
 * Si un kit, una recompensa, un premio o la tienda «da» una de estas monedas, no se entrega el objeto: se suma al
 * monedero del jugador. Aquí se reconocen y se ingresan.
 */
public final class TFCoins {
    public enum Kind { NONE, GOLD, GREEN }

    private TFCoins() {}

    /** ¿Es una moneda? GOLD (la de oro, la economía del servidor), GREEN (la verde, la del Gachapón) o NONE. */
    public static Kind kind(ItemStack s) {
        if (s == null || s.isEmpty() || TFItems.COIN == null || TFItems.GREEN_COIN == null) return Kind.NONE;
        if (s.is(TFItems.COIN.get())) return Kind.GOLD;
        if (s.is(TFItems.GREEN_COIN.get())) return Kind.GREEN;
        return Kind.NONE;
    }

    public static boolean isCoin(ItemStack s) {
        return kind(s) != Kind.NONE;
    }

    /**
     * Si el objeto es una moneda, la ingresa (tantas como su cantidad) en el monedero del jugador y devuelve true (el
     * objeto no se da). Si no es una moneda, no hace nada y devuelve false (hay que dar el objeto). Si la economía falla,
     * avisa en el registro y en el pad, y también devuelve true: una moneda nunca se entrega como objeto.
     */
    public static boolean credit(ServerPlayer player, ItemStack s) {
        Kind k = kind(s);
        if (k == Kind.NONE) return false;
        long n = s.getCount();
        if (n <= 0) return true;
        if (k == Kind.GREEN) {
            PadGreen.add(player, n);
            return true;
        }
        if (TFEconomy.give(player.getServer(), player.getUUID(), player.getGameProfile().getName(), n)) {
            TFPadNet.sendState(player);
        } else {
            TFClient.LOGGER.warn("TF Economía: no se pudieron ingresar {} monedas a {}", n, player.getGameProfile().getName());
            TFPadNet.notice(player, "No se pudieron ingresar " + n + " monedas. Avisa al staff.");
        }
        return true;
    }
}
