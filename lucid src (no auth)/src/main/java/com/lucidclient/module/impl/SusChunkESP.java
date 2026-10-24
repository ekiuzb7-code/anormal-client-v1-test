package com.lucidclient.module.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.ColorSetting;
import com.lucidclient.module.setting.EnumSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.IntSetting;
import com.lucidclient.LucidClient;
import com.lucidclient.suschunk.ChunkHeuristics;
import com.lucidclient.suschunk.SusChunkCache;
import com.lucidclient.suschunk.SusChunkCache.ChunkEntry;
import com.lucidclient.suschunk.SusChunkCache.BlockEntityRecord;
import com.lucidclient.ui.util.WorldRender3D;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SusChunkESP — Highlights chunks with high storage density using a
 * depth-tested 3D world overlay (the chunk box is drawn IN the world, not as
 * a 2D screen overlay, so it sits naturally on top of the terrain instead of
 * flashing a dark color across the screen).
 *
 * ── Anti-xray bypass (DonutSMP / PaperMC Engine Mode 2) ─────────────
 * Hardcore servers obfuscate block data and cull chunks below Y=0 to blind
 * the renderer. This module doesn't rely on the renderer at all — it reads
 * from {@link SusChunkCache}, which is fed by a packet-interception mixin
 * ({@code ClientPlayNetworkHandlerMixin}) that sniffs every
 * {@code ChunkDataS2CPacket} and {@code BlockEntityUpdateS2CPacket} at HEAD,
 * BEFORE the vanilla engine touches them. Anti-xray can't hide block entities
 * (chests, hoppers, spawners, shulker boxes) because the server must send
 * their NBT data so the client knows they exist. Cached leaks persist beyond
 * chunk unloads, so split-second data leaks during lag spikes / teleports /
 * fast flight are permanently logged and projected back onto the world.
 *
 * ── Detection heuristics ───────────────────────────────────────────
 * Each scan tick we score every cached + loaded chunk within render distance:
 *
 *   1. Container density (weighted): ShulkerBox/EnderChest=3, TrappedChest/
 *      Barrel=2, Chest/Hopper/Dispenser/Dropper=1, Furnace/BrewingStand=0.5,
 *      MobSpawner=2.5, Beacon=2. A chunk is flagged when its own score ≥ Min
 *      Score.
 *   2. Cluster detection (optional): a chunk is also flagged when the sum of
 *      scores within ±Cluster Radius chunks ≥ Min Score × (2·radius+1) — a
 *      stash spread across several chunks.
 *   3. Below-Y=0 anomaly (optional): flag any chunk with cached block
 *      entities below Y=0 — the "player modified the deepslate" signature
 *      that anti-xray tries hardest to hide. This catches bases even when
 *      their container count is below the threshold.
 *   4. Spawner presence (optional): a single mob spawner is a strong base /
 *      farm indicator. Flag any chunk containing a spawner.
 *   5. Section-scan heuristics (optional, live chunks only — see {@link
 *      ChunkHeuristics}): walks each loaded chunk's block-state palette on a
 *      sparse stride and tallies four classic base-hunter signals:
 *        a. Unnatural-block palette (obsidian, planks, glass, workstations,
 *           redstone, beds, doors, fences) — the Wurst BaseFinder approach.
 *        b. Air-pocket anomalies (excavated rooms below Y=0 — anti-xray
 *           doesn't obfuscate air correctly).
 *        c. Light sources underground (torches, lanterns, glowstone in
 *           stone — players light their bases).
 *        d. Heightmap inconsistencies (server-computed heightmap not matching
 *           the actual block layout — leaks edits through anti-xray).
 *      These four are the signals Krypton's Sus Chunk Finder uses; they're
 *      anti-xray-resistant because air + heightmaps are computed server-side
 *      from the real data, not the obfuscated palette.
 *
 * ── Rendering ──────────────────────────────────────────────────────
 * Drawn through {@link WorldRenderEvents#END_MAIN} via {@link LucidClient}
 * which dispatches to {@link #renderWorld(...)}. Each flagged chunk is drawn
 * as a <b>flat horizontal FILLED box</b> (a translucent red slab) covering the
 * exact 16&#xd7;16 chunk footprint — via {@link WorldRender3D#drawFilledBox}
 * on a custom no-depth QUADS layer. The fill is translucent (alpha capped
 * ~33%) so it highlights the chunk with its hue without hiding the terrain
 * or the base inside it, and depth test is disabled so the slab is visible
 * through terrain — the X-ray-through-walls quality.
 *
 * <p>The slab is CHUNK-ALIGNED, not camera-sliding: the X/Z footprint is the
 * exact 16&#xd7;16 chunk extent (cx&#xb7;16 .. cx&#xb7;16+16). Its Y level is set by
 * the <b>Y Mode</b> setting:
 * <ul>
 *   <li><b>Terrain</b> (default) — for LIVE chunks the slab rests at (highest
 *       non-air block Y + Top Offset), so it sits ON the land; for CACHED /
 *       unloaded chunks it rests at (world bottom + Fallback Height).</li>
 *   <li><b>Fixed</b> — every slab rests at exactly Fixed Y, ignoring the chunk's
 *       heightmap entirely — all flagged chunks align on one level plane.</li>
 * </ul>
 * {@code Box Thickness} sets the slab's vertical thickness (1 = a flat sheet).
 *
 * ── Anti-cheat evasion (rate limiting + passive scanning) ───────────
 * This module is PASSIVE — it only reads data the server has already pushed
 * to the client (chunk packets, block-entity packets, sound/particle/event
 * packets). It never sends extra chunk requests, never moves the player, and
 * never pings the server for data. The staggered 10-tick scan cadence (not
 * every tick) keeps CPU/throughput low so movement looks natural — the
 * server's anti-cheat sees a normal player, not a scanner. The scan interval
 * is also configurable (Scan Interval) so on paranoid servers you can slow
 * it down to blend the checks with regular player loading patterns. The
 * cache persists across chunk unloads, so split-second data leaks captured
 * during a single lag spike / teleport / fast-flight pass are kept
 * permanently and projected back onto the world — no need to re-trigger.
 *
 * ── Client-side coordinate logging ──────────────────────────────────
 * With "Log Coords" on, every newly-flagged chunk's coordinates are written
 * to the log + chat (once per chunk — no spam) so you can revisit / inspect
 * flagged areas later. The flag set persists across module toggles (the
 * cache self-prunes by age), so flagged coordinates from this session stay
 * flagged next time you re-enable.
 *
 * Settings (right-click → settings card):
 *   Min Score        — weighted score threshold to flag a chunk
 *   Cluster Radius   — chunk-cluster flag radius (0 disables cluster check)
 *   Below Y=0 Anomaly — flag chunks with deep block entities
 *   Spawner Flag      — flag any chunk containing a mob spawner
 *   Unnatural Blocks — flag chunks with player-building blocks in palette
 *   Air Pockets      — flag chunks with air-pocket anomalies below Y=0
 *   Light Sources    — flag chunks with non-natural light sources underground
 *   Heightmap Inconsistency — flag chunks with heightmap mismatches
 *   Scan Threshold   — min section-scan score to flag (0 = any signal)
 *   Event Threshold  — min event-leak score to flag (0 = off)
 *   Sound Events / Block Update Sniff / Piston Desync / Entity Density /
 *     Particle Sniff — individual event-leak gates
 *   Color            — overlay color
 *   Y Mode           — Terrain (slab follows the chunk's surface) or Fixed (every slab at Fixed Y)
 *   Fixed Y          — the Y level for every slab when Y Mode = Fixed
 *   Top Offset       — how far above the chunk's surface the flat slab sits (Terrain mode)
 *   Fallback Height  — slab height (above world bottom) for cached/unloaded chunks (Terrain mode)
 *   Box Thickness    — vertical thickness of the filled slab covering the chunk
 *   Scan Interval     — tick cadence (higher = slower, more anti-AC-stealthy)
 *   Log Coords       — log + chat-flag newly-flagged chunk coordinates
 *   Show Unloaded   — render cached chunks even after they unload
 *   Cache Only      — only read from the cache (skip live chunk scan)
 */
public class SusChunkESP extends Module {

    private final IntSetting minScore = addSetting(
        new IntSetting("Min Score", "Weighted storage score needed to flag a chunk (chest/hopper/dropper=1, barrel/trapped=2, shulker/ender=3, furnace/smoker/blast=0.5).",
            1, 100, 8, 1));
    /** Krypton-style primary detection: flag a chunk whose weighted storage
     *  count (chest=1, hopper=1, barrel=2, shulker/ender chest=3 …) reaches
     *  this. Block entities MUST be sent by the server even under anti-xray,
     *  so this is the most reliable base-finder signal on DonutSMP. 5 is the
     *  classic "stash room" threshold. */
    private final IntSetting minContainers = addSetting(
        new IntSetting("Min Containers", "Hard floor: flag a chunk only when it holds at least this many storage containers (5 = classic stash threshold).",
            1, 50, 5, 1));
    /** Krypton-style tracer lines from the camera to the nearest flagged
     *  chunks — the signature "which direction is the base" feature. */
    private final BooleanSetting tracers = addSetting(
        new BooleanSetting("Tracers", "Draw a tracer line from the camera to each flagged chunk (Krypton-style direction finder).",
            true));
    /** Cap on simultaneous tracer lines so a busy session doesn't turn the
     *  screen into a starburst — only the nearest N chunks get tracers. */
    private final IntSetting maxTracers = addSetting(
        new IntSetting("Max Tracers", "Only draw tracers to the N nearest flagged chunks (prevents clutter).",
            1, 50, 8, 1));
    /** Krypton-style wireframe box hugging the flagged chunk's container
     *  Y-range — shows WHERE vertically in the chunk the base actually is
     *  (e.g. "the stash sits at Y -35..-20") instead of a whole column. */
    private final BooleanSetting showBoxes = addSetting(
        new BooleanSetting("Show Boxes", "Draw a wireframe box at the flagged chunk's container Y-range (shows where in the chunk the base is).",
            true));
    /** Krypton-style stash log: flagged chunks are written to
     *  sus_chunks.json and re-loaded on the next session, so bases you flew
     *  past stay marked on the map across relogs. */
    private final BooleanSetting persistFinds = addSetting(
        new BooleanSetting("Persist Finds", "Save flagged chunks to sus_chunks.json so found bases survive relogs (Krypton-style stash log).",
            true));
    private final IntSetting clusterRadius = addSetting(
        new IntSetting("Cluster Radius", "Chunk-cluster flag radius (0 = single-chunk only).",
            0, 4, 1, 1));
    private final BooleanSetting belowY0 = addSetting(
        new BooleanSetting("Below Y=0 Anomaly", "Containers below Y=0 count double toward the score floor (deepslate stash bias; the Min Containers floor still applies).",
            true));
    private final BooleanSetting spawnerFlag = addSetting(
        new BooleanSetting("Spawner Flag", "Flag any chunk containing a mob spawner (farm/base indicator).",
            true));

    // ── Section-scan heuristics (live chunks only) ───────────────────
    /** Enable the unnatural-block palette scan (Wurst BaseFinder approach).
     *  Reads the chunk's block-state palette for player-building blocks
     *  (obsidian, planks, glass, workstations, redstone). Weaker under
     *  anti-xray but strong on servers without it. */
    private final BooleanSetting unnaturalBlocksFlag = addSetting(
        new BooleanSetting("Unnatural Blocks", "Flag chunks with player-building blocks in their palette (obsidian, planks, glass, workstations).",
            true));
    /** Enable the air-pocket anomaly scan. Air pockets below Y=0 are the
     *  strongest anti-xray-resistant signal — anti-xray obfuscates ore but
     *  doesn't hide excavated rooms. */
    private final BooleanSetting airPocketsFlag = addSetting(
        new BooleanSetting("Air Pockets", "Flag chunks with air-pocket anomalies below Y=0 (excavated rooms — anti-xray-resistant).",
            true));
    /** Enable the light-source scan. Torches, lanterns, and glowstone
     *  placed underground are a strong base-lighting signal — natural
     *  generation rarely puts light sources deep in stone. */
    private final BooleanSetting lightSourcesFlag = addSetting(
        new BooleanSetting("Light Sources", "Flag chunks with non-natural light sources underground (torches, lanterns, glowstone in stone).",
            true));
    /** Enable the heightmap-inconsistency scan. The chunk's stored
     *  heightmap (computed server-side from real data) is compared against
     *  the actual block layout — mismatches leak edits even through
     *  anti-xray obfuscation. */
    private final BooleanSetting heightmapFlag = addSetting(
        new BooleanSetting("Heightmap Inconsistency", "Flag chunks where the stored heightmap doesn't match the actual block layout (anti-xray-resistant).",
            true));
    /** Minimum section-scan score to flag a chunk. The section scan
     *  contributes a weighted sum: unnaturalBlocks×1.0 + airPockets×1.5 +
     *  lightSources×2.0 + heightmapMismatches×1.0 (sampled counts). */
    private final FloatSetting scanThreshold = addSetting(
        new FloatSetting("Scan Threshold", "Min section-scan score (unnatural blocks + air + light + heightmap) to flag a chunk. Higher = fewer false positives (caves).",
            0f, 100f, 20f, 1));

    // ── Event-driven leak heuristics ────────────────────────────────
    /** Threshold for the weighted event score (sounds + block updates +
     *  piston events + particles + entity spawns) above which a chunk is
     *  flagged as an "active base". 0 disables event-based flagging. */
    private final IntSetting eventThreshold = addSetting(
        new IntSetting("Event Threshold", "Min event-leak score to flag a chunk (sounds, block updates, pistons). 0 = off.",
            0, 100, 0, 1));
    /** Treat any sound event from this chunk as a flag (very aggressive —
     *  the "sound triangulation" bypass. Useful on servers that heavily
     *  restrict block-entity data but still must broadcast sounds. */
    private final BooleanSetting soundFlag = addSetting(
        new BooleanSetting("Sound Events", "Flag chunks with sound events (redstone/mob/piston audio leaks).",
            true));
    /** Flag chunks with a high rate of block-state updates — the
     *  "block-update sniffing" bypass (active farms / redstone). */
    private final BooleanSetting blockUpdateFlag = addSetting(
        new BooleanSetting("Block Update Sniff", "Flag chunks with high-frequency block-state updates (active farms/redstone).",
            true));
    /** Flag chunks with piston / door / note-block events — the "piston
     *  desync" bypass that forces the server to drop anti-xray obfuscation
     *  for a split second. */
    private final BooleanSetting pistonDesyncFlag = addSetting(
        new BooleanSetting("Piston Desync", "Flag chunks with piston/door/note-block events (anti-xray desync leaks).",
            true));
    /** Flag chunks with entity spawns — the "entity density" bypass
     *  (clusters of cows underground, item frames, armor stands). */
    private final BooleanSetting entityDensityFlag = addSetting(
        new BooleanSetting("Entity Density", "Flag chunks with frequent entity spawns (mob farms / storage markers).",
            true));
    /** Flag chunks with particle events — torch smoke, redstone dust,
     *  mob flames. Weaker signal but catches fully-obfuscated bases. */
    private final BooleanSetting particleFlag = addSetting(
        new BooleanSetting("Particle Sniff", "Flag chunks with particle events (torch smoke, redstone dust, mob flames).",
            false));

    private final ColorSetting color = addSetting(
        new ColorSetting("Color", "Overlay color for suspicious chunks (default red).", 0xFFFF0000));
    /** How the flat overlay's Y level is chosen. {@code Terrain} rests the
     *  slab on the chunk's own heightmap (highest block Y + Top Offset) for
     *  live chunks, and at world bottom + Fallback Height for cached/unloaded
     *  chunks — the slab follows the land. {@code Fixed} ignores the heightmap
     *  entirely and places every slab at the single {@link #fixedY} value, so
     *  all flagged chunks render at the same elevation (useful for a clean,
     *  level overview instead of terrain-following bumps). */
    private final EnumSetting yMode = addSetting(
        new EnumSetting("Y Mode", "Terrain = slab follows the chunk's surface. Fixed = every slab sits at Fixed Y (a single level).",
            new String[]{"Terrain", "Fixed"}, "Terrain"));
    /** The fixed Y level used when {@link #yMode} is {@code Fixed}. Every
     *  flagged chunk's slab rests at exactly this Y (its base), regardless of
     *  the chunk's terrain. Let's you set the overlay to e.g. -64, 100, or any
     *  value so all highlighted chunks align on one plane. */
    private final IntSetting fixedY = addSetting(
        new IntSetting("Fixed Y", "Y level for every slab when Y Mode = Fixed (overrides terrain-following).",
            -64, 320, 100, 1));
    /** How far ABOVE the chunk's highest block the flat filled slab sits, so the
     *  overlay always pokes above the terrain (never buried in it). For the
     *  flat-box mode the slab is just a thin sheet resting at this height above
     *  the surface; the slab thickness is governed by {@link #boxThickness}.
     *  Only used when {@link #yMode} is {@code Terrain}. */
    private final IntSetting topOffset = addSetting(
        new IntSetting("Top Offset", "How far above the chunk's highest block the flat overlay sits (so it always clears terrain). Terrain mode only.",
            0, 128, 1, 1));
    /** Ground height (above the world floor) used when the chunk is unloaded /
     *  cached-only and we can't read its heightmap. The flat slab rests here.
     *  Only used when {@link #yMode} is {@code Terrain}. */
    private final IntSetting fallbackHeight = addSetting(
        new IntSetting("Fallback Height", "Slab height (above world bottom) when the chunk is unloaded/cached-only. Terrain mode only.",
            16, 512, 64, 1));
    /** Thickness (in blocks) of the flat filled slab that covers each flagged
     *  chunk. 1 = a visually flat sheet; higher = a thicker horizontal box.
     *  The slab sits on the surface (live chunks) or at Fallback Height
     *  (cached chunks); this controls only how thick the filled cover is. */
    private final IntSetting boxThickness = addSetting(
        new IntSetting("Box Thickness", "Thickness (blocks) of the flat filled slab covering each flagged chunk. 1 = flat sheet.",
            1, 64, 1, 1));
    private final BooleanSetting showUnloaded = addSetting(
        new BooleanSetting("Show Unloaded", "Render cached chunks even after they unload (persistent leak projection).",
            true));
    private final BooleanSetting cacheOnly = addSetting(
        new BooleanSetting("Cache Only", "Read exclusively from the packet cache (skip live world scan). Bypasses anti-xray fully.",
            false));
    /** Scan cadence in ticks (10 = ~0.5s, 20 = ~1s). Higher = slower scan,
     *  lower CPU/throughput, and looks more natural to server anti-cheat
     *  (blends with regular player loading patterns). The rate-limiting
     *  evasion: a fast every-tick scan can read as abnormal data access. */
    private final IntSetting scanInterval = addSetting(
        new IntSetting("Scan Interval", "Ticks between full scans (higher = slower + more anti-AC-stealthy).",
            2, 100, 10, 1));
    /** Log + chat-flag newly-flagged chunk coordinates (once per chunk —
     *  no spam). Lets you revisit / inspect flagged areas later. */
    private final BooleanSetting logCoords = addSetting(
        new BooleanSetting("Log Coords", "Log + chat-flag newly-flagged chunk coordinates (client-side logging).",
            true));

    /** ChunkPos.toLong() → score (for flagged chunks). */
    private final Map<Long, Float> flaggedChunks = new HashMap<>();
    /** ChunkPos.toLong() → top-Y (highest non-air block Y in the chunk, or
     *  the fallback height for unloaded/cached chunks). The render pass uses
     *  this to anchor the box so it always clears the terrain. Populated
     *  during the tick scan (chunks can unload between tick and render). */
    private final Map<Long, Integer> flaggedChunkTops = new HashMap<>();
    /** ChunkPos.toLong() → [minY, maxY] of the chunk's cached container
     *  block entities, or absent/null when unknown. Drives the Krypton-style
     *  wireframe box that hugs the base's actual vertical extent. */
    private final Map<Long, int[]> flaggedChunkYRange = new HashMap<>();
    /** Already-announced chunks (prevents chat spam). */
    private final Set<Long> notifiedChunks = new HashSet<>();

    // ── Krypton-style persistence (sus_chunks.json) ──────────────────
    /** A flagged chunk persisted to disk. Plain public fields — Gson
     *  serializes them directly, no adapters needed. */
    public static final class SavedChunk {
        public int cx;
        public int cz;
        public float score;
        public int containers;
        public long firstSeenMs;
    }

    private static final Path DATA_FILE = FabricLoader.getInstance().getConfigDir()
        .resolve("lucidclient").resolve("sus_chunks.json");
    private static final int MAX_SAVED = 5000;
    private static final Type SAVED_LIST_TYPE = new TypeToken<List<SavedChunk>>() {}.getType();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    /** packed chunk key → saved record. Loaded on enable, merged into every
     *  scan so past finds keep rendering + tracing, and written back whenever
     *  a new chunk is flagged. */
    private final Map<Long, SavedChunk> savedChunks = new HashMap<>();

    private int scanTick = 0;
    private int pruneTick = 0;

    public SusChunkESP() {
        super("SusChunkESP", "Highlights suspicious chunks with a flat filled red box covering each chunk (3D world overlay). Anti-xray bypass via packet cache.",
            Category.RENDER);
    }

    @Override
    protected void onEnable() {
        flaggedChunks.clear();
        flaggedChunkTops.clear();
        flaggedChunkYRange.clear();
        notifiedChunks.clear();
        scanTick = 0;
        pruneTick = 0;
        loadSavedChunks();
    }

    @Override
    protected void onDisable() {
        flaggedChunks.clear();
        flaggedChunkTops.clear();
        flaggedChunkYRange.clear();
        notifiedChunks.clear();
        // Don't clear the SusChunkCache itself — other modules may rely on it,
        // and the cache self-prunes by age. Persisting it across toggles is a
        // feature: a leak captured this session stays flagged next time you
        // re-enable.
    }

    /** Load the persisted stash log so past finds re-render + re-trace. */
    private void loadSavedChunks() {
        savedChunks.clear();
        try {
            if (Files.exists(DATA_FILE)) {
                List<SavedChunk> list = gson.fromJson(
                    Files.readString(DATA_FILE), SAVED_LIST_TYPE);
                if (list != null) {
                    for (SavedChunk sc : list) {
                        if (sc == null) continue;
                        savedChunks.put(ChunkPos.toLong(sc.cx, sc.cz), sc);
                    }
                }
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.warn("Lucid SusChunkESP could not load {}", DATA_FILE, t);
        }
        // Past finds were already announced in a previous session — seed the
        // guard set so they don't re-alert, they just render.
        notifiedChunks.addAll(savedChunks.keySet());
    }

    /** Persist a newly-flagged chunk and write the stash log to disk. */
    private void saveNewChunk(long key, int cx, int cz, float score, int containers) {
        try {
            SavedChunk sc = new SavedChunk();
            sc.cx = cx;
            sc.cz = cz;
            sc.score = score;
            sc.containers = containers;
            sc.firstSeenMs = System.currentTimeMillis();
            savedChunks.put(key, sc);
            // Bound the log — drop the oldest entry when over capacity.
            if (savedChunks.size() > MAX_SAVED) {
                long oldestKey = -1;
                long oldest = Long.MAX_VALUE;
                for (Map.Entry<Long, SavedChunk> e : savedChunks.entrySet()) {
                    if (e.getValue().firstSeenMs < oldest) {
                        oldest = e.getValue().firstSeenMs;
                        oldestKey = e.getKey();
                    }
                }
                if (oldestKey >= 0) savedChunks.remove(oldestKey);
            }
            Files.createDirectories(DATA_FILE.getParent());
            Files.writeString(DATA_FILE, gson.toJson(new ArrayList<>(savedChunks.values()), SAVED_LIST_TYPE));
        } catch (Throwable t) {
            LucidClient.LOGGER.warn("Lucid SusChunkESP could not save {}", DATA_FILE, t);
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        // Stagger the scan to every Scan Interval ticks (default 10 = ~0.5s)
        // to avoid FPS impact AND keep throughput low so the scan blends
        // with regular player loading patterns (anti-cheat rate-limiting).
        int interval = scanInterval.get();
        if (interval < 2) interval = 2;
        if (++scanTick % interval != 0) return;

        // Periodically prune the cache (every ~5s = 100 ticks) and decay
        // event counters so a quiet base stops broadcasting after a few
        // minutes.
        if (++pruneTick % 100 == 0) {
            long now = System.currentTimeMillis();
            SusChunkCache.get().prune();
            SusChunkCache.get().decayEvents(now);
        }

        ClientWorld world = client.world;
        int viewDist = client.options.getViewDistance().getValue();
        if (viewDist < 2) viewDist = 8;
        if (viewDist > 32) viewDist = 32;
        int scanR = viewDist + 1;
        int pcx = client.player.getBlockPos().getX() >> 4;
        int pcz = client.player.getBlockPos().getZ() >> 4;

        // Refresh the cache's "loaded" markers so the live scan can keep
        // currently-loaded chunks from being pruned. (The mixin already
        // marks chunks loaded on ChunkDataS2CPacket, but refreshing here
        // covers chunks loaded before the mixin was active.)
        if (!cacheOnly.isEnabled()) {
            for (int cx = pcx - scanR; cx <= pcx + scanR; cx++) {
                for (int cz = pcz - scanR; cz <= pcz + scanR; cz++) {
                    if (world.getChunkManager().isChunkLoaded(cx, cz)) {
                        SusChunkCache.get().setLoaded(cx, cz, true);
                        // Opportunistically backfill: if the cache hasn't seen
                        // this chunk's block entities yet (e.g. the mixin was
                        // added mid-session, or the chunk was loaded before
                        // the module was enabled), feed the live chunk's data
                        // in. We use a minimal NBT compound — the cache only
                        // needs the type + pos for scoring; the NBT hash is
                        // used purely for change detection.
                        ChunkEntry entry = SusChunkCache.get().get(cx, cz);
                        if (entry == null || entry.blockEntities.isEmpty()) {
                            WorldChunk chunk = world.getChunk(cx, cz);
                            if (chunk != null) {
                                // Capture for the lambda — Java requires
                                // effectively-final locals in lambdas.
                                final int fcx = cx, fcz = cz;
                                chunk.getBlockEntities().forEach((pos, be) -> {
                                    BlockEntityType<?> t = be.getType();
                                    if (SusChunkCache.isContainer(t)) {
                                        // Use an empty NbtCompound for the
                                        // backfill — the cache records the
                                        // hash for change detection, and an
                                        // empty compound is a stable sentinel
                                        // for "backfilled from live scan".
                                        SusChunkCache.get().recordFromChunkData(
                                            fcx, fcz, pos, t, new net.minecraft.nbt.NbtCompound());
                                    }
                                });
                            }
                        }
                    }
                }
            }
        }

        // ── Score every candidate chunk (live + cached) ────────────
        Map<Long, Float> newFlagged = new HashMap<>();
        Map<Long, Integer> newTops = new HashMap<>();
        Map<Long, int[]> newYRanges = new HashMap<>();

        // Live scan: chunks within render distance. Pass the loaded
        // WorldChunk through so scoreAndFlag can run the section-scan
        // heuristics (which need the actual block-state palette).
        if (!cacheOnly.isEnabled()) {
            for (int cx = pcx - scanR; cx <= pcx + scanR; cx++) {
                for (int cz = pcz - scanR; cz <= pcz + scanR; cz++) {
                    WorldChunk chunk = null;
                    try {
                        if (world.getChunkManager().isChunkLoaded(cx, cz)) {
                            chunk = world.getChunk(cx, cz);
                        }
                    } catch (Throwable ignored) {
                        // Chunk can unload between the isChunkLoaded check
                        // and the getChunk call — treat as no live chunk.
                    }
                    scoreAndFlag(cx, cz, chunk, newFlagged, newTops, newYRanges);
                }
            }
        }

        // Cached scan: also flag chunks the cache has data for, even if they
        // unloaded (the "persistent leak projection" bypass). Only include
        // unloaded chunks when Show Unloaded is on, to keep the overlay
        // manageable. The cached scan can't run section heuristics (no live
        // chunk) — it relies purely on the cache + event signals.
        if (showUnloaded.isEnabled()) {
            for (long key : SusChunkCache.get().snapshotKeys()) {
                int cx = ChunkPos.getPackedX(key);
                int cz = ChunkPos.getPackedZ(key);
                // Skip chunks already scored in the live scan.
                if (newFlagged.containsKey(key)) continue;
                // Skip chunks still within live range — they were already
                // scored above (avoids double-flagging + lets the live scan
                // govern currently-visible chunks).
                int dx = cx - pcx, dz = cz - pcz;
                if (Math.abs(dx) <= scanR && Math.abs(dz) <= scanR) continue;
                scoreAndFlag(cx, cz, null, newFlagged, newTops, newYRanges);
            }
        }

        // Merge saved (persisted) finds so past sessions' stashes keep
        // rendering + tracing. They use the fallback slab (no live
        // heightmap); the Y-range comes from the cache when still in memory,
        // else the box is skipped (the slab stays).
        //
        // Each persisted find is RE-VALIDATED against the CURRENT thresholds:
        // a container-based find (containers > 0) only keeps rendering while
        // it still meets today's Min Containers / Min Score floors with the
        // data it was found with. Without this, temporarily lowering Min
        // Containers (e.g. to 1) would latch every village/ruin chunk into
        // the stash log forever — flagged at min=1, rendered forever even
        // after raising the threshold back up. Non-container finds
        // (containers == 0: spawner / event / section-heuristic signals) are
        // exempt — they were never gated on these floors. The log itself is
        // NOT rewritten: raising the threshold back down restores those
        // finds, by design.
        if (persistFinds.isEnabled() && !savedChunks.isEmpty()) {
            int minC = minContainers.get();
            int minS = minScore.get();
            for (Map.Entry<Long, SavedChunk> e : savedChunks.entrySet()) {
                long key = e.getKey();
                if (newFlagged.containsKey(key)) continue;
                SavedChunk sc = e.getValue();
                if (sc.containers > 0 && !(sc.containers >= minC || sc.score >= minS)) {
                    continue; // no longer meets the current floors — filtered
                }
                newFlagged.put(key, sc.score);
                // No live heightmap → sentinel triggers the fallback slab.
                newTops.put(key, Integer.MIN_VALUE);
                int[] yr = SusChunkCache.get().getYRange(sc.cx, sc.cz);
                if (yr != null) newYRanges.put(key, yr);
            }
        }

        flaggedChunks.clear();
        flaggedChunks.putAll(newFlagged);
        flaggedChunkTops.clear();
        flaggedChunkTops.putAll(newTops);
        flaggedChunkYRange.clear();
        flaggedChunkYRange.putAll(newYRanges);

        // Client-side coordinate logging: print a chat line (and log) for
        // each chunk that's newly flagged this scan (i.e. not in
        // notifiedChunks). The guard set prevents spam — each chunk only
        // announces once per session, so a base that stays flagged doesn't
        // re-announce every scan.
        if (logCoords.isEnabled()) {
            for (Long key : newFlagged.keySet()) {
                if (notifiedChunks.add(key)) {
                    int cx = ChunkPos.getPackedX(key);
                    int cz = ChunkPos.getPackedZ(key);
                    // Chunk center block coords (the chunk's 8,8 corner).
                    int bx = (cx << 4) + 8;
                    int bz = (cz << 4) + 8;
                    int containers = SusChunkCache.get().storageCount(cx, cz);
                    String msg = "Sus chunk @ " + bx + ", ~, " + bz
                        + " (score " + Math.round(newFlagged.get(key))
                        + ", " + containers + " containers)";
                    LucidClient.LOGGER.info("[SusChunkESP] {}", msg);
                    // Krypton-style stash log: persist so the find survives
                    // the next relog.
                    if (persistFinds.isEnabled()) {
                        saveNewChunk(key, cx, cz, newFlagged.get(key), containers);
                    }
                }
            }
        }
    }

    /**
     * Score a chunk via the cache + apply heuristic flags, adding it to
     * {@code out} if it meets any flagging criterion.
     *
     * Flagging criteria:
     *   1. Container density (weighted score ≥ Min Score)
     *   2. Cluster detection (sum of scores within ±Cluster Radius ≥ threshold)
     *   3. Below-Y=0 anomaly (deepslate-base signature)
     *   4. Spawner presence
     *   5. Event-driven leak score ≥ Event Threshold (sounds + block updates +
     *      piston events + particles + entity spawns — the anti-xray bypass)
     *      ─ individually gated by the Sound Events / Block Update Sniff /
     *      Piston Desync / Entity Density / Particle Sniff settings.
     *   6. Section-scan heuristics (live chunks only): unnatural blocks, air
     *      pockets, light sources, heightmap inconsistencies — gated
     *      individually. The section scan needs the actual block-state
     *      palette, so it only runs when {@code chunk} is non-null (i.e. the
     *      chunk is currently loaded).
     *
     * @param chunk the loaded {@link WorldChunk} for the section-scan
     *              heuristics, or {@code null} to skip them (cached / unloaded
     *              chunks)
     * @param tops  map receiving the chunk's top-Y (highest non-air block)
     *              for flagged chunks, used by the render pass to anchor the
     *              box so it always clears the terrain
     */
    private void scoreAndFlag(int cx, int cz, WorldChunk chunk,
                              Map<Long, Float> out, Map<Long, Integer> tops,
                              Map<Long, int[]> yRanges) {
        long key = ChunkPos.toLong(cx, cz);
        ChunkEntry entry = SusChunkCache.get().get(cx, cz);
        // NOTE: Do NOT return early when entry is null. The section-scan
        // heuristics (criterion 6) don't depend on the cache — a loaded chunk
        // with only unnatural blocks / air pockets / light sources but zero
        // block entities must still be scannable. We guard the cache-dependent
        // reads (criteria 1-5) below and let the section scan run regardless.

        float score = 0f;
        float storageScore = 0f;
        int storageCount = 0;
        boolean hasSpawner = false;
        // Per-event-type counts for the individual heuristic gates.
        long mechanicalSounds = 0;
        long totalSounds = 0;
        long blockUpdates = 0;
        long blockEvents = 0;
        long particleEvents = 0;
        long entitySpawns = 0;
        if (entry != null) {
            synchronized (entry) {
                for (BlockEntityRecord r : entry.blockEntities.values()) {
                    score += SusChunkCache.weightOf(r.type);
                    float w = SusChunkCache.storageWeightOf(r.type);
                    if (w > 0f) {
                        storageCount++;
                        // Deepslate stash bias: containers below Y=0 count
                        // double toward the weighted-score floor (Min Score).
                        // The plain count floor (Min Containers) still applies.
                        storageScore += (belowY0.isEnabled() && r.y < 0) ? w * 2.0f : w;
                    }
                    if (r.type == BlockEntityType.MOB_SPAWNER) hasSpawner = true;
                }
                // Snapshot event counters under the entry lock so the read is
                // consistent with the block-entity read above.
                SusChunkCache.EventStats ev = entry.events;
                totalSounds = ev.soundEvents;
                mechanicalSounds = ev.mechanicalSounds;
                blockUpdates = ev.blockUpdates;
                blockEvents = ev.blockEvents;
                particleEvents = ev.particleEvents;
                entitySpawns = ev.entitySpawns;
            }
        }

        boolean flag = false;

        // The two threshold settings are the hard gates for every storage
        // signal. "Min Containers" is a plain storage-container count;
        // "Min Score" is the weighted storage score (chest/hopper/dropper=1,
        // barrel/trapped=2, shulker/ender=3, furnace/smoker/blast=0.5).
        // Spawners and beacons are NOT storage -- they can only trip the
        // dedicated spawner flag below, never these floors.
        int minC = minContainers.get();
        int minS = minScore.get();
        boolean meetsCountFloor = storageCount >= minC;
        boolean meetsScoreFloor = storageScore >= minS;

        // 0. Krypton-style container-count detection -- the primary,
        //    anti-xray-proof signal. Storage block entities MUST be sent by
        //    the server even under PaperMC Engine Mode 2 (the client must know
        //    chests/hoppers exist), so a chunk with 5+ containers is always a
        //    real stash even when its palette looks like solid stone. Either
        //    floor trips it independently, but neither can be bypassed by a
        //    single stray chest or a lone sub-zero block-entity record.
        if (storageCount > 0 && (meetsCountFloor || meetsScoreFloor)) {
            flag = true;
        }
        // 4. Spawner presence -- a discrete high-value find, intentionally
        //    exempt from the container floors. Turn off the toggle if natural
        //    dungeon / fortress / trial-chamber spawners are noise.
        if (spawnerFlag.isEnabled() && hasSpawner) flag = true;

        // 2. Cluster detection: sum of scores within ±Cluster Radius.
        int r = clusterRadius.get();
        if (!flag && r > 0) {
            float clusterSum = 0f;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    clusterSum += SusChunkCache.get().storageScore(cx + dx, cz + dz);
                }
            }
            float clusterThreshold = minS * (2 * r + 1);
            if (clusterSum >= clusterThreshold) flag = true;
        }

        // 5. Event-driven leak heuristics (the anti-xray bypass).
        //
        // Each gated heuristic flags the chunk if its event count is non-zero
        // (i.e. the server has broadcast at least one such event from this
        // chunk since the last decay). This is intentionally aggressive — a
        // single piston extend from an otherwise-invisible underground base
        // is a strong signal. The decay logic in the cache keeps the
        // counters from accumulating forever, so a base that went quiet stops
        // flagging after a few minutes.
        int threshold = eventThreshold.get();
        if (!flag && threshold > 0) {
            float eventScore = 0f;
            if (soundFlag.isEnabled()) {
                // Mechanical sounds (pistons/redstone/mob farms) weigh 2.0
                // per event; ambient sounds weigh 0.5. Use the non-mechanical
                // count = totalSounds - mechanicalSounds for the lower tier.
                eventScore += mechanicalSounds * 2.0f;
                eventScore += Math.max(0, totalSounds - mechanicalSounds) * 0.5f;
            }
            if (blockUpdateFlag.isEnabled()) {
                // Block updates are high-volume → low per-event weight, but
                // accumulate fast for active farms.
                eventScore += blockUpdates * 0.05f;
                // Special-case: a burst of updates (≥ 20) is a strong farm
                // signal regardless of the per-event weight.
                if (blockUpdates >= 20) eventScore += 5f;
            }
            if (pistonDesyncFlag.isEnabled()) {
                // Piston / door / note-block events are rare and high-value.
                eventScore += blockEvents * 1.5f;
            }
            if (entityDensityFlag.isEnabled()) {
                eventScore += entitySpawns * 0.5f;
                // A cluster of spawns (≥ 5) suggests a mob farm or animal pen.
                if (entitySpawns >= 5) eventScore += 3f;
            }
            if (particleFlag.isEnabled()) {
                eventScore += particleEvents * 0.2f;
            }
            if (eventScore >= threshold) flag = true;
        }

        // 6. Section-scan heuristics (live chunks only). These read the
        // actual block-state palette — the Krypton Sus Chunk Finder signals.
        // They only run if at least one of the four section-scan gates is on
        // AND we have a live chunk (cached-only / unloaded chunks skip this).
        if (!flag && chunk != null && (unnaturalBlocksFlag.isEnabled()
                || airPocketsFlag.isEnabled() || lightSourcesFlag.isEnabled()
                || heightmapFlag.isEnabled())) {
            ChunkHeuristics.ScanResult scan;
            try {
                scan = ChunkHeuristics.scanChunk(chunk);
            } catch (Throwable ignored) {
                // A chunk can unload mid-scan, or its heightmap may not be
                // populated yet — never let the scanner crash the tick thread.
                scan = null;
            }
            if (scan != null) {
                // Add the section-scan contribution to the running score so
                // the overlay color reflects the combined suspicion.
                score += scan.score;
                // Build the gated section-scan score. Each individual signal
                // is weighted per the ChunkHeuristics scoring, but only counts
                // when its gate is on. This lets the user tune which signals
                // they trust (e.g. disable Unnatural Blocks on heavily
                // anti-xray'd servers where the palette is fake-stone).
                float scanScore = 0f;
                if (unnaturalBlocksFlag.isEnabled()) {
                    scanScore += scan.unnaturalBlocks * 1.0f;
                }
                if (airPocketsFlag.isEnabled()) {
                    // Air is now a CORROBORATING signal (only counted near
                    // unnatural blocks / light), so it weighs low — it
                    // amplifies the unnatural-block signal rather than
                    // standing alone. This kills the cave false-positives
                    // where bare natural-cave air was tripping the threshold.
                    scanScore += scan.airPockets * 0.5f;
                }
                if (lightSourcesFlag.isEnabled()) {
                    scanScore += scan.lightSources * 2.0f;
                }
                if (heightmapFlag.isEnabled()) {
                    scanScore += scan.heightmapMismatches * 1.0f;
                }
                // The section scan also surfaces hasBelowY0BlockEntity +
                // hasSpawner (read from the live block-entity map, cheaper than
                // the cache). Fold them into the existing gates so a live
                // chunk that the cache missed still gets flagged.
                // Below-Y0 live-scan signal respects the same hard floors as
                // criterion 0 -- a lone sub-zero chest can't bypass them.
                if (belowY0.isEnabled() && scan.hasBelowY0BlockEntity
                        && (meetsCountFloor || meetsScoreFloor)) {
                    flag = true;
                }
                if (spawnerFlag.isEnabled() && scan.hasSpawner) {
                    flag = true;
                }
                if (!flag && scanScore >= scanThreshold.get()) {
                    flag = true;
                }
            }
        }

        if (flag) {
            out.put(key, score);
            // Compute the chunk's top-Y for the render pass so the box can
            // be anchored to the terrain (and extend above it). For live
            // chunks, sample the WORLD_SURFACE heightmap across the 16×16
            // columns and take the max — that's the highest non-air block.
            // For cached/unloaded chunks, use a sentinel (Integer.MIN_VALUE)
            // — the render pass falls back to world bottom + Fallback Height.
            int topY;
            if (chunk != null) {
                int maxH = Integer.MIN_VALUE;
                try {
                    net.minecraft.world.Heightmap hm =
                        chunk.getHeightmap(net.minecraft.world.Heightmap.Type.WORLD_SURFACE);
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            int h = hm.get(lx, lz);
                            if (h > maxH) maxH = h;
                        }
                    }
                } catch (Throwable ignored) {
                    // Heightmap not populated yet — fall back below.
                    maxH = Integer.MIN_VALUE;
                }
                if (maxH == Integer.MIN_VALUE) {
                    // No heightmap available — use the world's top Y as a
                    // safe upper bound.
                    topY = chunk.getTopYInclusive();
                } else {
                    topY = maxH;
                }
            } else {
                // Cached/unloaded chunk: sentinel — render pass uses the
                // fallback slab (world bottom + Fallback Height).
                topY = Integer.MIN_VALUE;
            }
            tops.put(key, topY);
            // Record the chunk's container block-entity Y-range so the render
            // pass can draw a Krypton-style wireframe box hugging the actual
            // base location vertically. May be null (no cached block
            // entities) — the render falls back to slab-only in that case.
            yRanges.put(key, SusChunkCache.get().getYRange(cx, cz));
        }
    }

    /**
     * Called from {@link LucidClient} during {@code WorldRenderEvents.END_MAIN}.
     * Draws a chunk-aligned <b>flat filled horizontal box</b> (a translucent
     * slab) over each flagged chunk via {@link WorldRender3D#drawFilledBox} on
     * the custom no-depth QUADS layer — so the highlight reads as a solid red
     * cover of the chunk, visible through terrain, instead of a tall wireframe
     * column.
     *
     * The slab's X/Z footprint is the exact 16&#xd7;16 chunk extent
     * (cx&#xb7;16 .. cx&#xb7;16+16), so it sits ON the chunk — not sliding with the
     * camera or player Y. The slab's Y level is set by the <b>Y Mode</b> setting:
     * <ul>
     *   <li><b>Terrain</b> (default) — LIVE chunks rest at (highest non-air
     *       block Y + Top Offset); CACHED/unloaded chunks rest at
     *       (world bottom + Fallback Height).</li>
     *   <li><b>Fixed</b> — every slab rests at exactly Fixed Y, ignoring the
     *       chunk's heightmap — all flagged chunks align on one plane.</li>
     * </ul>
     * {@code Box Thickness} sets the slab's vertical thickness (1 = a flat
     * sheet). The fill is translucent (alpha capped ~33%) so it highlights the
     * chunk with its hue without hiding the terrain/base.
     */
    public void renderWorld(WorldRenderContext wrc) {
        if (flaggedChunks.isEmpty()) return;
        if (!isEnabled()) return;

        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        net.minecraft.client.util.math.MatrixStack matrices = wrc.matrices();
        net.minecraft.client.render.VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        int baseColor = color.getColor();
        // Flat-box geometry. The overlay is a THIN horizontal filled slab
        // covering each flagged chunk — a translucent red sheet that reads as a
        // chunk highlight, instead of the old tall wireframe column. The slab is:
        //   • 16 blocks wide × 16 blocks deep — the exact chunk footprint.
        //   • as thick as Box Thickness (1 by default — visually a flat box).
        //
        // Y placement depends on Y Mode:
        //   • Terrain (default): LIVE chunks rest at (chunk's highest block Y
        //     + Top Offset), so the slab sits ON the terrain; CACHED/unloaded
        //     chunks rest at (world bottom + Fallback Height).
        //   • Fixed: every slab rests at exactly Fixed Y, ignoring the chunk's
        //     heightmap — all flagged chunks align on one level plane.
        boolean fixedMode = yMode.getSelectedIndex() == 1;   // 0=Terrain, 1=Fixed
        int fixedYv = fixedY.get();
        int topPad = topOffset.get();
        int fallbackH = fallbackHeight.get();
        int slabH = Math.max(1, boxThickness.get());
        int worldBottomY = client.world.getBottomY();
        // Krypton-style extras: a wireframe box hugging the chunk's container
        // Y-range (Show Boxes) + tracer lines from the camera (Tracers). Both
        // render on the shared no-depth LINES layer at the overlay hue, at full
        // alpha — the slab is the translucent fill, these are the sharp guide
        // lines.
        boolean drawBoxes = showBoxes.isEnabled();
        boolean drawTracers = tracers.isEnabled();
        int lineColor = baseColor;  // full-alpha ARGB → opaque wireframe/tracer
        Vec3d camPos = camera.getCameraPos();
        // chunk key → tracer target Y (the base's vertical center). Built in
        // pass 1, consumed by the tracer pass so Max Tracers can sort by
        // distance. Cheap empty map when tracers are off.
        Map<Long, Double> tracerYByChunk = new HashMap<>();

        WorldRender3D.begin(matrices, camera);
        try {
            for (Map.Entry<Long, Float> entry : flaggedChunks.entrySet()) {
                long key = entry.getKey();
                int cx = ChunkPos.getPackedX(key);
                int cz = ChunkPos.getPackedZ(key);
                double slabBottom;
                if (fixedMode) {
                    // Fixed Y — every slab at the same elevation regardless of
                    // the chunk's terrain (live or cached, heightmap ignored).
                    slabBottom = (double) fixedYv;
                } else {
                    int topY = flaggedChunkTops.getOrDefault(key, Integer.MIN_VALUE);
                    if (topY == Integer.MIN_VALUE) {
                        // Cached/unloaded chunk — no heightmap. Rest the slab
                        // at world bottom + Fallback Height.
                        slabBottom = (double) worldBottomY + fallbackH;
                    } else {
                        // Live chunk — rest the slab on the chunk's highest
                        // block + Top Offset so it sits ON the surface.
                        slabBottom = (double) topY + topPad;
                    }
                }
                double slabTop = slabBottom + slabH;
                // X/Z = exact 16×16 chunk footprint (chunk-aligned).
                Box box = new Box(cx * 16, slabBottom, cz * 16,
                                  (cx + 1) * 16, slabTop,   (cz + 1) * 16);
                WorldRender3D.drawFilledBox(consumers, box, baseColor);

                // ── Krypton-style wireframe box at the container Y-range ──
                // Hugs the base's actual vertical extent (e.g. the stash
                // spans Y -35..-20) instead of a full 384-block column —
                // shows WHERE in the chunk the storage really is. The
                // Y-range comes from the cache's block-entity record set
                // (the one signal anti-xray cannot strip), so it's reliable
                // even on DonutSMP's obfuscated chunks.
                int[] yr = flaggedChunkYRange.get(key);
                double baseCenterY;
                if (yr != null) {
                    int minY = yr[0];
                    int maxY = yr[1];
                    baseCenterY = (minY + maxY) * 0.5;
                    if (drawBoxes) {
                        // Render a single-Y stash as a 1-block-tall box (a
                        // zero-height outline would be invisible).
                        if (maxY <= minY) maxY = minY + 1;
                        // Slight inset so the wireframe reads as a distinct
                        // box INSIDE the chunk rather than coincident with
                        // the slab edges.
                        Box yBox = new Box(
                            cx * 16 + 0.25, minY,            cz * 16 + 0.25,
                            (cx + 1) * 16 - 0.25, maxY + 1,   (cz + 1) * 16 - 0.25);
                        WorldRender3D.drawBox(consumers, yBox, lineColor);
                    }
                } else {
                    // No cached Y-range (e.g. a section-scan flag with no
                    // recorded block entities): point the tracer at the slab.
                    baseCenterY = slabBottom + slabH * 0.5;
                }
                if (drawTracers) tracerYByChunk.put(key, baseCenterY);
            }

            // ── Tracers (Krypton-style direction finder) ───────────────
            // A line from the camera to each flagged chunk center, capped at
            // Max Tracers nearest chunks so a session with many finds doesn't
            // turn into a starburst. The line points at the base's vertical
            // center (the Y-range mid) when known, else at the slab.
            if (drawTracers && !tracerYByChunk.isEmpty()) {
                int cap = maxTracers.get();
                List<Long> nearest = new ArrayList<>(tracerYByChunk.keySet());
                nearest.sort(Comparator.comparingDouble(key -> {
                    int cx = ChunkPos.getPackedX(key);
                    int cz = ChunkPos.getPackedZ(key);
                    double ty = tracerYByChunk.get(key);
                    double dx = (cx * 16 + 8) - camPos.x;
                    double dy = ty - camPos.y;
                    double dz = (cz * 16 + 8) - camPos.z;
                    return dx * dx + dy * dy + dz * dz;
                }));
                int n = Math.min(cap, nearest.size());
                for (int i = 0; i < n; i++) {
                    long key = nearest.get(i);
                    int cx = ChunkPos.getPackedX(key);
                    int cz = ChunkPos.getPackedZ(key);
                    Vec3d to = new Vec3d(cx * 16 + 8, tracerYByChunk.get(key),
                                         cz * 16 + 8);
                    WorldRender3D.drawTracer(consumers, camera, to, lineColor);
                }
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }
}