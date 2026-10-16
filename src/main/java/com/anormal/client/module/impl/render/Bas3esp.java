package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
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
 * Locates storage clusters, container chests and redstone machines, then groups
 * nearby containers into clusters and draws 3D bounding boxes with panel tags.
 *
 * <p>Fixed on the shared budgeted {@link BlockScanner}:</p>
 * <ul>
 *   <li>The old scan only covered {@code y <= 0} and used a step of 2 on every
 *       axis, so it missed every base above bedrock level and blinked on single
 *       chests. The vertical range is now independent and centred on the
 *       player, and every block in the cylinder is checked.</li>
 *   <li>The cost per tick is a fixed budget, so a 96-block radius no longer
 *       freezes the client.</li>
 *   <li>Results persist between sweeps and are re-validated, so markers do not
 *       flicker and clusters stay stable.</li>
 * </ul>
 */
public class Bas3esp extends Module {

    public final NumberSetting range = new NumberSetting("Range", "Horizontal search radius", 96.0, 32.0, 160.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up and down to search", 96.0, 8.0, 320.0, 8.0);
    public final NumberSetting scanSpeed = new NumberSetting("Scan Speed", "Blocks checked per tick", 8000.0, 2000.0, 30000.0, 500.0);
    public final NumberSetting recentreDistance = new NumberSetting("Recentre Distance", "How far you may move before the search restarts", 8.0, 4.0, 32.0, 2.0);
    public final NumberSetting minBlocks = new NumberSetting("Min Blocks", "Minimum containers required to flag cluster", 4.0, 2.0, 30.0, 1.0);
    public final NumberSetting clusterRadius = new NumberSetting("Cluster Radius", "Max distance between blocks in same cluster", 16.0, 8.0, 32.0, 2.0);
    public final BooleanSetting showPanels = new BooleanSetting("Show Panels", "Display detailed cluster info panels", true);
    public final BooleanSetting tracers = new BooleanSetting("Tracers", "Draw lines to detected bases", false);
    public final BooleanSetting notify = new BooleanSetting("Notify", "Send chat alert when a new base is found", true);
    public final ColorSetting color = new ColorSetting("Color", "Cluster box outline color", ColorUtils.rgba(255, 180, 0, 255));

    private static final long RENDER_BUDGET_NS = 5_000_000L;
    private static final int MAX_FOUND = 2000;

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

    private final BlockScanner scanner = new BlockScanner();
    private final Map<BlockPos, String> found = new HashMap<>();
    private final List<BaseCluster> clusters = new ArrayList<>();
    private final Set<String> notified = new HashSet<>();
    private int centreX = Integer.MIN_VALUE, centreY, centreZ;
    private boolean sweepComplete = false;
    private int clusterTicks = 0;

    public Bas3esp() {
        super("Bas3 esp", "Highlights storage and redstone clusters nearby", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(scanSpeed);
        addSetting(recentreDistance);
        addSetting(minBlocks);
        addSetting(clusterRadius);
        addSetting(showPanels);
        addSetting(tracers);
        addSetting(notify);
        addSetting(color);
    }

    @Override
    public void onDisable() {
        found.clear();
        clusters.clear();
        notified.clear();
        scanner.resetCylinder();
        centreX = Integer.MIN_VALUE;
        sweepComplete = false;
    }

    private boolean isContainer(String path) {
        return path.contains("chest") || path.contains("hopper") || path.contains("barrel")
                || path.contains("shulker_box") || path.contains("dropper") || path.contains("dispenser")
                || path.contains("crafter");
    }

    private boolean isWanted(BlockState state) {
        return isContainer(Registries.BLOCK.getId(state.getBlock()).getPath());
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int vr = verticalRange.getValue().intValue();

        if (centreX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - centreX) > recentreDistance.getValue()
                || Math.abs(origin.getY() - centreY) > recentreDistance.getValue()
                || Math.abs(origin.getZ() - centreZ) > recentreDistance.getValue()) {
            centreX = origin.getX();
            centreY = origin.getY();
            centreZ = origin.getZ();
            scanner.resetCylinder();
            prune(origin, r, vr);
            sweepComplete = false;
        }

        // Re-validate so removed containers vanish immediately (no ghost markers).
        found.entrySet().removeIf(e -> {
            if (!mc.world.isChunkLoaded(e.getKey())) return false;
            return !isWanted(mc.world.getBlockState(e.getKey()));
        });

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scanCylinder(mc.world, centreX, centreY, centreZ, r, vr,
                    scanSpeed.getValue().intValue(), scratch,
                    state -> !state.isAir() && isWanted(state),
                    new BlockScanner.Callback() {
                        @Override
                        public void accept(BlockPos.Mutable pos, BlockState state) {
                            if (found.size() < MAX_FOUND) {
                                found.put(pos.toImmutable(), Registries.BLOCK.getId(state.getBlock()).getPath());
                            }
                        }

                        @Override
                        public void onSweepComplete() {
                            sweepComplete = true;
                        }
                    });
        } catch (Throwable ignored) {
        }

        // Cluster every second (and once a sweep finishes) so markers appear
        // quickly instead of waiting for a full 96-block cylinder sweep.
        if (sweepComplete || ++clusterTicks >= 20) {
            sweepComplete = false;
            clusterTicks = 0;
            cluster();
        }
    }

    private void prune(BlockPos origin, int range, int verticalRange) {
        int rr = range * range;
        found.keySet().removeIf(pos -> {
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            return dx * dx + dz * dz > rr || Math.abs(pos.getY() - origin.getY()) > verticalRange + 2;
        });
    }

    private void cluster() {
        double cDistSq = Math.pow(clusterRadius.getValue(), 2);
        List<Map.Entry<BlockPos, String>> remaining = new ArrayList<>(found.entrySet());
        List<BaseCluster> newClusters = new ArrayList<>();

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
                    } catch (Throwable ignored) {
                    }
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
        int drawn = 0;
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;

        for (BaseCluster cl : clusters) {
            if ((drawn & 7) == 0 && System.nanoTime() > deadline) break;

            Vec3d center = new Vec3d(cl.center.getX() + 0.5, cl.center.getY() + 0.5, cl.center.getZ() + 0.5);
            int[] sc = ProjectionUtil.project(center, tickDelta);
            if (sc == null) continue;
            drawn++;

            // Draw 3D wireframe box around cluster
            drawClusterBox(context, cl, col, tickDelta);

            // Tracers
            if (tracers.isEnabled()) {
                RenderUtils.drawLine(context, sw / 2, sh / 2, sc[0], sc[1], col, 1.0f);
            }

            // Prestige V5 style panel tag
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
