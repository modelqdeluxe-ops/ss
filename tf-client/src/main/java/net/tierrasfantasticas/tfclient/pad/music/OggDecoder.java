package net.tierrasfantasticas.tfclient.pad.music;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** OGG Vorbis con stb_vorbis (el mismo que usa Minecraft para sus sonidos). El archivo entero va a memoria nativa. */
final class OggDecoder implements AudioDecoder {
    private static final int FRAMES = 4096;
    private ByteBuffer data;
    private long handle;
    private ShortBuffer buffer;
    private final int rate, channels;

    OggDecoder(Path file, long startMs) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        data = MemoryUtil.memAlloc(bytes.length);
        data.put(bytes).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            handle = STBVorbis.stb_vorbis_open_memory(data, error, null);
            if (handle == 0) {
                MemoryUtil.memFree(data);
                data = null;
                throw new IOException("OGG no válido (error " + error.get(0) + ")");
            }
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(handle, info);
            rate = info.sample_rate();
            channels = Math.min(2, info.channels());
        }
        buffer = MemoryUtil.memAllocShort(FRAMES * channels);
        if (startMs > 0) STBVorbis.stb_vorbis_seek(handle, (int) Math.min(Integer.MAX_VALUE, startMs * rate / 1000));
    }

    /** Duración en milisegundos de un OGG (para la biblioteca). */
    static long durationMs(Path file) throws IOException {
        try (OggDecoder d = new OggDecoder(file, 0)) {
            int samples = STBVorbis.stb_vorbis_stream_length_in_samples(d.handle);
            return samples * 1000L / Math.max(1, d.rate);
        }
    }

    @Override
    public int sampleRate() {
        return rate;
    }

    @Override
    public int channels() {
        return channels;
    }

    @Override
    public byte[] next() {
        if (handle == 0) return null;
        buffer.clear();
        int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, buffer);
        if (frames <= 0) return null;
        byte[] out = new byte[frames * channels * 2];
        for (int i = 0, o = 0; i < frames * channels; i++) {
            short v = buffer.get(i);
            out[o++] = (byte) v;
            out[o++] = (byte) (v >> 8);
        }
        return out;
    }

    @Override
    public void close() {
        if (handle != 0) STBVorbis.stb_vorbis_close(handle);
        handle = 0;
        if (data != null) MemoryUtil.memFree(data);
        data = null;
        if (buffer != null) MemoryUtil.memFree(buffer);
        buffer = null;
    }
}
