package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.ColorSetting;
import com.lucidclient.module.setting.IntSetting;
import com.lucidclient.suschunk.ChunkHeuristics;
import com.lucidclient.ui.util.WorldRender3D;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * HoleESP (base-finding) — detects and renders player-dug air pockets
 * (tunnels, rooms, shafts) in loaded chunks. The detector runs a
 * <b>per-connected-region flood-fill</b>: each separate pocket of connected
 * air is identified, measured, and — if it passes the volume + (optional)
 * unnatural-block corroboration filters — boxed at its own tight bounding
 * box. This gives per-hole resolution (not per-chunk) without the
 * screen-covering noise of unfiltered air scans.
 *
 * <h3>Why flood-fill per region</h3>
 * The previous "bounding box of all air in the chunk" approach produced one
 * giant box spanning every air pocket in the chunk — if a chunk had a small
 * player hole next to a natural cave, the box covered the cave too. A
 * per-region flood-fill treats each connected air pocket as its own
 * candidate, so a 1×20 tunnel gets a thin long box and a separate 7×7 room
 * in the same chunk gets its own box.
 *
 * <h3>Filtering — what makes air "player-made"</h3>
 * Natural caves, ravines, and lava-tubes are large, irregular, and lack
 * player-placed blocks. Player-dug structures are bounded and often have
 * chests / planks / torches / obsidian nearby. A region is flagged when:
 * <ul>
 *   <li>volume ≥ {@code Min Volume} AND ≤ {@code Max Volume} (excludes
 *       tiny noise AND huge natural caves), AND</li>
 *   <li>either {@code Require Unnatural} is OFF, OR the region's shell
 *       (the blocks touching its air) contains ≥ {@code Unnatural Threshold}
 *       unnatural blocks from {@link ChunkHeuristics#getUnnaturalBlocks()}.</li>
 * </ul>
 *
 * <h3>Anti-xray</h3>
 * Only reads air geometry (the server can't fake air without suffocating
 * players) + the shell block states (which anti-xray may obfuscate, so
 * unnatural corroboration is a soft signal, not a hard gate by default).
 *
 * <h3>Performance</h3>
 * Uses packed-{@code long} fastutil collections keyed by
 * {@link BlockPos#asLong}: {@link LongOpenHashSet} for the per-chunk visited
 * set (reset per chunk — bounded size) and {@link LongArrayFIFOQueue} for
 * the flood-fill queue. A single scratch {@link BlockPos.Mutable} handles
 * world-coord lookups. The candidate scan is stride-1; flood-fill is capped
 * at {@code Max Flood Fill} per region (regions hitting the cap are
 * treated as "huge cave" and skipped). Scan runs at {@code Scan Interval}
 * ticks (default 20 = 1Hz; bases don't move).
 *
 * <h3>Rendering</h3>
 * Each flagged region's bounding box is drawn via
 * {@link WorldRender3D#drawBox} on the no-depth-test line layer — the same
 * per-block render path SpawnerESP uses (proven to render in-game).
 * Corroborated regions render in {@code Confirmed Color}; uncorroborated
 * regions render dim in {@code Unconfirmed Color} (or are dropped entirely
 * if {@code Require Unnatural} is on).
 */
public class HoleESP extends Module {

    // ── Settings ──────────────────────────────────────────────
    private final IntSetting range = addSetting(
        new IntSetting("Range", "Chunk radius to scan around the player.",
            1, 8, 4, 1));
    private final IntSetting yWindow = addSetting(
        new IntSetting("Y Window", "Blocks above AND below the player to scan.",
            16, 128, 64, 16));
    private final IntSetting scanInterval = addSetting(
        new IntSetting("Scan Interval", "Ticks between scans. 20 = 1Hz (bases don't move).",
            5, 100, 20, 5));
    private final IntSetting minVolume = addSetting(
        new IntSetting("Min Volume", "Min air-block count for a region to flag. Excludes tiny noise.",
            3, 200, 8, 1));
    private final IntSetting maxVolume = addSetting(
        new IntSetting("Max Volume", "Max air-block count for a region to flag. Excludes huge caves.",
            50, 20000, 3000, 50));
    private final IntSetting maxFloodFill = addSetting(
        new IntSetting("Max Flood Fill", "Hard cap on flood-fill expansion per region (prevents runaway).",
            100, 20000, 3000, 100));
    private final IntSetting unnaturalThreshold = addSetting(
        new IntSetting("Unnatural Threshold", "Min unnatural blocks in a region's shell to corroborate it.",
            1, 50, 1, 1));
    private final BooleanSetting requireUnnatural = addSetting(
        new BooleanSetting("Require Unnatural", "Only flag regions whose shell has player-placed blocks (chest/planks/torch). Off = flag all, dim uncorroborated.",
            false));
    private final ColorSetting confirmedColor = addSetting(
        new ColorSetting("Confirmed Color", "Color for corroborated regions. Click to cycle.",
            0xFFEF4444));
    private final ColorSetting unconfirmedColor = addSetting(
        new ColorSetting("Unconfirmed Color", "Color for uncorroborated regions. Click to cycle.",
            0xFFF59E0B));

    /** Cached (renderBox, corroborated) snapshot, replaced each scan. */
    private final List<HoleEntry> cached = new ArrayList<>();
    private record HoleEntry(Box renderBox, boolean corroborated) {}

    /** Per-chunk visited set (packed longs). Reset each chunk. */
    private final LongOpenHashSet visited = new LongOpenHashSet();
    /** Reusable flood-fill queue (packed longs). */
    private final LongArrayFIFOQueue fillQueue = new LongArrayFIFOQueue();
    /** Scratch mutable pos for world-coord block-state lookups. */
    private final BlockPos.Mutable scratch = new BlockPos.Mutable();
    /** Per-region shell-candidate set (packed longs). Reset each region. */
    private final LongOpenHashSet shellSet = new LongOpenHashSet();

    private int scanTick = 0;

    public HoleESP() {
        super("HoleESP", "Finds player-dug tunnels/rooms/holes by air-pocket flood-fill + unnatural-block corroboration. Renders a tight box per pocket.",
            Category.RENDER);
    }

    @Override
    protected void onEnable() {
        cached.clear(); visited.clear(); fillQueue.clear(); shellSet.clear();
        scanTick = 0;
    }
    @Override
    protected void onDisable() {
        cached.clear(); visited.clear(); fillQueue.clear(); shellSet.clear();
    }

    // ════════════════════════════════════════════════════════════════
    //  onTick — scan loaded chunks for player-dug air regions
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        int interval = scanInterval.get();
        if (interval > 1 && ++scanTick % interval != 0) return;

        ClientWorld world = client.world;
        BlockPos center = client.player.getBlockPos();
        int chunkR = range.get();
        int pcx = center.getX() >> 4, pcz = center.getZ() >> 4;
        int yMin = Math.max(world.getBottomY(), center.getY() - yWindow.get());
        int yMax = Math.min(world.getTopYInclusive(), center.getY() + yWindow.get());
        if (yMin > yMax) { cached.clear(); return; }

        cached.clear();

        for (int cx = pcx - chunkR; cx <= pcx + chunkR; cx++) {
            for (int cz = pcz - chunkR; cz <= pcz + chunkR; cz++) {
                if (!world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                WorldChunk chunk;
                try {
                    chunk = world.getChunk(cx, cz);
                } catch (Throwable ignored) {
                    continue;
                }
                if (chunk == null) continue;
                visited.clear(); // per-chunk scope — bounded size
                scanChunk(chunk, world, yMin, yMax);
            }
        }
    }

    /**
     * Walk every block in the chunk's Y-window. For each air block not yet
     * visited, flood-fill the connected air region, measure it, and box it
     * if it passes the volume + corroboration filters. Stride-1 — a 1-wide
     * tunnel can sit at any coordinate.
     */
    private void scanChunk(WorldChunk chunk, ClientWorld world, int yMin, int yMax) {
        ChunkSection[] sections = chunk.getSectionArray();
        int bottomY = chunk.getBottomY();
        int baseX = chunk.getPos().x << 4;
        int baseZ = chunk.getPos().z << 4;

        for (int s = 0; s < sections.length; s++) {
            ChunkSection section = sections[s];
            if (section == null || section.isEmpty()) continue;
            int sectionYBase = bottomY + s * 16;
            if (sectionYBase + 15 < yMin || sectionYBase > yMax) continue;
            for (int lx = 0; lx < 16; lx++) {
                for (int ly = 0; ly < 16; ly++) {
                    for (int lz = 0; lz < 16; lz++) {
                        if (!section.getBlockState(lx, ly, lz).isAir()) continue;
                        int absY = sectionYBase + ly;
                        if (absY < yMin || absY > yMax) continue;
                        int wx = baseX + lx, wz = baseZ + lz;
                        long key = BlockPos.asLong(wx, absY, wz);
                        if (visited.contains(key)) continue;
                        // New region — flood-fill + evaluate.
                        evaluateRegion(wx, absY, wz, world);
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Per-region flood-fill + evaluation
    // ════════════════════════════════════════════════════════════════
    /**
     * Flood-fill the connected air region containing {@code (x,y,z)} using
     * packed-long collections. Track the bounding box and collect the shell
     * (non-air neighbours) as we go. Then apply volume + corroboration
     * filters and, if it passes, add the tight bounding box to the cache.
     */
    private void evaluateRegion(int x, int y, int z, ClientWorld world) {
        int cap = maxFloodFill.get();
        fillQueue.clear();
        shellSet.clear();
        long startKey = BlockPos.asLong(x, y, z);
        fillQueue.enqueue(startKey);
        visited.add(startKey);

        int volume = 0;
        int minX = x, minY = y, minZ = z;
        int maxX = x, maxY = y, maxZ = z;
        boolean hitCap = false;

        while (!fillQueue.isEmpty()) {
            if (volume >= cap) { hitCap = true; break; }
            long key = fillQueue.dequeueLong();
            int cx = BlockPos.unpackLongX(key);
            int cy = BlockPos.unpackLongY(key);
            int cz = BlockPos.unpackLongZ(key);
            volume++;
            if (cx < minX) minX = cx; if (cx > maxX) maxX = cx;
            if (cy < minY) minY = cy; if (cy > maxY) maxY = cy;
            if (cz < minZ) minZ = cz; if (cz > maxZ) maxZ = cz;

            // 6-connected expansion. Shell blocks (non-air neighbours) are
            // recorded for corroboration.
            expandNeighbour(world, cx + 1, cy, cz);
            expandNeighbour(world, cx - 1, cy, cz);
            expandNeighbour(world, cx, cy + 1, cz);
            expandNeighbour(world, cx, cy - 1, cz);
            expandNeighbour(world, cx, cy, cz + 1);
            expandNeighbour(world, cx, cy, cz - 1);
        }
        // On cap-hit, the unprocessed queue cells are already marked visited
        // (visited is set at enqueue time in expandNeighbour), so the outer
        // scan won't re-enter this region. No drain needed.

        // Huge cave → skip flagging (blocks stay visited).
        if (hitCap) return;
        // Volume filter: too small = noise, too large = natural cave.
        if (volume < minVolume.get()) return;
        if (volume > maxVolume.get()) return;

        Box box = new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        int unnatCount = countUnnaturalInShell(world);
        boolean corroborated = unnatCount >= unnaturalThreshold.get();
        if (requireUnnatural.isEnabled() && !corroborated) return;
        cached.add(new HoleEntry(box, corroborated));
    }

    /**
     * Check the neighbour at {@code (x,y,z)}: if air, enqueue it for the
     * flood-fill; if non-air, record it in the shell set for corroboration.
     */
    private void expandNeighbour(ClientWorld world, int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        if (visited.contains(key)) return;
        BlockState st = world.getBlockState(scratch.set(x, y, z));
        if (st.isAir()) {
            visited.add(key);
            fillQueue.enqueue(key);
        } else {
            // Solid neighbour — shell candidate for corroboration.
            shellSet.add(key);
        }
    }

    /**
     * Count how many shell blocks (recorded during the flood-fill) are in
     * {@link ChunkHeuristics#getUnnaturalBlocks()} — chests, planks, torches,
     * obsidian, workstations, etc. This is the player-base corroboration
     * signal: a bare natural cave has stone walls (zero), a player-dug room
     * often has chests/torches/planks in its walls.
     */
    private int countUnnaturalInShell(ClientWorld world) {
        Set<Block> unnatural = ChunkHeuristics.getUnnaturalBlocks();
        int count = 0;
        for (long key : shellSet) {
            int x = BlockPos.unpackLongX(key);
            int y = BlockPos.unpackLongY(key);
            int z = BlockPos.unpackLongZ(key);
            BlockState st = world.getBlockState(scratch.set(x, y, z));
            if (unnatural.contains(st.getBlock())) {
                count++;
                if (count >= unnaturalThreshold.get()) return count; // early-out
            }
        }
        return count;
    }

    // ════════════════════════════════════════════════════════════════
    //  renderWorld — draw a tight box per flagged region
    // ════════════════════════════════════════════════════════════════
    public void renderWorld(WorldRenderContext wrc) {
        if (!isEnabled() || cached.isEmpty()) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;

        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        int cConfirmed = confirmedColor.getColor();
        int cUnconfirmed = unconfirmedColor.getColor();
        boolean dimUncorroborated = !requireUnnatural.isEnabled();

        WorldRender3D.begin(matrices, camera);
        try {
            for (HoleEntry e : cached) {
                int color = e.corroborated() ? cConfirmed
                    : (dimUncorroborated ? cUnconfirmed : cConfirmed);
                WorldRender3D.drawBox(consumers, e.renderBox(), color);
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }
}