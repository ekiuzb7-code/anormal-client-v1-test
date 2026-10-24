package com.anormal.client.music;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads the bitrate and tag size of an MP3 so its length can be estimated without decoding it. */
public final class Mp3Probe {

    private Mp3Probe() {
    }

    /**
     * @return {@code {bitrateKbps, tagBytes}}, or null when the file is not a readable MP3
     */
    public static int[] probe(Path path) {
        try {
            int tagBytes = id3Size(path);
            int bitrate = 0;
            // JLayer's Bitstream is not AutoCloseable, so it is closed by hand.
            InputStream raw = null;
            Bitstream bitstream = null;
            try {
                raw = Files.newInputStream(path);
                bitstream = new Bitstream(new BufferedInputStream(raw));
                Header header = bitstream.readFrame();
                if (header == null) return null;
                bitrate = header.bitrate();
                bitstream.closeFrame();
            } finally {
                try {
                    if (bitstream != null) bitstream.close();
                } catch (Throwable ignored) {
                }
                try {
                    if (raw != null) raw.close();
                } catch (Throwable ignored) {
                }
            }
            return new int[]{bitrate, tagBytes};
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Total bytes occupied by the ID3v2 tag at the head of the file (0 when absent). */
    public static int id3Size(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] head = in.readNBytes(10);
            if (head.length < 10 || head[0] != 'I' || head[1] != 'D' || head[2] != '3') return 0;
            int size = ((head[6] & 0x7F) << 21) | ((head[7] & 0x7F) << 14)
                    | ((head[8] & 0x7F) << 7) | (head[9] & 0x7F);
            int flags = head[5] & 0xFF;
            int footer = (flags & 0x10) != 0 ? 10 : 0;
            return 10 + size + footer;
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
