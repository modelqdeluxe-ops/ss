/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.npcs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.tierrasfantasticas.tfclient.skills.SkillDefs;

/**
 * El catálogo de los NPC de escenas (assets/tfclient/npcs/catalog.json, de tools/npcs/build_npcs.py): cada pack con
 * sus NPC (su nombre del menú del pack, su modelo, sus pins y rutas, qué cosméticos le van) y las opciones de los menús
 * del pack (sombreros, espalda, manos, armaduras, quitar cosméticos). Lo leen el servidor y el panel del cliente.
 */
public final class NpcCatalog {
    /** Una opción de un menú del pack: lo que cambia (part/remap; «» = el modelo del NPC) y las variables que guarda. */
    public record Option(String id, String name, List<String[]> ops, Map<String, String> vars) {}

    /**
     * Un NPC del pack. marker = sin modelo (un generador o un puesto del pack: solo lo ve el staff con el panel);
     * autoskin = las pieles entre las que el panel le elige una al colocarlo (lleva la plantilla «S» del pack).
     */
    public record Npc(String mob, String name, String model, boolean marker, boolean walks, boolean hostile,
                      Map<String, List<String>> options, List<String> pins, List<Integer> routes, List<String> autoskin) {
        /** Su modelo sin el pack delante («scene_guards»): lo que el pack llama «skinchange». */
        public String bareModel() {
            return model == null ? "" : model.substring(model.indexOf('.') + 1);
        }
    }

    public record Pack(String id, String name, List<Npc> npcs, Map<String, List<Option>> options) {
        public Npc npc(String mob) {
            for (Npc n : npcs) if (n.mob.equalsIgnoreCase(mob)) return n;
            return null;
        }

        public Option option(String group, String id) {
            for (Option o : options.getOrDefault(group, List.of())) if (o.id.equals(id)) return o;
            return null;
        }
    }

    /** Los grupos de opciones, en el orden del panel, con su nombre. */
    public static final Map<String, String> GROUPS = Map.of("skin", "Piel", "skin2", "Piel del 2.º", "hat", "Sombrero",
            "back", "Espalda", "left", "Mano izquierda", "right", "Mano derecha", "armor", "Armadura");
    /** En este orden se ponen (la piel primero: remapmodel deja cada hueso como en la piel y quita los cosméticos). */
    public static final List<String> GROUP_ORDER = List.of("skin", "skin2", "hat", "back", "left", "right", "armor");

    private static List<Pack> packs;

    private NpcCatalog() {}

    public static synchronized List<Pack> packs() {
        if (packs == null) {
            List<Pack> out = new ArrayList<>();
            JsonArray arr = SkillDefs.npcCatalog();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                String id = o.get("id").getAsString();
                if (!SkillDefs.npcs().containsKey(id)) continue;
                Map<String, List<Option>> options = new LinkedHashMap<>();
                if (o.has("options") && o.get("options").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> g : o.getAsJsonObject("options").entrySet()) {
                        List<Option> list = new ArrayList<>();
                        for (JsonElement oe : g.getValue().getAsJsonArray()) {
                            JsonObject op = oe.getAsJsonObject();
                            List<String[]> ops = new ArrayList<>();
                            for (JsonElement x : op.getAsJsonArray("ops")) {
                                JsonArray xa = x.getAsJsonArray();
                                String[] s = new String[xa.size()];
                                for (int i = 0; i < s.length; i++) s[i] = xa.get(i).getAsString();
                                ops.add(s);
                            }
                            Map<String, String> vars = new LinkedHashMap<>();
                            if (op.has("vars")) op.getAsJsonObject("vars").entrySet().forEach(v -> vars.put(v.getKey(), v.getValue().getAsString()));
                            list.add(new Option(op.get("id").getAsString(), op.get("name").getAsString(), List.copyOf(ops), vars));
                        }
                        options.put(g.getKey(), List.copyOf(list));
                    }
                }
                List<Npc> npcs = new ArrayList<>();
                for (JsonElement ne : o.getAsJsonArray("npcs")) {
                    JsonObject n = ne.getAsJsonObject();
                    Map<String, List<String>> opts = new LinkedHashMap<>();
                    if (n.has("options")) {
                        for (Map.Entry<String, JsonElement> g : n.getAsJsonObject("options").entrySet()) {
                            List<String> ids = new ArrayList<>();
                            for (JsonElement x : g.getValue().getAsJsonArray()) ids.add(x.getAsString());
                            opts.put(g.getKey(), List.copyOf(ids));
                        }
                    }
                    List<String> pins = new ArrayList<>();
                    if (n.has("pins")) for (JsonElement x : n.getAsJsonArray("pins")) pins.add(x.getAsString().toLowerCase(Locale.ROOT));
                    List<Integer> routes = new ArrayList<>();
                    if (n.has("routes")) for (JsonElement x : n.getAsJsonArray("routes")) routes.add(x.getAsInt());
                    List<String> autoskin = new ArrayList<>();
                    if (n.has("autoskin") && n.get("autoskin").isJsonArray()) for (JsonElement x : n.getAsJsonArray("autoskin")) autoskin.add(x.getAsString());
                    npcs.add(new Npc(n.get("mob").getAsString(), n.get("name").getAsString(),
                            n.has("model") && !n.get("model").isJsonNull() ? n.get("model").getAsString() : null,
                            n.has("marker") && n.get("marker").getAsBoolean(), n.has("walks") && n.get("walks").getAsBoolean(),
                            n.has("hostile") && n.get("hostile").getAsBoolean(), opts, List.copyOf(pins), List.copyOf(routes),
                            List.copyOf(autoskin)));
                }
                out.add(new Pack(id, o.get("name").getAsString(), List.copyOf(npcs), options));
            }
            packs = Collections.unmodifiableList(out);
        }
        return packs;
    }

    public static Pack pack(String id) {
        for (Pack p : packs()) if (p.id.equals(id)) return p;
        return null;
    }

    /** El nombre corto de un pack para el panel: «Scenes Townsfolks Vol 1» → «Townsfolks Vol 1». */
    public static String shortName(Pack p) {
        return p.name.startsWith("Scenes ") ? p.name.substring(7) : p.name;
    }
}
