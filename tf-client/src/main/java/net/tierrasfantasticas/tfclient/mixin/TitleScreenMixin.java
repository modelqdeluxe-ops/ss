package net.tierrasfantasticas.tfclient.mixin;

import java.util.ArrayList;
import java.util.List;
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.client.TFDraw;
import net.tierrasfantasticas.tfclient.client.TFLogoRenderer;
import net.tierrasfantasticas.tfclient.client.TFMenuButton;
import net.tierrasfantasticas.tfclient.client.TFTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menú principal de TF: el paisaje de fondo en lugar del panorama, el emblema TF encima de los botones y los botones
 * con el diseño de TF (TIERRAS FANTÁSTICAS, Mundo local, Mods, Opciones, Salir). Sin textos de Mojang, versión,
 * Realms, idioma ni accesibilidad (idioma y accesibilidad siguen en Opciones).
 *
 * Importante: sin lambdas ni referencias a métodos aquí (van en TFMenuButton); en un mixin pueden generar métodos que
 * nombran la clase del mixin y Forge no deja cargarla.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Shadow
    private SplashRenderer splash;

    @Unique
    private static final int BUTTON_SHIFT = 12;

    @Unique
    private int tfclient$buttonsTop = -1;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    /** Fondo: el paisaje de Tierras Fantásticas. */
    @Inject(method = "render", at = @At("HEAD"))
    private void tfclient$drawBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        try {
            g.fill(0, 0, this.width, this.height, 0xFF000000);
            TFDraw.cover(g, TFTextures.MENU, this.width, this.height, 1f);
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Client: error al dibujar el fondo del menú", t);
        }
    }

    /** Sin panorama giratorio. */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/PanoramaRenderer;render(FF)V"))
    private void tfclient$skipPanorama(PanoramaRenderer panorama, float partialTick, float alpha) {
    }

    /** Emblema TF encima de los botones en lugar del logo de Minecraft. */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
    private void tfclient$drawLogo(LogoRenderer logoRenderer, GuiGraphics g, int screenWidth, float alpha) {
        int buttonsTop = this.tfclient$buttonsTop > 0 ? this.tfclient$buttonsTop : this.height / 4 + 48;
        int top = 4;
        int logoHeight = Math.min(170, buttonsTop - top - 8); // margen para que flote sin tocar los botones
        try {
            if (logoHeight > 16) TFLogoRenderer.render(g, screenWidth / 2, top, logoHeight, alpha);
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Client: error al dibujar el emblema", t);
        }
    }

    /**
     * Sin el aviso de Realms. Solo se deja de dibujar: si se apaga realmsNotificationsEnabled(), Minecraft crea igual la
     * pantalla de avisos sin iniciarla y crashea al volver al menú (RealmsNotificationsScreen.added con minecraft nulo).
     */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/realmsclient/gui/screens/RealmsNotificationsScreen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void tfclient$hideRealmsNotifications(RealmsNotificationsScreen screen, GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void tfclient$customizeButtons(CallbackInfo ci) {
        try {
            this.tfclient$replaceButtons();
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Client: error al cambiar los botones del menú", t);
        }
    }

    @Unique
    private void tfclient$replaceButtons() {
        this.splash = null; // sin frase amarilla: taparía el emblema

        Button singleplayer = null;
        Button mods = null;
        Button options = null;
        Button quit = null;
        List<GuiEventListener> remove = new ArrayList<>();
        for (GuiEventListener child : this.children()) {
            if (child instanceof PlainTextButton || child instanceof ImageButton) {
                remove.add(child); // copyright de Mojang, idioma y accesibilidad
                continue;
            }
            if (!(child instanceof Button button)) continue;
            if (!(button.getMessage().getContents() instanceof TranslatableContents contents)) continue;
            switch (contents.getKey()) {
                case "menu.singleplayer" -> singleplayer = button;
                case "menu.multiplayer", "menu.online" -> remove.add(button);
                case "fml.menu.mods" -> mods = button;
                case "menu.options" -> options = button;
                case "menu.quit" -> quit = button;
                default -> {
                }
            }
        }
        if (singleplayer == null) return; // modo demo: se deja el menú normal

        // Botones un poco más abajo para dejar sitio a un emblema más grande
        int shift = Math.max(0, Math.min(BUTTON_SHIFT, this.height - (singleplayer.getY() + 132)));
        int x = this.width / 2 - 100;
        int top = singleplayer.getY() + shift;
        this.tfclient$buttonsTop = top;
        for (GuiEventListener child : remove) {
            this.removeWidget(child);
        }

        this.addRenderableWidget(TFMenuButton.server(x, top, 200));
        this.tfclient$swap(singleplayer, x, top + 24, 200, "Mundo local");
        if (mods != null) this.tfclient$swap(mods, x, top + 48, 200, "Mods");
        if (options != null) this.tfclient$swap(options, options.getX(), options.getY() + shift, options.getWidth(), "Opciones");
        if (quit != null) this.tfclient$swap(quit, quit.getX(), quit.getY() + shift, quit.getWidth(), "Salir");
        // Web y Discord, de oro, debajo de Opciones y Salir
        int linksY = (options != null ? options.getY() : top + 84) + shift + 24;
        this.addRenderableWidget(TFMenuButton.web(x, linksY, 98));
        this.addRenderableWidget(TFMenuButton.discord(x + 102, linksY, 98));
    }

    @Unique
    private void tfclient$swap(Button original, int x, int y, int width, String text) {
        this.removeWidget(original);
        this.addRenderableWidget(TFMenuButton.wrapping(original, x, y, width, text));
    }
}
