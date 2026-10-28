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
import net.tierrasfantasticas.tfclient.server.TFStaffLock;

/** La contraseña de /tf ({@link TFStaffLock}): se escribe con puntos y se manda por su paquete, no por el chat. */
public class TFStaffLockScreen extends Screen {
    private EditBox box;
    private boolean sent;

    public TFStaffLockScreen() {
        super(Component.literal("Contraseña de /tf"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new TFStaffLockScreen());
    }

    @Override
    protected void init() {
        int cx = this.width / 2, y = this.height / 2;
        box = new EditBox(this.font, cx - 100, y - 10, 200, 20, Component.literal("Contraseña"));
        box.setMaxLength(64);
        box.setFormatter((text, start) -> FormattedCharSequence.forward("•".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(new TFMenuButton(cx - 102, y + 22, 98, 20, TFMenuButton.label("Cancelar"), b -> onClose(), false));
        addRenderableWidget(new TFMenuButton(cx + 4, y + 22, 98, 20, TFMenuButton.label("Desbloquear"), b -> submit(), false));
    }

    private void submit() {
        if (box.getValue().isBlank()) return;
        send(box.getValue());
        Minecraft.getInstance().setScreen(null);
    }

    private void send(String password) {
        if (sent) return;
        sent = true;
        TFStaffLock.CHANNEL.sendToServer(new TFStaffLock.Answer(password));
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
        g.drawCenteredString(this.font, TFMenuButton.label("Comandos de staff"), cx, y - 46, 0xFFD667);
        g.drawCenteredString(this.font, Component.literal("Escribe la contraseña de /tf."), cx, y - 30, 0x9AA0AA);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        send("");
        Minecraft.getInstance().setScreen(null);
    }
}
