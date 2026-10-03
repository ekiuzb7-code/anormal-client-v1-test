package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;

public class JumpReset extends Module {
    public final NumberSetting chance = new NumberSetting("Chance", "Chance to attempt a perfect jump when hit", 80.0, 0.0, 100.0, 1.0);
    public final NumberSetting accuracy = new NumberSetting("Accuracy", "Chance a started jump is perfectly timed", 90.0, 0.0, 100.0, 1.0);
    public final BooleanSetting onlyWhenTargeting = new BooleanSetting("Only When Targeting", "Only jump when attacker is near crosshair", true);
    public final BooleanSetting waterCheck = new BooleanSetting("Water Check", "Disable in water or lava", true);
    private boolean wasHurt;
    private int pendingTicks;
    private int holdTicks;

    public JumpReset() {
        super("JumpReset", "Times a jump on incoming hits to cut knockback", Category.UZNY11);
        addSetting(chance);
        addSetting(accuracy);
        addSetting(onlyWhenTargeting);
        addSetting(waterCheck);
    }

    @Override
    public void onEnable() {
        wasHurt = false;
        pendingTicks = 0;
        holdTicks = 0;
    }

    @Override
    public void onDisable() {
        if (mc.options != null && holdTicks > 0) mc.options.jumpKey.setPressed(false);
        holdTicks = 0;
        pendingTicks = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.options == null) return;
        if (holdTicks > 0 && --holdTicks == 0) mc.options.jumpKey.setPressed(false);
        if (pendingTicks > 0 && --pendingTicks == 0) pressJump();
        boolean hurt = mc.player.hurtTime > 0;
        if (hurt && !wasHurt) attemptJump();
        wasHurt = hurt;
    }

    private void attemptJump() {
        if (waterCheck.isEnabled() && (mc.player.isTouchingWater() || mc.player.isInLava())) return;
        if (onlyWhenTargeting.isEnabled()) {
            if (!(mc.targetedEntity instanceof LivingEntity t) || !t.isAlive() || mc.player.distanceTo(t) > 5.0) return;
        }
        if (Math.random() * 100.0 >= chance.getValue()) return;
        if (Math.random() * 100.0 < accuracy.getValue()) pressJump();
        else pendingTicks = 4;
    }

    private void pressJump() {
        if (!mc.player.isOnGround() || mc.player.isGliding()) return;
        mc.options.jumpKey.setPressed(true);
        holdTicks = 1;
    }
}
