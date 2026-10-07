package net.tierrasfantasticas.tfclient.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rangos de la tienda (dentro de /tf web):
 * <pre>
 * /tf web rango &lt;jugador&gt;                 muestra su rango (staff, nivel 3)
 * /tf web rango &lt;jugador&gt; &lt;rango|ninguno&gt; pone o quita un rango: LuckPerms, nametag y web (staff, nivel 3)
 * /tf web rango permisos                  vuelve a poner en LuckPerms y EssentialsX lo que trae cada rango
 * </pre>
 */
public final class TFBridgeCommands {
    private static final String NONE = "ninguno";

    private static final SuggestionProvider<CommandSourceStack> RANKS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            Stream.concat(TFRanks.list().stream().map(TFRanks.Rank::key), Stream.of(NONE)), builder);

    private TFBridgeCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> rango() {
        return Commands.literal("rango")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("permisos").executes(TFBridgeCommands::permissions))
                .then(Commands.argument("jugador", EntityArgument.player())
                        .executes(TFBridgeCommands::showRank)
                        .then(Commands.argument("rango", StringArgumentType.word())
                                .suggests(RANKS)
                                .executes(TFBridgeCommands::setRank)));
    }

    private static int permissions(CommandContext<CommandSourceStack> ctx) {
        List<String> report = TFRankPerms.force(ctx.getSource().getServer());
        for (String line : report) {
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.YELLOW), true);
        }
        return report.size();
    }

    private static int showRank(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "jugador");
        TFRanks.Rank rank = TFRanks.of(player.getUUID());
        String name = player.getGameProfile().getName();
        Component text = rank == null
                ? Component.literal(name + " no tiene rango de la tienda.").withStyle(ChatFormatting.GRAY)
                : Component.literal(name + " tiene ").withStyle(ChatFormatting.GRAY).append(rank.tag());
        ctx.getSource().sendSuccess(() -> text, false);
        return rank == null ? 0 : rank.tier();
    }

    private static int setRank(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "jugador");
        String wanted = StringArgumentType.getString(ctx, "rango");
        if (TFRanks.list().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Aún no hay conexión con la web: no se conocen los rangos."));
            return 0;
        }
        TFRanks.Rank rank = null;
        if (!NONE.equalsIgnoreCase(wanted)) {
            rank = TFRanks.find(wanted);
            if (rank == null) {
                String options = TFRanks.list().stream().map(TFRanks.Rank::key).collect(Collectors.joining(", "));
                ctx.getSource().sendFailure(Component.literal("Rango desconocido. Opciones: " + options + ", " + NONE));
                return 0;
            }
        }

        // Los mismos comandos que una compra: el grupo nuevo y fuera los demás grupos de rango.
        MinecraftServer server = ctx.getSource().getServer();
        String name = player.getGameProfile().getName();
        List<String> failed = new ArrayList<>();
        for (TFRanks.Rank r : TFRanks.list()) {
            if (r.group().isEmpty()) continue;
            boolean add = rank != null && r.id().equals(rank.id());
            String command = "lp user " + name + " parent " + (add ? "add " : "remove ") + r.group();
            if (!TFBridge.run(server, command).isEmpty() && add) failed.add(command);
        }
        TFRanks.set(server, player, rank);
        TFBridge.queueRankChange(player.getUUID(), rank == null ? null : rank.id());

        TFRanks.Rank finalRank = rank;
        ctx.getSource().sendSuccess(() -> finalRank == null
                ? Component.literal("Quitado el rango de " + name).withStyle(ChatFormatting.YELLOW)
                : Component.literal("Ahora " + name + " es ").withStyle(ChatFormatting.YELLOW).append(finalRank.tag()), true);
        if (!failed.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No está LuckPerms (/lp): no se ha podido poner el grupo «" + failed.get(0) + "»."));
        }
        return 1;
    }
}
