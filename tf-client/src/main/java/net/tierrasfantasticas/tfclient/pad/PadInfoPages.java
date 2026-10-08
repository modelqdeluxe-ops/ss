package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.tierrasfantasticas.tfclient.TFConfig;

/** Páginas del pad que se dibujan en el cliente: Monedero, Mi rango, Armario, Efectos y Ajustes. */
final class PadInfoPages {
    private PadInfoPages() {}

    static final java.text.DecimalFormat THOUSANDS = new java.text.DecimalFormat("#,##0",
            java.text.DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-ES")));

    static String web() {
        return TFConfig.webUrl().replaceAll("/+$", "");
    }

    /** Página con panel, icono grande a la izquierda, texto y una fila de botones abajo. */
    abstract static class Simple extends PadPage {
        record B(String label, int style, Runnable action) {}

        final List<B> buttons = new ArrayList<>();
        private final List<int[]> rects = new ArrayList<>();

        Simple(TFPadScreen pad, String app) {
            super(pad, app);
        }

        @Override
        String title() {
            return PadHomePage.APPS.stream().filter(a -> a.id().equals(app)).map(PadHomePage.App::name).findFirst().orElse(app);
        }

        abstract void body(GuiGraphics g, int x, int y, int w);

        @Override
        void render(GuiGraphics g, double mx, double my, float partial) {
            PadUi.panel(g, X, Y, W, H);
            pad.blit(g, "icon_" + app, X + 10, Y + 10);
            body(g, X + 52, Y + 10, W - 62);
            rects.clear();
            int bx = X + 10, by = Y + H - 24;
            for (B b : buttons) {
                int bw = PadUi.buttonWidth(b.label);
                PadUi.button(g, bx, by, bw, b.label, b.style, PadUi.inside(mx, my, bx, by, bw, 15), true);
                rects.add(new int[] {bx, by, bw});
                bx += bw + 5;
            }
        }

        @Override
        boolean click(double mx, double my, int button) {
            if (button != 0) return false;
            for (int i = 0; i < rects.size(); i++) {
                int[] r = rects.get(i);
                if (PadUi.inside(mx, my, r[0], r[1], r[2], 15)) {
                    pad.sound("select", 0.75F);
                    buttons.get(i).action.run();
                    return true;
                }
            }
            return false;
        }

        /** Texto grande (letra de Minecraft al doble) si cabe; si no, normal. */
        static void big(GuiGraphics g, String text, int x, int y, int w, int color) {
            if (PadUi.font().width(text) * 2 > w) {
                PadUi.text(g, text, x, y + 4, color);
                return;
            }
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(2, 2, 1);
            g.drawString(PadUi.font(), text, 0, 0, color, false);
            g.pose().popPose();
        }
    }

    static final class Monedero extends Simple {
        Monedero(TFPadScreen pad) {
            super(pad, "monedero");
            buttons.add(new B("OFICIOS", PadView.BLUE, () -> pad.openApp("oficios")));
            buttons.add(new B("MISIONES", PadView.BLUE, () -> pad.openApp("misiones")));
            buttons.add(new B("GTS", PadView.BLUE, () -> pad.openApp("gts")));
            buttons.add(new B("COMPRAR", PadView.GOLD, () -> pad.link(web() + "/tienda#tiendamonedas")));
        }

        @Override
        void body(GuiGraphics g, int x, int y, int w) {
            TFPadNet.State s = TFPadClient.state;
            PadUi.text(g, "Tu saldo", x, y, PadUi.MUTED);
            String amount = s == null ? "..." : s.balance() < 0 ? "—" : THOUSANDS.format(s.balance()) + " " + s.currency();
            big(g, amount, x, y + 11, w, 0xFFC27A10);
            PadUi.wrap(g, "Gana monedas con los oficios, las misiones, las cazas y vendiendo en la tienda o en el GTS.",
                    x, y + 32, w, PadUi.TEXT, 3);
        }
    }

    static final class Rango extends Simple {
        Rango(TFPadScreen pad) {
            super(pad, "rango");
            buttons.add(new B("VER RANGOS", PadView.GOLD, () -> pad.link(web() + "/tienda#rangos")));
        }

        @Override
        void body(GuiGraphics g, int x, int y, int w) {
            TFPadNet.State s = TFPadClient.state;
            PadUi.text(g, "Tu rango", x, y, PadUi.MUTED);
            if (s == null) big(g, "...", x, y + 11, w, PadUi.TEXT);
            else if (s.rank().isEmpty()) big(g, "Sin rango", x, y + 11, w, PadUi.MUTED);
            else big(g, s.rank(), x, y + 11, w, 0xFF000000 | readable(s.rankColor()));
            String homes = s != null && s.homes() >= 0 ? "Hogares: " + s.homes() + ". " : "";
            PadUi.wrap(g, homes + "Cada rango trae sus ventajas: míralas en la web.", x, y + 32, w, PadUi.TEXT, 3);
        }

        /** Oscurece los colores muy claros para que se lean sobre el panel blanco. */
        static int readable(int rgb) {
            int r = rgb >> 16 & 255, gr = rgb >> 8 & 255, b = rgb & 255;
            if (Math.max(r, Math.max(gr, b)) < 200) return rgb;
            return (r * 3 / 4) << 16 | (gr * 3 / 4) << 8 | (b * 3 / 4);
        }
    }

    /** Armario y Efectos: se eligen en la web (es la que sabe qué tiene comprado cada uno). */
    static final class Web extends Simple {
        private final String head, text;

        Web(TFPadScreen pad, String app) {
            super(pad, app);
            boolean wardrobe = app.equals("armario");
            head = wardrobe ? "Tu armario" : "Tus efectos";
            text = wardrobe ? "Elige lo que llevas puesto desde tu cuenta en la web. Se aplica al momento en el servidor."
                    : "Elige tu efecto de kill y tus habilidades en la web. Se aplica al momento en el servidor.";
            String url = web() + (wardrobe ? "/cuenta" : "/tienda#vfx");
            buttons.add(new B("ABRIR EN LA WEB", PadView.GOLD, () -> pad.link(url)));
        }

        @Override
        void body(GuiGraphics g, int x, int y, int w) {
            big(g, head, x, y + 2, w, PadUi.TEXT);
            PadUi.wrap(g, text, x, y + 26, w, PadUi.TEXT, 4);
        }
    }

    /** Ajustes del pad: sonidos, tic al pasar por encima, animaciones y volumen. */
    static final class Ajustes extends PadPage {
        private final List<int[]> rects = new ArrayList<>();

        Ajustes(TFPadScreen pad) {
            super(pad, "ajustes");
        }

        @Override
        String title() {
            return "AJUSTES";
        }

        @Override
        void render(GuiGraphics g, double mx, double my, float partial) {
            PadUi.panel(g, X, Y, W, H);
            rects.clear();
            row(g, 0, "Sonidos del pad", PadSettings.sounds ? "SÍ" : "NO", PadSettings.sounds, mx, my);
            row(g, 1, "Tic al pasar por encima", PadSettings.hoverTick ? "SÍ" : "NO", PadSettings.hoverTick, mx, my);
            row(g, 2, "Animaciones", PadSettings.animations ? "SÍ" : "NO", PadSettings.animations, mx, my);
            row(g, 3, "Volumen", PadSettings.volume + "%", true, mx, my);
            PadUi.text(g, "Tecla del pad: se cambia en Opciones › Controles.", X + 8, Y + H - 13, PadUi.MUTED);
        }

        private void row(GuiGraphics g, int i, String label, String value, boolean on, double mx, double my) {
            int y = Y + 6 + i * 22;
            PadUi.text(g, label, X + 10, y + 4, PadUi.TEXT);
            int bw = 46, bx = X + W - bw - 10;
            PadUi.button(g, bx, y, bw, value, on ? PadView.GREEN : PadView.RED, PadUi.inside(mx, my, bx, y, bw, 15), true);
            rects.add(new int[] {bx, y, bw});
            if (i < 3) g.fill(X + 6, y + 19, X + W - 6, y + 20, 0xFFC8E4F8);
        }

        @Override
        boolean click(double mx, double my, int button) {
            for (int i = 0; i < rects.size(); i++) {
                int[] r = rects.get(i);
                if (!PadUi.inside(mx, my, r[0], r[1], r[2], 15)) continue;
                switch (i) {
                    case 0 -> PadSettings.sounds = !PadSettings.sounds;
                    case 1 -> PadSettings.hoverTick = !PadSettings.hoverTick;
                    case 2 -> PadSettings.animations = !PadSettings.animations;
                    case 3 -> PadSettings.volume = PadSettings.volume >= 100 ? 20 : PadSettings.volume + 20;
                    default -> {
                    }
                }
                PadSettings.save();
                pad.sound("select", 0.75F);
                return true;
            }
            return false;
        }
    }
}
