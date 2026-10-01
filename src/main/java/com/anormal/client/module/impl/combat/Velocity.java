package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.util.math.Vec3d;

public class Velocity extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Velocity mode", "Standard", "Standard", "Jump Reset");
    public final NumberSetting horizontal = new NumberSetting("Horizontal", "Horizontal knockback %", 0.0, 0.0, 100.0, 5.0);
    public final NumberSetting vertical = new NumberSetting("Vertical", "Vertical knockback %", 100.0, 0.0, 100.0, 5.0);
    public final NumberSetting chance = new NumberSetting("Chance", "Knockback reduction chance %", 100.0, 10.0, 100.0, 5.0);
    public final BooleanSetting onlyMoving = new BooleanSetting("Only Moving", "Only reduce knockback while moving", false);

    public Velocity() {
        super("Velocity", "Reduces or eliminates knockback taken from damage", Category.COMBAT);
        addSetting(mode);
        addSetting(horizontal);
        addSetting(vertical);
        addSetting(chance);
        addSetting(onlyMoving);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (onlyMoving.isEnabled() && mc.player.forwardSpeed == 0 && mc.player.sidewaysSpeed == 0) return;

        if (mc.player.hurtTime == 9 || mc.player.hurtTime == 8) {
            if (Math.random() * 100.0 <= chance.getValue()) {
                if ("Jump Reset".equals(mode.getValue()) && mc.player.isOnGround()) {
                    mc.player.jump();
                } else {
                    double h = horizontal.getValue() / 100.0;
                    double v = vertical.getValue() / 100.0;
                    Vec3d vel = mc.player.getVelocity();
                    mc.player.setVelocity(vel.x * h, vel.y * v, vel.z * h);
                }
            }
        }
    }
}
