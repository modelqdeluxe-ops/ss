package net.tierrasfantasticas.tfclient.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Lee archivos del .jar del mod directamente, sin pasar por los resource packs, para tenerlos disponibles desde el
 * primer instante del arranque (antes de que el juego cargue recursos).
 */
public final class TFResources {
    private TFResources() {}

    public static InputStream open(String path) throws IOException {
        ModList mods = ModList.get();
        IModFileInfo info = mods == null ? null : mods.getModFileById(TFClient.MOD_ID);
        if (info != null) {
            Path resource = info.getFile().findResource(path.split("/"));
            if (Files.exists(resource)) return Files.newInputStream(resource);
        }
        InputStream in = TFResources.class.getResourceAsStream("/" + path);
        if (in == null) throw new IOException("No se encuentra " + path);
        return in;
    }
}
