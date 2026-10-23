/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.TFConfig;
import net.tierrasfantasticas.tfclient.bosses.BossTest;

/**
 * AutoModpack primero (1.3.121, el dueño: «usamos AutoModpack, que le descarga al usuario los recursos del servidor; al
 * entrar al server que no le pida los mods restantes primero: primero debe pedirle el AutoModpack que tiene el server,
 * ese jar; sin ese jar no pueden entrar; ya dentro, AutoModpack le descarga todos los mods y recursos»; «no pongas
 * versión, solo pide el AutoModpack, nosotros lo compartimos»).
 * <ul>
 *   <li>Sin AutoModpack, no se entra al servidor (el botón del menú, la lista de servidores, la conexión directa:
 *   {@code ConnectScreenMixin}): sale {@link Missing}, que dice que pongan en mods el jar que comparte el servidor y
 *   reinicien, con botones para abrir la carpeta mods y el Discord.</li>
 *   <li>Con AutoModpack, se conecta sin mirar los demás mods (antes TFServerCheck enseñaba la lista de los que faltaban):
 *   AutoModpack descarga lo del servidor.</li>
 * </ul>
 * Cuenta como instalado si Forge lo cargó (modId automodpack) o si hay un automodpack*.jar en la carpeta mods (recién
 * puesto, antes de reiniciar; o si una versión se llamara de otra forma: nunca se deja fuera a quien sí lo tiene).
 */
public final class TFAutoModpack {
    private TFAutoModpack() {}

    public static boolean present() {
        if (BossTest.ON) return true; // la prueba en el juego no entra a ningún servidor con mods
        if (ModList.get() != null && (ModList.get().isLoaded("automodpack") || ModList.get().isLoaded("automodpack_mod"))) return true;
        try (Stream<Path> files = Files.list(FMLPaths.MODSDIR.get())) {
            return files.map(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))
                    .anyMatch(n -> n.startsWith("automodpack") && n.endsWith(".jar"));
        } catch (IOException e) {
            TFClient.LOGGER.warn("TF Client: no se pudo mirar la carpeta mods: {}", e.getMessage());
            return false;
        }
    }

    /** «Falta AutoModpack»: qué hacer, con la carpeta mods y el Discord a un clic. */
    public static class Missing extends Screen {
        private final Screen parent;

        public Missing(Screen parent) {
            super(Component.literal("Falta AutoModpack"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int y = this.height / 2 + 34;
            addRenderableWidget(new TFMenuButton(this.width / 2 - 154, y, 100, 20, TFMenuButton.label("Carpeta mods"),
                    b -> Util.getPlatform().openFile(FMLPaths.MODSDIR.get().toFile()), false));
            addRenderableWidget(new TFMenuButton(this.width / 2 - 50, y, 100, 20, TFMenuButton.label("Discord"), b -> TFDiscord.open(), false));
            addRenderableWidget(new TFMenuButton(this.width / 2 + 54, y, 100, 20, TFMenuButton.label("Volver"),
                    b -> Minecraft.getInstance().setScreen(parent), false));
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderBackground(g);
            int cx = this.width / 2, y = this.height / 2 - 52;
            g.drawCenteredString(this.font, TFMenuButton.label("Necesitas AutoModpack para entrar"), cx, y, 0xFFD667);
            String[] lines = {
                "Para jugar en " + TFConfig.serverName() + " primero necesitas AutoModpack.",
                "Pon en tu carpeta mods el jar de AutoModpack que compartimos (Discord) y reinicia el juego.",
                "Al entrar, AutoModpack te descarga solo los mods y recursos del servidor."};
            y += 20;
            for (String line : lines) {
                for (FormattedCharSequence part : this.font.split(Component.literal(line), Math.min(360, this.width - 24))) {
                    g.drawCenteredString(this.font, part, cx, y, 0xE6E0D4);
                    y += 11;
                }
                y += 3;
            }
            super.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            Minecraft.getInstance().setScreen(parent);
        }
    }
}
