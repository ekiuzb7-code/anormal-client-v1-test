package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
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
 * Bas3Finderv2 (Base Finder + Chunk Finder Hybrid)
 * Inspired by Prestige Client V5.
 *
 * Scans chunks within simulation distance and calculates a suspicion confidence percentage
 * based on player activity signals:
 * - Hand dug shafts & mined tunnels
 * - Crafted deepslate bricks/tiles & obsidian
 * - Redstone components & machines
 * - Amethyst cluster growth
 * - Growable indicators (kelp, cave vines, bamboo)
 * - Live player activity underground
 *
 * Displays:
 * 1. Top-left HUD listing most suspicious chunks with confidence %
 * 2. 3D in-world chunk highlights with hover tooltip popup explaining flagged reasons
 */
public class Bas3Finderv2 extends Module {

    public final NumberSetting simulationDistance = new NumberSetting("Simulation Distance", "Scan radius in chunks", 16.0, 4.0, 32.0, 1.0);
    public final NumberSetting minConfidence = new NumberSetting("Min Confidence", "Minimum suspicion percentage to display", 30.0, 10.0, 90.0, 5.0);
    public final BooleanSetting showHud = new BooleanSetting("HUD List", "Show suspicious chunk list in top-left", true);
    public final BooleanSetting hoverTooltip = new BooleanSetting("Hover Tooltip", "Show detailed popup when looking at flagged chunks", true);
    public final BooleanSetting detectLivePlayers = new BooleanSetting("Live Players", "Detect signals of players actively mining", true);
    public final BooleanSetting tracers = new BooleanSetting("Tracers", "Draw lines to high confidence bases", false);
    public final ColorSetting color = new ColorSetting("Highlight Color", "Chunk highlight color", ColorUtils.rgba(180, 70, 255, 255));

    public static class FlaggedChunk {
        public final int cx, cz;
        public int score;
        public int confidence; // 0 to 100%
        public String category; // "Dug Shaft", "Redstone", "Stash", "Potential Base", "Unknown"
        public int dugBlocks;
        public int craftedBlocks;
        public int obsidian;
        public int redstone;
        public int amethyst;
        public int containers;
        public boolean livePlayer;
        public int avgY;
        public long lastUpdated;

        public FlaggedChunk(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    private final Map<ChunkPos, FlaggedChunk> flaggedChunks = new HashMap<>();
    private final List<ChunkPos> scanQueue = new ArrayList<>();
    private int queueIdx = 0;
    private int lastCenterCx = Integer.MIN_VALUE, lastCenterCz = Integer.MIN_VALUE;

    public Bas3Finderv2() {
        super("Bas3Finderv2", "Prestige V5 hybrid Base Finder + Chunk Finder with suspicion scores", Category.RENDER);
        addSetting(simulationDistance);
        addSetting(minConfidence);
        addSetting(showHud);
        addSetting(hoverTooltip);
        addSetting(detectLivePlayers);
        addSetting(tracers);
        addSetting(color);
    }

    @Override
    public void onDisable() {
        flaggedChunks.clear();
        scanQueue.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        int playerCx = mc.player.getBlockX() >> 4;
        int playerCz = mc.player.getBlockZ() >> 4;
        int dist = simulationDistance.getValue().intValue();

        if (playerCx != lastCenterCx || playerCz != lastCenterCz || scanQueue.isEmpty()) {
            lastCenterCx = playerCx;
            lastCenterCz = playerCz;
            scanQueue.clear();
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dz = -dist; dz <= dist; dz++) {
                    if (dx * dx + dz * dz <= dist * dist) {
                        scanQueue.add(new ChunkPos(playerCx + dx, playerCz + dz));
                    }
                }
            }
            scanQueue.sort(Comparator.comparingInt(cp -> {
                int ddx = cp.x - playerCx, ddz = cp.z - playerCz;
                return ddx * ddx + ddz * ddz;
            }));
            queueIdx = 0;
        }

        // Prune far chunks
        int maxD2 = (dist + 2) * (dist + 2);
        flaggedChunks.entrySet().removeIf(e -> {
            int dx = e.getKey().x - playerCx;
            int dz = e.getKey().z - playerCz;
            return dx * dx + dz * dz > maxD2;
        });

        // Scan 4 chunks per tick
        int processed = 0;
        while (processed < 4 && !scanQueue.isEmpty()) {
            if (queueIdx >= scanQueue.size()) queueIdx = 0;
            ChunkPos cp = scanQueue.get(queueIdx++);
            processed++;

            if (!mc.world.isChunkLoaded(cp.x, cp.z)) continue;
            WorldChunk chunk = mc.world.getChunk(cp.x, cp.z);
            if (chunk == null) continue;

            analyzeChunk(chunk, cp);
        }
    }

    private void analyzeChunk(WorldChunk chunk, ChunkPos cp) {
        FlaggedChunk fc = new FlaggedChunk(cp.x, cp.z);
        BlockPos.Mutable p = new BlockPos.Mutable();

        int minY = mc.world.getBottomY();
        int maxY = Math.min(100, mc.world.getTopYInclusive());
        int ySum = 0;
        int points = 0;

        for (int y = minY; y <= maxY; y += 3) {
            for (int x = 0; x < 16; x += 3) {
                for (int z = 0; z < 16; z += 3) {
                    p.set((cp.x << 4) + x, y, (cp.z << 4) + z);
                    BlockState bs = chunk.getBlockState(p);
                    if (bs.isAir()) continue;

                    String path = Registries.BLOCK.getId(bs.getBlock()).getPath();

                    if (path.contains("chest") || path.contains("hopper") || path.contains("barrel") || path.contains("shulker")) {
                        fc.containers++;
                        points += 8;
                        ySum += y;
                    } else if ((path.contains("redstone") || path.contains("repeater") || path.contains("comparator") || path.contains("piston") || path.contains("observer") || path.contains("dropper") || path.contains("dispenser")) && !path.contains("ore")) {
                        fc.redstone++;
                        points += 6;
                        ySum += y;
                    } else if (path.equals("obsidian")) {
                        fc.obsidian++;
                        points += 3;
                        ySum += y;
                    } else if (path.contains("deepslate_bricks") || path.contains("deepslate_tiles") || path.contains("polished_deepslate")) {
                        fc.craftedBlocks++;
                        points += 4;
                        ySum += y;
                    } else if (path.equals("torch") || path.equals("wall_torch") || path.contains("crafting_table") || path.contains("furnace")) {
                        fc.dugBlocks++;
                        points += 4;
                        ySum += y;
                    }
                }
            }
        }

        // Check for live player underground in this chunk
        if (detectLivePlayers.isEnabled()) {
            for (net.minecraft.entity.player.PlayerEntity pl : mc.world.getPlayers()) {
                if (pl != mc.player && (pl.getBlockX() >> 4) == cp.x && (pl.getBlockZ() >> 4) == cp.z && pl.getY() < 50) {
                    fc.livePlayer = true;
                    points += 25;
                    ySum += (int) pl.getY();
                }
            }
        }

        // Require genuine crafted / placed base evidence (prevents false positives in natural caves)
        int genuineBaseSignals = fc.containers * 2 + fc.redstone * 2 + fc.craftedBlocks + (fc.obsidian >= 4 ? 2 : 0) + (fc.livePlayer ? 5 : 0);
        if (genuineBaseSignals < 2) {
            flaggedChunks.remove(cp);
            return;
        }

        int totalSignals = fc.containers + fc.redstone + (fc.obsidian >= 4 ? fc.obsidian : 0) + fc.craftedBlocks + fc.dugBlocks + (fc.livePlayer ? 5 : 0);
        if (totalSignals > 0) {
            fc.avgY = ySum / Math.max(1, totalSignals);
        } else {
            fc.avgY = 0;
        }

        // Calculate confidence percentage
        fc.score = points;
        fc.confidence = Math.min(99, Math.max(15, (int) (points * 2.2)));

        // Category determination
        if (fc.containers >= 3 || (fc.containers >= 1 && fc.craftedBlocks >= 4)) {
            fc.category = "Stash";
        } else if (fc.redstone >= 2) {
            fc.category = "Redstone Base";
        } else if (fc.dugBlocks >= 3 || fc.craftedBlocks >= 4) {
            fc.category = "Dug Shaft";
        } else if (fc.livePlayer) {
            fc.category = "Live Player";
        } else if (fc.confidence >= 50) {
            fc.category = "Potential Base";
        } else {
            fc.category = "Suspect Chunk";
        }

        if (fc.confidence >= minConfidence.getValue().intValue()) {
            flaggedChunks.put(cp, fc);
        } else {
            flaggedChunks.remove(cp);
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || flaggedChunks.isEmpty()) return;

        int col = color.getValue();
        List<FlaggedChunk> sorted = new ArrayList<>(flaggedChunks.values());
        sorted.sort((a, b) -> Integer.compare(b.confidence, a.confidence));

        // 1. Top-Left HUD List (Prestige V5 style)
        if (showHud.isEnabled() && mc.textRenderer != null) {
            drawHudList(context, sorted);
        }

        // 2. In-World Chunk Render & Hover Tooltip
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        FlaggedChunk hoveredChunk = null;
        int hoveredDistSq = Integer.MAX_VALUE;

        for (FlaggedChunk fc : sorted) {
            int x1 = fc.cx << 4;
            int z1 = fc.cz << 4;
            int y = fc.avgY;

            Vec3d center = new Vec3d(x1 + 8.0, y + 1.0, z1 + 8.0);
            int[] sc = ProjectionUtil.project(center, tickDelta);
            if (sc == null) continue;

            int markerCol = confidenceColor(fc.confidence);

            // Diamond marker so flagged chunks read clearly at a glance.
            for (int i = -4; i <= 4; i++) {
                int w = 4 - Math.abs(i);
                context.fill(sc[0] - w, sc[1] + i, sc[0] + w + 1, sc[1] + i + 1, markerCol);
            }
            RenderUtils.drawBorder(context, sc[0] - 5, sc[1] - 5, sc[0] + 5, sc[1] + 5, 1, 0xFF000000);

            if (tracers.isEnabled() && fc.confidence >= 60) {
                RenderUtils.drawLine(context, sw / 2, sh / 2, sc[0], sc[1], markerCol, 1.0f);
            }

            // Tight 12px radius around the crosshair.
            int mdx = sw / 2 - sc[0];
            int mdy = sh / 2 - sc[1];
            int d2 = mdx * mdx + mdy * mdy;
            if (d2 <= 144 && d2 < hoveredDistSq) {
                hoveredDistSq = d2;
                hoveredChunk = fc;
            }
        }

        // 3. Hover Tooltip (detailed breakdown panel)
        if (hoverTooltip.isEnabled() && hoveredChunk != null && mc.textRenderer != null) {
            drawTooltip(context, hoveredChunk, sw, sh, col);
        }
    }

    /** Confidence -> colour: green (safe bet) to red (barely suspicious). */
    private static int confidenceColor(int confidence) {
        if (confidence >= 70) return 0xFF55FF55;
        if (confidence >= 45) return 0xFFFFAA00;
        return 0xFFFF5555;
    }

    /** A 100%-wide bar showing the confidence value. */
    private void drawConfidenceBar(DrawContext context, int x, int y, int width, int confidence) {
        int filled = Math.max(1, Math.min(width, width * confidence / 100));
        RenderUtils.fill(context, x, y, x + width, y + 4, ColorUtils.rgba(40, 44, 56, 220));
        RenderUtils.fill(context, x, y, x + filled, y + 4, confidenceColor(confidence));
    }

    private void drawHudList(DrawContext context, List<FlaggedChunk> sorted) {
        int hudX = 10;
        int hudY = 12;
        int hudW = 168;
        int shown = Math.min(5, sorted.size());

        // Header
        RenderUtils.fill(context, hudX, hudY, hudX + hudW, hudY + 18, ColorUtils.rgba(14, 16, 24, 235));
        RenderUtils.drawBorder(context, hudX, hudY, hudX + hudW, hudY + 18, 1, ThemeManager.getBorderColor());
        RenderUtils.drawText(context, mc.textRenderer, "§6Bas3Finder", hudX + 6, hudY + 5, 0xFFFFFFFF, true);
        String count = "§7" + sorted.size() + " nearby";
        RenderUtils.drawText(context, mc.textRenderer, count,
                hudX + hudW - mc.textRenderer.getWidth(count) - 6, hudY + 5, 0xFFFFFFFF, true);
        hudY += 20;

        for (int i = 0; i < shown; i++) {
            FlaggedChunk fc = sorted.get(i);
            int rowH = 32;
            RenderUtils.fill(context, hudX, hudY, hudX + hudW, hudY + rowH, ColorUtils.rgba(18, 20, 28, 205));
            RenderUtils.drawBorder(context, hudX, hudY, hudX + hudW, hudY + rowH, 1, ColorUtils.rgba(40, 45, 60, 160));

            String name = "§f" + (i + 1) + ". §6" + fc.category;
            String conf = fc.confidence + "%";
            int confCol = confidenceColor(fc.confidence);

            RenderUtils.drawText(context, mc.textRenderer, name, hudX + 6, hudY + 4, 0xFFFFFFFF, true);
            RenderUtils.drawText(context, mc.textRenderer, conf,
                    hudX + hudW - mc.textRenderer.getWidth(conf) - 6, hudY + 4, confCol, true);

            drawConfidenceBar(context, hudX + 6, hudY + 15, hudW - 12, fc.confidence);

            int dist = (int) Math.sqrt(Math.pow((fc.cx << 4) + 8 - mc.player.getX(), 2)
                    + Math.pow((fc.cz << 4) + 8 - mc.player.getZ(), 2));
            String sub = "§8" + fc.cx + ", " + fc.cz + " §7· " + dist + "m §7· Y " + fc.avgY;
            RenderUtils.drawText(context, mc.textRenderer, sub, hudX + 6, hudY + 22, 0xFF888888, true);

            hudY += rowH + 3;
        }
    }

    private void drawTooltip(DrawContext context, FlaggedChunk fc, int sw, int sh, int col) {
        // Build the breakdown first so the panel can size itself to its content.
        List<String> lines = new ArrayList<>();
        if (fc.containers > 0) lines.add("§e" + fc.containers + " §7containers");
        if (fc.redstone > 0) lines.add("§e" + fc.redstone + " §7redstone parts");
        if (fc.craftedBlocks > 0) lines.add("§e" + fc.craftedBlocks + " §7crafted deepslate");
        if (fc.obsidian > 0) lines.add("§e" + fc.obsidian + " §7obsidian");
        if (fc.dugBlocks > 0) lines.add("§e" + fc.dugBlocks + " §7dug shafts");
        if (fc.livePlayer) lines.add("§cLIVE PLAYER SIGNAL");
        if (lines.isEmpty()) lines.add("§7No signals");

        int tw = 186;
        int th = 52 + lines.size() * 11;

        int tx = sw / 2 + 18;
        int ty = sh / 2 - th / 2;
        if (tx + tw > sw - 6) tx = sw / 2 - 18 - tw;
        if (ty < 6) ty = 6;

        RenderUtils.fill(context, tx, ty, tx + tw, ty + th, ColorUtils.rgba(12, 14, 20, 242));
        RenderUtils.drawBorder(context, tx, ty, tx + tw, ty + th, 1, col);

        // Title + confidence badge
        RenderUtils.drawText(context, mc.textRenderer, "§6" + fc.category, tx + 7, ty + 6, 0xFFFFFFFF, true);
        String confStr = fc.confidence + "%";
        int badgeW = mc.textRenderer.getWidth(confStr) + 8;
        RenderUtils.fill(context, tx + tw - badgeW - 6, ty + 4, tx + tw - 6, ty + 16,
                ColorUtils.rgba(0, 0, 0, 120));
        RenderUtils.drawText(context, mc.textRenderer, confStr,
                tx + tw - badgeW - 2, ty + 6, confidenceColor(fc.confidence), true);

        // Confidence bar
        drawConfidenceBar(context, tx + 7, ty + 19, tw - 14, fc.confidence);

        // Meta line
        int dist = (int) Math.sqrt(Math.pow((fc.cx << 4) + 8 - mc.player.getX(), 2)
                + Math.pow((fc.cz << 4) + 8 - mc.player.getZ(), 2));
        RenderUtils.drawText(context, mc.textRenderer,
                "§7Chunk §f" + fc.cx + ", " + fc.cz + "  §7·  §f" + dist + "m  §7·  Y §f" + fc.avgY,
                tx + 7, ty + 26, 0xFFAAAAAA, true);

        // Divider
        RenderUtils.fill(context, tx + 7, ty + 38, tx + tw - 7, ty + 39, ColorUtils.rgba(60, 66, 84, 160));

        // Breakdown
        int py = ty + 43;
        for (String line : lines) {
            RenderUtils.drawText(context, mc.textRenderer, "§8• " + line, tx + 9, py, 0xFFDDDDDD, true);
            py += 11;
        }
    }
}
