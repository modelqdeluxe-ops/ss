package net.tierrasfantasticas.tfclient.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.server.TFBridge;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Ruleta con las monedas del servidor. Los premios, sus probabilidades y el precio son los mismos que en la web (los
 * manda la web por el puente y se guardan en config/tfclient-ruleta.json por si arranca sin conexión).
 * Se gira desde la web («Girar con monedas»), que manda:
 * <pre>
 * /tf web ruleta girar &lt;jugador&gt; &lt;n&gt;     gira n veces cobrándole las monedas (staff, nivel 3)
 * </pre>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFRoulette {
    /** Un premio: comandos a ejecutar ({player}) o, si weapon, un arma al azar del set. */
    public record Prize(String id, String name, double chance, List<String> give, boolean weapon) {}

    private record Config(String set, long coinPrice, List<Prize> pool, List<String[]> weapons) {}

    /** Premio ya elegido: nombre y comandos. */
    private record Result(String id, String name, List<String> commands, boolean weapon) {}

    private static Config config;
    private static List<JsonObject> capture;

    private TFRoulette() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("tfclient-ruleta.json");
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        JsonObject json = TFJson.read(file());
        if (json != null) parse(json);
    }

    /** La ruleta que manda la web en cada consulta del puente. */
    public static void setConfig(JsonObject json) {
        boolean first = config == null;
        if (parse(json) && first) TFJson.write(file(), json);
        else if (config != null && !json.equals(TFJson.read(file()))) TFJson.write(file(), json);
    }

    private static boolean parse(JsonObject json) {
        try {
            List<Prize> pool = new ArrayList<>();
            for (JsonElement el : json.getAsJsonArray("pool")) {
                JsonObject o = el.getAsJsonObject();
                List<String> give = new ArrayList<>();
                if (o.has("give")) for (JsonElement c : o.getAsJsonArray("give")) give.add(c.getAsString());
                pool.add(new Prize(o.get("id").getAsString(), o.get("name").getAsString(), o.get("chance").getAsDouble(), give,
                        o.has("weapon") && o.get("weapon").getAsBoolean()));
            }
            List<String[]> weapons = new ArrayList<>();
            for (JsonElement el : json.getAsJsonArray("weapons")) {
                JsonObject o = el.getAsJsonObject();
                weapons.add(new String[] {o.get("id").getAsString(), o.get("name").getAsString()});
            }
            if (pool.isEmpty()) return false;
            config = new Config(json.get("set").getAsString(), Math.max(0, json.get("coinPrice").getAsLong()), pool, weapons);
            return true;
        } catch (RuntimeException e) {
            TFClient.LOGGER.warn("TF Ruleta: configuración no válida: {}", e.toString());
            return false;
        }
    }

    // --- Premios ---

    private static Result pick() {
        double total = config.pool().stream().mapToDouble(Prize::chance).sum();
        double r = ThreadLocalRandom.current().nextDouble() * total;
        Prize prize = config.pool().get(config.pool().size() - 1);
        for (Prize p : config.pool()) {
            if ((r -= p.chance()) < 0) {
                prize = p;
                break;
            }
        }
        if (prize.weapon() && !config.weapons().isEmpty()) {
            String[] w = config.weapons().get(ThreadLocalRandom.current().nextInt(config.weapons().size()));
            return new Result(w[0], w[1], List.of("tf web sets give {player} " + config.set() + " " + w[0]), true);
        }
        return new Result(prize.id(), prize.name(), prize.give(), false);
    }

    /** Cobra y elige los premios. null si no tiene bastantes monedas (no se cobra nada). */
    private static List<Result> charge(ServerPlayer player, int n) {
        if (!TFEconomy.take(player, config.coinPrice() * n)) return null;
        List<Result> results = new ArrayList<>();
        for (int i = 0; i < n; i++) results.add(pick());
        return results;
    }

    private static void give(ServerPlayer player, List<Result> results) {
        MinecraftServer server = player.getServer();
        String name = player.getGameProfile().getName();
        for (Result r : results) {
            for (String cmd : r.commands()) {
                List<String> errors = TFBridge.run(server, cmd.replace("{player}", name).replace("{uuid}", player.getStringUUID()));
                if (!errors.isEmpty()) TFClient.LOGGER.warn("TF Ruleta: «{}» falló: {}", cmd, errors.get(0));
            }
            if (capture != null) {
                JsonObject o = new JsonObject();
                o.addProperty("id", r.id());
                o.addProperty("name", r.name());
                if (r.weapon()) o.addProperty("weapon", true);
                capture.add(o);
            }
        }
        // Mensaje con todo lo que tocó, y anuncio si cayó un arma legendaria
        MutableComponent msg = Component.literal("✦ Ruleta: ").withStyle(ChatFormatting.GOLD);
        for (int i = 0; i < results.size(); i++) {
            Result r = results.get(i);
            if (i > 0) msg.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            msg.append(Component.literal(r.name()).withStyle(r.weapon() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.YELLOW));
        }
        player.sendSystemMessage(msg);
        Result best = best(results);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(6, 40, 12));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(best.weapon() ? "¡ARMA LEGENDARIA!" : "¡Premio!")
                .withStyle(best.weapon() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(best.name()).withStyle(ChatFormatting.WHITE)));
        player.playNotifySound(best.weapon() ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.8F, 1.0F);
        for (Result r : results) {
            if (r.weapon()) {
                server.getPlayerList().broadcastSystemMessage(Component.literal("✦ " + name + " ha ganado ").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(r.name()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                        .append(Component.literal(" en la ruleta").withStyle(ChatFormatting.LIGHT_PURPLE)), false);
            }
        }
    }

    /** El premio más raro de la tirada (el que se enseña al final de la animación). */
    private static Result best(List<Result> results) {
        Result best = results.get(0);
        double bestChance = Double.MAX_VALUE;
        for (Result r : results) {
            double c = r.weapon() ? -1 : config.pool().stream().filter(p -> p.id().equals(r.id())).mapToDouble(Prize::chance).findFirst().orElse(100);
            if (c < bestChance) {
                bestChance = c;
                best = r;
            }
        }
        return best;
    }

    /** Lo usa el puente: apunta los premios de los comandos que se ejecuten hasta stopCapture (para la web). */
    public static void startCapture() {
        capture = new ArrayList<>();
    }

    public static JsonArray stopCapture() {
        JsonArray out = new JsonArray();
        if (capture != null) capture.forEach(out::add);
        capture = null;
        return out;
    }

    // --- Comandos ---

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("ruleta")
                .then(Commands.literal("girar").requires(s -> s.hasPermission(3))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .then(Commands.argument("giros", IntegerArgumentType.integer(1, 50)).executes(TFRoulette::spinCommand))));
    }

    private static int spinCommand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "jugador");
        int n = IntegerArgumentType.getInteger(ctx, "giros");
        if (config == null) {
            ctx.getSource().sendFailure(Component.literal("La ruleta aún no está configurada (falta la conexión con la web)."));
            return 0;
        }
        List<Result> results = charge(player, n);
        if (results == null) {
            ctx.getSource().sendFailure(Component.literal("No tenías bastantes monedas en el servidor (" + n + " giro" + (n > 1 ? "s" : "")
                    + " = " + TFEconomy.format(config.coinPrice() * n) + "). No se ha cobrado nada."));
            player.sendSystemMessage(Component.literal("La ruleta de la web no pudo girar: necesitas "
                    + TFEconomy.format(config.coinPrice() * n) + ".").withStyle(ChatFormatting.RED));
            return 0;
        }
        give(player, results);
        return results.size();
    }
}
