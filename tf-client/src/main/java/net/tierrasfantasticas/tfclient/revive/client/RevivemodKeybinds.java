/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.revive.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;

/** Las dos teclas, con los mismos nombres que en el revivemod (así se conservan las que tuviera cambiadas cada jugador). */
public final class RevivemodKeybinds {
    public static final String CATEGORY = "key.categories.revivemod";
    public static final int DEFAULT_SURRENDER_KEY = 69;
    public static final int DEFAULT_SELF_REVIVE_KEY = 70;
    public static final KeyMapping SURRENDER = new KeyMapping("key.revivemod.surrender", KeyConflictContext.IN_GAME, Type.KEYSYM, 69, "key.categories.revivemod");
    public static final KeyMapping SELF_REVIVE = new KeyMapping(
        "key.revivemod.self_revive", KeyConflictContext.IN_GAME, Type.KEYSYM, 70, "key.categories.revivemod"
    );

    private RevivemodKeybinds() {
    }
}
