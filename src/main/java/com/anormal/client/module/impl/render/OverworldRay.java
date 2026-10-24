package com.anormal.client.module.impl.render;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.lucid.IntSetting;
import com.anormal.client.lucid.WorldRender3D;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * OverworldRay — exposed-scan overworld-ore tracker with a persistent
 * cross-session cache. Reads only block states the server was forced to
 * reveal (cave walls, ravine edges, player-mined neighbors) and accumulates
 * every revealed ore to disk so the ore map grows across sessions. No world
 * seed required, no anti-xray bypass needed.
 *
 * <h3>Why this shape (and not a seed-sim)</h3>
 * A seed-sim would require the world seed, and on DonutSMP seedcracking is
 * blocked (both structure AND bedrock cracking), so the seed is unobtainable.
 * This module uses the only approach that survives: read the blocks the
 * server legitimately sends (the exposed scan), and persist them so a
 * single session's finds accumulate into a real asset over time. PaperMC
 * anti-xray (Engine Mode 2/3) hides ores by replacing them with stone, but
 * it must reveal the real block once it's adjacent to air the player can
 * see — so mining into a wall / flying past a cave exposes real ores, which
 * this module captures and saves.
 *
 * <h3>What it renders</h3>
 * <ul><li><b>Confirmed (per-ore color, bright):</b> an ore block the server
 * revealed this scan — real, mine it.</li>
 * <li><b>Cached (per-ore color, dim):</b> the same ore found in a prior
 * session (loaded from disk at enable), still in the rendered set but not
 * currently visible in the live scan. Confirmed upgrades cached at the same
 * position.</li></ul>
 *
 * <p><b>Mined ores disappear:</b> the scan re-verifies every cached position
 * against the live block — once you mine an ore (or it's replaced), its
 * cache entry is deleted, the dim box stops rendering, and the next save
 * drops it from disk. Stale entries from older sessions self-heal the same
 * way as soon as their chunk is scanned.
 *
 * <p>Each ore type has its own toggle + color. The high-value ores (diamond,
 * emerald) are on by default; the abundant ones (coal, iron) are off so they
 * don't flood the screen.
 */
public class OverworldRay extends Module {

    // ════════════════════════════════════════════════════════════════
    //  Settings — scan
    // ════════════════════════════════════════════════════════════════

    /** Chunk radius around the player to scan. */
    private final IntSetting range = addSetting(
        new IntSetting("Range", "Chunk radius to scan around the player.",
            1, 8, 4, 1));
    /** Chunks re-verified per tick. The scan is AMORTIZED: a rotating
     *  cursor re-scans a couple chunks every tick instead of every chunk
     *  in one frame (the old 1Hz full sweep caused a visible lag spike
     *  each second). At 2/tick a range-4 grid (81 chunks) fully refreshes
     *  in ~2s with zero per-tick spikes. */
    private final IntSetting chunksPerTick = addSetting(
        new IntSetting("Chunks Per Tick", "How many chunks the rotating scanner re-verifies each tick. Higher = faster refresh, more per-tick cost.",
            1, 16, 2, 1));
    /** Hard Y clamp for the exposed scan's section walk. Defaults cover the
     *  full overworld ore range (-64..320 in 1.21); narrow to focus on
     *  diamond/emerald depths (e.g. -64..16) to reduce noise. */
    private final IntSetting yMin = addSetting(
        new IntSetting("Y Min", "Hard lower Y clamp for the exposed scan.",
            -64, 320, -64, 1));
    private final IntSetting yMax = addSetting(
        new IntSetting("Y Max", "Hard upper Y clamp for the exposed scan.",
            -64, 320, 320, 1));

    /** The exposed-scan pass: walk loaded chunks' Y-band and add any real
     *  ore the server revealed. Free — no seed, no anti-cheat signal. */
    private final BooleanSetting exposedScan = addSetting(
        new BooleanSetting("Exposed Scan", "Scan loaded chunks for already-revealed ores (the only approach that works without a seed). Leave ON.",
            true));

    /** Persist every revealed ore to disk and reload it next session, so the
     *  ore map accumulates over time. Per-server + per-dimension. */
    private final BooleanSetting persistentCache = addSetting(
        new BooleanSetting("Persistent Cache", "Save every revealed ore to disk and reload it next session, so your ore map grows across sessions. Per-server + per-dimension.",
            true));

    // ════════════════════════════════════════════════════════════════
    //  Settings — per-ore toggles + colors
    //  State ordinal encodes precedence (see STATE_* below). Each ore has a
    //  toggle (on = scan for it + render it) and a color. High-value ores
    //  default ON; abundant ores default OFF to avoid screen flooding.
    // ════════════════════════════════════════════════════════════════

    private final BooleanSetting showDiamond = addSetting(
        new BooleanSetting("Diamond", "Highlight revealed Diamond Ore.",
            true));
    private final ColorSetting diamondColor = addSetting(
        new ColorSetting("Diamond Color", "Color for diamond-ore positions. Click to cycle.",
            0xFF33EBFF));

    private final BooleanSetting showEmerald = addSetting(
        new BooleanSetting("Emerald", "Highlight revealed Emerald Ore.",
            true));
    private final ColorSetting emeraldColor = addSetting(
        new ColorSetting("Emerald Color", "Color for emerald-ore positions. Click to cycle.",
            0xFF17DD62));

    private final BooleanSetting showGold = addSetting(
        new BooleanSetting("Gold", "Highlight revealed Gold Ore.",
            true));
    private final ColorSetting goldColor = addSetting(
        new ColorSetting("Gold Color", "Color for gold-ore positions. Click to cycle.",
            0xFFFFEE00));

    private final BooleanSetting showLapis = addSetting(
        new BooleanSetting("Lapis", "Highlight revealed Lapis Lazuli Ore.",
            false));
    private final ColorSetting lapisColor = addSetting(
        new ColorSetting("Lapis Color", "Color for lapis-ore positions. Click to cycle.",
        0xFF1E5BD6));

    private final BooleanSetting showRedstone = addSetting(
        new BooleanSetting("Redstone", "Highlight revealed Redstone Ore.",
            false));
    private final ColorSetting redstoneColor = addSetting(
        new ColorSetting("Redstone Color", "Color for redstone-ore positions. Click to cycle.",
            0xFFFF0000));

    private final BooleanSetting showIron = addSetting(
        new BooleanSetting("Iron", "Highlight revealed Iron Ore (abundant).",
            false));
    private final ColorSetting ironColor = addSetting(
        new ColorSetting("Iron Color", "Color for iron-ore positions. Click to cycle.",
            0xFFD8AF93));

    private final BooleanSetting showCoal = addSetting(
        new BooleanSetting("Coal", "Highlight revealed Coal Ore (very abundant).",
            false));
    private final ColorSetting coalColor = addSetting(
        new ColorSetting("Coal Color", "Color for coal-ore positions. Click to cycle.",
            0xFF2B2B2B));

    private final BooleanSetting showCopper = addSetting(
        new BooleanSetting("Copper", "Highlight revealed Copper Ore (abundant).",
            false));
    private final ColorSetting copperColor = addSetting(
        new ColorSetting("Copper Color", "Color for copper-ore positions. Click to cycle.",
            0xFFE08C55));

    // ════════════════════════════════════════════════════════════════
    //  Settings — render
    // ════════════════════════════════════════════════════════════════

    private final BooleanSetting tracers = addSetting(
        new BooleanSetting("Tracers", "Draw tracer lines from the camera to revealed + cached ore positions.",
            false));
    private final ColorSetting tracerColor = addSetting(
        new ColorSetting("Tracer Color", "Tracer line color. Click to cycle.",
            0xFFFFAA00));

    /** Dim factor applied to cached (prior-session) ore colors so you can
     *  tell "revealed just now" from "revealed before" at a glance. */
    private final IntSetting cacheAlpha = addSetting(
        new IntSetting("Cache Alpha", "Opacity (0-255) for cached (prior-session) ore boxes. Lower = dimmer.",
            0, 255, 96, 8));

    // ════════════════════════════════════════════════════════════════
    //  Constants — ore states. Higher ordinal = higher dedup precedence.
    //  Cached variants sit just below their confirmed variant so a live
    //  re-reveal upgrades cached -> confirmed.
    // ════════════════════════════════════════════════════════════════

    private static final int STATE_COAL_CACHED      = 1;
    private static final int STATE_COAL             = 2;
    private static final int STATE_COPPER_CACHED    = 3;
    private static final int STATE_COPPER           = 4;
    private static final int STATE_IRON_CACHED      = 5;
    private static final int STATE_IRON             = 6;
    private static final int STATE_REDSTONE_CACHED  = 7;
    private static final int STATE_REDSTONE         = 8;
    private static final int STATE_LAPIS_CACHED     = 9;
    private static final int STATE_LAPIS            = 10;
    private static final int STATE_GOLD_CACHED      = 11;
    private static final int STATE_GOLD             = 12;
    private static final int STATE_EMERALD_CACHED   = 13;
    private static final int STATE_EMERALD          = 14;
    private static final int STATE_DIAMOND_CACHED   = 15;
    private static final int STATE_DIAMOND          = 16;

    /** Maps a confirmed state -> its cached variant (one ordinal lower). */
    private static int cachedVariant(int confirmedState) { return confirmedState - 1; }

    // ════════════════════════════════════════════════════════════════
    //  State
    // ════════════════════════════════════════════════════════════════

    /** Cached (renderBox, state, pos) snapshot, replaced each scan. Keyed by
     *  packed {@link BlockPos#asLong} so the live scan and the cache-restore
     *  pass never double-render the same block; confirmed beats cached
     *  (if the live scan re-reveals a cached block it upgrades). */
    private final Long2ObjectOpenHashMap<OreEntry> renderMap = new Long2ObjectOpenHashMap<>();
    private record OreEntry(Box renderBox, int state, BlockPos pos) {}

    /** Throttled-save counter (~every 30s while enabled). */
    private int saveTick = 0;
    /** Tracks the world-null transition so we save+clear on disconnect. */
    private boolean hadWorld = false;

    // ── Amortized scan scheduling ────────────────────────────────
    // The old scan swept EVERY chunk in range in a single tick (a lag spike
    // each second at range 4 — ~8M block reads). Now a rotating cursor
    // re-verifies only `Chunks Per Tick` chunks per tick via a distance-
    // sorted offset ring, so the cost is constant and tiny.
    /** (dx, dz) chunk offsets sorted by distance from center — rebuilt when
     *  the Range setting changes. */
    private int[][] ringOffsets = null;
    /** The Range value {@link #ringOffsets} was built for. */
    private int ringRadiusBuilt = -1;
    /** Rotating index into {@link #ringOffsets}; wraps each full pass. */
    private int ringCursor = 0;

    /** The persistent cross-session ore cache (per-server + per-dim). */
    private final OreCache oreCache = new OreCache();

    public OverworldRay() {
        super("OverworldRay", "Exposed-scan overworld-ore tracker with a persistent cross-session cache. Finds revealed ores (diamond, emerald, gold, ...) without a seed — works on servers where seedcracking is blocked.",
            Category.MISC);
        // Lucid dispatched renderWorld() from its client entrypoint; self-register here.
        try {
            net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_MAIN.register(this::anormal$dispatchWorld);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onEnable() {
        clearRenderMap();
        saveTick = 0;
        hadWorld = false;
        ringCursor = 0;  // fresh pass — nearest chunks scan first
        oreCache.load();
    }

    @Override
    public void onDisable() {
        oreCache.save();
        clearRenderMap();
    }

    private void clearRenderMap() {
        renderMap.clear();
    }

    // ════════════════════════════════════════════════════════════════
    //  onTick — amortized rotating scan: `Chunks Per Tick` chunks per tick
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick() {
        // Detect disconnect: save + clear the persistent cache so the next
        // server starts fresh.
        boolean hasWorld = mc.world != null;
        if (hadWorld && !hasWorld) {
            oreCache.save();
            oreCache.clear();
            clearRenderMap();
            ringOffsets = null;
            ringRadiusBuilt = -1;
            ringCursor = 0;
        }
        hadWorld = hasWorld;

        if (mc.player == null || mc.world == null) return;

        // If the cache wasn't loaded at enable (enabled in menu), load it now
        // that we have a world + server entry to key on.
        if (persistentCache.isEnabled() && !oreCache.loaded) {
            oreCache.load();
        }

        ClientWorld world = mc.world;
        int pcx = mc.player.getBlockPos().getX() >> 4;
        int pcz = mc.player.getBlockPos().getZ() >> 4;

        // Build (or rebuild) the distance-sorted offset ring for the range.
        int chunkR = range.get();
        if (ringOffsets == null || ringRadiusBuilt != chunkR) {
            List<int[]> offs = new ArrayList<>();
            for (int dx = -chunkR; dx <= chunkR; dx++) {
                for (int dz = -chunkR; dz <= chunkR; dz++) {
                    offs.add(new int[]{dx, dz});
                }
            }
            offs.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1],
                                                b[0] * b[0] + b[1] * b[1]));
            ringOffsets = offs.toArray(new int[0][]);
            ringRadiusBuilt = chunkR;
            ringCursor = 0;
        }

        // Scan `Chunks Per Tick` chunks from the rotating cursor — constant,
        // tiny per-tick cost instead of the old single-tick full sweep.
        int perTick = chunksPerTick.get();
        for (int i = 0; i < perTick && ringOffsets.length > 0; i++) {
            int[] off = ringOffsets[ringCursor];
            ringCursor = (ringCursor + 1) % ringOffsets.length;
            processChunk(world, pcx + off[0], pcz + off[1]);
        }

        // Throttled save (~every 30s at 20 tps) so a crash doesn't lose finds.
        if (persistentCache.isEnabled() && ++saveTick >= 600) {
            saveTick = 0;
            oreCache.save();
        }
    }

    /**
     * Fully re-verify ONE chunk: drop its stale render entries, palette-skip
     * its sections and re-add every revealed ore (bright), then re-check the
     * persistent cache's entries for this chunk directly — mined ores leave
     * the cache, still-present ones render dim.
     *
     * <p>Called from the rotating cursor, a couple chunks per tick — the
     * per-chunk work is bounded and there is no full-grid spike.
     */
    private void processChunk(ClientWorld world, int cx, int cz) {
        int yMinV = yMin.get();
        int yMaxV = yMax.get();

        // Unloaded chunk: purge whatever this chunk previously contributed to
        // the render map (its data is gone — don't ghost-render it), and skip
        // (cache entries can't be verified without the chunk).
        if (!world.getChunkManager().isChunkLoaded(cx, cz)) {
            purgeChunkRenderEntries(cx, cz);
            return;
        }
        WorldChunk chunk = world.getChunk(cx, cz);
        if (chunk == null) {
            purgeChunkRenderEntries(cx, cz);
            return;
        }

        // Fresh pass: out with the chunk's old entries…
        purgeChunkRenderEntries(cx, cz);

        // …in with the new. The palette fast-sip skips whole sections that
        // can't contain a tracked ore (solid stone under anti-xray) without
        // touching a single block.
        if (exposedScan.isEnabled()) {
            scanExposedChunk(chunk, yMinV, yMaxV);
        }

        // Re-verify the persistent cache's entries in THIS chunk directly —
        // one targeted block read per cached position (not per-scanned-block
        // hashing). Mined / replaced ores leave the cache; still-present
        // ones restore dim unless the live scan already confirmed them.
        if (persistentCache.isEnabled() && oreCache.loaded && !oreCache.entries.isEmpty()) {
            for (var entry : oreCache.entries.long2IntEntrySet()) {
                long key = entry.getLongKey();
                int x = BlockPos.unpackLongX(key);
                int z = BlockPos.unpackLongZ(key);
                if ((x >> 4) != cx || (z >> 4) != cz) continue;
                int y = BlockPos.unpackLongY(key);
                if (y < yMinV || y > yMaxV) continue;

                int cachedState = entry.getIntValue();
                int actual = oreTypeOf(world.getBlockState(new BlockPos(x, y, z)).getBlock());
                if (actual == 0 || actual != cachedState) {
                    // Mined or replaced — drop it from the cache (and the
                    // purged render map already dropped its box).
                    oreCache.remove(key);
                    continue;
                }
                // Still there — restore the dim cached render (no-op if the
                // live scan already placed a bright confirmed entry).
                addEntryRaw(x, y, z, cachedVariant(cachedState));
            }
        }
    }

    /** Remove every render-map entry belonging to chunk (cx, cz). */
    private void purgeChunkRenderEntries(int cx, int cz) {
        var it = renderMap.keySet().iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            if ((BlockPos.unpackLongX(key) >> 4) == cx
                && (BlockPos.unpackLongZ(key) >> 4) == cz) {
                it.remove();
            }
        }
    }

    /**
     * Walk the chunk's Y-band sections and add any real ore the server was
     * forced to reveal (cave/ravine/player-mined neighbors). Free, no seed
     * needed, no anti-cheat signal. Every revealed ore is added to the
     * persistent cache so it survives across sessions.
     *
     * <p><b>Section fast-skip:</b> a section whose block-state palette
     * contains no tracked ore is skipped wholesale ({@code hasAny}) — under
     * PaperMC anti-xray, hidden ores read as stone in the palette, so a
     * no-ore palette genuinely has nothing to render. When you mine toward a
     * hidden ore the server updates the section, the ore enters the palette,
     * and the section gets walked on the next pass.
     */
    private void scanExposedChunk(WorldChunk chunk, int yMinV, int yMaxV) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomY = chunk.getBottomY();
        int baseX = chunk.getPos().x << 4;
        int baseZ = chunk.getPos().z << 4;
        boolean cacheOn = persistentCache.isEnabled();

        for (int s = 0; s < sections.length; s++) {
            ChunkSection section = sections[s];
            if (section == null || section.isEmpty()) continue;
            int sectionYBase = bottomY + s * 16;
            if (sectionYBase + 15 < yMinV || sectionYBase > yMaxV) continue;
            // Palette fast-skip — no tracked ore state in the palette means
            // there is nothing to add from this section.
            if (!section.hasAny(state -> oreTypeOf(state.getBlock()) != 0)) continue;
            for (int ly = 0; ly < 16; ly++) {
                int absY = sectionYBase + ly;
                if (absY < yMinV || absY > yMaxV) continue;
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        BlockState state = section.getBlockState(lx, ly, lz);
                        int actual = oreTypeOf(state.getBlock());
                        if (actual == 0) continue;          // not an ore
                        int x = baseX + lx, z = baseZ + lz;
                        addEntry(x, absY, z, actual);
                        if (cacheOn) oreCache.add(BlockPos.asLong(x, absY, z), actual);
                        // NOTE: no per-ore toggle check here anymore — the
                        // toggle only affects RENDERING (see renderWorld),
                        // so the cache stays complete and the mined-ore
                        // cleanup stays toggle-independent.
                    }
                }
            }
        }
    }

    /** The ore type of a block, ignoring the per-ore render toggles.
     *  0 = not a tracked ore. Used by both the render path and the
     *  mined-ore cleanup (which must classify blocks even when their
     *  toggle is off). */
    private static int oreTypeOf(Block b) {
        if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) return STATE_DIAMOND;
        if (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE) return STATE_EMERALD;
        if (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE) return STATE_GOLD;
        if (b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE) return STATE_LAPIS;
        if (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE) return STATE_REDSTONE;
        if (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE) return STATE_IRON;
        if (b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE) return STATE_COAL;
        if (b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE) return STATE_COPPER;
        return 0;
    }

    /** Is this ore type's render toggle on? */
    private boolean toggleFor(int state) {
        return switch (state) {
            case STATE_DIAMOND -> showDiamond.isEnabled();
            case STATE_EMERALD -> showEmerald.isEnabled();
            case STATE_GOLD -> showGold.isEnabled();
            case STATE_LAPIS -> showLapis.isEnabled();
            case STATE_REDSTONE -> showRedstone.isEnabled();
            case STATE_IRON -> showIron.isEnabled();
            case STATE_COAL -> showCoal.isEnabled();
            case STATE_COPPER -> showCopper.isEnabled();
            default -> false;
        };
    }

    /**
     * Add an ore position to the render map, dedup'ing by packed
     * {@link BlockPos#asLong}. Higher states take precedence over lower
     * (confirmed beats cached; diamond beats everything at a shared position
     * — though shared positions are rare since ores don't overlap). Never
     * downgrades an existing entry.
     */
    private void addEntry(int x, int y, int z, int state) {
        long key = BlockPos.asLong(x, y, z);
        OreEntry existing = renderMap.get(key);
        if (existing != null) {
            if (state > existing.state()) {
                renderMap.put(key, new OreEntry(existing.renderBox(), state, existing.pos()));
            }
            return;
        }
        Box box = new Box(x, y, z, x + 1, y + 1, z + 1);
        BlockPos pos = new BlockPos(x, y, z);
        renderMap.put(key, new OreEntry(box, state, pos));
    }

    /** Like {@link #addEntry} but skips the upgrade check (only used for
     *  cached restores, where the caller already checked the key is absent). */
    private void addEntryRaw(int x, int y, int z, int state) {
        long key = BlockPos.asLong(x, y, z);
        if (renderMap.containsKey(key)) return;
        Box box = new Box(x, y, z, x + 1, y + 1, z + 1);
        BlockPos pos = new BlockPos(x, y, z);
        renderMap.put(key, new OreEntry(box, state, pos));
    }

    // ════════════════════════════════════════════════════════════════
    //  renderWorld — draw wireframe boxes + optional tracers
    // ════════════════════════════════════════════════════════════════
    private void anormal$dispatchWorld(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext wrc) {
        if (!isEnabled()) return;
        renderWorld(wrc);
    }

    public void renderWorld(WorldRenderContext wrc) {
        if (!isEnabled() || renderMap.isEmpty()) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;

        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        // Pre-compute the per-ore colors (confirmed = full alpha, cached =
        // dimmed via cacheAlpha). A cached entry is the confirmed state minus 1.
        int cDiamond   = diamondColor.getColor();
        int cEmerald   = emeraldColor.getColor();
        int cGold      = goldColor.getColor();
        int cLapis     = lapisColor.getColor();
        int cRedstone  = redstoneColor.getColor();
        int cIron      = ironColor.getColor();
        int cCoal      = coalColor.getColor();
        int cCopper    = copperColor.getColor();
        int cTracer    = tracerColor.getColor();
        int dimAlpha   = cacheAlpha.get() & 0xFF;
        boolean drawTracers = tracers.isEnabled();

        // Dimmed (cached) variants — replace the alpha byte with cacheAlpha.
        int cDiamondC   = withAlpha(cDiamond, dimAlpha);
        int cEmeraldC   = withAlpha(cEmerald, dimAlpha);
        int cGoldC      = withAlpha(cGold, dimAlpha);
        int cLapisC     = withAlpha(cLapis, dimAlpha);
        int cRedstoneC  = withAlpha(cRedstone, dimAlpha);
        int cIronC      = withAlpha(cIron, dimAlpha);
        int cCoalC      = withAlpha(cCoal, dimAlpha);
        int cCopperC    = withAlpha(cCopper, dimAlpha);

        WorldRender3D.begin(matrices, camera);
        try {
            for (OreEntry e : renderMap.values()) {
                // The scan collects ALL tracked ore types; the per-ore
                // toggles gate RENDERING only (cached states are odd,
                // confirmed even — round the cached variant up to its
                // confirmed state for the toggle check).
                int baseState = (e.state() % 2 == 0) ? e.state() : e.state() + 1;
                if (!toggleFor(baseState)) continue;
                int color;
                switch (e.state()) {
                    case STATE_DIAMOND:   color = cDiamond;   break;
                    case STATE_DIAMOND_CACHED:   color = cDiamondC;   break;
                    case STATE_EMERALD:   color = cEmerald;   break;
                    case STATE_EMERALD_CACHED:   color = cEmeraldC;   break;
                    case STATE_GOLD:      color = cGold;      break;
                    case STATE_GOLD_CACHED:      color = cGoldC;      break;
                    case STATE_LAPIS:     color = cLapis;     break;
                    case STATE_LAPIS_CACHED:     color = cLapisC;     break;
                    case STATE_REDSTONE:  color = cRedstone;  break;
                    case STATE_REDSTONE_CACHED:  color = cRedstoneC;  break;
                    case STATE_IRON:      color = cIron;      break;
                    case STATE_IRON_CACHED:      color = cIronC;      break;
                    case STATE_COAL:      color = cCoal;      break;
                    case STATE_COAL_CACHED:      color = cCoalC;      break;
                    case STATE_COPPER:    color = cCopper;    break;
                    case STATE_COPPER_CACHED:    color = cCopperC;    break;
                    default:              color = cDiamond;   break;
                }
                WorldRender3D.drawBox(consumers, e.renderBox(), color);
                if (drawTracers) {
                    WorldRender3D.drawTracer(consumers, camera,
                        net.minecraft.util.math.Vec3d.ofCenter(e.pos()), cTracer);
                }
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }

    /** Replace the alpha byte (top 8 bits) of an ARGB color. */
    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    // ════════════════════════════════════════════════════════════════
    //  OreCache — persistent cross-session cache of revealed ores.
    //  Per-server + per-dimension JSON file under the Fabric config dir.
    //  Stores {packedPos -> confirmedState} so a re-revealed ore keeps its
    //  type without re-scanning.
    // ════════════════════════════════════════════════════════════════
    private static final Gson GSON = new Gson();

    /** A serializable {pos: state} entry — Gson needs a concrete type for the
     *  map values, so we serialize as a list of [pos, state] pairs. */
    private record CachedOre(long pos, int state) {}

    private final class OreCache {
        /** Revealed-ore packed positions -> confirmed state. */
        final Long2IntOpenHashMap entries = new Long2IntOpenHashMap();
        /** True once a file has been loaded (or a load was attempted). */
        boolean loaded = false;
        /** The file path for the current server+dim (set on load). */
        private Path file;
        /** Dirty flag — only write if something changed. */
        private boolean dirty = false;

        /** Compute the cache file for the current server + dimension. */
        private Path resolveFile() {
            
            if (mc == null) return null;
            String server;
            if (mc.getCurrentServerEntry() != null) {
                server = mc.getCurrentServerEntry().address;
            } else if (mc.isInSingleplayer() && mc.world != null) {
                server = "singleplayer";
            } else {
                return null;
            }
            String dim;
            if (mc.world != null) {
                dim = mc.world.getRegistryKey().getValue().toString();
            } else {
                return null;
            }
            String safe = (server + "_" + dim).replaceAll("[^A-Za-z0-9._-]", "_");
            Path dir = FabricLoader.getInstance().getConfigDir()
                .resolve("anormal").resolve("overworld_ray");
            return dir.resolve(safe + ".json");
        }

        void load() {
            if (loaded) return;
            if (!persistentCache.isEnabled()) { loaded = true; return; }
            try {
                file = resolveFile();
                if (file == null) return;
                entries.clear();
                if (Files.exists(file)) {
                    java.lang.reflect.Type type = new TypeToken<List<CachedOre>>() {}.getType();
                    try (Reader r = Files.newBufferedReader(file)) {
                        List<CachedOre> list = GSON.fromJson(r, type);
                        if (list != null) {
                            for (CachedOre o : list) entries.put(o.pos(), o.state());
                        }
                    }
                }
                loaded = true;
                dirty = false;
            } catch (IOException e) {
                loaded = true;
            }
        }

        /** Add or refresh a revealed-ore position. Returns true if new/changed.
         *  Note: {@link Long2IntOpenHashMap#put} returns 0 for missing keys
         *  (the fastutil primitive default), so we check {@link #containsKey}
         *  first to disambiguate "absent" from "present with state 0". */
        boolean add(long key, int confirmedState) {
            boolean wasPresent = entries.containsKey(key);
            int prev = entries.put(key, confirmedState);
            if (!wasPresent || prev != confirmedState) {
                dirty = true;
                return true;
            }
            return false;
        }

        /** Delete a cached position (the ore was mined / replaced). The
         *  next cache-restore pass stops rendering it and the next save
         *  drops it from disk. */
        void remove(long key) {
            if (entries.containsKey(key)) {
                entries.remove(key);
                dirty = true;
            }
        }

        void save() {
            if (!loaded || !persistentCache.isEnabled()) return;
            if (!dirty && file != null && Files.exists(file)) return;
            try {
                if (file == null) file = resolveFile();
                if (file == null) return;
                Files.createDirectories(file.getParent());
                List<CachedOre> list = new ArrayList<>(entries.size());
                for (var entry : entries.long2IntEntrySet()) {
                    list.add(new CachedOre(entry.getLongKey(), entry.getIntValue()));
                }
                try (Writer w = Files.newBufferedWriter(file)) {
                    GSON.toJson(list, w);
                }
                dirty = false;
            } catch (IOException ignored) {
                // Best-effort persistence.
            }
        }

        void clear() {
            entries.clear();
            loaded = false;
            file = null;
            dirty = false;
        }
    }
}