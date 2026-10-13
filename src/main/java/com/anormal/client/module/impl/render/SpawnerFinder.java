package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Locates mob spawners (dungeon and trial) through walls.
 *
 * <p><b>Sliding fix:</b> markers are projected against the interpolated render
 * camera ({@code ProjectionUtil} now caches {@code Camera.getYaw()/getPitch()/
 * getCameraPos()} once per frame), so the outline stays locked to its block
 * while running or flying instead of drifting behind you.</p>
 *
 * <p><b>Spike fix:</b> the scan is budgeted ({@link BlockScanner}), so a large
 * range no longer freezes the client, and rendering has a hard per-frame time
 * budget with off-screen culling.</p>
 */
public class SpawnerFinder extends Module {
    public final ModeSetting filter = new ModeSetting("Spawners", "Which mob spawners to find", "All", "All", "Dungeon", "Trial");
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Shows distance to the spawner on the overlay", true);
    public final BooleanSetting showMob = new BooleanSetting("Show Mob", "Shows spawner kind (Dungeon/Trial)", true);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Only renders spawners within this range", 48.0, 8.0, 128.0, 4.0);
    public final ColorSetting color = new ColorSetting("Color", "Spawner overlay color", ColorUtils.rgba(255, 60, 220, 255));

    // ---- Render ----
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw a 3D block outline", true);
    public final BooleanSetting fill = new BooleanSetting("Fill", "Draw a translucent fill", false);
    public final NumberSetting fillOpacity = new NumberSetting("Fill Opacity", "Opacity of the fill", 90.0, 0.0, 255.0, 5.0);
    public final NumberSetting outlineWidth = new NumberSetting("Outline Width", "Thickness of the outline", 1.0, 1.0, 4.0, 1.0);
    public final NumberSetting maxRendered = new NumberSetting("Max Rendered", "Max spawners drawn at once", 150.0, 10.0, 500.0, 10.0);

    private final Map<BlockPos, String> found = new HashMap<>();
    private final BlockScanner scanner = new BlockScanner();
    private int scanOriginX = Integer.MIN_VALUE, scanOriginY, scanOriginZ;

    private static final int SCAN_BUDGET = 2500;
    private static final long RENDER_BUDGET_NS = 5_000_000L;

    public SpawnerFinder() {
        super("SpawnerFinder", "Locates and highlights mob spawners through walls", Category.RENDER);
        addSetting(filter);
        addSetting(showDistance);
        addSetting(showMob);
        addSetting(maxDistance);
        addSetting(color);
        addSetting(outline);
        addSetting(fill);
        addSetting(fillOpacity);
        addSetting(outlineWidth);
        addSetting(maxRendered);
    }

    @Override
    public void onDisable() {
        found.clear();
        scanner.reset();
        scanOriginX = Integer.MIN_VALUE;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = maxDistance.getValue().intValue();

        if (scanOriginX == Integer.MIN_VALUE
                || Math.abs(origin.getX() - scanOriginX) > 16
                || Math.abs(origin.getZ() - scanOriginZ) > 16) {
            scanOriginX = origin.getX();
            scanOriginY = origin.getY();
            scanOriginZ = origin.getZ();
            prune(origin, r);
        }

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scan(mc.world, scanOriginX, scanOriginY, scanOriginZ, r, SCAN_BUDGET,
                    scratch, state -> kindOf(state.getBlock()) != null,
                    (pos, state) -> {
                        String kind = kindOf(state.getBlock());
                        if (kind != null) found.put(pos.toImmutable(), kind);
                    });
        } catch (Throwable ignored) {
        }
    }

    private void prune(BlockPos origin, int r) {
        int rr = r * r;
        found.keySet().removeIf(pos -> {
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            return dx * dx + dz * dz > rr || Math.abs(pos.getY() - origin.getY()) > r + 1;
        });
    }

    /** @return "Dungeon" / "Trial", or null when the block is not a wanted spawner. */
    private String kindOf(Block block) {
        if (block == Blocks.SPAWNER) {
            return filter.is("Trial") ? null : "Dungeon";
        }
        if (block == Blocks.TRIAL_SPAWNER) {
            return filter.is("Dungeon") ? null : "Trial";
        }
        // Fallback for any modded/renamed spawner block.
        try {
            String path = Registries.BLOCK.getId(block).getPath();
            if (!path.contains("spawner")) return null;
            if (filter.is("Dungeon") && !path.equals("spawner")) return null;
            if (filter.is("Trial") && !path.contains("trial")) return null;
            return path.contains("trial") ? "Trial" : "Dungeon";
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || found.isEmpty()) return;

        int c = color.getValue();
        int limit = maxRendered.getValue().intValue();
        int drawn = 0;
        int alpha = fillOpacity.getValue().intValue();
        int width = outlineWidth.getValue().intValue();
        long deadline = System.nanoTime() + RENDER_BUDGET_NS;

        try {
            for (Map.Entry<BlockPos, String> e : found.entrySet()) {
                if (drawn >= limit) break;
                if ((drawn & 7) == 0 && System.nanoTime() > deadline) break;

                BlockPos p = e.getKey();

                // Off-screen cull before emitting geometry.
                if (RenderUtils.projectBlockBounds(p, tickDelta) == null) continue;
                drawn++;

                if (fill.isEnabled()) RenderUtils.drawBlockFill(context, p, c, tickDelta, alpha);
                if (outline.isEnabled()) RenderUtils.drawBlockOutline(context, p, c, tickDelta, width);

                if (mc.textRenderer == null) continue;

                if (showDistance.isEnabled()) {
                    String d = (int) Math.sqrt(p.getSquaredDistance(mc.player.getBlockPos())) + "m";
                    int[] sc = ProjectionUtil.project(
                            new Vec3d(p.getX() + 0.5, p.getY() + 1.3, p.getZ() + 0.5), tickDelta);
                    if (sc != null) RenderUtils.drawText(context, mc.textRenderer, d, sc[0] + 3, sc[1] - 4, c, true);
                }
                if (showMob.isEnabled()) {
                    String kind = e.getValue();
                    int[] sc = ProjectionUtil.project(
                            new Vec3d(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5), tickDelta);
                    if (sc != null) {
                        String label = kind.equals("Trial") ? "Trial Spawner" : "Dungeon";
                        RenderUtils.drawText(context, mc.textRenderer, "§e" + label,
                                sc[0] - mc.textRenderer.getWidth(label) / 2, sc[1] + 8, c, true);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
