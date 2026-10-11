package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.claims.data.ClaimConfig;
import net.tierrasfantasticas.tfclient.items.TFLimits;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig;
import net.tierrasfantasticas.tfclient.pad.PadConfig;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.server.TFServerConfig;
import net.tierrasfantasticas.tfclient.shop.TFCoinShop;
import net.tierrasfantasticas.tfclient.shop.TFRoulette;
import net.tierrasfantasticas.tfclient.shop.TFShopConfig;
import net.tierrasfantasticas.tfclient.util.TFConfigDir;
import net.tierrasfantasticas.tfclient.vfx.VfxServer;

/**
 * /tf reload (y RECARGAR CONFIGS del pad de administrador): vuelve a leer todas las configs de config/tfclient/ (también
 * gachapon.json y pase.json, 1.3.38) sin
 * reiniciar y lo aplica al momento:
 * <ul>
 *   <li>Antes de recargar cada JSON se comprueba que se puede leer. Si tiene un error (una coma de más, unas comillas
 *       sin cerrar…), esa config se queda como estaba y se dice el archivo y el error; las demás se recargan igual. Así
 *       un fallo al editar nunca deja el servidor con los valores de fábrica.</li>
 *   <li>Después, a todos los conectados: su estado del pad (apps encendidas, saldo) y otra vez la app que tengan
 *       abierta, ya con lo nuevo.</li>
 * </ul>
 */
public final class TFReload {
    /** done: lo que se recargó (nombres para el aviso); problems: lo que no, con el porqué. */
    public record Result(List<String> done, List<String> problems) {}

    private interface Loader {
        List<String> load() throws Exception;
    }

    private TFReload() {}

    public static Result all() {
        List<String> done = new ArrayList<>(), problems = new ArrayList<>();
        reload("pad.json", "pad", () -> {
            PadConfig.load();
            return List.of();
        }, done, problems);
        reload("tienda.json", "tienda", TFShopConfig::load, done, problems);
        reload("oficios.json", "oficios", TFJobsConfig::load, done, problems);
        reload("kits.json", "kits", () -> {
            PadKits.loadConfig();
            return List.of();
        }, done, problems);
        reload("recompensas.json", "recompensas", () -> {
            PadRewards.loadConfig();
            return List.of();
        }, done, problems);
        reload("misiones.json", "misiones y cazas", () -> {
            PadMissions.loadConfig();
            return List.of();
        }, done, problems);
        reload("gachapon.json", "gachapón", () -> {
            PadGacha.loadConfig();
            return List.of();
        }, done, problems);
        reload("pase.json", "TF Pass", () -> {
            PadPass.loadConfig();
            return List.of();
        }, done, problems);
        reload("ayuda.json", "ayuda", () -> {
            PadHelp.loadConfig();
            return List.of();
        }, done, problems);
        reload("protecciones.json", "protecciones", () -> {
            if (!ClaimConfig.get().reload()) throw new IllegalStateException("el servidor aún no la ha cargado");
            return List.of();
        }, done, problems);
        reload("limites.json", "límites", () -> {
            TFLimits.load();
            return List.of();
        }, done, problems);
        reload("vfx.json", "efectos", () -> {
            VfxServer.reloadConfig();
            return List.of();
        }, done, problems);
        reload("skills.json", "clases de skills", () -> {
            net.tierrasfantasticas.tfclient.skills.SkillServer.reloadConfig();
            return List.of();
        }, done, problems);
        reload("tienda-monedas.json", "tienda de monedas", () -> {
            TFCoinShop.reload();
            return List.of();
        }, done, problems);
        reload("ruleta.json", "ruleta", () -> {
            TFRoulette.reload();
            return List.of();
        }, done, problems);
        reload(null, "servidor", () -> {
            TFServerConfig.load();
            return List.of();
        }, done, problems);
        PadServer.refreshOpen();
        TFClient.LOGGER.info("TF Client: configs recargadas ({}); {} avisos", String.join(", ", done), problems.size());
        for (String p : problems) TFClient.LOGGER.warn("TF Client: {}", p);
        return new Result(done, problems);
    }

    /** Recarga una config si su JSON se puede leer (json null = no es JSON). Los avisos de la carga van a problems. */
    private static void reload(String json, String name, Loader loader, List<String> done, List<String> problems) {
        if (json != null) {
            String error = jsonError(TFConfigDir.dir().resolve(json));
            if (error != null) {
                problems.add(json + " tiene un error y se queda como estaba: " + error);
                return;
            }
        }
        try {
            List<String> warnings = loader.load();
            done.add(name);
            for (String w : warnings) problems.add(name + ": " + w);
        } catch (Exception e) {
            problems.add("No se pudo recargar " + name + ": " + e.getMessage());
            TFClient.LOGGER.error("TF Client: no se pudo recargar {}", name, e);
        }
    }

    /** El error de un JSON que existe y no se puede leer, o null si está bien (o no existe: se crea con lo de fábrica). */
    private static String jsonError(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.isBlank()) return "está vacío";
            if (!JsonParser.parseString(text).isJsonObject()) return "no es un objeto JSON";
            return null;
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (e.getCause() != null && e.getCause().getMessage() != null) msg = e.getCause().getMessage();
            return msg.length() > 160 ? msg.substring(0, 160) + "…" : msg;
        }
    }
}
