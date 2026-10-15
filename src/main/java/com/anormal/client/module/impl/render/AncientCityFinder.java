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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds ancient cities by their sculk clusters.
 *
 * <p><b>What was wrong.</b> Two things. The sweep ran over the whole
 * {@code 97 x 33 x 97} box in a single tick (about 310 000 lookups), which
 * stuttered the client every four seconds. And the result list was cleared on
 * every sweep, so every marker vanished and reappeared on a 4 second cycle
 * instead of staying put. The clustering loop could also degrade to
 * {@code O(n^3)} on a dense sculk patch.</p>
 *
 * <p>Now the sweep is budgeted, hits persist until the blocks are actually
 * gone, and clustering is a single pass through {@link Clusterer}.</p>
 */
public class AncientCityFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Horizontal scan radius in blocks", 48.0, 16.0, 128.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up/down to scan", 24.0, 8.0, 48.0, 4.0);
    public final NumberSetting minBlocks = new NumberSetting("Min Blocks", "Sculk blocks to count as a city", 5.0, 2.0, 30.0, 1.0);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max city markers drawn", 5.0, 1.0, 20.0, 1.0);
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Log new cities in chat", true);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw a 3D block outline", true);
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", "Thickness of the outline", 1.0, 1.0, 4.0, 1.0);
    public final ColorSetting color = new ColorSetting("Color", "City marker color", ColorUtils.rgba(0, 200, 180, 255));

    private static final int SCAN_BUDGET = 4000;
    private static final double CLUSTER_RADIUS = 14.0;
    private static final long RENDER_BUDGET_NS = 4_000_000L;

    private final Map<BlockPos, Integer> hits = new HashMap<>();
    private final List<Clusterer.Cluster> cities = new ArrayList<>();
    private final Set<String> logged = new HashSet<>();
    private final BlockScanner scanner = new BlockScanner();

    private int originX = Integer.MIN_VALUE, originY, originZ;
    private boolean dirty = true;

    public AncientCityFinder() {
        super("AncientCityFinder", "Finds sculk clusters marking ancient cities", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(minBlocks);
        addSetting(maxShown);
        addSetting(chatLog);
        addSetting(outline);
        addSetting(outlineWidth);
        addSetting(color);
    }

    @Override
    public void onDisable() {
        hits.clear();
        cities.clear();
        scanner.resetCylinder();
        originX = Integer.MIN_VALUE;
    }

    private static boolean isSculk(String path) {
        return path.contains("sculk");
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        int vr = verticalRange.getValue().intValue();

        if (originX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - originX) > 16
                || Math.abs(origin.getZ() - originZ) > 16) {
            originX = origin.getX();
            originY = origin.getY();
            originZ = origin.getZ();
            scanner.resetCylinder();
            prune(origin, r, vr);
        }

        // Remove sculk blocks that are no longer there.
        hits.keySet().removeIf(pos -> {
            if (!mc.world.isChunkLoaded(pos)) return false;
            String path = Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).getPath();
            if (!isSculk(path)) {
                dirty = true;
                return true;
            }
            return false;
        });

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scanCylinder(mc.world, originX, originY, originZ, r, vr, SCAN_BUDGET,
                    scratch,
                    state -> isSculk(Registries.BLOCK.getId(state.getBlock()).getPath()),
                    (pos, state) -> {
                        if (hits.put(pos.toImmutable(), 1) == null) dirty = true;
                    });
        } catch (Throwable ignored) {
        }

        if (dirty) {
            rebuild();
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

    private void rebuild() {
        cities.clear();
        cities.addAll(Clusterer.cluster(hits.keySet(),
                CLUSTER_RADIUS, minBlocks.getValue().intValue(), maxShown.getValue().intValue()));

        if (!chatLog.isEnabled() || mc.inGameHud == null) return;
        for (Clusterer.Cluster c : cities) {
            BlockPos center = c.center();
            String key = (center.getX() >> 4) + "," + (center.getY() >> 4) + "," + (center.getZ() >> 4);
            if (!logged.add(key)) continue;
            try {
                mc.inGameHud.getChatHud().addMessage(Text.literal(
                        "§3[City] §f" + c.size + " sculk at " + center.getX() + " " + center.getY() + " " + center.getZ()));
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || cities.isEmpty()) return;

        int col = color.getValue();
        int width = outlineWidth.getValue().intValue();
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;
        int drawn = 0;
        int limit = maxShown.getValue().intValue();

        for (Clusterer.Cluster c : cities) {
            if (drawn++ >= limit || System.nanoTime() > deadline) break;

            BlockPos center = c.center();
            if (outline.isEnabled()) {
                RenderUtils.drawBlockOutline(context, center, col, tickDelta, width);
            }

            int[] sc = ProjectionUtil.project(
                    new Vec3d(center.getX() + 0.5, center.getY() + 1.2, center.getZ() + 0.5), tickDelta);
            if (sc == null || mc.textRenderer == null) continue;
            RenderUtils.drawText(context, mc.textRenderer, "§3" + c.size + "x", sc[0] + 3, sc[1] - 4, 0xFFFFFFFF, true);
        }
    }
}
