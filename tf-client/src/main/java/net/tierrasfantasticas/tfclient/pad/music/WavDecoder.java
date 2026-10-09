package net.tierrasfantasticas.tfclient.pad.music;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** WAV sin comprimir: PCM de 8, 16, 24 o 32 bits o float de 32; más de dos canales se quedan en los dos primeros. */
final class WavDecoder implements AudioDecoder {
    private final DataInputStream in;
    private final int rate, channels, inChannels, bits;
    private final boolean floats;
    private long left;

    WavDecoder(Path file, long startMs) throws IOException {
        in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16));
        try {
            in.skipNBytes(12);
            int fmt = -1, ch = 0, sr = 0, bps = 0;
            while (true) {
                byte[] id = in.readNBytes(4);
                if (id.length < 4) throw new IOException("WAV sin datos");
                long size = Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
                String name = new String(id, java.nio.charset.StandardCharsets.US_ASCII);
                if (name.equals("fmt ")) {
                    fmt = Short.reverseBytes(in.readShort()) & 0xFFFF;
                    ch = Short.reverseBytes(in.readShort()) & 0xFFFF;
                    sr = Integer.reverseBytes(in.readInt());
                    in.skipNBytes(6);
                    bps = Short.reverseBytes(in.readShort()) & 0xFFFF;
                    if (fmt == 0xFFFE && size >= 26) {
                        in.skipNBytes(8);
                        fmt = Short.reverseBytes(in.readShort()) & 0xFFFF;
                        in.skipNBytes(size - 26);
                    } else {
                        in.skipNBytes(size - 16);
                    }
                    if ((size & 1) == 1) in.skipNBytes(1);
                } else if (name.equals("data")) {
                    left = size;
                    break;
                } else {
                    in.skipNBytes(size + (size & 1));
                }
            }
            if ((fmt != 1 && fmt != 3) || ch < 1 || sr < 4000 || (bps != 8 && bps != 16 && bps != 24 && bps != 32))
                throw new IOException("WAV no soportado (formato " + fmt + ", " + bps + " bits)");
            rate = sr;
            inChannels = ch;
            channels = Math.min(2, ch);
            bits = bps;
            floats = fmt == 3;
            if (floats && bps != 32) throw new IOException("WAV float no soportado");
            long frame = (long) ch * (bps / 8);
            long skip = Math.min(left / frame, startMs * sr / 1000) * frame;
            in.skipNBytes(skip);
            left -= skip;
        } catch (IOException e) {
            in.close();
            throw e;
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
    public byte[] next() throws IOException {
        int bytesPer = bits / 8, frame = bytesPer * inChannels;
        int frames = (int) Math.min(4096, left / frame);
        if (frames <= 0) return null;
        byte[] raw = in.readNBytes(frames * frame);
        frames = raw.length / frame;
        if (frames == 0) return null;
        left -= (long) frames * frame;
        byte[] out = new byte[frames * channels * 2];
        int o = 0;
        for (int f = 0; f < frames; f++) {
            for (int c = 0; c < channels; c++) {
                int p = f * frame + c * bytesPer;
                int v;
                if (floats) {
                    int bitsF = (raw[p] & 0xFF) | (raw[p + 1] & 0xFF) << 8 | (raw[p + 2] & 0xFF) << 16 | raw[p + 3] << 24;
                    v = (int) Math.max(-32768, Math.min(32767, Float.intBitsToFloat(bitsF) * 32767));
                } else if (bits == 8) {
                    v = ((raw[p] & 0xFF) - 128) << 8;
                } else {
                    v = raw[p + bytesPer - 1] << 8 | (raw[p + bytesPer - 2] & 0xFF); // los dos bytes altos
                }
                out[o++] = (byte) v;
                out[o++] = (byte) (v >> 8);
            }
        }
        return out;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
