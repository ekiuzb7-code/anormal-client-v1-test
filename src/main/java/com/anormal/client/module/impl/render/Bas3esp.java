package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Bas3 esp (Base ESP)
 * Inspired by Prestige Client V5.
 *
 * Locates storage clusters, container chests, and redstone machines below Y=0.
 * Groups nearby containers into clusters and displays 3D bounding boxes
 * with informative panel tags: "[Base ESP] 58m - Unclear (14 containers, 14 hoppers)".
 */
public class Bas3esp extends Module {

    public final NumberSetting range = new NumberSetting("Range", "Scan radius in blocks", 96.0, 32.0, 160.0, 8.0);
    public final NumberSetting minBlocks = new NumberSetting("Min Blocks", "Minimum containers required to flag cluster", 8.0, 3.0, 30.0, 1.0);
    public final NumberSetting clusterRadius = new NumberSetting("Cluster Radius", "Max distance between blocks in same cluster", 16.0, 8.0, 32.0, 2.0);
    public final BooleanSetting showPanels = new BooleanSetting("Show Panels", "Display detailed cluster info panels", true);
    public final BooleanSetting tracers = new BooleanSetting("Tracers", "Draw lines to detected bases", false);
    public final BooleanSetting notify = new BooleanSetting("Notify", "Send chat alert when a new base is found", true);
    public final ColorSetting color = new ColorSetting("Color", "Cluster box outline color", ColorUtils.rgba(255, 180, 0, 255));

    public static class BaseCluster {
        public BlockPos minPos;
        public BlockPos maxPos;
        public BlockPos center;
        public int totalContainers;
        public int hoppers;
        public int chests;
        public int machines;

        public BaseCluster(BlockPos first) {
            this.minPos = first;
            this.maxPos = first;
            this.center = first;
        }

        public void add(BlockPos p, String type) {
            minPos = new BlockPos(Math.min(minPos.getX(), p.getX()), Math.min(minPos.getY(), p.getY()), Math.min(minPos.getZ(), p.getZ()));
            maxPos = new BlockPos(Math.max(maxPos.getX(), p.getX()), Math.max(maxPos.getY(), p.getY()), Math.max(maxPos.getZ(), p.getZ()));
            center = new BlockPos((minPos.getX() + maxPos.getX()) / 2, (minPos.getY() + maxPos.getY()) / 2, (minPos.getZ() + maxPos.getZ()) / 2);
            totalContainers++;
            if (type.contains("hopper")) hoppers++;
            else if (type.contains("chest") || type.contains("barrel") || type.contains("shulker")) chests++;
            else machines++;
        }
    }

    private final List<BaseCluster> clusters = new ArrayList<>();
    private final Set<String> notified = new HashSet<>();
    private int tickCounter = 0;

    public Bas3esp() {
        super("Bas3 esp", "Highlights storage and redstone clusters below Y 0", Category.RENDER);
        addSetting(range);
        addSetting(minBlocks);
        addSetting(clusterRadius);
        addSetting(showPanels);
        addSetting(tracers);
        addSetting(notify);
        addSetting(color);
    }

    @Override
    public void onDisable() {
        clusters.clear();
        notified.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++tickCounter < 40) return; // scan every 2 seconds
        tickCounter = 0;

        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        double cDistSq = Math.pow(clusterRadius.getValue(), 2);

        List<Map.Entry<BlockPos, String>> found = new ArrayList<>();
        BlockPos.Mutable p = new BlockPos.Mutable();

        // Scan underground blocks below Y=0 (Prestige V5 feature)
        int minY = mc.world.getBottomY();
        int maxY = 0;

        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                if (dx * dx + dz * dz > r * r) continue;
                for (int y = minY; y <= maxY; y += 2) {
                    p.set(playerPos.getX() + dx, y, playerPos.getZ() + dz);
                    if (!mc.world.isChunkLoaded(p)) continue;

                    BlockState bs = mc.world.getBlockState(p);
                    if (bs.isAir()) continue;

                    String path = Registries.BLOCK.getId(bs.getBlock()).getPath();
                    if (path.contains("chest") || path.contains("hopper") || path.contains("barrel")
                            || path.contains("shulker_box") || path.contains("dropper") || path.contains("dispenser")
                            || path.contains("crafter")) {
                        found.add(new AbstractMap.SimpleEntry<>(p.toImmutable(), path));
                        if (found.size() > 1500) break;
                    }
                }
            }
        }

        // Cluster points
        List<BaseCluster> newClusters = new ArrayList<>();
        List<Map.Entry<BlockPos, String>> remaining = new ArrayList<>(found);

        while (!remaining.isEmpty()) {
            Map.Entry<BlockPos, String> seed = remaining.remove(0);
            BaseCluster cluster = new BaseCluster(seed.getKey());
            cluster.add(seed.getKey(), seed.getValue());

            boolean expanded;
            do {
                expanded = false;
                for (int i = remaining.size() - 1; i >= 0; i--) {
                    Map.Entry<BlockPos, String> candidate = remaining.get(i);
                    double distSq = candidate.getKey().getSquaredDistance(cluster.center);
                    if (distSq <= cDistSq) {
                        cluster.add(candidate.getKey(), candidate.getValue());
                        remaining.remove(i);
                        expanded = true;
                    }
                }
            } while (expanded);

            if (cluster.totalContainers >= minBlocks.getValue().intValue()) {
                newClusters.add(cluster);
                String key = (cluster.center.getX() >> 4) + "," + (cluster.center.getZ() >> 4);
                if (notify.isEnabled() && notified.add(key) && mc.inGameHud != null) {
                    try {
                        mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(
                                String.format("§6[Bas3 esp] §fBase cluster found near %d %d %d (%d containers)",
                                        cluster.center.getX(), cluster.center.getY(), cluster.center.getZ(), cluster.totalContainers)));
                    } catch (Throwable ignored) {}
                }
            }
        }

        clusters.clear();
        clusters.addAll(newClusters);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || clusters.isEmpty()) return;

        int col = color.getValue();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        for (BaseCluster cl : clusters) {
            Vec3d center = new Vec3d(cl.center.getX() + 0.5, cl.center.getY() + 0.5, cl.center.getZ() + 0.5);
            int[] sc = ProjectionUtil.project(center, tickDelta);
            if (sc == null) continue;

            // Draw 3D wireframe box around cluster
            drawClusterBox(context, cl, col, tickDelta);

            // Tracers
            if (tracers.isEnabled()) {
                RenderUtils.drawLine(context, sw / 2, sh / 2, sc[0], sc[1], col, 1.0f);
            }

            // Prestige V5 style panel tag: "[Base ESP] 58m - Unclear (14 containers, 14 hoppers)"
            if (showPanels.isEnabled() && mc.textRenderer != null) {
                int dist = (int) Math.sqrt(mc.player.squaredDistanceTo(center));
                String title = String.format("§6BASE ESP §f%dm", dist);
                String sub = String.format("§7%d containers, %d hoppers", cl.totalContainers, cl.hoppers);

                int w1 = mc.textRenderer.getWidth(title);
                int w2 = mc.textRenderer.getWidth(sub);
                int panelW = Math.max(w1, w2) + 12;
                int panelH = 24;

                int px = sc[0] - panelW / 2;
                int py = sc[1] - 14;

                RenderUtils.fill(context, px, py, px + panelW, py + panelH, ColorUtils.rgba(14, 16, 22, 230));
                RenderUtils.drawBorder(context, px, py, px + panelW, py + panelH, 1, col);

                RenderUtils.drawText(context, mc.textRenderer, title, px + (panelW - w1) / 2, py + 3, 0xFFFFFFFF, true);
                RenderUtils.drawText(context, mc.textRenderer, sub, px + (panelW - w2) / 2, py + 13, 0xFFDDDDDD, true);
            }
        }
    }

    private void drawClusterBox(DrawContext context, BaseCluster cl, int col, float tickDelta) {
        int x1 = cl.minPos.getX();
        int y1 = cl.minPos.getY();
        int z1 = cl.minPos.getZ();
        int x2 = cl.maxPos.getX() + 1;
        int y2 = cl.maxPos.getY() + 1;
        int z2 = cl.maxPos.getZ() + 1;

        int[] p1 = ProjectionUtil.project(new Vec3d(x1, y1, z1), tickDelta);
        int[] p2 = ProjectionUtil.project(new Vec3d(x2, y1, z1), tickDelta);
        int[] p3 = ProjectionUtil.project(new Vec3d(x2, y1, z2), tickDelta);
        int[] p4 = ProjectionUtil.project(new Vec3d(x1, y1, z2), tickDelta);

        int[] p5 = ProjectionUtil.project(new Vec3d(x1, y2, z1), tickDelta);
        int[] p6 = ProjectionUtil.project(new Vec3d(x2, y2, z1), tickDelta);
        int[] p7 = ProjectionUtil.project(new Vec3d(x2, y2, z2), tickDelta);
        int[] p8 = ProjectionUtil.project(new Vec3d(x1, y2, z2), tickDelta);

        if (p1 != null && p2 != null) RenderUtils.drawLine(context, p1[0], p1[1], p2[0], p2[1], col, 1.5f);
        if (p2 != null && p3 != null) RenderUtils.drawLine(context, p2[0], p2[1], p3[0], p3[1], col, 1.5f);
        if (p3 != null && p4 != null) RenderUtils.drawLine(context, p3[0], p3[1], p4[0], p4[1], col, 1.5f);
        if (p4 != null && p1 != null) RenderUtils.drawLine(context, p4[0], p4[1], p1[0], p1[1], col, 1.5f);

        if (p5 != null && p6 != null) RenderUtils.drawLine(context, p5[0], p5[1], p6[0], p6[1], col, 1.0f);
        if (p6 != null && p7 != null) RenderUtils.drawLine(context, p6[0], p6[1], p7[0], p7[1], col, 1.0f);
        if (p7 != null && p8 != null) RenderUtils.drawLine(context, p7[0], p7[1], p8[0], p8[1], col, 1.0f);
        if (p8 != null && p5 != null) RenderUtils.drawLine(context, p8[0], p8[1], p5[0], p5[1], col, 1.0f);

        if (p1 != null && p5 != null) RenderUtils.drawLine(context, p1[0], p1[1], p5[0], p5[1], col, 1.0f);
        if (p2 != null && p6 != null) RenderUtils.drawLine(context, p2[0], p2[1], p6[0], p6[1], col, 1.0f);
        if (p3 != null && p7 != null) RenderUtils.drawLine(context, p3[0], p3[1], p7[0], p7[1], col, 1.0f);
        if (p4 != null && p8 != null) RenderUtils.drawLine(context, p4[0], p4[1], p8[0], p8[1], col, 1.0f);
    }
}
