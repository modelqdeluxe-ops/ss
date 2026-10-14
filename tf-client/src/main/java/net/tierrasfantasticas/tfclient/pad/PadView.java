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

    /**
     * Casillas pequeñas (objeto y texto pixel debajo, como un inventario), tarjetas (objeto grande, nombre y precio) o
     * galería (1.4: tarjetas más grandes con la imagen en grande, para los efectos y las clases de skills).
     */
    public static final int SMALL = 0, CARDS = 1, GALLERY = 2;
    /**
     * 1.3.67: filas compactas (icono de 16 en una pastilla de 22, casi la mitad de alto), para las pantallas de ajustes
     * del pad de administrador: así cabe todo sin bajar. Va como un bit en cellStyle (ver {@link #style}, {@link #compact}).
     */
    public static final int COMPACT = 16;

    /** Casillas, tarjetas o galería (sin el bit de COMPACT). */
    public int style() {
        return cellStyle & 15;
    }

    /** ¿Filas compactas? */
    public boolean compact() {
        return (cellStyle & COMPACT) != 0;
    }

    /**
     * Una textura del pad para el campo image de filas y tarjetas: «fx/kill_fuego_infernal» →
     * «tfclient:textures/gui/pad/fx/kill_fuego_infernal.png». image puede ser cualquier ResourceLocation de una textura
     * (los iconos de las skills: «tfclient:textures/gui/skills/…»); si el cliente no la tiene, dibuja el objeto.
     */
    /** Imagen especial: el propio jugador en 3D, con la skin que lleva (la vista previa de la skin puesta del Armario). */
    public static final String SELF_MODEL = "tfclient:self_model";
    /** Imagen especial (1.3.67): la vista previa de las estelas de arma (Efectos › COMBATE), una espada dando tajos. */
    public static final String TRAIL_PREVIEW = "tfclient:trail_preview";

    public static String padImage(String name) {
        return "tfclient:textures/gui/pad/" + name + ".png";
    }

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
     * pulsa); selected: la fila sale resaltada. image (1.4): una textura que se dibuja en lugar del objeto ("" = el
     * objeto; ver {@link #padImage}); el objeto sigue yendo, por si el cliente no tiene esa textura.
     */
    public record Row(ItemStack icon, String title, int color, List<String> lines, float progress, String badge,
                      Btn button, Btn button2, String click, boolean selected, String image) {
        public Row(ItemStack icon, String title, int color, List<String> lines, float progress, String badge, Btn button, Btn button2) {
            this(icon, title, color, lines, progress, badge, button, button2, "", false, "");
        }

        public Row(ItemStack icon, String title, int color, List<String> lines, float progress, String badge, Btn button, Btn button2,
                   String click, boolean selected) {
            this(icon, title, color, lines, progress, badge, button, button2, click, selected, "");
        }

        public Row clickable(String action) {
            return new Row(icon, title, color, lines, progress, badge, button, button2, action, selected, image);
        }

        public Row selected(boolean on) {
            return new Row(icon, title, color, lines, progress, badge, button, button2, click, on, image);
        }

        /** La misma fila con una imagen en lugar del objeto (ver {@link #padImage}). */
        public Row image(String id) {
            return new Row(icon, title, color, lines, progress, badge, button, button2, click, selected, id == null ? "" : id);
        }

        public boolean hasImage() {
            return image != null && !image.isEmpty();
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
                       int tone, String image) {
        public Cell(ItemStack icon, String label, int color, String action, boolean selected) {
            this(icon, label, color, action, selected, "", -1, "", 0, "");
        }

        public Cell(ItemStack icon, String label, int color, String action, boolean selected, String sub) {
            this(icon, label, color, action, selected, sub, -1, "", 0, "");
        }

        public Cell(ItemStack icon, String label, int color, String action, boolean selected, String sub, float progress, String badge,
                    int tone) {
            this(icon, label, color, action, selected, sub, progress, badge, tone, "");
        }

        /** La misma tarjeta con una imagen en lugar del objeto (ver {@link #padImage}). */
        public Cell image(String id) {
            return new Cell(icon, label, color, action, selected, sub, progress, badge, tone, id == null ? "" : id);
        }

        public boolean hasImage() {
            return image != null && !image.isEmpty();
        }
    }

    /** Tonos de las etiquetas (0: el que toque por el texto). */
    public static final int TONE_GOLD = 1, TONE_GREEN = 2, TONE_GRAY = 3, TONE_BLUE = 4;

    /** Dinero exacto para una etiqueta: «¤1.299.999» (cabeceras de las fichas, monedero, admin: donde se paga o se fija). */
    public static String moneyExact(long v) {
        return "¤" + java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("es-ES")).format(v);
    }

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

    /**
     * Número corto (para debajo de una casilla o encima de un objeto): 950, 1,2K, 35K, 1,5M. Redondea hacia abajo
     * (1.899 → «1,8K»); para lo que falta, que no debe quedarse corto, {@link #shortUp}.
     */
    public static String shortNumber(long v) {
        if (v < 1000) return Long.toString(v);
        if (v < 10_000) return decimal(Math.floor(v / 100.0) / 10) + "K";
        if (v < 1_000_000) return (v / 1000) + "K";
        return decimal(Math.floor(v / 100_000.0) / 10) + "M";
    }

    /** Como shortNumber pero redondeando hacia arriba (1.801 → «1,9K»): para «Faltan…», que nunca diga de menos. */
    public static String shortUp(long v) {
        if (v < 1000) return Long.toString(v);
        if (v < 10_000) return decimal(Math.ceil(v / 100.0) / 10) + "K";
        if (v < 1_000_000) {
            long k = (v + 999) / 1000;
            return k < 1000 ? k + "K" : "1M";
        }
        return decimal(Math.ceil(v / 100_000.0) / 10) + "M";
    }

    private static String decimal(double d) {
        String s = String.format(java.util.Locale.ROOT, "%.1f", d);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s.replace('.', ',');
    }

    /** Un número entero con sus puntos de miles, como el dinero: «1.024». */
    public static String number(long v) {
        return java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("es-ES")).format(v);
    }

    /**
     * La cantidad que se dibuja encima de un objeto: la de verdad (un montón de la tienda puede ser de 1.024 aunque el
     * objeto se apile de 64 en 64). Al doble (big) cabe exacta hasta 9.999; al tamaño normal, hasta 999; más, corta.
     */
    public static String countLabel(long n, boolean big) {
        return n < (big ? 10_000 : 1000) ? number(n) : shortNumber(n);
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
        private boolean compact;

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
            if (cellStyle == SMALL) cellStyle = CARDS;
            return this;
        }

        /** Una tarjeta ya hecha (por ejemplo, con imagen: {@link Cell#image}). Sale como tarjeta si no se pidió otra cosa. */
        public Builder cell(Cell cell) {
            cells.add(cell);
            if (cellStyle == SMALL) cellStyle = CARDS;
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
            if (cellStyle == SMALL) cellStyle = CARDS;
            return this;
        }

        /** Filas compactas (ver {@link #COMPACT}). */
        public Builder compact() {
            compact = true;
            return this;
        }

        public Builder cards() {
            cellStyle = CARDS;
            return this;
        }

        /** Tarjetas grandes con la imagen en grande (1.4): efectos, clases de skills. */
        public Builder gallery() {
            cellStyle = GALLERY;
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
            return new PadView(app, tabs, tab, header, hero, cells, rows, footer, input, empty, cellStyle | (compact ? COMPACT : 0));
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
            writeIcon(buf, cell.icon);
            buf.writeUtf(cut(cell.label, 40), 80);
            buf.writeInt(cell.color);
            buf.writeUtf(cut(cell.action, 120), 128);
            buf.writeBoolean(cell.selected);
            buf.writeUtf(cut(cell.sub, 40), 80);
            buf.writeFloat(cell.progress);
            buf.writeUtf(cut(cell.badge, 12), 24);
            buf.writeByte(cell.tone);
            buf.writeUtf(cut(cell.image, 160), 256);
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
            cells.add(new Cell(readIcon(buf), buf.readUtf(80), buf.readInt(), buf.readUtf(128), buf.readBoolean(), buf.readUtf(80),
                    buf.readFloat(), buf.readUtf(24), buf.readByte(), buf.readUtf(256)));
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
        writeIcon(buf, r.icon);
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
        buf.writeUtf(cut(r.image, 160), 256);
    }

    private static Row readRow(FriendlyByteBuf buf) {
        ItemStack icon = readIcon(buf);
        String title = buf.readUtf(256);
        int color = buf.readInt();
        List<String> lines = new ArrayList<>();
        for (int j = buf.readVarInt(); j > 0; j--) lines.add(buf.readUtf(512));
        float progress = buf.readFloat();
        String badge = buf.readUtf(64);
        Btn b1 = readBtn(buf), b2 = readBtn(buf);
        String click = buf.readUtf(128);
        boolean selected = buf.readBoolean();
        return new Row(icon, title, color, lines, progress, badge, b1, b2, click, selected, buf.readUtf(256));
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

    /**
     * El objeto de una casilla o fila. La cantidad de un ItemStack viaja en un byte, así que el objeto va con 127 como
     * mucho (para no salir negativo) y detrás va la cantidad de verdad: la tienda manda 128 o 1.024 unidades (o un kit
     * viejo de 200) y el pad dibuja ese número, no 64 ni 127.
     */
    private static void writeIcon(FriendlyByteBuf buf, ItemStack s) {
        if (s == null || s.isEmpty()) {
            buf.writeItem(ItemStack.EMPTY);
            buf.writeVarInt(0);
            return;
        }
        ItemStack w = s;
        if (s.getCount() > 127) {
            w = s.copy();
            w.setCount(127);
        }
        buf.writeItem(w);
        buf.writeVarInt(s.getCount());
    }

    private static ItemStack readIcon(FriendlyByteBuf buf) {
        ItemStack s = buf.readItem();
        int count = buf.readVarInt();
        // solo se dibuja (nunca va a un inventario): puede llevar más de lo que se apila
        if (!s.isEmpty() && count > 0) s.setCount(count);
        return s;
    }
}
