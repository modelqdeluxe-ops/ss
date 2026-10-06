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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.menu.TFIcon;
import net.tierrasfantasticas.tfclient.menu.TFMenu;
import net.tierrasfantasticas.tfclient.server.TFBridge;
import net.tierrasfantasticas.tfclient.server.TFServerConfig;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Ruleta con las monedas del servidor. Los premios, sus probabilidades y el precio son los mismos que en la web (los
 * manda la web por el puente y se guardan en config/tfclient-ruleta.json por si arranca sin conexión).
 * <pre>
 * /tf ruleta                         abre la ruleta (todos): se gira con monedas y se ve girar
 * /tf ruleta girar &lt;jugador&gt; &lt;n&gt;     gira n veces cobrándole las monedas (lo usa la web; staff, nivel 3)
 * </pre>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFRoulette {
    /** Un premio: comandos a ejecutar ({player}) o, si weapon, un arma al azar del set. */
    public record Prize(String id, String name, double chance, List<String> give, boolean weapon) {}

    private record Config(String set, long coinPrice, List<Prize> pool, List<String[]> weapons) {}

    /** Premio ya elegido: nombre, comandos e icono. */
    private record Result(String id, String name, List<String> commands, ItemStack icon, boolean weapon) {}

    private static Config config;
    private static List<JsonObject> capture;
    private static final Map<UUID, Spin> spins = new HashMap<>();
    private static final int[] SPIN_COUNTS = {1, 5, 10};
    private static final int REEL_ROW = 27;
    /** Último paso del carrete: el centro (casilla 4 de la fila) enseña reel[LAST_STEP + 4], que es el premio. */
    private static final int LAST_STEP = 36;

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
            return new Result(w[0], w[1], List.of("tf web sets give {player} " + config.set() + " " + w[0]), weaponIcon(w[0]), true);
        }
        return new Result(prize.id(), prize.name(), prize.give(), icon(prize), false);
    }

    private static ItemStack weaponIcon(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(TFClient.MOD_ID, config.set() + "_" + id));
        return new ItemStack(item == null || item == Items.AIR ? Items.NETHERITE_SWORD : item);
    }

    /** Icono de un premio: el objeto que da su «give», monedas para «tf monedas dar», un arma para las armas. */
    private static ItemStack icon(Prize p) {
        if (p.weapon()) return config.weapons().isEmpty() ? new ItemStack(Items.NETHERITE_SWORD) : weaponIcon(config.weapons().get(0)[0]);
        for (String cmd : p.give()) {
            String[] parts = cmd.trim().split("\\s+");
            if (parts.length >= 3 && parts[0].equals("give")) {
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(parts[2]));
                if (item != null && item != Items.AIR) {
                    int count = parts.length >= 4 && parts[3].matches("\\d+") ? Integer.parseInt(parts[3]) : 1;
                    return new ItemStack(item, Math.max(1, Math.min(64, count)));
                }
            }
            if (cmd.contains("monedas")) return new ItemStack(Items.SUNFLOWER);
        }
        return new ItemStack(Items.CHEST);
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
                .executes(ctx -> {
                    open(ctx.getSource().getPlayerOrException());
                    return 1;
                })
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

    // --- Menú: la ruleta que se ve girar ---

    private static final class Spin {
        final ServerPlayer player;
        final TFMenu menu;
        final List<Result> results;
        final List<ItemStack> reel = new ArrayList<>();
        int step;
        int wait;
        boolean done;

        Spin(ServerPlayer player, TFMenu menu, List<Result> results) {
            this.player = player;
            this.menu = menu;
            this.results = results;
        }
    }

    public static void open(ServerPlayer player) {
        if (config == null) {
            player.sendSystemMessage(Component.literal("La ruleta aún no está lista: falta la conexión con la web.").withStyle(ChatFormatting.RED));
            return;
        }
        TFMenu.open(player, 6, Component.literal("Ruleta"), menu -> {
            fill(menu, player, null);
            menu.onClose(() -> {
                Spin spin = spins.get(player.getUUID());
                if (spin != null && spin.menu == menu) finish(spin);
            });
        });
    }

    private static void fill(TFMenu menu, ServerPlayer player, List<ItemStack> reel) {
        menu.clear();
        ItemStack pane = TFIcon.of(Items.BLACK_STAINED_GLASS_PANE).name(" ").build();
        ItemStack purple = TFIcon.of(Items.PURPLE_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) menu.set(i, i >= 18 && i < 45 ? purple : pane);
        // Fila 1: los premios con su probabilidad
        double total = config.pool().stream().mapToDouble(Prize::chance).sum();
        for (int i = 0; i < Math.min(9, config.pool().size()); i++) {
            Prize p = config.pool().get(i);
            int pct = (int) Math.round(p.chance() / total * 100);
            TFIcon icon = TFIcon.of(icon(p)).name(p.name(), p.weapon() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.YELLOW)
                    .line("Probabilidad: " + pct + "%", ChatFormatting.WHITE);
            if (p.weapon()) icon.text("Una de las " + config.weapons().size() + " armas legendarias de Nazgul, al azar.");
            menu.set(9 + i, icon.glow(p.weapon()).build());
        }
        menu.set(4, TFIcon.of(Items.NETHER_STAR).name("Ruleta", ChatFormatting.GOLD)
                .text("Cada giro da uno de los premios de abajo según su probabilidad. Los mismos que en la web.")
                .line("Precio: " + TFEconomy.format(config.coinPrice()) + " por giro", ChatFormatting.YELLOW).build());
        // Fila del carrete con flechas encima y debajo del centro
        menu.set(22, TFIcon.of(Items.YELLOW_STAINED_GLASS_PANE).name("▼", ChatFormatting.GOLD).build());
        menu.set(40, TFIcon.of(Items.YELLOW_STAINED_GLASS_PANE).name("▲", ChatFormatting.GOLD).build());
        for (int i = 0; i < 9; i++) {
            ItemStack stack = reel != null && i < reel.size() ? reel.get(i) : icon(config.pool().get(i % config.pool().size()));
            menu.set(REEL_ROW + i, stack);
        }
        // Botones
        OptionalLong balance = TFEconomy.balance(player.getServer(), player.getUUID());
        menu.set(45, TFIcon.of(Items.SUNFLOWER).name("Tus " + TFServerConfig.currency(), ChatFormatting.GOLD)
                .line(balance.isPresent() ? TFEconomy.format(balance.getAsLong()) : "Míralas con el comando de economía", ChatFormatting.YELLOW).build());
        int[] slots = {47, 49, 51};
        for (int k = 0; k < SPIN_COUNTS.length; k++) {
            int n = SPIN_COUNTS[k];
            menu.set(slots[k], TFIcon.of(n == 1 ? Items.LIME_DYE : n == 5 ? Items.LIME_CONCRETE : Items.EMERALD_BLOCK).count(n)
                    .name("Girar " + n + (n == 1 ? " vez" : " veces"), ChatFormatting.GREEN)
                    .line(TFEconomy.format(config.coinPrice() * n), ChatFormatting.YELLOW)
                    .blank().line("Clic para girar", ChatFormatting.GRAY).build(), (pl, t, b) -> start(pl, menu, n));
        }
        menu.set(53, TFIcon.of(Items.BARRIER).name("Cerrar", ChatFormatting.RED).build(), (pl, t, b) -> pl.closeContainer());
        menu.update();
    }

    private static void start(ServerPlayer player, TFMenu menu, int n) {
        if (spins.containsKey(player.getUUID())) return; // ya está girando
        List<Result> results = charge(player, n);
        if (results == null) {
            player.sendSystemMessage(Component.literal("No tienes bastantes " + TFServerConfig.currency() + ": " + n + " giro"
                    + (n > 1 ? "s" : "") + " cuestan " + TFEconomy.format(config.coinPrice() * n) + ".").withStyle(ChatFormatting.RED));
            player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.MASTER, 0.6F, 1.0F);
            return;
        }
        Spin spin = new Spin(player, menu, results);
        // El carrete: premios al azar y, al final, el mejor de la tirada en el centro
        for (int i = 0; i < 44; i++) spin.reel.add(icon(config.pool().get(ThreadLocalRandom.current().nextInt(config.pool().size()))));
        spin.reel.set(LAST_STEP + 4, best(results).icon().copy());
        spins.put(player.getUUID(), spin);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || spins.isEmpty()) return;
        for (Spin spin : List.copyOf(spins.values())) {
            if (spin.player.hasDisconnected() || spin.player.containerMenu != spin.menu) {
                finish(spin);
                continue;
            }
            if (spin.wait-- > 0) continue;
            if (spin.step > LAST_STEP) {
                finish(spin);
                continue;
            }
            // El carrete corre hacia la izquierda y frena al final: la casilla del centro (31) acaba en el premio
            int offset = spin.step;
            for (int i = 0; i < 9; i++) spin.menu.set(REEL_ROW + i, spin.reel.get(offset + i));
            spin.menu.update();
            spin.player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 0.5F, 1.2F + spin.step * 0.01F);
            spin.step++;
            int left = LAST_STEP - spin.step;
            spin.wait = left > 14 ? 1 : left > 7 ? 2 : left > 2 ? 4 : 8;
        }
    }

    private static void finish(Spin spin) {
        if (spin.done) return;
        spin.done = true;
        spins.remove(spin.player.getUUID());
        give(spin.player, spin.results);
        if (!spin.player.hasDisconnected() && spin.player.containerMenu == spin.menu) {
            fill(spin.menu, spin.player, spin.reel.subList(LAST_STEP, LAST_STEP + 9));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Spin spin = spins.get(event.getEntity().getUUID());
        if (spin != null) finish(spin);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (Spin spin : List.copyOf(spins.values())) finish(spin);
    }
}
