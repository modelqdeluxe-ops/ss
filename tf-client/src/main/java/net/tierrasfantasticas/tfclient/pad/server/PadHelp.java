package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Ayuda: la guía del servidor en pestañas (como la wiki). Se edita en config/tfclient-ayuda.json, que se crea con
 * estas secciones la primera vez.
 */
public final class PadHelp {
    record Section(String id, String title, List<String[]> blocks) {}

    private static final List<Section> SECTIONS = new ArrayList<>();
    public static final PadServer.App APP = new App();
    public static final PadServer.Store STORE = new PadServer.Store() {
        @Override
        public void load(MinecraftServer server) {
            loadConfig();
        }

        @Override
        public void save(MinecraftServer server) {}

        @Override
        public boolean dirty() {
            return false;
        }
    };

    private PadHelp() {}

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            PadView.Builder b = PadView.of("ayuda").empty("La guía está vacía.");
            Section open = SECTIONS.isEmpty() ? null : SECTIONS.get(0);
            for (Section s : SECTIONS) {
                b.tab(s.id, s.title.toUpperCase(java.util.Locale.ROOT));
                if (s.id.equals(tab)) open = s;
            }
            if (open == null) return b.build();
            b.selected(open.id);
            for (String[] block : open.blocks) b.text(block[0], 0x1854BE, List.of(block[1]));
            return b.build();
        }
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("tfclient-ayuda.json");
    }

    static void loadConfig() {
        JsonObject o = TFJson.read(file());
        if (o == null) {
            o = defaults();
            if (!Files.exists(file())) TFJson.write(file(), o);
        }
        SECTIONS.clear();
        if (!o.has("secciones")) return;
        for (JsonElement e : o.getAsJsonArray("secciones")) {
            JsonObject s = e.getAsJsonObject();
            List<String[]> blocks = new ArrayList<>();
            for (JsonElement p : s.getAsJsonArray("bloques")) {
                JsonObject q = p.getAsJsonObject();
                blocks.add(new String[] {TFJson.str(q, "titulo", ""), TFJson.str(q, "texto", "")});
            }
            String id = TFJson.str(s, "id", "");
            if (id.isEmpty() || id.length() > 32) id = "s" + SECTIONS.size();
            SECTIONS.add(new Section(id, TFJson.str(s, "titulo", "?"), blocks));
        }
    }

    private static JsonObject defaults() {
        JsonObject o = new JsonObject();
        JsonArray secs = new JsonArray();
        secs.add(section("empezar", "Empezar",
                "Bienvenido", "Tierras Fantásticas es un mundo de supervivencia, aventura, fantasía y rol. Abre el pad con la C para todo lo del servidor.",
                "Tus primeros pasos", "Reclama el Kit inicial en Kits, elige un oficio en Oficios y protege tu casa con una protección (Protección).",
                "Ganar monedas", "Con los oficios, las misiones diarias y semanales, las cazas y vendiendo en la tienda o en el GTS."));
        secs.add(section("normas", "Normas",
                "Respeto", "Nada de insultos, acoso ni spam. Trata a los demás como quieres que te traten.",
                "Juego limpio", "Nada de trucos, hacks ni aprovechar fallos. Si encuentras uno, avisa al staff.",
                "Construcciones", "No rompas ni robes en lo que no es tuyo. Protege lo tuyo con una protección.",
                "Comunidad", "Solo fotos del juego y para todos los públicos. Lo demás se borra."));
        secs.add(section("pad", "El pad",
                "Apps", "Dos páginas de apps: pasa de una a otra con la rueda, las flechas o arrastrando. Arriba ves la hora, tus monedas y los ajustes.",
                "Cámara y Comunidad", "Toma fotos con la Cámara: se guardan en tu ordenador. Publica las que quieras en Comunidad y dale like a las de los demás.",
                "Viajes y Explorar", "Viaja al spawn, a tu cama o a los puntos del servidor. Explorar te lleva a un sitio nuevo al azar."));
        secs.add(section("comandos", "Comandos",
                "Para todos", "/tf jobs (oficios), /tf shop (tienda) y /tf claims (protecciones). Todo lo demás, en el pad.",
                "Ayuda del staff", "Si algo no va, pregunta en el Discord o en el WhatsApp del servidor (pad → Comunidad)."));
        o.add("secciones", secs);
        return o;
    }

    private static JsonObject section(String id, String title, String... pairs) {
        JsonObject s = new JsonObject();
        s.addProperty("id", id);
        s.addProperty("titulo", title);
        JsonArray blocks = new JsonArray();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            JsonObject b = new JsonObject();
            b.addProperty("titulo", pairs[i]);
            b.addProperty("texto", pairs[i + 1]);
            blocks.add(b);
        }
        s.add("bloques", blocks);
        return s;
    }
}
