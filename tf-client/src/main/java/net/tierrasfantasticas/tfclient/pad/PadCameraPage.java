package net.tierrasfantasticas.tfclient.pad;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La Cámara: tus fotos (de .minecraft/tfclient/fotos), una grande con flechas para pasar, y los botones: PUBLICAR (en
 * Comunidad, con un texto), BORRAR, CARPETA y MODO FOTO (para hacer una nueva).
 */
final class PadCameraPage extends PadPage {
    private static final int PW = 160, PH = 90;

    private record Tex(ResourceLocation loc) {}

    private List<Path> photos = new ArrayList<>();
    private int index;
    private final Map<Path, Tex> textures = new LinkedHashMap<>(8, 0.75F, true);
    private boolean captioning;
    private String caption = "";
    private long confirmDelete;
    private long lastScan;
    private final List<int[]> buttons = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();

    PadCameraPage(TFPadScreen pad) {
        super(pad, "camara");
        scan();
        if (PadCamera.lastPhoto != null) select(PadCamera.lastPhoto);
    }

    @Override
    String title() {
        return "CÁMARA";
    }

    private void scan() {
        Path keep = photos.isEmpty() ? null : photos.get(Math.min(index, photos.size() - 1));
        photos = PadCamera.photos();
        lastScan = System.currentTimeMillis();
        if (keep != null) select(keep);
        index = Math.max(0, Math.min(index, photos.size() - 1));
    }

    private void select(Path p) {
        int i = photos.indexOf(p);
        if (i >= 0) index = i;
    }

    @Override
    void tick() {
        // la foto recién hecha se guarda en segundo plano: se vuelve a mirar hasta que aparece
        if (PadCamera.lastPhoto != null && !photos.contains(PadCamera.lastPhoto) && System.currentTimeMillis() - lastScan > 400) {
            scan();
            select(PadCamera.lastPhoto);
        }
    }

    private ResourceLocation texture(Path p) {
        Tex t = textures.get(p);
        if (t != null) return t.loc;
        try (InputStream in = Files.newInputStream(p); NativeImage src = NativeImage.read(in)) {
            int sw = src.getWidth(), sh = src.getHeight();
            int cw = sw, ch = sw * 9 / 16;
            if (ch > sh) {
                ch = sh;
                cw = sh * 16 / 9;
            }
            NativeImage small = new NativeImage(320, 180, false);
            src.resizeSubRectTo((sw - cw) / 2, (sh - ch) / 2, cw, ch, small);
            ResourceLocation loc = new ResourceLocation(TFClient.MOD_ID, "fotos/" + Integer.toHexString(p.toString().hashCode()));
            Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(small));
            textures.put(p, new Tex(loc));
            while (textures.size() > 6) {
                Path oldest = textures.keySet().iterator().next();
                Minecraft.getInstance().getTextureManager().release(textures.remove(oldest).loc);
            }
            return loc;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        buttons.clear();
        actions.clear();
        PadUi.panel(g, X, Y, W, H);
        int px = X + 6, py = Y + 6;
        PadUi.box(g, px - 1, py - 1, PW + 2, PH + 2, PadUi.NAVY);
        g.fill(px, py, px + PW, py + PH, 0xFF1A2440);
        if (photos.isEmpty()) {
            PadUi.wrap(g, "Aún no tienes fotos. Pulsa MODO FOTO, busca un buen sitio y dispara con la C.", px + 10, py + 30, PW - 20, 0xFFE0ECFF, 4);
        } else {
            Path p = photos.get(index);
            ResourceLocation loc = texture(p);
            if (loc != null) g.blit(loc, px, py, PW, PH, 0, 0, 320, 180, 320, 180);
            if (photos.size() > 1) {
                arrow(g, px + 2, py + PH / 2 - 7, true, mx, my, () -> move(-1));
                arrow(g, px + PW - 12, py + PH / 2 - 7, false, mx, my, () -> move(1));
            }
        }
        // abajo del todo: modo foto o el campo del texto
        int by = Y + H - 18;
        if (captioning) {
            int fw = W - 12 - 54;
            PadUi.box(g, px - 1, by, fw, 15, PadUi.NAVY);
            PadUi.box(g, px, by + 1, fw - 2, 13, 0xFFFFFFFF);
            String shown = caption.isEmpty() ? "Escribe un texto (opcional)" : caption;
            var font = PadUi.font();
            while (font.width(shown) > fw - 12 && shown.length() > 1) shown = shown.substring(1);
            PadUi.text(g, shown, px + 4, by + 4, caption.isEmpty() ? 0xFF96AACC : PadUi.TEXT);
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = px + 4 + (caption.isEmpty() ? 0 : font.width(shown));
                g.fill(cx, by + 3, cx + 1, by + 12, PadUi.TEXT);
            }
            button(g, X + W - 6 - 50, by, 50, "ENVIAR", PadView.GREEN, mx, my, this::send);
        } else {
            button(g, px, by, PW, "MODO FOTO", PadView.GREEN, mx, my, PadCamera::start);
        }
        // columna derecha
        int cx = X + 172, cw = W - 178;
        if (!photos.isEmpty()) {
            Path p = photos.get(index);
            PadUi.text(g, "Foto " + (index + 1) + " de " + photos.size(), cx, Y + 7, PadUi.TEXT);
            long when = 0;
            try {
                when = Files.getLastModifiedTime(p).toMillis();
            } catch (Exception ignored) {
                // sin fecha
            }
            PadUi.text(g, new SimpleDateFormat("d MMM · HH:mm").format(new Date(when)), cx, Y + 17, PadUi.MUTED);
            String status = PadCommunityClient.uploadStatus;
            if (status != null) {
                PadUi.text(g, status, cx, Y + 32, PadUi.TEXT);
                PadUi.progress(g, cx, Y + 44, cw, PadCommunityClient.uploadProgress);
            } else if (captioning) {
                PadUi.wrap(g, "Se publicará en Comunidad para que la vean todos. Pulsa ENVIAR.", cx, Y + 32, cw, PadUi.MUTED, 4);
            } else {
                button(g, cx, Y + 32, cw, "PUBLICAR", PadView.GOLD, mx, my, () -> {
                    captioning = true;
                    caption = "";
                });
                boolean sure = System.currentTimeMillis() - confirmDelete < 3000;
                button(g, cx, Y + 51, cw, sure ? "¿SEGURO?" : "BORRAR", PadView.RED, mx, my, this::delete);
            }
        }
        button(g, cx, by, cw, "CARPETA", PadView.BLUE, mx, my, () -> {
            try {
                Files.createDirectories(PadCamera.dir());
            } catch (Exception ignored) {
                // se abre igual
            }
            Util.getPlatform().openFile(PadCamera.dir().toFile());
        });
    }

    private void arrow(GuiGraphics g, int x, int y, boolean left, double mx, double my, Runnable action) {
        boolean hover = PadUi.inside(mx, my, x, y, 10, 14);
        PadUi.box(g, x, y, 10, 14, PadUi.NAVY);
        PadUi.box(g, x + 1, y + 1, 8, 12, hover ? 0xFFF6B628 : 0xCC3496FA);
        int c = 0xFFFFFFFF;
        if (left) {
            g.fill(x + 3, y + 6, x + 4, y + 8, c);
            g.fill(x + 4, y + 5, x + 5, y + 9, c);
            g.fill(x + 5, y + 4, x + 6, y + 10, c);
        } else {
            g.fill(x + 6, y + 6, x + 7, y + 8, c);
            g.fill(x + 5, y + 5, x + 6, y + 9, c);
            g.fill(x + 4, y + 4, x + 5, y + 10, c);
        }
        if (hover) pad.hover("§arrow" + left);
        buttons.add(new int[] {x, y, 10, 14});
        actions.add(action);
    }

    private void button(GuiGraphics g, int x, int y, int w, String label, int style, double mx, double my, Runnable action) {
        boolean hover = PadUi.inside(mx, my, x, y, w, 15);
        if (hover) pad.hover("§" + label);
        PadUi.button(g, x, y, w, label, style, hover, true);
        buttons.add(new int[] {x, y, w, 15});
        actions.add(action);
    }

    private void move(int d) {
        if (photos.isEmpty()) return;
        index = (index + d + photos.size()) % photos.size();
        captioning = false;
        pad.sound("hover", 0.6F);
    }

    private void delete() {
        if (photos.isEmpty()) return;
        if (System.currentTimeMillis() - confirmDelete > 3000) {
            confirmDelete = System.currentTimeMillis();
            return;
        }
        confirmDelete = 0;
        Path p = photos.get(index);
        Tex t = textures.remove(p);
        if (t != null) Minecraft.getInstance().getTextureManager().release(t.loc);
        try {
            Files.deleteIfExists(p);
        } catch (Exception e) {
            pad.showNotice("No se pudo borrar la foto.");
        }
        scan();
    }

    private void send() {
        if (photos.isEmpty() || PadCommunityClient.uploadStatus != null) return;
        PadCommunityClient.publish(photos.get(index), caption.trim());
        captioning = false;
        caption = "";
    }

    @Override
    boolean click(double mx, double my, int button) {
        if (button != 0) return false;
        for (int i = 0; i < buttons.size(); i++) {
            int[] b = buttons.get(i);
            if (PadUi.inside(mx, my, b[0], b[1], b[2], b[3])) {
                pad.sound("select", 0.75F);
                actions.get(i).run();
                return true;
            }
        }
        return false;
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        move(delta < 0 ? 1 : -1);
        return true;
    }

    @Override
    boolean typing() {
        return captioning;
    }

    @Override
    boolean key(int key, int scan, int mods) {
        if (!captioning) {
            if (key == 262) {
                move(1);
                return true;
            }
            if (key == 263) {
                move(-1);
                return true;
            }
            return false;
        }
        if (key == 256) { // Esc: deja de escribir
            captioning = false;
            return true;
        }
        if (key == 259) {
            if (!caption.isEmpty()) caption = caption.substring(0, caption.length() - 1);
            return true;
        }
        if (key == 257 || key == 335) {
            send();
            return true;
        }
        return false;
    }

    @Override
    boolean chr(char c) {
        if (!captioning || c < ' ' || c == '§' || caption.length() >= 120) return false;
        caption += c;
        return true;
    }

    @Override
    void closed() {
        for (Tex t : textures.values()) Minecraft.getInstance().getTextureManager().release(t.loc);
        textures.clear();
    }
}
