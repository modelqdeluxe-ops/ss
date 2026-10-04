package net.tierrasfantasticas.tfclient.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.tierrasfantasticas.tfclient.TFConfig;
import net.tierrasfantasticas.tfclient.client.TFDraw;
import net.tierrasfantasticas.tfclient.client.TFServer;
import net.tierrasfantasticas.tfclient.client.TFTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menú principal de Minecraft con la imagen de TF: el paisaje de fondo en lugar del panorama, el emblema TF encima de
 * los botones en lugar del logo de Minecraft, y los botones "Tierras Fantásticas" (conexión directa) y "Mundo local"
 * en lugar de Un jugador, Multijugador y Realms. El resto (Mods, Opciones, Salir, idioma...) queda como siempre.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Shadow
    private SplashRenderer splash;

    @Unique
    private int tfclient$buttonsTop = -1;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    /** Fondo: el paisaje de Tierras Fantásticas. */
    @Inject(method = "render", at = @At("HEAD"))
    private void tfclient$drawBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        g.fill(0, 0, this.width, this.height, 0xFF000000);
        TFDraw.cover(g, TFTextures.MENU, this.width, this.height, 1f);
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
        int top = 6;
        int logoHeight = Math.min(150, buttonsTop - top - 6);
        if (logoHeight > 16) TFDraw.image(g, TFTextures.LOGO, screenWidth / 2, top, logoHeight, alpha);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void tfclient$customizeButtons(CallbackInfo ci) {
        this.splash = null; // sin frase amarilla: taparía el emblema

        Button singleplayer = null;
        Button mods = null;
        List<Button> remove = new ArrayList<>();
        for (GuiEventListener child : this.children()) {
            if (!(child instanceof Button button)) continue;
            if (!(button.getMessage().getContents() instanceof TranslatableContents contents)) continue;
            switch (contents.getKey()) {
                case "menu.singleplayer" -> singleplayer = button;
                case "menu.multiplayer", "menu.online" -> remove.add(button);
                case "fml.menu.mods" -> mods = button;
                default -> {
                }
            }
        }
        if (singleplayer == null) return; // modo demo: se deja el menú normal

        int x = this.width / 2 - 100;
        int top = singleplayer.getY();
        this.tfclient$buttonsTop = top;
        remove.forEach(this::removeWidget);

        Button server = Button.builder(Component.literal(TFConfig.serverName().toUpperCase(java.util.Locale.ROOT)),
                b -> TFServer.join(this)).bounds(x, top, 200, 20).build();
        this.addRenderableWidget(server);

        singleplayer.setMessage(Component.literal("Mundo local"));
        singleplayer.setY(top + 24);
        if (mods != null) {
            mods.setX(x);
            mods.setWidth(200);
            mods.setY(top + 48);
        }
    }
}
