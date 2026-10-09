package net.tierrasfantasticas.tfclient.pad.music;

import java.io.IOException;

/**
 * Un decodificador que mezcla los dos canales en uno: el motor de sonido solo sitúa en el mundo los sonidos mono (el
 * altavoz se oye desde el jugador que lo lleva; en estéreo se oiría «en la cabeza» de todos).
 */
final class MonoDecoder implements AudioDecoder {
    private final AudioDecoder in;

    MonoDecoder(AudioDecoder in) {
        this.in = in;
    }

    @Override
    public int sampleRate() {
        return in.sampleRate();
    }

    @Override
    public int channels() {
        return 1;
    }

    @Override
    public byte[] next() throws IOException {
        byte[] b = in.next();
        if (b == null || in.channels() == 1) return b;
        int ch = in.channels(), frames = b.length / (2 * ch);
        byte[] out = new byte[frames * 2];
        for (int f = 0; f < frames; f++) {
            int sum = 0;
            for (int c = 0; c < ch; c++) {
                int i = (f * ch + c) * 2;
                sum += (short) ((b[i] & 0xFF) | (b[i + 1] << 8));
            }
            int v = sum / ch;
            out[f * 2] = (byte) v;
            out[f * 2 + 1] = (byte) (v >> 8);
        }
        return out;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
