package net.tierrasfantasticas.tfclient.pad;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Los emojis de Comunidad (cliente y servidor): Twemoji (los de Discord) y animados de Noto, en atlas, con el índice
 * textures/gui/emoji/index.json que hace tools/pad/make_emojis.py. Aquí solo los datos (sin nada de dibujo, para que el
 * servidor pueda validar las reacciones): cada emoji con su id (el atajo de Discord: joy, thumbsup, fire...), sus
 * atajos en inglés y español, categoría, nombre en español y su celda en el atlas. Dibujarlos: {@link PadEmojiDraw}.
 */
public final class PadEmojis {
    /** Largo máximo de un id (también por red). */
    public static final int ID_MAX = 48;

    /**
     * Un emoji. es: su nombre en español; esCode: su atajo principal en español (fuego, risa...; puede faltar). keys: palabras para buscar (ya normalizadas, con su nombre en español). page/cell: hoja y primera celda en su atlas (estáticos o animados); frames y frameMs solo en los
     * animados (sus fotogramas van seguidos desde cell).
     */
    public record Emoji(String id, String category, String unicode, String es, String esCode, List<String> names, String keys,
                        boolean animated, int page, int cell, int frames, int frameMs) {
        /** El atajo con dos puntos, como en Discord. */
        public String code() {
            return ":" + id + ":";
        }
    }

    /** Una hoja de un atlas: textures/gui/emoji/&lt;file&gt;.png, de cols x rows celdas de cell píxeles. */
    public record Page(String file, int cell, int cols, int rows) {}

    public record Category(String id, String name, String icon) {}

    private static final String INDEX = "/assets/tfclient/textures/gui/emoji/index.json";

    private static boolean loaded;
    private static final List<Emoji> ALL = new ArrayList<>();
    private static final Map<String, Emoji> BY_ID = new HashMap<>();
    private static final Map<String, Emoji> BY_NAME = new HashMap<>();
    private static final Map<String, Emoji> BY_CHAR = new HashMap<>();
    private static final Map<String, List<Emoji>> BY_CATEGORY = new LinkedHashMap<>();
    private static final List<Page> STATIC = new ArrayList<>();
    private static final List<Page> ANIM = new ArrayList<>();
    private static final List<Category> CATEGORIES = new ArrayList<>();
    private static final Map<String, String> LEGACY = new HashMap<>();
    /** El emoji más largo en caracteres de Java (para buscar emojis escritos con el teclado en los textos). */
    private static int longestChars = 1;

    private PadEmojis() {}

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = PadEmojis.class.getResourceAsStream(INDEX)) {
            if (in == null) {
                TFClient.LOGGER.error("Emojis: falta {}", INDEX);
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("static")) STATIC.add(page(e.getAsJsonObject()));
            for (JsonElement e : root.getAsJsonArray("anim")) ANIM.add(page(e.getAsJsonObject()));
            for (JsonElement e : root.getAsJsonArray("categories")) {
                JsonObject c = e.getAsJsonObject();
                CATEGORIES.add(new Category(c.get("id").getAsString(), c.get("name").getAsString(), c.get("icon").getAsString()));
                BY_CATEGORY.put(c.get("id").getAsString(), new ArrayList<>());
            }
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("legacy").entrySet()) LEGACY.put(e.getKey(), e.getValue().getAsString());
            for (JsonElement el : root.getAsJsonArray("emojis")) {
                JsonObject o = el.getAsJsonObject();
                List<String> names = new ArrayList<>();
                for (JsonElement n : o.getAsJsonArray("n")) names.add(n.getAsString());
                boolean anim = o.has("a");
                Emoji e = new Emoji(o.get("id").getAsString(), o.get("c").getAsString(), o.get("u").getAsString(),
                        o.get("es").getAsString(), o.has("s") ? o.get("s").getAsString() : "", List.copyOf(names),
                        (o.has("k") ? o.get("k").getAsString() + " " : "") + norm(o.get("es").getAsString()), anim,
                        o.get("p").getAsInt(), o.get("i").getAsInt(), anim ? o.get("f").getAsInt() : 1, anim ? o.get("t").getAsInt() : 0);
                if (e.id().length() > ID_MAX || BY_ID.containsKey(e.id())) continue;
                ALL.add(e);
                BY_ID.put(e.id(), e);
                for (String n : names) BY_NAME.putIfAbsent(n, e);
                if (!anim) {
                    String bare = bare(e.unicode());
                    BY_CHAR.putIfAbsent(bare, e);
                    longestChars = Math.max(longestChars, bare.length());
                }
                BY_CATEGORY.computeIfAbsent(e.category(), k -> new ArrayList<>()).add(e);
            }
            // los atajos que son id de otro emoji, para el suyo (:heart: es el corazón, no un alias de otro)
            for (Emoji e : ALL) BY_NAME.put(e.id(), e);
        } catch (Exception ex) {
            TFClient.LOGGER.error("Emojis: no se pudo leer el índice", ex);
        }
    }

    private static Page page(JsonObject o) {
        return new Page(o.get("file").getAsString(), o.get("cell").getAsInt(), o.get("cols").getAsInt(), o.get("rows").getAsInt());
    }

    static String bare(String s) {
        return s.replace("️", "");
    }

    public static List<Emoji> all() {
        load();
        return Collections.unmodifiableList(ALL);
    }

    public static List<Category> categories() {
        load();
        return Collections.unmodifiableList(CATEGORIES);
    }

    public static List<Emoji> inCategory(String id) {
        load();
        return BY_CATEGORY.getOrDefault(id, List.of());
    }

    static Page page(Emoji e) {
        load();
        List<Page> l = e.animated() ? ANIM : STATIC;
        return e.page() >= 0 && e.page() < l.size() ? l.get(e.page()) : null;
    }

    static List<Page> pages(boolean animated) {
        load();
        return animated ? ANIM : STATIC;
    }

    /** El id de siempre de un emoji (los nombres viejos de las reacciones, risa/wow/triste/fuego/top, al suyo); null si no existe. */
    public static String canonical(String id) {
        if (id == null || id.isEmpty() || id.length() > ID_MAX) return null;
        load();
        String legacy = LEGACY.get(id);
        if (legacy != null) id = legacy;
        return BY_ID.containsKey(id) ? id : null;
    }

    /** El emoji de un id (o de un nombre viejo); null si no existe. */
    public static Emoji get(String id) {
        String c = canonical(id);
        return c == null ? null : BY_ID.get(c);
    }

    /** El emoji de un atajo escrito (:fuego:, :fire:, :+1:...; sin los dos puntos). */
    public static Emoji byName(String name) {
        load();
        Emoji e = BY_NAME.get(norm(name));
        return e != null ? e : get(name);
    }

    /** El emoji escrito con el teclado que empieza en s[at] (el más largo que encaje), o null. */
    static Emoji unicodeAt(String s, int at, int[] len) {
        load();
        int max = Math.min(s.length() - at, longestChars + 4);
        for (int n = max; n >= 1; n--) {
            String sub = s.substring(at, at + n);
            Emoji e = BY_CHAR.get(bare(sub));
            if (e != null && !bare(sub).isEmpty()) {
                // solo si no parte un par sustituto
                if (at + n < s.length() && Character.isLowSurrogate(s.charAt(at + n))) continue;
                len[0] = n;
                return e;
            }
        }
        return null;
    }

    /** Como norm() de make_emojis.py: minúsculas, sin tildes, lo que no es letra ni número (ni +) como un solo _. */
    public static String norm(String s) {
        String d = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder b = new StringBuilder();
        boolean sep = false;
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '+') {
                if (sep && b.length() > 0) b.append('_');
                b.append(c);
                sep = false;
            } else {
                sep = true;
            }
        }
        return b.toString();
    }

    /**
     * Busca por atajo (inglés o español), nombre en español o palabra clave, como el buscador de Discord: primero los
     * que tienen un atajo que empieza por lo escrito, luego los que lo contienen y luego por nombre o palabra.
     */
    public static List<Emoji> search(String query, int max) {
        load();
        String q = norm(query);
        if (q.isEmpty()) return List.of();
        List<List<Emoji>> tiers = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (Emoji e : ALL) {
            int t = 9;
            for (String n : e.names()) {
                if (n.equals(q)) t = Math.min(t, 0);
                else if (n.startsWith(q)) t = Math.min(t, 1);
                else if (n.contains("_" + q)) t = Math.min(t, 2);
                else if (n.contains(q)) t = Math.min(t, 3);
            }
            if (t == 9 && e.keys().contains(q)) t = 4;
            if (t < tiers.size()) tiers.get(t).add(e);
        }
        List<Emoji> out = new ArrayList<>();
        for (List<Emoji> l : tiers) {
            for (Emoji e : l) {
                if (out.size() >= max) return out;
                out.add(e);
            }
        }
        return out;
    }
}
