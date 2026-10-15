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
 * Finds hidden storage clusters and logs them.
 *
 * <p><b>What was wrong.</b> The container sweep covered the whole
 * {@code 97 x 33 x 97} box in one tick (~310 000 lookups), so the client hitched
 * every four seconds, and the stash list was cleared on each sweep so markers
 * blinked instead of staying on the containers. The clustering loop could also
 * blow up to {@code O(n^3)} inside a big storage room.</p>
 *
 * <p>The sweep is now budgeted, results persist while the containers exist, and
 * clustering is a single {@link Clusterer} pass.</p>
 */
public class StashFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Horizontal scan radius in blocks", 48.0, 16.0, 128.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up/down to scan", 24.0, 8.0, 48.0, 4.0);
    public final NumberSetting minStorages = new NumberSetting("Min Storages", "Containers to count as a stash", 3.0, 2.0, 20.0, 1.0);
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max stash markers drawn", 20.0, 1.0, 64.0, 1.0);
    public final BooleanSetting logInChat = new BooleanSetting("Log In Chat", "Log new stashes in chat", true);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance on marker", true);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw a 3D block outline", true);
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", "Thickness of the outline", 1.0, 1.0, 4.0, 1.0);
    public final ColorSetting color = new ColorSetting("Color", "Stash marker color", ColorUtils.rgba(255, 220, 0, 255));

    private static final int SCAN_BUDGET = 4000;
    private static final double CLUSTER_RADIUS = 8.0;
    private static final long RENDER_BUDGET_NS = 4_000_000L;

    private final Map<BlockPos, Integer> hits = new HashMap<>();
    private final List<Clusterer.Cluster> stashes = new ArrayList<>();
    private final Set<String> logged = new HashSet<>();
    private final BlockScanner scanner = new BlockScanner();

    private int originX = Integer.MIN_VALUE, originY, originZ;
    private boolean dirty = true;

    public StashFinder() {
        super("StashFinder", "Finds hidden storage clusters and logs them", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(minStorages);
        addSetting(maxShown);
        addSetting(logInChat);
        addSetting(showDistance);
        addSetting(outline);
        addSetting(outlineWidth);
        addSetting(color);
    }

    @Override
    public void onDisable() {
        hits.clear();
        stashes.clear();
        scanner.resetCylinder();
        originX = Integer.MIN_VALUE;
    }

    private static boolean isStorage(String path) {
        return path.contains("chest") || path.contains("barrel") || path.contains("shulker")
                || path.contains("hopper") || path.contains("furnace") || path.contains("dispenser")
                || path.contains("dropper");
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

        // Forget containers that have been broken.
        hits.keySet().removeIf(pos -> {
            if (!mc.world.isChunkLoaded(pos)) return false;
            String path = Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).getPath();
            if (!isStorage(path)) {
                dirty = true;
                return true;
            }
            return false;
        });

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scanCylinder(mc.world, originX, originY, originZ, r, vr, SCAN_BUDGET,
                    scratch,
                    state -> isStorage(Registries.BLOCK.getId(state.getBlock()).getPath()),
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
        stashes.clear();
        stashes.addAll(Clusterer.cluster(hits.keySet(),
                CLUSTER_RADIUS, minStorages.getValue().intValue(), maxShown.getValue().intValue()));

        if (!logInChat.isEnabled() || mc.inGameHud == null) return;
        for (Clusterer.Cluster c : stashes) {
            BlockPos center = c.center();
            String key = (center.getX() >> 4) + "," + (center.getY() >> 4) + "," + (center.getZ() >> 4);
            if (!logged.add(key)) continue;
            try {
                mc.inGameHud.getChatHud().addMessage(Text.literal(
                        "§6[Stash] §f" + c.size + " containers at " + center.getX() + " " + center.getY() + " " + center.getZ()));
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || stashes.isEmpty()) return;

        int col = color.getValue();
        int width = outlineWidth.getValue().intValue();
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;
        int drawn = 0;
        int limit = maxShown.getValue().intValue();

        for (Clusterer.Cluster c : stashes) {
            if (drawn++ >= limit || System.nanoTime() > deadline) break;

            BlockPos center = c.center();
            if (outline.isEnabled()) {
                RenderUtils.drawBlockOutline(context, center, col, tickDelta, width);
            }

            int[] sc = ProjectionUtil.project(
                    new Vec3d(center.getX() + 0.5, center.getY() + 1.2, center.getZ() + 0.5), tickDelta);
            if (sc == null || mc.textRenderer == null) continue;

            String label = "§6" + c.size + "x";
            if (showDistance.isEnabled()) {
                label += " " + (int) Math.sqrt(center.getSquaredDistance(mc.player.getBlockPos())) + "m";
            }
            RenderUtils.drawText(context, mc.textRenderer, label, sc[0] + 3, sc[1] - 4, 0xFFFFFFFF, true);
        }
    }
}
