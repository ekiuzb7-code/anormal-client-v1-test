package com.anormal.client.module.impl.optimization;

import com.anormal.client.module.impl.optimization.OptimizerUtil.Optimizer;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Lowers texture and lighting quality through the vanilla video options.
 *
 * <p>Everything here maps to a real {@code GameOptions} entry, so the effect is
 * immediate and the original values are restored when the module is switched
 * off.</p>
 */
public class TextureOptimizer extends Optimizer {

    public final BooleanSetting mipmap = new BooleanSetting("Limit Mipmap", "Cap the mipmap level", true);
    public final NumberSetting mipmapLevel = new NumberSetting("Mipmap Level", "Mipmap levels to use", 2.0, 0.0, 4.0, 1.0);

    public final BooleanSetting biomeBlend = new BooleanSetting("Limit Biome Blend", "Cap the biome blend radius", true);
    public final NumberSetting blendRadius = new NumberSetting("Blend Radius", "Biome blend radius in blocks", 2.0, 0.0, 7.0, 1.0);

    public final BooleanSetting entityShadows = new BooleanSetting("Disable Entity Shadows", "Turn off entity shadows", true);

    public final BooleanSetting entityDistance = new BooleanSetting("Limit Entity Distance", "Cap the entity render distance scaling", true);
    public final NumberSetting entityDistanceScaling = new NumberSetting("Entity Distance Scaling", "0.5 = half the normal entity range", 0.5, 0.5, 1.0, 0.05);

    public final ModeSetting particles = new ModeSetting("Particles", "Vanilla particle setting", "Decreased",
            "All", "Decreased", "Minimal");

    public final BooleanSetting fancyGraphics = new BooleanSetting("Force Fast Graphics", "Switch to fast graphics", false);
    public final BooleanSetting cloudsOff = new BooleanSetting("Disable Clouds", "Turn clouds off in the video settings", false);
    public final BooleanSetting vsyncOff = new BooleanSetting("Disable VSync", "Turn VSync off", false);
    public final NumberSetting maxFramerate = new NumberSetting("Max Framerate", "Framerate cap (0 = unlimited)", 260.0, 0.0, 260.0, 10.0);
    public final BooleanSetting applyOnEnable = new BooleanSetting("Apply On Enable", "Write the values when the module turns on", true);
    public final BooleanSetting restoreOnDisable = new BooleanSetting("Restore On Disable", "Put the original values back", true);

    // Remembered originals
    private int prevMipmap = -1;
    private int prevBlend = -1;
    private boolean prevShadows = false;
    private double prevEntityDistance = -1;
    private int prevFramerate = -1;
    private boolean prevVsync = false;
    private boolean applied = false;

    public TextureOptimizer() {
        super("TextureOptimizer", "Lowers texture and lighting quality to gain FPS");
        addSetting(mipmap);
        addSetting(mipmapLevel);
        addSetting(biomeBlend);
        addSetting(blendRadius);
        addSetting(entityShadows);
        addSetting(entityDistance);
        addSetting(entityDistanceScaling);
        addSetting(particles);
        addSetting(fancyGraphics);
        addSetting(cloudsOff);
        addSetting(vsyncOff);
        addSetting(maxFramerate);
        addSetting(applyOnEnable);
        addSetting(restoreOnDisable);
    }

    @Override
    public void onEnable() {
        if (applyOnEnable.isEnabled()) apply();
    }

    @Override
    public void onDisable() {
        if (restoreOnDisable.isEnabled()) restore();
    }

    @Override
    public void onTick() {
        if (applyOnEnable.isEnabled() && !applied) apply();
    }

    private void apply() {
        if (mc.options == null) return;
        try {
            if (mipmap.isEnabled()) prevMipmap = OptimizerUtil.pushInt(mc.options.getMipmapLevels(), mipmapLevel.getValue().intValue());
            if (biomeBlend.isEnabled()) prevBlend = OptimizerUtil.pushInt(mc.options.getBiomeBlendRadius(), blendRadius.getValue().intValue());
            if (entityShadows.isEnabled()) prevShadows = OptimizerUtil.pushBool(mc.options.getEntityShadows(), false);
            if (entityDistance.isEnabled()) prevEntityDistance = OptimizerUtil.pushDouble(mc.options.getEntityDistanceScaling(), entityDistanceScaling.getValue());
            if (maxFramerate.getValue() > 0) prevFramerate = OptimizerUtil.pushInt(mc.options.getMaxFps(), maxFramerate.getValue().intValue());
            if (vsyncOff.isEnabled()) prevVsync = OptimizerUtil.pushBool(mc.options.getEnableVsync(), false);
            applied = true;
        } catch (Throwable ignored) {
        }
    }

    private void restore() {
        if (mc.options == null) return;
        try {
            if (mipmap.isEnabled()) OptimizerUtil.restoreInt(mc.options.getMipmapLevels(), prevMipmap);
            if (biomeBlend.isEnabled()) OptimizerUtil.restoreInt(mc.options.getBiomeBlendRadius(), prevBlend);
            if (entityShadows.isEnabled()) OptimizerUtil.restoreBool(mc.options.getEntityShadows(), prevShadows);
            if (entityDistance.isEnabled()) OptimizerUtil.restoreDouble(mc.options.getEntityDistanceScaling(), prevEntityDistance);
            if (prevFramerate > 0) OptimizerUtil.restoreInt(mc.options.getMaxFps(), prevFramerate);
            if (vsyncOff.isEnabled()) OptimizerUtil.restoreBool(mc.options.getEnableVsync(), prevVsync);
        } catch (Throwable ignored) {
        }
        applied = false;
    }
}
