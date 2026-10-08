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
    private static final int DEFAULT_LIMIT = 3;

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
        return server.getCommands().getDispatcher().getRoot().getChild("sethome") != null && Files.isDirectory(userdata(server));
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

    /** Los hogares que puede tener: los de su rango, o los «default» de EssentialsX, o 3. */
    static int limit(ServerPlayer player) {
        TFRanks.Rank rank = TFRanks.full(TFRanks.of(player.getUUID()));
        if (rank != null && rank.homes() > 0) return rank.homes();
        if (essentials(player.getServer())) {
            try {
                Path config = player.getServer().getServerDirectory().toPath().resolve(TFServerConfig.essentialsConfig()).normalize();
                boolean inBlock = false;
                for (String line : Files.readAllLines(config, StandardCharsets.UTF_8)) {
                    if (line.startsWith("sethome-multiple:")) {
                        inBlock = true;
                        continue;
                    }
                    if (inBlock && !line.isBlank() && !Character.isWhitespace(line.charAt(0))) break;
                    Matcher m = Pattern.compile("^\\s+['\"]?default['\"]?:\\s*(\\d+)").matcher(line);
                    if (inBlock && m.find()) return Integer.parseInt(m.group(1));
                }
            } catch (Exception ignored) {
                // sin config: el de siempre
            }
        }
        return DEFAULT_LIMIT;
    }

    private static String where(Home h) {
        String w = h.world.toLowerCase(Locale.ROOT);
        String dim = w.contains("nether") || w.endsWith("dim-1") ? "Nether" : w.contains("end") || w.endsWith("dim1") ? "El End" : "Mundo normal";
        return dim + " · " + (int) Math.floor(h.x) + ", " + (int) Math.floor(h.y) + ", " + (int) Math.floor(h.z);
    }

    private static ItemStack icon(Home h) {
        String w = h.world.toLowerCase(Locale.ROOT);
        if (w.contains("nether") || w.endsWith("dim-1")) return new ItemStack(Items.CRIMSON_NYLIUM);
        if (w.contains("end") || w.endsWith("dim1")) return new ItemStack(Items.END_STONE);
        return new ItemStack(Items.RED_BED);
    }

    private static boolean sameWorld(ServerPlayer player, Home h) {
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
            b.header(homes.size() + " de " + limit + " hogares." + (homes.size() < limit ? " Escribe un nombre y GUARDAR AQUÍ guarda donde estás."
                    : " Para uno nuevo, borra otro (o guarda encima con el mismo nombre)."));
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
            b.empty("Aún no tienes hogares. Escribe un nombre abajo (por ejemplo «casa») y pulsa GUARDAR AQUÍ.");
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
                        TFPadNet.notice(player, "Usa un nombre corto: letras, números, _ o - (hasta 16).");
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
        String dim = player.level().dimension().location().toString();
        return switch (dim) {
            case "minecraft:the_nether" -> "world_nether";
            case "minecraft:the_end" -> "world_the_end";
            default -> "world";
        };
    }

    /** Un comando como si lo escribiera el jugador (los de EssentialsX están en el mismo despachador en Mohist). */
    private static void run(ServerPlayer player, String command) {
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
