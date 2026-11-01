/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.revive.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ReviveConfigScreen extends Screen {
    private final Screen parent;
    private KeyMapping selecting;
    private Button surrenderBtn;
    private Button selfBtn;

    public ReviveConfigScreen(Screen parent) {
        super(Component.literal("Configuración de Revive Mod"));
        this.parent = parent;
    }

    protected void init() {
        int cx = this.width / 2;
        int y = 60;
        this.surrenderBtn = Button.builder(Component.empty(), b -> {
            this.selecting = RevivemodKeybinds.SURRENDER;
            this.updateButtons();
        }).bounds(cx - 120, y, 240, 20).build();
        this.addRenderableWidget(this.surrenderBtn);
        this.selfBtn = Button.builder(Component.empty(), b -> {
            this.selecting = RevivemodKeybinds.SELF_REVIVE;
            this.updateButtons();
        }).bounds(cx - 120, y + 28, 240, 20).build();
        this.addRenderableWidget(this.selfBtn);
        this.addRenderableWidget(Button.builder(Component.literal("Restablecer teclas por defecto"), b -> {
            this.setKey(RevivemodKeybinds.SURRENDER, Type.KEYSYM.getOrCreate(69));
            this.setKey(RevivemodKeybinds.SELF_REVIVE, Type.KEYSYM.getOrCreate(70));
            this.selecting = null;
            this.updateButtons();
        }).bounds(cx - 120, y + 60, 240, 20).build());
        this.addRenderableWidget(
            Button.builder(Component.literal("Todos los controles…"), b -> this.minecraft.setScreen(new KeyBindsScreen(this, this.minecraft.options)))
                .bounds(cx - 120, y + 88, 240, 20)
                .build()
        );
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).bounds(cx - 120, y + 124, 240, 20).build());
        this.updateButtons();
    }

    private void updateButtons() {
        this.surrenderBtn.setMessage(this.label("Rendirse / Morir", RevivemodKeybinds.SURRENDER));
        this.selfBtn.setMessage(this.label("Auto-revivir", RevivemodKeybinds.SELF_REVIVE));
    }

    private Component label(String name, KeyMapping km) {
        Component keyName = km.getTranslatedKeyMessage();
        return this.selecting == km
            ? Component.literal(name + ": ")
                .append(Component.literal("> ").withStyle(ChatFormatting.YELLOW))
                .append(keyName.copy().withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" <").withStyle(ChatFormatting.YELLOW))
            : Component.literal(name + ": ").append(keyName.copy().withStyle(ChatFormatting.GREEN));
    }

    private void setKey(KeyMapping km, Key key) {
        this.minecraft.options.setKey(km, key);
        KeyMapping.resetMapping();
        this.minecraft.options.save();
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.selecting != null) {
            if (keyCode == 256) {
                this.setKey(this.selecting, InputConstants.UNKNOWN);
            } else {
                this.setKey(this.selecting, InputConstants.getKey(keyCode, scanCode));
            }

            this.selecting = null;
            this.updateButtons();
            return true;
        } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (this.selecting != null) {
            this.setKey(this.selecting, Type.MOUSE.getOrCreate(button));
            this.selecting = null;
            this.updateButtons();
            return true;
        } else {
            return super.mouseClicked(mx, my, button);
        }
    }

    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 18, 16777215);
        g.drawCenteredString(
            this.font,
            Component.literal("Haz clic en una tecla y luego pulsa la nueva tecla.").withStyle(ChatFormatting.GRAY),
            this.width / 2,
            38,
            10526880
        );
        super.render(g, mx, my, partialTick);
    }

    public void onClose() {
        this.minecraft.options.save();
        this.minecraft.setScreen(this.parent);
    }
}
