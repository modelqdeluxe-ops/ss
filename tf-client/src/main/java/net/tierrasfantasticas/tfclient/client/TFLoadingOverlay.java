/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.mixin.LoadingOverlayAccessor;
import org.joml.Matrix4f;

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

    /** (1.3.136) Para la prueba en el juego: sin devolver la proyección de la interfaz (como antes del arreglo). */
    public static volatile boolean testNoFix = System.getenv("TFCLIENT_PRUEBA_JEFES") != null;

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
            // (1.3.136, el dueño con captura: «la primera vez que abro Minecraft después de instalar todo con el
            // AutoModpack, la pantalla de carga sale así»: la roja de Forge y la de TF pequeña, abajo a la izquierda y
            // del revés) El primer arranque (antes de que fml.toml diga earlyWindowControl = false) la original es la de
            // Forge (ForgeLoadingOverlay): pinta su ventana de arranque con la proyección en píxeles de la ventana y la Y
            // hacia arriba, y no la devuelve. Lo de TF se dibujaba con esa: a la mitad de ancho y alto, abajo y girado.
            // Se guarda la de la interfaz y se vuelve a poner (con su marco, la ventana entera y sin recortar caras).
            Matrix4f gui = new Matrix4f(RenderSystem.getProjectionMatrix());
            delegate.render(g, mouseX, mouseY, partialTick);
            if (!testNoFix) restoreGui(gui);
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

    /** Lo que deja cambiado la pantalla de carga de Forge, como estaba para la interfaz (ver render). */
    private static void restoreGui(Matrix4f gui) {
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.viewport(0, 0, w.getWidth(), w.getHeight());
        RenderSystem.setProjectionMatrix(gui, VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
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
        if (!finished) logTimes();
        finished = true;
        Minecraft.getInstance().setOverlay(null);
    }

    /** Cuándo se lanzó Java (el arranque del juego) y si ya se apuntó el primer arranque hasta el menú. */
    private static boolean timed;

    /**
     * (1.3.136, el dueño: «que entre rápido al menú») En el log, lo que tardó: desde que se abrió el juego hasta el menú, y
     * cuánto de eso fue la carga de recursos (texturas, modelos y sonidos de todos los mods: lo que dibuja esta pantalla).
     * Con el latest.log del dueño se ve dónde se va el tiempo.
     */
    private void logTimes() {
        long now = System.currentTimeMillis();
        long jvm = java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();
        long reloadMs = firstFrame < 0L ? 0L : Util.getMillis() - firstFrame;
        if (!timed) {
            timed = true;
            TFClient.LOGGER.info("TF Client: arranque: {} s desde que se abrió el juego hasta el menú (la pantalla de carga, {} s; mods: {})",
                    String.format(java.util.Locale.ROOT, "%.1f", (now - jvm) / 1000.0), String.format(java.util.Locale.ROOT, "%.1f", reloadMs / 1000.0),
                    net.minecraftforge.fml.ModList.get().size());
        } else {
            TFClient.LOGGER.info("TF Client: recarga de recursos: {} s", String.format(java.util.Locale.ROOT, "%.1f", reloadMs / 1000.0));
        }
    }

    /** La pantalla de carga que envuelve (la de Forge, ForgeLoadingOverlay, o la de Minecraft). */
    public String wrapped() {
        return delegate.getClass().getSimpleName();
    }

    @Override
    public boolean isPauseScreen() {
        return delegate.isPauseScreen();
    }
}
