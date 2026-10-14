package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.trail.TrailPreview;
import net.tierrasfantasticas.tfclient.trail.TrailProfile;
import net.tierrasfantasticas.tfclient.trail.TrailSettings;
import net.tierrasfantasticas.tfclient.trail.VividColors;

/**
 * Efectos › COMBATE (1.3.67): los ajustes de las estelas de arma y de los colores vivos. Son de cada jugador y viven en
 * el cliente (config/tfclient/estelas.properties), así que esta pestaña no pasa por el servidor: PadViewPage la monta
 * aquí y sus acciones se hacen aquí. Arriba, la vista previa (una espada que da tajos con la estela de ahora); debajo,
 * todo en filas compactas: interruptores, − + y « ».
 */
final class PadCombat {
    static final String TAB = "combate";
    private static final int TEXT = 0x18265C;

    private PadCombat() {}

    /** La vista de Efectos con la pestaña COMBATE (si el servidor no la trae, por ser de otra versión). */
    static PadView withTab(PadView v) {
        for (PadView.Tab t : v.tabs()) if (t.key().equals(TAB)) return v;
        List<PadView.Tab> tabs = new ArrayList<>(v.tabs());
        tabs.add(new PadView.Tab(TAB, "COMBATE"));
        return new PadView(v.app(), tabs, v.tab(), v.header(), v.hero(), v.cells(), v.rows(), v.footer(), v.input(), v.empty(), v.cellStyle());
    }

    /** ¿Esta acción es de la pestaña COMBATE (se hace aquí y no en el servidor)? */
    static boolean local(PadView view, String action) {
        if (action.equals("tab:" + TAB)) return true;
        return view != null && view.tab().equals(TAB) && !action.startsWith("tab:");
    }

    /** Hace la acción y devuelve la vista nueva. */
    static PadView act(PadView view, String action) {
        TrailSettings.load();
        if (!action.startsWith("tab:")) {
            apply(action);
            TrailSettings.save();
        }
        return view(view == null ? List.of() : view.tabs());
    }

    private static void apply(String action) {
        String[] p = action.split(":");
        int dir = p.length > 2 && p[2].equals("-") ? -1 : 1;
        switch (p[0]) {
            case "on" -> TrailSettings.enabled = !TrailSettings.enabled;
            case "reset" -> TrailSettings.reset();
            case "t" -> {
                switch (p[1]) {
                    case "golpe" -> TrailSettings.swing = !TrailSettings.swing;
                    case "ataques" -> TrailSettings.attackOnly = !TrailSettings.attackOnly;
                    case "carga" -> TrailSettings.cooldown = !TrailSettings.cooldown;
                    case "sacar" -> TrailSettings.pullOut = !TrailSettings.pullOut;
                    case "otros" -> TrailSettings.others = !TrailSettings.others;
                    case "espadas" -> TrailSettings.swords = !TrailSettings.swords;
                    case "hachas" -> TrailSettings.axes = !TrailSettings.axes;
                    case "picos" -> TrailSettings.pickaxes = !TrailSettings.pickaxes;
                    case "palas" -> TrailSettings.shovels = !TrailSettings.shovels;
                    case "azadas" -> TrailSettings.hoes = !TrailSettings.hoes;
                    case "armas" -> TrailSettings.others_tools = !TrailSettings.others_tools;
                    case "vivos" -> TrailSettings.vivid = !TrailSettings.vivid;
                    case "detalle" -> TrailSettings.lowDetail = !TrailSettings.lowDetail;
                    case "destellos" -> TrailSettings.reducedFlash = !TrailSettings.reducedFlash;
                    default -> {
                    }
                }
            }
            case "n" -> {
                switch (p[1]) {
                    case "duracion" -> TrailSettings.length = clamp(TrailSettings.length + dir, 2, 20);
                    case "ancho" -> TrailSettings.width = clamp(TrailSettings.width + 10 * dir, 10, 100);
                    case "opacidad" -> TrailSettings.opacity = clamp(TrailSettings.opacity + 5 * dir, 10, 100);
                    case "minima" -> TrailSettings.minOpacity = clamp(TrailSettings.minOpacity + 10 * dir, 0, 60);
                    case "filo" -> TrailSettings.core = clamp(TrailSettings.core + 10 * dir, 0, 100);
                    case "distancia" -> TrailSettings.othersDistance = clamp(TrailSettings.othersDistance + 8 * dir, 0, 128);
                    case "vivos" -> TrailSettings.vividAmount = clamp(TrailSettings.vividAmount + 10 * dir, 10, 100);
                    default -> {
                    }
                }
            }
            case "c" -> {
                switch (p[1]) {
                    case "estilo" -> TrailSettings.profile = wrap(TrailSettings.profile + dir, TrailProfile.ALL.size());
                    case "mezcla" -> TrailSettings.blend = wrap(TrailSettings.blend + dir, TrailSettings.BLENDS.length);
                    case "cola" -> TrailSettings.fade = wrap(TrailSettings.fade + dir, TrailSettings.FADES.length);
                    case "color" -> {
                        // automático y luego los colores propios
                        int i = TrailSettings.autoColor ? -1 : TrailSettings.color;
                        i = wrap(i + 1 + dir, TrailSettings.COLORS.length + 1) - 1;
                        TrailSettings.autoColor = i < 0;
                        if (i >= 0) TrailSettings.color = i;
                    }
                    default -> {
                    }
                }
            }
            default -> {
            }
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int wrap(int v, int n) {
        return ((v % n) + n) % n;
    }

    // ---------------------------------------------------------------------------------------------------------------

    static PadView view(List<PadView.Tab> serverTabs) {
        TrailSettings.load();
        PadView.Builder b = PadView.of("efectos");
        if (serverTabs.isEmpty()) {
            b.tab("", "AL MATAR").tab("skills", "SKILLS").tab(TAB, "COMBATE");
        } else {
            for (PadView.Tab t : serverTabs) b.tab(t.key(), t.label());
            boolean has = serverTabs.stream().anyMatch(t -> t.key().equals(TAB));
            if (!has) b.tab(TAB, "COMBATE");
        }
        b.selected(TAB).compact();
        TrailProfile profile = TrailProfile.current();
        boolean on = TrailSettings.enabled;
        List<String> heroLines = new ArrayList<>();
        heroLines.add(on ? profile.name() + " · " + colorName() + " · " + TrailSettings.BLENDS[TrailSettings.blend].toLowerCase(Locale.ROOT)
                : "Apagadas: no se ve ninguna estela.");
        heroLines.add("Cámbialo abajo: se guarda solo.");
        b.hero(new PadView.Row(TrailPreview.weapon(), "Estelas de arma", TEXT, heroLines, -1, "",
                PadView.Btn.of(on ? "ACTIVADAS" : "APAGADAS", "on", on ? PadView.GREEN : PadView.RED), null).image(PadView.TRAIL_PREVIEW));

        b.row(section("ESTILO"));
        b.row(cycle(Items.NETHER_STAR, "Estilo", profile.description(), profile.name(), "c:estilo:-", "c:estilo:+"));
        b.row(cycle(Items.AMETHYST_SHARD, "Color", TrailSettings.autoColor ? "El de tu arma (su material y encantamientos)." : "Siempre este color.",
                colorName(), "c:color:-", "c:color:+"));
        b.row(cycle(Items.GLOWSTONE_DUST, "Mezcla", blendHelp(), TrailSettings.BLENDS[TrailSettings.blend], "c:mezcla:-", "c:mezcla:+"));
        b.row(stepper(Items.CLOCK, "Duración", "Cuánto tarda en apagarse.", seconds(TrailSettings.length), "n:duracion"));
        b.row(stepper(Items.IRON_SWORD, "Ancho", "Qué parte de la hoja cubre, desde la punta.", TrailSettings.width + " %", "n:ancho"));
        b.row(stepper(Items.GLASS_PANE, "Opacidad", "", TrailSettings.opacity + " %", "n:opacidad"));
        b.row(cycle(Items.FEATHER, "Cola", "Cómo se apaga el final.", TrailSettings.FADES[TrailSettings.fade], "c:cola:-", "c:cola:+"));
        b.row(stepper(Items.TINTED_GLASS, "Lo menos que se apaga", "0 %: la cola se va del todo.",
                TrailSettings.minOpacity == 0 ? "Nada" : TrailSettings.minOpacity + " %", "n:minima"));
        b.row(stepper(Items.END_ROD, "Filo brillante", "Una línea de luz por el borde.", TrailSettings.core == 0 ? "No" : TrailSettings.core + " %",
                "n:filo"));

        b.row(section("CUÁNDO"));
        b.row(toggle(Items.DIAMOND_SWORD, "Al golpear", "", TrailSettings.swing, "t:golpe"));
        b.row(toggle(Items.TARGET, "Solo ataques", TrailSettings.attackOnly ? "No al picar bloques ni al usar cosas." : "En cualquier golpe.",
                TrailSettings.attackOnly, "t:ataques"));
        b.row(toggle(Items.REDSTONE, "Según la carga", "Un golpe flojo deja una estela floja.", TrailSettings.cooldown, "t:carga"));
        b.row(toggle(Items.CHEST, "Al sacar el arma", "Al cambiar de casilla a un arma.", TrailSettings.pullOut, "t:sacar"));
        b.row(toggle(Items.PLAYER_HEAD, "Otros jugadores y mobs", "", TrailSettings.others, "t:otros"));
        if (TrailSettings.others) {
            b.row(stepper(Items.SPYGLASS, "Hasta qué distancia", "Las de los demás, desde tan lejos.",
                    TrailSettings.othersDistance == 0 ? "Sin límite" : TrailSettings.othersDistance + " bloques", "n:distancia"));
        }

        b.row(section("CON QUÉ"));
        b.row(toggle(Items.GOLDEN_SWORD, "Espadas", "", TrailSettings.swords, "t:espadas"));
        b.row(toggle(Items.IRON_AXE, "Hachas", "", TrailSettings.axes, "t:hachas"));
        b.row(toggle(Items.IRON_PICKAXE, "Picos", "", TrailSettings.pickaxes, "t:picos"));
        b.row(toggle(Items.IRON_SHOVEL, "Palas", "", TrailSettings.shovels, "t:palas"));
        b.row(toggle(Items.IRON_HOE, "Azadas", "", TrailSettings.hoes, "t:azadas"));
        b.row(toggle(Items.TRIDENT, "Otras armas", "Las de otros mods (lanzas, martillos...).", TrailSettings.others_tools, "t:armas"));

        b.row(section("COLORES VIVOS"));
        if (VividColors.available()) {
            b.row(toggle(Items.SUNFLOWER, "Colores vivos", "Todo el mundo con colores más intensos.", TrailSettings.vivid, "t:vivos"));
            if (TrailSettings.vivid) {
                b.row(stepper(Items.BLAZE_POWDER, "Cuánto", "", TrailSettings.vividAmount + " %", "n:vivos"));
            }
        } else {
            b.row(note("Con shaders (Oculus) no hace falta: los shaders ya ponen su propio color."));
        }

        b.row(section("RENDIMIENTO"));
        b.row(toggle(Items.COMPARATOR, "Poco detalle", "Menos puntos y sin suavizar: para PCs justos.", TrailSettings.lowDetail, "t:detalle"));
        b.row(toggle(Items.ENDER_EYE, "Menos destellos", "Más suave a la vista.", TrailSettings.reducedFlash, "t:destellos"));

        b.footer(PadView.Btn.of("RESTABLECER", "reset", PadView.GRAY));
        return b.build();
    }

    private static String colorName() {
        return TrailSettings.autoColor ? "Automático" : TrailSettings.COLOR_NAMES[clamp(TrailSettings.color, 0, TrailSettings.COLOR_NAMES.length - 1)];
    }

    private static String blendHelp() {
        return switch (TrailSettings.blend) {
            case 0 -> "Luz que suma: brilla, sobre todo de noche.";
            case 1 -> "Se ve el color tal cual, también de día.";
            default -> "Casi opaca, como pintada.";
        };
    }

    private static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.2f s", ticks / 20F).replace('.', ',');
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Filas (las mismas que el pad de administrador: el interruptor SÍ/NO se dibuja como interruptor)
    // ---------------------------------------------------------------------------------------------------------------

    private static PadView.Row toggle(net.minecraft.world.item.Item icon, String title, String help, boolean on, String action) {
        return new PadView.Row(new ItemStack(icon), title, on ? TEXT : 0x7E8CA8, help.isEmpty() ? List.of() : List.of(help), -1, "",
                PadView.Btn.of(on ? "SÍ" : "NO", action, on ? PadView.GREEN : PadView.RED), null).clickable(action);
    }

    private static PadView.Row stepper(net.minecraft.world.item.Item icon, String title, String help, String value, String key) {
        return new PadView.Row(new ItemStack(icon), title, TEXT, help.isEmpty() ? List.of() : List.of(help), -1, value,
                PadView.Btn.of("-", key + ":-", PadView.BLUE), PadView.Btn.of("+", key + ":+", PadView.BLUE));
    }

    private static PadView.Row cycle(net.minecraft.world.item.Item icon, String title, String help, String value, String prev, String next) {
        return new PadView.Row(new ItemStack(icon), title, TEXT, help.isEmpty() ? List.of() : List.of(help), -1, value,
                PadView.Btn.of("«", prev, PadView.BLUE), PadView.Btn.of("»", next, PadView.BLUE)).clickable(next);
    }

    private static PadView.Row section(String title) {
        return new PadView.Row(ItemStack.EMPTY, title, TEXT, List.of(), -1, "", null, null);
    }

    private static PadView.Row note(String text) {
        return new PadView.Row(ItemStack.EMPTY, "", TEXT, List.of(text), -1, "", null, null);
    }
}
