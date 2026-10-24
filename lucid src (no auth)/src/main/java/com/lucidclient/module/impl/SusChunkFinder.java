package com.lucidclient.module.impl;

import com.lucidclient.LucidClient;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.ColorSetting;
import com.lucidclient.module.setting.EnumSetting;
import com.lucidclient.module.setting.IntSetting;
import com.lucidclient.suschunk.SusChunkCache;
import com.lucidclient.ui.theme.Colors;
import com.lucidclient.ui.util.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SusChunkFinder — multi-signal per-chunk base detection (glazed ChunkFinder
 * port).
 *
 * <h3>Design origin</h3>
 * Reworked from glazed's {@code ChunkFinderV5} (Meteor addon for DonutSMP).
 * glazed's README calls it "Chunk Finder (13 optional base detectors)" — the
 * real module runs FOUR independent detection tracks over each chunk, each
 * with per-detector toggles and glazed's threshold semantics:
 *
 * <ol>
 *   <li><b>Base detectors</b> — the headline scoring system. Each chunk's raw
 *       signals (cobble lines, deepslate bands, obsidian, hidden ore, needle
 *       shafts, air pockets, dropped items, mob clusters, storage, …) are
 *       counted once when the chunk is analysed, then converted into a
 *       weighted score with short evidence tags ("CobbleLine(4)",
 *       "Storage(6)", …). A chunk whose score ≥ Min Score (default 5) is
 *       flagged and announced with glazed's signature
 *       {@code BOII we MAYY Be RICHHH S:<score> [tags]} alert. The Storage
 *       detector is ungated (always contributes once ≥ 3); every other
 *       detector has its own toggle.</li>
 *   <li><b>Block detectors</b> — glazed's legacy "suspicious blocks" family:
 *       player-placed deepslate (axis-Y, isolated), cobbled deepslate,
 *       rotated deepslate (axis ≠ Y) and end stone, each with its own count
 *       threshold. Blocks exposed to air/fluid are ignored, and chunks that
 *       contain a trial chamber (waxed copper / tuff bricks ≥ threshold) are
 *       skipped entirely (all their flags dropped).</li>
 *   <li><b>Amethyst</b> — detects geodes stripped of their budding amethyst
 *       (intact geodes are skipped): ≥ 40 crystals, or under Smart Check a
 *       geode-shape analysis (density / matrix / spread / exposure) whose
 *       thresholds loosen with the Sensitivity slider (1–10, default 5).
 *       "Magic amethyst" additionally flags geodes with unlit spawnable space
 *       around them (dark-air light probe with a configurable radius).</li>
 *   <li><b>Structure score</b> — redstone / farm / workstation density
 *       (powered repeaters, occupied beehives, cobbled-deepslate floors,
 *       vine / seagrass / flower farms, crafting+storage workstations) rolled
 *       into a capped score, mapped to a {@code base chance %} =
 *       clamp(25 + score/2, 20, 99). Chunks with chance ≥ Min Base Chance
 *       (default 45) are flagged ("Repeater chunk" when ≥ 3 powered
 *       repeaters).</li>
 * </ol>
 *
 * <h3>Lucid adaptations (why this file differs from glazed where it does)</h3>
 * <ul>
 *   <li><b>Storage signal from {@link SusChunkCache}</b> — glazed counts
 *       chest/hopper block entities from the live chunk. Lucid reads the same
 *       signal from the packet-fed {@link SusChunkCache} (fed by
 *       {@code ClientPlayNetworkHandlerMixin}), which survives anti-xray and
 *       chunk unloads; the live chunk's block-entity map is only used as a
 *       fallback when the cache has no entry for the chunk.</li>
 *   <li><b>Tick-driven discovery</b> — glazed hooks ChunkData / BlockUpdate
 *       events via Meteor's event bus. Lucid discovers newly-loaded chunks
 *       from the tick loop (view-distance sweep + a scanned-set) and analyses
 *       a bounded number of chunks per tick, so a burst of chunk loads never
 *       spikes a frame (the same amortisation LightFinder uses; it replaces
 *       glazed's executor pool / scan-delay / max-concurrent-scans).</li>
 *   <li><b>Alerts</b> — glazed's Toast becomes a chat line
 *       (typed: SUCCESS for base finds, INFO otherwise, WARNING for trial
 *       chambers); chat alerts use {@code sendMessage}. Glazed's semantics are
 *       kept: 45s per-chunk cooldown, Max Alerts per rolling minute, and the
 *       chat gating toggles. (glazed's XP-orb sound alert is not ported —
 *       Lucid still prints the alert into chat.)</li>
 *   <li><b>Rendering</b> — keeps Lucid's 2D screen-space boxes (drawn from
 *       the HUD dispatcher) but adopts glazed's four category colors, the
 *       per-category render cap, and per-kind suspicious-block highlights
 *       with glazed's default colors.</li>
 *   <li><b>Trial-chamber forget</b> — glazed drops the chunk from its
 *       scanned set so a later BlockUpdate can rescan it; Lucid's tick sweep
 *       would instantly re-queue an un-scanned loaded chunk, so Lucid keeps
 *       the chunk marked scanned (no rescan loop) while still clearing every
 *       flag.</li>
 * </ol>
 *
 * <h3>Entity signals</h3>
 * Every {@code Entity Scan Ticks} (default 20) the loaded entities are
 * swept: item entities below Y=0 feed Dropped Items, polar bears feed Polar
 * Bear (×3 each), and any non-player mob counts toward Mob Cluster (> 20
 * mobs in one chunk). These are snapshots consumed at chunk-analysis time,
 * exactly like glazed's {@code refreshEntitySignals}.
 *
 * <h3>Cleanup</h3>
 * Every {@code Cleanup Interval} seconds (default 30) state beyond view
 * distance + 5 chunks (flags, analysis, entity signals, alert cooldowns)
 * and view distance + 3 chunks (scanned/pending sets) is dropped, and
 * suspicious-block highlights beyond view-distance blocks + 80 are pruned —
 * glazed's {@code performCleanup} cadence.
 *
 * Settings (grouped as in glazed):
 *   Block detectors: Detect Deepslate / Cobbled / Rotated / End Stone (+ per-
 *     detector thresholds), Ignore Exposed, Ignore Trial Chambers (+ threshold)
 *   Base detectors: Base Detectors, Min Score, 14 detector toggles
 *   Amethyst: Detect Amethyst, Smart Check, Sensitivity, Magic Amethyst,
 *     Magic Threshold, Magic Scan Radius
 *   Structure: Detect Structure, Min Base Chance, 7 detector toggles
 *   Performance: Chunks Per Tick, Entity Scan Ticks, Cleanup Interval
 *   Alerts: Notify Mode (Chat/None), Chat Alerts,
 *     Trial Chamber Alerts, Max Alerts
 *   Render: Show Boxes, Highlight Blocks, Max Chunks/Blocks Render, four
 *     chunk colors + four block-kind colors (glazed defaults)
 */
public class SusChunkFinder extends Module {

    // ════════════════════════════════════════════════════════════════
    //  Constants (glazed)
    // ════════════════════════════════════════════════════════════════

    /** Per-chunk notification cooldown — glazed's NOTIFICATION_COOLDOWN_MS. */
    private static final long NOTIFICATION_COOLDOWN_MS = 45_000L;
    /** Rolling window for the Max Alerts rate limit (alerts per minute). */
    private static final long ALERT_WINDOW_MS = 60_000L;
    /** Block-detector scan ceiling (glazed scans sections 0..128). */
    private static final int BLOCK_SCAN_MAX_Y = 128;
    /** Amethyst geode scan window (world Y). */
    private static final int GEODE_MIN_Y = -64;
    private static final int GEODE_MAX_Y = 48;
    /** Neighbour light above which a "dark" cell counts as lit (magic amethyst). */
    private static final int MAGIC_NEIGHBOUR_LIGHT = 4;

    // Geode voxel cell kinds (order matters: buriedFaces uses >= CELL_CALCITE).
    private static final int CELL_CRYSTAL = 1;
    private static final int CELL_CALCITE = 2;
    private static final int CELL_BASALT = 3;
    private static final int CELL_AIR = 4;
    private static final int CELL_SOLID = 5;

    // ════════════════════════════════════════════════════════════════
    //  Settings — block detectors (glazed "suspicious blocks")
    // ════════════════════════════════════════════════════════════════

    private final BooleanSetting detectDeepslate = addSetting(
        new BooleanSetting("Detect Deepslate", "Flag chunks with isolated player-placed deepslate (long natural runs are ignored).", true));
    private final IntSetting deepslateThreshold = addSetting(
        new IntSetting("Deepslate Threshold", "Isolated deepslate blocks needed to flag a chunk.", 1, 15, 1, 1));
    private final BooleanSetting detectCobbledDeepslate = addSetting(
        new BooleanSetting("Detect Cobbled Deepslate", "Flag chunks with cobbled deepslate (player-placed mining residue).", true));
    private final IntSetting cobbledDeepslateThreshold = addSetting(
        new IntSetting("Cobbled Deepslate Threshold", "Cobbled deepslate blocks needed to flag a chunk.", 1, 15, 4, 1));
    private final BooleanSetting detectRotatedDeepslate = addSetting(
        new BooleanSetting("Detect Rotated Deepslate", "Flag chunks with deepslate whose axis is not Y (impossible in natural generation).", true));
    private final IntSetting rotatedDeepslateThreshold = addSetting(
        new IntSetting("Rotated Deepslate Threshold", "Rotated deepslate blocks needed to flag a chunk.", 1, 20, 3, 1));
    private final BooleanSetting detectEndStone = addSetting(
        new BooleanSetting("Detect End Stone", "Flag chunks containing end stone outside The End.", true));
    private final IntSetting endStoneThreshold = addSetting(
        new IntSetting("End Stone Threshold", "End stone blocks needed to flag a chunk.", 1, 15, 2, 1));
    private final BooleanSetting ignoreExposed = addSetting(
        new BooleanSetting("Ignore Exposed", "Skip suspicious blocks exposed to air or fluid (surface noise).", true));
    private final BooleanSetting ignoreTrialChambers = addSetting(
        new BooleanSetting("Ignore Trial Chambers", "Skip chunks that contain a trial chamber (waxed copper / tuff bricks).", true));
    private final IntSetting trialChamberThreshold = addSetting(
        new IntSetting("Trial Chamber Threshold", "Trial-chamber blocks (waxed copper / tuff bricks) needed to skip a chunk.", 1, 50, 50, 1));

    // ════════════════════════════════════════════════════════════════
    //  Settings — base detectors (glazed scoring system)
    // ════════════════════════════════════════════════════════════════

    private final BooleanSetting baseDetectors = addSetting(
        new BooleanSetting("Base Detectors", "Run glazed's multi-signal base scoring (Min Score gates the flag).", true));
    private final IntSetting minScore = addSetting(
        new IntSetting("Min Score", "Weighted base-detector score needed to flag a chunk.", 1, 30, 5, 1));
    private final BooleanSetting detectCobbleLines = addSetting(
        new BooleanSetting("Cobble Lines", "+min(lines,6) when adjacent cobblestone pairs below Y=0 reach 3.", true));
    private final BooleanSetting detectDeepslateBand = addSetting(
        new BooleanSetting("Deepslate Band", "+min(n/2,5) for polished/brick/tile deepslate bands below Y=0 (>=4).", true));
    private final BooleanSetting detectObsidianBackbone = addSetting(
        new BooleanSetting("Obsidian Backbone", "+min(n,6) for obsidian below Y=20 (>=3) — portal/box backbone.", true));
    private final BooleanSetting detectVeinIntegrity = addSetting(
        new BooleanSetting("Vein Integrity", "+4 when valuable ore sits next to storage (untouched stash vein).", true));
    private final BooleanSetting detectVerticalVein = addSetting(
        new BooleanSetting("Vertical Vein", "+count for ore columns stacked 4 deep (>=2) — x-ray mining signature.", true));
    private final BooleanSetting detectNeedleHole = addSetting(
        new BooleanSetting("Needle Hole", "+needles*2 for 1x1 shafts mined straight down below Y=0 (>=2).", true));
    private final BooleanSetting detectHiddenOre = addSetting(
        new BooleanSetting("Hidden Ore", "+count for ore walled in on 5 of 6 faces (>=2) — buried stash protection.", true));
    private final BooleanSetting detectPistonArray = addSetting(
        new BooleanSetting("Piston Array", "+min(n,8) for pistons below Y=20 (>=3) — door/trap machinery.", true));
    private final BooleanSetting detectMobCluster = addSetting(
        new BooleanSetting("Mob Cluster", "+mobs/5 when a chunk holds more than 20 mobs (>=2) — grinder.", true));
    private final BooleanSetting detectDroppedItems = addSetting(
        new BooleanSetting("Dropped Items", "+n*2 for item entities below Y=0 (>=2) — active loot room.", true));
    private final BooleanSetting detectAirPocket = addSetting(
        new BooleanSetting("Air Pocket", "+pocket/3 for air runs >= 12 below Y=20 (>=5) — carved rooms.", true));
    private final BooleanSetting detectSyntheticStairs = addSetting(
        new BooleanSetting("Synthetic Stairs", "+n/3 for crafted stair blocks below Y=0 (>=5) — staircase access.", true));
    private final BooleanSetting detectPolarBear = addSetting(
        new BooleanSetting("Polar Bear", "+3 per polar bear in the chunk — glazed's joke detector for 'ice road' bases.", true));
    private final BooleanSetting detectChestCluster = addSetting(
        new BooleanSetting("Chest Cluster", "+min(n,12) for 10+ chests below Y=0 — the classic stash room.", true));

    // ════════════════════════════════════════════════════════════════
    //  Settings — amethyst (stripped geodes + magic amethyst)
    // ════════════════════════════════════════════════════════════════

    private final BooleanSetting detectAmethyst = addSetting(
        new BooleanSetting("Detect Amethyst", "Flag geodes whose budding amethyst has been stripped.", true));
    private final BooleanSetting smartCheck = addSetting(
        new BooleanSetting("Smart Check", "Analyse geode shape (density/matrix/spread/exposure) instead of a flat crystal count.", true));
    private final IntSetting sensitivity = addSetting(
        new IntSetting("Sensitivity", "Loosens the smart geode thresholds (higher = more geodes flagged).", 1, 10, 5, 1));
    private final BooleanSetting magicAmethyst = addSetting(
        new BooleanSetting("Magic Amethyst", "Flag geodes with unlit spawnable space around them (dark-air light probe).", true));
    private final IntSetting magicThreshold = addSetting(
        new IntSetting("Magic Threshold", "Dark cells around a geode needed to flag it as magic amethyst.", 1, 100, 12, 1));
    private final IntSetting magicScanRadius = addSetting(
        new IntSetting("Magic Scan Radius", "Cube radius (blocks) around the geode centre probed for dark air.", 1, 24, 8, 1));

    // ════════════════════════════════════════════════════════════════
    //  Settings — structure score (base chance %)
    // ════════════════════════════════════════════════════════════════

    private final BooleanSetting detectStructure = addSetting(
        new BooleanSetting("Detect Structure", "Score chunks on redstone, farms, and storage; flag high base-chance chunks.", true));
    private final IntSetting minBaseChance = addSetting(
        new IntSetting("Min Base Chance", "Base chance percent (clamp(25+score/2, 20..99)) needed to flag a structure chunk.", 20, 99, 45, 1));
    private final BooleanSetting detectRepeaters = addSetting(
        new BooleanSetting("Powered Repeaters", "Structure score from repeaters (x16) and powered ones (x28), cap 90.", true));
    private final BooleanSetting detectBeehives = addSetting(
        new BooleanSetting("Occupied Beehives", "Structure score from beehives (x22) plus bees inside (x10), cap 90.", true));
    private final BooleanSetting detectDeepslateFloor = addSetting(
        new BooleanSetting("Cobbled Deepslate Floor", "Structure score from cobbled deepslate floors at Y 0..20 (n/2, cap 65).", true));
    private final BooleanSetting detectVines = addSetting(
        new BooleanSetting("Vine Farm", "Structure score from vine blocks (n/4, cap 55).", true));
    private final BooleanSetting detectSeagrass = addSetting(
        new BooleanSetting("Seagrass Farm", "Structure score from seagrass at Y<=70 (cap 70).", true));
    private final BooleanSetting detectFlowers = addSetting(
        new BooleanSetting("Flower Farm", "Structure score from flowers at Y<=70 (x12, cap 75).", true));
    private final BooleanSetting detectWorkstations = addSetting(
        new BooleanSetting("Crafting Storage", "Structure score from workstations + storage blocks (x18, cap 100).", true));

    // ════════════════════════════════════════════════════════════════
    //  Settings — performance / alerts / render
    // ════════════════════════════════════════════════════════════════

    /** Replaces glazed's executor pool + scan-delay + max-concurrent-scans:
     *  chunks are analysed on the client thread, this many per tick. */
    private final IntSetting chunksPerTick = addSetting(
        new IntSetting("Chunks Per Tick", "How many queued chunks are fully analysed per tick (amortised scan).", 1, 8, 1, 1));
    /** glazed entity-scan-ticks: cadence of the mob / dropped-item sweep. */
    private final IntSetting entityScanTicks = addSetting(
        new IntSetting("Entity Scan Ticks", "Ticks between entity sweeps (mobs, dropped items, polar bears).", 5, 200, 20, 1));
    /** glazed cleanup-interval: seconds between far-chunk purges. */
    private final IntSetting cleanupInterval = addSetting(
        new IntSetting("Cleanup Interval", "Seconds between purges of out-of-range chunk state.", 15, 300, 30, 1));

    /** Whether chunk alerts are announced in chat at all. */
    private final EnumSetting notifyMode = addSetting(
        new EnumSetting("Notify Mode", "Whether chunk alerts are printed into chat.",
            new String[]{"Chat", "None"}, "Chat"));
    /** glazed chat-alerts: gates the chat line for non-trial-chamber alerts. */
    private final BooleanSetting chatAlerts = addSetting(
        new BooleanSetting("Chat Alerts", "Also print non-trial-chamber alerts into chat.", true));
    /** glazed trial-chamber-alerts: gates the trial-chamber chat line. */
    private final BooleanSetting trialChamberAlerts = addSetting(
        new BooleanSetting("Trial Chamber Alerts", "Announce skipped trial-chamber chunks.", true));
    /** glazed max-alerts: rolling per-minute cap across all categories. */
    private final IntSetting maxAlerts = addSetting(
        new IntSetting("Max Alerts", "Maximum alerts per rolling minute (rate limit).", 1, 20, 5, 1));

    /** KEEP (lucid): render the 2D screen-space boxes over flagged chunks. */
    private final BooleanSetting showBoxes = addSetting(
        new BooleanSetting("Show Boxes", "Render screen-space boxes over flagged chunks.", true));
    /** glazed highlight-blocks: per-block highlights for the block detectors. */
    private final BooleanSetting highlightBlocks = addSetting(
        new BooleanSetting("Highlight Blocks", "Render small boxes over individual suspicious blocks.", true));
    /** glazed max-chunks-render: cap per category. */
    private final IntSetting maxChunksRender = addSetting(
        new IntSetting("Max Chunks Render", "Maximum chunk boxes rendered per category.", 10, 400, 50, 1));
    /** glazed max-blocks-render. */
    private final IntSetting maxBlocksRender = addSetting(
        new IntSetting("Max Blocks Render", "Maximum suspicious-block highlights rendered.", 50, 1000, 200, 10));

    // glazed chunk category colors (gold / green / purple / orange).
    private final ColorSetting chunkColor = addSetting(
        new ColorSetting("Block Chunk Color", "Chunk box color for block-detector flags (glazed gold).", 0xFFFFD700));
    private final ColorSetting baseChunkColor = addSetting(
        new ColorSetting("Base Chunk Color", "Chunk box color for base-detector flags (glazed green).", 0xFF00FF50));
    private final ColorSetting amethystChunkColor = addSetting(
        new ColorSetting("Amethyst Chunk Color", "Chunk box color for amethyst flags (glazed purple).", 0xFFB464FF));
    private final ColorSetting structureChunkColor = addSetting(
        new ColorSetting("Structure Chunk Color", "Chunk box color for structure flags (glazed orange).", 0xFFFF7800));
    // glazed block-kind colors.
    private final ColorSetting deepslateColor = addSetting(
        new ColorSetting("Deepslate Color", "Highlight color for isolated deepslate blocks.", 0xC8646464));
    private final ColorSetting cobbledColor = addSetting(
        new ColorSetting("Cobbled Color", "Highlight color for cobbled deepslate blocks.", 0xC8505050));
    private final ColorSetting rotatedColor = addSetting(
        new ColorSetting("Rotated Color", "Highlight color for rotated deepslate blocks.", 0xC8780078));
    private final ColorSetting endStoneColor = addSetting(
        new ColorSetting("End Stone Color", "Highlight color for end stone blocks.", 0xC8FFFFC8));

    // ════════════════════════════════════════════════════════════════
    //  Analysis model
    // ════════════════════════════════════════════════════════════════

    /** Which legacy block detector found a block (drives highlight color). */
    private enum BlockKind { DEEPSLATE, COBBLED, ROTATED, END_STONE }

    /**
     * Raw per-chunk counters — glazed's ChunkAnalysis split into flat fields.
     * Populated once per chunk by the scan* methods, then scored by
     * {@link #evaluate}. All fields are client-thread only (no sync needed).
     */
    private static final class ChunkAnalysis {
        // Block detectors (scan window Y 0..128).
        int deepslate;
        int cobbledDeepslate;
        int rotatedDeepslate;
        int endStone;
        int trialChamber;
        // Base detectors (scan window chunk bottom..60 + entity/storage signals).
        int storage;          // chest + hopper block entities (cache-backed)
        int cobbleLines;      // horizontally adjacent cobble pairs below Y=0
        int deepslateBand;    // polished/brick/tile/chiselled deepslate below Y=0
        int obsidian;         // obsidian below Y=20
        int valuableOre;      // diamond/emerald/ancient debris blocks
        int vertVeins;        // ore columns stacked 4 deep
        int needles;          // 1x1 shafts mined straight down below Y=0
        int hiddenOre;        // ore walled in on 5 of 6 faces (counts double)
        int pistons;          // pistons below Y=20
        double airPocket;     // air runs >= 12 below Y=20 (run/4 each)
        int syntheticStairs;  // crafted stairs below Y=0
        int deepChests;       // chest/trapped-chest blocks below Y=0
        int mobCluster;       // mobs/5 when a chunk holds > 20 mobs
        int droppedItems;     // item entities below Y=0
        int polarBears;       // polar bears x3
        // Base scoring output.
        int baseScore;
        final List<String> baseTags = new ArrayList<>();
        // Amethyst.
        boolean geodeHit;
        int geodeCrystals;
        int magicSpots;
        // Structure.
        int repeaters;
        int poweredRepeaters;
        int beehives;
        int bees;
        int deepslateFloor;
        int vines;
        int seagrass;
        int flowers;
        int workstations;
        int structureScore;
        int structureChance;
    }

    /** Per-chunk entity snapshot (glazed's EntitySignals). */
    private static final class EntitySignals {
        int mobs;
        int droppedItems;
        int polarBears;
    }

    /** Geode scan output (glazed's GeodeResult). */
    private static final class GeodeResult {
        int crystals;
        int matrix;
        boolean hit;
    }

    /** One rendered chunk box: coords + color + short label. */
    private record FlaggedChunk(int chunkX, int chunkZ, int color, String label) {}

    // ════════════════════════════════════════════════════════════════
    //  State
    // ════════════════════════════════════════════════════════════════

    /** Chunks fully analysed (never re-scanned unless flagged as trial chamber). */
    private final Set<Long> scannedChunks = new HashSet<>();
    /** Loaded chunks waiting for analysis (insertion-ordered work queue). */
    private final Set<Long> pendingChunks = new LinkedHashSet<>();
    /** Analysis output per chunk (survives until cleanup). */
    private final Map<Long, ChunkAnalysis> chunkData = new HashMap<>();
    /** Block-detector flags: key -> human-readable reason (kept for labels). */
    private final Map<Long, String> flaggedChunks = new HashMap<>();
    /** Base-detector flags: key -> "S:<score> [tags]". */
    private final Map<Long, String> baseFlaggedChunks = new HashMap<>();
    /** Amethyst flags: key -> "StrippedGeode(n) MagicAmethyst(n)". */
    private final Map<Long, String> amethystChunks = new HashMap<>();
    /** Structure flags: key -> chance tag. */
    private final Map<Long, String> structureChunks = new HashMap<>();
    /** Suspicious blocks kept for highlighting (glazed suspiciousBlocks). */
    private final Map<BlockPos, BlockKind> suspiciousBlocks = new HashMap<>();
    /** Latest entity sweep per chunk. */
    private final Map<Long, EntitySignals> entitySignals = new HashMap<>();
    /** Last alert time per chunk (45s cooldown). */
    private final Map<Long, Long> notificationTimes = new HashMap<>();
    /** Alert timestamps inside the rolling minute (rate limit). */
    private final ArrayDeque<Long> recentAlerts = new ArrayDeque<>();
    /** Rebuilt render list (staggered every 10 ticks — lucid cadence). */
    private final List<FlaggedChunk> renderList = new ArrayList<>();

    private int tickCounter = 0;
    private int entityTicks = 0;
    private long lastCleanupMs = 0L;

    public SusChunkFinder() {
        super("SusChunkFinder",
            "Multi-signal per-chunk base finder (glazed ChunkFinder port): base-detector scoring, "
                + "suspicious-block detection, stripped-geode amethyst, and structure chance — with "
                + "per-detector toggles, notifications and chunk boxes.",
            Category.BASEFINDING);
    }

    @Override
    protected void onEnable() {
        clearState();
        lastCleanupMs = System.currentTimeMillis();
    }

    @Override
    protected void onDisable() {
        clearState();
    }

    private void clearState() {
        scannedChunks.clear();
        pendingChunks.clear();
        chunkData.clear();
        flaggedChunks.clear();
        baseFlaggedChunks.clear();
        amethystChunks.clear();
        structureChunks.clear();
        suspiciousBlocks.clear();
        entitySignals.clear();
        notificationTimes.clear();
        recentAlerts.clear();
        renderList.clear();
        tickCounter = 0;
        entityTicks = 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  Tick — entity sweep, chunk discovery, analysis, cleanup, render
    // ════════════════════════════════════════════════════════════════

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            renderList.clear();
            return;
        }
        ClientWorld world = client.world;
        long now = System.currentTimeMillis();

        // Trim the rate-limit window (glazed onTick).
        while (!recentAlerts.isEmpty() && now - recentAlerts.peek() > ALERT_WINDOW_MS) {
            recentAlerts.poll();
        }

        // Entity sweep every Entity Scan Ticks (glazed refreshEntitySignals).
        if (baseDetectors.isEnabled() && ++entityTicks >= entityScanTicks.get()) {
            entityTicks = 0;
            refreshEntitySignals(world);
        }

        // Discover newly-loaded chunks within view distance (Lucid's
        // event-bus replacement for glazed's ChunkDataEvent hook).
        int viewDist = client.options.getViewDistance().getValue();
        if (viewDist < 2) viewDist = 8;
        if (viewDist > 32) viewDist = 32;
        int scanR = viewDist + 1;
        int pcx = client.player.getBlockPos().getX() >> 4;
        int pcz = client.player.getBlockPos().getZ() >> 4;
        ChunkManager cm = world.getChunkManager();
        for (int cx = pcx - scanR; cx <= pcx + scanR; cx++) {
            for (int cz = pcz - scanR; cz <= pcz + scanR; cz++) {
                long key = ChunkPos.toLong(cx, cz);
                if (scannedChunks.contains(key) || pendingChunks.contains(key)) continue;
                if (cm.isChunkLoaded(cx, cz)) pendingChunks.add(key);
            }
        }

        // Analyse a bounded number of queued chunks per tick (replaces
        // glazed's executor pool / max-concurrent-scans).
        int budget = chunksPerTick.get();
        Iterator<Long> it = pendingChunks.iterator();
        while (budget > 0 && it.hasNext()) {
            long key = it.next();
            it.remove();
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            if (!cm.isChunkLoaded(cx, cz)) continue;
            analyzeChunk(client, cx, cz);
            budget--;
        }

        // Periodic far-chunk purge (glazed performCleanup).
        if (now - lastCleanupMs > cleanupInterval.get() * 1000L) {
            lastCleanupMs = now;
            performCleanup(pcx, pcz, viewDist);
        }

        // Staggered render-list rebuild (KEEP: Lucid 10-tick cadence).
        if (++tickCounter % 10 == 0) {
            rebuildRenderList(pcx, pcz);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Chunk analysis pipeline
    // ════════════════════════════════════════════════════════════════

    /** Scan one chunk through every enabled track, then evaluate its flags. */
    private void analyzeChunk(MinecraftClient client, int cx, int cz) {
        ClientWorld world = client.world;
        if (world == null) return;
        long key = ChunkPos.toLong(cx, cz);
        if (!scannedChunks.add(key)) return;
        WorldChunk chunk = world.getChunk(cx, cz);
        if (chunk == null) return;

        ChunkAnalysis a = new ChunkAnalysis();
        boolean inEnd = world.getRegistryKey() == net.minecraft.world.World.END;
        scanBlockSignals(world, chunk, cx, cz, a, inEnd);
        if (baseDetectors.isEnabled()) scanBaseSignals(world, chunk, cx, cz, a);
        if (detectAmethyst.isEnabled()) {
            GeodeResult geode = scanGeode(chunk);
            a.geodeHit = geode.hit;
            a.geodeCrystals = geode.crystals;
        }
        if (magicAmethyst.isEnabled()) a.magicSpots = scanMagicAmethyst(world, chunk, cx, cz);
        if (detectStructure.isEnabled()) scanStructure(chunk, cx, cz, a);
        chunkData.put(key, a);
        evaluate(cx, cz, a);
    }

    // ── Track 1: legacy block detectors (scan window Y 0..128) ──────

    /**
     * Walks the chunk's non-empty sections in the glazed block-signal window
     * (world Y 0..128) and classifies each non-air block via
     * {@link #classifyBlock}. Section-based so empty sections cost nothing.
     */
    private void scanBlockSignals(ClientWorld world, WorldChunk chunk, int cx, int cz,
                                  ChunkAnalysis a, boolean inEnd) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = chunk.getBottomY();
        int originX = cx << 4;
        int originZ = cz << 4;
        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            int sectionBottom = bottom + (i << 4);
            if (sectionBottom > BLOCK_SCAN_MAX_Y) continue;
            for (int y = 0; y < 16; y++) {
                int worldY = sectionBottom + y;
                if (worldY < 0 || worldY > BLOCK_SCAN_MAX_Y) continue;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) continue;
                        classifyBlock(world, new BlockPos(originX + x, worldY, originZ + z),
                            state, a, inEnd);
                    }
                }
            }
        }
    }

    /**
     * glazed classifyBlock: trial-chamber blocks are tallied before the
     * exposure check (they gate whole chunks, not per-block), exposed blocks
     * are skipped entirely, and each enabled detector bumps its counter and
     * (re)assigns the highlight kind — the last matching detector wins, as in
     * glazed.
     */
    private void classifyBlock(ClientWorld world, BlockPos pos, BlockState state,
                               ChunkAnalysis a, boolean inEnd) {
        if (ignoreTrialChambers.isEnabled() && isTrialChamberBlock(state)) a.trialChamber++;
        if (ignoreExposed.isEnabled() && isExposed(world, pos)) return;
        BlockKind kind = null;
        if (detectDeepslate.isEnabled() && isPlainDeepslate(state)
            && !isInLongDeepslateRun(world, pos, pos.getY())) {
            a.deepslate++;
            kind = BlockKind.DEEPSLATE;
        }
        if (detectRotatedDeepslate.isEnabled() && isRotatedDeepslate(state)) {
            a.rotatedDeepslate++;
            kind = BlockKind.ROTATED;
        }
        if (detectCobbledDeepslate.isEnabled() && state.isOf(Blocks.COBBLED_DEEPSLATE)) {
            a.cobbledDeepslate++;
            kind = BlockKind.COBBLED;
        }
        if (detectEndStone.isEnabled() && state.isOf(Blocks.END_STONE) && !inEnd) {
            a.endStone++;
            kind = BlockKind.END_STONE;
        }
        if (kind != null && highlightBlocks.isEnabled()) suspiciousBlocks.put(pos, kind);
    }

    /** glazed isTrialChamberBlock. */
    private static boolean isTrialChamberBlock(BlockState state) {
        return state.isOf(Blocks.WAXED_COPPER_BLOCK)
            || state.isOf(Blocks.WAXED_OXIDIZED_COPPER)
            || state.isOf(Blocks.TUFF_BRICKS);
    }

    /** Deepslate in its natural vertical orientation. */
    private static boolean isPlainDeepslate(BlockState state) {
        if (!state.isOf(Blocks.DEEPSLATE)) return false;
        return state.contains(Properties.AXIS) && state.get(Properties.AXIS) == Direction.Axis.Y;
    }

    /** Deepslate rotated off-axis — impossible without a player placing it. */
    private static boolean isRotatedDeepslate(BlockState state) {
        if (!state.isOf(Blocks.DEEPSLATE)) return false;
        return state.contains(Properties.AXIS) && state.get(Properties.AXIS) != Direction.Axis.Y;
    }

    /**
     * True when any of the six neighbours is air or holds a fluid (the block
     * is visible from a cave / surface, so far weaker evidence). glazed's
     * isExposed, with the Y bounds coming from the world.
     */
    private static boolean isExposed(ClientWorld world, BlockPos pos) {
        int minY = world.getBottomY();
        int maxY = minY + world.getHeight();
        BlockPos.Mutable mpos = new BlockPos.Mutable();
        for (Direction dir : Direction.values()) {
            int y = pos.getY() + dir.getOffsetY();
            if (y < minY || y >= maxY) continue;
            mpos.set(pos.getX() + dir.getOffsetX(), y, pos.getZ() + dir.getOffsetZ());
            BlockState neighbour = world.getBlockState(mpos);
            if (neighbour.isAir()) return true;
            if (!neighbour.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    /**
     * Natural deepslate forms long contiguous runs; a player-placed block
     * sits isolated. Walks east / north (and up, only above Y=0) for up to
     * {@code limit} blocks — a run that long means natural generation, so the
     * block is NOT counted as suspicious.
     *
     * <p>NOTE (deviation): glazed compares the run length with {@code > 50},
     * but its walk is capped at {@code limit} (max 50), so the check can
     * never fire and every underground chunk flags on plain deepslate. Lucid
     * ports the evident intent ({@code >= limit}) instead of the dead branch.
     */
    private boolean isInLongDeepslateRun(ClientWorld world, BlockPos pos, int y) {
        int limit = y > -8 ? 50 : 20;
        return runLength(world, pos, Direction.EAST, limit) >= limit
            || runLength(world, pos, Direction.NORTH, limit) >= limit
            || (y > 0 && runLength(world, pos, Direction.UP, limit) >= limit);
    }

    /** Blocks of consecutive plain deepslate walking one direction (incl. start). */
    private int runLength(ClientWorld world, BlockPos pos, Direction dir, int limit) {
        int length = 1;
        BlockPos.Mutable mpos = new BlockPos.Mutable();
        for (int i = 1; i < limit; i++) {
            mpos.set(pos.getX() + dir.getOffsetX() * i,
                pos.getY() + dir.getOffsetY() * i,
                pos.getZ() + dir.getOffsetZ() * i);
            if (!world.getBlockState(mpos).isOf(Blocks.DEEPSLATE)) break;
            length++;
        }
        return length;
    }

    // ── Track 2: base detectors (scan window chunk bottom..60) ──────

    /**
     * Column-by-column scan of the glazed base-signal window (chunk bottom up
     * to Y=60). Everything is counted as raw signals; the weighting and the
     * per-detector gates happen later in {@link #scoreBaseSignals}.
     *
     * <p>The {@code storage} counter — glazed counts chest/hopper block
     * entities — is read from Lucid's {@link SusChunkCache} (packet-fed, so
     * it works under anti-xray); the live chunk block-entity map is the
     * fallback when the cache has no entry.
     */
    private void scanBaseSignals(ClientWorld world, WorldChunk chunk, int cx, int cz,
                                 ChunkAnalysis a) {
        a.storage = countStorage(cx, cz, chunk);
        EntitySignals signals = entitySignals.get(ChunkPos.toLong(cx, cz));
        if (signals != null) {
            a.droppedItems = signals.droppedItems;
            a.polarBears = signals.polarBears;
            if (signals.mobs > 20) a.mobCluster = signals.mobs / 5;
        }

        int bottom = chunk.getBottomY();
        int scanCeiling = Math.min(bottom + chunk.getHeight() - 1, 60);
        int originX = cx << 4;
        int originZ = cz << 4;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int airRun = 0;
                int deepAirRun = 0;
                int oreColumn = 0;
                for (int y = bottom; y <= scanCeiling; y++) {
                    BlockState state = chunk.getBlockState(cursor.set(originX + x, y, originZ + z));
                    Block block = state.getBlock();
                    if (state.isAir()) {
                        airRun++;
                        if (y < 0) deepAirRun++;
                    } else {
                        // Close any air run: long runs below Y=20 are carved rooms.
                        if (airRun >= 12 && y < 20) a.airPocket += airRun / 4.0;
                        airRun = 0;
                        if (y < 0) deepAirRun = 0;
                        if (y < 0) {
                            // Adjacent cobble pair = mined-out tunnel lining.
                            if (block == Blocks.COBBLESTONE || block == Blocks.COBBLED_DEEPSLATE
                                || block == Blocks.MOSSY_COBBLESTONE) {
                                if (x < 15) {
                                    BlockState east = chunk.getBlockState(
                                        cursor.set(originX + x + 1, y, originZ + z));
                                    if (east.getBlock() == block) a.cobbleLines++;
                                }
                            }
                            if (block == Blocks.POLISHED_DEEPSLATE || block == Blocks.DEEPSLATE_BRICKS
                                || block == Blocks.DEEPSLATE_TILES || block == Blocks.CHISELED_DEEPSLATE) {
                                a.deepslateBand++;
                            }
                            if (state.isIn(BlockTags.STAIRS)) a.syntheticStairs++;
                            if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) a.deepChests++;
                        }
                        if (y < 20) {
                            if (block == Blocks.OBSIDIAN) a.obsidian++;
                            if (isPiston(state)) a.pistons++;
                        }
                        if (isOre(block)) {
                            oreColumn++;
                            if (isValuableOre(block)) a.valuableOre++;
                            if (isHiddenOre(chunk, originX + x, y, originZ + z, state, originX, originZ)) {
                                a.hiddenOre += 2;
                            }
                        } else {
                            oreColumn = 0;
                        }
                        // A 4-deep ore column is one "vertical vein" (stacks per 4).
                        if (oreColumn == 4) a.vertVeins++;
                    }
                }
                // A full-depth air column means a 1x1 needle shaft was mined down.
                if (deepAirRun >= 10) a.needles++;
            }
        }
    }

    /**
     * Chest + hopper block entities in the chunk — glazed's {@code storage}
     * signal. Lucid reads it from the packet-fed {@link SusChunkCache}
     * (ChestBlockEntity covers trapped chests, so the type-level check
     * CHEST / TRAPPED_CHEST / HOPPER is exactly glazed's instanceof test);
     * the live chunk's block-entity map is the fallback.
     */
    private int countStorage(int cx, int cz, WorldChunk chunk) {
        SusChunkCache.ChunkEntry entry = SusChunkCache.get().get(cx, cz);
        if (entry != null && !entry.blockEntities.isEmpty()) {
            int count = 0;
            synchronized (entry) {
                for (SusChunkCache.BlockEntityRecord record : entry.blockEntities.values()) {
                    BlockEntityType<?> t = record.type;
                    if (t == BlockEntityType.CHEST || t == BlockEntityType.TRAPPED_CHEST
                        || t == BlockEntityType.HOPPER) {
                        count++;
                    }
                }
            }
            return count;
        }
        int count = 0;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ChestBlockEntity || be instanceof HopperBlockEntity) count++;
        }
        return count;
    }

    /** glazed isOre — every ore block family. */
    private static boolean isOre(Block block) {
        return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
            || block == Blocks.ANCIENT_DEBRIS
            || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
            || block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE
            || block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
            || block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
            || block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
            || block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE
            || block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE;
    }

    /** glazed isValuableOre — the "stash vein" ores. */
    private static boolean isValuableOre(Block block) {
        return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
            || block == Blocks.ANCIENT_DEBRIS
            || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE;
    }

    /** Piston family (base + sticky + head + moving) for the Piston Array signal. */
    private static boolean isPiston(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.PISTON || block == Blocks.STICKY_PISTON
            || block == Blocks.PISTON_HEAD || block == Blocks.MOVING_PISTON;
    }

    /**
     * glazed isHiddenOre: an ore walled in on at least 5 of its 6 faces by
     * solid "container" blocks (cobble, stone, deepslate, obsidian,
     * netherrack) — the classic buried-stash protection pattern. Neighbours
     * outside the chunk are skipped, exactly like glazed.
     */
    private static boolean isHiddenOre(WorldChunk chunk, int x, int y, int z,
                                       BlockState state, int originX, int originZ) {
        Block block = state.getBlock();
        int enclosed = 0;
        BlockPos.Mutable mpos = new BlockPos.Mutable();
        for (Direction dir : Direction.values()) {
            int nx = x + dir.getOffsetX();
            int ny = y + dir.getOffsetY();
            int nz = z + dir.getOffsetZ();
            if (nx < originX || nx > originX + 15 || nz < originZ || nz > originZ + 15) continue;
            Block neighbour = chunk.getBlockState(mpos.set(nx, ny, nz)).getBlock();
            if (neighbour == Blocks.COBBLESTONE || neighbour == Blocks.COBBLED_DEEPSLATE
                || neighbour == Blocks.STONE || neighbour == Blocks.DEEPSLATE
                || neighbour == Blocks.OBSIDIAN || neighbour == Blocks.NETHERRACK) {
                enclosed++;
            }
        }
        return enclosed >= 5;
    }

    // ── Track 3: amethyst (stripped geodes + magic amethyst) ─────────

    /**
     * glazed scanGeode: voxel-scans the geode window (world Y -64..48) into
     * cells (crystal / calcite / basalt / air / solid). Any budding
     * amethyst means an intact geode — never flagged. Otherwise the crystal
     * count, matrix (calcite) count and shape statistics decide:
     *
     * <ul>
     *   <li>no Smart Check — flagged when crystals ≥ minCrystals (40, eased
     *       by Sensitivity);</li>
     *   <li>Smart Check — flagged when crystals ≥ minCrystals AND density ≥
     *       minDensity AND matrix ≥ minMatrix AND spread ≤ maxSpread AND
     *       exposure ≤ maxExposure. Density is crystals per scanned volume,
     *       spread is the mean distance from the crystal centroid, and
     *       exposure is the fraction of crystal faces touching air (a mined-
     *       out geode exposes far more faces than a natural one).</li>
     * </ul>
     */
    private GeodeResult scanGeode(WorldChunk chunk) {
        GeodeResult result = new GeodeResult();
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = chunk.getBottomY();
        // Relative section indices keep the voxel array non-negative even
        // with a negative world bottom (glazed's index math, made explicit).
        int relMin = Math.max(0, (GEODE_MIN_Y - bottom) >> 4);
        int relMax = Math.min(sections.length - 1, (GEODE_MAX_Y - bottom) >> 4);
        if (relMin > relMax) return result;
        int size = relMax - relMin + 1;
        int[][][] cells = new int[16][size * 16][16];
        List<int[]> crystals = new ArrayList<>();
        int matrixCount = 0;
        boolean intact = false;
        for (int i = relMin; i <= relMax; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            int sy = (i - relMin) << 4;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Block block = state.getBlock();
                        if (block == Blocks.BUDDING_AMETHYST) {
                            intact = true;
                            continue;
                        }
                        if (intact) continue;
                        int cell;
                        if (isCrystal(block)) {
                            crystals.add(new int[]{x, (i << 4) + y, z});
                            cell = CELL_CRYSTAL;
                        } else if (block == Blocks.CALCITE) {
                            matrixCount++;
                            cell = CELL_CALCITE;
                        } else if (block == Blocks.SMOOTH_BASALT) {
                            cell = CELL_BASALT;
                        } else if (state.isAir()) {
                            cell = CELL_AIR;
                        } else {
                            cell = CELL_SOLID;
                        }
                        cells[x][sy + y][z] = cell;
                    }
                }
            }
        }
        result.crystals = crystals.size();
        if (intact || crystals.isEmpty()) return result;
        result.matrix = matrixCount;

        if (!smartCheck.isEnabled()) {
            result.hit = crystals.size() >= minCrystals();
            return result;
        }

        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        double sumX = 0, sumY = 0, sumZ = 0;
        for (int[] crystal : crystals) {
            lowest = Math.min(lowest, crystal[1]);
            highest = Math.max(highest, crystal[1]);
            sumX += crystal[0];
            sumY += crystal[1];
            sumZ += crystal[2];
        }
        double density = crystals.size() / (double) (256 * Math.max(1, highest - lowest + 1));
        double centroidX = sumX / crystals.size();
        double centroidY = sumY / crystals.size();
        double centroidZ = sumZ / crystals.size();
        double spread = 0;
        for (int[] crystal : crystals) {
            double dx = crystal[0] - centroidX;
            double dy = crystal[1] - centroidY;
            double dz = crystal[2] - centroidZ;
            spread += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        spread /= crystals.size();
        int openFaces = 0;
        int buriedFaces = 0;
        int cellMinY = relMin << 4;
        int cellMaxY = (relMax << 4) + 16;
        for (int[] crystal : crystals) {
            for (Direction dir : Direction.values()) {
                int nx = crystal[0] + dir.getOffsetX();
                int ny = crystal[1] + dir.getOffsetY();
                int nz = crystal[2] + dir.getOffsetZ();
                if (nx < 0 || nx >= 16 || nz < 0 || nz >= 16) continue;
                if (ny < cellMinY || ny >= cellMaxY) continue;
                int cell = cells[nx][ny][nz];
                if (cell == CELL_AIR) openFaces++;
                else if (cell >= CELL_CALCITE) buriedFaces++;
            }
        }
        int totalFaces = openFaces + buriedFaces;
        double exposure = totalFaces > 0 ? (double) openFaces / totalFaces : 1.0;
        result.hit = crystals.size() >= minCrystals()
            && density >= minDensity()
            && matrixCount >= minMatrix()
            && spread <= maxSpread()
            && exposure <= maxExposure();
        return result;
    }

    private static boolean isCrystal(Block block) {
        return block == Blocks.AMETHYST_CLUSTER || block == Blocks.LARGE_AMETHYST_BUD
            || block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.SMALL_AMETHYST_BUD
            || block == Blocks.AMETHYST_BLOCK;
    }

    // Sensitivity-eased geode thresholds (glazed's exact easing math).
    private int minCrystals() { return (int) (40 - (sensitivity.get() - 1) * 3.5555555555555554); }
    private double minDensity() { return 0.015 - (sensitivity.get() - 1) * 0.0013333333333333333; }
    private int minMatrix() { return (int) (15 - (sensitivity.get() - 1) * 1.3333333333333333); }
    private double maxExposure() { return 0.08 + (sensitivity.get() - 1) * 0.027; }
    private double maxSpread() { return 6.0 + (sensitivity.get() - 1) * 0.4444444444444444; }

    /**
     * glazed scanMagicAmethyst (the "ig" magic detector): finds the geode
     * centre (mean of amethyst block positions in the geode window), then
     * probes a cube of the configured radius around it via the WORLD (the
     * cube crosses chunk borders). A cell counts as dark when it is air or an
     * amethyst cluster with zero block light, none of its six neighbours is
     * brighter than {@link #MAGIC_NEIGHBOUR_LIGHT}, and (two-step expansion)
     * the neighbours of its first dark neighbour are equally dark. Enough
     * dark cells means unlit spawnable space around the geode — a farmed
     * geode.
     */
    private int scanMagicAmethyst(ClientWorld world, WorldChunk chunk, int cx, int cz) {
        int bottom = chunk.getBottomY();
        int minY = Math.max(bottom, GEODE_MIN_Y);
        int maxY = Math.min(bottom + chunk.getHeight() - 1, GEODE_MAX_Y);
        double sumX = 0, sumY = 0, sumZ = 0;
        int count = 0;
        int originX = cx << 4;
        int originZ = cz << 4;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int y = minY; y <= maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Block block = chunk.getBlockState(cursor.set(originX + x, y, originZ + z)).getBlock();
                    if (block == Blocks.AMETHYST_BLOCK || block == Blocks.BUDDING_AMETHYST) {
                        sumX += originX + x;
                        sumY += y;
                        sumZ += originZ + z;
                        count++;
                    }
                }
            }
        }
        if (count == 0) return 0;
        int centerX = (int) (sumX / count);
        int centerY = (int) (sumY / count);
        int centerZ = (int) (sumZ / count);

        int radius = magicScanRadius.get();
        int worldBottom = world.getBottomY();
        int worldTop = worldBottom + world.getHeight();
        int spots = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int wx = centerX + x;
                    int wy = centerY + y;
                    int wz = centerZ + z;
                    if (wy < worldBottom || wy >= worldTop) continue;
                    BlockState state = world.getBlockState(cursor.set(wx, wy, wz));
                    Block block = state.getBlock();
                    if (block != Blocks.AIR && block != Blocks.AMETHYST_CLUSTER) continue;
                    if (blockLight(world, cursor) != 0) continue;
                    boolean lit = false;
                    BlockPos darkAir = null;
                    for (Direction dir : Direction.values()) {
                        BlockPos neighbour = new BlockPos(wx + dir.getOffsetX(),
                            wy + dir.getOffsetY(), wz + dir.getOffsetZ());
                        int light = blockLight(world, neighbour);
                        if (light > MAGIC_NEIGHBOUR_LIGHT) {
                            lit = true;
                            break;
                        }
                        if (darkAir == null && light == 0 && world.getBlockState(neighbour).isAir()) {
                            darkAir = neighbour;
                        }
                    }
                    if (lit) continue;
                    if (darkAir != null) {
                        for (Direction dir : Direction.values()) {
                            BlockPos second = new BlockPos(darkAir.getX() + dir.getOffsetX(),
                                darkAir.getY() + dir.getOffsetY(), darkAir.getZ() + dir.getOffsetZ());
                            if (blockLight(world, second) > MAGIC_NEIGHBOUR_LIGHT) {
                                lit = true;
                                break;
                            }
                        }
                    }
                    if (!lit) spots++;
                }
            }
        }
        return spots;
    }

    /** Block-light lookup (Yarn: LightingProvider -> ChunkLightingView). */
    private static int blockLight(ClientWorld world, BlockPos pos) {
        return world.getLightingProvider().get(LightType.BLOCK).getLightLevel(pos);
    }

    // ── Track 4: structure score (base chance %) ─────────────────────

    /**
     * glazed scanStructureChunk: block entities first (beehives + bee count,
     * workstations), then a block pass from Y=0 upward for repeaters (and
     * their POWERED state), vines, cobbled-deepslate floors at Y 0..20, and
     * seagrass / flowers at Y ≤ 70.
     */
    private void scanStructure(WorldChunk chunk, int cx, int cz, ChunkAnalysis a) {
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            BlockState state = chunk.getBlockState(be.getPos());
            Block block = state.getBlock();
            if (block == Blocks.BEEHIVE || block == Blocks.BEE_NEST) {
                a.beehives++;
                if (be instanceof BeehiveBlockEntity hive) a.bees += hive.getBeeCount();
            } else if (isWorkstation(block)) {
                a.workstations++;
            }
        }
        int topY = Math.min(chunk.getBottomY() + chunk.getHeight() - 1, 320);
        int originX = cx << 4;
        int originZ = cz << 4;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int y = 0; y <= topY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockState state = chunk.getBlockState(cursor.set(originX + x, y, originZ + z));
                    Block block = state.getBlock();
                    if (block == Blocks.REPEATER) {
                        a.repeaters++;
                        if (state.get(Properties.POWERED)) a.poweredRepeaters++;
                    }
                    if (block == Blocks.VINE) a.vines++;
                    if (block == Blocks.COBBLED_DEEPSLATE && y >= 0 && y <= 20) a.deepslateFloor++;
                    if (y <= 70 && (block == Blocks.SEAGRASS || block == Blocks.TALL_SEAGRASS)) a.seagrass++;
                    if (y <= 70 && state.isIn(BlockTags.FLOWERS)) a.flowers++;
                    if (isWorkstation(block)) a.workstations++;
                }
            }
        }
    }

    /** glazed isWorkstation — storage + crafting station blocks. */
    private static boolean isWorkstation(Block block) {
        return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL
            || block == Blocks.ENDER_CHEST || block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE
            || block == Blocks.SMOKER || block == Blocks.CRAFTING_TABLE || block == Blocks.ENCHANTING_TABLE
            || block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL;
    }

    /**
     * Capped, per-detector-gated structure score (glazed structureScore):
     * repeaters x16 + powered x28 (cap 90), hives x22 + bees x10 (cap 90),
     * deepslate floor /2 (cap 65), vines /4 (cap 55), seagrass (cap 70),
     * flowers x12 (cap 75), workstations x18 (cap 100).
     */
    private int structureScore(ChunkAnalysis a) {
        int score = 0;
        if (detectRepeaters.isEnabled()) {
            score += Math.min(90, a.repeaters * 16 + a.poweredRepeaters * 28);
        }
        if (detectBeehives.isEnabled()) {
            score += Math.min(90, a.beehives * 22 + a.bees * 10);
        }
        if (detectDeepslateFloor.isEnabled()) {
            score += Math.min(65, a.deepslateFloor / 2);
        }
        if (detectVines.isEnabled()) {
            score += Math.min(55, a.vines / 4);
        }
        if (detectSeagrass.isEnabled()) {
            score += Math.min(70, a.seagrass);
        }
        if (detectFlowers.isEnabled()) {
            score += Math.min(75, a.flowers * 12);
        }
        if (detectWorkstations.isEnabled()) {
            score += Math.min(100, a.workstations * 18);
        }
        return score;
    }

    // ════════════════════════════════════════════════════════════════
    //  Scoring + flag evaluation (glazed scoreBaseSignals / evaluate)
    // ════════════════════════════════════════════════════════════════

    /**
     * Converts raw signals into the weighted base score with evidence tags.
     * Every contribution is gated by its detector toggle (except Storage,
     * which glazed leaves ungated at ≥ 3). Mirrors glazed's exact weights.
     */
    private int scoreBaseSignals(ChunkAnalysis a) {
        int score = 0;
        List<String> tags = a.baseTags;
        if (detectCobbleLines.isEnabled() && a.cobbleLines >= 3) {
            int v = Math.min(a.cobbleLines, 6);
            score += v;
            tags.add("CobbleLine(" + v + ")");
        }
        if (detectDeepslateBand.isEnabled() && a.deepslateBand >= 4) {
            int v = (int) Math.min(a.deepslateBand / 2.0, 5);
            score += v;
            tags.add("Deepslate(" + v + ")");
        }
        if (detectObsidianBackbone.isEnabled() && a.obsidian >= 3) {
            int v = Math.min(a.obsidian, 6);
            score += v;
            tags.add("Obsidian(" + v + ")");
        }
        if (detectVeinIntegrity.isEnabled() && a.valuableOre >= 1 && a.storage >= 1) {
            score += 4;
            tags.add("VeinInt");
        }
        if (detectVerticalVein.isEnabled() && a.vertVeins >= 2) {
            score += a.vertVeins;
            tags.add("VertVein(" + a.vertVeins + ")");
        }
        if (detectNeedleHole.isEnabled() && a.needles >= 2) {
            int v = a.needles * 2;
            score += v;
            tags.add("Needle(" + v + ")");
        }
        if (detectHiddenOre.isEnabled() && a.hiddenOre >= 2) {
            score += a.hiddenOre;
            tags.add("HiddenOre(" + a.hiddenOre + ")");
        }
        if (detectPistonArray.isEnabled() && a.pistons >= 3) {
            int v = Math.min(a.pistons, 8);
            score += v;
            tags.add("PistonArray(" + v + ")");
        }
        if (detectMobCluster.isEnabled() && a.mobCluster >= 2) {
            score += a.mobCluster;
            tags.add("MobCluster(" + a.mobCluster * 5 + ")");
        }
        if (detectDroppedItems.isEnabled() && a.droppedItems >= 2) {
            int v = a.droppedItems * 2;
            score += v;
            tags.add("Dropped(" + v + ")");
        }
        if (detectAirPocket.isEnabled() && a.airPocket >= 5) {
            int v = (int) (a.airPocket / 3);
            score += v;
            tags.add("AirPocket(" + v + ")");
        }
        if (detectSyntheticStairs.isEnabled() && a.syntheticStairs >= 5) {
            int v = a.syntheticStairs / 3;
            score += v;
            tags.add("Stairs(" + v + ")");
        }
        if (detectPolarBear.isEnabled() && a.polarBears > 0) {
            score += a.polarBears;
            tags.add("PolarBear");
        }
        if (detectChestCluster.isEnabled() && a.deepChests >= 10) {
            int v = Math.min(a.deepChests, 12);
            score += v;
            tags.add("ChestCluster(" + v + ")");
        }
        // Ungated in glazed: plain storage density always scores.
        if (a.storage >= 3) {
            score += a.storage;
            tags.add("Storage(" + a.storage + ")");
        }
        return score;
    }

    /**
     * Applies every flag rule to a fresh analysis (glazed evaluate):
     * trial-chamber skip first, then block-detector reasons, base score,
     * amethyst, and structure chance — alerting on NEW flags only.
     */
    private void evaluate(int cx, int cz, ChunkAnalysis a) {
        long key = ChunkPos.toLong(cx, cz);

        // Trial chamber: glazed drops every flag for the chunk and stops.
        if (ignoreTrialChambers.isEnabled() && a.trialChamber >= trialChamberThreshold.get()) {
            forgetChunk(key);
            notifyChunk(key, cx, cz, "Trial chamber - Copper/Tuff blocks: " + a.trialChamber,
                true);
            return;
        }

        // Legacy block detectors — reasons string + flag/unflag.
        StringBuilder reasons = new StringBuilder();
        if (detectDeepslate.isEnabled() && a.deepslate >= deepslateThreshold.get()) {
            reasons.append("Deepslate[").append(a.deepslate).append("] ");
        }
        if (detectCobbledDeepslate.isEnabled() && a.cobbledDeepslate >= cobbledDeepslateThreshold.get()) {
            reasons.append("CobbledDeepslate[").append(a.cobbledDeepslate).append("] ");
        }
        if (detectRotatedDeepslate.isEnabled() && a.rotatedDeepslate >= rotatedDeepslateThreshold.get()) {
            reasons.append("RotatedDeepslate[").append(a.rotatedDeepslate).append("] ");
        }
        if (detectEndStone.isEnabled() && a.endStone >= endStoneThreshold.get()) {
            reasons.append("EndStone[").append(a.endStone).append("] ");
        }
        if (reasons.length() > 0) {
            String reason = reasons.toString().trim();
            if (flaggedChunks.put(key, reason) == null) {
                notifyChunk(key, cx, cz, "Base detected - " + reason, false);
            }
        } else if (flaggedChunks.remove(key) != null) {
            // Un-flagged chunks may re-alert later (glazed clears the cooldown).
            notificationTimes.remove(key);
        }

        // Base detectors — glazed's signature alert.
        if (baseDetectors.isEnabled()) {
            int score = scoreBaseSignals(a);
            a.baseScore = score;
            if (score >= minScore.get()) {
                String tag = "S:" + score + " [" + String.join(" | ", a.baseTags) + "]";
                if (baseFlaggedChunks.put(key, tag) == null) {
                    notifyChunk(key, cx, cz, "BOII we MAYY Be RICHHH " + tag, false);
                }
            }
        }

        // Amethyst — stripped geode and/or magic amethyst.
        if (detectAmethyst.isEnabled()) {
            boolean stripped = a.geodeHit;
            boolean magic = magicAmethyst.isEnabled() && a.magicSpots >= magicThreshold.get();
            if (stripped || magic) {
                List<String> tags = new ArrayList<>();
                if (stripped) tags.add("StrippedGeode(" + a.geodeCrystals + ")");
                if (magic) tags.add("MagicAmethyst(" + a.magicSpots + ")");
                String tag = String.join(" ", tags);
                if (amethystChunks.put(key, tag) == null) {
                    notifyChunk(key, cx, cz, "Amethyst - " + tag, false);
                }
            }
        }

        // Structure — base chance percent.
        if (detectStructure.isEnabled()) {
            int score = structureScore(a);
            a.structureScore = score;
            a.structureChance = Math.min(99, Math.max(20, 25 + score / 2));
            if (a.structureChance >= minBaseChance.get() && score > 0) {
                String tag = a.poweredRepeaters >= 3
                    ? "Repeater chunk"
                    : "Base chance " + a.structureChance + "% (score " + score + ")";
                if (structureChunks.put(key, tag) == null) {
                    notifyChunk(key, cx, cz, tag, false);
                }
            }
        }
    }

    /** glazed forget(): drop every flag + cached analysis for a chunk.
     *  Deviation: the chunk stays in {@code scannedChunks} — Lucid's tick
     *  sweep would otherwise instantly re-queue the loaded chunk forever. */
    private void forgetChunk(long key) {
        flaggedChunks.remove(key);
        baseFlaggedChunks.remove(key);
        amethystChunks.remove(key);
        structureChunks.remove(key);
        chunkData.remove(key);
        notificationTimes.remove(key);
        pendingChunks.remove(key);
    }

    // ════════════════════════════════════════════════════════════════
    //  Entity sweep (glazed refreshEntitySignals)
    // ════════════════════════════════════════════════════════════════

    /**
     * Snapshots per-chunk entity signals from the rendered entity list:
     * polar bears count 3 each, item entities below Y=0 count as dropped
     * loot, and every non-player entity feeds the mob-cluster count
     * (&gt; 20 mobs in one chunk = grinder).
     */
    private void refreshEntitySignals(ClientWorld world) {
        Map<Long, EntitySignals> fresh = new HashMap<>();
        for (Entity entity : world.getEntities()) {
            if (entity == null) continue;
            BlockPos pos = entity.getBlockPos();
            long key = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
            EntitySignals signals = fresh.computeIfAbsent(key, k -> new EntitySignals());
            if (entity instanceof PolarBearEntity) {
                signals.polarBears += 3;
            } else if (entity instanceof ItemEntity && entity.getY() < 0) {
                signals.droppedItems++;
            }
            if (!(entity instanceof PlayerEntity)) signals.mobs++;
        }
        entitySignals.clear();
        entitySignals.putAll(fresh);
    }

    // ════════════════════════════════════════════════════════════════
    //  Alerts (glazed notify semantics, chat channel)
    // ════════════════════════════════════════════════════════════════

    /**
     * glazed notify(): 45s per-chunk cooldown (skipped for trial chambers),
     * Max Alerts per rolling minute, message format
     * {@code SusChunkFinder [x, z] - details}. The chat line is gated by
     * Notify Mode and by Chat Alerts / Trial Chamber Alerts.
     */
    private void notifyChunk(long key, int cx, int cz, String details,
                             boolean trialChamber) {
        long now = System.currentTimeMillis();
        if (recentAlerts.size() >= maxAlerts.get()) return;
        Long last = notificationTimes.get(key);
        if (!trialChamber && last != null && now - last < NOTIFICATION_COOLDOWN_MS) return;

        String message = "SusChunkFinder [" + cx + ", " + cz + "] - " + details;
        boolean allowChat = trialChamber ? trialChamberAlerts.isEnabled() : chatAlerts.isEnabled();
        MinecraftClient client = MinecraftClient.getInstance();
        // A stale saved value (an old channel name) still alerts, so removing
        // the alert channel can never silence an existing config.
        if (!"None".equals(notifyMode.get()) && allowChat && client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }

        recentAlerts.offer(now);
        notificationTimes.put(key, now);
        LucidClient.LOGGER.info("[SusChunkFinder] {}", message);
    }

    // ════════════════════════════════════════════════════════════════
    //  Cleanup (glazed performCleanup)
    // ════════════════════════════════════════════════════════════════

    /**
     * Drops chunk state beyond view distance (+5 for flags/analysis, +3 for
     * the scanned/pending sets) and block highlights beyond view-distance
     * blocks + 80, so long sessions stay bounded.
     */
    private void performCleanup(int pcx, int pcz, int viewDist) {
        int flagRange = viewDist + 5;
        removeFarChunks(flaggedChunks, pcx, pcz, flagRange);
        removeFarChunks(baseFlaggedChunks, pcx, pcz, flagRange);
        removeFarChunks(amethystChunks, pcx, pcz, flagRange);
        removeFarChunks(structureChunks, pcx, pcz, flagRange);
        removeFarChunks(chunkData, pcx, pcz, flagRange);
        removeFarChunks(entitySignals, pcx, pcz, flagRange);
        removeFarChunks(notificationTimes, pcx, pcz, flagRange);
        int scanRange = viewDist + 3;
        scannedChunks.removeIf(key -> {
            int dx = ChunkPos.getPackedX(key) - pcx;
            int dz = ChunkPos.getPackedZ(key) - pcz;
            return Math.abs(dx) > scanRange || Math.abs(dz) > scanRange;
        });
        pendingChunks.removeIf(key -> {
            int dx = ChunkPos.getPackedX(key) - pcx;
            int dz = ChunkPos.getPackedZ(key) - pcz;
            return Math.abs(dx) > scanRange || Math.abs(dz) > scanRange;
        });
        double maxDist = viewDist * 16.0 + 80.0;
        suspiciousBlocks.keySet().removeIf(pos -> {
            double dx = pos.getX() - (pcx << 4);
            double dz = pos.getZ() - (pcz << 4);
            return dx * dx + dz * dz > maxDist * maxDist;
        });
    }

    /** Removes every chunk key further than {@code range} (chebyshev) from the player. */
    private static void removeFarChunks(Map<Long, ?> map, int pcx, int pcz, int range) {
        Iterator<Long> it = map.keySet().iterator();
        while (it.hasNext()) {
            long key = it.next();
            int dx = ChunkPos.getPackedX(key) - pcx;
            int dz = ChunkPos.getPackedZ(key) - pcz;
            if (Math.abs(dx) > range || Math.abs(dz) > range) it.remove();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Render — 2D screen-space boxes (KEEP lucid, glazed categories)
    // ════════════════════════════════════════════════════════════════

    /**
     * Rebuilds the render list from the four flag maps. Each category is
     * sorted nearest-first and capped at Max Chunks Render (glazed caps per
     * category); the label shows the chunk coords plus the category's
     * evidence (truncated for the box tag).
     */
    private void rebuildRenderList(int pcx, int pcz) {
        renderList.clear();
        collectFlags(flaggedChunks, chunkColor.getColor(), "B", pcx, pcz);
        collectFlags(baseFlaggedChunks, baseChunkColor.getColor(), "S", pcx, pcz);
        collectFlags(amethystChunks, amethystChunkColor.getColor(), "G", pcx, pcz);
        collectFlags(structureChunks, structureChunkColor.getColor(), "R", pcx, pcz);
    }

    /** Sort one category nearest-first, cap it, and append to the render list. */
    private void collectFlags(Map<Long, String> map, int color, String prefix, int pcx, int pcz) {
        if (map.isEmpty()) return;
        List<long[]> entries = new ArrayList<>(map.size());
        for (Map.Entry<Long, String> e : map.entrySet()) {
            long key = e.getKey();
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            int dx = cx - pcx;
            int dz = cz - pcz;
            entries.add(new long[]{key, (long) (dx * dx + dz * dz)});
        }
        entries.sort((a, b) -> Long.compare(a[1], b[1]));
        int cap = Math.min(maxChunksRender.get(), entries.size());
        for (int i = 0; i < cap; i++) {
            long key = entries.get(i)[0];
            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            String label = shorten("(" + prefix + ") " + map.get(key), 30);
            renderList.add(new FlaggedChunk(cx, cz, color, label));
        }
    }

    /** Truncate a render label with an ellipsis. */
    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /**
     * 2D screen-space overlay (KEEP lucid's pipeline — same projection math
     * as before): one box per flagged chunk (32-block slab centred on the
     * player's eye Y so it's visible from any angle), colored per glazed
     * category with the evidence label, plus per-block suspicious highlights
     * capped at Max Blocks Render.
     */
    public void draw(DrawContext ctx, float tickDelta) {
        boolean wantChunks = showBoxes.isEnabled() && !renderList.isEmpty();
        boolean wantBlocks = highlightBlocks.isEnabled() && !suspiciousBlocks.isEmpty();
        if (!wantChunks && !wantBlocks) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.gameRenderer == null) return;

        Camera camera = client.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return;
        Vec3d camPos = camera.getCameraPos();

        // View matrix via inverse camera quaternion (same pipeline as EntityESP).
        Matrix4f viewMatrix = new Matrix4f();
        viewMatrix.identity();
        Quaternionf invRot = new Quaternionf(camera.getRotation()).invert();
        viewMatrix.rotate(invRot);
        viewMatrix.translate((float) -camPos.x, (float) -camPos.y, (float) -camPos.z);

        Matrix4f projMatrix = client.gameRenderer.getBasicProjectionMatrix(tickDelta);
        Matrix4f vpMatrix = new Matrix4f();
        vpMatrix.set(projMatrix);
        vpMatrix.mul(viewMatrix);

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        TextRenderer tr = client.textRenderer;

        // Chunk slabs: 32-block-tall slab centred on the player's Y.
        if (wantChunks) {
            int playerY = client.player.getBlockPos().getY();
            int bottomY = playerY - 16;
            int topY = playerY + 16;
            for (FlaggedChunk fc : renderList) {
                int x0 = fc.chunkX() * 16;
                int z0 = fc.chunkZ() * 16;
                drawProjectedBox(ctx, vpMatrix, screenW, screenH, tr,
                    x0, bottomY, z0, x0 + 16, topY, z0 + 16, fc.color(), fc.label());
            }
        }

        // Suspicious-block highlights (glazed highlight-blocks).
        if (wantBlocks) {
            int drawn = 0;
            int cap = maxBlocksRender.get();
            int viewDist = client.options.getViewDistance().getValue();
            if (viewDist < 2) viewDist = 8;
            if (viewDist > 32) viewDist = 32;
            double maxDist = viewDist * 16.0 + 80.0;
            double maxDist2 = maxDist * maxDist;
            for (Map.Entry<BlockPos, BlockKind> e : suspiciousBlocks.entrySet()) {
                if (drawn >= cap) break;
                BlockPos bp = e.getKey();
                double dx = bp.getX() + 0.5 - camPos.x;
                double dy = bp.getY() + 0.5 - camPos.y;
                double dz = bp.getZ() + 0.5 - camPos.z;
                if (dx * dx + dy * dy + dz * dz > maxDist2) continue;
                int color;
                switch (e.getValue()) {
                    case COBBLED -> color = cobbledColor.getColor();
                    case ROTATED -> color = rotatedColor.getColor();
                    case END_STONE -> color = endStoneColor.getColor();
                    default -> color = deepslateColor.getColor();
                }
                if (drawProjectedBox(ctx, vpMatrix, screenW, screenH, tr,
                    bp.getX(), bp.getY(), bp.getZ(),
                    bp.getX() + 1, bp.getY() + 1, bp.getZ() + 1, color, null)) {
                    drawn++;
                }
            }
        }
    }

    /**
     * Projects an AABB's 8 corners to screen space and draws a 1px outline
     * box (plus an optional label tag above it). Shared by the chunk-slab
     * and block-highlight passes.
     *
     * @return true when the box was actually drawn (used for the block cap)
     */
    private boolean drawProjectedBox(DrawContext ctx, Matrix4f vpMatrix, int screenW, int screenH,
                                     TextRenderer tr, double x0, double y0, double z0,
                                     double x1, double y1, double z1, int color, String label) {
        Vec3d[] corners = {
            new Vec3d(x0, y0, z0), new Vec3d(x1, y0, z0),
            new Vec3d(x1, y0, z1), new Vec3d(x0, y0, z1),
            new Vec3d(x0, y1, z0), new Vec3d(x1, y1, z0),
            new Vec3d(x1, y1, z1), new Vec3d(x0, y1, z1),
        };
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        int valid = 0;
        for (Vec3d c : corners) {
            Vector4f v = new Vector4f((float) c.x, (float) c.y, (float) c.z, 1f);
            vpMatrix.transform(v);
            if (v.w <= 0.05f) continue;
            float ndcZ = v.z / v.w;
            if (ndcZ < -1f || ndcZ > 1f) continue;
            float ndcX = v.x / v.w;
            float ndcY = v.y / v.w;
            valid++;
            float sx = (ndcX * 0.5f + 0.5f) * screenW;
            float sy = (0.5f - ndcY * 0.5f) * screenH;
            if (sx < minX) minX = sx;
            if (sx > maxX) maxX = sx;
            if (sy < minY) minY = sy;
            if (sy > maxY) maxY = sy;
        }
        if (valid == 0) return false;

        int bx = (int) minX, by = (int) minY;
        int bw = (int) (maxX - minX);
        int bh = (int) (maxY - minY);
        if (bw < 3) bw = 3;
        if (bh < 3) bh = 3;

        // Box outline (4 thin filled rects, 1px thick).
        ctx.fill(bx, by, bx + bw, by + 1, color);
        ctx.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        ctx.fill(bx, by, bx + 1, by + bh, color);
        ctx.fill(bx + bw - 1, by, bx + bw, by + bh, color);

        if (label != null && !label.isEmpty()) {
            int labelW = tr.getWidth(label);
            int labelH = tr.fontHeight + 4;
            int tx = bx + (bw - labelW) / 2;
            if (tx < 2) tx = 2;
            int ty = by - labelH - 1;
            if (ty < 2) ty = by + 1; // flip below if no room
            int bg = Colors.withAlpha(0x111111, 210);
            ctx.fill(tx - 4, ty, tx + labelW + 4, ty + labelH, bg);
            ctx.fill(tx - 4, ty, tx + labelW + 4, ty + 1, color);
            ctx.fill(tx - 4, ty + labelH - 1, tx + labelW + 4, ty + labelH, color);
            ctx.fill(tx - 4, ty, tx - 3, ty + labelH, color);
            ctx.fill(tx + labelW + 3, ty, tx + labelW + 4, ty + labelH, color);
            RenderUtils.drawText(ctx, tr, label, tx, ty + 2,
                Colors.withAlpha(Colors.TEXT_INVERTED, 255), false);
        }
        return true;
    }
}
