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
import net.tierrasfantasticas.tfclient.revive.ReviveNetwork;
import net.tierrasfantasticas.tfclient.revive.SelfReviveTogglePacket;
import net.tierrasfantasticas.tfclient.revive.SurrenderTogglePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Pose;
import org.lwjgl.glfw.GLFW;

public final class RevivemodClient {
    private static final int CHANNEL_TICKS = 80;
    public static volatile boolean LOCAL_DOWNED = false;
    public static volatile int SELF_COST = 10;
    private static int sneakTicks = 0;
    private static int selfTicks = 0;

    private RevivemodClient() {
    }

    public static void reset() {
        LOCAL_DOWNED = false;
        sneakTicks = 0;
        selfTicks = 0;
    }

    public static void onDownStart(int selfCost) {
        LOCAL_DOWNED = true;
        SELF_COST = selfCost;
        sneakTicks = 0;
        selfTicks = 0;
    }

    public static void onDownEnd() {
        reset();
    }

    private static boolean isHeld(Minecraft mc, KeyMapping kb) {
        Key key = InputConstants.getKey(kb.saveString());
        long win = mc.getWindow().getWindow();
        if (key.getType() == Type.MOUSE) {
            return GLFW.glfwGetMouseButton(win, key.getValue()) == 1;
        } else {
            int code = key.getValue();
            return code == InputConstants.UNKNOWN.getValue() ? kb.isDown() : InputConstants.isKeyDown(win, code);
        }
    }

    public static void onClientTick(Minecraft mc) {
        if (LOCAL_DOWNED && mc.player != null) {
            if (isHeld(mc, RevivemodKeybinds.SURRENDER)) {
                if (++sneakTicks == 80) {
                    ReviveNetwork.CHANNEL.sendToServer(new SurrenderTogglePacket());
                    sneakTicks = 0;
                }
            } else {
                sneakTicks = 0;
            }

            boolean canAfford = mc.player.experienceLevel >= SELF_COST;
            if (canAfford && isHeld(mc, RevivemodKeybinds.SELF_REVIVE)) {
                if (++selfTicks == 80) {
                    ReviveNetwork.CHANNEL.sendToServer(new SelfReviveTogglePacket());
                    selfTicks = 0;
                }
            } else {
                selfTicks = 0;
            }

            mc.player.setPose(Pose.SWIMMING);
        } else {
            sneakTicks = 0;
            selfTicks = 0;
        }
    }

    public static void onHud(GuiGraphics ctx, int screenWidth) {
        if (LOCAL_DOWNED) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                Font tr = mc.font;
                int cx = screenWidth / 2;
                int y = 55;
                if (sneakTicks > 0) {
                    drawBar(ctx, tr, cx, y, "Rindiéndote", sneakTicks, -43691);
                } else if (selfTicks > 0) {
                    drawBar(ctx, tr, cx, y, "Auto-reviviéndote", selfTicks, -11162881);
                } else {
                    boolean canAfford = mc.player.experienceLevel >= SELF_COST;
                    String surrKey = keyName(RevivemodKeybinds.SURRENDER);
                    String selfKey = keyName(RevivemodKeybinds.SELF_REVIVE);
                    MutableComponent surrender = Component.literal("")
                        .append(Component.literal("[" + surrKey + "]").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}))
                        .append(Component.literal(" Rendirte").withStyle(ChatFormatting.WHITE));
                    MutableComponent self = Component.literal("")
                        .append(
                            Component.literal("[" + selfKey + "]")
                                .withStyle(new ChatFormatting[]{canAfford ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY, ChatFormatting.BOLD})
                        )
                        .append(Component.literal(" Auto-revivir (" + SELF_COST + " niveles de XP)").withStyle(ChatFormatting.WHITE));
                    ctx.drawCenteredString(tr, surrender, cx, y, -1);
                    ctx.drawCenteredString(tr, self, cx, y + 11, -1);
                }
            }
        }
    }

    private static String keyName(KeyMapping km) {
        return km.getTranslatedKeyMessage().getString();
    }

    private static void drawBar(GuiGraphics ctx, Font tr, int cx, int y, String label, int ticks, int color) {
        int pct = Math.min(100, ticks * 100 / 80);
        int filled = pct / 5;
        StringBuilder b = new StringBuilder(20);

        for (int i = 0; i < 20; i++) {
            b.append((char)(i < filled ? '|' : '.'));
        }

        MutableComponent t = Component.literal(label + " ")
            .withStyle(ChatFormatting.WHITE)
            .append(Component.literal("[" + b + "] " + pct + "%").withStyle(ChatFormatting.YELLOW));
        ctx.drawCenteredString(tr, t, cx, y, color);
    }
}
