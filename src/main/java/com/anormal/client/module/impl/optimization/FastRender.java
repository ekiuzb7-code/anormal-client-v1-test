package com.anormal.client.module.impl.optimization;

import com.anormal.client.module.impl.optimization.OptimizerUtil.Optimizer;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * The "just make it faster" switch.
 *
 * <p>Applies a set of safe, high-impact render reductions through the vanilla
 * video options plus a couple of render hooks, and remembers the previous
 * values so everything can be put back when the module is turned off.</p>
 */
public class FastRender extends Optimizer {

    public final BooleanSetting enabled = new BooleanSetting("Enable", "Master switch", true);

    public final BooleanSetting graphics = new BooleanSetting("Force Fast Graphics", "Switch to the fast graphics preset", true);
    public final BooleanSetting clouds = new BooleanSetting("Disable Clouds", "Turn the cloud layer off", true);
    public final BooleanSetting sky = new BooleanSetting("Disable Sky", "Skip the sky dome", false);
    public final BooleanSetting weather = new BooleanSetting("Disable Weather Rendering", "Skip rain and snow", true);

    public final BooleanSetting entityShadows = new BooleanSetting("Disable Entity Shadows", "Turn off entity shadows", true);
    public final BooleanSetting entityDistance = new BooleanSetting("Limit Entity Distance", "Cap the entity distance scaling", true);
    public final NumberSetting entityDistanceScaling = new NumberSetting("Entity Distance Scaling", "Value used above", 0.6, 0.5, 1.0, 0.05);

    public final BooleanSetting particles = new BooleanSetting("Limit Particles", "Lower the vanilla particle setting", true);
    public final ModeSetting particleMode = new ModeSetting("Particle Mode", "Value used above", "Decreased",
            "All", "Decreased", "Minimal");

    public final BooleanSetting biomeBlend = new BooleanSetting("Limit Biome Blend", "Reduce the biome blend radius", true);
    public final NumberSetting blendRadius = new NumberSetting("Blend Radius", "Value used above", 1.0, 0.0, 7.0, 1.0);

    public final BooleanSetting mipmap = new BooleanSetting("Limit Mipmap", "Reduce mipmap levels", true);
    public final NumberSetting mipmapLevel = new NumberSetting("Mipmap Level", "Value used above", 2.0, 0.0, 4.0, 1.0);

    public final BooleanSetting viewDistance = new BooleanSetting("Clamp View Distance", "Do not let the view distance exceed the cap", false);
    public final NumberSetting viewDistanceCap = new NumberSetting("View Distance Cap", "Maximum render distance in chunks", 12.0, 2.0, 32.0, 1.0);

    public final BooleanSetting smoothLighting = new BooleanSetting("Disable Smooth Lighting", "Turn smooth lighting off", false);
    public final BooleanSetting fancyLeaves = new BooleanSetting("Fast Leaves", "Turn fancy leaves off", false);
    public final BooleanSetting restoreOnDisable = new BooleanSetting("Restore On Disable", "Put every value back", true);

    private int prevBlend = -1;
    private int prevMipmap = -1;
    private boolean prevShadows = false;
    private double prevEntityDistance = -1;
    private int prevView = -1;

    public FastRender() {
        super("FastRender", "One switch that applies the highest-impact render reductions");
        addSetting(enabled);
        addSetting(graphics);
        addSetting(clouds);
        addSetting(sky);
        addSetting(weather);
        addSetting(entityShadows);
        addSetting(entityDistance);
        addSetting(entityDistanceScaling);
        addSetting(particles);
        addSetting(particleMode);
        addSetting(biomeBlend);
        addSetting(blendRadius);
        addSetting(mipmap);
        addSetting(mipmapLevel);
        addSetting(viewDistance);
        addSetting(viewDistanceCap);
        addSetting(smoothLighting);
        addSetting(fancyLeaves);
        addSetting(restoreOnDisable);
    }

    @Override
    public void onEnable() {
        apply();
    }

    @Override
    public void onDisable() {
        if (restoreOnDisable.isEnabled()) restore();
    }

    @Override
    public void onTick() {
        if (viewDistance.isEnabled()) {
            try {
                int cap = viewDistanceCap.getValue().intValue();
                if (mc.options.getViewDistance().getValue() > cap) {
                    mc.options.getViewDistance().setValue(cap);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void apply() {
        if (mc.options == null) return;
        try {
            if (entityShadows.isEnabled()) prevShadows = OptimizerUtil.pushBool(mc.options.getEntityShadows(), false);
            if (entityDistance.isEnabled()) prevEntityDistance = OptimizerUtil.pushDouble(mc.options.getEntityDistanceScaling(), entityDistanceScaling.getValue());
            if (biomeBlend.isEnabled()) prevBlend = OptimizerUtil.pushInt(mc.options.getBiomeBlendRadius(), blendRadius.getValue().intValue());
            if (mipmap.isEnabled()) prevMipmap = OptimizerUtil.pushInt(mc.options.getMipmapLevels(), mipmapLevel.getValue().intValue());
            if (viewDistance.isEnabled()) {
                prevView = mc.options.getViewDistance().getValue();
                int cap = viewDistanceCap.getValue().intValue();
                if (prevView > cap) mc.options.getViewDistance().setValue(cap);
            }
        } catch (Throwable ignored) {
        }
    }

    private void restore() {
        if (mc.options == null) return;
        try {
            if (entityShadows.isEnabled()) OptimizerUtil.restoreBool(mc.options.getEntityShadows(), prevShadows);
            if (entityDistance.isEnabled()) OptimizerUtil.restoreDouble(mc.options.getEntityDistanceScaling(), prevEntityDistance);
            if (biomeBlend.isEnabled()) OptimizerUtil.restoreInt(mc.options.getBiomeBlendRadius(), prevBlend);
            if (mipmap.isEnabled()) OptimizerUtil.restoreInt(mc.options.getMipmapLevels(), prevMipmap);
            if (viewDistance.isEnabled()) OptimizerUtil.restoreInt(mc.options.getViewDistance(), prevView);
        } catch (Throwable ignored) {
        }
    }

    // ---- Static queries used by the mixins -------------------------------

    private static FastRender get() {
        FastRender m = com.anormal.client.module.ModuleManager.getModule(FastRender.class);
        return (m != null && m.isEnabled() && m.enabled.isEnabled()) ? m : null;
    }

    public static boolean hideClouds() {
        FastRender m = get();
        return m != null && m.clouds.isEnabled();
    }

    public static boolean hideSky() {
        FastRender m = get();
        return m != null && m.sky.isEnabled();
    }

    public static boolean hideWeather() {
        FastRender m = get();
        return m != null && m.weather.isEnabled();
    }
}
