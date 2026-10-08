package net.tierrasfantasticas.tfclient.pad;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Una página del pad (la portada o una app). Todo en «píxeles de pad»: la zona de contenido va de
 * (X, Y) a (X + W, Y + H), justo debajo de la barra de arriba.
 */
abstract class PadPage {
    /** Empieza en y=84: más arriba, a la izquierda, el emblema TF del marco se mete en la pantalla. */
    static final int X = 60, Y = 84, W = 272, H = 110;

    final TFPadScreen pad;
    final String app;

    PadPage(TFPadScreen pad, String app) {
        this.pad = pad;
        this.app = app;
    }

    /** Lo que sale en la barra de arriba. */
    abstract String title();

    abstract void render(GuiGraphics g, double mx, double my, float partial);

    /** Para lo que se dibuja fuera de la escala del pad (descripciones de objetos): mx, my en coordenadas de pantalla. */
    void renderOver(GuiGraphics g, int mx, int my) {}

    boolean click(double mx, double my, int button) {
        return false;
    }

    boolean scroll(double mx, double my, double delta) {
        return false;
    }

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
