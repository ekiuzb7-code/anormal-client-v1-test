package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.util.math.Vec3d;

public class Velocity extends Module {
    public final NumberSetting horizontal = new NumberSetting("Horizontal", "Horizontal knockback reduction %", 100.0, 0.0, 100.0, 1.0);
    public final NumberSetting vertical = new NumberSetting("Vertical", "Vertical knockback reduction %", 100.0, 0.0, 100.0, 1.0);
    public final BooleanSetting onlyWhenHit = new BooleanSetting("Only When Hit", "Only reduce when actually hit", true);

    private boolean wasHurt = false;

    public Velocity() {
        super("Velocity", "Reduces knockback taken", Category.UZNY11);
        addSetting(horizontal);
        addSetting(vertical);
        addSetting(onlyWhenHit);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        boolean hurt = mc.player.hurtTime > 0;
        if (hurt && !wasHurt) {
            applyVelocity();
        }
        wasHurt = hurt;
    }

    private void applyVelocity() {
        if (onlyWhenHit.isEnabled() && mc.player.hurtTime <= 0) return;

        double h = horizontal.getValue() / 100.0;
        double v = vertical.getValue() / 100.0;

        Vec3d vel = mc.player.getVelocity();
        mc.player.setVelocity(vel.x * h, vel.y * v, vel.z * h);
    }
}