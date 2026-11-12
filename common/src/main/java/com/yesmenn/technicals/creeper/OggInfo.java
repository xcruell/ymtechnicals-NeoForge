package com.yesmenn.technicals.creeper;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Reads sample rate, channel count and length of an Ogg Vorbis file from its first page (identification header) and
 * its last page (granule position = number of samples), without decoding it.
 */
public final class OggInfo {
    private static final int HEAD_BYTES = 4096;
    private static final int TAIL_BYTES = 65536;

    public record Info(int sampleRate, int channels, long samples) {
        public int seconds() {
            return sampleRate <= 0 ? 0 : (int) Math.ceil(samples / (double) sampleRate);
        }

        public long ticks() {
            return sampleRate <= 0 ? 0 : samples * 20L / sampleRate;
        }
    }

    private OggInfo() {
    }

    public static Info read(Path file) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            long length = input.length();
            byte[] head = new byte[(int) Math.min(HEAD_BYTES, length)];
            input.readFully(head);
            byte[] tail = new byte[(int) Math.min(TAIL_BYTES, length)];
            input.seek(length - tail.length);
            input.readFully(tail);
            return parse(head, tail);
        }
    }

    public static Info read(byte[] data) throws IOException {
        int tailLength = Math.min(TAIL_BYTES, data.length);
        byte[] tail = new byte[tailLength];
        System.arraycopy(data, data.length - tailLength, tail, 0, tailLength);
        return parse(data, tail);
    }

    private static Info parse(byte[] head, byte[] tail) throws IOException {
        if (head.length < 28 || !isPage(head, 0)) {
            throw new IOException("Not an Ogg file");
        }
        int packet = 27 + (head[26] & 0xFF);
        if (head.length < packet + 16 || head[packet] != 1
                || !"vorbis".equals(new String(head, packet + 1, 6, StandardCharsets.US_ASCII))) {
            throw new IOException("Not an Ogg Vorbis file");
        }
        int channels = head[packet + 11] & 0xFF;
        int sampleRate = (int) littleEndian(head, packet + 12, 4);
        long samples = -1;
        // The last page with a granule position holds the total sample count.
        for (int offset = tail.length - 27; offset >= 0 && samples < 0; offset--) {
            if (isPage(tail, offset)) {
                long granule = littleEndian(tail, offset + 6, 8);
                if (granule >= 0) {
                    samples = granule;
                }
            }
        }
        if (channels <= 0 || sampleRate <= 0 || samples < 0) {
            throw new IOException("Damaged Ogg Vorbis file");
        }
        return new Info(sampleRate, channels, samples);
    }

    private static boolean isPage(byte[] data, int offset) {
        return offset + 27 <= data.length && data[offset] == 'O' && data[offset + 1] == 'g' && data[offset + 2] == 'g'
                && data[offset + 3] == 'S' && data[offset + 4] == 0;
    }

    private static long littleEndian(byte[] data, int offset, int bytes) {
        long value = 0;
        for (int i = bytes - 1; i >= 0; i--) {
            value = value << 8 | data[offset + i] & 0xFF;
        }
        return value;
    }
}
