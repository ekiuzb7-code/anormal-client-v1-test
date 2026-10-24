package com.lucidclient.suschunk;

import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SusChunkCache — persistent, packet-fed cache of every block-entity the
 * server leaks to the client, keyed by chunk.
 *
 * ── Why this exists ─────────────────────────────────────────────────
 * Hardcore servers (DonutSMP-style) run aggressive anti-xray (PaperMC Engine
 * Mode 2) that obfuscates normal block data and heavily culls chunks below
 * Y=0. The renderer can't draw what the server won't send. BUT the server is
 * forced to leak block-entity data (chests, hoppers, spawners, shulker boxes)
 * via {@code ChunkDataS2CPacket} and {@code BlockEntityUpdateS2CPacket} so the
 * client knows these tile entities exist at all.
 *
 * {@code ClientPlayNetworkHandlerMixin} intercepts those packets at HEAD —
 * BEFORE the vanilla engine touches them — and feeds the raw (pos, type, nbt)
 * triples into this cache. The cache then survives chunk unloads, so even
 * split-second leaks during lag spikes / teleports / fast flight are
 * permanently logged and can be projected back onto the world later.
 *
 * ── Storage ────────────────────────────────────────────────────────
 *   chunkKey (ChunkPos.toLong) → ChunkEntry
 *     ChunkEntry:
 *       blockEntities : Map<BlockPos, BlockEntityRecord>
 *       lastSeenMs    : long (when we last received data for this chunk)
 *
 *   BlockEntityRecord:
 *     type          : BlockEntityType<?>
 *     y             : int (cached for "below Y=0" anomaly detection)
 *     nbtHash       : int (NbtCompound.hashCode — change detection)
 *     firstSeenMs   : long
 *
 * The map is a {@link ConcurrentHashMap} because the network thread fires the
 * mixin handlers while the render thread reads. Per-entry maps are plain
 * HashMaps guarded by entry-level synchronization on update (the read path
 * in the ESP iterates a snapshot copy).
 *
 * ── Scoring ────────────────────────────────────────────────────────
 * Each chunk has a weighted suspicion score computed from its block-entity
 * types (see {@link #scoreChunk}). Shulker boxes and ender chests weigh
 * heaviest (high-value, mobile storage); plain chests/hoppers weigh less.
 *
 * ── Pruning ────────────────────────────────────────────────────────
 * The cache is bounded: chunks not seen for {@link #MAX_AGE_MS} are evicted
 * by {@link #prune()} (called from the SusChunkESP tick), so long sessions
 * don't leak memory. A chunk is NEVER pruned while it's still loaded — that
 * would discard data the server is still actively sending.
 */
public final class SusChunkCache {

    /** Max age (ms) a chunk's data is kept after we last received data for it. */
    private static final long MAX_AGE_MS = 10 * 60 * 1000L; // 10 minutes

    // ── Singleton ──────────────────────────────────────────────────
    private static final SusChunkCache INSTANCE = new SusChunkCache();
    public static SusChunkCache get() { return INSTANCE; }

    /** A single block-entity record persisted in the cache. */
    public static final class BlockEntityRecord {
        public final BlockEntityType<?> type;
        public final int y;
        public final int nbtHash;
        public final long firstSeenMs;
        public long lastSeenMs;

        BlockEntityRecord(BlockEntityType<?> type, int y, int nbtHash, long now) {
            this.type = type;
            this.y = y;
            this.nbtHash = nbtHash;
            this.firstSeenMs = now;
            this.lastSeenMs = now;
        }
    }

    /** Per-chunk aggregate. */
    public static final class ChunkEntry {
        public final Map<BlockPos, BlockEntityRecord> blockEntities = new HashMap<>();
        public long lastSeenMs = 0L;
        public boolean newChunkFlagged = false; // set when first observed
        /** Event-driven leak stats (sounds, block updates, piston events,
         *  particles, entity spawns). Each counter decays over time so a
         *  quiet base doesn't stay flagged forever from a single burst. */
        public final EventStats events = new EventStats();
    }

    /**
     * Event-driven leak counters for a chunk. The cache's
     * {@link #decayEvents(long)} rolls these off exponentially so the stats
     * reflect recent activity — a base that went quiet stops broadcasting
     * after a few minutes, while an actively-running farm keeps the score
     * pinned.
     *
     * Counters are intentionally simple longs (atomic not required — the
     * network thread writes via the mixin, the client thread reads for
     * scoring; worst case is a slightly stale read, never a torn 64-bit
     * value on the JVM).
     */
    public static final class EventStats {
        /** Total sounds observed in this chunk (any category). */
        public long soundEvents;
        /** Mechanical / redstone / mob sounds (the high-value flags). */
        public long mechanicalSounds;
        /** Block-state updates (BlockUpdateS2CPacket + ChunkDeltaUpdate). */
        public long blockUpdates;
        /** Piston / door / redstone BlockEventS2CPacket firings. */
        public long blockEvents;
        /** Particles spawned (torch smoke, redstone dust, mob flames). */
        public long particleEvents;
        /** Entities spawned (mob spawns, item frames, armor stands). */
        public long entitySpawns;
        /** Last-decay timestamp (ms) — used by decayEvents. Initialized to
         *  the creation time so the first decay tick doesn't compute an
         *  enormous elapsed delta (now - 0 ≈ 1.7 trillion ms) that would
         *  zero out every counter via Math.pow(0.5, huge) ≈ 0. */
        public long lastDecayMs = System.currentTimeMillis();

        /** Total weighted event score — used by SusChunkESP heuristics. */
        public float totalScore() {
            // Weighted: mechanical sounds + piston events are the strongest
            // base signatures. Block updates are noisy but high frequency =
            // farm. Particles are weak. Entity spawns are a soft signal.
            return mechanicalSounds * 2.0f
                + blockEvents * 1.5f
                + blockUpdates * 0.05f   // high-volume → low per-event weight
                + soundEvents * 0.5f
                + particleEvents * 0.2f
                + entitySpawns * 0.5f;
        }
    }

    private final ConcurrentHashMap<Long, ChunkEntry> chunks = new ConcurrentHashMap<>();
    private final Set<Long> currentlyLoaded = new HashSet<>();

    private SusChunkCache() {}

    /** Mark a chunk as currently loaded (so prune() won't evict it). */
    public void setLoaded(int cx, int cz, boolean loaded) {
        long key = ChunkPos.toLong(cx, cz);
        synchronized (currentlyLoaded) {
            if (loaded) currentlyLoaded.add(key);
            else currentlyLoaded.remove(key);
        }
    }

    /**
     * Record a block-entity observed from a {@code ChunkDataS2CPacket} — the
     * chunk's full block-entity list as sent on initial load. Called from
     * the mixin on the network thread.
     */
    public void recordFromChunkData(int cx, int cz, BlockPos pos,
                                     BlockEntityType<?> type, NbtCompound nbt) {
        long key = ChunkPos.toLong(cx, cz);
        ChunkEntry entry = chunks.computeIfAbsent(key, k -> new ChunkEntry());
        long now = System.currentTimeMillis();
        synchronized (entry) {
            entry.lastSeenMs = now;
            entry.blockEntities.put(pos.toImmutable(),
                new BlockEntityRecord(type, pos.getY(), nbt.hashCode(), now));
        }
    }

    /**
     * Record a block-entity observed from a {@code BlockEntityUpdateS2CPacket}
     * — a single-tile update (e.g. chest opened, hopper lock changed). These
     * fire even after the chunk has already been loaded, so they catch the
     * "player modified this area months ago" leaks the ESP heuristics rely on.
     */
    public void recordFromBlockEntityUpdate(BlockPos pos,
                                            BlockEntityType<?> type,
                                            NbtCompound nbt) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        long key = ChunkPos.toLong(cx, cz);
        ChunkEntry entry = chunks.computeIfAbsent(key, k -> new ChunkEntry());
        long now = System.currentTimeMillis();
        synchronized (entry) {
            entry.lastSeenMs = now;
            entry.blockEntities.put(pos.toImmutable(),
                new BlockEntityRecord(type, pos.getY(), nbt.hashCode(), now));
        }
    }

    // ── Event-driven leak recording (sounds, block updates, etc.) ────

    /**
     * Record a sound event observed from a {@code PlaySoundS2CPacket}. The
     * server must send sound coordinates so the client can pan the audio —
     * we read the X/Y/Z and increment the chunk's sound counters. Mechanical
     * sounds (pistons, redstone, mob damage) get a separate counter because
     * they're the strongest base signature.
     *
     * @param cx the chunk X (already computed by the caller — saves a /16)
     * @param cz the chunk Z
     * @param mechanical true if the sound is mechanical / redstone / mob-
     *                   related (the high-value category)
     */
    public void recordSoundEvent(int cx, int cz, boolean mechanical) {
        long key = ChunkPos.toLong(cx, cz);
        ChunkEntry entry = chunks.computeIfAbsent(key, k -> new ChunkEntry());
        long now = System.currentTimeMillis();
        synchronized (entry) {
            entry.lastSeenMs = now;
            entry.events.soundEvents++;
            if (mechanical) entry.events.mechanicalSounds++;
        }
    }

    /**
     * Record a block-state update observed from a
     * {@code BlockUpdateS2CPacket} or {@code ChunkDeltaUpdateS2CPacket}.
     * High-frequency updates in a chunk that should be static rock are a
     * farm/redstone beacon.
     */
    public void recordBlockUpdate(int cx, int cz) {
        long key = ChunkPos.toLong(cx, cz);
        ChunkEntry entry = chunks.computeIfAbsent(key, k -> new ChunkEntry());
        long now = System.currentTimeMillis();
        synchronized (entry) {
            entry.lastSeenMs = now;
            entry.events.blockUpdates++;
        }
    }

    /**
     * Record a {@code BlockEventS2CPacket} firing — piston extensions,
     * door opens, note blocks, redstone. These are the "moving parts" that
     * force the server to drop its anti-xray obfuscation for a split second
     * (the piston-desync bypass).
     */
    public void recordBlockEvent(int cx, int cz) {
        long key = ChunkPos.toLong(cx, cz);
        ChunkEntry entry = chunks.computeIfAbsent(key, k -> new ChunkEntry());
        long now = System.currentTimeMillis();
        synchronized (entry) {
            entry.lastSeenMs = now;
            entry.events.blockEvents++;
        }
    }

    /**
     * Record a particle spawn observed from a {@code ParticleS2CPacket}.
     * Underground torch smoke, redstone dust, mob flames, portal particles
     * — all flag a base even when block data is fully obfuscated.
     */
    public void recordParticleEvent(int cx, int cz) {
        long key = ChunkPos.toLong(cx, cz);
        ChunkEntry entry = chunks.computeIfAbsent(key, k -> new ChunkEntry());
        long now = System.currentTimeMillis();
        synchronized (entry) {
            entry.lastSeenMs = now;
            entry.events.particleEvents++;
        }
    }

    /**
     * Record an entity spawn observed from a {@code EntitySpawnS2CPacket}.
     * A cluster of cows packed underground, or item frames / armor stands
     * (storage markers), is a dead giveaway.
     */
    public void recordEntitySpawn(int cx, int cz) {
        long key = ChunkPos.toLong(cx, cz);
        ChunkEntry entry = chunks.computeIfAbsent(key, k -> new ChunkEntry());
        long now = System.currentTimeMillis();
        synchronized (entry) {
            entry.lastSeenMs = now;
            entry.events.entitySpawns++;
        }
    }

    /**
     * Decay event counters exponentially so a quiet base stops broadcasting
     * after a few minutes. Called from the SusChunkESP tick (client thread)
     * every ~5s. The half-life is ~2 minutes (each decay tick halves the
     * counters if they haven't been touched in that window).
     *
     * @param now current {@code System.currentTimeMillis()} value (passed in
     *             so callers can share one clock read).
     */
    public void decayEvents(long now) {
        // Decay fires once per ~10s per chunk — the lastDecayMs guard keeps
        // it from running every tick.
        for (ChunkEntry entry : chunks.values()) {
            synchronized (entry) {
                EventStats s = entry.events;
                long elapsed = now - s.lastDecayMs;
                if (elapsed < 10000L) continue;
                // Halve every 120s. With a 10s decay tick that's ~12 ticks,
                // so per-tick decay factor is 0.5^(elapsed/120000).
                double factor = Math.pow(0.5, elapsed / 120000.0);
                s.soundEvents = (long) (s.soundEvents * factor);
                s.mechanicalSounds = (long) (s.mechanicalSounds * factor);
                s.blockUpdates = (long) (s.blockUpdates * factor);
                s.blockEvents = (long) (s.blockEvents * factor);
                s.particleEvents = (long) (s.particleEvents * factor);
                s.entitySpawns = (long) (s.entitySpawns * factor);
                s.lastDecayMs = now;
            }
        }
    }

    /** Get the entry for a chunk, or null if never observed. */
    public ChunkEntry get(int cx, int cz) {
        return chunks.get(ChunkPos.toLong(cx, cz));
    }

    /** Total cached chunk count (for HUD/debug). */
    public int size() { return chunks.size(); }

    /** Total cached block-entity count. */
    public int totalBlockEntities() {
        int n = 0;
        for (ChunkEntry e : chunks.values()) {
            synchronized (e) { n += e.blockEntities.size(); }
        }
        return n;
    }

    /**
     * Snapshot of all cached chunks → packed long keys. Used by the ESP to
     * iterate for rendering without holding the live map.
     */
    public long[] snapshotKeys() {
        return chunks.keySet().stream().mapToLong(Long::longValue).toArray();
    }

    /**
     * Weighted suspicion score for a chunk — same weighting as
     * SusChunkFinder, exposed here so the ESP can score cached chunks even
     * after they're unloaded.
     */
    public float scoreChunk(int cx, int cz) {
        ChunkEntry entry = chunks.get(ChunkPos.toLong(cx, cz));
        if (entry == null) return 0f;
        float score = 0f;
        synchronized (entry) {
            for (BlockEntityRecord r : entry.blockEntities.values()) {
                score += weightOf(r.type);
            }
        }
        return score;
    }

    /** Count of containers (weighted-1 types) in a chunk. */
    public int containerCount(int cx, int cz) {
        ChunkEntry entry = chunks.get(ChunkPos.toLong(cx, cz));
        if (entry == null) return 0;
        int n = 0;
        synchronized (entry) {
            for (BlockEntityRecord r : entry.blockEntities.values()) {
                if (isContainer(r.type)) n++;
            }
        }
        return n;
    }

    /**
     * Evict chunks not seen for {@code MAX_AGE_MS} AND not currently loaded.
     * Called periodically from the SusChunkESP tick (client thread).
     */
    public void prune() {
        long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
        Iterator<Map.Entry<Long, ChunkEntry>> it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ChunkEntry> e = it.next();
            long key = e.getKey();
            synchronized (currentlyLoaded) {
                if (currentlyLoaded.contains(key)) continue;
            }
            ChunkEntry entry = e.getValue();
            synchronized (entry) {
                if (entry.lastSeenMs < cutoff) it.remove();
            }
        }
    }

    /** Wipe everything (called when SusChunkESP is disabled). */
    public void clear() {
        chunks.clear();
        synchronized (currentlyLoaded) { currentlyLoaded.clear(); }
    }

    // ── Classification helpers ──────────────────────────────────────

    /** True if the block-entity type is a storage container worth caching. */
    public static boolean isContainer(BlockEntityType<?> type) {
        return type == BlockEntityType.CHEST
            || type == BlockEntityType.TRAPPED_CHEST
            || type == BlockEntityType.ENDER_CHEST
            || type == BlockEntityType.SHULKER_BOX
            || type == BlockEntityType.BARREL
            || type == BlockEntityType.HOPPER
            || type == BlockEntityType.DISPENSER
            || type == BlockEntityType.DROPPER
            || type == BlockEntityType.FURNACE
            || type == BlockEntityType.BLAST_FURNACE
            || type == BlockEntityType.SMOKER
            || type == BlockEntityType.BREWING_STAND
            // Non-storage but high-value tile entities that anti-xray can't
            // easily hide — spawners (farms), beacons (bases), enchanting
            // tables, anvils (repair stations). Caching them lets the ESP
            // flag bases even when no chests are present.
            || type == BlockEntityType.MOB_SPAWNER
            || type == BlockEntityType.BEACON
            || type == BlockEntityType.ENCHANTING_TABLE;
    }

    /**
     * Storage-only weight for Krypton-style container counting — the same
     * scale as {@link #weightOf} but with the non-storage signals (spawner,
     * beacon, enchanting table) zeroed so a "5 containers" threshold means
     * five actual storage blocks, not a spawner plus two chests.
     */
    public static float storageWeightOf(BlockEntityType<?> type) {
        if (type == BlockEntityType.MOB_SPAWNER) return 0f;
        if (type == BlockEntityType.BEACON) return 0f;
        if (type == BlockEntityType.ENCHANTING_TABLE) return 0f;
        return weightOf(type);
    }

    /**
     * Weighted STORAGE score for a chunk (chests/hoppers/shulkers/etc. only —
     * no spawners/beacons). The Krypton SusChunk-style primary detection
     * signal: the server must send block-entity data even under anti-xray,
     * so this works on DonutSMP where palette scans see obfuscated stone.
     */
    public float storageScore(int cx, int cz) {
        ChunkEntry entry = chunks.get(ChunkPos.toLong(cx, cz));
        if (entry == null) return 0f;
        float score = 0f;
        synchronized (entry) {
            for (BlockEntityRecord r : entry.blockEntities.values()) {
                score += storageWeightOf(r.type);
            }
        }
        return score;
    }

    /** Plain count of storage-container records in a chunk (for alerts). */
    public int storageCount(int cx, int cz) {
        ChunkEntry entry = chunks.get(ChunkPos.toLong(cx, cz));
        if (entry == null) return 0;
        int n = 0;
        synchronized (entry) {
            for (BlockEntityRecord r : entry.blockEntities.values()) {
                if (storageWeightOf(r.type) > 0f) n++;
            }
        }
        return n;
    }

    /**
     * The [minY, maxY] range over the chunk's cached block-entity positions,
     * or {@code null} when the chunk has no cached records. Used by the ESP
     * to draw a Krypton-style wireframe box hugging the actual base location
     * vertically — "the stash is at Y -35..-20" instead of a whole column.
     */
    public int[] getYRange(int cx, int cz) {
        ChunkEntry entry = chunks.get(ChunkPos.toLong(cx, cz));
        if (entry == null) return null;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        synchronized (entry) {
            for (BlockEntityRecord r : entry.blockEntities.values()) {
                if (r.y < min) min = r.y;
                if (r.y > max) max = r.y;
            }
        }
        return min == Integer.MAX_VALUE ? null : new int[]{min, max};
    }

    /** Weighted value of a block-entity type for suspicion scoring. */
    public static float weightOf(BlockEntityType<?> type) {
        if (type == BlockEntityType.SHULKER_BOX) return 3.0f;
        if (type == BlockEntityType.ENDER_CHEST) return 3.0f;
        if (type == BlockEntityType.TRAPPED_CHEST) return 2.0f;
        if (type == BlockEntityType.BARREL) return 2.0f;
        if (type == BlockEntityType.CHEST) return 1.0f;
        if (type == BlockEntityType.HOPPER) return 1.0f;
        if (type == BlockEntityType.DISPENSER) return 1.0f;
        if (type == BlockEntityType.DROPPER) return 1.0f;
        if (type == BlockEntityType.FURNACE) return 0.5f;
        if (type == BlockEntityType.BLAST_FURNACE) return 0.5f;
        if (type == BlockEntityType.SMOKER) return 0.5f;
        if (type == BlockEntityType.BREWING_STAND) return 0.5f;
        if (type == BlockEntityType.MOB_SPAWNER) return 2.5f;
        if (type == BlockEntityType.BEACON) return 2.0f;
        if (type == BlockEntityType.ENCHANTING_TABLE) return 0.5f;
        return 0f;
    }
}