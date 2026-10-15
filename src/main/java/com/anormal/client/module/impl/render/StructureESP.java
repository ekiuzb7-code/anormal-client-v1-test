package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.Clusterer;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Marks structures by their characteristic blocks.
 *
 * <p><b>Why it used to be unreliable.</b> The scan advanced by <b>4 blocks on
 * every axis</b> to stay fast, so it only ever looked at 1/64th of the volume.
 * A bed cluster, a sculk patch or an end portal frame almost never landed on a
 * sampled coordinate, which is why markers appeared randomly, pointed at empty
 * terrain, or never showed up at all.</p>
 *
 * <p>It now walks <b>every</b> block, but through the shared
 * {@link BlockScanner} so the cost is spread over many ticks instead of
 * stalling one. Results are kept between sweeps and only removed once the
 * blocks are genuinely gone, so markers no longer blink on and off, and
 * clustering uses {@link Clusterer} instead of the old nested re-scan.</p>
 */
public class StructureESP extends Module {
    public final BooleanSetting village = new BooleanSetting("Village", "Mark villages (bed clusters)", true);
    public final BooleanSetting stronghold = new BooleanSetting("Stronghold", "Mark end portal frames", true);
    public final BooleanSetting ancientCity = new BooleanSetting("Ancient City", "Mark sculk clusters", true);
    public final BooleanSetting mansion = new BooleanSetting("Mansion", "Mark dark oak clusters", false);
    public final BooleanSetting monument = new BooleanSetting("Monument", "Mark prismarine clusters", false);
    public final BooleanSetting bastion = new BooleanSetting("Bastion", "Mark blackstone clusters", false);
    public final BooleanSetting fortress = new BooleanSetting("Fortress", "Mark nether brick clusters", false);
    public final BooleanSetting endCity = new BooleanSetting("End City", "Mark end stone clusters", false);
    public final BooleanSetting desertTemple = new BooleanSetting("Desert Temple", "Mark chiseled sandstone", false);
    public final BooleanSetting jungleTemple = new BooleanSetting("Jungle Temple", "Mark mossy stone clusters", false);
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show structure name", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance", true);
    public final BooleanSetting showCoords = new BooleanSetting("Show Coordinates", "Show coordinates", false);
    public final BooleanSetting box = new BooleanSetting("Box", "Draw a 3D block outline", true);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", "Line from crosshair", false);
    public final NumberSetting range = new NumberSetting("Range", "Horizontal scan radius", 64.0, 16.0, 128.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up/down to scan", 24.0, 8.0, 48.0, 4.0);
    public final NumberSetting minBlocks = new NumberSetting("Min Blocks", "Blocks needed to count as a structure", 3.0, 1.0, 20.0, 1.0);
    public final NumberSetting maxFound = new NumberSetting("Max Found", "Max markers drawn", 8.0, 1.0, 32.0, 1.0);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Skip beyond this", 500.0, 50.0, 2000.0, 50.0);
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", "Thickness of the outlines", 1.0, 1.0, 4.0, 1.0);
    public final ColorSetting color = new ColorSetting("Color", "Marker color", ColorUtils.rgba(255, 220, 0, 255));

    /** Max world lookups per tick - constant cost regardless of radius. */
    private static final int SCAN_BUDGET = 4000;
    private static final long RENDER_BUDGET_NS = 5_000_000L;
    /** Two hits closer than this belong to the same structure. */
    private static final double CLUSTER_RADIUS = 28.0;

    /** Characteristic blocks found so far: position -> structure name. */
    private final Map<BlockPos, String> hits = new HashMap<>();
    private final List<Clusterer.Cluster> clusters = new ArrayList<>();
    private final BlockScanner scanner = new BlockScanner();

    private int originX = Integer.MIN_VALUE, originY, originZ;
    private boolean dirty = true;

    public StructureESP() {
        super("StructureESP", "Marks structures by characteristic blocks", Category.RENDER);
        addSetting(village);
        addSetting(stronghold);
        addSetting(ancientCity);
        addSetting(mansion);
        addSetting(monument);
        addSetting(bastion);
        addSetting(fortress);
        addSetting(endCity);
        addSetting(desertTemple);
        addSetting(jungleTemple);
        addSetting(showName);
        addSetting(showDistance);
        addSetting(showCoords);
        addSetting(box);
        addSetting(tracer);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(minBlocks);
        addSetting(maxFound);
        addSetting(maxDistance);
        addSetting(outlineWidth);
        addSetting(color);
    }

    @Override
    public void onDisable() {
        hits.clear();
        clusters.clear();
        scanner.resetCylinder();
        originX = Integer.MIN_VALUE;
    }

    private String match(String path) {
        if (path.contains("trial_spawner")) return null;
        if (path.contains("bed") && !path.contains("bedrock")) return "Village";
        if (path.contains("end_portal_frame")) return "Stronghold";
        if (path.contains("sculk")) return "Ancient City";
        if (path.contains("dark_oak_log")) return "Mansion";
        if (path.contains("prismarine")) return "Monument";
        if (path.contains("blackstone") || path.contains("basalt")) return "Bastion";
        if (path.contains("nether_brick") && !path.contains("chiseled")) return "Fortress";
        if (path.contains("end_stone_brick") || path.contains("purpur")) return "End City";
        if (path.contains("chiseled_sandstone") || path.contains("cut_sandstone")) return "Desert Temple";
        if (path.contains("mossy_cobblestone") || path.contains("chiseled_stone")) return "Jungle Temple";
        return null;
    }

    private boolean enabledFor(String name) {
        return switch (name) {
            case "Village" -> village.isEnabled();
            case "Stronghold" -> stronghold.isEnabled();
            case "Ancient City" -> ancientCity.isEnabled();
            case "Mansion" -> mansion.isEnabled();
            case "Monument" -> monument.isEnabled();
            case "Bastion" -> bastion.isEnabled();
            case "Fortress" -> fortress.isEnabled();
            case "End City" -> endCity.isEnabled();
            case "Desert Temple" -> desertTemple.isEnabled();
            case "Jungle Temple" -> jungleTemple.isEnabled();
            default -> false;
        };
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int vr = verticalRange.getValue().intValue();

        // Re-centre when the player has moved far enough.
        if (originX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - originX) > 16
                || Math.abs(origin.getZ() - originZ) > 16) {
            originX = origin.getX();
            originY = origin.getY();
            originZ = origin.getZ();
            scanner.resetCylinder();
            prune(origin, r, vr);
        }

        // Drop hits whose block is gone or whose type was switched off.
        hits.entrySet().removeIf(e -> {
            if (!enabledFor(e.getValue())) return true;
            if (!mc.world.isChunkLoaded(e.getKey())) return false;
            BlockState bs = mc.world.getBlockState(e.getKey());
            String m = match(Registries.BLOCK.getId(bs.getBlock()).getPath());
            if (m == null || !m.equals(e.getValue())) {
                dirty = true;
                return true;
            }
            return false;
        });

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scanCylinder(mc.world, originX, originY, originZ, r, vr, SCAN_BUDGET,
                    scratch,
                    state -> {
                        String m = match(Registries.BLOCK.getId(state.getBlock()).getPath());
                        return m != null && enabledFor(m);
                    },
                    (pos, state) -> {
                        String m = match(Registries.BLOCK.getId(state.getBlock()).getPath());
                        if (m == null || !enabledFor(m)) return;
                        BlockPos immutable = pos.toImmutable();
                        String old = hits.put(immutable, m);
                        if (!m.equals(old)) dirty = true;
                    });
        } catch (Throwable ignored) {
        }

        if (dirty) {
            rebuildClusters();
            dirty = false;
        }
    }

    private void prune(BlockPos origin, int r, int vr) {
        int rr = r * r;
        boolean removed = hits.keySet().removeIf(pos -> {
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            int dy = pos.getY() - origin.getY();
            return dx * dx + dz * dz > rr || Math.abs(dy) > vr + 2;
        });
        if (removed) dirty = true;
    }

    /** Groups hits by structure type, then clusters each type separately. */
    private void rebuildClusters() {
        clusters.clear();
        int min = minBlocks.getValue().intValue();
        int limit = maxFound.getValue().intValue();

        Map<String, List<BlockPos>> byType = new HashMap<>();
        for (Map.Entry<BlockPos, String> e : hits.entrySet()) {
            byType.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }

        List<Clusterer.Cluster> all = new ArrayList<>();
        for (List<BlockPos> group : byType.values()) {
            all.addAll(Clusterer.cluster(group, CLUSTER_RADIUS, min, limit));
        }
        all.sort((a, b) -> Integer.compare(b.size, a.size));
        for (int i = 0; i < all.size() && i < limit; i++) clusters.add(all.get(i));
    }

    /** Label for a cluster: the type of the nearest hit to its centre. */
    private String labelFor(Clusterer.Cluster c) {
        BlockPos center = c.center();
        String best = "Structure";
        double bestD = Double.MAX_VALUE;
        for (Map.Entry<BlockPos, String> e : hits.entrySet()) {
            double d = e.getKey().getSquaredDistance(center);
            if (d < bestD) {
                bestD = d;
                best = e.getValue();
            }
        }
        return best;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || clusters.isEmpty()) return;

        int col = color.getValue();
        int width = outlineWidth.getValue().intValue();
        int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;

        for (Clusterer.Cluster c : clusters) {
            if (System.nanoTime() > deadline) break;

            BlockPos center = c.center();
            double dist = Math.sqrt(center.getSquaredDistance(mc.player.getBlockPos()));
            if (dist > maxDistance.getValue()) continue;

            // Real 3D outline around the structure centre block.
            if (box.isEnabled()) {
                RenderUtils.drawBlockOutline(context, center, col, tickDelta, width);
            }

            int[] sc = ProjectionUtil.project(
                    new Vec3d(center.getX() + 0.5, center.getY() + 1.2, center.getZ() + 0.5), tickDelta);
            if (sc == null) continue;

            if (tracer.isEnabled()) {
                RenderUtils.drawLine(context, w / 2, h / 2, sc[0], sc[1], col, 1.0f);
            }

            if (mc.textRenderer == null) continue;
            StringBuilder label = new StringBuilder();
            if (showName.isEnabled()) label.append("§6").append(labelFor(c));
            if (showDistance.isEnabled()) {
                if (label.length() > 0) label.append(" ");
                label.append("§f").append((int) dist).append("m");
            }
            if (showCoords.isEnabled()) {
                if (label.length() > 0) label.append(" ");
                label.append(String.format("§7%d %d %d", center.getX(), center.getY(), center.getZ()));
            }
            if (label.length() > 0) {
                String text = label.toString();
                RenderUtils.drawText(context, mc.textRenderer, text,
                        sc[0] - mc.textRenderer.getWidth(text) / 2, sc[1] + 4, 0xFFFFFFFF, true);
            }
        }
    }
}
