package com.anormal.client.music;

import com.anormal.client.lucid.LucidLog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * One audio file: its path plus whatever metadata could be read from it.
 *
 * <p>Everything except the path is <b>lazy</b>. Reading an ID3 header means touching the first
 * 512 KB of the file, and the queue can hold hundreds of tracks - doing that while building the
 * list would stall the frame that first draws the HUD. Instead each track is parsed the first time
 * its title/artist/cover is actually asked for, which is only ever the visible ones.</p>
 *
 * <p>When a file has no tags the filename is split on " - " so {@code Artist - Title.mp3} still
 * shows something useful instead of the raw file name.</p>
 */
public final class MusicTrack {

    public final Path path;

    private String title;
    private String artist;
    private String album;
    private byte[] coverBytes;
    private boolean parsed;

    private long durationMs = -1;

    public MusicTrack(Path path) {
        this.path = path;
    }

    /** Parses the tag block once; safe to call from any thread. */
    private synchronized void parse() {
        if (parsed) return;
        parsed = true;

        String fallbackName = path.getFileName().toString();
        int dot = fallbackName.lastIndexOf('.');
        if (dot > 0) fallbackName = fallbackName.substring(0, dot);

        String t = null;
        String a = null;
        String al = null;
        byte[] art = null;

        try {
            if (isMp3(path)) {
                Id3 id3 = Id3.read(path);
                if (id3 != null) {
                    t = id3.title;
                    a = id3.artist;
                    al = id3.album;
                    art = id3.cover;
                }
            }
        } catch (Throwable error) {
            LucidLog.LOGGER.warn("could not read tags of {}", path, error);
        }

        if (t == null || t.isBlank()) {
            int dash = fallbackName.indexOf(" - ");
            if (dash > 0) {
                if (a == null || a.isBlank()) a = fallbackName.substring(0, dash).trim();
                t = fallbackName.substring(dash + 3).trim();
            } else {
                t = fallbackName;
            }
        }
        if (a == null || a.isBlank()) a = "Unknown artist";
        if (al == null || al.isBlank()) al = "";

        this.title = t;
        this.artist = a;
        this.album = al;
        this.coverBytes = art;
    }

    public String title() {
        parse();
        return title;
    }

    public String artist() {
        parse();
        return artist;
    }

    public String album() {
        parse();
        return album;
    }

    public static boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".aiff")
                || name.endsWith(".aif") || name.endsWith(".au");
    }

    private static boolean isMp3(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3");
    }

    /** Cover art decoded from the embedded ID3 picture, or null when there is none. */
    public BufferedImage cover() {
        parse();
        if (coverBytes == null || coverBytes.length == 0) return null;
        try (InputStream in = new ByteArrayInputStream(coverBytes)) {
            return ImageIO.read(in);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Track length in milliseconds, or -1 when unknown.
     *
     * <p>MP3 length is estimated from the first frame's bitrate and the audio payload size; WAV and
     * friends are exact because the JDK reports frame count and frame rate.</p>
     */
    public long durationMs() {
        if (durationMs > 0) return durationMs;
        try {
            if (isMp3(path)) {
                long size = Files.size(path);
                int[] info = Mp3Probe.probe(path);
                if (info != null && info[0] > 0) {
                    long audioBytes = Math.max(0, size - info[1]);
                    durationMs = (long) (audioBytes * 8.0 / info[0] * 1000.0);
                }
            } else {
                try (javax.sound.sampled.AudioInputStream in =
                             javax.sound.sampled.AudioSystem.getAudioInputStream(path.toFile())) {
                    javax.sound.sampled.AudioFormat fmt = in.getFormat();
                    durationMs = (long) (in.getFrameLength() * 1000.0 / fmt.getFrameRate());
                }
            }
        } catch (Throwable ignored) {
        }
        return durationMs;
    }

    public void overrideDuration(long ms) {
        if (ms > 0) durationMs = ms;
    }

    @Override
    public String toString() {
        return title() + " - " + artist();
    }

    // ------------------------------------------------------------------
    //  Minimal ID3v2 reader
    // ------------------------------------------------------------------

    private static final class Id3 {
        String title;
        String artist;
        String album;
        byte[] cover;

        static Id3 read(Path path) throws Exception {
            byte[] data;
            try (InputStream in = Files.newInputStream(path)) {
                data = in.readNBytes(512 * 1024); // tags live at the head; 512k is plenty
            }
            if (data.length < 10 || data[0] != 'I' || data[1] != 'D' || data[2] != '3') return null;

            int major = data[3] & 0xFF;
            int flags = data[5] & 0xFF;
            int size = synchsafe(data, 6, 4);

            Id3 out = new Id3();
            int pos = 10;
            // Extended header (v2.3/2.4) sits before the frames.
            if ((flags & 0x40) != 0 && major >= 3) {
                int extSize = major == 4 ? synchsafe(data, pos, 4) : beInt(data, pos);
                pos += major == 4 ? extSize : extSize + 4;
            }

            int end = Math.min(data.length, 10 + size);
            while (pos + 10 <= end) {
                String id = new String(data, pos, 4, StandardCharsets.ISO_8859_1);
                if (id.charAt(0) == 0) break;

                int frameSize = major == 4 ? synchsafe(data, pos + 4, 4) : beInt(data, pos + 4);
                if (frameSize <= 0 || pos + 10 + frameSize > end) break;

                int body = pos + 10;
                switch (id) {
                    case "TIT2" -> out.title = text(data, body, frameSize);
                    case "TPE1" -> out.artist = text(data, body, frameSize);
                    case "TALB" -> out.album = text(data, body, frameSize);
                    case "APIC" -> out.cover = picture(data, body, frameSize);
                    default -> {
                    }
                }
                pos = body + frameSize;
            }
            return out;
        }

        private static int synchsafe(byte[] d, int off, int len) {
            int v = 0;
            for (int i = 0; i < len; i++) {
                v = (v << 7) | (d[off + i] & 0x7F);
            }
            return v;
        }

        private static int beInt(byte[] d, int off) {
            return ((d[off] & 0xFF) << 24) | ((d[off + 1] & 0xFF) << 16)
                    | ((d[off + 2] & 0xFF) << 8) | (d[off + 3] & 0xFF);
        }

        /** Decodes a text frame: first byte is the encoding, the rest is the string. */
        private static String text(byte[] d, int off, int len) {
            if (len < 2) return null;
            int enc = d[off] & 0xFF;
            int start = off + 1;
            int count = len - 1;
            String value;
            switch (enc) {
                case 1 -> value = new String(d, start, count, StandardCharsets.UTF_16);
                case 2 -> value = new String(d, start, count, StandardCharsets.UTF_16BE);
                case 3 -> value = new String(d, start, count, StandardCharsets.UTF_8);
                default -> value = new String(d, start, count, StandardCharsets.ISO_8859_1);
            }
            value = value.replace("\u0000", "").trim();
            return value.isEmpty() ? null : value;
        }

        /**
         * Decodes an APIC frame: encoding, mime (nul-terminated), picture type, description
         * (nul-terminated), then the image bytes.
         */
        private static byte[] picture(byte[] d, int off, int len) {
            try {
                int enc = d[off] & 0xFF;
                int p = off + 1;
                int end = off + len;

                while (p < end && d[p] != 0) p++;   // mime
                p++;
                p++;                                 // picture type
                if (enc == 1 || enc == 2) {
                    while (p + 1 < end && !(d[p] == 0 && d[p + 1] == 0)) p += 2;
                    p += 2;
                } else {
                    while (p < end && d[p] != 0) p++;
                    p++;
                }
                if (p >= end) return null;
                byte[] img = new byte[end - p];
                System.arraycopy(d, p, img, 0, img.length);
                return img;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
