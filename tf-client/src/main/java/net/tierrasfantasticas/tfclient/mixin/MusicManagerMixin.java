package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.tierrasfantasticas.tfclient.client.TFMusic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    @Shadow
    private SoundInstance currentMusic;

    @Shadow
    public abstract void stopPlaying();

    /** Mientras suena la música de TF, la música normal de Minecraft no empieza (y se corta si sonaba). */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tfclient$silenceVanillaMusic(CallbackInfo ci) {
        if (TFMusic.isActive()) {
            if (this.currentMusic != null) this.stopPlaying();
            ci.cancel();
        }
    }
}
