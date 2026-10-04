package net.tierrasfantasticas.tfclient.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.mixin.LoadingOverlayAccessor;

/**
 * Pantalla de carga de TF: el banner de Tierras Fantásticas a pantalla completa con la barra de progreso de Minecraft.
 * Envuelve la pantalla de carga original (la de Mojang o la de Forge): la deja hacer todo su trabajo (terminar la
 * carga, abrir el menú y quitarse) y dibuja encima la de TF.
 */
public class TFLoadingOverlay extends LoadingOverlay {
    private final LoadingOverlay delegate;
    private final ReloadInstance reload;
    private final boolean fadeIn;
    private long firstFrame = -1L;
    private long doneAt = -1L;
    private float progress;
    private boolean broken;
    /** La pantalla original ya terminó y pidió quitarse: a partir de aquí la de TF se funde sola sobre el menú. */
    private long delegateDoneAt = -1L;
    private boolean finished;

    private static final long HOLD_MS = 500L;
    private static final long FADE_MS = 700L;

    public TFLoadingOverlay(LoadingOverlay delegate) {
        this(delegate, ((LoadingOverlayAccessor) delegate).tfclient$getReload(), ((LoadingOverlayAccessor) delegate).tfclient$isFadeIn());
    }

    private TFLoadingOverlay(LoadingOverlay delegate, ReloadInstance reload, boolean fadeIn) {
        super(Minecraft.getInstance(), reload, error -> {}, fadeIn);
        this.delegate = delegate;
        this.reload = reload;
        this.fadeIn = fadeIn;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // La pantalla original sigue mandando: termina la carga y pide quitarse al acabar (ver holdAfterDelegate).
        if (delegateDoneAt < 0L || broken) {
            delegate.render(g, mouseX, mouseY, partialTick);
        }
        if (broken) {
            if (delegateDoneAt >= 0L) finish();
            return;
        }
        try {
            if (delegateDoneAt >= 0L) {
                // Ya cargó: se ve el menú debajo y la pantalla de TF se desvanece (sin pasar por el rótulo de Mojang).
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen != null) mc.screen.render(g, mouseX, mouseY, partialTick);
            }
            renderTF(g);
        } catch (Throwable t) {
            // Si algo falla al dibujar, se queda la pantalla de carga normal: el juego nunca se cierra por esto.
            broken = true;
            TFClient.LOGGER.error("TF Client: error en la pantalla de carga, se usa la normal", t);
        }
    }

    private void renderTF(GuiGraphics g) {
        if (Minecraft.getInstance().level == null) TFMusic.play();

        long now = Util.getMillis();
        if (firstFrame < 0L) firstFrame = now;
        progress = Mth.clamp(progress * 0.95f + reload.getActualProgress() * 0.05f, 0f, 1f);
        if (doneAt < 0L && reload.isDone() && (!fadeIn || now - firstFrame >= 1000L)) doneAt = now;

        // Opaca mientras la original se desvanece (así nunca se ve su rótulo rojo); después, medio segundo más y fundido.
        float in = fadeIn ? Mth.clamp((now - firstFrame) / 300f, 0f, 1f) : 1f;
        float out = 1f;
        if (delegateDoneAt >= 0L) {
            out = 1f - Mth.clamp((now - delegateDoneAt - HOLD_MS) / (float) FADE_MS, 0f, 1f);
        }
        float alpha = in * out;
        if (delegateDoneAt >= 0L && out <= 0f) {
            finish();
            return;
        }
        if (alpha <= 0.01f) return;

        int width = g.guiWidth();
        int height = g.guiHeight();
        g.fill(0, 0, width, height, TFDraw.argb(alpha, 0x000000));
        TFDraw.cover(g, TFTextures.LOADING, width, height, alpha);

        int barHalfWidth = (int) (Math.min(width * 0.75, height) * 0.5);
        int barCenterY = (int) (height * 0.8325);
        TFDraw.progressBar(g, width / 2 - barHalfWidth, barCenterY - 5, width / 2 + barHalfWidth, barCenterY + 5,
                doneAt >= 0L ? 1f : progress, alpha);
    }

    /**
     * Lo llama MinecraftMixin cuando la pantalla original pide quitarse (setOverlay(null)). Devuelve true para que la
     * de TF siga en pantalla hasta terminar su fundido.
     */
    public boolean holdAfterDelegate() {
        if (finished) return false;
        if (delegateDoneAt < 0L) delegateDoneAt = Util.getMillis();
        return true;
    }

    private void finish() {
        finished = true;
        Minecraft.getInstance().setOverlay(null);
    }

    @Override
    public boolean isPauseScreen() {
        return delegate.isPauseScreen();
    }
}
