package net.tierrasfantasticas.tfclient.skins;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/**
 * Las imágenes de las skins (sin clases del cliente: ImageIO, vale en el servidor y en el juego). Comprueba que un PNG
 * es una skin de Minecraft (64x64, o 64x32 de las antiguas, que se pasa a 64x64 como hace el juego), la deja como la
 * dibuja el juego (el cuerpo sin transparencias, HttpTexture.processLegacySkin) y la vuelve a guardar en PNG; y su huella
 * (SHA-1 en hexadecimal), que es su nombre en el disco y en la red.
 */
public final class SkinImages {
    /** Lo más que pesa una skin ya guardada (y lo más que viaja en un paquete). */
    public static final int MAX_PNG = 16 * 1024;
    /** Lo más que se lee de un archivo o de una descarga antes de mirarlo (algunas vienen mal comprimidas). */
    public static final int MAX_INPUT = 96 * 1024;

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private SkinImages() {}

    /** Por qué no vale una imagen (el texto se le enseña al jugador). */
    public static final class Bad extends Exception {
        public Bad(String message) {
            super(message, null, false, false);
        }
    }

    /** Una skin lista: el PNG que se guarda, su huella y si parece de brazos finos (Alex). */
    public record Result(byte[] png, String hash, boolean slimGuess) {}

    /** Comprueba y normaliza un PNG. Lanza Bad con el motivo (en español) si no es una skin. */
    public static Result normalize(byte[] png) throws Bad {
        if (png == null || png.length < 33) throw new Bad("No es una imagen PNG.");
        if (png.length > MAX_INPUT) throw new Bad("La imagen pesa demasiado.");
        for (int i = 0; i < SIGNATURE.length; i++) if (png[i] != SIGNATURE[i]) throw new Bad("No es una imagen PNG.");
        // el tamaño, de la cabecera (IHDR), antes de descomprimir nada
        if (png[12] != 'I' || png[13] != 'H' || png[14] != 'D' || png[15] != 'R') throw new Bad("No es una imagen PNG.");
        int w = readInt(png, 16), h = readInt(png, 20);
        if (w != 64 || h != 64 && h != 32) throw new Bad("Una skin mide 64x64 (o 64x32); esta mide " + w + "x" + h + ".");
        BufferedImage src;
        try {
            src = ImageIO.read(new ByteArrayInputStream(png));
        } catch (Exception e) {
            throw new Bad("La imagen está dañada.");
        }
        if (src == null || src.getWidth() != 64 || src.getHeight() != h) throw new Bad("La imagen está dañada.");
        int[] px = new int[64 * 64];
        for (int y = 0; y < h; y++) for (int x = 0; x < 64; x++) px[y * 64 + x] = src.getRGB(x, y);
        boolean legacy = h == 32;
        boolean slim = !legacy && transparent(px, 54, 20, 56, 32) && transparent(px, 50, 16, 52, 20);
        if (legacy) legacy(px);
        setNoAlpha(px, 0, 0, 32, 16);
        if (legacy) notchHack(px, 32, 0, 64, 32);
        setNoAlpha(px, 0, 16, 64, 32);
        setNoAlpha(px, 16, 48, 48, 64);
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, 64, 64, px, 0, 64);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
        try {
            if (!ImageIO.write(out, "png", bytes)) throw new Bad("No se pudo guardar la skin.");
        } catch (Bad b) {
            throw b;
        } catch (Exception e) {
            throw new Bad("No se pudo guardar la skin.");
        }
        byte[] result = bytes.toByteArray();
        if (result.length > MAX_PNG) throw new Bad("La skin pesa demasiado (más de 16 KB).");
        return new Result(result, hash(result), slim);
    }

    /** SHA-1 en hexadecimal (40 letras). */
    public static String hash(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** ¿Es una huella bien formada? (lo que llega por la red se mira antes de usarlo como nombre de archivo). */
    public static boolean validHash(String hash) {
        return hash != null && hash.length() == 40 && hash.chars().allMatch(c -> c >= '0' && c <= '9' || c >= 'a' && c <= 'f');
    }

    private static int readInt(byte[] b, int at) {
        return (b[at] & 255) << 24 | (b[at + 1] & 255) << 16 | (b[at + 2] & 255) << 8 | b[at + 3] & 255;
    }

    private static boolean transparent(int[] px, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if ((px[y * 64 + x] >>> 24) != 0) return false;
        return true;
    }

    /** Las de 64x32: la pierna y el brazo izquierdos son el derecho en espejo (como HttpTexture). */
    private static void legacy(int[] px) {
        for (int i = 32 * 64; i < 64 * 64; i++) px[i] = 0;
        copy(px, 4, 16, 16, 32, 4, 4);
        copy(px, 8, 16, 16, 32, 4, 4);
        copy(px, 0, 20, 24, 32, 4, 12);
        copy(px, 4, 20, 16, 32, 4, 12);
        copy(px, 8, 20, 8, 32, 4, 12);
        copy(px, 12, 20, 16, 32, 4, 12);
        copy(px, 44, 16, -8, 32, 4, 4);
        copy(px, 48, 16, -8, 32, 4, 4);
        copy(px, 40, 20, 0, 32, 4, 12);
        copy(px, 44, 20, -8, 32, 4, 12);
        copy(px, 48, 20, -16, 32, 4, 12);
        copy(px, 52, 20, -8, 32, 4, 12);
    }

    /** NativeImage.copyRect(x, y, dx, dy, w, h, flipX = true, flipY = false). */
    private static void copy(int[] px, int x, int y, int dx, int dy, int w, int h) {
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                px[(y + dy + i) * 64 + x + dx + (w - 1 - j)] = px[(y + i) * 64 + x + j];
            }
        }
    }

    private static void setNoAlpha(int[] px, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) px[y * 64 + x] |= 0xFF000000;
    }

    /** Las antiguas con el sombrero entero opaco: el sombrero no se ve (como el juego). */
    private static void notchHack(int[] px, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) if ((px[y * 64 + x] >>> 24) < 128) return;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) px[y * 64 + x] &= 0xFFFFFF;
    }
}
