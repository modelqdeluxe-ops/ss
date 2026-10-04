package net.tierrasfantasticas.tfclient.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.tierrasfantasticas.tfclient.mixin.LoadingOverlayAccessor;

/**
 * Pantalla de carga de TF. Envuelve la pantalla de carga original (la de Mojang o la de Forge): la deja hacer
 * todo su trabajo (terminar la carga, abrir el menú, quitarse) y dibuja encima el fondo, el emblema y la barra.
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
        g.fill(0, 0, width, height, TFDraw.argb(alpha, TFDraw.NIGHT));
        TFDraw.background(g, width, height, alpha, now, -1, -1, 0.5f);
        // Viñeta inferior para que la barra destaque
        g.fillGradient(0, height / 2, width, height, TFDraw.argb(0f, TFDraw.NIGHT), TFDraw.argb(alpha * 0.85f, TFDraw.NIGHT));

        float logoHeight = Math.min(height * 0.5f, width * 0.42f);
        float bob = (float) Math.sin(now / 700.0) * 3f;
        float logoY = height * 0.44f + bob;
        TFDraw.logo(g, width / 2f, logoY, logoHeight, alpha);

        int barWidth = Math.min(width * 3 / 5, 280);
        int barY = Math.min((int) (height * 0.44f + logoHeight / 2f) + 16, height - 24);
        float shown = doneAt >= 0L ? 1f : progress;
        TFDraw.progressBar(g, (width - barWidth) / 2, barY, barWidth, 5, shown, alpha, now);
    }

    @Override
    public boolean isPauseScreen() {
        return delegate.isPauseScreen();
    }
}
