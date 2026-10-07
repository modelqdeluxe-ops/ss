package net.tierrasfantasticas.tfclient.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Lo que trae cada rango en el servidor, puesto solo: la web manda en la lista de rangos los permisos de EssentialsX de
 * cada uno (/craft, /anvil, /hat, /fly…) y sus hogares, y aquí se dejan así en LuckPerms y EssentialsX:
 * <ul>
 *   <li>LuckPerms: crea el grupo de cada rango (si no existe) y le pone sus permisos. Si un permiso que puso el TF
 *   Client deja de estar en la web, se lo quita. Los permisos que el staff ponga a mano en esos grupos no se tocan.</li>
 *   <li>EssentialsX: el número de hogares de cada grupo en «sethome-multiple» de su config.yml (y «essentials
 *   reload»). Se guarda una copia del archivo original la primera vez.</li>
 * </ul>
 * Solo se hace cuando cambia lo que manda la web (lo último aplicado va en &lt;mundo&gt;/tfclient/rank_perms.json) o
 * con /tf web rango permisos. Se desactiva con ranks.permissions=false en tfclient-server.properties.
 */
public final class TFRankPerms {
    private static final Pattern GROUP = Pattern.compile("[a-z0-9_\\-]{1,36}");
    private static final Pattern NODE = Pattern.compile("[A-Za-z0-9_\\-.*]{1,100}");
    /** Lo que ya se intentó en esta sesión (para no repetirlo en cada consulta si falta LuckPerms). */
    private static String tried = "";

    private TFRankPerms() {}

    /** Llega la lista de rangos de la web: si cambió desde lo último aplicado, se aplica. */
    public static void onRankList(MinecraftServer server) {
        if (!TFServerConfig.rankPermissions()) return;
        Plan plan = plan(TFRanks.list());
        if (plan.groups.isEmpty() || plan.signature.equals(tried)) return;
        tried = plan.signature;
        JsonObject state = TFJson.read(stateFile(server));
        if (state != null && plan.signature.equals(TFJson.str(state, "signature", ""))) return;
        for (String line : apply(server, plan, state)) TFClient.LOGGER.info("TF Ranks: {}", line);
    }

    /** /tf web rango permisos: lo vuelve a poner todo aunque no haya cambiado. Devuelve qué se hizo. */
    public static List<String> force(MinecraftServer server) {
        Plan plan = plan(TFRanks.list());
        if (plan.groups.isEmpty()) return List.of("Aún no hay conexión con la web: no se conocen los rangos.");
        tried = plan.signature;
        return apply(server, plan, TFJson.read(stateFile(server)));
    }

    /** Lo que debe tener cada grupo según la web (en el orden de los rangos). */
    private record Plan(Map<String, Set<String>> groups, Map<String, Integer> homes, String signature) {}

    private static Plan plan(Collection<TFRanks.Rank> ranks) {
        Map<String, Set<String>> groups = new LinkedHashMap<>();
        Map<String, Integer> homes = new LinkedHashMap<>();
        for (TFRanks.Rank rank : ranks) {
            String group = rank.group();
            // Sin permisos ni hogares: una web antigua que no los manda (no se toca nada de ese rango)
            if (!GROUP.matcher(group).matches() || (rank.permissions().isEmpty() && rank.homes() <= 0)) continue;
            Set<String> nodes = new LinkedHashSet<>();
            for (String node : rank.permissions()) {
                if (NODE.matcher(node).matches()) nodes.add(node);
                else TFClient.LOGGER.warn("TF Ranks: permiso no válido «{}» en el rango {}", node, group);
            }
            groups.put(group, nodes);
            if (rank.homes() > 0) homes.put(group, rank.homes());
        }
        return new Plan(groups, homes, groups + "|" + homes);
    }

    private static List<String> apply(MinecraftServer server, Plan plan, JsonObject state) {
        List<String> report = new ArrayList<>();
        // Lo que puso el TF Client la última vez (para quitar solo lo suyo)
        Map<String, Set<String>> before = new LinkedHashMap<>();
        JsonObject old = TFJson.obj(state, "groups");
        for (Map.Entry<String, JsonElement> e : old.entrySet()) {
            Set<String> nodes = new LinkedHashSet<>();
            if (e.getValue().isJsonArray()) e.getValue().getAsJsonArray().forEach(n -> nodes.add(n.getAsString()));
            before.put(e.getKey(), nodes);
        }

        boolean luckPerms = TFBridge.hasLuckPerms(server);
        if (luckPerms) {
            int set = 0, unset = 0;
            for (Map.Entry<String, Set<String>> e : plan.groups.entrySet()) {
                String group = e.getKey();
                run(server, "lp creategroup " + group);
                for (String node : e.getValue()) {
                    run(server, "lp group " + group + " permission set " + node + " true");
                    set++;
                }
                for (String node : before.getOrDefault(group, Set.of())) {
                    if (e.getValue().contains(node) || !NODE.matcher(node).matches()) continue;
                    run(server, "lp group " + group + " permission unset " + node);
                    unset++;
                }
            }
            // Rangos que ya no existen en la web: se les quita lo que puso el TF Client (el grupo se deja)
            for (Map.Entry<String, Set<String>> e : before.entrySet()) {
                if (plan.groups.containsKey(e.getKey()) || !GROUP.matcher(e.getKey()).matches()) continue;
                for (String node : e.getValue()) {
                    if (!NODE.matcher(node).matches()) continue;
                    run(server, "lp group " + e.getKey() + " permission unset " + node);
                    unset++;
                }
            }
            report.add("LuckPerms: " + plan.groups.size() + " grupos de rango, " + set + " permisos puestos"
                    + (unset > 0 ? ", " + unset + " quitados" : "") + ".");
            plan.groups.forEach((group, nodes) -> report.add("  " + group + ": " + nodes.size() + " permisos"
                    + (plan.homes.containsKey(group) ? ", " + plan.homes.get(group) + " hogares" : "")));
        } else {
            report.add("No está LuckPerms (no existe el comando /lp): no se han podido poner los permisos de los rangos.");
        }

        String essentials = essentials(server, plan.homes);
        if (essentials != null) report.add(essentials);

        if (luckPerms) {
            JsonObject out = new JsonObject();
            out.addProperty("signature", plan.signature);
            JsonObject groups = new JsonObject();
            plan.groups.forEach((group, nodes) -> {
                JsonArray arr = new JsonArray();
                nodes.forEach(arr::add);
                groups.add(group, arr);
            });
            out.add("groups", groups);
            TFJson.write(stateFile(server), out);
        }
        return report;
    }

    /** Los hogares de cada rango en el config.yml de EssentialsX. Devuelve qué pasó (o null si no hay nada que hacer). */
    private static String essentials(MinecraftServer server, Map<String, Integer> homes) {
        if (homes.isEmpty()) return null;
        Path file = server.getServerDirectory().toPath().resolve(TFServerConfig.essentialsConfig()).normalize();
        if (!Files.isRegularFile(file)) {
            return "No se encuentra " + TFServerConfig.essentialsConfig() + " (EssentialsX): los hogares de los rangos no se han podido poner.";
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String updated = EssentialsHomes.update(text, homes);
            if (updated.equals(text)) return "EssentialsX: los hogares de los rangos ya estaban puestos.";
            Path backup = file.resolveSibling(file.getFileName() + ".antes-de-tfclient");
            if (!Files.exists(backup)) Files.copy(file, backup);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, updated, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            // Su respuesta llega por el mismo sitio que los errores: no se mira
            if (hasCommand(server, "essentials")) TFBridge.run(server, "essentials reload");
            return "EssentialsX: hogares de los rangos puestos en " + TFServerConfig.essentialsConfig() + " y recargado.";
        } catch (IOException e) {
            return "EssentialsX: no se pudo cambiar " + file + ": " + e.getMessage();
        }
    }

    private static boolean hasCommand(MinecraftServer server, String name) {
        return server.getCommands().getDispatcher().getRoot().getChild(name) != null;
    }

    private static void run(MinecraftServer server, String command) {
        List<String> errors = TFBridge.run(server, command);
        if (!errors.isEmpty()) TFClient.LOGGER.warn("TF Ranks: «{}» → {}", command, String.join("; ", errors));
    }

    private static Path stateFile(MinecraftServer server) {
        return TFJson.worldFile(server, "rank_perms.json");
    }
}
