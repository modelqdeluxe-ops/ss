/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.server;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Lo poco de Bukkit que hace falta en Mohist (por reflexión: sin Bukkit, Forge solo, todo devuelve null y se usa lo de
 * Minecraft). 1.3.119, el dueño: «usamos Multiverse, el spawn está en otro overworld: tiene que funcionar bien entre
 * dimensiones, los tp; el pad ligado a los hogares y los permisos».
 * <ul>
 *   <li>{@link #playerCommand}: un comando como si lo escribiera el jugador (Player.performCommand), para /home, /warp
 *   y /spawn de EssentialsX: así viajan entre los mundos de Multiverse como siempre.</li>
 *   <li>{@link #hasCommand}: si un plugin tiene ese comando (Bukkit.getPluginCommand).</li>
 *   <li>{@link #worldName}: el nombre de Bukkit del mundo en el que está («world», «spawn»…, lo que guarda EssentialsX).</li>
 *   <li>{@link #hasPermission}: su permiso de Bukkit (LuckPerms); null sin Bukkit.</li>
 * </ul>
 */
public final class TFBukkit {
    private static boolean looked;
    private static Method getPlayer, performCommand, getPluginCommand, getWorld, getName, hasPermission;

    private TFBukkit() {}

    private static boolean find() {
        if (!looked) {
            looked = true;
            try {
                Class<?> b = Class.forName("org.bukkit.Bukkit");
                ClassLoader cl = b.getClassLoader();
                getPlayer = b.getMethod("getPlayer", UUID.class);
                getPluginCommand = b.getMethod("getPluginCommand", String.class);
                Class<?> player = Class.forName("org.bukkit.entity.Player", false, cl);
                performCommand = player.getMethod("performCommand", String.class);
                getWorld = Class.forName("org.bukkit.entity.Entity", false, cl).getMethod("getWorld");
                getName = Class.forName("org.bukkit.World", false, cl).getMethod("getName");
                hasPermission = Class.forName("org.bukkit.permissions.Permissible", false, cl).getMethod("hasPermission", String.class);
            } catch (Throwable t) {
                getPlayer = null;
            }
        }
        return getPlayer != null;
    }

    /** ¿Es un servidor con Bukkit (Mohist)? */
    public static boolean present() {
        return find();
    }

    private static Object player(ServerPlayer p) throws Exception {
        return getPlayer.invoke(null, p.getUUID());
    }

    /** El comando como el jugador: true/false lo que diga Bukkit; null si no hay Bukkit o no está ese jugador. */
    public static Boolean playerCommand(ServerPlayer p, String command) {
        if (!find()) return null;
        try {
            Object bp = player(p);
            if (bp == null) return null;
            return (Boolean) performCommand.invoke(bp, command);
        } catch (Throwable t) {
            TFClient.LOGGER.warn("TF Bukkit: «/{}» de {} falló: {}", command, p.getGameProfile().getName(), t.toString());
            return false;
        }
    }

    /** ¿Algún plugin tiene el comando /name? (false sin Bukkit) */
    public static boolean hasCommand(String name) {
        if (!find()) return false;
        try {
            return getPluginCommand.invoke(null, name) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** El nombre de Bukkit de su mundo, o null sin Bukkit. */
    public static String worldName(ServerPlayer p) {
        if (!find()) return null;
        try {
            Object bp = player(p);
            return bp == null ? null : (String) getName.invoke(getWorld.invoke(bp));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Su permiso de Bukkit (LuckPerms), o null sin Bukkit. */
    public static Boolean hasPermission(ServerPlayer p, String perm) {
        if (!find()) return null;
        try {
            Object bp = player(p);
            return bp == null ? null : (Boolean) hasPermission.invoke(bp, perm);
        } catch (Throwable t) {
            return null;
        }
    }
}
