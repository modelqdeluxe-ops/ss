/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
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
 * /tf web verdes ver|dar|quitar &lt;jugador&gt; [n]   las Fantastic Gacha Coins (1.3.115: las dan los rangos y crates)
 * /tf web monedas|verdes retirar &lt;uuid|jugador&gt; &lt;n&gt;  (1.3.125) reembolso: quita lo que tenga (también desconectado) y
 *                                                  lo que falte queda como deuda (saldo negativo; TFDebt)
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
                .then(change("poner", 2))
                .then(revoke(false));
    }

    /** (1.3.125) retirar &lt;uuid|jugador&gt; &lt;n&gt;: lo manda la web al reembolsar una compra que dio monedas. */
    private static LiteralArgumentBuilder<CommandSourceStack> revoke(boolean green) {
        return Commands.literal("retirar").requires(s -> s.hasPermission(3))
                .then(Commands.argument("jugador", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .then(Commands.argument("cantidad", LongArgumentType.longArg(1, 1_000_000_000_000L)).executes(ctx -> {
                            String who = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "jugador");
                            long n = LongArgumentType.getLong(ctx, "cantidad");
                            net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();
                            java.util.UUID uuid = null;
                            String name = who;
                            try {
                                uuid = java.util.UUID.fromString(who);
                            } catch (IllegalArgumentException notUuid) {
                                ServerPlayer online = server.getPlayerList().getPlayerByName(who);
                                if (online != null) uuid = online.getUUID();
                                else if (server.getProfileCache() != null) {
                                    uuid = server.getProfileCache().get(who).map(com.mojang.authlib.GameProfile::getId).orElse(null);
                                }
                            }
                            if (uuid == null) {
                                ctx.getSource().sendFailure(Component.literal("No conozco a «" + who + "»."));
                                return 0;
                            }
                            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
                            if (online != null) name = online.getGameProfile().getName();
                            long now = green ? net.tierrasfantasticas.tfclient.pad.server.PadGreen.revoke(server, uuid, n)
                                    : TFEconomy.revoke(server, uuid, online != null ? name : null, n);
                            long debt = n - now;
                            String what = green ? " Fantastic Gacha Coins" : " monedas";
                            if (online != null) {
                                online.sendSystemMessage(Component.literal("Reembolso: se te retiran " + n + what
                                        + (debt > 0 ? " (te faltaban " + debt + ": saldo negativo, se paga con lo próximo que ganes)" : "") + ".")
                                        .withStyle(ChatFormatting.RED));
                            }
                            String done = "Retiradas " + n + what + " a " + name + (debt > 0 ? " (" + debt + " quedan como deuda)" : "");
                            ctx.getSource().sendSuccess(() -> Component.literal(done).withStyle(ChatFormatting.YELLOW), true);
                            return 1;
                        })));
    }

    /** (1.3.115) Las Fantastic Gacha Coins (las verdes del Gachapón): ver, dar y quitar. */
    public static LiteralArgumentBuilder<CommandSourceStack> green() {
        return Commands.literal("verdes")
                .then(Commands.literal("ver").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("jugador", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "jugador");
                            long n = net.tierrasfantasticas.tfclient.pad.server.PadGreen.balance(p.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().getName() + " tiene " + n
                                    + " Fantastic Gacha Coins").withStyle(ChatFormatting.GREEN), false);
                            return (int) Math.min(Integer.MAX_VALUE, n);
                        })))
                .then(greenChange("dar", true))
                .then(greenChange("quitar", false))
                .then(revoke(true));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> greenChange(String name, boolean give) {
        return Commands.literal(name).requires(s -> s.hasPermission(3))
                .then(Commands.argument("jugador", EntityArgument.player())
                        .then(Commands.argument("cantidad", LongArgumentType.longArg(1, 1_000_000L)).executes(ctx -> {
                            ServerPlayer p = EntityArgument.getPlayer(ctx, "jugador");
                            long n = LongArgumentType.getLong(ctx, "cantidad");
                            if (give) {
                                net.tierrasfantasticas.tfclient.pad.server.PadGreen.add(p, n);
                            } else if (!net.tierrasfantasticas.tfclient.pad.server.PadGreen.take(p, n)) {
                                ctx.getSource().sendFailure(Component.literal(p.getGameProfile().getName() + " no tiene bastantes Fantastic Gacha Coins."));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal((give ? "Dadas " : "Quitadas ") + n + " Fantastic Gacha Coins a "
                                    + p.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);
                            return 1;
                        })));
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
