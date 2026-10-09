package net.tierrasfantasticas.tfclient.pad.server;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.jobs.TFJobs;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig.Job;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig.Mission;
import net.tierrasfantasticas.tfclient.jobs.TFJobsData.JobProgress;
import net.tierrasfantasticas.tfclient.jobs.TFJobsData.MissionState;
import net.tierrasfantasticas.tfclient.jobs.TFJobsData.PlayerJobs;
import net.tierrasfantasticas.tfclient.jobs.TFJobsMenu;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.server.TFServerConfig;

/**
 * Oficios dentro del pad: la lista de oficios con tu nivel y, al entrar en uno, sus pestañas MISIONES (con RECLAMAR),
 * CÓMO SE GANA (qué paga y cuánto con tu nivel) y PREMIOS (lo que te dan los próximos niveles). Abajo, unirse, cambiar
 * o dejar el oficio (cambiar y dejar piden un segundo clic). Pestañas: "" la lista; "o:&lt;id&gt;:m|a|r" un oficio.
 */
public final class PadJobs {
    public static final PadServer.App APP = new App();

    private static final int TEXT = 0x18265C, MUTED = 0x7E8CA8, GREEN = 0x1E9E46;

    private PadJobs() {}

    public static String tab(String jobId) {
        return "o:" + jobId + ":m";
    }

    private static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            PlayerJobs p = TFJobs.data().player(player.getUUID());
            if (tab.startsWith("o:")) {
                String[] parts = tab.split(":");
                Job job = parts.length > 1 ? TFJobsConfig.job(parts[1]) : null;
                if (job != null) return job(player, p, job, parts.length > 2 ? parts[2] : "m");
            }
            return list(player, p);
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            String[] a = action.split(":");
            PlayerJobs p = TFJobs.data().player(player.getUUID());
            Job job = a.length > 1 ? TFJobsConfig.job(a[1]) : null;
            switch (a[0]) {
                case "ver" -> {
                    return job == null ? "" : tab(job.id());
                }
                case "volver" -> {
                    return "";
                }
                case "cobrar" -> {
                    Mission m = job == null || a.length < 3 ? null : job.mission(a[2]);
                    if (m == null) return null;
                    if (!p.active.contains(job.id())) {
                        TFPadNet.notice(player, "Trabaja de " + job.name() + " para cobrar sus misiones.");
                        return null;
                    }
                    String error = TFJobs.claim(player, job, m);
                    TFPadNet.notice(player, error != null ? error : "Cobraste «" + m.name() + "»: " + TFJobs.rewardText(m.reward()) + ".");
                    TFPadNet.sendState(player);
                    return null;
                }
                case "unirme", "cambiar" -> {
                    if (job == null) return null;
                    if (a[0].equals("cambiar") && !PadServer.confirm(player, "oficio.cambiar")) return null;
                    String error = a[0].equals("cambiar") ? TFJobs.switchTo(player, job) : TFJobs.join(player, job);
                    TFPadNet.notice(player, error != null ? error : "Oficio: " + job.name() + ".");
                    return null;
                }
                case "dejar" -> {
                    if (job == null || !PadServer.confirm(player, "oficio.dejar")) return null;
                    TFJobs.leave(player, job.id(), false);
                    TFPadNet.notice(player, "Dejaste " + job.name() + ". Tu nivel y tus misiones se quedan guardados.");
                    TFPadNet.sendState(player);
                    return null;
                }
                default -> {
                    return null;
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static PadView list(ServerPlayer player, PlayerJobs p) {
        PadView.Builder b = PadView.of("oficios");
        if (!TFJobsConfig.enabled) return b.empty("Oficios desactivados.").build();
        Job active = p.active.isEmpty() ? null : TFJobsConfig.job(p.active.get(0));
        if (active != null) {
            double pending = TFJobs.pendingCoins(player.getUUID());
            b.header("Trabajas de " + active.name() + (pending >= 1 ? " · cobras " + TFEconomy.format(Math.round(Math.floor(pending)))
                    + " en el próximo pago." : " · cobras cada " + TFJobsConfig.paySeconds + " s."));
        } else {
            b.header("Elige un oficio.");
        }
        for (Job job : TFJobsConfig.jobs.values()) {
            JobProgress jp = p.jobs.get(job.id());
            boolean on = p.active.contains(job.id());
            float progress = -1;
            String badge = "NUEVO";
            if (jp != null) {
                boolean max = jp.level >= TFJobsConfig.maxLevel;
                progress = max ? 1 : (float) (jp.xp / TFJobsConfig.xpFor(jp.level));
                badge = max ? "NIVEL MÁX." : "NIVEL " + jp.level;
            }
            int ready = on && jp != null ? claimable(job, jp) : 0;
            if (ready > 0) badge = ready + (ready == 1 ? " PREMIO" : " PREMIOS");
            b.row(new PadView.Row(new ItemStack(TFJobsMenu.icon(job)), job.name() + (on ? " · tu oficio" : ""), job.color(),
                    List.of(job.description()), progress, badge, null, null).clickable("ver:" + job.id()).selected(on));
        }
        if (TFJobsConfig.jobs.isEmpty()) b.empty("Sin oficios.");
        return b.build();
    }

    private static int claimable(Job job, JobProgress jp) {
        int n = 0;
        for (Mission m : job.missions()) {
            MissionState ms = TFJobs.refresh(jp, m);
            if (ms.done && ms.claimedDay < 0) n++;
        }
        return n;
    }

    private static PadView job(ServerPlayer player, PlayerJobs p, Job job, String section) {
        JobProgress jp = p.job(job.id());
        boolean active = p.active.contains(job.id());
        String base = "o:" + job.id() + ":";
        int ready = active ? claimable(job, jp) : 0;
        PadView.Builder b = PadView.of("oficios").tab(base + "m", ready > 0 ? "MISIONES (" + ready + ")" : "MISIONES")
                .tab(base + "a", "CÓMO SE GANA").tab(base + "r", "PREMIOS").selected(base + section);
        boolean max = jp.level >= TFJobsConfig.maxLevel;
        b.header(job.name() + " · nivel " + jp.level + "/" + TFJobsConfig.maxLevel
                + (max ? " · máximo" : " · " + (int) jp.xp + "/" + TFJobsConfig.xpFor(jp.level) + " xp")
                + (active ? "" : " · no es tu oficio"));
        switch (section) {
            case "a" -> actions(b, job, jp);
            case "r" -> rewards(b, jp);
            default -> missions(b, job, jp, active);
        }
        b.footer(PadView.Btn.of("ATRÁS", "volver", PadView.BLUE));
        boolean full = !active && p.active.size() >= TFJobsConfig.maxJobs;
        if (active) {
            boolean sure = PadServer.confirming(player, "oficio.dejar");
            b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "DEJAR", "dejar:" + job.id(), PadView.RED));
        } else if (full) {
            boolean sure = PadServer.confirming(player, "oficio.cambiar");
            b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "CAMBIARME", "cambiar:" + job.id(), PadView.GOLD));
        } else {
            b.footer(PadView.Btn.of("TRABAJAR AQUÍ", "unirme:" + job.id(), PadView.GREEN));
        }
        return b.build();
    }

    private static void missions(PadView.Builder b, Job job, JobProgress jp, boolean active) {
        for (Mission m : job.missions()) {
            MissionState ms = TFJobs.refresh(jp, m);
            boolean locked = jp.level < m.level();
            boolean claimed = ms.claimedDay >= 0;
            boolean done = ms.done && !claimed;
            List<String> lines = new ArrayList<>();
            if (!m.description().isEmpty()) lines.add(m.description());
            String repeat = switch (m.repeat()) {
                case DAILY -> "cada día";
                case ALWAYS -> "se repite";
                case NEVER -> "una vez";
            };
            if (locked) lines.add("Se desbloquea en el nivel " + m.level() + ".");
            else if (claimed) lines.add(m.repeat() == TFJobsConfig.Repeat.DAILY ? "Cobrada hoy. Vuelve mañana." : "Hecha para siempre.");
            else lines.add(TFJobsMenu.goal(m).replaceFirst(": \\d+$", "") + ": " + Math.min(ms.progress, m.amount()) + "/" + m.amount() + " · " + repeat);
            String reward = TFJobs.rewardText(m.reward());
            long coins = Math.round(m.reward().coins());
            if (!reward.equals("—") && (coins <= 0 || reward.contains("·"))) lines.add("Premio: " + reward);
            PadView.Btn btn = null;
            if (done) btn = active ? PadView.Btn.of("COBRAR", "cobrar:" + job.id() + ":" + m.id(), PadView.GREEN) : PadView.Btn.off("COBRAR");
            else if (claimed) btn = PadView.Btn.off("HECHA");
            ItemStack icon = new ItemStack(locked ? Items.GRAY_DYE : TFJobsMenu.item(m.icon(), Items.PAPER));
            float progress = locked || claimed ? -1 : Math.min(1F, (float) ms.progress / Math.max(1, m.amount()));
            int color = locked || claimed ? MUTED : done ? GREEN : TEXT;
            b.row(new PadView.Row(icon, m.name(), color, lines, progress, coins > 0 ? "+" + PadShop.price(coins) : "", btn, null).selected(done && active));
        }
        if (job.missions().isEmpty()) b.empty("Sin misiones.");
    }

    private static void actions(PadView.Builder b, Job job, JobProgress jp) {
        double bonus = 1 + TFJobsConfig.coinBonusPerLevel * (jp.level - 1);
        if (TFJobsConfig.coinBonusPerLevel > 0 && jp.level > 1) {
            b.header("Con tu nivel cobras un " + Math.round((bonus - 1) * 100) + " % más.");
        }
        for (TFJobsConfig.Action a : job.actions()) {
            String target = a.target().raw();
            ItemStack icon = target.startsWith("#") || !target.contains(":") ? new ItemStack(TFJobsMenu.icon(job))
                    : new ItemStack(TFJobsMenu.item(target, TFJobsMenu.icon(job)));
            List<String> lines = new ArrayList<>();
            lines.add("+" + num(a.xp()) + " xp" + (a.coins() > 0 ? " · +" + num(a.coins() * bonus) + " " + TFServerConfig.currency() : ""));
            b.row(new PadView.Row(icon, TFJobsMenu.actionLabel(a), TEXT, lines, -1, "", null, null));
        }
        if (job.actions().isEmpty()) b.empty("Solo paga con misiones.");
    }

    private static void rewards(PadView.Builder b, JobProgress jp) {
        if (jp.level >= TFJobsConfig.maxLevel) {
            b.empty("Todas reclamadas.");
            return;
        }
        int to = Math.min(TFJobsConfig.maxLevel, jp.level + 8);
        for (int level = jp.level + 1; level <= to; level++) addLevel(b, level, level == jp.level + 1);
        // los hitos que quedan más lejos también se enseñan
        for (var e : TFJobsConfig.milestones.entrySet()) if (e.getKey() > to && e.getKey() <= TFJobsConfig.maxLevel) addLevel(b, e.getKey(), false);
    }

    private static void addLevel(PadView.Builder b, int level, boolean next) {
        long coins = TFJobsConfig.levelUpCoins(level);
        TFJobsConfig.Reward stone = TFJobsConfig.milestones.get(level);
        List<String> lines = new ArrayList<>();
        if (stone != null) {
            coins += Math.round(stone.coins());
            TFJobsConfig.Reward extra = new TFJobsConfig.Reward(0, stone.xp(), stone.items(), List.of());
            if (!extra.isEmpty()) lines.add("Además: " + TFJobs.rewardText(extra));
        }
        if (lines.isEmpty()) lines.add(next ? "El siguiente nivel." : "Al llegar a este nivel.");
        ItemStack icon = new ItemStack(stone != null ? Items.CHEST : Items.EXPERIENCE_BOTTLE);
        b.row(new PadView.Row(icon, "Nivel " + level + (stone != null ? " · hito" : ""), stone != null ? 0xC27A10 : TEXT, lines, -1,
                "+" + PadShop.price(coins), null, null).selected(next));
    }

    private static String num(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? Long.toString(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', ',');
    }
}
