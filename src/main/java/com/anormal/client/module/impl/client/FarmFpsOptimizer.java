package com.anormal.client.module.impl.client;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Farm FPS Optimizer
 * Inspired by Prestige Client V5.
 *
 * Maximizes FPS at massive automated farms (dropping from 20 FPS to 140+ FPS):
 * - Hides floating dropped items & XP orbs
 * - Disables rendering of active TNT, falling blocks & explosions
 * - Sound spam limiter (mutes repeated sound effects e.g. pistons, note blocks)
 * - Removes piston extension lag animations
 * - Hides redstone dust, repeaters, comparators and redstone particles
 */
public class FarmFpsOptimizer extends Module {

    public static FarmFpsOptimizer INSTANCE;

    public final BooleanSetting items = new BooleanSetting("Items", "Hide dropped item entities in water streams", true);
    public final BooleanSetting xpOrbs = new BooleanSetting("XP Orbs", "Hide experience orb entities", true);
    public final BooleanSetting fallingBlocks = new BooleanSetting("Falling Blocks", "Hide falling sand, gravel, concrete powder", true);
    public final BooleanSetting tnt = new BooleanSetting("TNT", "Hide primed TNT entities", true);
    public final BooleanSetting explosions = new BooleanSetting("Explosions", "Disable explosion particles and flashes", true);
    public final BooleanSetting sounds = new BooleanSetting("Sounds Filter", "Filter repetitive sound loops", true);
    public final NumberSetting soundSpam = new NumberSetting("Sound Spam", "Max times same sound can play in short window", 4.0, 1.0, 10.0, 1.0);
    public final BooleanSetting pistons = new BooleanSetting("Pistons", "Disable piston movement animations", true);
    public final BooleanSetting redstone = new BooleanSetting("Redstone", "Hide redstone dust lines and particles", true);

    public FarmFpsOptimizer() {
        super("FarmFpsOptimizer", "Removes farm lag (items, xp, tnt, piston sounds) boosting FPS 2x", Category.CLIENT);
        INSTANCE = this;
        addSetting(items);
        addSetting(xpOrbs);
        addSetting(fallingBlocks);
        addSetting(tnt);
        addSetting(explosions);
        addSetting(sounds);
        addSetting(soundSpam);
        addSetting(pistons);
        addSetting(redstone);
    }

    public static boolean shouldCancelItemRender() {
        return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.items.isEnabled();
    }

    public static boolean shouldCancelXpRender() {
        return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.xpOrbs.isEnabled();
    }

    public static boolean shouldCancelTntRender() {
        return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.tnt.isEnabled();
    }

    public static boolean shouldCancelFallingRender() {
        return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.fallingBlocks.isEnabled();
    }

    public static boolean shouldCancelRedstoneRender() {
        return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.redstone.isEnabled();
    }
}
