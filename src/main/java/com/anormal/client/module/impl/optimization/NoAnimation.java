package com.anormal.client.module.impl.optimization;

import com.anormal.client.module.impl.optimization.OptimizerUtil.Optimizer;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Freezes animated textures and cosmetic animations.
 *
 * <p>Water, lava, fire, portals, magma and command blocks animate their
 * textures every tick, which costs a texture upload per frame. The hook lives
 * in {@code SpriteMixin}, which answers {@code isAnimated()} so the animation is
 * never even scheduled.</p>
 *
 * <p>"Only When Lagging" makes the whole module conditional on your frame rate,
 * so you keep the pretty animations when you have headroom and drop them only
 * when they would actually hurt.</p>
 */
public class NoAnimation extends Optimizer {

    public final BooleanSetting freezeTextures = new BooleanSetting("Freeze Textures", "Stop every animated texture (water, lava, fire, portals)", true);
    public final BooleanSetting onlyWhenLagging = new BooleanSetting("Only When Lagging", "Only freeze while FPS is below the target", false);
    public final NumberSetting targetFps = new NumberSetting("Target FPS", "FPS used by Only When Lagging", 60.0, 20.0, 240.0, 5.0);

    public final BooleanSetting blockBreaking = new BooleanSetting("Block Breaking Animation", "Hide the crack overlay while mining", false);
    public final BooleanSetting itemSwing = new BooleanSetting("Item Swing", "Hide the held item swing animation", false);
    public final BooleanSetting totemAnimation = new BooleanSetting("Totem Animation", "Skip the totem pop animation", false);
    public final BooleanSetting elytra = new BooleanSetting("Elytra Animation", "Freeze elytra wing movement", false);
    public final BooleanSetting weather = new BooleanSetting("Weather Animation", "Freeze rain and snow animation", false);
    public final BooleanSetting portalOverlay = new BooleanSetting("Portal Overlay", "Hide the nether portal screen animation", false);
    public final BooleanSetting nausea = new BooleanSetting("Nausea Animation", "Freeze the warped vision effect", false);
    public final BooleanSetting fireOverlay = new BooleanSetting("Fire Overlay Animation", "Freeze the fire screen overlay", false);

    public final BooleanSetting entityAnimations = new BooleanSetting("Entity Animations", "Stop animating entities beyond a distance", false);
    public final NumberSetting animationDistance = new NumberSetting("Animation Distance", "Entities further than this stop animating", 24.0, 4.0, 96.0, 4.0);
    public final BooleanSetting showHud = new BooleanSetting("Show HUD", "Show whether animations are currently frozen", false);

    public NoAnimation() {
        super("NoAnimation", "Freezes animated textures and cosmetic animations");
        addSetting(freezeTextures);
        addSetting(onlyWhenLagging);
        addSetting(targetFps);
        addSetting(blockBreaking);
        addSetting(itemSwing);
        addSetting(totemAnimation);
        addSetting(elytra);
        addSetting(weather);
        addSetting(portalOverlay);
        addSetting(nausea);
        addSetting(fireOverlay);
        addSetting(entityAnimations);
        addSetting(animationDistance);
        addSetting(showHud);
    }

    private static NoAnimation get() {
        NoAnimation m = com.anormal.client.module.ModuleManager.getModule(NoAnimation.class);
        return (m != null && m.isEnabled()) ? m : null;
    }

    /** True when textures should be frozen right now. */
    public boolean freezing() {
        if (!freezeTextures.isEnabled()) return false;
        if (!onlyWhenLagging.isEnabled()) return true;
        try {
            return mc.getCurrentFps() < targetFps.getValue();
        } catch (Throwable t) {
            return true;
        }
    }

    /** Called from {@code SpriteMixin.isAnimated()}. */
    public static boolean shouldFreeze() {
        NoAnimation m = get();
        return m != null && m.freezing();
    }

    public static boolean hideBreakingAnimation() {
        NoAnimation m = get();
        return m != null && m.blockBreaking.isEnabled();
    }

    public static boolean hidePortalOverlay() {
        NoAnimation m = get();
        return m != null && m.portalOverlay.isEnabled();
    }

    public static boolean hideNausea() {
        NoAnimation m = get();
        return m != null && m.nausea.isEnabled();
    }

    public static boolean hideFireOverlay() {
        NoAnimation m = get();
        return m != null && m.fireOverlay.isEnabled();
    }

    public static boolean hideWeather() {
        NoAnimation m = get();
        return m != null && m.weather.isEnabled();
    }
}
