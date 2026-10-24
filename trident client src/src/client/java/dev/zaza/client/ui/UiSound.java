package dev.zaza.client.ui;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Clip;
import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class UiSound {
    private static final int SAMPLE_RATE = 22050;

    private UiSound() {}

    public static void play(ZazaSettings.SoundSet set, String kind) {
        int volume = ZazaSettings.volume();
        if (volume <= 0) return;
        byte[] pcm = switch (kind) {
            case "open" -> tone(set, 520, 760, 90, 0.55f);
            case "close" -> tone(set, 640, 380, 110, 0.5f);
            case "switch" -> tone(set, 440, 590, 70, 0.4f);
            default -> tone(set, 880, 880, 40, 0.3f);
        };
        float gain = volume / 100f * 0.6f;
        Thread thread = new Thread(() -> playPcm(pcm, gain), "zaza-sound");
        thread.setDaemon(true);
        thread.start();
    }

    private static byte[] tone(ZazaSettings.SoundSet set, int from, int to, int millis, float level) {
        int frames = SAMPLE_RATE * millis / 1000;
        ByteArrayOutputStream out = new ByteArrayOutputStream(frames * 2);
        float sweep = switch (set) {
            case GLASS -> 1.0f;
            case CLICK -> 0.55f;
            case SYNTH -> 1.6f;
        };
        for (int i = 0; i < frames; i++) {
            float t = (float) i / frames;
            float freq = (from + (to - from) * t) * sweep;
            double angle = 2.0 * Math.PI * freq * i / SAMPLE_RATE;
            double attack = Math.min(1.0, i / (0.008f * SAMPLE_RATE));
            double release = Math.pow(1.0 - t, switch (set) {
                case GLASS -> 2.2;
                case CLICK -> 4.0;
                case SYNTH -> 1.4;
            });
            double envelope = attack * release;
            double wave = switch (set) {
                case GLASS -> Math.sin(angle) + 0.35 * Math.sin(angle * 2.7) + 0.18 * Math.sin(angle * 5.1);
                case CLICK -> Math.sin(angle) * (1.0 - 0.6 * t);
                case SYNTH -> Math.sin(angle) + 0.25 * Math.sin(angle * 0.5) + 0.2 * Math.sin(angle * 3.0);
            };
            short sample = (short) Math.clamp(Math.round(wave * envelope * level * 32767.0), -32768, 32767);
            out.write(sample & 0xFF);
            out.write((sample >> 8) & 0xFF);
        }
        return out.toByteArray();
    }

    private static void playPcm(byte[] pcm, float gain) {
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            try (Clip clip = AudioSystem.getClip()) {
                clip.open(format, pcm, 0, pcm.length);
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float db = (float) (20.0 * Math.log10(Math.max(gain, 0.0001)));
                    control.setValue(Math.clamp(db, control.getMinimum(), control.getMaximum()));
                }
                clip.start();
                Thread.sleep(Math.max(1, pcm.length / (SAMPLE_RATE / 1000)) + 60);
            }
        } catch (Exception ignored) {
        }
    }
}
