package com.anormal.client.module.impl.optimization;

import com.anormal.client.module.impl.optimization.OptimizerUtil.Optimizer;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.BlockPos;

/**
 * Skips block entities (chests, signs, banners, beds, shulkers...) that are too
 * far away or too small on screen to matter.
 *
 * <p>Block entities bypass the chunk mesh and are re-submitted every frame, so
 * a base full of chests and signs is disproportionately expensive. The hook
 * lives in {@code BlockEntityRenderManagerMixin}.</p>
 */
public class BlockEntityOptimizer extends Optimizer {

    public final BooleanSetting enabled = new BooleanSetting("Enable", "Master switch", true);
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", "Skip block entities beyond this", 48.0, 8.0, 256.0, 4.0);
    public final BooleanSetting cullChests = new BooleanSetting("Cull Chests", "Apply the limit to chests and barrels", true);
    public final BooleanSetting cullSigns = new BooleanSetting("Cull Signs", "Apply the limit to signs and hanging signs", true);
    public final BooleanSetting cullBanners = new BooleanSetting("Cull Banners", "Apply the limit to banners", true);
    public final BooleanSetting cullBeds = new BooleanSetting("Cull Beds", "Apply the limit to beds", false);
    public final BooleanSetting cullShulkers = new BooleanSetting("Cull Shulkers", "Apply the limit to shulker boxes", true);
    public final BooleanSetting cullSkulls = new BooleanSetting("Cull Skulls", "Apply the limit to heads and skulls", false);
    public final BooleanSetting cullDecorations = new BooleanSetting("Cull Decorations", "Apply the limit to bells, lecterns, campfires and the rest", false);
    public final BooleanSetting cullBeacons = new BooleanSetting("Cull Beacons", "Apply the limit to beacon beams", false);
    public final BooleanSetting skipWhenBusy = new BooleanSetting("Skip When Busy", "Tighten the limit automatically when FPS is low", true);
    public final NumberSetting busyFactor = new NumberSetting("Busy Factor", "Distance multiplier used when FPS is low", 0.6, 0.2, 1.0, 0.05);
    public final NumberSetting targetFps = new NumberSetting("Target FPS", "FPS used by Skip When Busy", 60.0, 20.0, 240.0, 5.0);

    public BlockEntityOptimizer() {
        super("BlockEntityOptimizer", "Skips distant block entities like chests, signs and banners");
        addSetting(enabled);
        addSetting(maxDistance);
        addSetting(cullChests);
        addSetting(cullSigns);
        addSetting(cullBanners);
        addSetting(cullBeds);
        addSetting(cullShulkers);
        addSetting(cullSkulls);
        addSetting(cullDecorations);
        addSetting(cullBeacons);
        addSetting(skipWhenBusy);
        addSetting(busyFactor);
        addSetting(targetFps);
    }

    private double limit() {
        double base = maxDistance.getValue();
        if (skipWhenBusy.isEnabled()) {
            try {
                int fps = mc.getCurrentFps();
                if (fps < targetFps.getValue()) base *= busyFactor.getValue();
            } catch (Throwable ignored) {
            }
        }
        return base;
    }

    /** @return true when the block entity at {@code pos} should not be rendered. */
    public static boolean shouldCull(BlockPos pos, String typeName) {
        try {
            BlockEntityOptimizer m = com.anormal.client.module.ModuleManager.getModule(BlockEntityOptimizer.class);
            if (m == null || !m.isEnabled() || !m.enabled.isEnabled()) return false;
            return m.check(pos, typeName);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean check(BlockPos pos, String typeName) {
        if (pos == null || mc.player == null) return false;

        String n = typeName == null ? "" : typeName.toLowerCase();
        boolean covered =
                (cullChests.isEnabled() && (n.contains("chest") || n.contains("barrel")))
                        || (cullSigns.isEnabled() && n.contains("sign"))
                        || (cullBanners.isEnabled() && n.contains("banner"))
                        || (cullBeds.isEnabled() && n.contains("bed"))
                        || (cullShulkers.isEnabled() && n.contains("shulker"))
                        || (cullSkulls.isEnabled() && (n.contains("skull") || n.contains("head")))
                        || (cullBeacons.isEnabled() && n.contains("beacon"));

        // Nothing specific matched - only cull when decorations are enabled too.
        if (!covered && !cullDecorations.isEnabled()) return false;

        double limit = limit();
        return pos.getSquaredDistance(mc.player.getBlockPos()) > limit * limit;
    }
}
