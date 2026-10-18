package net.tierrasfantasticas.tfclient.vacio;

/** Para la prueba en el juego (BossTest): lo que se mira del cliente del Void. */
public final class VoidTestHooks {
    /** La prueba: dibujar nuestro cielo aunque haya paquete de shaders (para ver si el paquete lo respeta). */
    public static volatile boolean ownSkyWithShaders;

    private VoidTestHooks() {}

    /** ¿Se cargó el shader del cielo? (solo en el cliente del juego de la prueba) */
    public static boolean skyShaderLoaded() {
        return VoidSky.shader != null;
    }

    /** Shaders (Oculus): si hay paquete puesto y qué se hizo con el Void. */
    public static String shaders() {
        return (ShaderPacks.inUse() ? "paquete de shaders puesto" : "sin paquete de shaders") + " (" + ShaderPacks.status + ")";
    }
}
