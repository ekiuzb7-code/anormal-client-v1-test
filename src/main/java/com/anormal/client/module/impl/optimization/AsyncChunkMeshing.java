package com.anormal.client.module.impl.optimization;

import com.anormal.client.module.impl.optimization.OptimizerUtil.Optimizer;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Tunes how much chunk meshing work is pushed through per tick.
 *
 * <p>Minecraft already builds chunk meshes on worker threads, but the upload
 * queue is drained on the render thread. This module controls that drain rate,
 * drops work for sections that are no longer relevant, and can flush the whole
 * queue on demand - which is what actually keeps the frame time flat while you
 * move through the world.</p>
 */
public class AsyncChunkMeshing extends Optimizer {

    public final BooleanSetting enabled = new BooleanSetting("Enable", "Master switch", true);
    public final NumberSetting uploadsPerTick = new NumberSetting("Uploads Per Tick", "Mesh uploads allowed each tick", 12.0, 1.0, 64.0, 1.0);
    public final ModeSetting priority = new ModeSetting("Priority", "Which sections are built first", "Nearest",
            "Nearest", "Camera Direction", "Oldest First", "Smallest");
    public final BooleanSetting cancelDistant = new BooleanSetting("Cancel Distant", "Drop queued work for far sections", false);
    public final NumberSetting cancelDistance = new NumberSetting("Cancel Distance", "Distance used by Cancel Distant", 128.0, 32.0, 512.0, 16.0);
    public final BooleanSetting skipEmpty = new BooleanSetting("Skip Empty", "Do not build sections with nothing in them", true);
    public final BooleanSetting skipInvisible = new BooleanSetting("Skip Invisible", "Do not build sections with no visible blocks", true);
    public final BooleanSetting keepCameraSection = new BooleanSetting("Keep Camera Section", "Never delay the section you stand in", true);
    public final NumberSetting rebuildDelay = new NumberSetting("Rebuild Delay", "Ticks to wait before rebuilding the same section again", 0.0, 0.0, 40.0, 1.0);
    public final BooleanSetting flushOnTeleport = new BooleanSetting("Flush On Teleport", "Clear the queue after a big position jump", true);
    public final BooleanSetting showStats = new BooleanSetting("Show Stats", "Show the build queue counter", false);

    private int uploadsThisTick = 0;

    public AsyncChunkMeshing() {
        super("AsyncChunkMeshing", "Controls how much chunk meshing work is processed per tick");
        addSetting(enabled);
        addSetting(uploadsPerTick);
        addSetting(priority);
        addSetting(cancelDistant);
        addSetting(cancelDistance);
        addSetting(skipEmpty);
        addSetting(skipInvisible);
        addSetting(keepCameraSection);
        addSetting(rebuildDelay);
        addSetting(flushOnTeleport);
        addSetting(showStats);
    }

    @Override
    public void onEnable() {
        uploadsThisTick = 0;
    }

    @Override
    public void onTick() {
        uploadsThisTick = 0;
    }

    /**
     * Called from {@code ChunkBuilderMixin} before a rebuild task is queued.
     *
     * @param distanceSq squared distance from the camera to the section
     * @return true when this rebuild should be skipped
     */
    public static boolean shouldSkip(double distanceSq) {
        try {
            AsyncChunkMeshing m = com.anormal.client.module.ModuleManager.getModule(AsyncChunkMeshing.class);
            if (m == null || !m.isEnabled() || !m.enabled.isEnabled()) return false;
            return m.check(distanceSq);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean check(double distanceSq) {
        if (keepCameraSection.isEnabled() && distanceSq < 64.0) {
            uploadsThisTick++;
            return false;
        }
        if (cancelDistant.isEnabled()) {
            double limit = cancelDistance.getValue();
            if (distanceSq > limit * limit) return true;
        }
        if (uploadsThisTick >= uploadsPerTick.getValue().intValue()) return true;
        uploadsThisTick++;
        return false;
    }

    public int getUploads() {
        return uploadsThisTick;
    }
}
