package net.tierrasfantasticas.tfclient.economy;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.OptionalLong;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Monedas (dentro de /tf web, solo staff):
 * <pre>
 * /tf web monedas ver &lt;jugador&gt;                las de un jugador (nivel 2)
 * /tf web monedas dar|quitar &lt;jugador&gt; &lt;n&gt;    dar o quitar (nivel 3); lo usan los premios y productos de la web
 * /tf web monedas poner &lt;jugador&gt; &lt;n&gt;         saldo exacto (solo con las monedas del TF Client)
 * </pre>
 */
public final class TFEconomyCommands {
    private TFEconomyCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("monedas")
                .then(Commands.literal("ver").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .executes(ctx -> show(ctx, EntityArgument.getPlayer(ctx, "jugador")))))
                .then(change("dar", 0))
                .then(change("quitar", 1))
                .then(change("poner", 2));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> change(String name, int kind) {
        return Commands.literal(name).requires(s -> s.hasPermission(3))
                .then(Commands.argument("jugador", EntityArgument.player())
                        .then(Commands.argument("cantidad", LongArgumentType.longArg(0, 1_000_000_000_000L))
                                .executes(ctx -> change(ctx, kind))));
    }

    private static int show(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        OptionalLong balance = TFEconomy.balance(ctx.getSource().getServer(), player.getUUID());
        if (balance.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Esta economía no deja ver el saldo desde el TF Client: usa el comando de economía del servidor."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " tiene ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(TFEconomy.format(balance.getAsLong())).withStyle(ChatFormatting.GOLD)), false);
        return (int) Math.min(Integer.MAX_VALUE, balance.getAsLong());
    }

    private static int change(CommandContext<CommandSourceStack> ctx, int kind) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "jugador");
        long amount = LongArgumentType.getLong(ctx, "cantidad");
        String name = player.getGameProfile().getName();
        boolean ok = switch (kind) {
            case 0 -> TFEconomy.give(ctx.getSource().getServer(), player.getUUID(), name, amount);
            case 1 -> TFEconomy.take(player, amount);
            default -> TFEconomy.set(ctx.getSource().getServer(), player.getUUID(), amount);
        };
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal(switch (kind) {
                case 1 -> name + " no tiene bastantes monedas.";
                case 2 -> "«poner» solo funciona con las monedas del TF Client (economy.mode=tf).";
                default -> "No se pudieron dar las monedas: revisa economy.* en config/tfclient/servidor.properties.";
            }));
            return 0;
        }
        String verb = kind == 0 ? "Dadas " : kind == 1 ? "Quitadas " : "Saldo puesto a ";
        ctx.getSource().sendSuccess(() -> Component.literal(verb + TFEconomy.format(amount) + (kind == 2 ? " para " : kind == 0 ? " a " : " a ") + name)
                .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }
}
