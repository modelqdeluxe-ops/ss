package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * Lo que una app del servidor manda al pad para dibujar: pestañas, unas líneas de cabecera, una rejilla de objetos
 * (casillas con su icono y un texto corto debajo, como un inventario), filas (icono de objeto, título, líneas, barra de
 * progreso, un texto a la derecha y hasta dos botones; la fila entera se puede pulsar), botones de abajo y, si hace
 * falta, un campo para escribir. El pad lo dibuja con su estilo (PadViewPage) y cada botón vuelve al servidor como una
 * acción (TFPadNet.Action). Las acciones que empiezan por «§open:» las resuelve el propio pad (abren otra app).
 * <p>
 * hero: la cabecera grande de una ficha (el objeto al doble, su nombre, unas líneas, una barra de progreso y el precio o
 * el dato importante en una etiqueta), fija arriba, fuera de la lista que se desplaza. Puede ser null.
 */
public record PadView(String app, List<Tab> tabs, String tab, List<String> header, Row hero, List<Cell> cells, List<Row> rows,
                      List<Btn> footer, Input input, String empty, int cellStyle) {

    /** Casillas pequeñas (objeto y texto pixel debajo, como un inventario) o tarjetas (objeto grande, nombre y precio). */
    public static final int SMALL = 0, CARDS = 1;

    /** Estilos de botón. */
    public static final int BLUE = 0, GOLD = 1, RED = 2, GREEN = 3, GRAY = 4;

    public record Tab(String key, String label) {}

    public record Btn(String label, String action, int style, boolean enabled) {
        public static Btn of(String label, String action, int style) {
            return new Btn(label, action, style, true);
        }

        public static Btn off(String label) {
            return new Btn(label, "", GRAY, false);
        }
    }

    /**
     * progress < 0: sin barra. color: color del título (0xRRGGBB). click: acción al pulsar la fila entera ("" = no se
     * pulsa); selected: la fila sale resaltada.
     */
    public record Row(ItemStack icon, String title, int color, List<String> lines, float progress, String badge,
                      Btn button, Btn button2, String click, boolean selected) {
        public Row(ItemStack icon, String title, int color, List<String> lines, float progress, String badge, Btn button, Btn button2) {
            this(icon, title, color, lines, progress, badge, button, button2, "", false);
        }

        public Row clickable(String action) {
            return new Row(icon, title, color, lines, progress, badge, button, button2, action, selected);
        }

        public Row selected(boolean on) {
            return new Row(icon, title, color, lines, progress, badge, button, button2, click, on);
        }
    }

    /**
     * Una casilla de la rejilla: el objeto (con su cantidad), un texto corto debajo de color color (en las pequeñas, letra
     * pixel, por ejemplo un precio; en las tarjetas, el nombre), sub (en las tarjetas, la segunda línea en dorado, por
     * ejemplo el precio), la acción al pulsarla y si sale marcada. En las tarjetas la segunda línea va en una etiqueta:
     * tone 0 elige solo (oro si es un precio, «¤1.250», con su moneda; verde si empieza por «+»; gris si la tarjeta no
     * se puede pulsar; azul si es un estado) o la fuerza: TONE_GOLD, TONE_GREEN, TONE_GRAY, TONE_BLUE. Solo en las
     * tarjetas: progress (&lt; 0 sin barra) y badge (un aviso corto en una burbuja, por ejemplo cuántos premios hay).
     */
    public record Cell(ItemStack icon, String label, int color, String action, boolean selected, String sub, float progress, String badge,
                       int tone) {
        public Cell(ItemStack icon, String label, int color, String action, boolean selected) {
            this(icon, label, color, action, selected, "", -1, "", 0);
        }

        public Cell(ItemStack icon, String label, int color, String action, boolean selected, String sub) {
            this(icon, label, color, action, selected, sub, -1, "", 0);
        }
    }

    /** Tonos de las etiquetas (0: el que toque por el texto). */
    public static final int TONE_GOLD = 1, TONE_GREEN = 2, TONE_GRAY = 3, TONE_BLUE = 4;

    /**
     * Dinero para una etiqueta: «¤1.250» (el pad pone la moneda en lugar de «¤»); desde 100.000, corto: «¤250K»,
     * «¤1,5M». Para lo que se cobra, "+" + money(v).
     */
    public static String money(long v) {
        if (v < 100_000) return "¤" + java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("es-ES")).format(v);
        if (v < 1_000_000) return "¤" + (v / 1000) + "K";
        String m = String.format(java.util.Locale.ROOT, "%.1f", Math.floor(v / 100_000.0) / 10);
        if (m.endsWith(".0")) m = m.substring(0, m.length() - 2);
        return "¤" + m.replace('.', ',') + "M";
    }

    /** Campo de texto: al pulsar Enter (o el botón) se manda la acción con lo escrito. */
    public record Input(String action, String hint, int max, String button) {}

    // ---------------------------------------------------------------------------------------------------------------
    // Para montarla cómodo en el servidor
    // ---------------------------------------------------------------------------------------------------------------

    public static Builder of(String app) {
        return new Builder(app);
    }

    public static final class Builder {
        private final String app;
        private final List<Tab> tabs = new ArrayList<>();
        private String tab = "";
        private final List<String> header = new ArrayList<>();
        private Row hero;
        private final List<Cell> cells = new ArrayList<>();
        private final List<Row> rows = new ArrayList<>();
        private final List<Btn> footer = new ArrayList<>();
        private Input input;
        private String empty = "";
        private int cellStyle = SMALL;

        private Builder(String app) {
            this.app = app;
        }

        public Builder tab(String key, String label) {
            tabs.add(new Tab(key, label));
            return this;
        }

        public Builder selected(String key) {
            tab = key;
            return this;
        }

        public Builder header(String line) {
            header.add(line);
            return this;
        }

        /** La cabecera grande de una ficha (ver hero). */
        public Builder hero(Row row) {
            hero = row;
            return this;
        }

        public Builder row(ItemStack icon, String title, int color, List<String> lines, float progress, String badge, Btn button) {
            rows.add(new Row(icon, title, color, lines, progress, badge, button, null));
            return this;
        }

        public Builder row(Row row) {
            rows.add(row);
            return this;
        }

        public Builder cell(ItemStack icon, String label, int color, String action, boolean selected) {
            cells.add(new Cell(icon, label, color, action, selected));
            return this;
        }

        /** Una tarjeta (pide cards()): objeto grande, nombre y una segunda línea dorada. */
        public Builder card(ItemStack icon, String name, int color, String sub, String action, boolean selected) {
            cells.add(new Cell(icon, name, color, action, selected, sub == null ? "" : sub));
            cellStyle = CARDS;
            return this;
        }

        /** Una tarjeta con barra de progreso bajo la segunda línea y una burbuja de aviso (badge, "" sin ella). */
        public Builder card(ItemStack icon, String name, int color, String sub, float progress, String badge, String action, boolean selected) {
            return card(icon, name, color, sub, 0, progress, badge, action, selected);
        }

        /** Una tarjeta con el tono de su etiqueta elegido (TONE_…), barra (&lt; 0 sin ella) y burbuja. */
        public Builder card(ItemStack icon, String name, int color, String sub, int tone, float progress, String badge, String action,
                            boolean selected) {
            cells.add(new Cell(icon, name, color, action, selected, sub == null ? "" : sub, progress, badge == null ? "" : badge, tone));
            cellStyle = CARDS;
            return this;
        }

        public Builder cards() {
            cellStyle = CARDS;
            return this;
        }

        /** Fila solo de texto (para Ayuda): se parte a lo ancho. */
        public Builder text(String title, int color, List<String> lines) {
            rows.add(new Row(ItemStack.EMPTY, title, color, lines, -1, "", null, null));
            return this;
        }

        public Builder footer(Btn b) {
            footer.add(b);
            return this;
        }

        public Builder input(String action, String hint, int max, String button) {
            input = new Input(action, hint, max, button);
            return this;
        }

        public Builder empty(String text) {
            empty = text;
            return this;
        }

        public PadView build() {
            return new PadView(app, tabs, tab, header, hero, cells, rows, footer, input, empty, cellStyle);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Red
    // ---------------------------------------------------------------------------------------------------------------

    private static final int MAX_ROWS = 80;
    private static final int MAX_CELLS = 120;

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(app, 32);
        buf.writeVarInt(tabs.size());
        for (Tab t : tabs) {
            buf.writeUtf(t.key, 64);
            buf.writeUtf(cut(t.label, 24), 48);
        }
        buf.writeUtf(tab, 64);
        buf.writeVarInt(Math.min(header.size(), 4));
        for (int i = 0; i < Math.min(header.size(), 4); i++) buf.writeUtf(cut(header.get(i), 200), 256);
        buf.writeBoolean(hero != null);
        if (hero != null) writeRow(buf, hero);
        int c = Math.min(cells.size(), MAX_CELLS);
        buf.writeVarInt(c);
        for (int i = 0; i < c; i++) {
            Cell cell = cells.get(i);
            buf.writeItem(wire(cell.icon));
            buf.writeUtf(cut(cell.label, 40), 80);
            buf.writeInt(cell.color);
            buf.writeUtf(cut(cell.action, 120), 128);
            buf.writeBoolean(cell.selected);
            buf.writeUtf(cut(cell.sub, 40), 80);
            buf.writeFloat(cell.progress);
            buf.writeUtf(cut(cell.badge, 12), 24);
            buf.writeByte(cell.tone);
        }
        int n = Math.min(rows.size(), MAX_ROWS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) writeRow(buf, rows.get(i));
        buf.writeVarInt(Math.min(footer.size(), 4));
        for (int i = 0; i < Math.min(footer.size(), 4); i++) writeBtn(buf, footer.get(i));
        buf.writeBoolean(input != null);
        if (input != null) {
            buf.writeUtf(input.action, 64);
            buf.writeUtf(cut(input.hint, 60), 128);
            buf.writeVarInt(input.max);
            buf.writeUtf(cut(input.button, 20), 32);
        }
        buf.writeUtf(cut(empty, 200), 256);
        buf.writeByte(cellStyle);
    }

    public static PadView read(FriendlyByteBuf buf) {
        String app = buf.readUtf(32);
        List<Tab> tabs = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) tabs.add(new Tab(buf.readUtf(64), buf.readUtf(48)));
        String tab = buf.readUtf(64);
        List<String> header = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) header.add(buf.readUtf(256));
        Row hero = buf.readBoolean() ? readRow(buf) : null;
        List<Cell> cells = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) {
            cells.add(new Cell(buf.readItem(), buf.readUtf(80), buf.readInt(), buf.readUtf(128), buf.readBoolean(), buf.readUtf(80),
                    buf.readFloat(), buf.readUtf(24), buf.readByte()));
        }
        List<Row> rows = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) rows.add(readRow(buf));
        List<Btn> footer = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) footer.add(readBtn(buf));
        Input input = buf.readBoolean() ? new Input(buf.readUtf(64), buf.readUtf(128), buf.readVarInt(), buf.readUtf(32)) : null;
        String empty = buf.readUtf(256);
        return new PadView(app, tabs, tab, header, hero, cells, rows, footer, input, empty, buf.readByte());
    }

    private static void writeRow(FriendlyByteBuf buf, Row r) {
        buf.writeItem(wire(r.icon));
        buf.writeUtf(cut(r.title, 120), 256);
        buf.writeInt(r.color);
        buf.writeVarInt(Math.min(r.lines.size(), 8));
        for (int j = 0; j < Math.min(r.lines.size(), 8); j++) buf.writeUtf(cut(r.lines.get(j), 300), 512);
        buf.writeFloat(r.progress);
        buf.writeUtf(cut(r.badge, 40), 64);
        writeBtn(buf, r.button);
        writeBtn(buf, r.button2);
        buf.writeUtf(cut(r.click, 120), 128);
        buf.writeBoolean(r.selected);
    }

    private static Row readRow(FriendlyByteBuf buf) {
        ItemStack icon = buf.readItem();
        String title = buf.readUtf(256);
        int color = buf.readInt();
        List<String> lines = new ArrayList<>();
        for (int j = buf.readVarInt(); j > 0; j--) lines.add(buf.readUtf(512));
        float progress = buf.readFloat();
        String badge = buf.readUtf(64);
        Btn b1 = readBtn(buf), b2 = readBtn(buf);
        return new Row(icon, title, color, lines, progress, badge, b1, b2, buf.readUtf(128), buf.readBoolean());
    }

    private static void writeBtn(FriendlyByteBuf buf, Btn b) {
        buf.writeBoolean(b != null);
        if (b == null) return;
        buf.writeUtf(cut(b.label, 24), 48);
        buf.writeUtf(b.action, 128);
        buf.writeByte(b.style);
        buf.writeBoolean(b.enabled);
    }

    private static Btn readBtn(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        return new Btn(buf.readUtf(48), buf.readUtf(128), buf.readByte(), buf.readBoolean());
    }

    static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** La cantidad viaja en un byte: un montón de más de 127 (kits viejos) se manda con 127 para no salir negativo. */
    private static ItemStack wire(ItemStack s) {
        if (s == null || s.isEmpty()) return ItemStack.EMPTY;
        if (s.getCount() <= 127) return s;
        ItemStack c = s.copy();
        c.setCount(127);
        return c;
    }
}
