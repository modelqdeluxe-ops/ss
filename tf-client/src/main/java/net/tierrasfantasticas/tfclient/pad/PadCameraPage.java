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
    /** La foto: 16:9 a la izquierda, lo más grande que quepa dejando sitio debajo para MODO FOTO (nunca se pisan). */
    private int pw() {
        int byHeight = (H - 30) * 16 / 9;
        return Math.max(128, Math.min((W - 24) * 3 / 5, byHeight)) / 16 * 16;
    }

    private int ph() {
        return pw() * 9 / 16;
    }

    /** Tamaño de las fotos en el pad (16:9). */
    static final int PREVIEW_W = 960, PREVIEW_H = 540;

    /** Con filtro lineal: al reducir o ampliar la foto se ve suave, no pixelada ni con dientes. */
    static DynamicTexture smooth(DynamicTexture tex) {
        tex.setFilter(true, false);
        return tex;
    }

    private record Tex(ResourceLocation loc) {}

    private List<Path> photos = new ArrayList<>();
    private int index;
    private final Map<Path, Tex> textures = new LinkedHashMap<>(8, 0.75F, true);
    private boolean captioning;
    private String caption = "";
    private long confirmDelete;
    private Path confirmPath;
    /** Fotos que no se pudieron abrir (no se reintentan en cada fotograma). */
    private final java.util.Set<Path> broken = new java.util.HashSet<>();
    private final java.util.Set<Path> decoding = new java.util.HashSet<>();
    /** Fotos que aún se están guardando: se vuelve a intentar abrirlas desde este momento. */
    private final java.util.Map<Path, Long> retryAt = new java.util.HashMap<>();
    private long lastScan;
    private boolean closed;
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
        // la recién hecha sale ya (aunque el archivo aún se esté guardando): su vista previa está en memoria
        if (PadCamera.lastPhoto != null && !photos.contains(PadCamera.lastPhoto) && System.currentTimeMillis() - PadCamera.flashAt < 15000) {
            photos.add(0, PadCamera.lastPhoto);
        }
        lastScan = System.currentTimeMillis();
        if (keep != null) select(keep);
        index = Math.max(0, Math.min(index, photos.size() - 1));
        confirmDelete = 0;
    }

    private void select(Path p) {
        int i = photos.indexOf(p);
        if (i >= 0) index = i;
    }

    @Override
    void tick() {
        // la foto recién hecha se guarda en segundo plano: se vuelve a mirar hasta que aparece
        if (PadCamera.lastPhoto != null && !photos.contains(PadCamera.lastPhoto) && System.currentTimeMillis() - lastScan > 400
                && System.currentTimeMillis() - PadCamera.flashAt < 6000) {
            scan();
            select(PadCamera.lastPhoto);
        }
    }

    /**
     * La foto a 960x540 (lo que ocupa en pantalla a 1080p, para que no se vea borrosa), dibujada con suavizado. Se lee
     * y se reduce en segundo plano: una captura 4K tarda en abrirse.
     */
    private ResourceLocation texture(Path p) {
        Tex t = textures.get(p);
        if (t != null) return t.loc;
        // la recién hecha: su vista previa ya está en memoria
        if (p.equals(PadCamera.lastPhoto) && PadCamera.preview != null) {
            ResourceLocation loc = new ResourceLocation(TFClient.MOD_ID, "fotos/" + Integer.toHexString(p.toString().hashCode()));
            Minecraft.getInstance().getTextureManager().register(loc, smooth(new DynamicTexture(PadCamera.preview)));
            PadCamera.preview = null;
            textures.put(p, new Tex(loc));
            return loc;
        }
        Long retry = retryAt.get(p);
        if (retry != null && System.currentTimeMillis() < retry) return null;
        if (closed || broken.contains(p) || !decoding.add(p)) return null;
        Util.backgroundExecutor().execute(() -> {
            NativeImage small = null;
            try (InputStream in = Files.newInputStream(p); NativeImage src = NativeImage.read(in)) {
                int sw = src.getWidth(), sh = src.getHeight();
                int cw = sw, ch = sw * 9 / 16;
                if (ch > sh) {
                    ch = sh;
                    cw = sh * 16 / 9;
                }
                small = new NativeImage(PREVIEW_W, PREVIEW_H, false);
                src.resizeSubRectTo((sw - cw) / 2, (sh - ch) / 2, cw, ch, small);
            } catch (Exception e) {
                if (small != null) small.close();
                small = null;
            }
            NativeImage done = small;
            Minecraft.getInstance().execute(() -> {
                decoding.remove(p);
                if (done == null) {
                    // si se acaba de hacer, puede que aún se esté guardando: otro intento en medio segundo
                    long age = Long.MAX_VALUE;
                    try {
                        age = System.currentTimeMillis() - Files.getLastModifiedTime(p).toMillis();
                    } catch (Exception ignored) {
                        // sin fecha: rota
                    }
                    if (age < 8000) retryAt.put(p, System.currentTimeMillis() + 500);
                    else broken.add(p);
                    return;
                }
                retryAt.remove(p);
                if (closed) {
                    done.close();
                    return;
                }
                ResourceLocation loc = new ResourceLocation(TFClient.MOD_ID, "fotos/" + Integer.toHexString(p.toString().hashCode()));
                Minecraft.getInstance().getTextureManager().register(loc, smooth(new DynamicTexture(done)));
                textures.put(p, new Tex(loc));
                while (textures.size() > 6) {
                    Path oldest = textures.keySet().iterator().next();
                    Minecraft.getInstance().getTextureManager().release(textures.remove(oldest).loc);
                }
            });
        });
        return null;
    }

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        buttons.clear();
        actions.clear();
        PadCommunityClient.checkUpload();
        PadUi.panel(g, X, Y, W, H);
        int PW = pw(), PH = ph();
        int px = X + 6, py = Y + 5;
        PadUi.box(g, px - 2, py - 2, PW + 4, PH + 4, PadUi.INK);
        PadUi.box(g, px - 1, py - 1, PW + 2, PH + 2, PadUi.GOLD_LO);
        g.fill(px, py, px + PW, py + PH, 0xFF1A2440);
        if (photos.isEmpty()) {
            PadUi.wrap(g, "Sin fotos. MODO FOTO y clic izquierdo.", px + 10, py + PH / 2 - 10, PW - 20, 0xFFE0ECFF, 3);
        } else {
            Path p = photos.get(index);
            ResourceLocation loc = texture(p);
            if (loc != null) {
                g.blit(loc, px, py, PW, PH, 0, 0, PREVIEW_W, PREVIEW_H, PREVIEW_W, PREVIEW_H);
            } else if (broken.contains(p)) {
                PadFont.drawCentered(g, "NO SE PUEDE ABRIR", px + PW / 2, py + PH / 2 - 3, 0xE0ECFF, false);
            } else {
                PadUi.spinner(g, px + PW / 2, py + PH / 2 - 4);
            }
            if (photos.size() > 1) {
                arrow(g, px + 2, py + PH / 2 - 7, true, mx, my, () -> move(-1));
                arrow(g, px + PW - 12, py + PH / 2 - 7, false, mx, my, () -> move(1));
            }
        }
        // abajo: MODO FOTO bajo la foto, o el campo del texto a lo ancho
        int by = py + PH + 6;
        int cx = px + PW + 8, cw = X + W - 6 - cx;
        if (captioning) {
            int sendW = PadUi.buttonWidth("ENVIAR");
            PadUi.field(g, px - 1, by, X + W - 6 - sendW - 4 - (px - 1), caption, "Escribe un texto (opcional)");
            button(g, X + W - 6 - sendW, by, sendW, "ENVIAR", PadView.GREEN, mx, my, this::send);
        } else {
            button(g, px - 1, by, PW + 2, "MODO FOTO", PadView.GREEN, mx, my, PadCamera::start);
            button(g, cx, by, cw, "CARPETA", PadView.BLUE, mx, my, () -> {
                try {
                    Files.createDirectories(PadCamera.dir());
                } catch (Exception ignored) {
                    // se abre igual
                }
                Util.getPlatform().openFile(PadCamera.dir().toFile());
            });
        }
        // columna derecha
        if (!photos.isEmpty()) {
            Path p = photos.get(index);
            PadUi.text(g, "Foto " + (index + 1) + " de " + photos.size(), cx, py + 1, PadUi.GOLD_TEXT);
            long when = 0;
            try {
                when = Files.getLastModifiedTime(p).toMillis();
            } catch (Exception ignored) {
                // sin fecha
            }
            PadUi.text(g, new SimpleDateFormat("d MMM · HH:mm").format(new Date(when)), cx, py + 11, PadUi.MUTED);
            String status = PadCommunityClient.uploadStatus;
            if (status != null) {
                int n = PadUi.wrap(g, status, cx, py + 26, cw, PadUi.TEXT, 2);
                PadUi.progress(g, cx, py + 28 + n * 10, cw, PadCommunityClient.uploadProgress);
            } else if (captioning) {
                PadUi.wrap(g, "Texto (opcional)", cx, py + 26, cw, PadUi.MUTED, 5);
            } else {
                button(g, cx, py + 26, cw, "PUBLICAR", PadView.GOLD, mx, my, () -> {
                    captioning = true;
                    caption = "";
                });
                boolean sure = p.equals(confirmPath) && System.currentTimeMillis() - confirmDelete < 3000;
                button(g, cx, py + 45, cw, sure ? "¿SEGURO?" : "BORRAR", PadView.RED, mx, my, this::delete);
            }
        } else {
            PadUi.wrap(g, "Pulsa una foto para publicarla.", cx, py + 1, cw, PadUi.MUTED, 6);
        }
    }

    private void arrow(GuiGraphics g, int x, int y, boolean left, double mx, double my, Runnable action) {
        boolean hover = PadUi.inside(mx, my, x, y, 10, 14);
        PadUi.box(g, x, y, 10, 14, PadUi.INK);
        g.fillGradient(x + 1, y + 1, x + 9, y + 13, hover ? 0xFFFFD650 : 0xEE5AB4FF, hover ? 0xFFE8961A : 0xEE2C74E4);
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
        boolean hover = PadUi.inside(mx, my, x, y, w, 16);
        if (hover) pad.hover("§" + label);
        PadUi.button(g, x, y, w, label, style, hover, true);
        buttons.add(new int[] {x, y, w, 16});
        actions.add(action);
    }

    private void move(int d) {
        if (photos.isEmpty()) return;
        index = (index + d + photos.size()) % photos.size();
        captioning = false;
        confirmDelete = 0;
        pad.sound("hover", 0.6F);
    }

    private void delete() {
        if (photos.isEmpty()) return;
        Path p = photos.get(index);
        if (!p.equals(confirmPath) || System.currentTimeMillis() - confirmDelete > 3000) {
            confirmDelete = System.currentTimeMillis();
            confirmPath = p;
            return;
        }
        confirmDelete = 0;
        if (p.equals(PadCamera.lastPhoto)) PadCamera.lastPhoto = null;
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
        closed = true;
        for (Tex t : textures.values()) Minecraft.getInstance().getTextureManager().release(t.loc);
        textures.clear();
    }
}
