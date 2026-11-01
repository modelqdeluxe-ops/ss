/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.revive;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * /revive, como en el revivemod (help, surrender, self; con permiso 2: status, reload, force, kill, down y set). (1.3.157)
 * Además, /revive set rematar jugadores|mobs &lt;true|false&gt;, y reload recarga config/tfclient/revivir.json (también
 * lo hace /tf reload).
 */
public final class ReviveCommands {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("revive");
        root.executes(ReviveCommands::help);
        root.then(Commands.literal("help").executes(ReviveCommands::help));
        root.then(Commands.literal("surrender").executes(ReviveCommands::surrender));
        root.then(Commands.literal("self").executes(ReviveCommands::selfRevive));
        root.then(Commands.literal("status").requires(s -> s.hasPermission(2)).executes(ReviveCommands::status));
        root.then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(ReviveCommands::reload));
        root.then(Commands.literal("force").requires(s -> s.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players()).executes(ReviveCommands::forceRevive)));
        root.then(Commands.literal("kill").requires(s -> s.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players()).executes(ReviveCommands::forceKill)));
        root.then(Commands.literal("down").requires(s -> s.hasPermission(2))
                .then(Commands.argument("targets", EntityArgument.players()).executes(ReviveCommands::forceDown)));
        LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set").requires(s -> s.hasPermission(2));
        set.then(Commands.literal("time").then(Commands.argument("seconds", IntegerArgumentType.integer(1, 6000)).executes(ctx -> {
            int v = IntegerArgumentType.getInteger(ctx, "seconds");
            TFRevive.getConfig().downTimeSeconds = v;
            TFRevive.saveConfig();
            ctx.getSource().sendSuccess(() -> green("downTimeSeconds = " + v), true);
            return 1;
        })));
        set.then(Commands.literal("distance").then(Commands.argument("blocks", DoubleArgumentType.doubleArg(1.0, 32.0)).executes(ctx -> {
            double v = DoubleArgumentType.getDouble(ctx, "blocks");
            TFRevive.getConfig().reviveDistance = v;
            TFRevive.saveConfig();
            ctx.getSource().sendSuccess(() -> green("reviveDistance = " + v), true);
            return 1;
        })));
        set.then(Commands.literal("channel").then(Commands.argument("ticks", IntegerArgumentType.integer(20, 6000)).executes(ctx -> {
            int v = IntegerArgumentType.getInteger(ctx, "ticks");
            TFRevive.getConfig().reviveTimeTicks = v;
            TFRevive.saveConfig();
            ctx.getSource().sendSuccess(() -> green("reviveTimeTicks = " + v), true);
            return 1;
        })));
        set.then(Commands.literal("selfcost").then(Commands.argument("levels", IntegerArgumentType.integer(0, 1000)).executes(ctx -> {
            int v = IntegerArgumentType.getInteger(ctx, "levels");
            TFRevive.getConfig().selfReviveLevelCost = v;
            TFRevive.saveConfig();
            ctx.getSource().sendSuccess(() -> green("selfReviveLevelCost = " + v), true);
            return 1;
        })));
        // (1.3.157) quién puede rematar a un noqueado
        set.then(Commands.literal("rematar")
                .then(Commands.literal("jugadores").then(Commands.argument("activado", BoolArgumentType.bool()).executes(ctx -> {
                    boolean v = BoolArgumentType.getBool(ctx, "activado");
                    TFRevive.getConfig().playersCanFinish = v;
                    TFRevive.saveConfig();
                    ctx.getSource().sendSuccess(() -> green("playersCanFinish = " + v + (v ? " (los jugadores pueden rematar)" : " (los jugadores no rematan)")), true);
                    return 1;
                })))
                .then(Commands.literal("mobs").then(Commands.argument("activado", BoolArgumentType.bool()).executes(ctx -> {
                    boolean v = BoolArgumentType.getBool(ctx, "activado");
                    TFRevive.getConfig().mobsCanFinish = v;
                    TFRevive.saveConfig();
                    ctx.getSource().sendSuccess(() -> green("mobsCanFinish = " + v + (v ? " (los mobs pueden rematar)" : " (los mobs no rematan)")), true);
                    return 1;
                }))));
        root.then(set);
        dispatcher.register(root);
    }

    private static int surrender(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayer();
        if (p == null) {
            return 0;
        } else if (!DownManager.isDown(p)) {
            p.displayClientMessage(Component.literal("No estás desangrándote.").withStyle(ChatFormatting.RED), false);
            return 0;
        } else {
            p.displayClientMessage(Component.literal("Te has rendido.").withStyle(ChatFormatting.DARK_RED), false);
            DownManager.forceDeath(p, p.damageSources().genericKill());
            return 1;
        }
    }

    private static int selfRevive(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ((CommandSourceStack)ctx.getSource()).getPlayer();
        if (p == null) {
            return 0;
        } else if (!DownManager.isDown(p)) {
            p.displayClientMessage(Component.literal("No estás desangrándote.").withStyle(ChatFormatting.RED), false);
            return 0;
        } else if (!TFRevive.getConfig().allowSelfRevive) {
            p.displayClientMessage(Component.literal("El auto-revivir está desactivado.").withStyle(ChatFormatting.RED), false);
            return 0;
        } else {
            int cost = TFRevive.getConfig().selfReviveLevelCost;
            if (p.experienceLevel < cost) {
                p.displayClientMessage(Component.literal("Necesitas " + cost + " niveles de experiencia (tienes " + p.experienceLevel + ").").withStyle(ChatFormatting.RED), false);
                return 0;
            } else if (DownManager.selfRevive(p)) {
                p.displayClientMessage(Component.literal("Te has revivido por " + cost + " niveles.").withStyle(ChatFormatting.GREEN), false);
                return 1;
            } else {
                return 0;
            }
        }
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = (CommandSourceStack)ctx.getSource();
        src.sendSuccess(() -> Component.literal("--- Revive Mod ---").withStyle(new ChatFormatting[]{ChatFormatting.GOLD, ChatFormatting.BOLD}), false);
        src.sendSuccess(
            () -> Component.literal("/revive surrender")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" - rendirte y morir (estando desangrándote)").withStyle(ChatFormatting.GRAY)),
            false
        );
        src.sendSuccess(
            () -> Component.literal("/revive self")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" - auto-revivirte pagando niveles").withStyle(ChatFormatting.GRAY)),
            false
        );
        if (src.hasPermission(2)) {
            src.sendSuccess(() -> Component.literal("/revive status | force | kill | down | set | reload").withStyle(ChatFormatting.AQUA), false);
        }

        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = (CommandSourceStack)ctx.getSource();
        if (DownManager.all().isEmpty()) {
            src.sendSuccess(() -> Component.literal("No hay jugadores desangrándose.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        } else {
            src.sendSuccess(() -> Component.literal("Desangrándose:").withStyle(ChatFormatting.GOLD), false);

            for (DownState st : DownManager.all()) {
                ServerPlayer p = src.getServer().getPlayerList().getPlayer(st.playerUuid);
                String name = p != null ? p.getGameProfile().getName() : st.playerUuid.toString();
                int sec = (st.remainingTicks + 19) / 20;
                src.sendSuccess(() -> Component.literal(" - " + name + " (" + sec + "s restantes)").withStyle(ChatFormatting.YELLOW), false);
            }

            return DownManager.all().size();
        }
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        try {
            TFRevive.reloadConfig();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("config/tfclient/" + TFRevive.FILE + " tiene un error y se queda como estaba: " + e.getMessage()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> green("Config recargada."), true);
        return 1;
    }

    private static int forceRevive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int n = 0;

        for (ServerPlayer p : targets) {
            if (DownManager.isDown(p)) {
                DownManager.revive(p);
                n++;
            }
        }

        int count = n;
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> green("Revividos " + count + " jugador(es)."), true);
        return n;
    }

    private static int forceKill(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int n = 0;

        for (ServerPlayer p : targets) {
            if (DownManager.isDown(p)) {
                DownManager.forceDeath(p, p.damageSources().genericKill());
                n++;
            }
        }

        int count = n;
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> green("Eliminados " + count + " noqueado(s)."), true);
        return n;
    }

    private static int forceDown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int n = 0;

        for (ServerPlayer p : targets) {
            if (!DownManager.isDown(p) && p.isAlive() && !p.isCreative() && !p.isSpectator()) {
                DownManager.knockDown(p, p.damageSources().generic());
                n++;
            }
        }

        int count = n;
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> green("Noqueados " + count + " jugador(es)."), true);
        return n;
    }

    private static Component green(String s) {
        return Component.literal(s).withStyle(ChatFormatting.GREEN);
    }
}
