package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * Lo que una app del servidor manda al pad para dibujar: pestañas, unas líneas de cabecera, filas (icono de objeto,
 * título, hasta tres líneas, barra de progreso, un texto a la derecha y hasta dos botones), botones de abajo y, si
 * hace falta, un campo para escribir. El pad lo dibuja con su estilo (PadViewPage) y cada botón vuelve al servidor como
 * una acción (TFPadNet.Action). Así Misiones, Cazas, Kits, Viajes, Clanes… se programan solo en el servidor.
 */
public record PadView(String app, List<Tab> tabs, String tab, List<String> header, List<Row> rows, List<Btn> footer,
                      Input input, String empty) {

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

    /** progress < 0: sin barra. color: color del título (0xRRGGBB). */
    public record Row(ItemStack icon, String title, int color, List<String> lines, float progress, String badge,
                      Btn button, Btn button2) {}

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
        private final List<Row> rows = new ArrayList<>();
        private final List<Btn> footer = new ArrayList<>();
        private Input input;
        private String empty = "";

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

        public Builder row(ItemStack icon, String title, int color, List<String> lines, float progress, String badge, Btn button) {
            rows.add(new Row(icon, title, color, lines, progress, badge, button, null));
            return this;
        }

        public Builder row(Row row) {
            rows.add(row);
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
            return new PadView(app, tabs, tab, header, rows, footer, input, empty);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Red
    // ---------------------------------------------------------------------------------------------------------------

    private static final int MAX_ROWS = 60;

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
        int n = Math.min(rows.size(), MAX_ROWS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            buf.writeItem(r.icon == null ? ItemStack.EMPTY : r.icon);
            buf.writeUtf(cut(r.title, 120), 256);
            buf.writeInt(r.color);
            buf.writeVarInt(Math.min(r.lines.size(), 8));
            for (int j = 0; j < Math.min(r.lines.size(), 8); j++) buf.writeUtf(cut(r.lines.get(j), 300), 512);
            buf.writeFloat(r.progress);
            buf.writeUtf(cut(r.badge, 40), 64);
            writeBtn(buf, r.button);
            writeBtn(buf, r.button2);
        }
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
    }

    public static PadView read(FriendlyByteBuf buf) {
        String app = buf.readUtf(32);
        List<Tab> tabs = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) tabs.add(new Tab(buf.readUtf(64), buf.readUtf(48)));
        String tab = buf.readUtf(64);
        List<String> header = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) header.add(buf.readUtf(256));
        List<Row> rows = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) {
            ItemStack icon = buf.readItem();
            String title = buf.readUtf(256);
            int color = buf.readInt();
            List<String> lines = new ArrayList<>();
            for (int j = buf.readVarInt(); j > 0; j--) lines.add(buf.readUtf(512));
            float progress = buf.readFloat();
            String badge = buf.readUtf(64);
            rows.add(new Row(icon, title, color, lines, progress, badge, readBtn(buf), readBtn(buf)));
        }
        List<Btn> footer = new ArrayList<>();
        for (int i = buf.readVarInt(); i > 0; i--) footer.add(readBtn(buf));
        Input input = buf.readBoolean() ? new Input(buf.readUtf(64), buf.readUtf(128), buf.readVarInt(), buf.readUtf(32)) : null;
        String empty = buf.readUtf(256);
        return new PadView(app, tabs, tab, header, rows, footer, input, empty);
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
}
