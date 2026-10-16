package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Removes fog, driven by {@code FogRendererMixin}.
 *
 * <p>Fog in 1.21.11 is six floats uploaded to a uniform buffer. The mixin hands
 * each of them to {@link #adjustFog(int, float)} so this module can rewrite them
 * individually. The six slots are, in the order the renderer passes them:</p>
 * <pre>
 *   0 environmentalStart   1 environmentalEnd
 *   2 renderDistanceStart  3 renderDistanceEnd
 *   4 skyEnd               5 cloudEnd
 * </pre>
 */
public class NoFog extends Module {

    /** Distances beyond this are effectively "no fog". */
    private static final float HUGE = 1.0E6f;

    public final BooleanSetting disableFog = new BooleanSetting("Disable Fog", "Master switch - removes all fog", true);
    public final BooleanSetting environmental = new BooleanSetting("Environmental Fog", "Keep terrain / water / lava / blindness fog", false);
    public final NumberSetting fogStart = new NumberSetting("Fog Start", "Custom environmental fog start distance", 0.0, 0.0, 256.0, 1.0);
    public final NumberSetting fogEnd = new NumberSetting("Fog End", "Custom environmental fog end distance", 96.0, 4.0, 512.0, 2.0);
    public final BooleanSetting renderDistance = new BooleanSetting("Render Distance Fog", "Keep the fog that hides the edge of the render distance", false);
    public final BooleanSetting sky = new BooleanSetting("Sky Fog", "Keep fog on the sky", false);
    public final BooleanSetting clouds = new BooleanSetting("Cloud Fog", "Keep fog on clouds", false);
    public final NumberSetting thickness = new NumberSetting("Fog Thickness", "Multiplier for the fog end distance", 1.0, 0.1, 3.0, 0.05);

    public final BooleanSetting water = new BooleanSetting("Clear Underwater", "Also remove fog while your head is in water", false);
    public final BooleanSetting lava = new BooleanSetting("Clear In Lava", "Also remove fog while you are in lava", false);
    public final BooleanSetting powderSnow = new BooleanSetting("Clear In Powder Snow", "Also remove fog inside powder snow", false);

    public final BooleanSetting undergroundOnly = new BooleanSetting("Underground Only", "Only apply below the Y level below", false);
    public final NumberSetting undergroundY = new NumberSetting("Underground Y", "Y level used by Underground Only", 63.0, -64.0, 320.0, 1.0);

    public final BooleanSetting smooth = new BooleanSetting("Smooth", "Fade the fog change instead of snapping", true);
    public final NumberSetting smoothSpeed = new NumberSetting("Smooth Speed", "How fast the fade is", 0.15, 0.02, 1.0, 0.01);

    /** 0 = vanilla fog, 1 = fully removed. Animated when "Smooth" is on. */
    private float factor = 0f;

    public NoFog() {
        super("NoFog", "Removes fog with fine-grained per-category control", Category.WORLD);
        addSetting(disableFog);
        addSetting(environmental);
        addSetting(fogStart);
        addSetting(fogEnd);
        addSetting(renderDistance);
        addSetting(sky);
        addSetting(clouds);
        addSetting(thickness);
        addSetting(water);
        addSetting(lava);
        addSetting(powderSnow);
        addSetting(undergroundOnly);
        addSetting(undergroundY);
        addSetting(smooth);
        addSetting(smoothSpeed);
    }

    @Override
    public void onEnable() {
        factor = smooth.isEnabled() ? 0f : 1f;
    }

    @Override
    public void onDisable() {
        factor = 0f;
    }

    @Override
    public void onTick() {
        float target = disableFog.isEnabled() ? 1f : 0f;
        if (!smooth.isEnabled()) {
            factor = target;
            return;
        }
        float speed = smoothSpeed.getValue().floatValue();
        if (factor < target) factor = Math.min(target, factor + speed);
        else if (factor > target) factor = Math.max(target, factor - speed);
    }

    /**
     * Called from {@code FogRendererMixin} for each fog distance.
     *
     * @param slot     0..5, see the class javadoc
     * @param original the vanilla value
     * @return the value to actually upload
     */
    public float adjustFog(int slot, float original) {
        if (!isEnabled() || !disableFog.isEnabled()) return original;
        if (factor <= 0.001f) return original;

        // Optional "only underground" restriction.
        if (undergroundOnly.isEnabled() && mc.player != null
                && mc.player.getY() > undergroundY.getValue()) {
            return original;
        }

        // Forced clears for submersion, independent of the per-category toggles.
        if (mc.player != null) {
            if (water.isEnabled() && mc.player.isSubmergedInWater()) return HUGE;
            if (lava.isEnabled() && mc.player.isInLava()) return HUGE;
            if (powderSnow.isEnabled() && mc.world != null
                    && mc.world.getBlockState(mc.player.getBlockPos()).isOf(net.minecraft.block.Blocks.POWDER_SNOW)) {
                return HUGE;
            }
        }

        boolean keepEnvironmental = environmental.isEnabled();
        float customStart = fogStart.getValue().floatValue();
        float customEnd = fogEnd.getValue().floatValue() * thickness.getValue().floatValue();

        float target;
        switch (slot) {
            case 0 -> target = keepEnvironmental ? customStart : HUGE;
            case 1 -> target = keepEnvironmental ? customEnd : HUGE;
            case 2, 3 -> target = renderDistance.isEnabled() ? original : HUGE;
            case 4 -> target = sky.isEnabled() ? original : HUGE;
            case 5 -> target = clouds.isEnabled() ? original : HUGE;
            default -> {
                return original;
            }
        }

        return original + (target - original) * factor;
    }
}
