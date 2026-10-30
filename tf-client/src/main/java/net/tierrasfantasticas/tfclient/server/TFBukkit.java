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

    // -------------------------------------------------------------------------------------------------------------
    // (1.3.154) El chat del clan en Mohist: el chat público de los plugins (EssentialsX Chat) va por el
    // AsyncPlayerChatEvent de Bukkit, no por Minecraft. Un oyente de Bukkit hecho por reflexión (con un Proxy de
    // Listener y EventExecutor, registrado a nombre de un plugin que ya está activo) quita de los destinatarios a quien
    // tiene el chat del clan puesto y, si un mensaje del chat del clan llegara hasta ahí, lo cancela (no lo ve ningún
    // plugin más) y lo manda al clan.
    // -------------------------------------------------------------------------------------------------------------

    private static boolean chatRegistered;

    /**
     * Registra el filtro del chat (una vez, al arrancar el servidor). hides(destinatario, remitente): no debe verlo;
     * divert(remitente, texto): es del chat del clan (true = cancelar el evento). Sin Bukkit no hace nada.
     */
    public static void registerChatFilter(java.util.function.BiPredicate<UUID, UUID> hides, java.util.function.BiPredicate<UUID, String> divert) {
        if (chatRegistered || !find()) return;
        try {
            Class<?> b = Class.forName("org.bukkit.Bukkit");
            ClassLoader cl = b.getClassLoader();
            Object pm = b.getMethod("getPluginManager").invoke(null);
            Class<?> pluginC = Class.forName("org.bukkit.plugin.Plugin", false, cl);
            Class<?> listenerC = Class.forName("org.bukkit.event.Listener", false, cl);
            Class<?> executorC = Class.forName("org.bukkit.plugin.EventExecutor", false, cl);
            Class<?> eventC = Class.forName("org.bukkit.event.player.AsyncPlayerChatEvent", false, cl);
            Class<?> priorityC = Class.forName("org.bukkit.event.EventPriority", false, cl);
            Class<?> playerC = Class.forName("org.bukkit.entity.Player", false, cl);
            Object plugin = null;
            Method enabled = pluginC.getMethod("isEnabled");
            Method pluginName = pluginC.getMethod("getName");
            for (Object p : (Object[]) pm.getClass().getMethod("getPlugins").invoke(pm)) {
                if (!(Boolean) enabled.invoke(p)) continue;
                String n = String.valueOf(pluginName.invoke(p));
                if (plugin == null || n.equals("LuckPerms") || n.equals("Essentials")) plugin = p;
            }
            if (plugin == null) {
                TFClient.LOGGER.warn("TF Clanes: hay Bukkit pero ningún plugin activo: el filtro del chat de Bukkit no se pudo poner");
                return;
            }
            Method getPlayer = eventC.getMethod("getPlayer");
            Method getMessage = eventC.getMethod("getMessage");
            Method getRecipients = eventC.getMethod("getRecipients");
            Method setCancelled = eventC.getMethod("setCancelled", boolean.class);
            Method uuidOf = playerC.getMethod("getUniqueId");
            Object listener = java.lang.reflect.Proxy.newProxyInstance(cl, new Class<?>[] {listenerC}, (proxy, m, args) -> switch (m.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "TFClanChatListener";
                default -> null;
            });
            Object executor = java.lang.reflect.Proxy.newProxyInstance(cl, new Class<?>[] {executorC}, (proxy, m, args) -> {
                switch (m.getName()) {
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    case "toString":
                        return "TFClanChatExecutor";
                    case "execute":
                        Object event = args[1];
                        if (!eventC.isInstance(event)) return null;
                        UUID sender = (UUID) uuidOf.invoke(getPlayer.invoke(event));
                        if (divert.test(sender, String.valueOf(getMessage.invoke(event)))) {
                            setCancelled.invoke(event, true);
                            return null;
                        }
                        java.util.Set<?> to = (java.util.Set<?>) getRecipients.invoke(event);
                        to.removeIf(p -> {
                            try {
                                return hides.test((UUID) uuidOf.invoke(p), sender);
                            } catch (Exception e) {
                                return false;
                            }
                        });
                        return null;
                    default:
                        return null;
                }
            });
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object lowest = Enum.valueOf((Class) priorityC, "LOWEST");
            pm.getClass().getMethod("registerEvent", Class.class, listenerC, priorityC, executorC, pluginC, boolean.class)
                    .invoke(pm, eventC, listener, lowest, executor, plugin, false);
            chatRegistered = true;
            TFClient.LOGGER.info("TF Clanes: filtro del chat del clan puesto en Bukkit (a nombre de {})", pluginName.invoke(plugin));
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Clanes: no se pudo poner el filtro del chat del clan en Bukkit", t);
        }
    }

    /** (1.3.154) ¿Está silenciado con EssentialsX (/mute)? null si no hay Bukkit o EssentialsX. */
    public static Boolean essentialsMuted(UUID uuid) {
        if (!find()) return null;
        try {
            Class<?> b = Class.forName("org.bukkit.Bukkit");
            Object pm = b.getMethod("getPluginManager").invoke(null);
            Object ess = pm.getClass().getMethod("getPlugin", String.class).invoke(pm, "Essentials");
            if (ess == null) return null;
            Object user = ess.getClass().getMethod("getUser", UUID.class).invoke(ess, uuid);
            return user == null ? null : (Boolean) user.getClass().getMethod("isMuted").invoke(user);
        } catch (Throwable t) {
            return null;
        }
    }
}
