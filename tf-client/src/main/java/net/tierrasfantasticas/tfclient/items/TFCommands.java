package net.tierrasfantasticas.tfclient.items;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomyCommands;
import net.tierrasfantasticas.tfclient.server.TFBridgeCommands;
import net.tierrasfantasticas.tfclient.shop.TFCoinShop;
import net.tierrasfantasticas.tfclient.shop.TFRoulette;

/**
 * Solo hay dos comandos (todo lo de los jugadores se hace desde el pad): /tf web (staff) y /tf reload (staff).
 * <pre>
 * /tf web sets list                              lista los sets
 * /tf web sets give &lt;jugadores&gt; &lt;set&gt; [objeto]   da el set entero (o un objeto suyo), vinculado a cada jugador
 * /tf web sets revoke &lt;uuid&gt; &lt;pedido&gt; &lt;set&gt; [objeto]
 *                                                retira lo de una compra reembolsada, esté donde esté ({@link TFRevocations})
 * /tf web tienda ...                             la tienda de monedas de la web ({@link TFCoinShop})
 * /tf web rango | monedas | ruleta ...           los usa la web al entregar (staff; {@link TFBridgeCommands},
 *                                                {@link TFEconomyCommands}, {@link TFRoulette})
 * /tf web claims ...                             las protecciones de zona (TF Claims: panel, bypass, list, stats...)
 * /tf web kits | viajes | comunidad | vfx ...    herramientas del staff de cada sistema
 * /tf reload                                     vuelve a leer TODAS las configs de config/tfclient/ y refresca los pads
 *                                                abiertos ({@link net.tierrasfantasticas.tfclient.pad.server.TFReload})
 * </pre>
 * /tf web pide ser operador (nivel 2). Funciona también en servidores Mohist.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFCommands {
    private static final DynamicCommandExceptionType UNKNOWN_SET =
            new DynamicCommandExceptionType(id -> Component.literal("No existe el set «" + id + "». Usa /tf web sets list"));
    private static final DynamicCommandExceptionType UNKNOWN_ITEM =
            new DynamicCommandExceptionType(id -> Component.literal("Ese set no tiene el objeto «" + id + "»"));

    private static final SuggestionProvider<CommandSourceStack> SETS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(TFSets.all().keySet(), builder);

    private static final SuggestionProvider<CommandSourceStack> ITEMS = (ctx, builder) -> {
        String setId;
        try {
            setId = StringArgumentType.getString(ctx, "set");
        } catch (IllegalArgumentException e) {
            return builder.buildFuture();
        }
        List<RegistryObject<Item>> list = TFItems.BY_SET.get(setId);
        if (list == null) return builder.buildFuture();
        String prefix = setId + "_";
        return SharedSuggestionProvider.suggest(list.stream().map(o -> o.getId().getPath().substring(prefix.length())), builder);
    };

    private TFCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /** /tf web pad admin [jugador]: el pad de administrador (solo funciona en manos del staff). */
    private static int giveAdminPad(CommandSourceStack source, java.util.Collection<net.minecraft.server.level.ServerPlayer> players) {
        int n = 0;
        for (net.minecraft.server.level.ServerPlayer p : players) {
            if (!p.hasPermissions(3)) {
                source.sendFailure(net.minecraft.network.chat.Component.literal(p.getGameProfile().getName() + " no es staff (nivel 3): no se le da."));
                continue;
            }
            net.minecraft.world.item.ItemStack pad = new net.minecraft.world.item.ItemStack(TFItems.ADMIN_PAD.get());
            if (!p.getInventory().add(pad)) p.drop(pad, false);
            n++;
        }
        int given = n;
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Pad de administrador dado a " + given + (given == 1 ? " jugador." : " jugadores.")), true);
        return n;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tf")
                .then(Commands.literal("reload").requires(s -> s.hasPermission(3)).executes(TFCommands::reload))
                .then(Commands.literal("web")
                        .requires(source -> source.hasPermission(2))
                        .then(TFCoinShop.command())
                        .then(TFBridgeCommands.rango())
                        .then(TFEconomyCommands.command())
                        .then(TFRoulette.command())
                        .then(net.tierrasfantasticas.tfclient.vfx.VfxServer.command())
                        .then(net.tierrasfantasticas.tfclient.claims.command.ClaimAdminCommands.command())
                        .then(net.tierrasfantasticas.tfclient.pad.server.PadKits.command())
                        .then(net.tierrasfantasticas.tfclient.pad.server.PadTravel.command())
                        .then(net.tierrasfantasticas.tfclient.pad.PadCommunityServer.command())
                        .then(Commands.literal("pad").requires(s -> s.hasPermission(3))
                                .then(Commands.literal("admin")
                                        .executes(ctx -> giveAdminPad(ctx.getSource(), java.util.List.of(ctx.getSource().getPlayerOrException())))
                                        .then(Commands.argument("jugador", EntityArgument.players())
                                                .executes(ctx -> giveAdminPad(ctx.getSource(), EntityArgument.getPlayers(ctx, "jugador"))))))
                        .then(Commands.literal("sets")
                                .then(Commands.literal("list").executes(TFCommands::list))
                                .then(Commands.literal("give")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("set", StringArgumentType.word())
                                                        .suggests(SETS)
                                                        .executes(ctx -> give(ctx, null))
                                                        .then(Commands.argument("item", StringArgumentType.word())
                                                                .suggests(ITEMS)
                                                                .executes(ctx -> give(ctx, StringArgumentType.getString(ctx, "item")))))))
                                .then(Commands.literal("revoke")
                                        .then(Commands.argument("uuid", StringArgumentType.word())
                                                .then(Commands.argument("order", StringArgumentType.word())
                                                        .then(Commands.argument("set", StringArgumentType.word())
                                                                .suggests(SETS)
                                                                .executes(ctx -> revoke(ctx, null))
                                                                .then(Commands.argument("item", StringArgumentType.word())
                                                                        .suggests(ITEMS)
                                                                        .executes(ctx -> revoke(ctx, StringArgumentType.getString(ctx, "item")))))))))));
    }

    /** /tf reload: todas las configs a la vez (lo mismo que RECARGAR CONFIGS en el pad de administrador). */
    private static int reload(CommandContext<CommandSourceStack> ctx) {
        net.tierrasfantasticas.tfclient.pad.server.TFReload.Result r = net.tierrasfantasticas.tfclient.pad.server.TFReload.all();
        ctx.getSource().sendSuccess(() -> Component.literal("Configs recargadas: " + String.join(", ", r.done()) + ".")
                .withStyle(ChatFormatting.GREEN), true);
        for (String w : r.problems()) ctx.getSource().sendFailure(Component.literal(w));
        return r.done().size();
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Component.literal("Sets de Tierras Fantásticas (" + TFSets.all().size() + "):").withStyle(ChatFormatting.GOLD), false);
        for (TFSets.SetDef set : TFSets.all().values()) {
            Component line = Component.literal(" • " + set.id()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(set.color())))
                    .append(Component.literal(" — " + set.name() + " (" + set.items().size() + " objetos)").withStyle(ChatFormatting.GRAY));
            source.sendSuccess(() -> line, false);
        }
        return TFSets.all().size();
    }

    private static final DynamicCommandExceptionType BAD_UUID =
            new DynamicCommandExceptionType(id -> Component.literal("UUID no válido: " + id));

    /** Compra reembolsada: se apunta y se quita al momento lo que esté cargado; lo demás, cuando se cargue. */
    private static int revoke(CommandContext<CommandSourceStack> ctx, String itemId) throws CommandSyntaxException {
        String uuidText = StringArgumentType.getString(ctx, "uuid");
        java.util.UUID owner;
        try {
            owner = java.util.UUID.fromString(uuidText);
        } catch (IllegalArgumentException e) {
            throw BAD_UUID.create(uuidText);
        }
        String order = StringArgumentType.getString(ctx, "order");
        String setId = StringArgumentType.getString(ctx, "set");
        int removed = TFRevocations.revoke(ctx.getSource().getServer(), new TFRevocations.Entry(owner, order, setId, itemId));
        ctx.getSource().sendSuccess(() -> Component.literal("Retirada la compra " + order + " (" + setId + (itemId != null ? " " + itemId : "")
                + "): " + removed + " objetos quitados ya; el resto se quita en cuanto se cargue").withStyle(ChatFormatting.RED), true);
        return Math.max(1, removed);
    }

    private static int give(CommandContext<CommandSourceStack> ctx, String itemId) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
        String setId = StringArgumentType.getString(ctx, "set");
        TFSets.SetDef set = TFSets.get(setId);
        List<RegistryObject<Item>> items = TFItems.BY_SET.get(setId);
        if (set == null || items == null) throw UNKNOWN_SET.create(setId);
        if (itemId != null) {
            String full = setId + "_" + itemId;
            items = items.stream().filter(o -> o.getId().getPath().equals(full)).toList();
            if (items.isEmpty()) throw UNKNOWN_ITEM.create(itemId);
        }
        for (ServerPlayer player : players) {
            for (RegistryObject<Item> obj : items) {
                ItemStack stack = new ItemStack(obj.get());
                TFBinding.bind(stack, player); // los cosméticos no se vinculan (ver TFBinding)
                TFRevocations.stamp(stack); // el pedido de la web, si lo entrega el puente
                boolean added = player.getInventory().add(stack);
                if (!added || !stack.isEmpty()) {
                    ItemEntity drop = player.drop(stack, false);
                    if (drop != null) {
                        drop.setNoPickUpDelay();
                        drop.setTarget(player.getUUID());
                    }
                }
            }
            player.containerMenu.broadcastChanges();
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                    0.3F, 1.4F);
        }
        int count = items.size();
        String what = itemId != null ? "«" + itemId + "» del set " + set.name() : "el set " + set.name() + " (" + count + " objetos)";
        String who = players.size() == 1 ? players.iterator().next().getGameProfile().getName() : players.size() + " jugadores";
        ctx.getSource().sendSuccess(() -> Component.literal("Entregado " + what + " a " + who)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(set.color()))), true);
        return count * players.size();
    }
}
