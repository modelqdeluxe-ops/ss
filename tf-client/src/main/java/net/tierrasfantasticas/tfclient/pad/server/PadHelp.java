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
 * Ayuda: la guía del servidor en pestañas (como la wiki). Se edita en config/tfclient/ayuda.json, que se crea con
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
        return net.tierrasfantasticas.tfclient.util.TFConfigDir.file("ayuda.json", "tfclient-ayuda.json");
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
                "Bienvenido", "Supervivencia, aventura, fantasía y rol. Todo el servidor está en el pad (tecla C).",
                "Tus primeros pasos", "Reclama el kit inicial en Kits, elige un oficio en Oficios y protege tu casa en Protección.",
                "Ganar monedas", "Oficios, misiones, cazas, la tienda y el GTS."));
        secs.add(section("normas", "Normas",
                "Respeto", "Sin insultos, acoso ni spam.",
                "Juego limpio", "Sin hacks ni bugs. Si encuentras uno, avisa al staff.",
                "Construcciones", "No rompas ni robes lo que no es tuyo.",
                "Comunidad", "Solo fotos del juego y aptas para todos."));
        secs.add(section("pad", "El pad",
                "Apps", "Baja con la rueda o arrastrando. Arriba: hora, monedas y ajustes.",
                "Cámara y Comunidad", "MODO FOTO y clic izquierdo para disparar. Publica en Comunidad.",
                "Viajes y Explorar", "Spawn, tu cama, warps y puntos del servidor. Explorar: un sitio al azar."));
        secs.add(section("comandos", "Comandos",
                "Para todos", "Ninguno: todo se hace desde el pad.",
                "Ayuda del staff", "Discord o WhatsApp del servidor."));
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
