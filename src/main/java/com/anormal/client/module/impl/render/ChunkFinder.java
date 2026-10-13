package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.HeaderSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Chunk Finder (Trail / Activity Finder).
 *
 * <p>Scans chunks within simulation distance for player activity indicators such as
 * amethyst cluster growth, mature kelp, cave vines, bamboo, cocoa pods, and bee nests.
 * These blocks reflect player presence and chunk ticking.</p>
 */
public class ChunkFinder extends Module {

    // ---- DETECTION Section ----
    public final HeaderSetting detectionHeader = new HeaderSetting("DETECTION");
    public final NumberSetting simulationDistance = new NumberSetting("Simulation distance", "Scan radius in chunks", 16.0, 2.0, 32.0, 1.0);
    public final NumberSetting sensitivity = new NumberSetting("Sensitivity", "Minimum indicator count to flag a chunk", 5.0, 1.0, 20.0, 1.0);
    public final BooleanSetting smartAdjustment = new BooleanSetting("Smart adjustment", "Smartly adjust threshold based on indicator types and depth", true);
    public final NumberSetting alpha = new NumberSetting("Alpha", "Highlight box fill opacity", 255.0, 0.0, 255.0, 5.0);
    public final ColorSetting highlightColor = new ColorSetting("Highlight color", "Chunk highlight color", ColorUtils.rgba(196, 71, 255, 255)); // #C447FF

    // ---- INDICATORS Section ----
    public final HeaderSetting indicatorsHeader = new HeaderSetting("INDICATORS");
    public final BooleanSetting kelp = new BooleanSetting("Kelp", "Detect grown or harvested kelp", false);
    public final BooleanSetting caveVines = new BooleanSetting("Cave vines", "Detect glowing cave vines", false);
    public final BooleanSetting vines = new BooleanSetting("Vines", "Detect vine growth trails", false);
    public final BooleanSetting amethyst = new BooleanSetting("Amethyst", "Detect amethyst clusters and budding amethyst growth", true);
    public final BooleanSetting bamboo = new BooleanSetting("Bamboo", "Detect mature bamboo stalks", false);
    public final BooleanSetting cocoa = new BooleanSetting("Cocoa", "Detect mature cocoa beans", false);
    public final BooleanSetting beeNest = new BooleanSetting("Bee nest", "Detect bee nests with active honey level", false);

    // ---- Visual Options ----
    public final BooleanSetting chunkBox = new BooleanSetting("Chunk Box", "Draw 3D chunk boundary highlight", true);
    public final BooleanSetting fillFloor = new BooleanSetting("Fill Floor", "Draw translucent ground-level fill", true);
    public final BooleanSetting tracers = new BooleanSetting("Tracers", "Draw tracer lines to active chunks", false);
    public final BooleanSetting showLabel = new BooleanSetting("Labels", "Show chunk coordinate and count tags", true);

    // Tracked chunk state
    public static class DetectedChunk {
        public final int chunkX;
        public final int chunkZ;
        public final int count;
        public final String primaryType;
        public final int avgY;
        public long lastSeen;

        public DetectedChunk(int chunkX, int chunkZ, int count, String primaryType, int avgY) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.count = count;
            this.primaryType = primaryType;
            this.avgY = avgY;
            this.lastSeen = System.currentTimeMillis();
        }
    }

    private final Map<ChunkPos, DetectedChunk> detected = new HashMap<>();
    private final List<ChunkPos> scanQueue = new ArrayList<>();
    private int queueIndex = 0;
    private int lastCenterChunkX = Integer.MIN_VALUE, lastCenterChunkZ = Integer.MIN_VALUE;
    private int lastRadius = -1;

    public ChunkFinder() {
        super("ChunkFinder", "Scans area for player activity indicators (amethyst, crops, vines)", Category.RENDER);

        addSetting(detectionHeader);
        addSetting(simulationDistance);
        addSetting(sensitivity);
        addSetting(smartAdjustment);
        addSetting(alpha);
        addSetting(highlightColor);

        addSetting(indicatorsHeader);
        addSetting(kelp);
        addSetting(caveVines);
        addSetting(vines);
        addSetting(amethyst);
        addSetting(bamboo);
        addSetting(cocoa);
        addSetting(beeNest);

        addSetting(chunkBox);
        addSetting(fillFloor);
        addSetting(tracers);
        addSetting(showLabel);
    }

    @Override
    public void onDisable() {
        detected.clear();
        scanQueue.clear();
        queueIndex = 0;
        lastCenterChunkX = Integer.MIN_VALUE;
    }

    private void rebuildQueue(int cx, int cz, int dist) {
        if (cx == lastCenterChunkX && cz == lastCenterChunkZ && dist == lastRadius) return;
        lastCenterChunkX = cx;
        lastCenterChunkZ = cz;
        lastRadius = dist;
        scanQueue.clear();

        for (int dx = -dist; dx <= dist; dx++) {
            for (int dz = -dist; dz <= dist; dz++) {
                if (dx * dx + dz * dz <= dist * dist) {
                    scanQueue.add(new ChunkPos(cx + dx, cz + dz));
                }
            }
        }
        // Center-first scan order
        scanQueue.sort(Comparator.comparingInt(cp -> {
            int ddx = cp.x - cx;
            int ddz = cp.z - cz;
            return ddx * ddx + ddz * ddz;
        }));
        queueIndex = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;
        int simDist = simulationDistance.getValue().intValue();

        rebuildQueue(playerChunkX, playerChunkZ, simDist);
        if (scanQueue.isEmpty()) return;

        // Prune chunks outside simulation distance
        int maxDistSq = (simDist + 2) * (simDist + 2);
        detected.entrySet().removeIf(e -> {
            int dx = e.getKey().x - playerChunkX;
            int dz = e.getKey().z - playerChunkZ;
            return (dx * dx + dz * dz) > maxDistSq;
        });

        // Budgeted scan: inspect up to 3 chunks per tick
        int chunksPerTick = 3;
        int count = 0;
        int targetSensitivity = sensitivity.getValue().intValue();

        while (count < chunksPerTick && !scanQueue.isEmpty()) {
            if (queueIndex >= scanQueue.size()) queueIndex = 0;
            ChunkPos cp = scanQueue.get(queueIndex++);
            count++;

            if (!mc.world.isChunkLoaded(cp.x, cp.z)) continue;
            WorldChunk chunk = mc.world.getChunk(cp.x, cp.z);
            if (chunk == null) continue;

            scanChunk(chunk, cp, targetSensitivity);
        }
    }

    private void scanChunk(WorldChunk chunk, ChunkPos cp, int requiredSensitivity) {
        int totalIndicators = 0;
        int ySum = 0;
        Map<String, Integer> typeCounts = new HashMap<>();

        int minY = mc.world.getBottomY();
        int maxY = mc.world.getTopYInclusive();

        BlockPos.Mutable p = new BlockPos.Mutable();

        // Sample blocks in the chunk: test blocks where indicators grow
        for (int y = minY; y <= maxY; y += 2) {
            for (int x = 0; x < 16; x += 3) {
                for (int z = 0; z < 16; z += 3) {
                    p.set((cp.x << 4) + x, y, (cp.z << 4) + z);
                    BlockState bs = chunk.getBlockState(p);
                    if (bs.isAir()) continue;

                    String path = Registries.BLOCK.getId(bs.getBlock()).getPath();
                    String indicator = checkIndicator(path);
                    if (indicator != null) {
                        totalIndicators++;
                        ySum += y;
                        typeCounts.put(indicator, typeCounts.getOrDefault(indicator, 0) + 1);
                    }
                }
            }
        }

        // Smart adjustment: Amethyst geode clusters or bee nests have higher weight
        int effectiveCount = totalIndicators;
        if (smartAdjustment.isEnabled()) {
            int amethystCount = typeCounts.getOrDefault("Amethyst", 0);
            int beeCount = typeCounts.getOrDefault("Bee nest", 0);
            effectiveCount += (amethystCount * 2) + (beeCount * 3);
        }

        if (effectiveCount >= requiredSensitivity && totalIndicators > 0) {
            String primary = "Activity";
            int maxC = 0;
            for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
                if (entry.getValue() > maxC) {
                    maxC = entry.getValue();
                    primary = entry.getKey();
                }
            }
            int avgY = ySum / totalIndicators;
            detected.put(cp, new DetectedChunk(cp.x, cp.z, totalIndicators, primary, avgY));
        } else {
            detected.remove(cp);
        }
    }

    private String checkIndicator(String path) {
        if (amethyst.isEnabled() && (path.contains("amethyst_cluster") || path.contains("amethyst_bud") || path.contains("budding_amethyst"))) {
            return "Amethyst";
        }
        if (kelp.isEnabled() && path.contains("kelp")) {
            return "Kelp";
        }
        if (caveVines.isEnabled() && path.contains("cave_vines")) {
            return "Cave vines";
        }
        if (vines.isEnabled() && path.equals("vine")) {
            return "Vines";
        }
        if (bamboo.isEnabled() && path.contains("bamboo")) {
            return "Bamboo";
        }
        if (cocoa.isEnabled() && path.contains("cocoa")) {
            return "Cocoa";
        }
        if (beeNest.isEnabled() && (path.contains("bee_nest") || path.contains("beehive"))) {
            return "Bee nest";
        }
        return null;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || detected.isEmpty()) return;

        int col = highlightColor.getValue();
        int a = Math.max(10, Math.min(255, alpha.getValue().intValue()));
        int fillColor = (a << 24) | (col & 0x00FFFFFF);

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        for (DetectedChunk dc : detected.values()) {
            int x1 = dc.chunkX << 4;
            int z1 = dc.chunkZ << 4;
            int x2 = x1 + 16;
            int z2 = z1 + 16;
            int y = dc.avgY;

            // 1. Draw 3D Chunk Floor / Bounds
            if (chunkBox.isEnabled()) {
                drawChunkBoundary(context, x1, y, z1, x2, y + 2, z2, col, tickDelta);
            }

            if (fillFloor.isEnabled()) {
                drawChunkFloor(context, x1, y, z1, x2, z2, fillColor, tickDelta);
            }

            // 2. Projected center point
            Vec3d center = new Vec3d(x1 + 8.0, y + 1.0, z1 + 8.0);
            int[] sc = ProjectionUtil.project(center, tickDelta);
            if (sc == null) continue;

            // 3. Tracers
            if (tracers.isEnabled()) {
                RenderUtils.drawLine(context, sw / 2, sh / 2, sc[0], sc[1], col, 1.0f);
            }

            // 4. Clean, orderly billboard tag
            if (showLabel.isEnabled() && mc.textRenderer != null) {
                int dist = (int) Math.sqrt(mc.player.squaredDistanceTo(center));
                String title = String.format("§dChunk [%d, %d]", dc.chunkX, dc.chunkZ);
                String sub = String.format("§f%s: %d §7(%dm)", dc.primaryType, dc.count, dist);

                int w1 = mc.textRenderer.getWidth(title);
                int w2 = mc.textRenderer.getWidth(sub);
                int boxW = Math.max(w1, w2) + 8;
                int boxH = 22;

                int bx = sc[0] - boxW / 2;
                int by = sc[1] - 12;

                // Neat badge frame
                RenderUtils.fill(context, bx, by, bx + boxW, by + boxH, ColorUtils.rgba(14, 16, 22, 210));
                RenderUtils.drawBorder(context, bx, by, bx + boxW, by + boxH, 1, col);

                RenderUtils.drawText(context, mc.textRenderer, title, bx + (boxW - w1) / 2, by + 3, 0xFFFFFFFF, true);
                RenderUtils.drawText(context, mc.textRenderer, sub, bx + (boxW - w2) / 2, by + 12, 0xFFDDDDDD, true);
            }
        }
    }

    private void drawChunkBoundary(DrawContext context, int x1, int y1, int z1, int x2, int y2, int z2, int col, float tickDelta) {
        int[] p1 = ProjectionUtil.project(new Vec3d(x1, y1, z1), tickDelta);
        int[] p2 = ProjectionUtil.project(new Vec3d(x2, y1, z1), tickDelta);
        int[] p3 = ProjectionUtil.project(new Vec3d(x2, y1, z2), tickDelta);
        int[] p4 = ProjectionUtil.project(new Vec3d(x1, y1, z2), tickDelta);

        int[] p5 = ProjectionUtil.project(new Vec3d(x1, y2, z1), tickDelta);
        int[] p6 = ProjectionUtil.project(new Vec3d(x2, y2, z1), tickDelta);
        int[] p7 = ProjectionUtil.project(new Vec3d(x2, y2, z2), tickDelta);
        int[] p8 = ProjectionUtil.project(new Vec3d(x1, y2, z2), tickDelta);

        // Base rectangle
        if (p1 != null && p2 != null) RenderUtils.drawLine(context, p1[0], p1[1], p2[0], p2[1], col, 1.5f);
        if (p2 != null && p3 != null) RenderUtils.drawLine(context, p2[0], p2[1], p3[0], p3[1], col, 1.5f);
        if (p3 != null && p4 != null) RenderUtils.drawLine(context, p3[0], p3[1], p4[0], p4[1], col, 1.5f);
        if (p4 != null && p1 != null) RenderUtils.drawLine(context, p4[0], p4[1], p1[0], p1[1], col, 1.5f);

        // Top rectangle
        if (p5 != null && p6 != null) RenderUtils.drawLine(context, p5[0], p5[1], p6[0], p6[1], col, 1.0f);
        if (p6 != null && p7 != null) RenderUtils.drawLine(context, p6[0], p6[1], p7[0], p7[1], col, 1.0f);
        if (p7 != null && p8 != null) RenderUtils.drawLine(context, p7[0], p7[1], p8[0], p8[1], col, 1.0f);
        if (p8 != null && p5 != null) RenderUtils.drawLine(context, p8[0], p8[1], p5[0], p5[1], col, 1.0f);

        // Vertical pillars
        if (p1 != null && p5 != null) RenderUtils.drawLine(context, p1[0], p1[1], p5[0], p5[1], col, 1.0f);
        if (p2 != null && p6 != null) RenderUtils.drawLine(context, p2[0], p2[1], p6[0], p6[1], col, 1.0f);
        if (p3 != null && p7 != null) RenderUtils.drawLine(context, p3[0], p3[1], p7[0], p7[1], col, 1.0f);
        if (p4 != null && p8 != null) RenderUtils.drawLine(context, p4[0], p4[1], p8[0], p8[1], col, 1.0f);
    }

    private void drawChunkFloor(DrawContext context, int x1, int y, int z1, int x2, int z2, int col, float tickDelta) {
        int[] p1 = ProjectionUtil.project(new Vec3d(x1, y, z1), tickDelta);
        int[] p2 = ProjectionUtil.project(new Vec3d(x2, y, z1), tickDelta);
        int[] p3 = ProjectionUtil.project(new Vec3d(x2, y, z2), tickDelta);
        int[] p4 = ProjectionUtil.project(new Vec3d(x1, y, z2), tickDelta);

        if (p1 == null || p2 == null || p3 == null || p4 == null) return;

        int minX = Math.min(Math.min(p1[0], p2[0]), Math.min(p3[0], p4[0]));
        int maxX = Math.max(Math.max(p1[0], p2[0]), Math.max(p3[0], p4[0]));
        int minY = Math.min(Math.min(p1[1], p2[1]), Math.min(p3[1], p4[1]));
        int maxY = Math.max(Math.max(p1[1], p2[1]), Math.max(p3[1], p4[1]));

        int sw = RenderUtils.screenWidth();
        int sh = RenderUtils.screenHeight();
        if (maxX < 0 || maxY < 0 || minX > sw || minY > sh) return;

        context.fill(minX, minY, maxX, maxY, col);
    }
}
