package net.tierrasfantasticas.tfclient.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig.Job;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig.Mission;
import net.tierrasfantasticas.tfclient.jobs.TFJobsData.JobProgress;
import net.tierrasfantasticas.tfclient.jobs.TFJobsData.MissionState;
import net.tierrasfantasticas.tfclient.jobs.TFJobsData.PlayerJobs;
import net.tierrasfantasticas.tfclient.menu.TFIcon;
import net.tierrasfantasticas.tfclient.menu.TFPanelMenu;
import net.tierrasfantasticas.tfclient.server.TFServerConfig;

/**
 * Menús de /tf jobs en la ventana {@link TFPanelMenu}: arriba el marco del pack Medieval Jobs con el dibujo del oficio
 * y su rejilla de 5×2 (los oficios en el menú principal, las misiones en el de cada oficio); debajo, aparte, la barra
 * de 9 botones (volver, nivel, páginas, monedas, recompensas, unirse o abandonar, cerrar). Nada encima del dibujo y
 * sin el inventario del jugador.
 */
public final class TFJobsMenu {
    private static final int GRID = TFPanelMenu.GRID;
    private static final String SIGN = "Oficios";

    private TFJobsMenu() {}

    // --- Menú principal: elegir oficio ---

    public static void openMain(ServerPlayer player) {
        openMain(player, 0);
    }

    private static void openMain(ServerPlayer player, int page) {
        if (TFJobsConfig.jobs.isEmpty()) {
            player.sendSystemMessage(Component.literal("No hay oficios configurados.").withStyle(ChatFormatting.RED));
            return;
        }
        PlayerJobs p = TFJobs.data().player(player.getUUID());
        Job current = p.active.isEmpty() ? null : TFJobsConfig.job(p.active.get(0));
        Job shown = current != null ? current : TFJobsConfig.jobs.values().iterator().next();
        TFPanelMenu.open(player, shown.background(), current != null ? current.name() : "Elige tu oficio", SIGN,
                menu -> fillMain(menu, player, page));
    }

    private static void fillMain(TFPanelMenu menu, ServerPlayer player, int page) {
        menu.clear();
        PlayerJobs p = TFJobs.data().player(player.getUUID());
        List<Job> jobs = new ArrayList<>(TFJobsConfig.jobs.values());
        int pages = Math.max(1, (jobs.size() + GRID - 1) / GRID);
        int current = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < GRID; i++) {
            int index = current * GRID + i;
            if (index >= jobs.size()) break;
            Job job = jobs.get(index);
            menu.grid(i, jobIcon(job, p), (pl, t, b) -> openJob(pl, job, 0));
        }
        // Barra: info · página anterior · monedas · página siguiente · tu oficio · cerrar
        menu.nav(0, TFIcon.of(Items.BOOK).name("¿Cómo funcionan?", ChatFormatting.GOLD)
                .text("Elige un oficio y gana experiencia y " + TFServerConfig.currency() + " haciendo su trabajo.")
                .blank()
                .text("Sube de nivel para cobrar más y desbloquear misiones con recompensa.")
                .blank()
                .text(TFJobsConfig.maxJobs == 1 ? "Puedes tener un oficio a la vez." : "Puedes tener " + TFJobsConfig.maxJobs + " oficios a la vez.")
                .text("Si lo dejas, no pierdes tu nivel ni tus misiones.", ChatFormatting.GREEN).build(), null);
        menu.nav(4, coins(player), null);
        menu.nav(8, close(), (pl, t, b) -> pl.closeContainer());
        if (!p.active.isEmpty()) {
            Job active = TFJobsConfig.job(p.active.get(0));
            if (active != null) {
                JobProgress jp = p.job(active.id());
                menu.nav(7, TFIcon.of(icon(active)).name(Component.literal("Tu oficio: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(active.name()).withStyle(color(active))))
                        .line("Nivel " + jp.level, ChatFormatting.YELLOW)
                        .blank()
                        .line("Clic para ver tus misiones", ChatFormatting.GREEN).glow(true).build(),
                        (pl, t, b) -> openJob(pl, active, 0));
            }
        }
        if (current > 0) menu.nav(2, arrow("◀ Más oficios"), (pl, t, b) -> fillMain(menu, pl, current - 1));
        if (current < pages - 1) menu.nav(6, arrow("Más oficios ▶"), (pl, t, b) -> fillMain(menu, pl, current + 1));
        menu.update();
    }

    private static ItemStack jobIcon(Job job, PlayerJobs p) {
        boolean active = p.active.contains(job.id());
        JobProgress jp = p.jobs.get(job.id());
        TFIcon icon = TFIcon.of(icon(job)).name(Component.literal(job.name()).withStyle(color(job).withBold(true)));
        if (active) icon.line("✔ Tu oficio actual", ChatFormatting.GREEN);
        icon.text(job.description());
        icon.blank();
        if (jp != null) {
            icon.line(Component.literal("Nivel " + jp.level).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(jp.level >= TFJobsConfig.maxLevel ? "  (máximo)" : "  " + (int) jp.xp + "/" + TFJobsConfig.xpFor(jp.level) + " xp")
                            .withStyle(ChatFormatting.GRAY)));
            if (jp.level < TFJobsConfig.maxLevel) {
                icon.line(TFIcon.bar(jp.xp / TFJobsConfig.xpFor(jp.level), 20, ChatFormatting.GREEN));
            }
        } else {
            icon.line("Aún no has trabajado de esto", ChatFormatting.DARK_GRAY);
        }
        icon.line(job.missions().size() + " misiones", ChatFormatting.GRAY);
        icon.blank();
        icon.line("Clic para ver el oficio", ChatFormatting.YELLOW);
        return icon.glow(active).build();
    }

    // --- Menú de un oficio: nivel, misiones, recompensas y unirse / abandonar ---

    public static void openJob(ServerPlayer player, Job job, int page) {
        TFPanelMenu.open(player, job.background(), job.name(), SIGN, menu -> fillJob(menu, player, job, page, false));
    }

    private static void fillJob(TFPanelMenu menu, ServerPlayer player, Job job, int page, boolean confirmLeave) {
        menu.clear();
        PlayerJobs p = TFJobs.data().player(player.getUUID());
        JobProgress jp = p.job(job.id());
        boolean active = p.active.contains(job.id());

        List<Mission> missions = job.missions();
        int pages = Math.max(1, (missions.size() + GRID - 1) / GRID);
        int current = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < GRID; i++) {
            int index = current * GRID + i;
            if (index >= missions.size()) break;
            Mission mission = missions.get(index);
            MissionState ms = TFJobs.refresh(jp, mission);
            boolean claimable = active && ms.done && ms.claimedDay < 0;
            menu.grid(i, missionIcon(mission, ms, jp, active), claimable ? (pl, t, b) -> {
                String error = TFJobs.claim(pl, job, mission);
                if (error != null) pl.sendSystemMessage(Component.literal(error).withStyle(ChatFormatting.RED));
                fillJob(menu, pl, job, current, false);
            } : null);
        }

        // Barra: volver · nivel · misiones anteriores · cómo se gana · monedas · recompensas · más misiones ·
        // unirse/abandonar · cerrar
        menu.nav(0, TFIcon.of(Items.ARROW).name("◀ Volver a los oficios", ChatFormatting.YELLOW).build(), (pl, t, b) -> openMain(pl));
        menu.nav(1, levelIcon(job, jp), null);
        menu.nav(3, actionsIcon(job, jp), null);
        menu.nav(4, coins(player), null);
        menu.nav(5, rewardsIcon(jp), null);
        menu.nav(8, close(), (pl, t, b) -> pl.closeContainer());

        // Unirse, cambiar o abandonar
        if (active) {
            menu.nav(7, TFIcon.of(confirmLeave ? Items.TNT : Items.RED_DYE)
                    .name(confirmLeave ? "¿Seguro? Clic otra vez para dejarlo" : "Abandonar oficio", ChatFormatting.RED)
                    .text("Vuelves al menú de oficios. Tu nivel, tu experiencia y tus misiones se quedan guardados por si vuelves.")
                    .glow(confirmLeave).build(), (pl, t, b) -> {
                        if (!confirmLeave) {
                            fillJob(menu, pl, job, current, true);
                            return;
                        }
                        TFJobs.leave(pl, job.id(), true);
                        openMain(pl);
                    });
        } else {
            boolean other = !p.active.isEmpty() && p.active.size() >= TFJobsConfig.maxJobs;
            Job otherJob = other ? TFJobsConfig.job(p.active.get(0)) : null;
            TFIcon join = TFIcon.of(Items.LIME_DYE).name(other ? "Cambiar a " + job.name() : "Trabajar de " + job.name(), ChatFormatting.GREEN);
            if (other && otherJob != null) {
                join.text("Dejas " + otherJob.name() + " (sin perder su progreso) y empiezas de " + job.name() + ".");
            } else {
                join.text("Empieza a ganar experiencia y " + TFServerConfig.currency() + " con este oficio.");
            }
            if (TFJobsConfig.switchWaitMinutes > 0) {
                join.line("Después hay que esperar " + TFJobsConfig.switchWaitMinutes + " min para cambiar.", ChatFormatting.DARK_GRAY);
            }
            menu.nav(7, join.build(), (pl, t, b) -> {
                String error = other ? TFJobs.switchTo(pl, job) : TFJobs.join(pl, job);
                if (error != null) {
                    pl.sendSystemMessage(Component.literal(error).withStyle(ChatFormatting.RED));
                    fillJob(menu, pl, job, current, false);
                } else {
                    openJob(pl, job, current);
                }
            });
        }
        if (current > 0) menu.nav(2, arrow("◀ Misiones anteriores"), (pl, t, b) -> fillJob(menu, pl, job, current - 1, false));
        if (current < pages - 1) menu.nav(6, arrow("Más misiones ▶"), (pl, t, b) -> fillJob(menu, pl, job, current + 1, false));
        menu.update();
    }

    private static ItemStack missionIcon(Mission mission, MissionState ms, JobProgress jp, boolean active) {
        boolean locked = jp.level < mission.level();
        boolean claimed = ms.claimedDay >= 0;
        boolean claimable = ms.done && !claimed;
        ChatFormatting nameColor = locked ? ChatFormatting.DARK_GRAY : claimable ? ChatFormatting.GREEN : claimed ? ChatFormatting.GRAY : ChatFormatting.YELLOW;
        TFIcon icon = TFIcon.of(locked ? Items.GRAY_DYE : item(mission.icon(), Items.PAPER)).name(mission.name(), nameColor);
        if (!mission.description().isEmpty()) icon.text(mission.description());
        icon.line(goal(mission), ChatFormatting.GRAY);
        icon.blank();
        if (locked) {
            icon.line("Se desbloquea en el nivel " + mission.level(), ChatFormatting.RED);
        } else if (claimable) {
            icon.line("✔ ¡Completada!", ChatFormatting.GREEN);
            icon.line(active ? "Clic para cobrar la recompensa" : "Trabaja de este oficio para cobrarla", ChatFormatting.YELLOW);
        } else if (claimed) {
            icon.line(mission.repeat() == TFJobsConfig.Repeat.DAILY ? "Cobrada hoy. Vuelve mañana." : "✔ Hecha para siempre", ChatFormatting.GRAY);
        } else {
            icon.line(Component.literal(ms.progress + " / " + mission.amount() + "  ").withStyle(ChatFormatting.WHITE)
                    .append(TFIcon.bar((double) ms.progress / mission.amount(), 12, ChatFormatting.GREEN)));
            if (!active) icon.line("Trabaja de este oficio para avanzar", ChatFormatting.DARK_GRAY);
        }
        icon.blank();
        icon.line("Recompensa:", ChatFormatting.GOLD);
        for (String part : TFIcon.wrap(TFJobs.rewardText(mission.reward()), TFIcon.LINE)) icon.line(" " + part, ChatFormatting.YELLOW);
        icon.line(switch (mission.repeat()) {
            case DAILY -> "Se repite cada día";
            case ALWAYS -> "Se repite al cobrarla";
            case NEVER -> "Solo una vez";
        }, ChatFormatting.DARK_GRAY);
        return icon.glow(claimable).count(1).build();
    }

    /** «Cosechar 64 × trigo» */
    private static String goal(Mission m) {
        String verb = switch (m.type()) {
            case "romper" -> "Romper";
            case "cosechar" -> "Cosechar";
            case "colocar" -> "Colocar";
            case "matar" -> "Derrotar";
            case "pescar" -> "Pescar";
            case "fabricar" -> "Fabricar";
            case "fundir" -> "Fundir";
            case "preparar" -> "Preparar";
            case "encantar" -> "Encantar";
            case "reparar" -> "Reparar en el yunque";
            case "criar" -> "Criar";
            default -> m.type();
        };
        return verb + ": " + m.amount();
    }

    private static ItemStack levelIcon(Job job, JobProgress jp) {
        boolean max = jp.level >= TFJobsConfig.maxLevel;
        TFIcon icon = TFIcon.of(Items.EXPERIENCE_BOTTLE).count(jp.level)
                .name(Component.literal("Nivel " + jp.level).withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(" / " + TFJobsConfig.maxLevel).withStyle(ChatFormatting.GRAY)));
        if (max) {
            icon.line("¡Nivel máximo!", ChatFormatting.GREEN);
        } else {
            long need = TFJobsConfig.xpFor(jp.level);
            icon.line(TFIcon.bar(jp.xp / need, 20, ChatFormatting.GREEN));
            icon.line((int) jp.xp + " / " + need + " xp", ChatFormatting.WHITE);
            icon.line("Faltan " + (need - (long) jp.xp) + " xp para el nivel " + (jp.level + 1), ChatFormatting.GRAY);
        }
        icon.blank();
        int bonus = (int) Math.round(TFJobsConfig.coinBonusPerLevel * (jp.level - 1) * 100);
        icon.line("Bonus de " + TFServerConfig.currency() + ": +" + bonus + "%", ChatFormatting.YELLOW);
        return icon.build();
    }

    private static ItemStack actionsIcon(Job job, JobProgress jp) {
        TFIcon icon = TFIcon.of(Items.WRITABLE_BOOK).name("Cómo se gana", ChatFormatting.AQUA);
        double mult = 1 + TFJobsConfig.coinBonusPerLevel * (jp.level - 1);
        int shown = 0;
        for (TFJobsConfig.Action a : job.actions()) {
            if (shown++ >= 8) break;
            icon.line(Component.literal("• " + actionLabel(a)).withStyle(ChatFormatting.WHITE));
            icon.line(Component.literal("   +" + TFJobs.fmt(a.xp()) + " xp").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("  +" + TFJobs.fmt(a.coins() * mult) + " " + TFServerConfig.currency()).withStyle(ChatFormatting.GOLD)));
        }
        icon.blank();
        icon.text("Las " + TFServerConfig.currency() + " se pagan juntas cada " + TFJobsConfig.paySeconds + " segundos.", ChatFormatting.DARK_GRAY);
        return icon.build();
    }

    private static String actionLabel(TFJobsConfig.Action a) {
        String what = a.target().raw();
        String nice = switch (what) {
            case "*" -> "cualquier cosa";
            case "hostil" -> "monstruos";
            case "animal" -> "animales";
            default -> {
                String path = what.substring(what.indexOf(':') + 1).replace('/', ' ').replace('_', ' ');
                Item item = what.startsWith("#") ? null : ForgeRegistries.ITEMS.getValue(new ResourceLocation(what));
                yield item != null && item != Items.AIR ? new ItemStack(item).getHoverName().getString().toLowerCase() : path;
            }
        };
        String verb = switch (a.type()) {
            case "romper" -> "Romper";
            case "cosechar" -> "Cosechar";
            case "colocar" -> "Colocar";
            case "matar" -> "Derrotar";
            case "pescar" -> "Pescar";
            case "fabricar" -> "Fabricar";
            case "fundir" -> "Fundir";
            case "preparar" -> "Preparar";
            case "encantar" -> "Encantar";
            case "reparar" -> "Reparar";
            case "criar" -> "Criar";
            default -> a.type();
        };
        String text = verb + " " + nice;
        return text.length() > TFIcon.LINE ? text.substring(0, TFIcon.LINE - 1) + "…" : text;
    }

    private static ItemStack rewardsIcon(JobProgress jp) {
        TFIcon icon = TFIcon.of(Items.CHEST).name("Recompensas por nivel", ChatFormatting.GOLD);
        int from = jp.level + 1;
        int to = Math.min(TFJobsConfig.maxLevel, jp.level + 5);
        if (from > TFJobsConfig.maxLevel) {
            icon.line("Ya tienes todas las recompensas.", ChatFormatting.GREEN);
        }
        for (int level = from; level <= to; level++) {
            long coins = TFJobsConfig.levelUpCoins(level);
            TFJobsConfig.Reward stone = TFJobsConfig.milestones.get(level);
            String extra = "";
            if (stone != null) {
                coins += Math.round(stone.coins());
                List<String> items = stone.items().stream().map(TFJobs::itemLabel).toList();
                if (!items.isEmpty()) extra = " + " + String.join(", ", items);
            }
            icon.line(Component.literal("Nivel " + level + ": ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(TFEconomy.format(coins)).withStyle(ChatFormatting.WHITE)));
            if (!extra.isEmpty()) for (String part : TFIcon.wrap(extra.trim(), TFIcon.LINE)) icon.line("   " + part, ChatFormatting.AQUA);
        }
        // Próximo hito especial
        for (var e : TFJobsConfig.milestones.entrySet()) {
            if (e.getKey() > to) {
                icon.blank();
                icon.line("Gran premio en el nivel " + e.getKey() + ":", ChatFormatting.LIGHT_PURPLE);
                for (String part : TFIcon.wrap(TFJobs.rewardText(e.getValue()), TFIcon.LINE)) icon.line(" " + part, ChatFormatting.LIGHT_PURPLE);
                break;
            }
        }
        return icon.build();
    }

    private static ItemStack coins(ServerPlayer player) {
        OptionalLong balance = TFEconomy.balance(player.getServer(), player.getUUID());
        double pendingCoins = TFJobs.pendingCoins(player.getUUID());
        TFIcon icon = TFIcon.of(Items.SUNFLOWER).name("Tus " + TFServerConfig.currency(), ChatFormatting.GOLD);
        if (balance.isPresent()) icon.line(TFEconomy.format(balance.getAsLong()), ChatFormatting.YELLOW);
        if (pendingCoins >= 1) icon.line("+" + TFEconomy.number((long) pendingCoins) + " en el próximo pago", ChatFormatting.GRAY);
        icon.blank().line("Gástalas en /tf tienda", ChatFormatting.DARK_GRAY);
        return icon.build();
    }

    private static ItemStack close() {
        return TFIcon.of(Items.BARRIER).name("Cerrar", ChatFormatting.RED).build();
    }

    private static ItemStack arrow(String name) {
        return TFIcon.of(Items.ARROW).name(name, ChatFormatting.YELLOW).build();
    }

    private static Item icon(Job job) {
        String id = job.icon().contains(":") ? job.icon() : TFClient.MOD_ID + ":job_" + job.icon();
        return item(id, Items.BOOK);
    }

    private static Item item(String id, Item fallback) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return item == null || item == Items.AIR ? fallback : item;
    }

    private static Style color(Job job) {
        return Style.EMPTY.withColor(TextColor.fromRgb(job.color()));
    }
}
