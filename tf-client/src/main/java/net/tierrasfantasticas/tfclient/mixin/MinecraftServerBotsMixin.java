/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import net.tierrasfantasticas.tfclient.server.TFBots;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (1.3.109) Los bots (/tf bots) también cuentan en la lista de servidores: el número de conectados (getPlayerCount: el
 * que usa la consulta y, en Mohist, el aviso de Bukkit) y, en Forge, la lista de nombres al pasar el ratón
 * (buildPlayerStatus).
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerBotsMixin {
    @Inject(method = "getPlayerCount", at = @At("RETURN"), cancellable = true)
    private void tfclient$bots(CallbackInfoReturnable<Integer> cir) {
        int bots = TFBots.listOnly().size(); // (1.3.125: los de verdad ya los cuenta el servidor)
        if (bots > 0) cir.setReturnValue(cir.getReturnValueI() + bots);
    }

    @Inject(method = "buildPlayerStatus", at = @At("RETURN"), cancellable = true)
    private void tfclient$botsStatus(CallbackInfoReturnable<ServerStatus.Players> cir) {
        List<TFBots.Bot> bots = TFBots.listOnly();
        ServerStatus.Players p = cir.getReturnValue();
        if (bots.isEmpty() || p == null) return;
        List<GameProfile> sample = new ArrayList<>(p.sample());
        for (TFBots.Bot b : bots) {
            if (sample.size() >= 12) break;
            sample.add(b.profile());
        }
        java.util.Collections.shuffle(sample);
        cir.setReturnValue(new ServerStatus.Players(p.max(), p.online() + bots.size(), sample));
    }
}
