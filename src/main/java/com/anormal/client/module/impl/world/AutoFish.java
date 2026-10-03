package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.item.FishingRodItem;
import net.minecraft.util.Hand;

public class AutoFish extends Module {
    public final BooleanSetting recastGround = new BooleanSetting("Recast Ground", "Recast if hook hits ground", true);
    public final BooleanSetting recastCaught = new BooleanSetting("Recast Caught", "Recast if hook snags an entity", true);

    private int waitTicks = 0;
    private double lastVelY = 0.0;
    private boolean hadHook = false;
    private int hookTicks = 0;

    public AutoFish() {
        super("AutoFish", "Catches fish and recasts the line automatically", Category.WORLD);
        addSetting(recastGround);
        addSetting(recastCaught);
    }

    @Override
    public void onEnable() {
        waitTicks = 0;
        hadHook = false;
        hookTicks = 0;
        lastVelY = 0.0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!(mc.player.getMainHandStack().getItem() instanceof FishingRodItem)
                && !(mc.player.getOffHandStack().getItem() instanceof FishingRodItem)) {
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            if (waitTicks == 0 && mc.player.fishHook == null) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
            return;
        }
        if (mc.player.fishHook == null) {
            if (hadHook) {
                hadHook = false;
                hookTicks = 0;
                waitTicks = 10;
            } else {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                waitTicks = 10;
            }
            return;
        }
        hadHook = true;
        hookTicks++;
        boolean reel = false;
        if (recastCaught.isEnabled() && mc.player.fishHook.getHookedEntity() != null) reel = true;
        if (!reel && recastGround.isEnabled() && mc.player.fishHook.isOnGround()) reel = true;
        if (!reel && hookTicks > 20) {
            try {
                double vy = mc.player.fishHook.getVelocity().y;
                if (mc.player.fishHook.isTouchingWater() && lastVelY >= -0.05 && vy < -0.15) reel = true;
                lastVelY = vy;
            } catch (Throwable ignored) {}
        }
        if (reel) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            lastVelY = 0.0;
            waitTicks = 10;
        }
    }
}
