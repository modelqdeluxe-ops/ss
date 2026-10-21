package net.tierrasfantasticas.tfclient.npcs.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.tierrasfantasticas.tfclient.npcs.NpcCatalog;
import net.tierrasfantasticas.tfclient.npcs.NpcNet;
import org.lwjgl.glfw.GLFW;

/**
 * (1.3.101) El panel de control de los NPC de escenas, a la izquierda de la pantalla y sin pausar el juego (los cambios
 * se ven en el mundo en el momento). Tres pestañas:
 * <ul>
 *   <li>Colocar: los packs y sus NPC (con el nombre de su pack); se ponen donde miras o a tus pies, mirándote.</li>
 *   <li>Editar: el NPC elegido (clic derecho con el panel, o el más cercano): moverlo respecto a como lo ves, subirlo,
 *   girarlo, llevarlo a donde miras, sus cosméticos de los menús de su pack, duplicarlo y borrarlo. Teclas: flechas,
 *   RePág/AvPág, Q/E (con Mayús, más).</li>
 *   <li>Puntos: los pins del NPC (los sitios entre los que anda) y las rutas de los caminantes.</li>
 * </ul>
 * Todo lo hace el servidor (npcs/TFNpcs) y contesta con su estado ({@link NpcNet.State}). Si la pestaña no cabe (una
 * pantalla pequeña o mucha escala de interfaz), se desplaza con la rueda (fuera de las listas, que tienen la suya): lo
 * que queda fuera de la zona visible no se dibuja ni se puede pulsar, y una barrita a la derecha dice dónde estás.
 */
public final class NpcPanelScreen extends Screen {
    private static final int W = 236;
    private static final int BG = 0xD0101418, LINE = 0xFF3A4350, GOLD = 0xFFE8C46A, TEXT = 0xFFE6E6E6, DIM = 0xFF9AA3AD, GREEN = 0xFF7BD88F,
            RED = 0xFFFF7A7A;
    private static final String[] TABS = {"Colocar", "Editar", "Puntos"};

    private static NpcNet.State state;
    private static int tab;
    private static String pack, mob;
    private static double step = 0.25;
    private static int scrollA, scrollB, scrollC;
    private static String notice = "";
    private static long noticeUntil;

    /** Cuánto está desplazada cada pestaña (en píxeles), si no cabe. */
    private static final int[] page = new int[TABS.length];

    private boolean confirmDelete;
    /** Zonas con rueda: x, y, ancho, alto y qué desplazan (0 = packs, 1 = NPC/cercanos, 2 = pins). */
    private final List<int[]> scrollZones = new ArrayList<>();
    /** Los botones de la pestaña (los que se desplazan) y la zona donde se ven. */
    private final List<Flat> content = new ArrayList<>();
    private int contentTop, contentBottom, maxPage;
    /** Dónde acaba lo de la pestaña (sin desplazar), para saber cuánto se puede desplazar. */
    private int contentEnd;

    public NpcPanelScreen() {
        super(Component.literal("Panel de NPC"));
    }

    /** Llega el estado del servidor: pone al día el panel (o lo abre si lo pide). */
    public static void receive(NpcNet.State s) {
        state = s;
        if (!s.notice().isEmpty()) {
            notice = s.notice();
            noticeUntil = System.currentTimeMillis() + 3500;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NpcPanelScreen sc) {
            if (s.open() && s.id() >= 0) tab = 1;
            sc.rebuild();
        } else if (s.open()) {
            if (s.id() >= 0) tab = 1;
            mc.setScreen(new NpcPanelScreen());
        }
    }

    private static void act(int op, String a, String b, double x, double y, double z) {
        NpcNet.toServer(new NpcNet.Act(op, state == null ? -1 : state.id(), a, b, x, y, z));
    }

    private static void act(int op) {
        act(op, "", "", 0, 0, 0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        rebuild();
    }

    // ------------------------------------------------------------------------------------------- botones

    /** Botón plano (sin la textura de Minecraft): fondo oscuro, más claro al pasar por encima, dorado si está elegido. */
    private static final class Flat extends Button {
        private final boolean on;
        private final int color;

        Flat(int x, int y, int w, int h, String text, boolean on, int color, Runnable press) {
            super(x, y, w, h, Component.literal(text), b -> press.run(), DEFAULT_NARRATION);
            this.on = on;
            this.color = color;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int bg = on ? 0xFF5A4A20 : isHoveredOrFocused() ? 0xFF33404D : 0xFF222A33;
            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, on ? 0xFFE8C46A : 0xFF3A4350);
            var font = Minecraft.getInstance().font;
            String s = getMessage().getString();
            while (font.width(s) > width - 4 && s.length() > 1) s = s.substring(0, s.length() - 1);
            g.drawString(font, s, getX() + (width - font.width(s)) / 2, getY() + (height - 8) / 2, active ? color : 0xFF666666, false);
        }
    }

    private Flat button(int x, int y, int w, String text, Runnable press) {
        return button(x, y, w, text, false, TEXT, press);
    }

    private Flat button(int x, int y, int w, String text, boolean on, int color, Runnable press) {
        Flat f = addRenderableWidget(new Flat(x, y, w, 14, text, on, color, press));
        content.add(f);
        return f;
    }

    /** Cuánto se baja lo de la pestaña (la de Colocar ya cabe: sus listas se ajustan al alto). */
    private int offset() {
        return tab == 0 ? 0 : page[tab];
    }

    void rebuild() {
        rebuild(true);
    }

    private void rebuild(boolean again) {
        clearWidgets();
        scrollZones.clear();
        int x0 = 8, y0 = 8;
        for (int i = 0; i < TABS.length; i++) {
            int t = i;
            button(x0 + 4 + i * 76, y0 + 20, 74, TABS[i], tab == i, tab == i ? GOLD : TEXT, () -> {
                tab = t;
                confirmDelete = false;
                rebuild();
            });
        }
        content.clear();
        contentTop = y0 + 37;
        contentBottom = height - 8 - 16;          // encima de la barra de avisos
        contentEnd = 0;
        maxPage = 0;
        if (state == null) return;
        int y = y0 + 40 - offset();
        if (NpcCatalog.packs().isEmpty()) return;
        switch (tab) {
            case 0 -> buildPlace(x0, y);
            case 1 -> buildEdit(x0, y);
            default -> buildPoints(x0, y);
        }
        for (Flat f : content) contentEnd = Math.max(contentEnd, f.getY() + f.getHeight() + offset());
        maxPage = tab == 0 ? 0 : Math.max(0, contentEnd + 4 - contentBottom);
        if (tab != 0 && page[tab] > maxPage) {
            page[tab] = maxPage;
            if (again) {
                rebuild(false);
                return;
            }
        }
        // lo que queda fuera de la zona visible, ni se ve ni se pulsa
        for (Flat f : content) f.visible = f.getY() >= contentTop && f.getY() + f.getHeight() <= contentBottom;
    }

    private int rows(int y) {
        return Math.max(4, (height - 8 - 60 - y) / 15);
    }

    private void buildPlace(int x0, int y) {
        List<NpcCatalog.Pack> packs = NpcCatalog.packs();
        if (pack == null || NpcCatalog.pack(pack) == null) pack = packs.get(0).id();
        int n = rows(y + 12);
        scrollA = Math.max(0, Math.min(scrollA, packs.size() - n));
        for (int i = 0; i < Math.min(n, packs.size() - scrollA); i++) {
            NpcCatalog.Pack p = packs.get(scrollA + i);
            button(x0 + 4, y + 12 + i * 15, 92, NpcCatalog.shortName(p), p.id().equals(pack), p.id().equals(pack) ? GOLD : TEXT, () -> {
                pack = p.id();
                mob = null;
                scrollB = 0;
                rebuild();
            });
        }
        scrollZones.add(new int[] {x0 + 4, y + 12, 92, n * 15, 0});
        NpcCatalog.Pack pk = NpcCatalog.pack(pack);
        List<NpcCatalog.Npc> npcs = pk.npcs();
        if (mob == null || pk.npc(mob) == null) mob = npcs.isEmpty() ? null : npcs.get(0).mob();
        scrollB = Math.max(0, Math.min(scrollB, npcs.size() - n));
        for (int i = 0; i < Math.min(n, npcs.size() - scrollB); i++) {
            NpcCatalog.Npc c = npcs.get(scrollB + i);
            boolean on = c.mob().equals(mob);
            String label = (c.marker() ? "· " : c.walks() ? "» " : "") + c.name();
            button(x0 + 100, y + 12 + i * 15, 132, label, on, on ? GOLD : c.marker() ? DIM : TEXT, () -> {
                mob = c.mob();
                rebuild();
            });
        }
        scrollZones.add(new int[] {x0 + 100, y + 12, 132, n * 15, 1});
        int by = y + 16 + n * 15;
        button(x0 + 4, by, 112, "Colocar donde miro", () -> act(NpcNet.PLACE, pack, mob, 0, 0, 0));
        button(x0 + 120, by, 112, "Colocar a mis pies", () -> act(NpcNet.PLACE, pack, mob, 1, 0, 0));
    }

    private void buildEdit(int x0, int y) {
        if (state.id() < 0) {
            // (1.3.106, captura del dueño: «Cerca de ti:» salía encima del botón) debajo del texto, que puede ocupar
            // dos líneas o más según la escala de la interfaz, el botón; luego «Cerca de ti:» y la lista
            int top = y + nearIntroHeight() + 4;
            button(x0 + 4, top, 112, "El más cercano", () -> act(NpcNet.SELECT_NEAR));
            int list = top + NEAR_LIST;
            int n = rows(list);
            scrollB = Math.max(0, Math.min(scrollB, state.near().size() - n));
            for (int i = 0; i < Math.min(n, state.near().size() - scrollB); i++) {
                NpcNet.Near nr = state.near().get(scrollB + i);
                button(x0 + 4, list + i * 15, 228, name(nr.pack(), nr.mob()) + String.format(Locale.ROOT, "  (%.0f m)", nr.dist()),
                        () -> NpcNet.toServer(new NpcNet.Act(NpcNet.SELECT, nr.id(), "", "", 0, 0, 0)));
            }
            scrollZones.add(new int[] {x0 + 4, list, 228, n * 15, 1});
            return;
        }
        int yy = y + 26;
        button(x0 + 4, yy, 74, "Más cercano", () -> act(NpcNet.SELECT_NEAR));
        button(x0 + 80, yy, 74, "Siguiente", () -> act(NpcNet.SELECT_NEAR, "", "", 1, 0, 0));
        button(x0 + 156, yy, 76, "Soltar", () -> act(NpcNet.DESELECT));
        yy += 20;
        double[] steps = {0.05, 0.25, 1};
        for (int i = 0; i < 3; i++) {
            double s = steps[i];
            button(x0 + 40 + i * 64, yy, 62, s == 1 ? "1 bloque" : String.valueOf(s), step == s, step == s ? GOLD : TEXT, () -> {
                step = s;
                rebuild();
            });
        }
        yy += 16;
        button(x0 + 4, yy, 74, "← Izquierda", () -> act(NpcNet.MOVE, "", "", -step, 0, 0));
        button(x0 + 80, yy, 74, "Lejos ↑", () -> act(NpcNet.MOVE, "", "", 0, 0, step));
        button(x0 + 156, yy, 76, "Derecha →", () -> act(NpcNet.MOVE, "", "", step, 0, 0));
        yy += 16;
        button(x0 + 4, yy, 74, "Subir", () -> act(NpcNet.MOVE, "", "", 0, step, 0));
        button(x0 + 80, yy, 74, "Cerca ↓", () -> act(NpcNet.MOVE, "", "", 0, 0, -step));
        button(x0 + 156, yy, 76, "Bajar", () -> act(NpcNet.MOVE, "", "", 0, -step, 0));
        yy += 20;
        button(x0 + 4, yy, 44, "⟲ 45°", () -> act(NpcNet.ROTATE, "", "", -45, 0, 0));
        button(x0 + 50, yy, 40, "⟲ 5°", () -> act(NpcNet.ROTATE, "", "", -5, 0, 0));
        button(x0 + 92, yy, 40, "5° ⟳", () -> act(NpcNet.ROTATE, "", "", 5, 0, 0));
        button(x0 + 134, yy, 44, "45° ⟳", () -> act(NpcNet.ROTATE, "", "", 45, 0, 0));
        button(x0 + 180, yy, 52, "Mirarme", () -> act(NpcNet.FACE_ME));
        yy += 16;
        button(x0 + 4, yy, 112, "A donde miro", () -> act(NpcNet.MOVE_TO, "", "", 0, 0, 0));
        button(x0 + 120, yy, 112, "A mis pies", () -> act(NpcNet.MOVE_TO, "", "", 1, 0, 0));
        yy += 20;
        NpcCatalog.Pack pk = NpcCatalog.pack(state.pack());
        NpcCatalog.Npc c = pk == null ? null : pk.npc(state.mob());
        if (c != null && !c.options().isEmpty()) {
            for (String g : NpcCatalog.GROUP_ORDER) {
                List<String> ids = c.options().get(g);
                if (ids == null || ids.isEmpty()) continue;
                String cur = state.chosen().get(g);
                int idx = cur == null ? -1 : ids.indexOf(cur);
                button(x0 + 92, yy, 20, "◀", () -> pick(pk, g, ids, idx < 0 ? ids.size() - 1 : idx - 1));
                NpcCatalog.Option o = idx >= 0 ? pk.option(g, ids.get(idx)) : null;
                button(x0 + 114, yy, 96, o == null ? "(el suyo)" : o.name(), o != null, o != null ? GOLD : DIM, () -> pick(pk, g, ids, idx + 1));
                button(x0 + 212, yy, 20, "▶", () -> pick(pk, g, ids, idx + 1));
                yy += 15;
            }
            if (!pk.options().getOrDefault("reset", List.of()).isEmpty()) {
                button(x0 + 92, yy, 140, "Quitar cosméticos", () -> act(NpcNet.RESET));
                yy += 15;
            }
            yy += 5;
        }
        button(x0 + 4, yy, 112, "Duplicar", () -> act(NpcNet.DUPLICATE));
        button(x0 + 120, yy, 112, confirmDelete ? "¿Seguro? Borrar" : "Borrar", confirmDelete, RED, () -> {
            if (confirmDelete) {
                confirmDelete = false;
                act(NpcNet.DELETE);
            } else {
                confirmDelete = true;
                rebuild();
            }
        });
        // debajo, la línea de las teclas (render): también cuenta para el desplazamiento
        contentEnd = Math.max(contentEnd, yy + 30 + offset());
    }

    /** El cosmético siguiente o anterior de ese grupo (dando la vuelta). */
    private static void pick(NpcCatalog.Pack pk, String g, List<String> ids, int i) {
        int k = Math.floorMod(i, ids.size());
        act(NpcNet.OPTION, g, ids.get(k), 0, 0, 0);
    }

    private void buildPoints(int x0, int y) {
        NpcCatalog.Pack pk = state.id() < 0 ? null : NpcCatalog.pack(state.pack());
        NpcCatalog.Npc c = pk == null ? null : pk.npc(state.mob());
        int yy = y + 12;
        if (c == null) yy += 12;
        if (c != null && !c.pins().isEmpty()) {
            yy += 12;
            int n = Math.max(3, Math.min(c.pins().size(), (height - 140 - yy - offset()) / 15));
            scrollC = Math.max(0, Math.min(scrollC, c.pins().size() - n));
            for (int i = 0; i < Math.min(n, c.pins().size() - scrollC); i++) {
                String pin = c.pins().get(scrollC + i);
                button(x0 + 150, yy + i * 15, 40, "Aquí", () -> act(NpcNet.PIN_SET, pin, "", 0, 0, 0));
                button(x0 + 192, yy + i * 15, 40, "Quitar", () -> act(NpcNet.PIN_CLEAR, pin, "", 0, 0, 0));
            }
            scrollZones.add(new int[] {x0 + 4, yy, 228, n * 15, 2});
            yy += n * 15 + 6;
        }
        List<Integer> routes = c != null && !c.routes().isEmpty() ? c.routes() : List.of(1, 2, 3, 4, 5, 6);
        yy += 12;
        for (int r : routes) {
            int rr = r;
            button(x0 + 70, yy, 40, "+ Aquí", () -> act(NpcNet.ROUTE_ADD, "", "", rr, 0, 0));
            button(x0 + 112, yy, 40, "Deshacer", () -> act(NpcNet.ROUTE_UNDO, "", "", rr, 0, 0));
            button(x0 + 154, yy, 36, "Borrar", () -> act(NpcNet.ROUTE_CLEAR, "", "", rr, 0, 0));
            Flat as = button(x0 + 192, yy, 40, "Asignar", () -> act(NpcNet.ROUTE_ASSIGN, "", "", rr, 0, 0));
            as.active = c != null && !c.routes().isEmpty();
            yy += 15;
        }
        // debajo, la explicación (render): también cuenta para el desplazamiento
        contentEnd = Math.max(contentEnd, yy + 6 + font.split(Component.literal(HELP), W - 12).size() * 9 + offset());
    }

    private static final String HELP = "Ponte donde va el punto y pulsa «Aquí». Con el panel en la mano se ven los pins "
            + "(verdes los de este NPC), las rutas y los marcadores.";

    // ------------------------------------------------------------------------------------------- dibujo

    private static String name(String pack, String mob) {
        NpcCatalog.Pack pk = NpcCatalog.pack(pack);
        NpcCatalog.Npc c = pk == null ? null : pk.npc(mob);
        return c == null ? mob : c.name() + " · " + NpcCatalog.shortName(pk);
    }

    /** Un texto de la pestaña, solo si cabe entero en la zona que se ve (como los botones: recortado asomaba medio renglón). */
    private void label(GuiGraphics g, String s, int x, int y, int color) {
        if (y >= contentTop && y + font.lineHeight <= contentBottom) g.drawString(font, s, x, y, color, false);
    }

    /** Lo mismo con un texto largo, partido en renglones (como drawWordWrap). */
    private static final String NEAR_INTRO = "Clic derecho a un NPC con el panel en la mano para elegirlo, o:";
    /** Desde el botón «El más cercano»: dónde va «Cerca de ti:» y dónde empieza la lista (el botón mide 14). */
    private static final int NEAR_LABEL = 18, NEAR_LIST = 30;

    /** Lo que ocupa el texto de arriba de la pestaña Editar sin NPC elegido (las líneas que salgan). */
    private int nearIntroHeight() {
        return font.split(Component.literal(NEAR_INTRO), W - 12).size() * font.lineHeight;
    }

    private void wrap(GuiGraphics g, String s, int x, int y, int color) {
        for (net.minecraft.util.FormattedCharSequence line : font.split(Component.literal(s), W - 12)) {
            if (y >= contentTop && y + font.lineHeight <= contentBottom) g.drawString(font, line, x, y, color, false);
            y += font.lineHeight;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int x0 = 8, y0 = 8, h = height - 16;
        g.fill(x0, y0, x0 + W, y0 + h, BG);
        g.fill(x0, y0, x0 + W, y0 + 1, LINE);
        g.fill(x0, y0 + h - 1, x0 + W, y0 + h, LINE);
        g.drawString(font, "Panel de NPC de escenas", x0 + 6, y0 + 6, GOLD, false);
        super.render(g, mx, my, pt);
        int y = y0 + 40 - offset();
        // los textos de la pestaña, recortados a la zona que se ve (si está desplazada)
        g.enableScissor(x0, contentTop, x0 + W, contentBottom);
        if (state == null) {
            label(g, "Cargando…", x0 + 6, y, DIM);
        } else if (NpcCatalog.packs().isEmpty()) {
            wrap(g, "Este TF Client no trae NPC de escenas.", x0 + 6, y, DIM);
        } else if (tab == 0) {
            label(g, "Packs", x0 + 6, y, DIM);
            label(g, "NPC   (» anda, · marcador)", x0 + 102, y, DIM);
        } else if (tab == 1) {
            if (state.id() < 0) {
                wrap(g, NEAR_INTRO, x0 + 6, y, DIM);
                // (debajo del botón «El más cercano»: buildEdit)
                label(g, "Cerca de ti:", x0 + 6, y + nearIntroHeight() + 4 + NEAR_LABEL, DIM);
            } else {
                label(g, name(state.pack(), state.mob()), x0 + 6, y, GOLD);
                label(g, String.format(Locale.ROOT, "%.2f  %.2f  %.2f   giro %.0f°", state.x(), state.y(), state.z(), state.yaw()),
                        x0 + 6, y + 12, TEXT);
                label(g, "Paso", x0 + 6, y + 50, DIM);
                NpcCatalog.Pack pk = NpcCatalog.pack(state.pack());
                NpcCatalog.Npc c = pk == null ? null : pk.npc(state.mob());
                int yy = y + 134;
                if (c != null) {
                    for (String gname : NpcCatalog.GROUP_ORDER) {
                        if (c.options().getOrDefault(gname, List.of()).isEmpty()) continue;
                        label(g, NpcCatalog.GROUPS.get(gname), x0 + 6, yy + 3, DIM);
                        yy += 15;
                    }
                    if (!c.options().isEmpty()) {
                        if (!pk.options().getOrDefault("reset", List.of()).isEmpty()) yy += 15;
                        yy += 5;
                    }
                }
                // debajo de Duplicar/Borrar (buildEdit: contentEnd)
                label(g, "Teclas: flechas, RePág/AvPág, Q/E", x0 + 6, yy + 19, DIM);
            }
        } else {
            NpcCatalog.Pack pk = state.id() < 0 ? null : NpcCatalog.pack(state.pack());
            NpcCatalog.Npc c = pk == null ? null : pk.npc(state.mob());
            int yy = y;
            if (c == null) {
                wrap(g, "Elige un NPC (pestaña Editar) para ver sus pins.", x0 + 6, yy, DIM);
                yy += 24;
            } else if (!c.pins().isEmpty()) {
                label(g, "Pins de " + c.name() + " (anda de uno a otro):", x0 + 6, yy, GOLD);
                yy += 24;
                int n = Math.max(3, Math.min(c.pins().size(), (height - 140 - yy - offset()) / 15));
                for (int i = 0; i < Math.min(n, c.pins().size() - scrollC); i++) {
                    String pin = c.pins().get(scrollC + i);
                    NpcNet.PinInfo info = null;
                    for (NpcNet.PinInfo pi : state.pins()) if (pi.name().equals(pin)) info = pi;
                    String st = info == null ? "sin poner" : info.here()
                            ? String.format(Locale.ROOT, "%.0f %.0f %.0f", info.x(), info.y(), info.z()) : "en otro mundo";
                    label(g, pin, x0 + 6, yy + i * 15 + 3, info == null ? RED : GREEN);
                    label(g, st, x0 + 84, yy + i * 15 + 3, DIM);
                }
                yy += n * 15 + 6;
            } else {
                label(g, c.name() + " no anda entre pins.", x0 + 6, yy, DIM);
                yy += 12;
            }
            label(g, "Rutas de caminantes", x0 + 6, yy, GOLD);
            yy += 12;
            List<Integer> routes = c != null && !c.routes().isEmpty() ? c.routes() : List.of(1, 2, 3, 4, 5, 6);
            for (int r : routes) {
                int k = r - 1 < state.routes().length ? state.routes()[r - 1] : 0;
                label(g, "Ruta " + r + ": " + k, x0 + 6, yy + 3, k > 0 ? GREEN : DIM);
                yy += 15;
            }
            wrap(g, HELP, x0 + 6, yy + 6, DIM);
        }
        g.disableScissor();
        if (maxPage > 0) {
            // la barrita: dónde estás de lo que hay (la rueda la mueve)
            int track = contentBottom - contentTop, thumb = Math.max(12, track * track / (track + maxPage));
            int ty = contentTop + (track - thumb) * page[tab] / maxPage;
            g.fill(x0 + W - 3, contentTop, x0 + W - 1, contentBottom, 0x40FFFFFF);
            g.fill(x0 + W - 3, ty, x0 + W - 1, ty + thumb, GOLD);
        }
        if (System.currentTimeMillis() < noticeUntil && !notice.isEmpty()) {
            g.fill(x0, y0 + h - 14, x0 + W, y0 + h, 0xE0202830);
            g.drawString(font, notice, x0 + 6, y0 + h - 11, GREEN, false);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // Sin oscurecer el mundo: el NPC se ve mientras se mueve
    }

    // ------------------------------------------------------------------------------------------- teclas y rueda

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        for (int[] z : scrollZones) {
            if (mx >= z[0] && mx < z[0] + z[2] && my >= z[1] && my < z[1] + z[3]) {
                int d = delta > 0 ? -1 : 1;
                if (z[4] == 0) scrollA += d;
                else if (z[4] == 1) scrollB += d;
                else scrollC += d;
                scrollA = Math.max(0, scrollA);
                scrollB = Math.max(0, scrollB);
                scrollC = Math.max(0, scrollC);
                rebuild();
                return true;
            }
        }
        // fuera de las listas: la pestaña entera, si no cabe
        if (maxPage > 0 && mx >= 8 && mx < 8 + W && my >= contentTop && my < contentBottom) {
            page[tab] = Math.max(0, Math.min(maxPage, page[tab] + (delta > 0 ? -15 : 15)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (tab == 1 && state != null && state.id() >= 0) {
            double s = (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? 1 : step;
            float rot = (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? 45 : 5;
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> act(NpcNet.MOVE, "", "", -s, 0, 0);
                case GLFW.GLFW_KEY_RIGHT -> act(NpcNet.MOVE, "", "", s, 0, 0);
                case GLFW.GLFW_KEY_UP -> act(NpcNet.MOVE, "", "", 0, 0, s);
                case GLFW.GLFW_KEY_DOWN -> act(NpcNet.MOVE, "", "", 0, 0, -s);
                case GLFW.GLFW_KEY_PAGE_UP -> act(NpcNet.MOVE, "", "", 0, s, 0);
                case GLFW.GLFW_KEY_PAGE_DOWN -> act(NpcNet.MOVE, "", "", 0, -s, 0);
                case GLFW.GLFW_KEY_Q -> act(NpcNet.ROTATE, "", "", -rot, 0, 0);
                case GLFW.GLFW_KEY_E -> act(NpcNet.ROTATE, "", "", rot, 0, 0);
                default -> {
                    return super.keyPressed(key, scan, mods);
                }
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        // Cada segundo, el estado otra vez (la posición de los que andan, los NPC de alrededor)
        if (minecraft != null && minecraft.player != null && minecraft.player.tickCount % 20 == 0) act(NpcNet.REFRESH);
    }
}
