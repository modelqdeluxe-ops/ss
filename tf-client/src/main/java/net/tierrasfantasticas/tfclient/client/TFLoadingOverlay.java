package net.tierrasfantasticas.tfclient.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
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
        // La pantalla original sigue mandando: termina la carga y se retira sola al acabar.
        delegate.render(g, mouseX, mouseY, partialTick);

        long now = Util.getMillis();
        if (firstFrame < 0L) firstFrame = now;
        progress = Mth.clamp(progress * 0.95f + reload.getActualProgress() * 0.05f, 0f, 1f);
        if (doneAt < 0L && reload.isDone() && (!fadeIn || now - firstFrame >= 1000L)) doneAt = now;

        // Mismos tiempos que la pantalla original: 1 s fija al terminar y 1 s de fundido hacia el menú.
        float in = fadeIn ? Mth.clamp((now - firstFrame) / 500f, 0f, 1f) : 1f;
        float out = doneAt < 0L ? 1f : 1f - Mth.clamp((now - doneAt) / 1000f - 1f, 0f, 1f);
        float alpha = in * out;
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

    @Override
    public boolean isPauseScreen() {
        return delegate.isPauseScreen();
    }
}
