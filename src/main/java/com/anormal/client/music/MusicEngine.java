package com.anormal.client.music;

import com.anormal.client.lucid.LucidLog;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plays audio files through the JDK mixer.
 *
 * <p>Everything is decoded to raw PCM and pushed into a {@link SourceDataLine} by one worker
 * thread, which is what makes pause, seek and volume behave the same for every format. MP3 goes
 * through JLayer (bundled in the jar), everything else through the JDK's own decoders.</p>
 *
 * <p>The worker never touches game state: it only reads volatile flags and updates a position
 * counter, so a slow decode can never stall the render thread.</p>
 */
public final class MusicEngine {

    /** Called on the worker thread when a track plays to its end. */
    public interface EndListener {
        void onEnd();
    }

    private static volatile MusicEngine instance;

    private Thread worker;
    private volatile boolean stopRequested;
    private volatile boolean paused;
    private volatile float volume = 0.8f;
    private volatile long seekTo = -1;

    private volatile long positionMs;
    private volatile long durationMs;
    private volatile MusicTrack current;
    private volatile EndListener endListener;

    public static MusicEngine get() {
        if (instance == null) {
            synchronized (MusicEngine.class) {
                if (instance == null) instance = new MusicEngine();
            }
        }
        return instance;
    }

    public MusicTrack current() {
        return current;
    }

    public boolean isPlaying() {
        return worker != null && worker.isAlive() && !paused;
    }

    public boolean isPaused() {
        return paused && worker != null && worker.isAlive();
    }

    public long positionMs() {
        return positionMs;
    }

    public long durationMs() {
        return durationMs;
    }

    public float volume() {
        return volume;
    }

    public void setVolume(float value) {
        this.volume = Math.max(0f, Math.min(1f, value));
    }

    public void setEndListener(EndListener listener) {
        this.endListener = listener;
    }

    public void play(MusicTrack track) {
        if (track == null) return;
        stop();

        current = track;
        durationMs = Math.max(0, track.durationMs());
        positionMs = 0;
        stopRequested = false;
        paused = false;
        seekTo = -1;

        worker = new Thread(() -> run(track), "anormal-music");
        worker.setDaemon(true);
        worker.start();
    }

    public void pause() {
        if (worker != null && worker.isAlive()) paused = true;
    }

    public void resume() {
        if (worker != null && worker.isAlive()) paused = false;
    }

    public void togglePause() {
        if (isPaused()) resume();
        else pause();
    }

    public void stop() {
        stopRequested = true;
        Thread w = worker;
        if (w != null) {
            try {
                // Never block the caller for long: play() runs from the render thread when the
                // user presses next, and a 400 ms join there would be a visible freeze. The old
                // worker sees the flag and closes its own line within a frame or two.
                w.join(60);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        worker = null;
        paused = false;
    }

    /** Requests a jump to {@code ms}. Applied by the worker on its next loop. */
    public void seek(long ms) {
        seekTo = Math.max(0, ms);
    }

    // ------------------------------------------------------------------
    //  Worker
    // ------------------------------------------------------------------

    private void run(MusicTrack track) {
        SourceDataLine line = null;
        PcmSource source = null;
        try {
            source = open(track.path);
            if (source == null) {
                LucidLog.LOGGER.warn("unsupported audio file: {}", track.path);
                return;
            }

            AudioFormat format = source.format();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, 64 * 1024);
            line.start();
            applyVolume(line);

            if (durationMs <= 0) {
                durationMs = source.estimateDurationMs();
                track.overrideDuration(durationMs);
            }

            byte[] buffer = new byte[8192];
            long written = 0;
            float appliedVolume = volume;
            int frameSize = Math.max(1, format.getFrameSize());
            float frameRate = format.getFrameRate() > 0 ? format.getFrameRate() : 44100f;

            while (!stopRequested) {
                // The slider only writes the field, so the line gain is refreshed here.
                if (volume != appliedVolume) {
                    appliedVolume = volume;
                    applyVolume(line);
                }

                if (paused) {
                    sleep(30);
                    continue;
                }

                long target = seekTo;
                if (target >= 0) {
                    seekTo = -1;
                    source.seek(target);
                    written = (long) (target / 1000.0 * frameRate) * frameSize;
                    positionMs = target;
                    line.flush();
                    continue;
                }

                int read = source.read(buffer);
                if (read < 0) break;

                line.write(buffer, 0, read);
                written += read;
                positionMs = (long) (written / (double) frameSize / frameRate * 1000.0);
            }

            if (!stopRequested) {
                // Let the tail of the buffer finish before reporting the end.
                line.drain();
                positionMs = durationMs;
                EndListener listener = endListener;
                if (listener != null) listener.onEnd();
            }
        } catch (Throwable error) {
            LucidLog.LOGGER.error("music playback failed", error);
        } finally {
            try {
                if (line != null) {
                    line.stop();
                    line.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (source != null) source.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private void applyVolume(SourceDataLine line) {
        try {
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                // -40 dB at silence, 0 dB at full - a straight linear slider feels wrong on ears.
                float db = volume <= 0.001f ? gain.getMinimum() : (float) (20.0 * Math.log10(volume));
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
            }
        } catch (Throwable ignored) {
        }
    }

    /** Re-applies the current volume; called when the slider moves mid-playback. */
    public void refreshVolume(SourceDataLine line) {
        applyVolume(line);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static PcmSource open(Path path) throws Exception {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".mp3")) return new Mp3Source(path);
        return new JdkSource(path);
    }

    // ------------------------------------------------------------------
    //  Decoders
    // ------------------------------------------------------------------

    private interface PcmSource extends AutoCloseable {
        AudioFormat format();

        int read(byte[] buffer) throws Exception;

        void seek(long ms) throws Exception;

        long estimateDurationMs();

        @Override
        void close();
    }

    /** WAV / AIFF / AU through the JDK. */
    private static final class JdkSource implements PcmSource {
        private final Path path;
        private AudioInputStream stream;
        private final AudioFormat format;

        JdkSource(Path path) throws Exception {
            this.path = path;
            this.stream = AudioSystem.getAudioInputStream(path.toFile());
            this.format = stream.getFormat();
        }

        @Override
        public AudioFormat format() {
            return format;
        }

        @Override
        public int read(byte[] buffer) throws Exception {
            return stream.read(buffer);
        }

        @Override
        public void seek(long ms) throws Exception {
            stream.close();
            stream = AudioSystem.getAudioInputStream(path.toFile());
            long frames = (long) (ms / 1000.0 * format.getFrameRate());
            long skipBytes = frames * format.getFrameSize();
            long skipped = 0;
            while (skipped < skipBytes) {
                long n = stream.skip(skipBytes - skipped);
                if (n <= 0) break;
                skipped += n;
            }
        }

        @Override
        public long estimateDurationMs() {
            try {
                long frames = stream.getFrameLength();
                return (long) (frames * 1000.0 / format.getFrameRate());
            } catch (Throwable ignored) {
                return 0;
            }
        }

        @Override
        public void close() {
            try {
                stream.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** MP3 through JLayer, converted to 16-bit signed PCM. */
    private static final class Mp3Source implements PcmSource {
        private final Path path;
        private Bitstream bitstream;
        private final Decoder decoder = new Decoder();
        private AudioFormat format;
        private short[] pending;
        private int pendingLen;
        private int pendingPos;

        Mp3Source(Path path) throws Exception {
            this.path = path;
            reopen();
        }

        private void reopen() throws Exception {
            if (bitstream != null) {
                try {
                    bitstream.close();
                } catch (Throwable ignored) {
                }
            }
            bitstream = new Bitstream(new BufferedInputStream(Files.newInputStream(path), 64 * 1024));
            pending = null;
            pendingLen = 0;
            pendingPos = 0;
        }

        @Override
        public AudioFormat format() {
            if (format == null) {
                // Sample rate/channels are only known after the first frame is decoded.
                try {
                    Header header = bitstream.readFrame();
                    if (header != null) {
                        SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                        pending = out.getBuffer();
                        pendingLen = out.getBufferLength();
                        pendingPos = 0;
                        format = new AudioFormat(decoder.getOutputFrequency(), 16,
                                decoder.getOutputChannels(), true, false);
                        bitstream.closeFrame();
                    }
                } catch (Throwable ignored) {
                }
                if (format == null) format = new AudioFormat(44100f, 16, 2, true, false);
            }
            return format;
        }

        @Override
        public int read(byte[] buffer) throws Exception {
            format();
            if (pending == null || pendingPos >= pendingLen) {
                Header header = bitstream.readFrame();
                if (header == null) return -1;
                SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                pending = out.getBuffer();
                pendingLen = out.getBufferLength();
                pendingPos = 0;
                bitstream.closeFrame();
                if (pendingLen <= 0) return 0;
            }

            int samples = Math.min((buffer.length / 2), pendingLen - pendingPos);
            for (int i = 0; i < samples; i++) {
                short s = pending[pendingPos + i];
                buffer[i * 2] = (byte) (s & 0xFF);
                buffer[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
            }
            pendingPos += samples;
            return samples * 2;
        }

        @Override
        public void seek(long ms) throws Exception {
            reopen();
            AudioFormat fmt = format();
            double targetSamples = ms / 1000.0 * fmt.getSampleRate();
            long decoded = 0;
            byte[] scratch = new byte[8192];
            while (decoded < targetSamples) {
                int n = read(scratch);
                if (n <= 0) break;
                decoded += n / fmt.getFrameSize();
            }
        }

        @Override
        public long estimateDurationMs() {
            int[] info = Mp3Probe.probe(path);
            if (info == null || info[0] <= 0) return 0;
            try {
                long size = Files.size(path);
                long audio = Math.max(0, size - info[1]);
                return (long) (audio * 8.0 / info[0] * 1000.0);
            } catch (Throwable ignored) {
                return 0;
            }
        }

        @Override
        public void close() {
            try {
                if (bitstream != null) bitstream.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
