package com.anormal.client.module.impl.optimization;

import com.anormal.client.module.impl.optimization.OptimizerUtil.Optimizer;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Caps how many particles may exist at once.
 *
 * <p>Particle spam (large farms, big explosions, dozens of campfires) is one of
 * the most common causes of frame drops. {@code ParticleManagerMixin} asks this
 * module before every spawn, so the cap is enforced before the particle is even
 * created.</p>
 */
public class ParticleLimit extends Optimizer {

    public final BooleanSetting enabled = new BooleanSetting("Limit Particles", "Enable the particle cap", true);
    public final NumberSetting maxParticles = new NumberSetting("Max Particles", "Hard cap on particles in the world", 2000.0, 100.0, 20000.0, 100.0);
    public final NumberSetting maxPerTick = new NumberSetting("Max Per Tick", "How many new particles may spawn each tick", 40.0, 1.0, 500.0, 1.0);
    public final NumberSetting distance = new NumberSetting("Max Distance", "Particles further away than this are skipped", 32.0, 4.0, 128.0, 4.0);
    public final BooleanSetting scaleWithFps = new BooleanSetting("Scale With FPS", "Lower the cap automatically when FPS drops", true);
    public final NumberSetting targetFps = new NumberSetting("Target FPS", "FPS used by Scale With FPS", 60.0, 20.0, 240.0, 5.0);
    public final BooleanSetting keepImportant = new BooleanSetting("Keep Important", "Never skip explosions, totems or boss particles", true);
    public final BooleanSetting keepNearby = new BooleanSetting("Keep Nearby", "Never skip particles within 8 blocks", true);
    public final BooleanSetting hideWhenBusy = new BooleanSetting("Hide When Busy", "Stop all new particles while the cap is full", true);
    public final BooleanSetting showStats = new BooleanSetting("Show Stats", "Show the particle counter on the HUD", false);

    private int spawnedThisTick = 0;

    public ParticleLimit() {
        super("ParticleLimit", "Caps particle count to protect your frame rate");
        addSetting(enabled);
        addSetting(maxParticles);
        addSetting(maxPerTick);
        addSetting(distance);
        addSetting(scaleWithFps);
        addSetting(targetFps);
        addSetting(keepImportant);
        addSetting(keepNearby);
        addSetting(hideWhenBusy);
        addSetting(showStats);
    }

    @Override
    public void onTick() {
        spawnedThisTick = 0;
    }

    /** Current per-tick budget, reduced when the frame rate is low. */
    private int tickBudget() {
        int base = maxPerTick.getValue().intValue();
        if (!scaleWithFps.isEnabled()) return base;
        try {
            int fps = mc.getCurrentFps();
            double target = targetFps.getValue();
            if (fps >= target) return base;
            double factor = Math.max(0.15, fps / target);
            return Math.max(1, (int) (base * factor));
        } catch (Throwable t) {
            return base;
        }
    }

    /** @return true when this particle should be skipped. */
    public static boolean shouldSkip(net.minecraft.client.particle.Particle particle) {
        try {
            ParticleLimit m = com.anormal.client.module.ModuleManager.getModule(ParticleLimit.class);
            if (m == null || !m.isEnabled() || !m.enabled.isEnabled()) return false;
            return m.check(particle);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean check(net.minecraft.client.particle.Particle particle) {
        if (spawnedThisTick >= tickBudget()) return true;

        if (particle != null && mc.player != null) {
            var c = particle.getBoundingBox().getCenter();
            double d = mc.player.squaredDistanceTo(c.x, c.y, c.z);
            double max = distance.getValue();
            if (keepNearby.isEnabled() && d < 64.0) {
                // still counts against the budget, just never distance-culled
            } else if (d > max * max) {
                return true;
            }

            if (keepImportant.isEnabled()) {
                String name = particle.getClass().getName().toLowerCase();
                if (name.contains("explosion") || name.contains("totem") || name.contains("dragon")) {
                    spawnedThisTick++;
                    return false;
                }
            }
        }

        spawnedThisTick++;
        return false;
    }
}
