package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.tierrasfantasticas.tfclient.TFConfig;

/** Páginas del pad que se dibujan en el cliente (las demás apps las manda el servidor): Ajustes. */
final class PadInfoPages {
    private PadInfoPages() {}

    static final java.text.DecimalFormat THOUSANDS = new java.text.DecimalFormat("#,##0",
            java.text.DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-ES")));

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
                    case 3 -> PadSettings.volume = PadSettings.volume >= 100 ? 20 : Math.min(100, PadSettings.volume + 20);
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
