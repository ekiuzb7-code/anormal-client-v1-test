package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

public class Velocity extends Module {
    public final NumberSetting horizontal = new NumberSetting("Horizontal", "Kept knockback on X Z percent", 90.0, 0.0, 100.0, 1.0);
    public final NumberSetting vertical = new NumberSetting("Vertical", "Kept knockback on Y percent", 100.0, 0.0, 100.0, 1.0);
    public final NumberSetting ticks = new NumberSetting("Ticks", "Delay before reducing knockback", 0.0, 0.0, 10.0, 1.0);
    public final BooleanSetting kiteMode = new BooleanSetting("Kite Mode", "Boost knockback when hit from behind", false);
    public final NumberSetting kiteHorizontal = new NumberSetting("Kite Horizontal", "Kept X Z percent when kiting", 130.0, 100.0, 200.0, 5.0);
    public final NumberSetting kiteVertical = new NumberSetting("Kite Vertical", "Kept Y percent when kiting", 110.0, 100.0, 200.0, 5.0);
    public final BooleanSetting alwaysKite = new BooleanSetting("Always Kite", "Kite regardless of hit direction", false);
    public final NumberSetting chance = new NumberSetting("Chance", "Chance to cut knockback", 100.0, 0.0, 100.0, 1.0);
    public final BooleanSetting onlyWhenTargeting = new BooleanSetting("Only When Targeting", "Only cut when attacker near crosshair", true);
    public final BooleanSetting waterCheck = new BooleanSetting("Water Check", "Disable in water or lava", true);
    private boolean wasHurt;
    private int pendingTicks;
    private boolean useKite;

    public Velocity() {
        super("Velocity", "Reduces knockback taken when hit", Category.UZNY11);
        addSetting(horizontal);
        addSetting(vertical);
        addSetting(ticks);
        addSetting(kiteMode);
        addSetting(kiteHorizontal);
        addSetting(kiteVertical);
        addSetting(alwaysKite);
        addSetting(chance);
        addSetting(onlyWhenTargeting);
        addSetting(waterCheck);
    }

    @Override
    public void onEnable() {
        wasHurt = false;
        pendingTicks = 0;
        useKite = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (pendingTicks > 0 && --pendingTicks == 0) apply(useKite);
        boolean hurt = mc.player.hurtTime > 0;
        if (hurt && !wasHurt) onHurt();
        wasHurt = hurt;
    }

    private void onHurt() {
        if (waterCheck.isEnabled() && (mc.player.isTouchingWater() || mc.player.isInLava())) return;
        if (onlyWhenTargeting.isEnabled() && !targeting()) return;
        if (Math.random() * 100.0 >= chance.getValue()) return;
        if (kiteMode.isEnabled() && (alwaysKite.isEnabled() || hitFromBehind())) {
            apply(true);
            return;
        }
        int delay = ticks.getValue().intValue();
        if (delay <= 0) apply(false);
        else {
            pendingTicks = delay;
            useKite = false;
        }
    }

    private boolean targeting() {
        if (!(mc.targetedEntity instanceof LivingEntity t) || !t.isAlive()) return false;
        return mc.player.distanceTo(t) <= 5.0;
    }

    private boolean hitFromBehind() {
        if (!(mc.targetedEntity instanceof LivingEntity t) || !t.isAlive()) return false;
        double dx = t.getX() - mc.player.getX();
        double dz = t.getZ() - mc.player.getZ();
        if (dx * dx + dz * dz < 1.0E-6) return false;
        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d toAttacker = new Vec3d(dx, 0.0, dz).normalize();
        return look.dotProduct(toAttacker) < 0.0;
    }

    private void apply(boolean kite) {
        double h = (kite ? kiteHorizontal : horizontal).getValue() / 100.0;
        double v = (kite ? kiteVertical : vertical).getValue() / 100.0;
        Vec3d vel = mc.player.getVelocity();
        mc.player.setVelocity(vel.x * h, vel.y * v, vel.z * h);
    }
}
