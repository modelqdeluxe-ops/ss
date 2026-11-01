/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * (1.3.160, el dueño: «asegúrate que el tag del clan al escribir en chat funcione bien el efecto arcoíris, no quiero que
 * nada se rompa») La etiqueta del clan animada (arcoíris u onda) también en las líneas del chat, como en el TAB y encima
 * de la cabeza: solo se cambian los colores de «[TAG]» (si vienen con color del servidor) al dibujar; el mensaje, el
 * resto de la línea y lo que guarda el chat no se tocan. require = 0: si otro mod cambiara el chat y no se encontrara
 * dónde engancharse, no pasa nada (sale quieto, como antes).
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentClanMixin {
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"),
            index = 1, require = 0)
    private FormattedCharSequence tfclient$clanTag(FormattedCharSequence line) {
        return net.tierrasfantasticas.tfclient.clans.client.ClanClient.animateChat(line);
    }
}
