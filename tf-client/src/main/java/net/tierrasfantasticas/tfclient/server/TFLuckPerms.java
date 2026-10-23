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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * El rango comprado, en LuckPerms (1.3.117, el dueño: «que el mod le asigne el rango al jugador cuando lo compre
 * automáticamente con LuckPerms: /lp user (usuario) parent set (rango); rangos mortal, inmortal, magico, eterno, cosmico,
 * celestial, fantastico; para que se le pongan todos los permisos, con Essentials también»).
 *
 * <ul>
 *   <li><b>Cómo se ejecuta</b> un comando de LuckPerms: en Mohist, LuckPerms es un plugin de Bukkit y se le habla por la
 *   consola de Bukkit ({@code Bukkit.dispatchCommand(getConsoleSender(), …)}), igual que si el staff lo escribiera en la
 *   consola. Antes iba por el despachador de comandos de Forge con una fuente propia y, en un servidor híbrido, el
 *   comando podía no llegar al plugin sin que nadie se enterara. Sin Bukkit (LuckPerms como mod de Forge), por el de
 *   Minecraft, como la consola.</li>
 *   <li><b>parent set</b> con el grupo de un rango: el jugador queda con ese grupo y nada más (y así hereda sus permisos,
 *   los de EssentialsX incluidos: {@link TFRankPerms}). Si además tiene un grupo que no es de la tienda (el staff:
 *   admin, mod…), no se le quita: se le pone el rango y se le quitan solo los otros rangos de la tienda.</li>
 *   <li><b>Comprobado</b>: LuckPerms trabaja en su hilo; a los 2 s se mira con su API (por reflexión: el plugin no es
 *   dependencia del mod) que el jugador tenga el grupo. Si no, se reintenta; si sigue sin tenerlo, se avisa en la
 *   consola y al staff conectado.</li>
 *   <li><b>Siempre al día</b>: al entrar y en cada consulta del puente, si la web dice que un conectado tiene un rango
 *   y LuckPerms no le tiene ese grupo, se le pone (una compra que llegó con LuckPerms caído, una mejora, un reinicio).
 *   Los quita solo la web (reembolsos y mejoras).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFLuckPerms {
    private static final long VERIFY_TICKS = 40;
    /** Para no repetir el arreglo de un jugador en cada consulta: como mucho uno por minuto. */
    private static final long HEAL_EVERY_MS = 60_000;

    private record Check(UUID uuid, String name, String group, long at, int tries) {}

    private static final ArrayDeque<Check> CHECKS = new ArrayDeque<>();
    private static final Map<UUID, Long> HEALED = new HashMap<>();
    private static long tick;
    /** Para la prueba y /tf web rango: lo último que pasó. */
    static volatile String last = "";

    private TFLuckPerms() {}

    // ------------------------------------------------------------------------------------------------- comandos

    /** ¿Es un comando de LuckPerms? */
    public static boolean isLuckPerms(String command) {
        String c = command.trim().toLowerCase(Locale.ROOT);
        return c.startsWith("lp ") || c.startsWith("luckperms ") || c.startsWith("perm ") || c.startsWith("perms ") || c.startsWith("permissions ");
    }

    /**
     * Ejecuta un comando de LuckPerms como la consola. Con «lp user X parent set G» de un rango de la tienda, lo hace
     * sin quitar los grupos del staff y lo comprueba después. Devuelve los errores (vacío si se mandó).
     */
    public static List<String> run(MinecraftServer srv, String command) {
        String[] w = command.trim().split("\\s+");
        if (w.length == 6 && w[1].equalsIgnoreCase("user") && w[3].equalsIgnoreCase("parent") && w[4].equalsIgnoreCase("set")
                && isRankGroup(w[5])) {
            return setRank(srv, w[2], w[5].toLowerCase(Locale.ROOT), 0);
        }
        return dispatch(srv, command);
    }

    /** El comando tal cual, por la consola de Bukkit si la hay; si no, por la de Minecraft. */
    static List<String> dispatch(MinecraftServer srv, String command) {
        Boolean bukkit = Bukkit.dispatch(command);
        if (bukkit != null) {
            return bukkit ? List.of() : List.of("la consola de Bukkit no conoce «" + command.split(" ")[0] + "» (¿está LuckPerms?)");
        }
        if (!TFBridge.hasLuckPerms(srv)) return List.of("no está LuckPerms (no existe el comando /lp)");
        try {
            srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack(), command);
            return List.of();
        } catch (Throwable t) {
            return List.of(t.toString());
        }
    }

    private static boolean isRankGroup(String group) {
        for (TFRanks.Rank r : TFRanks.list()) if (r.group().equalsIgnoreCase(group)) return true;
        // (aún sin la lista de la web: los siete de la tienda)
        return TFRanks.list().isEmpty() && Set.of("mortal", "inmortal", "magico", "eterno", "cosmico", "celestial", "fantastico")
                .contains(group.toLowerCase(Locale.ROOT));
    }

    private static Set<String> rankGroups() {
        Set<String> out = new LinkedHashSet<>();
        for (TFRanks.Rank r : TFRanks.list()) if (!r.group().isEmpty()) out.add(r.group().toLowerCase(Locale.ROOT));
        if (out.isEmpty()) out.addAll(List.of("mortal", "inmortal", "magico", "eterno", "cosmico", "celestial", "fantastico"));
        return out;
    }

    /** lp user X parent set G (o, si tiene un grupo del staff, add G y fuera los otros rangos) y su comprobación. */
    private static List<String> setRank(MinecraftServer srv, String name, String group, int tries) {
        // (1.3.125) sin el grupo en LuckPerms, «parent set» falla y el jugador se queda en default («MIEMBRO» en el
        // TAB): antes se crean los grupos de los rangos con sus permisos (LuckPerms hace los comandos en orden)
        GroupInfo info = group(group);
        if (info != null && !info.exists()) {
            TFClient.LOGGER.warn("TF LuckPerms: no existe el grupo «{}»: se crean los grupos de los rangos antes de ponérselo a {}", group, name);
            TFRankPerms.ensureGroups(srv);
        }
        ServerPlayer online = srv.getPlayerList().getPlayerByName(name);
        UUID uuid = online != null ? online.getUUID() : null;
        Set<String> parents = uuid == null ? null : Api.parents(uuid);
        List<String> staff = new ArrayList<>();
        if (parents != null) {
            Set<String> ranks = rankGroups();
            for (String p : parents) if (!p.equals("default") && !ranks.contains(p)) staff.add(p);
        }
        List<String> errors = new ArrayList<>();
        if (staff.isEmpty()) {
            errors.addAll(dispatch(srv, "lp user " + name + " parent set " + group));
        } else {
            errors.addAll(dispatch(srv, "lp user " + name + " parent add " + group));
            for (String other : rankGroups()) {
                if (!other.equals(group) && parents.contains(other)) dispatch(srv, "lp user " + name + " parent remove " + other);
            }
            TFClient.LOGGER.info("TF LuckPerms: {} tiene {} (staff): se le pone {} sin quitarle eso", name, staff, group);
        }
        if (errors.isEmpty() && uuid != null) CHECKS.addLast(new Check(uuid, name, group, tick + VERIFY_TICKS, tries));
        last = "lp user " + name + " → " + group + (staff.isEmpty() ? " (parent set)" : " (staff " + staff + ": parent add)")
                + (errors.isEmpty() ? "" : " FALLO: " + errors);
        return errors;
    }

    // --------------------------------------------------------------------------------------- comprobar y arreglar

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick++;
        if (CHECKS.isEmpty()) return;
        MinecraftServer srv = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (srv == null) return;
        Iterator<Check> it = CHECKS.iterator();
        List<Check> again = new ArrayList<>();
        while (it.hasNext()) {
            Check c = it.next();
            if (c.at() > tick) continue;
            it.remove();
            Set<String> parents = Api.parents(c.uuid());
            if (parents == null) {
                // Sin la API (o ya no está conectado): no se puede mirar, se da por bueno lo que dijo la consola
                last = c.name() + ": mandado «" + c.group() + "» a LuckPerms (sin poder comprobarlo: " + Api.why + ")";
                TFClient.LOGGER.info("TF LuckPerms: {}", last);
                continue;
            }
            if (parents.contains(c.group())) {
                last = c.name() + " ya tiene el grupo «" + c.group() + "» en LuckPerms (" + parents + ")";
                TFClient.LOGGER.info("TF LuckPerms: {}", last);
            } else if (c.tries() < 1) {
                TFClient.LOGGER.warn("TF LuckPerms: {} aún no tiene «{}» ({}): se repite", c.name(), c.group(), parents);
                again.add(c);
            } else {
                last = c.name() + " NO tiene el grupo «" + c.group() + "» en LuckPerms tras dos intentos (tiene " + parents + ")";
                TFClient.LOGGER.error("TF LuckPerms: {}", last);
                Component msg = Component.literal("[TF] " + last + ". Pónselo a mano: /lp user " + c.name() + " parent set " + c.group())
                        .withStyle(ChatFormatting.RED);
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) if (p.hasPermissions(3)) p.sendSystemMessage(msg);
            }
        }
        for (Check c : again) setRank(srv, c.name(), c.group(), c.tries() + 1);
    }

    /** Si la web dice que este conectado tiene un rango y LuckPerms no le tiene su grupo, se le pone. */
    static void ensure(MinecraftServer srv, ServerPlayer player, TFRanks.Rank rank) {
        if (rank == null || rank.group().isEmpty()) return;
        Set<String> parents = Api.parents(player.getUUID());
        if (parents == null || parents.contains(rank.group().toLowerCase(Locale.ROOT))) return;
        // Si el staff le dio a mano un rango igual o más alto, se respeta
        for (TFRanks.Rank r : TFRanks.list()) {
            if (r.tier() >= TFRanks.full(rank).tier() && parents.contains(r.group().toLowerCase(Locale.ROOT))) return;
        }
        // (la entrega de la compra ya se lo está poniendo: llega en la misma consulta que el rango)
        for (Check c : CHECKS) if (c.uuid().equals(player.getUUID()) && c.group().equalsIgnoreCase(rank.group())) return;
        long now = System.currentTimeMillis();
        Long at = HEALED.get(player.getUUID());
        if (at != null && now - at < HEAL_EVERY_MS) return;
        HEALED.put(player.getUUID(), now);
        TFClient.LOGGER.warn("TF LuckPerms: {} tiene el rango {} en la web pero no su grupo en LuckPerms ({}): se le pone",
                player.getGameProfile().getName(), rank.group(), parents);
        setRank(srv, player.getGameProfile().getName(), rank.group().toLowerCase(Locale.ROOT), 0);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HEALED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CHECKS.clear();
        HEALED.clear();
    }

    /** Un comando por la consola de Bukkit: true si lo conoce, false si no, null si no hay Bukkit (Forge puro). */
    static Boolean console(String command) {
        return Bukkit.dispatch(command);
    }

    /** ¿Está la API de LuckPerms? (el plugin o el mod cargado) */
    static boolean present() {
        return Api.find();
    }

    /**
     * Lo que tiene un grupo en LuckPerms: si existe, sus permisos (los suyos, sin heredados), si tiene prefijo y peso
     * propios. null si no se puede saber (sin la API).
     */
    record GroupInfo(boolean exists, Set<String> permissions, boolean prefix, boolean weight) {}

    static GroupInfo group(String name) {
        return Api.group(name);
    }

    /** Para la prueba en el juego: los grupos de un jugador en LuckPerms (o por qué no se pueden ver). */
    public static String describe(UUID uuid) {
        Set<String> p = Api.parents(uuid);
        return p == null ? "sin API de LuckPerms (" + Api.why + ")" : p.toString();
    }

    // ------------------------------------------------------------------------------------------- Bukkit y la API

    /** La consola de Bukkit (Mohist), por reflexión. */
    private static final class Bukkit {
        private static boolean looked;
        private static Method console, dispatch;

        /** true si la consola lo conoce, false si no; null si no hay Bukkit. */
        static Boolean dispatch(String command) {
            if (!looked) {
                looked = true;
                try {
                    Class<?> b = Class.forName("org.bukkit.Bukkit");
                    Class<?> sender = Class.forName("org.bukkit.command.CommandSender", false, b.getClassLoader());
                    console = b.getMethod("getConsoleSender");
                    dispatch = b.getMethod("dispatchCommand", sender, String.class);
                } catch (Throwable t) {
                    console = null;
                }
            }
            if (console == null) return null;
            try {
                return (Boolean) dispatch.invoke(null, console.invoke(null), command);
            } catch (Throwable t) {
                TFClient.LOGGER.warn("TF LuckPerms: la consola de Bukkit no pudo con «{}»: {}", command, t.toString());
                return false;
            }
        }
    }

    /** La API de LuckPerms (plugin de Bukkit por su ServicesManager, o mod con LuckPermsProvider), por reflexión. */
    private static final class Api {
        private static long lookedAt = -1;
        private static Object api;
        private static Method getUserManager, getUser, getNodes, getGroupName;
        private static Object inheritance;
        /** (1.3.125) los grupos: GroupManager.getGroup, sus nodos de permiso, prefijo y peso. */
        private static Method getGroupManager, getGroup, groupNodes, permissionOf;
        private static Object permissionType, prefixType, weightType;
        static String why = "no mirado";

        private static boolean find() {
            if (api != null) return true;
            long now = System.currentTimeMillis();
            // LuckPerms puede cargar después del mod: se vuelve a mirar cada 30 s
            if (lookedAt >= 0 && now - lookedAt < 30_000) return false;
            lookedAt = now;
            try {
                Class<?> lp = null;
                Object found = null;
                try {
                    Class<?> b = Class.forName("org.bukkit.Bukkit");
                    Object services = b.getMethod("getServicesManager").invoke(null);
                    @SuppressWarnings("unchecked")
                    Collection<Class<?>> known = (Collection<Class<?>>) services.getClass().getMethod("getKnownServices").invoke(services);
                    for (Class<?> c : known) if (c.getName().equals("net.luckperms.api.LuckPerms")) lp = c;
                    if (lp != null) {
                        Object reg = services.getClass().getMethod("getRegistration", Class.class).invoke(services, lp);
                        if (reg != null) {
                            Method provider = reg.getClass().getMethod("getProvider");
                            provider.setAccessible(true);
                            found = provider.invoke(reg);
                        }
                    }
                } catch (ClassNotFoundException noBukkit) {
                    // sin Bukkit: LuckPerms como mod
                }
                if (found == null) {
                    Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
                    found = provider.getMethod("get").invoke(null);
                    lp = Class.forName("net.luckperms.api.LuckPerms", false, provider.getClassLoader());
                }
                ClassLoader cl = lp.getClassLoader();
                Class<?> um = Class.forName("net.luckperms.api.model.user.UserManager", false, cl);
                Class<?> user = Class.forName("net.luckperms.api.model.user.User", false, cl);
                Class<?> nodeType = Class.forName("net.luckperms.api.node.NodeType", false, cl);
                Class<?> inh = Class.forName("net.luckperms.api.node.types.InheritanceNode", false, cl);
                getUserManager = lp.getMethod("getUserManager");
                getUser = um.getMethod("getUser", UUID.class);
                getNodes = user.getMethod("getNodes", nodeType);
                getGroupName = inh.getMethod("getGroupName");
                inheritance = nodeType.getField("INHERITANCE").get(null);
                try {
                    Class<?> gm = Class.forName("net.luckperms.api.model.group.GroupManager", false, cl);
                    Class<?> group = Class.forName("net.luckperms.api.model.group.Group", false, cl);
                    Class<?> perm = Class.forName("net.luckperms.api.node.types.PermissionNode", false, cl);
                    getGroupManager = lp.getMethod("getGroupManager");
                    getGroup = gm.getMethod("getGroup", String.class);
                    groupNodes = group.getMethod("getNodes", nodeType);
                    permissionOf = perm.getMethod("getPermission");
                    permissionType = nodeType.getField("PERMISSION").get(null);
                    prefixType = nodeType.getField("PREFIX").get(null);
                    weightType = nodeType.getField("WEIGHT").get(null);
                } catch (Throwable t) {
                    getGroup = null;
                    TFClient.LOGGER.warn("TF LuckPerms: sin la parte de grupos de la API: {}", t.toString());
                }
                api = found;
                why = "API de LuckPerms encontrada";
                TFClient.LOGGER.info("TF LuckPerms: API encontrada ({})", found.getClass().getName());
                return true;
            } catch (Throwable t) {
                why = t instanceof ClassNotFoundException ? "no está LuckPerms" : t.toString();
                return false;
            }
        }

        /** Un grupo (existe, permisos propios, prefijo y peso), o null si no se puede saber. */
        static GroupInfo group(String name) {
            if (!find() || getGroup == null) return null;
            try {
                Object g = getGroup.invoke(getGroupManager.invoke(api), name.toLowerCase(Locale.ROOT));
                if (g == null) return new GroupInfo(false, Set.of(), false, false);
                Set<String> perms = new LinkedHashSet<>();
                for (Object node : (Collection<?>) groupNodes.invoke(g, permissionType)) perms.add(String.valueOf(permissionOf.invoke(node)));
                boolean prefix = !((Collection<?>) groupNodes.invoke(g, prefixType)).isEmpty();
                boolean weight = !((Collection<?>) groupNodes.invoke(g, weightType)).isEmpty();
                return new GroupInfo(true, perms, prefix, weight);
            } catch (Throwable t) {
                why = t.toString();
                return null;
            }
        }

        /** Los grupos de los que hereda directamente (en minúsculas), o null si no se puede saber (sin API, desconectado). */
        static Set<String> parents(UUID uuid) {
            if (!find()) return null;
            try {
                Object user = getUser.invoke(getUserManager.invoke(api), uuid);
                if (user == null) {
                    why = "el jugador no está cargado en LuckPerms";
                    return null;
                }
                Set<String> out = new LinkedHashSet<>();
                for (Object node : (Collection<?>) getNodes.invoke(user, inheritance)) {
                    out.add(String.valueOf(getGroupName.invoke(node)).toLowerCase(Locale.ROOT));
                }
                return out;
            } catch (Throwable t) {
                why = t.toString();
                return null;
            }
        }
    }
}
