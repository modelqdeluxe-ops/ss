/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import com.mojang.blaze3d.audio.OggAudioStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.tierrasfantasticas.tfclient.crates.client.media.SealedAudio;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (1.3.109) La música de la escena de las crates, cifrada dentro del mod (SealedAudio): al abrirla para sonar (en
 * streaming, como la música), se descifra en memoria. Lo que no viene cifrado sigue por el camino de siempre.
 */
@Mixin(SoundBufferLibrary.class)
public abstract class SoundBufferLibraryMixin {
    @Shadow
    @Final
    private ResourceProvider resourceManager;

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void tfclient$sealed(ResourceLocation loc, boolean looping, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (!SealedAudio.candidate(loc)) return;
        ResourceProvider resources = this.resourceManager;
        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            try {
                byte[] plain = SealedAudio.open(resources, loc);
                InputStream in = plain != null ? new ByteArrayInputStream(plain) : resources.open(loc);
                return (AudioStream) (looping ? new LoopingAudioStream(OggAudioStream::new, in) : new OggAudioStream(in));
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, Util.backgroundExecutor()));
    }
}
