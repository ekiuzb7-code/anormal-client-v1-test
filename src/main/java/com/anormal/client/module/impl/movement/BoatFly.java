package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class BoatFly extends Module {
    public final NumberSetting speed = new NumberSetting("Speed", "Forward speed multiplier", 2.5, 0.1, 10.0, 0.1);
    public final NumberSetting upwardSpeed = new NumberSetting("Upward Speed", "Up/down speed (Space/Shift)", 0.5, 0.0, 5.0, 0.1);
    public final BooleanSetting changeForwardSpeed = new BooleanSetting("Change Forward Speed", "Allows custom forward speed, disables smooth acceleration", false);
    public final BooleanSetting cancelFallDamage = new BooleanSetting("Cancel Fall Damage", "Prevent fall damage when exiting boat", true);
    public final BooleanSetting autoMount = new BooleanSetting("Auto Mount", "Automatically mount nearby boats", false);
    public final NumberSetting autoMountRange = new NumberSetting("Auto Mount Range", "Range to auto mount boats", 5.0, 1.0, 10.0, 0.5);

    public BoatFly() {
        super("BoatFly", "Fly in boats with full 3D movement", Category.MOVEMENT);
        addSetting(speed);
        addSetting(upwardSpeed);
        addSetting(changeForwardSpeed);
        addSetting(cancelFallDamage);
        addSetting(autoMount);
        addSetting(autoMountRange);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // Auto mount nearby boat
        if (autoMount.isEnabled()) {
            tryMountBoat();
        }

        // Only work when riding a boat
        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof BoatEntity boat)) return;

        Vec3d velocity = boat.getVelocity();

        // Default motion
        double motionX = velocity.x;
        double motionY = 0;
        double motionZ = velocity.z;

        // Up/Down
        if (mc.options.jumpKey.isPressed()) {
            motionY = upwardSpeed.getValue();
        } else if (mc.options.sprintKey.isPressed()) {
            motionY = velocity.y; // Keep current Y velocity when sprint held
        }

        // Forward (W key)
        if (mc.options.forwardKey.isPressed() && changeForwardSpeed.isEnabled()) {
            double s = speed.getValue();
            float yawRad = boat.getYaw() * MathHelper.RADIANS_PER_DEGREE;
            motionX = -MathHelper.sin(yawRad) * s;
            motionZ = MathHelper.cos(yawRad) * s;
        }

        // Apply motion directly to boat
        boat.setVelocity(motionX, motionY, motionZ);

        // Cancel fall distance
        if (cancelFallDamage.isEnabled()) {
            mc.player.fallDistance = 0.0f;
        }
    }

    private void tryMountBoat() {
        if (mc.player.getVehicle() != null) return; // Already riding something

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof BoatEntity boat) {
                double dist = mc.player.distanceTo(boat);
                if (dist <= autoMountRange.getValue()) {
                    mc.player.startRiding(boat);
                    break;
                }
            }
        }
    }
}