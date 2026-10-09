package net.tierrasfantasticas.tfclient.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Todas las configuraciones del TF Client van juntas en config/tfclient/ (una por módulo: servidor.properties,
 * tienda.json, oficios.json, kits.json, pad.json…). Si encuentra el archivo de antes suelto en config/ (por ejemplo
 * config/tfclient/tienda.json), lo mueve a su sitio nuevo la primera vez, sin perder nada.
 */
public final class TFConfigDir {
    private TFConfigDir() {}

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("tfclient");
    }

    /** config/tfclient/name. old: el nombre que tenía antes en config/ (o null). */
    public static Path file(String name, String old) {
        Path dir = dir();
        Path file = dir.resolve(name);
        try {
            Files.createDirectories(dir);
            if (old != null && !Files.exists(file)) {
                Path before = FMLPaths.CONFIGDIR.get().resolve(old);
                if (Files.isRegularFile(before)) {
                    Files.move(before, file, StandardCopyOption.REPLACE_EXISTING);
                    TFClient.LOGGER.info("TF Client: config/{} ahora es config/tfclient/{}", old, name);
                }
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Client: no se pudo preparar config/tfclient/{}: {}", name, e.getMessage());
        }
        return file;
    }

    /**
     * Si la config del servidor es de una versión anterior (key &lt; version), guarda una copia
     * («nombre.antes-vN.json», junto a ella) y la cambia por la nueva por defecto. Devuelve la que vale.
     */
    public static com.google.gson.JsonObject upgrade(Path file, com.google.gson.JsonObject current, String key, int version,
                                                     com.google.gson.JsonObject defaults, String what) {
        if (current == null || defaults == null || TFJson.num(current, key, 0) >= version) return current;
        try {
            String name = file.getFileName().toString().replaceFirst("\\.json$", "");
            Path backup = file.resolveSibling(name + ".antes-v" + version + ".json");
            if (!Files.exists(backup)) Files.copy(file, backup);
            TFJson.write(file, defaults);
            TFClient.LOGGER.info("TF Client: {} actualizado a la versión {} (la de antes, en {})", what, version, backup.getFileName());
            return defaults;
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Client: no se pudo actualizar {}: {}", what, e.getMessage());
            return current;
        }
    }

    /** Como file(), pero trae la config de antes desde otra ruta (por ejemplo, la del mundo). */
    public static Path fileFrom(String name, Path before) {
        Path file = file(name, null);
        try {
            if (!Files.exists(file) && before != null && Files.isRegularFile(before)) {
                Files.copy(before, file);
                TFClient.LOGGER.info("TF Client: {} copiado a config/tfclient/{}", before, name);
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Client: no se pudo copiar {}: {}", before, e.getMessage());
        }
        return file;
    }
}
