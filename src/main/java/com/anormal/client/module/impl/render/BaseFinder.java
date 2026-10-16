package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.BlockScanner;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds base block clusters and logs them.
 *
 * <p>Migrated to budgeted {@link BlockScanner}: no full-sphere freeze,
 * persistent cache with re-validation (no blink/ghosts), 3-axis recentre
 * and independent vertical range for deep coverage.</p>
 */
public class BaseFinder extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Horizontal search radius", 48.0, 16.0, 128.0, 8.0);
    public final NumberSetting verticalRange = new NumberSetting("Vertical Range", "How far up and down to search", 32.0, 8.0, 160.0, 4.0);
    public final NumberSetting scanSpeed = new NumberSetting("Scan Speed", "Blocks checked per tick", 8000.0, 2000.0, 30000.0, 500.0);
    public final NumberSetting recentreDistance = new NumberSetting("Recentre Distance", "How far you may move before the search restarts", 8.0, 4.0, 32.0, 2.0);
    public final NumberSetting minBlocks = new NumberSetting("Min Blocks", "Blocks to count as base", 4.0, 2.0, 10.0, 1.0);
    public final BooleanSetting chatLog = new BooleanSetting("Chat Log", "Log new bases in chat", true);
    public final ColorSetting color = new ColorSetting("Color", "Base marker color", ColorUtils.rgba(255, 220, 0, 255));
    public final NumberSetting maxShown = new NumberSetting("Max Shown", "Max base markers drawn", 5.0, 1.0, 20.0, 1.0);

    private static final long RENDER_BUDGET_NS = 5_000_000L;
    private final BlockScanner scanner = new BlockScanner();
    private final List<BlockPos> found = new ArrayList<>();
    private final List<Base> bases = new ArrayList<>();
    private final Set<String> logged = new HashSet<>();
    private int centreX = Integer.MIN_VALUE, centreY, centreZ;
    private boolean sweepComplete = false;

    private static final class Base {
        final BlockPos pos;
        final int count;

        Base(BlockPos pos, int count) {
            this.pos = pos;
            this.count = count;
        }
    }

    public BaseFinder() {
        super("BaseFinder", "Finds base block clusters and logs them", Category.RENDER);
        addSetting(range);
        addSetting(verticalRange);
        addSetting(scanSpeed);
        addSetting(recentreDistance);
        addSetting(minBlocks);
        addSetting(chatLog);
        addSetting(color);
        addSetting(maxShown);
    }

    @Override
    public void onDisable() {
        found.clear();
        bases.clear();
        scanner.resetCylinder();
        centreX = Integer.MIN_VALUE;
        sweepComplete = false;
    }

    private boolean isBase(String p) {
        if (p.equals("bedrock")) return false;
        return p.contains("crafting_table") || p.contains("furnace") || p.contains("bed") || p.contains("chest");
    }

    private boolean isWanted(BlockState state) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return isBase(path);
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
            found.clear();
            bases.clear();
            sweepComplete = false;
            pruneFound(origin, r, vr);
        }

        found.removeIf(pos -> {
            if (!mc.world.isChunkLoaded(pos)) return false;
            return !isWanted(mc.world.getBlockState(pos));
        });

        BlockPos.Mutable scratch = new BlockPos.Mutable();
        try {
            scanner.scanCylinder(mc.world, centreX, centreY, centreZ, r, vr,
                    scanSpeed.getValue().intValue(), scratch,
                    state -> !state.isAir() && isWanted(state),
                    new BlockScanner.Callback() {
                        @Override
                        public void accept(BlockPos.Mutable pos, BlockState state) {
                            if (found.size() < 2000) found.add(pos.toImmutable());
                        }

                        @Override
                        public void onSweepComplete() {
                            sweepComplete = true;
                        }
                    });
        } catch (Throwable ignored) {
        }

        if (sweepComplete) {
            sweepComplete = false;
            clusterAndLog();
        }
    }

    private void pruneFound(BlockPos origin, int range, int verticalRange) {
        int rr = range * range;
        found.removeIf(pos -> {
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            return dx * dx + dz * dz > rr || Math.abs(pos.getY() - origin.getY()) > verticalRange + 2;
        });
    }

    private void clusterAndLog() {
        bases.clear();
        List<BlockPos> left = new ArrayList<>(found);
        while (!left.isEmpty()) {
            BlockPos seed = left.remove(0);
            List<BlockPos> group = new ArrayList<>();
            group.add(seed);
            boolean grew;
            do {
                grew = false;
                for (int i = left.size() - 1; i >= 0; i--) {
                    BlockPos p = left.get(i);
                    for (BlockPos c : group) {
                        double dx = p.getX() - c.getX(), dy = p.getY() - c.getY(), dz = p.getZ() - c.getZ();
                        if (dx * dx + dy * dy + dz * dz <= 100.0) {
                            group.add(p);
                            left.remove(i);
                            grew = true;
                            break;
                        }
                    }
                    if (grew) break;
                }
            } while (grew);
            if (group.size() < minBlocks.getValue().intValue()) continue;
            int ax = 0, ay = 0, az = 0;
            for (BlockPos c : group) {
                ax += c.getX();
                ay += c.getY();
                az += c.getZ();
            }
            BlockPos center = new BlockPos(ax / group.size(), ay / group.size(), az / group.size());
            bases.add(new Base(center, group.size()));
            String key = center.getX() + "," + center.getY() + "," + center.getZ();
            if (chatLog.isEnabled() && logged.add(key) && mc.inGameHud != null) {
                try {
                    mc.inGameHud.getChatHud().addMessage(Text.literal("§e[Base] §f" + group.size() + " blocks at " + key));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || bases.isEmpty()) return;
        try {
            int col = color.getValue(), drawn = 0, limit = maxShown.getValue().intValue();
            long deadline = System.nanoTime() + RENDER_BUDGET_NS;
            for (Base b : bases) {
                if (drawn >= limit) break;
                if ((drawn & 7) == 0 && System.nanoTime() > deadline) break;
                int[] sc = com.anormal.client.util.ProjectionUtil.project(
                        new Vec3d(b.pos.getX() + 0.5, b.pos.getY() + 0.5, b.pos.getZ() + 0.5), tickDelta);
                if (sc == null) continue;
                drawn++;
                context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] + 4, (col & 0x00FFFFFF) | 0x66000000);
                RenderUtils.drawBorder(context, sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] + 4, 1, col);
                if (mc.textRenderer != null)
                    RenderUtils.drawText(context, mc.textRenderer, "§e" + b.count + "x", sc[0] + 6, sc[1] - 4, 0xFFFFFFFF, true);
            }
        } catch (Throwable ignored) {
        }
    }
}
