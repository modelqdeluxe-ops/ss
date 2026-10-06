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
import net.tierrasfantasticas.tfclient.jobs.TFJobsCommands;
import net.tierrasfantasticas.tfclient.server.TFBridgeCommands;
import net.tierrasfantasticas.tfclient.shop.TFCoinShop;

/**
 * /tf web sets list                              lista los sets
 * /tf web sets give &lt;jugadores&gt; &lt;set&gt; [objeto]   da el set entero (o un objeto suyo)
 * Hace falta ser operador (nivel 2). Funciona también en servidores Mohist.
 * /tf vincular y /tf rango están en {@link TFBridgeCommands}; /tf tienda en {@link TFCoinShop};
 * /tf monedas en {@link TFEconomyCommands}; /tf jobs (o /tf oficios) en {@link TFJobsCommands}.
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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /tf no pide permisos: cada subcomando pone los suyos (/tf vincular es para todos los jugadores).
        var tf = Commands.literal("tf")
                .then(TFBridgeCommands.vincular())
                .then(TFBridgeCommands.rango())
                .then(TFCoinShop.command())
                .then(TFEconomyCommands.command());
        for (var jobs : TFJobsCommands.commands()) tf = tf.then(jobs);
        dispatcher.register(tf
                .then(Commands.literal("web")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("sets")
                                .then(Commands.literal("list").executes(TFCommands::list))
                                .then(Commands.literal("give")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("set", StringArgumentType.word())
                                                        .suggests(SETS)
                                                        .executes(ctx -> give(ctx, null))
                                                        .then(Commands.argument("item", StringArgumentType.word())
                                                                .suggests(ITEMS)
                                                                .executes(ctx -> give(ctx, StringArgumentType.getString(ctx, "item"))))))))));
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
