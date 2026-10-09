package net.tierrasfantasticas.tfclient.pad.server;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.tierrasfantasticas.tfclient.server.TFServerConfig;

/**
 * Los warps de EssentialsX (plugins/Essentials/warps/&lt;warp&gt;.yml): todos los que pone el staff con /setwarp salen
 * solos en Viajes. Se leen como mucho cada 10 s.
 */
final class EssentialsWarps {
    /** id: el nombre del archivo (lo que va en /warp); name: el nombre que se ve (la clave «name» si la tiene). */
    record Warp(String id, String name, String world) {}

    private static List<Warp> cache = List.of();
    private static long readAt;

    private EssentialsWarps() {}

    static List<Warp> list(MinecraftServer server) {
        if (System.currentTimeMillis() - readAt < 10_000) return cache;
        readAt = System.currentTimeMillis();
        List<Warp> out = new ArrayList<>();
        if (server.getCommands().getDispatcher().getRoot().getChild("warp") == null) return cache = out;
        Path dir = server.getServerDirectory().toPath().resolve(TFServerConfig.essentialsConfig()).normalize().resolveSibling("warps");
        if (!Files.isDirectory(dir)) return cache = out;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml")).forEach(f -> {
                String file = f.getFileName().toString();
                String id = file.substring(0, file.length() - 4);
                String name = id, world = "world";
                try {
                    for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        String t = line.trim();
                        if (t.startsWith("name:")) name = unquote(t.substring(5));
                        else if (t.startsWith("world-name:")) world = unquote(t.substring(11));
                    }
                } catch (Exception ignored) {
                    // el warp sale con su nombre de archivo
                }
                if (id.matches("[A-Za-z0-9_-]{1,40}")) out.add(new Warp(id, name.isBlank() ? id : name, world));
            });
        } catch (Exception ignored) {
            // sin carpeta legible: sin warps
        }
        out.sort(Comparator.comparing(w -> w.name().toLowerCase(Locale.ROOT)));
        return cache = out;
    }

    private static String unquote(String v) {
        String t = v.trim();
        if (t.length() > 1 && (t.startsWith("'") || t.startsWith("\""))) t = t.substring(1, t.length() - 1);
        return t;
    }
}
