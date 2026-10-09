package net.tierrasfantasticas.tfclient.pad.music;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Una canción sonando en el motor de sonido del juego: sin posición (se oye igual mire donde mire), en la categoría
 * principal (el volumen general del juego y el de la app), en streaming desde {@link MusicStream} (Forge deja dar el
 * stream con getStream). No necesita sounds.json: el «sonido» se inventa en resolve.
 */
final class MusicSound extends AbstractSoundInstance implements TickableSoundInstance {
    private static final ResourceLocation ID = new ResourceLocation(TFClient.MOD_ID, "pad_musica");

    final MusicLibrary.Track track;
    final long startMs;
    volatile MusicStream stream;
    volatile Throwable failure;
    private boolean stopped;

    MusicSound(MusicLibrary.Track track, long startMs, float volume) {
        super(ID, SoundSource.MASTER, SoundInstance.createUnseededRandom());
        this.track = track;
        this.startMs = startMs;
        this.volume = volume;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.looping = false;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        this.sound = new Sound(ID.toString(), ConstantFloat.of(1F), ConstantFloat.of(1F), 1, Sound.Type.FILE, true, false, 16);
        WeighedSoundEvents events = new WeighedSoundEvents(ID, null);
        events.addSound(this.sound);
        return events;
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                MusicStream s = new MusicStream(AudioDecoder.open(MusicLibrary.file(track), startMs), startMs);
                stream = s;
                return (AudioStream) s;
            } catch (Exception e) {
                failure = e;
                throw new CompletionException(e);
            }
        }, Util.backgroundExecutor());
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    void setVolume(float v) {
        this.volume = v;
    }

    void stopNow() {
        stopped = true;
    }

    @Override
    public void tick() {
        this.volume = MusicPlayer.volume();
    }
}
