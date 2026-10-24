package com.anormal.client.music;

import com.anormal.client.lucid.LucidLog;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * The playlist: what is queued, what is playing, and how it advances.
 *
 * <p>Kept separate from the module so the HUD and the library screen drive the same state. The
 * queue is persisted as a plain list of paths, so a restart keeps your songs without embedding
 * anything binary in the config.</p>
 */
public final class MusicLibrary {

    public enum Loop {
        OFF, ALL, ONE
    }

    private static final MusicLibrary INSTANCE = new MusicLibrary();

    private final List<MusicTrack> tracks = new ArrayList<>();
    private final Deque<Integer> history = new ArrayDeque<>();
    private final Random random = new Random();

    private int index = -1;
    private boolean shuffle;
    private Loop loop = Loop.OFF;
    private boolean loaded;

    private MusicLibrary() {
    }

    public static MusicLibrary get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    //  Queue
    // ------------------------------------------------------------------

    public List<MusicTrack> tracks() {
        ensureLoaded();
        return Collections.unmodifiableList(tracks);
    }

    public int index() {
        return index;
    }

    public MusicTrack current() {
        ensureLoaded();
        if (index < 0 || index >= tracks.size()) return null;
        return tracks.get(index);
    }

    public boolean isEmpty() {
        ensureLoaded();
        return tracks.isEmpty();
    }

    public void add(Path path) {
        ensureLoaded();
        if (addInternal(path)) save();
    }

    /** Adds without touching the queue file, so a folder import writes it once. */
    private boolean addInternal(Path path) {
        if (!Files.isRegularFile(path) || !MusicTrack.isSupported(path)) return false;
        for (MusicTrack t : tracks) {
            if (t.path.equals(path)) return false;
        }
        tracks.add(new MusicTrack(path));
        return true;
    }

    /** Adds every supported audio file directly inside {@code folder} (non-recursive). */
    public int addFolder(Path folder) {
        ensureLoaded();
        if (!Files.isDirectory(folder)) return 0;
        int before = tracks.size();
        try (var stream = Files.list(folder)) {
            // One save for the whole import instead of one per file.
            stream.filter(MusicTrack::isSupported).sorted().forEach(this::addInternal);
        } catch (Throwable error) {
            LucidLog.LOGGER.warn("could not list {}", folder, error);
        }
        save();
        return tracks.size() - before;
    }

    public void remove(int at) {
        ensureLoaded();
        if (at < 0 || at >= tracks.size()) return;
        boolean wasCurrent = at == index;
        tracks.remove(at);
        if (wasCurrent) {
            MusicEngine.get().stop();
            index = Math.min(index, tracks.size() - 1);
        } else if (at < index) {
            index--;
        }
        save();
    }

    public void clear() {
        MusicEngine.get().stop();
        tracks.clear();
        history.clear();
        index = -1;
        save();
    }

    // ------------------------------------------------------------------
    //  Playback
    // ------------------------------------------------------------------

    public void playIndex(int at) {
        ensureLoaded();
        if (at < 0 || at >= tracks.size()) return;
        if (index >= 0 && index != at) history.push(index);
        index = at;
        MusicEngine.get().play(tracks.get(at));
    }

    public void playCurrentOrFirst() {
        ensureLoaded();
        if (tracks.isEmpty()) return;
        if (index < 0 || index >= tracks.size()) playIndex(0);
        else MusicEngine.get().play(tracks.get(index));
    }

    public void next(boolean fromUser) {
        ensureLoaded();
        if (tracks.isEmpty()) return;

        // Loop ONE repeats the same track only when it ends by itself; pressing next still moves on.
        if (!fromUser && loop == Loop.ONE) {
            playIndex(index);
            return;
        }

        int nextIndex;
        if (shuffle && tracks.size() > 1) {
            do {
                nextIndex = random.nextInt(tracks.size());
            } while (nextIndex == index);
        } else {
            nextIndex = index + 1;
            if (nextIndex >= tracks.size()) {
                if (loop == Loop.OFF && !fromUser) {
                    MusicEngine.get().stop();
                    index = 0;
                    return;
                }
                nextIndex = 0;
            }
        }
        playIndex(nextIndex);
    }

    public void previous() {
        ensureLoaded();
        if (tracks.isEmpty()) return;
        if (!history.isEmpty()) {
            int at = history.pop();
            if (at >= 0 && at < tracks.size()) {
                index = at;
                MusicEngine.get().play(tracks.get(at));
                return;
            }
        }
        playIndex(index <= 0 ? tracks.size() - 1 : index - 1);
    }

    /** Called by the engine when a track finishes on its own. */
    public void onTrackEnd() {
        if (tracks.isEmpty()) return;
        next(false);
    }

    // ------------------------------------------------------------------
    //  Modes
    // ------------------------------------------------------------------

    public boolean shuffle() {
        return shuffle;
    }

    public void toggleShuffle() {
        shuffle = !shuffle;
    }

    public Loop loop() {
        return loop;
    }

    public void cycleLoop() {
        loop = switch (loop) {
            case OFF -> Loop.ALL;
            case ALL -> Loop.ONE;
            case ONE -> Loop.OFF;
        };
    }

    // ------------------------------------------------------------------
    //  Persistence
    // ------------------------------------------------------------------

    private static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("config").resolve("anormal").resolve("music.txt");
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = file();
            if (!Files.isRegularFile(f)) return;
            for (String line : Files.readAllLines(f)) {
                if (line.isBlank()) continue;
                try {
                    Path p = Path.of(line.trim());
                    if (Files.isRegularFile(p) && MusicTrack.isSupported(p)) {
                        tracks.add(new MusicTrack(p));
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable error) {
            LucidLog.LOGGER.warn("could not load the music queue", error);
        }
    }

    private void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            List<String> lines = new ArrayList<>(tracks.size());
            for (MusicTrack t : tracks) lines.add(t.path.toString());
            Files.write(f, lines);
        } catch (Throwable error) {
            LucidLog.LOGGER.warn("could not save the music queue", error);
        }
    }
}
