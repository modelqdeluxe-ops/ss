/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.tierrasfantasticas.tfclient.TFConfig;

/**
 * La contraseña de los mundos locales ({@link TFLock}): el TF Client es para jugar en {@code TFConfig.serverName()};
 * los mundos locales, solo para quien tenga la contraseña (el staff). Bien puesta, abre lo que se iba a abrir (la
 * lista de mundos o crear uno) y no se vuelve a pedir en ese ordenador.
 */
public class TFLocalLockScreen extends Screen {
    private final Screen parent, next;
    private EditBox box;
    private String error = "";

    public TFLocalLockScreen(Screen parent, Screen next) {
        super(Component.literal("Mundo local"));
        this.parent = parent;
        this.next = next;
    }

    @Override
    protected void init() {
        int cx = this.width / 2, y = this.height / 2;
        box = new EditBox(this.font, cx - 100, y - 10, 200, 20, Component.literal("Contraseña"));
        box.setMaxLength(64);
        // se escribe sin que se vea (puntos)
        box.setFormatter((text, start) -> FormattedCharSequence.forward("•".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(new TFMenuButton(cx - 102, y + 22, 98, 20, TFMenuButton.label("Volver"), b -> onClose(), false));
        addRenderableWidget(new TFMenuButton(cx + 4, y + 22, 98, 20, TFMenuButton.label("Entrar"), b -> submit(), false));
    }

    private void submit() {
        if (TFLock.tryUnlock(box.getValue())) {
            Minecraft.getInstance().setScreen(next != null ? next : new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(parent));
        } else {
            error = "Contraseña incorrecta.";
            box.setValue("");
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = this.width / 2, y = this.height / 2;
        g.drawCenteredString(this.font, TFMenuButton.label("Mundo local"), cx, y - 62, 0xFFD667);
        g.drawCenteredString(this.font, Component.literal("TF Client es para jugar en " + TFConfig.serverName() + "."), cx, y - 46, 0xE0E0E0);
        g.drawCenteredString(this.font, Component.literal("Los mundos locales necesitan la contraseña del staff."), cx, y - 34, 0x9AA0AA);
        if (!error.isEmpty()) g.drawCenteredString(this.font, Component.literal(error), cx, y + 48, 0xFF6B5E);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
