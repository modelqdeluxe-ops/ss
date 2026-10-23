/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.server;

import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Empezar limpios (1.3.120, el dueño: «limpia todo registro guardado de jugadores del mod, vamos a comenzar limpios;
 * quita todo su registro o relacionado, lo que haya comprado»). Antes de que se cargue nada (ServerAboutToStart), los
 * datos de jugadores del TF Client pasan a &lt;mundo&gt;/tfclient/respaldo-&lt;fecha&gt;/ (no se borra nada sin copia):
 * <ul>
 *   <li>enteros: monedas, monedas del gachapón, estadísticas y ranking, títulos, hogares, regalos, clanes, oficios,
 *   clases de skills, efectos, armario, la tienda del día y su registro, el GTS y su registro, la Comunidad (fotos) y
 *   sus skins, y las zonas de protección (claimblocks_data.json);</li>
 *   <li>de los archivos que también guardan cosas del staff (Viajes: sus puntos; Pase, Gachapón, Kits y Recompensas),
 *   solo «jugadores».</li>
 * </ul>
 * Se hace una vez sola al arrancar la 1.3.120 (marca limpieza-1.3.120.hecha) y otra cada vez que el staff lo pida con
 * /tf limpiar datos (marca LIMPIAR; al reiniciar el servidor). Lo de los bots va aparte (TFBots: su registro empieza de
 * cero). Lo que se compró en la web lo vuelve a mandar la web (es su registro).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFDataReset {
    private static final String DONE = "limpieza-1.3.120.hecha";
    private static final String ASKED = "LIMPIAR";
    private static final List<String> WHOLE = List.of("monedas.json", "monedas-verdes.json", "pad-estadisticas.json", "titulos.json",
            "hogares.json", "regalos.json", "clanes.json", "oficios.json", "skills.json", "vfx.json", "wardrobe.json", "tienda.json",
            "tienda-registro.log", "gts.json", "gts-registro.log", "comunidad.json", "comunidad", "skins", "deudas.json");
    private static final List<String> PLAYERS_ONLY = List.of("viajes.json", "pase.json", "gachapon.json", "kits.json", "recompensas.json");
    private static final List<String> WORLD_ROOT = List.of("claimblocks_data.json", "claimblocks_data.json.bak");

    private TFDataReset() {}

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("tfclient");
        boolean first = !Files.exists(dir.resolve(DONE)), asked = Files.exists(dir.resolve(ASKED));
        if (!first && !asked) return;
        try {
            Files.createDirectories(dir);
            List<String> moved = wipe(server, dir);
            Files.writeString(dir.resolve(DONE), "Datos de jugadores del TF Client limpiados el " + LocalDateTime.now() + "\n");
            Files.deleteIfExists(dir.resolve(ASKED));
            TFClient.LOGGER.warn("TF Client: datos de jugadores limpiados ({}): {}", asked ? "pedido con /tf limpiar datos" : "primera vez con la 1.3.120",
                    moved.isEmpty() ? "no había nada" : String.join(", ", moved));
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Client: no se pudieron limpiar los datos de jugadores", e);
        }
    }

    /** Los jugadores que tenían datos: la web también los reinicia (TFBridge los manda en «reset» y borra el archivo). */
    static final String WEB_RESET = "reinicio-web.json";

    private static void collect(Path f, java.util.Set<String> uuids) {
        if (!f.toString().endsWith(".json")) return;
        JsonObject o = TFJson.read(f);
        if (o == null) return;
        java.util.List<JsonObject> where = new ArrayList<>(List.of(o));
        if (o.has("jugadores") && o.get("jugadores").isJsonObject()) where.add(o.getAsJsonObject("jugadores"));
        for (JsonObject w : where) {
            for (String k : w.keySet()) {
                try {
                    uuids.add(java.util.UUID.fromString(k).toString());
                } catch (IllegalArgumentException ignored) {
                    // no es un jugador
                }
            }
        }
    }

    private static List<String> wipe(MinecraftServer server, Path dir) throws Exception {
        List<String> moved = new ArrayList<>();
        java.util.Set<String> uuids = new java.util.LinkedHashSet<>();
        for (String name : WHOLE) collect(dir.resolve(name), uuids);
        for (String name : PLAYERS_ONLY) collect(dir.resolve(name), uuids);
        Path backup = dir.resolve("respaldo-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        for (String name : WHOLE) {
            Path f = dir.resolve(name);
            if (!Files.exists(f)) continue;
            Files.createDirectories(backup);
            Files.move(f, backup.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            moved.add(name);
        }
        for (String name : PLAYERS_ONLY) {
            Path f = dir.resolve(name);
            JsonObject o = TFJson.read(f);
            if (o == null || !o.has("jugadores")) continue;
            Files.createDirectories(backup);
            Files.copy(f, backup.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            o.remove("jugadores");
            TFJson.write(f, o);
            moved.add(name + " (jugadores)");
        }
        Path root = server.getWorldPath(LevelResource.ROOT);
        for (String name : WORLD_ROOT) {
            Path f = root.resolve(name);
            if (!Files.exists(f)) continue;
            Files.createDirectories(backup);
            Files.move(f, backup.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            moved.add(name);
        }
        if (!uuids.isEmpty()) {
            JsonObject web = new JsonObject();
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            uuids.forEach(a::add);
            web.add("uuids", a);
            TFJson.write(dir.resolve(WEB_RESET), web);
            moved.add(uuids.size() + " jugadores para reiniciar en la web");
        }
        // y los bots: su registro (TFBots lo empieza de cero si no es de la versión nueva)
        Path bots = FMLPaths.CONFIGDIR.get().resolve("tfclient-bots.json");
        if (Files.exists(bots)) {
            Files.createDirectories(backup);
            Files.move(bots, backup.resolve("tfclient-bots.json"), StandardCopyOption.REPLACE_EXISTING);
            moved.add("tfclient-bots.json");
        }
        return moved;
    }

    /** /tf limpiar datos: se limpia al reiniciar el servidor (con copia). */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("limpiar").requires(s -> s.hasPermission(4))
                .then(Commands.literal("datos").executes(ctx -> {
                    Path dir = ctx.getSource().getServer().getWorldPath(LevelResource.ROOT).resolve("tfclient");
                    try {
                        Files.createDirectories(dir);
                        Files.writeString(dir.resolve(ASKED), "Pedido el " + LocalDateTime.now() + "\n");
                    } catch (Exception e) {
                        ctx.getSource().sendFailure(Component.literal("No se pudo pedir la limpieza: " + e.getMessage()));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("Al reiniciar el servidor se borran los datos de jugadores del TF Client "
                            + "(monedas, pad, ranking, hogares, clanes, oficios, tienda, GTS, fotos, protecciones y bots), con copia en "
                            + "<mundo>/tfclient/respaldo-<fecha>/. Para no hacerlo, borra <mundo>/tfclient/" + ASKED + " antes de reiniciar.")
                            .withStyle(ChatFormatting.GOLD), true);
                    return 1;
                }));
    }
}
