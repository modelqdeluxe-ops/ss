/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.server.TFRanks;
import net.tierrasfantasticas.tfclient.server.TFServerConfig;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Hogares dentro del pad. Con EssentialsX (Mohist) son los suyos de siempre: se leen de
 * plugins/Essentials/userdata/&lt;uuid&gt;.yml y IR, GUARDAR y BORRAR ejecutan /home, /sethome y /delhome como el
 * jugador (con sus permisos, su límite y su cuenta atrás). Sin EssentialsX, el pad guarda los hogares él mismo
 * (&lt;mundo&gt;/tfclient/hogares.json) con el límite del rango (3 si no tiene) y viaja con la cuenta atrás de Viajes.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class PadHomes {
    public static final PadServer.App APP = new App();
    static final JsonStore STORE_IMPL = new JsonStore("hogares.json");
    public static final PadServer.Store STORE = STORE_IMPL;

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,16}");
    private static final int TEXT = 0x18265C;

    record Home(String name, String world, double x, double y, double z, float yaw, float pitch) {}

    /**
     * Lo que acaba de cambiar con /sethome o /delhome: EssentialsX guarda el archivo un poco después, así que durante
     * unos segundos se enseña lo que sabemos (y luego se vuelve a leer y se manda al pad).
     */
    private record Recent(Map<String, Home> homes, int until) {}

    private static final Map<UUID, Recent> RECENT = new HashMap<>();
    private static int tick;

    private PadHomes() {}

    // ---------------------------------------------------------------------------------------------------------------

    private static boolean essentials(MinecraftServer server) {
        // (1.3.119) en Mohist, el comando del plugin puede no estar en el despachador de Minecraft: también por Bukkit
        return (server.getCommands().getDispatcher().getRoot().getChild("sethome") != null
                || net.tierrasfantasticas.tfclient.server.TFBukkit.hasCommand("sethome")) && Files.isDirectory(userdata(server));
    }

    private static Path userdata(MinecraftServer server) {
        Path config = server.getServerDirectory().toPath().resolve(TFServerConfig.essentialsConfig()).normalize();
        return config.resolveSibling("userdata");
    }

    /** Los hogares del jugador (en el orden en que los guardó). */
    static Map<String, Home> homes(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (!essentials(server)) return own(player.getUUID());
        Recent r = RECENT.get(player.getUUID());
        if (r != null && tick < r.until) return new LinkedHashMap<>(r.homes);
        return readEssentials(userdata(server).resolve(player.getUUID() + ".yml"));
    }

    private static final Pattern KEY = Pattern.compile("^(\\s*)(['\"]?)([^:'\"#]+)\\2:\\s*(.*?)\\s*$");

    /** El bloque «homes:» del yml de EssentialsX (sin librería de YAML: solo hace falta esto). */
    static Map<String, Home> readEssentials(Path file) {
        Map<String, Home> out = new LinkedHashMap<>();
        List<String> lines;
        try {
            if (!Files.isRegularFile(file)) return out;
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return out;
        }
        int i = 0;
        while (i < lines.size() && !lines.get(i).matches("^homes:\\s*(\\{\\s*})?\\s*$")) i++;
        if (i >= lines.size() || lines.get(i).contains("{")) return out;
        String name = null;
        int nameIndent = -1;
        Map<String, String> values = new HashMap<>();
        for (i++; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.trim().startsWith("#")) continue;
            if (!Character.isWhitespace(line.charAt(0))) break;
            Matcher m = KEY.matcher(line);
            if (!m.matches()) continue;
            int indent = m.group(1).length();
            if (nameIndent < 0 || indent <= nameIndent) {
                if (name != null) add(out, name, values);
                name = m.group(3).trim();
                nameIndent = indent;
                values = new HashMap<>();
            } else {
                String v = m.group(4);
                if (v.length() > 1 && (v.startsWith("'") || v.startsWith("\""))) v = v.substring(1, v.length() - 1);
                values.put(m.group(3).trim(), v);
            }
        }
        if (name != null) add(out, name, values);
        return out;
    }

    private static void add(Map<String, Home> out, String name, Map<String, String> v) {
        try {
            out.put(name, new Home(name, v.getOrDefault("world-name", v.getOrDefault("world", "world")),
                    Double.parseDouble(v.getOrDefault("x", "0")), Double.parseDouble(v.getOrDefault("y", "64")),
                    Double.parseDouble(v.getOrDefault("z", "0")), Float.parseFloat(v.getOrDefault("yaw", "0")),
                    Float.parseFloat(v.getOrDefault("pitch", "0"))));
        } catch (NumberFormatException ignored) {
            // hogar con datos raros: no se enseña
        }
    }

    private static Map<String, Home> own(UUID uuid) {
        Map<String, Home> out = new LinkedHashMap<>();
        JsonObject p = STORE_IMPL.playerIfAny(uuid);
        if (p == null) return out;
        for (Map.Entry<String, JsonElement> e : p.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject o = e.getValue().getAsJsonObject();
            out.put(e.getKey(), new Home(e.getKey(), TFJson.str(o, "dim", "minecraft:overworld"), TFJson.dec(o, "x", 0), TFJson.dec(o, "y", 64),
                    TFJson.dec(o, "z", 0), (float) TFJson.dec(o, "yaw", 0), (float) TFJson.dec(o, "pitch", 0)));
        }
        return out;
    }

    /** Sin límite (essentials.sethome.multiple.unlimited). */
    static final int UNLIMITED = Integer.MAX_VALUE;

    /**
     * Los hogares que puede tener. (1.3.119, el dueño: «el pad debe estar ligado a los hogares o permisos») Con EssentialsX
     * en Mohist, exactamente como lo cuenta EssentialsX (Settings.getHomeLimit): 1; con essentials.sethome.multiple, los
     * de «default» de sethome-multiple; y el mayor de cada grupo de sethome-multiple cuyo permiso
     * essentials.sethome.multiple.&lt;grupo&gt; tenga (los rangos los traen en LuckPerms: TFRankPerms); con
     * essentials.sethome.multiple.unlimited, sin límite. Sin Bukkit: los de su rango de la web, o los «default», o los del
     * pad.
     */
    static int limit(ServerPlayer player) {
        boolean ess = essentials(player.getServer());
        Map<String, Integer> multiple = ess ? sethomeMultiple(player.getServer()) : Map.of();
        Boolean multi = net.tierrasfantasticas.tfclient.server.TFBukkit.hasPermission(player, "essentials.sethome.multiple");
        if (ess && multi != null) {
            if (Boolean.TRUE.equals(net.tierrasfantasticas.tfclient.server.TFBukkit.hasPermission(player, "essentials.sethome.multiple.unlimited"))) {
                return UNLIMITED;
            }
            int limit = 1;
            if (multi) limit = multiple.getOrDefault("default", 1);
            for (Map.Entry<String, Integer> e : multiple.entrySet()) {
                if (e.getValue() > limit && Boolean.TRUE.equals(net.tierrasfantasticas.tfclient.server.TFBukkit.hasPermission(player,
                        "essentials.sethome.multiple." + e.getKey()))) {
                    limit = e.getValue();
                }
            }
            return limit;
        }
        TFRanks.Rank rank = TFRanks.full(TFRanks.of(player.getUUID()));
        if (rank != null && rank.homes() > 0) return rank.homes();
        if (multiple.containsKey("default")) return multiple.get("default");
        return (int) net.tierrasfantasticas.tfclient.pad.PadConfig.get("hogares.sinRango");
    }

    private static final Pattern MULTI_ENTRY = Pattern.compile("^\\s+['\"]?([^'\":\\s#]+)['\"]?:\\s*(-?\\d+)");

    /** El bloque «sethome-multiple» del config.yml de EssentialsX: grupo → hogares. */
    private static Map<String, Integer> sethomeMultiple(MinecraftServer server) {
        Map<String, Integer> out = new LinkedHashMap<>();
        try {
            Path config = server.getServerDirectory().toPath().resolve(TFServerConfig.essentialsConfig()).normalize();
            boolean inBlock = false;
            for (String line : Files.readAllLines(config, StandardCharsets.UTF_8)) {
                if (line.startsWith("sethome-multiple:")) {
                    inBlock = true;
                    continue;
                }
                if (inBlock && !line.isBlank() && !Character.isWhitespace(line.charAt(0))) break;
                Matcher m = MULTI_ENTRY.matcher(line);
                if (inBlock && m.find()) out.put(m.group(1).toLowerCase(Locale.ROOT), Integer.parseInt(m.group(2)));
            }
        } catch (Exception ignored) {
            // sin config: nada
        }
        return out;
    }

    private static String where(Home h) {
        return worldLabel(h.world) + " · " + (int) Math.floor(h.x) + ", " + (int) Math.floor(h.y) + ", " + (int) Math.floor(h.z);
    }

    /** El nombre del mundo para el jugador: los tres de siempre y, los de Multiverse, por su nombre («spawn» → Spawn). */
    private static String worldLabel(String world) {
        String w = world.toLowerCase(Locale.ROOT);
        if (w.contains(":")) return PadTravel.dimName(w);
        if (w.equals("world") || w.equals("overworld")) return "Mundo normal";
        if (w.equals("world_nether") || w.endsWith("dim-1")) return "Nether";
        if (w.equals("world_the_end") || w.endsWith("dim1")) return "El End";
        return PadTravel.dimName("minecraft:" + w);
    }

    private static ItemStack icon(Home h) {
        String w = h.world.toLowerCase(Locale.ROOT);
        if (w.contains("nether") || w.endsWith("dim-1")) return new ItemStack(Items.CRIMSON_NYLIUM);
        if (w.contains("end") || w.endsWith("dim1")) return new ItemStack(Items.END_STONE);
        return new ItemStack(Items.RED_BED);
    }

    private static boolean sameWorld(ServerPlayer player, Home h) {
        // (1.3.119) con Bukkit, por el nombre del mundo (los de Multiverse son overworlds con otro nombre)
        String bukkit = net.tierrasfantasticas.tfclient.server.TFBukkit.worldName(player);
        if (bukkit != null && !h.world.contains(":")) return bukkit.equalsIgnoreCase(h.world);
        String dim = player.level().dimension().location().toString();
        String w = h.world.toLowerCase(Locale.ROOT);
        if (w.contains(":")) return w.equals(dim);
        boolean nether = w.contains("nether") || w.endsWith("dim-1");
        boolean end = !nether && (w.contains("end") || w.endsWith("dim1"));
        return nether ? dim.equals("minecraft:the_nether") : end ? dim.equals("minecraft:the_end") : dim.equals("minecraft:overworld");
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            Map<String, Home> homes = homes(player);
            int limit = limit(player);
            PadView.Builder b = PadView.of("hogares");
            b.header(limit == UNLIMITED ? homes.size() + " hogares (sin límite)." : homes.size() + " de " + limit + " hogares."
                    + (homes.size() < limit ? "" : " Borra uno para guardar otro."));
            for (Home h : homes.values()) {
                List<String> lines = new ArrayList<>();
                String line = where(h);
                if (sameWorld(player, h)) {
                    double d = Math.sqrt(player.distanceToSqr(h.x, h.y, h.z));
                    line += " · a " + (d < 1000 ? (int) d + " m" : String.format(Locale.ROOT, "%.1f km", d / 1000).replace('.', ','));
                }
                lines.add(line);
                boolean sure = PadServer.confirming(player, "hogar.borrar:" + h.name);
                b.row(new PadView.Row(icon(h), h.name, TEXT, lines, -1, "", PadView.Btn.of("IR", "ir:" + h.name, PadView.BLUE),
                        PadView.Btn.of(sure ? "¿SEGURO?" : "BORRAR", "borrar:" + h.name, PadView.RED)));
            }
            b.empty("Sin hogares. Escribe un nombre y pulsa GUARDAR AQUÍ.");
            b.input("guardar", "NOMBRE DEL HOGAR", 16, "GUARDAR AQUÍ");
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            String[] a = action.split(":", 2);
            String name = a.length > 1 ? a[1] : text.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
            MinecraftServer server = player.getServer();
            Map<String, Home> homes = homes(player);
            boolean ess = essentials(server);
            switch (a[0]) {
                case "guardar" -> {
                    if (!NAME.matcher(name).matches()) {
                        TFPadNet.notice(player, "Nombre: letras, números, _ o - (hasta 16).");
                        return null;
                    }
                    if (!homes.containsKey(name) && homes.size() >= limit(player)) {
                        TFPadNet.notice(player, "Ya tienes " + homes.size() + " hogares. Borra uno o guarda encima de otro.");
                        return null;
                    }
                    Home h = new Home(name, ess ? player.level().dimension().location().getPath() : player.level().dimension().location().toString(),
                            player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
                    if (ess) {
                        run(player, "sethome " + name);
                        homes.put(name, new Home(name, worldName(player), h.x, h.y, h.z, h.yaw, h.pitch));
                        remember(player, homes);
                    } else {
                        JsonObject o = new JsonObject();
                        o.addProperty("dim", h.world);
                        o.addProperty("x", h.x);
                        o.addProperty("y", h.y);
                        o.addProperty("z", h.z);
                        o.addProperty("yaw", h.yaw);
                        o.addProperty("pitch", h.pitch);
                        STORE_IMPL.player(player.getUUID()).add(name, o);
                        STORE_IMPL.changed();
                    }
                    TFPadNet.notice(player, "Hogar «" + name + "» guardado aquí.");
                    return null;
                }
                case "borrar" -> {
                    if (!homes.containsKey(name) || !PadServer.confirm(player, "hogar.borrar:" + name)) return null;
                    if (ess) {
                        run(player, "delhome " + name);
                        homes.remove(name);
                        remember(player, homes);
                    } else {
                        JsonObject p = STORE_IMPL.playerIfAny(player.getUUID());
                        if (p != null) p.remove(name);
                        STORE_IMPL.changed();
                    }
                    TFPadNet.notice(player, "Hogar «" + name + "» borrado.");
                    return null;
                }
                case "ir" -> {
                    Home h = homes.get(name);
                    if (h == null) return null;
                    if (ess) {
                        TFPadNet.close(player);
                        run(player, "home " + name);
                        return null;
                    }
                    ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(h.world));
                    ServerLevel level = server.getLevel(key);
                    if (level == null) {
                        TFPadNet.notice(player, "Ese mundo ya no existe.");
                        return null;
                    }
                    PadTravel.warmup(player, "Yendo a «" + name + "»", () -> PadTravel.arrive(player, level, h.x, h.y, h.z, h.yaw, h.pitch));
                    return null;
                }
                default -> {
                    return null;
                }
            }
        }
    }

    /** El nombre de mundo de Bukkit (lo que EssentialsX guarda en «world-name»). */
    private static String worldName(ServerPlayer player) {
        String bukkit = net.tierrasfantasticas.tfclient.server.TFBukkit.worldName(player);
        if (bukkit != null) return bukkit;
        String dim = player.level().dimension().location().toString();
        return switch (dim) {
            case "minecraft:the_nether" -> "world_nether";
            case "minecraft:the_end" -> "world_the_end";
            default -> "world";
        };
    }

    /**
     * Un comando como si lo escribiera el jugador: (1.3.119) en Mohist por Bukkit (Player.performCommand: EssentialsX lleva
     * entre los mundos de Multiverse); sin Bukkit, por el despachador de Minecraft.
     */
    private static void run(ServerPlayer player, String command) {
        if (net.tierrasfantasticas.tfclient.server.TFBukkit.playerCommand(player, command) != null) return;
        try {
            player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Pad: «/{}» falló: {}", command, e.getMessage());
        }
    }

    private static void remember(ServerPlayer player, Map<String, Home> homes) {
        RECENT.put(player.getUUID(), new Recent(new LinkedHashMap<>(homes), tick + 100));
    }

    /** Pasado el rato, se vuelve a leer lo que guardó EssentialsX y se manda al pad (por si dijo que no). */
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick++;
        if (RECENT.isEmpty()) return;
        Iterator<Map.Entry<UUID, Recent>> it = RECENT.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Recent> e = it.next();
            if (tick < e.getValue().until) continue;
            it.remove();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (player != null) PadServer.refresh(player, "hogares", "");
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        RECENT.clear();
    }
}
