package com.lucidclient.module.impl;

import com.lucidclient.LucidClient;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.ColorSetting;
import com.lucidclient.module.setting.IntSetting;
import com.lucidclient.ui.util.WorldRender3D;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * LightFinder (glazed LightESP-style) — renders every light SOURCE block in
 * loaded chunks: torches, lava, glowstone, lanterns, campfires, and more,
 * each color-coded by category.
 *
 * <h3>What it's for</h3>
 * On DonutSMP, light sources underground are the loudest tell of player
 * activity — a torch-lit tunnel or a lava-lit base room shows through
 * everything. This module paints those blocks through the world so bases
 * and tunnels pop out while you fly or travel.
 *
 * <h3>How it works</h3>
 * Detection is chunk-arrival driven (the client's chunk mixin queues each
 * incoming chunk) PLUS a nearest-first backlog on enable, so the light
 * sources around you appear almost instantly and distant ones stream in
 * behind them. Scans are cheap: each chunk is walked section-by-section
 * and EMPTY sections are skipped entirely ({@link ChunkSection#isEmpty}),
 * which cuts the typical cave-and-sky chunk from ~98k block reads to the
 * few hundred non-empty sections' worth.
 *
 * <h3>Per-tick time budget</h3>
 * Instead of one chunk per tick, each tick keeps scanning queued chunks
 * until ~3 ms of work is done — on enable the whole render distance
 * lights up within a second or two instead of crawling at 1 chunk/tick,
 * without ever spiking a frame.
 *
 * <h3>Stale-entry refresh</h3>
 * Every 3 seconds the 3×3 chunks around the player are RE-scanned and
 * their cached entries replaced, so torches you (or others) break stop
 * rendering as boxes and newly placed lights appear — no ghost lights
 * lingering until you walk away.
 *
 * <h3>Thermal colors</h3>
 * With {@code Thermal Colors} on (default), every category renders in its
 * own fixed color — green torches, yellow glowstone, orange lava, purple
 * crying obsidian, teal sea pickles. Off = one configurable color.
 *
 * Settings:
 *   Torches, Glowstone, Lava, Lanterns, Sea Pickles, Shroomlights, Fire,
 *   Campfires, Jack o'Lanterns, Magma, Crying Obsidian, Glow Lichen,
 *   Other Light Sources, Thermal Colors, Color, Filled, Render Distance
 */
public class LightFinder extends Module {

    // ── Category settings ─────────────────────────────────────
    private final BooleanSetting torches = addSetting(
        new BooleanSetting("Torches", "Render torches (incl. soul & redstone torches).", true));
    private final BooleanSetting glowstone = addSetting(
        new BooleanSetting("Glowstone", "Render glowstone blocks.", true));
    private final BooleanSetting lava = addSetting(
        new BooleanSetting("Lava", "Render lava.", true));
    private final BooleanSetting lanterns = addSetting(
        new BooleanSetting("Lanterns", "Render lanterns and soul lanterns.", true));
    private final BooleanSetting seaPickles = addSetting(
        new BooleanSetting("Sea Pickles", "Render sea pickles.", true));
    private final BooleanSetting shroomlights = addSetting(
        new BooleanSetting("Shroomlights", "Render shroomlights.", true));
    private final BooleanSetting fires = addSetting(
        new BooleanSetting("Fire", "Render fire and soul fire.", true));
    private final BooleanSetting campfires = addSetting(
        new BooleanSetting("Campfires", "Render campfires and soul campfires.", true));
    private final BooleanSetting jackOLanterns = addSetting(
        new BooleanSetting("Jack o'Lanterns", "Render jack o'lanterns.", true));
    private final BooleanSetting magma = addSetting(
        new BooleanSetting("Magma", "Render magma blocks.", true));
    private final BooleanSetting cryingObsidian = addSetting(
        new BooleanSetting("Crying Obsidian", "Render crying obsidian.", true));
    private final BooleanSetting glowLichen = addSetting(
        new BooleanSetting("Glow Lichen", "Render glow lichen.", true));
    private final BooleanSetting other = addSetting(
        new BooleanSetting("Other Light Sources", "Render any other block that emits light (brewing stands, end rods, ...).", false));

    // ── Render settings ───────────────────────────────────────
    private final BooleanSetting thermalColors = addSetting(
        new BooleanSetting("Thermal Colors", "Color-code each category (green torches, orange lava, ...) instead of one solid color.", true));
    private final ColorSetting color = addSetting(
        new ColorSetting("Color", "Single color used when Thermal Colors is off. Click to cycle.", 0xFFFFFF00));
    private final BooleanSetting filled = addSetting(
        new BooleanSetting("Filled", "Draw a translucent fill in addition to the box outline.", true));
    private final IntSetting renderDistance = addSetting(
        new IntSetting("Render Distance", "Maximum distance (blocks) at which cached light sources render. Entries beyond it are pruned.", 16, 256, 128, 8));

    // ── Thermal palette (ARGB) ────────────────────────────────
    private static final int COL_TORCH       = 0xFF00CC00;
    private static final int COL_GLOWSTONE   = 0xFFFFFF00;
    private static final int COL_LAVA        = 0xFFFF7300;
    private static final int COL_LANTERN     = 0xFFFFD700;
    private static final int COL_SEA_PICKLE  = 0xFF00B7A0;
    private static final int COL_SHROOMLIGHT = 0xFFFF4500;
    private static final int COL_FIRE        = 0xFFFF6600;
    private static final int COL_CAMPFIRE    = 0xFFFF8C42;
    private static final int COL_JACK        = 0xFFFF7518;
    private static final int COL_MAGMA       = 0xFFCC3300;
    private static final int COL_CRYING      = 0xFF9900FF;
    private static final int COL_LICHEN      = 0xFF7FFFD4;
    private static final int COL_OTHER       = 0xFFFFFFFF;

    /** Scan budget per tick: keeps enable-backlog near-instant without frame spikes. */
    private static final long SCAN_BUDGET_NS = 3_000_000L; // 3 ms

    private static final int PRUNE_INTERVAL_TICKS = 60;    // 3 s
    private static final int REFRESH_INTERVAL_TICKS = 60;  // 3 s

    /** Cached light-source blocks: pos → thermal color. */
    private final Map<BlockPos, Integer> cache = new LinkedHashMap<>();
    /** Reverse index: chunk key → cached positions (for stale replacement). */
    private final Map<Long, Set<BlockPos>> byChunk = new HashMap<>();

    /** Chunks waiting to be scanned (fed by the chunk mixin + enable backlog). */
    private final Queue<Long> pendingChunks = new ArrayDeque<>();
    private final Set<Long> scannedChunks = new HashSet<>();

    private int pruneTicks = 0;
    private int refreshTicks = 0;

    public LightFinder() {
        super("LightFinder", "Renders light-source blocks (torches, lava, glowstone, ...) in loaded chunks, color-coded by type.",
            Category.RENDER);
    }

    @Override
    protected void onEnable() {
        clearAll();
        // Backlog already-loaded chunks, NEAREST FIRST, so the light sources
        // around the player appear almost instantly on toggle.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.world != null) {
            int pcx = client.player.getBlockPos().getX() >> 4;
            int pcz = client.player.getBlockPos().getZ() >> 4;
            int r = (int) Math.ceil(renderDistance.get() / 16.0);
            List<int[]> ring = new ArrayList<>();
            for (int cx = pcx - r; cx <= pcx + r; cx++) {
                for (int cz = pcz - r; cz <= pcz + r; cz++) {
                    if (client.world.isChunkLoaded(cx, cz)) {
                        ring.add(new int[]{ cx, cz });
                    }
                }
            }
            ring.sort(Comparator.comparingInt(a ->
                (a[0] - pcx) * (a[0] - pcx) + (a[1] - pcz) * (a[1] - pcz)));
            for (int[] c : ring) enqueueChunk(c[0], c[1]);
        }
    }

    @Override
    protected void onDisable() {
        clearAll();
    }

    private void clearAll() {
        cache.clear();
        byChunk.clear();
        pendingChunks.clear();
        scannedChunks.clear();
        pruneTicks = 0;
        refreshTicks = 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  CHUNK HOOK — called from ClientPlayNetworkHandlerMixin when a
    //  chunk's block data arrives. Runs on the netty thread: only queue.
    // ════════════════════════════════════════════════════════════════
    public static void onChunkLoaded(int cx, int cz) {
        LucidClient instance = LucidClient.getInstance();
        if (instance == null || instance.getModuleManager() == null) return;
        LightFinder lf = instance.getModuleManager().getModule(LightFinder.class);
        if (lf != null && lf.isEnabled()) {
            lf.enqueueChunk(cx, cz);
        }
    }

    private void enqueueChunk(int cx, int cz) {
        long key = ChunkPos.toLong(cx, cz);
        if (scannedChunks.add(key)) {
            pendingChunks.add(key);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  TICK — budgeted scanning + stale refresh + prune
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            clearAll();
            return;
        }

        // Budgeted backlog: keep scanning until ~3 ms is spent this tick.
        // First tick after enable typically clears a dozen+ chunks, so the
        // nearby world lights up within a frame or two.
        if (!pendingChunks.isEmpty()) {
            long deadline = System.nanoTime() + SCAN_BUDGET_NS;
            int scanned = 0;
            do {
                Long key = pendingChunks.poll();
                if (key == null) break;
                int cx = (int) (key & 0xFFFFFFFFL);
                int cz = (int) (key >>> 32);
                if (client.world.isChunkLoaded(cx, cz)) {
                    scanChunk(client.world, cx, cz, true);
                }
                scanned++;
            } while (System.nanoTime() < deadline && scanned < 64);
        }

        // Stale refresh: re-scan the 3×3 chunks around the player and
        // REPLACE their entries — broken torches stop rendering, newly
        // placed lights appear.
        if (++refreshTicks >= REFRESH_INTERVAL_TICKS) {
            refreshTicks = 0;
            int pcx = client.player.getBlockPos().getX() >> 4;
            int pcz = client.player.getBlockPos().getZ() >> 4;
            for (int cx = pcx - 1; cx <= pcx + 1; cx++) {
                for (int cz = pcz - 1; cz <= pcz + 1; cz++) {
                    if (client.world.isChunkLoaded(cx, cz)) {
                        scanChunk(client.world, cx, cz, true);
                    }
                }
            }
        }

        // Prune cached entries beyond the render distance.
        if (++pruneTicks >= PRUNE_INTERVAL_TICKS) {
            pruneTicks = 0;
            prune(client.player);
        }
    }

    /**
     * Scan a chunk for light-source blocks. With {@code replace}, any
     * previously cached entries for this chunk are dropped first (stale
     * removal). Skips empty sections — the bulk of any chunk's volume.
     */
    private void scanChunk(ClientWorld world, int cx, int cz, boolean replace) {
        WorldChunk chunk = world.getChunk(cx, cz);
        long key = ChunkPos.toLong(cx, cz);

        if (replace) {
            Set<BlockPos> old = byChunk.remove(key);
            if (old != null) {
                for (BlockPos p : old) cache.remove(p);
            }
        }
        Set<BlockPos> index = byChunk.computeIfAbsent(key, k -> new HashSet<>());

        ChunkSection[] sections = chunk.getSectionArray();
        int baseY = world.getBottomY();
        int baseX = cx << 4;
        int baseZ = cz << 4;

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue; // skip air
            int sectionBaseY = baseY + (i << 4);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        BlockState state = section.getBlockState(x, y, z);
                        int col = categorize(state);
                        if (col != 0) {
                            BlockPos pos = new BlockPos(baseX + x, sectionBaseY + y, baseZ + z);
                            cache.put(pos, col);
                            index.add(pos);
                        }
                    }
                }
            }
        }
        if (index.isEmpty()) byChunk.remove(key);
    }

    /** @return the thermal color for a light-emitting state, or 0 to skip. */
    private int categorize(BlockState state) {
        if (state.isAir()) return 0;
        var block = state.getBlock();
        if (block == Blocks.TORCH || block == Blocks.WALL_TORCH
                || block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH
                || block == Blocks.REDSTONE_TORCH || block == Blocks.REDSTONE_WALL_TORCH) {
            return torches.isEnabled() ? COL_TORCH : 0;
        }
        if (block == Blocks.GLOWSTONE) return glowstone.isEnabled() ? COL_GLOWSTONE : 0;
        if (block == Blocks.LAVA) return lava.isEnabled() ? COL_LAVA : 0;
        if (block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN) {
            return lanterns.isEnabled() ? COL_LANTERN : 0;
        }
        if (block == Blocks.SEA_PICKLE) return seaPickles.isEnabled() ? COL_SEA_PICKLE : 0;
        if (block == Blocks.SHROOMLIGHT) return shroomlights.isEnabled() ? COL_SHROOMLIGHT : 0;
        if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) {
            return fires.isEnabled() ? COL_FIRE : 0;
        }
        if (block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE) {
            return campfires.isEnabled() ? COL_CAMPFIRE : 0;
        }
        if (block == Blocks.JACK_O_LANTERN) return jackOLanterns.isEnabled() ? COL_JACK : 0;
        if (block == Blocks.MAGMA_BLOCK) return magma.isEnabled() ? COL_MAGMA : 0;
        if (block == Blocks.CRYING_OBSIDIAN) return cryingObsidian.isEnabled() ? COL_CRYING : 0;
        if (block == Blocks.GLOW_LICHEN) return glowLichen.isEnabled() ? COL_LICHEN : 0;
        // Other: any remaining state that actually emits light.
        if (other.isEnabled() && state.getLuminance() > 0) return COL_OTHER;
        return 0;
    }

    /** Drop cached entries beyond the render distance. */
    private void prune(ClientPlayerEntity player) {
        double max = renderDistance.get();
        double max2 = max * max;
        Iterator<Map.Entry<BlockPos, Integer>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            BlockPos p = it.next().getKey();
            double dx = p.getX() + 0.5 - player.getX();
            double dy = p.getY() + 0.5 - player.getY();
            double dz = p.getZ() + 0.5 - player.getZ();
            if (dx * dx + dy * dy + dz * dz > max2) {
                it.remove();
                Set<BlockPos> index = byChunk.get(ChunkPos.toLong(p.getX() >> 4, p.getZ() >> 4));
                if (index != null) index.remove(p);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  RENDER — translucent thermal boxes through the world
    // ════════════════════════════════════════════════════════════════
    public void renderWorld(WorldRenderContext wrc) {
        if (!isEnabled() || cache.isEmpty()) return;
        Camera camera = wrc.gameRenderer().getCamera();
        if (camera == null || !camera.isReady()) return;

        MatrixStack matrices = wrc.matrices();
        VertexConsumerProvider consumers = wrc.consumers();
        if (matrices == null || consumers == null) return;

        Vec3d camPos = camera.getCameraPos();
        double max = renderDistance.get();
        double max2 = max * max;
        int singleColor = color.getColor();
        boolean thermal = thermalColors.isEnabled();
        boolean fill = filled.isEnabled();

        WorldRender3D.begin(matrices, camera);
        try {
            for (Map.Entry<BlockPos, Integer> e : cache.entrySet()) {
                BlockPos bp = e.getKey();
                double dx = bp.getX() + 0.5 - camPos.x;
                double dy = bp.getY() + 0.5 - camPos.y;
                double dz = bp.getZ() + 0.5 - camPos.z;
                if (dx * dx + dy * dy + dz * dz > max2) continue;

                int col = thermal ? e.getValue() : singleColor;
                Box box = new Box(bp.getX(), bp.getY(), bp.getZ(),
                                  bp.getX() + 1, bp.getY() + 1, bp.getZ() + 1);
                WorldRender3D.drawBox(consumers, box, col);
                if (fill) {
                    WorldRender3D.drawFilledBox(consumers, box, col);
                }
            }
        } finally {
            WorldRender3D.end(matrices);
        }
    }
}
