package com.anormal.client.module.impl.optimization;

import com.anormal.client.module.impl.optimization.OptimizerUtil.Optimizer;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Spreads chunk rebuilding out over time.
 *
 * <p>When you fly or turn quickly the game can queue hundreds of chunk rebuilds
 * in one frame, which shows up as a stutter even though the average FPS looks
 * fine. This module caps how many rebuilds may be scheduled per tick, so the
 * work is smoothed out instead of arriving in one burst.</p>
 */
public class ChunkUpdateLimiter extends Optimizer {

    public final BooleanSetting enabled = new BooleanSetting("Enable", "Master switch", true);
    public final NumberSetting maxPerTick = new NumberSetting("Max Per Tick", "Chunk rebuilds allowed each tick", 8.0, 1.0, 64.0, 1.0);
    public final BooleanSetting scaleWithFps = new BooleanSetting("Scale With FPS", "Allow more rebuilds when FPS is high", true);
    public final NumberSetting targetFps = new NumberSetting("Target FPS", "FPS used by Scale With FPS", 60.0, 20.0, 240.0, 5.0);
    public final BooleanSetting skipFar = new BooleanSetting("Skip Distant", "Refuse rebuilds further away than the limit", false);
    public final NumberSetting farDistance = new NumberSetting("Far Distance", "Distance used by Skip Distant", 96.0, 16.0, 256.0, 8.0);
    public final BooleanSetting priorityNear = new BooleanSetting("Priority Near", "Always allow rebuilds close to the camera", true);
    public final NumberSetting nearDistance = new NumberSetting("Near Distance", "Distance that is always allowed", 24.0, 4.0, 96.0, 4.0);
    public final BooleanSetting pauseWhenIdle = new BooleanSetting("Pause When Idle", "Stop rebuilding while you stand still and look straight", false);
    public final BooleanSetting showStats = new BooleanSetting("Show Stats", "Show the rebuild counter", false);

    private int scheduledThisTick = 0;
    private int skippedThisTick = 0;

    public ChunkUpdateLimiter() {
        super("ChunkUpdateLimiter", "Smooths chunk rebuilding to remove stutter spikes");
        addSetting(enabled);
        addSetting(maxPerTick);
        addSetting(scaleWithFps);
        addSetting(targetFps);
        addSetting(skipFar);
        addSetting(farDistance);
        addSetting(priorityNear);
        addSetting(nearDistance);
        addSetting(pauseWhenIdle);
        addSetting(showStats);
    }

    @Override
    public void onTick() {
        scheduledThisTick = 0;
        skippedThisTick = 0;
    }

    private int budget() {
        int base = maxPerTick.getValue().intValue();
        if (!scaleWithFps.isEnabled()) return base;
        try {
            int fps = mc.getCurrentFps();
            double target = targetFps.getValue();
            if (fps <= target) return base;
            return (int) Math.min(base * 4L, base * (fps / target));
        } catch (Throwable t) {
            return base;
        }
    }

    /**
     * Called from {@code WorldRendererMixin} before a chunk rebuild is queued.
     *
     * @param dx chunk-centre offset from the player on X
     * @param dz chunk-centre offset from the player on Z
     * @return true when the rebuild should be refused
     */
    public static boolean shouldBlock(int dx, int dz) {
        try {
            ChunkUpdateLimiter m = com.anormal.client.module.ModuleManager.getModule(ChunkUpdateLimiter.class);
            if (m == null || !m.isEnabled() || !m.enabled.isEnabled()) return false;
            return m.check(dx, dz);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean check(int dx, int dz) {
        double dist = Math.sqrt((double) dx * dx + (double) dz * dz);

        if (priorityNear.isEnabled() && dist <= nearDistance.getValue()) {
            scheduledThisTick++;
            return false;
        }
        if (skipFar.isEnabled() && dist > farDistance.getValue()) {
            skippedThisTick++;
            return true;
        }
        if (scheduledThisTick >= budget()) {
            skippedThisTick++;
            return true;
        }
        scheduledThisTick++;
        return false;
    }

    public int getScheduled() {
        return scheduledThisTick;
    }

    public int getSkipped() {
        return skippedThisTick;
    }
}
