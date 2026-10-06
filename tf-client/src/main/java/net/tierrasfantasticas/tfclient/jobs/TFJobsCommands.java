package net.tierrasfantasticas.tfclient.jobs;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig.Job;
import net.tierrasfantasticas.tfclient.jobs.TFJobsData.JobProgress;
import net.tierrasfantasticas.tfclient.jobs.TFJobsData.PlayerJobs;

/**
 * <pre>
 * /tf jobs                                abre el menú de oficios (también /tf oficios)
 * /tf jobs ver &lt;oficio&gt;                   abre un oficio
 * /tf jobs unirse &lt;oficio&gt; | abandonar     sin menú
 * /tf jobs recargar                       vuelve a leer config/tfclient-jobs.json (staff, nivel 3)
 * /tf jobs nivel &lt;jugador&gt; &lt;oficio&gt; &lt;n&gt;    pone el nivel (staff)
 * /tf jobs xp &lt;jugador&gt; &lt;oficio&gt; &lt;n&gt;       suma experiencia (staff)
 * /tf jobs reiniciar &lt;jugador&gt; [oficio]   borra el progreso (staff)
 * </pre>
 */
public final class TFJobsCommands {
    private static final SuggestionProvider<CommandSourceStack> JOBS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(TFJobsConfig.jobs.keySet(), builder);

    private TFJobsCommands() {}

    public static List<LiteralArgumentBuilder<CommandSourceStack>> commands() {
        return List.of(build("jobs"), build("oficios"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .executes(ctx -> {
                    TFJobsMenu.openMain(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("ver").then(Commands.argument("oficio", StringArgumentType.word()).suggests(JOBS)
                        .executes(ctx -> {
                            TFJobsMenu.openJob(ctx.getSource().getPlayerOrException(), job(ctx), 0);
                            return 1;
                        })))
                .then(Commands.literal("unirse").then(Commands.argument("oficio", StringArgumentType.word()).suggests(JOBS)
                        .executes(ctx -> {
                            String error = TFJobs.join(ctx.getSource().getPlayerOrException(), job(ctx));
                            if (error != null) ctx.getSource().sendFailure(Component.literal(error));
                            return error == null ? 1 : 0;
                        })))
                .then(Commands.literal("abandonar").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    PlayerJobs p = TFJobs.data().player(player.getUUID());
                    if (p.active.isEmpty()) {
                        ctx.getSource().sendFailure(Component.literal("No tienes ningún oficio."));
                        return 0;
                    }
                    for (String id : List.copyOf(p.active)) TFJobs.leave(player, id, true);
                    return 1;
                }))
                .then(Commands.literal("recargar").requires(s -> s.hasPermission(3)).executes(ctx -> {
                    List<String> warnings = TFJobsConfig.load();
                    ctx.getSource().sendSuccess(() -> Component.literal("Oficios recargados: " + TFJobsConfig.jobs.size() + " oficios"
                            + (warnings.isEmpty() ? "." : ", " + warnings.size() + " avisos (mira la consola).")).withStyle(ChatFormatting.GREEN), true);
                    for (String w : warnings) ctx.getSource().sendFailure(Component.literal(w));
                    return TFJobsConfig.jobs.size();
                }))
                .then(Commands.literal("nivel").requires(s -> s.hasPermission(3))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .then(Commands.argument("oficio", StringArgumentType.word()).suggests(JOBS)
                                        .then(Commands.argument("nivel", IntegerArgumentType.integer(1, 10_000)).executes(ctx -> {
                                            ServerPlayer player = EntityArgument.getPlayer(ctx, "jugador");
                                            Job job = job(ctx);
                                            JobProgress jp = TFJobs.data().player(player.getUUID()).job(job.id());
                                            jp.level = Math.min(TFJobsConfig.maxLevel, IntegerArgumentType.getInteger(ctx, "nivel"));
                                            jp.xp = 0;
                                            TFJobs.data().changed();
                                            ctx.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " ahora es nivel "
                                                    + jp.level + " de " + job.name()).withStyle(ChatFormatting.YELLOW), true);
                                            return jp.level;
                                        })))))
                .then(Commands.literal("xp").requires(s -> s.hasPermission(3))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .then(Commands.argument("oficio", StringArgumentType.word()).suggests(JOBS)
                                        .then(Commands.argument("cantidad", DoubleArgumentType.doubleArg(0, 1_000_000)).executes(ctx -> {
                                            ServerPlayer player = EntityArgument.getPlayer(ctx, "jugador");
                                            Job job = job(ctx);
                                            JobProgress jp = TFJobs.data().player(player.getUUID()).job(job.id());
                                            double amount = DoubleArgumentType.getDouble(ctx, "cantidad");
                                            TFJobs.giveXp(player, job, amount);
                                            ctx.getSource().sendSuccess(() -> Component.literal("+" + TFJobs.fmt(amount) + " xp de " + job.name() + " para "
                                                    + player.getGameProfile().getName() + " (nivel " + jp.level + ")").withStyle(ChatFormatting.YELLOW), true);
                                            return 1;
                                        })))))
                .then(Commands.literal("reiniciar").requires(s -> s.hasPermission(3))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .executes(ctx -> reset(ctx, null))
                                .then(Commands.argument("oficio", StringArgumentType.word()).suggests(JOBS)
                                        .executes(ctx -> reset(ctx, job(ctx))))));
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, Job job) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "jugador");
        PlayerJobs p = TFJobs.data().player(player.getUUID());
        if (job == null) {
            p.jobs.clear();
            p.active.clear();
            p.lastJoin = 0;
        } else {
            p.jobs.remove(job.id());
            p.active.remove(job.id());
        }
        TFJobs.data().changed();
        ctx.getSource().sendSuccess(() -> Component.literal("Progreso de " + (job == null ? "todos los oficios" : job.name()) + " de "
                + player.getGameProfile().getName() + " borrado.").withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static Job job(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "oficio");
        Job job = TFJobsConfig.job(id);
        if (job == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.literal("No existe el oficio «" + id + "». Opciones: " + String.join(", ", TFJobsConfig.jobs.keySet()))).create();
        }
        return job;
    }
}
