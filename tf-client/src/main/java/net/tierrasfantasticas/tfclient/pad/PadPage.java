package net.tierrasfantasticas.tfclient.pad;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Una página del pad (la portada o una app). Todo en «unidades de pantalla» del cristal (las pone TFPadScreen según
 * el tamaño de la ventana): la zona de la página va de (X, Y) a (X + W, Y + H), bajo la barra de arriba; el cristal
 * entero mide SW x SH.
 */
abstract class PadPage {
    static int X = 12, Y = 22, W = 270, H = 106, SW = 288, SH = 132;
    /** Esquina del cristal en las coordenadas de la página (0,0 salvo en las páginas «a escala»: ver {@link #scaled}). */
    static int OX = 0, OY = 0;

    final TFPadScreen pad;
    final String app;

    PadPage(TFPadScreen pad, String app) {
        this.pad = pad;
        this.app = app;
    }

    /** Lo que sale en la barra de arriba. */
    abstract String title();

    /**
     * true: la página se dibuja a la escala grande del pad (TFPadScreen.bs, 1,5 a 1080p): mientras se dibuja o recibe
     * el ratón, X, Y, W, H (y SW, SH, OX, OY) están en «unidades de vista», con la zona de la app empezando en 0,0, y
     * todo sale 1,5 veces más grande y nítido. Las apps lo usan; la portada no (dibuja ella sus fichas grandes).
     */
    boolean scaled() {
        return true;
    }

    abstract void render(GuiGraphics g, double mx, double my, float partial);

    /** Para lo que se dibuja fuera de la escala del pad (descripciones de objetos): mx, my en coordenadas de pantalla. */
    void renderOver(GuiGraphics g, int mx, int my) {}

    boolean click(double mx, double my, int button) {
        return false;
    }

    boolean scroll(double mx, double my, double delta) {
        return false;
    }

    /** Arrastrar con el ratón (dy en unidades): para desplazar listas como en un móvil. */
    boolean drag(double mx, double my, double dy) {
        return false;
    }

    /** Se soltó el botón del ratón (para barras que se arrastran). */
    void release(double mx, double my, int button) {}

    /** true si la página se queda la tecla (por ejemplo, escribiendo en un campo). */
    boolean key(int key, int scan, int mods) {
        return false;
    }

    boolean chr(char c) {
        return false;
    }

    /** Si está escribiendo, la tecla del pad escribe en vez de cerrar. */
    boolean typing() {
        return false;
    }

    void tick() {}

    void closed() {}
}
