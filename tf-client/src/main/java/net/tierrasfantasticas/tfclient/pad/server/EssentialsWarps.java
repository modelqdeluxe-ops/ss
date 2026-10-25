/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
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
import net.minecraft.server.level.ServerPlayer;
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

    private static Boolean perWarp;
    private static long perWarpAt;

    /** ¿Tiene EssentialsX «per-warp-permission: true»? (cada warp pide essentials.warps.&lt;nombre&gt;) */
    static boolean perWarpPermission(MinecraftServer server) {
        if (perWarp != null && System.currentTimeMillis() - perWarpAt < 30_000) return perWarp;
        perWarpAt = System.currentTimeMillis();
        boolean on = false;
        try {
            Path cfg = server.getServerDirectory().toPath().resolve(TFServerConfig.essentialsConfig()).normalize();
            if (Files.isRegularFile(cfg)) {
                for (String line : Files.readAllLines(cfg, StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (t.startsWith("per-warp-permission:")) on = unquote(t.substring(20)).equalsIgnoreCase("true");
                }
            }
        } catch (Exception ignored) {
            // sin leerlo: como EssentialsX por defecto (false)
        }
        return perWarp = on;
    }

    /** Nombres que son del staff aunque EssentialsX no pida permiso por warp: admin, staff, mod, op, privado, oculto, test. */
    private static final java.util.regex.Pattern STAFF = java.util.regex.Pattern.compile(
            "(?i).*(admin|staff|privad|ocult).*|(?i)(.*[_-])?(mods?|op|ops|test|prueba)([_-].*)?");

    /**
     * (1.3.139, el dueño: «hay warps públicos y de admin; que el pad no publique los de admin») ¿Le sale este warp a
     * este jugador en Viajes? Los OP ven todos. Si EssentialsX pide permiso por warp, solo los que tiene
     * (essentials.warps.&lt;nombre&gt; o essentials.warps.*); sin poder preguntarlo, ninguno. Y nunca los que se llaman
     * como del staff (admin, staff, mod…), salvo que tenga su permiso.
     */
    static boolean visible(ServerPlayer player, Warp w) {
        if (player.hasPermissions(2)) return true;
        Boolean own = net.tierrasfantasticas.tfclient.server.TFBukkit.hasPermission(player, "essentials.warps." + w.id().toLowerCase(Locale.ROOT));
        if (perWarpPermission(player.getServer())) return Boolean.TRUE.equals(own);
        if (STAFF.matcher(w.id()).matches() || STAFF.matcher(w.name()).matches()) return Boolean.TRUE.equals(own);
        return true;
    }

    private static String unquote(String v) {
        String t = v.trim();
        if (t.length() > 1 && (t.startsWith("'") || t.startsWith("\""))) t = t.substring(1, t.length() - 1);
        return t;
    }
}
