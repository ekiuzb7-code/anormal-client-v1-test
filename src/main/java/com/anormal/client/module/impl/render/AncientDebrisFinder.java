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
 * Ancient Debris Finder (Netherite Finder)
 * Inspired by Prestige Client V5.
 *
 * Scans the Nether for Ancient Debris. Calculates target generation pockets (Y=8 to Y=24, and Y=119)
 * and marks potential debris clusters with 3D wireframe boxes and tracers.
 */
public class AncientDebrisFinder extends Module {

    public final NumberSetting range = new NumberSetting("Range", "Horizontal scan radius in blocks", 48.0, 8.0, 96.0, 4.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up and down to scan", 32.0, 8.0, 128.0, 4.0);
    public final NumberSetting scanSpeed = new NumberSetting("Scan Speed", "Blocks checked per tick", 6000.0, 2000.0, 30000.0, 500.0);
    public final NumberSetting recentreDistance = new NumberSetting("Recentre Distance", "How far you may move before the scan restarts", 8.0, 4.0, 32.0, 2.0);
    public final BooleanSetting tracers = new BooleanSetting("Tracers", "Draw lines to debris blocks", true);
    public final BooleanSetting showDistance = new BooleanSetting("Distance", "Show distance in meters", true);
    public final BooleanSetting outline = new BooleanSetting("Outline", "Draw 3D block wireframe", true);
    public final ColorSetting color = new ColorSetting("Color", "Debris outline color", ColorUtils.rgba(255, 120, 0, 255));

    private final Set<BlockPos> foundDebris = new HashSet<>();
    private final BlockScanner scanner = new BlockScanner();
    private int originX = Integer.MIN_VALUE, originY, originZ;

    public AncientDebrisFinder() {
        super("AncientDebrisFinder", "Locates Ancient Debris in Nether with 3D wireframe boxes (Netherite Finder)", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(scanSpeed);
        addSetting(recentreDistance);
        addSetting(tracers);
        addSetting(showDistance);
        addSetting(outline);
        addSetting(color);
    }

    @Override
    public void onDisable() {
        foundDebris.clear();
        scanner.reset();
        originX = Integer.MIN_VALUE;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.getValue().intValue();

        // Purge broken debris
        foundDebris.removeIf(p -> {
            if (mc.world.isChunkLoaded(p)) {
                BlockState bs = mc.world.getBlockState(p);
                return !Registries.BLOCK.getId(bs.getBlock()).getPath().equals("ancient_debris");
            }
            return false;
        });

        if (originX == Integer.MIN_VALUE
                || Math.abs(playerPos.getX() - originX) > recentreDistance.getValue()
                || Math.abs(playerPos.getY() - originY) > recentreDistance.getValue()
                || Math.abs(playerPos.getZ() - originZ) > recentreDistance.getValue()) {
            originX = playerPos.getX();
            originY = playerPos.getY();
            originZ = playerPos.getZ();
            scanner.resetCylinder();
            prune(playerPos, r);
        }

        BlockPos.Mutable scratch = new BlockPos.Mutable();

        try {
            scanner.scanCylinder(mc.world, originX, originY, originZ, r,
                    verticalRange.getValue().intValue(), scanSpeed.getValue().intValue(), scratch,
                    state -> Registries.BLOCK.getId(state.getBlock()).getPath().equals("ancient_debris"),
                    (pos, state) -> {
                        if (foundDebris.size() < 200) {
                            foundDebris.add(pos.toImmutable());
                        }
                    });
        } catch (Throwable ignored) {}
    }

    private void prune(BlockPos center, int r) {
        int r2 = r * r;
        int vr = verticalRange.getValue().intValue();
        foundDebris.removeIf(p -> {
            int dx = p.getX() - center.getX();
            int dz = p.getZ() - center.getZ();
            return dx * dx + dz * dz > r2 || Math.abs(p.getY() - center.getY()) > vr + 2;
        });
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || foundDebris.isEmpty()) return;

        int col = color.getValue();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        for (BlockPos p : foundDebris) {
            int[] bounds = RenderUtils.projectBlockBounds(p, tickDelta);
            if (bounds == null) continue;

            if (outline.isEnabled()) {
                RenderUtils.drawBlockOutline(context, p, col, tickDelta, 2);
            }

            int[] sc = ProjectionUtil.project(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
            if (sc == null) continue;

            if (tracers.isEnabled()) {
                RenderUtils.drawLine(context, sw / 2, sh / 2, sc[0], sc[1], col, 1.0f);
            }

            if (showDistance.isEnabled() && mc.textRenderer != null) {
                int dist = (int) Math.sqrt(p.getSquaredDistance(mc.player.getBlockPos()));
                String tag = String.format("§6[Debris] §f%dm", dist);
                RenderUtils.drawText(context, mc.textRenderer, tag, sc[0] + 5, sc[1] - 4, 0xFFFFFFFF, true);
            }
        }
    }
}
