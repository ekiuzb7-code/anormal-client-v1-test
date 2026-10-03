package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Vec3d;

public class BoatFly extends Module {
    public final NumberSetting speed = new NumberSetting("Speed", "Boat flight speed", 2.0, 0.5, 5.0, 0.1);
    public final NumberSetting verticalSpeed = new NumberSetting("Vertical Speed", "Up/down speed", 1.5, 0.1, 3.0, 0.1);
    public final NumberSetting acceleration = new NumberSetting("Acceleration", "How fast boat reaches max speed", 0.15, 0.01, 0.5, 0.01);
    public final NumberSetting friction = new NumberSetting("Friction", "How fast boat stops when no input", 0.98, 0.8, 1.0, 0.01);
    public final BooleanSetting allowSprint = new BooleanSetting("Allow Sprint Boost", "Double speed when sprinting", true);
    public final NumberSetting sprintMultiplier = new NumberSetting("Sprint Multiplier", "Speed multiplier when sprinting", 1.5, 1.0, 3.0, 0.1);
    public final BooleanSetting noClip = new BooleanSetting("No Clip", "Phase through blocks", true);
    public final BooleanSetting cancelFallDamage = new BooleanSetting("Cancel Fall Damage", "Prevent fall damage when exiting", true);
    public final BooleanSetting autoMount = new BooleanSetting("Auto Mount", "Automatically mount nearby boats", false);
    public final NumberSetting autoMountRange = new NumberSetting("Auto Mount Range", "Range to auto mount boats", 5.0, 1.0, 10.0, 0.5);

    private double velocityX = 0, velocityY = 0, velocityZ = 0;

    public BoatFly() {
        super("BoatFly", "Fly in boats with full 3D movement", Category.MOVEMENT);
        addSetting(speed);
        addSetting(verticalSpeed);
        addSetting(acceleration);
        addSetting(friction);
        addSetting(allowSprint);
        addSetting(sprintMultiplier);
        addSetting(noClip);
        addSetting(cancelFallDamage);
        addSetting(autoMount);
        addSetting(autoMountRange);
    }

    @Override
    public void onEnable() {
        velocityX = velocityY = velocityZ = 0;
    }

    @Override
    public void onDisable() {
        velocityX = velocityY = velocityZ = 0;
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

        // Get input
        boolean forward = mc.options.forwardKey.isPressed();
        boolean back = mc.options.backKey.isPressed();
        boolean left = mc.options.leftKey.isPressed();
        boolean right = mc.options.rightKey.isPressed();
        boolean up = mc.options.jumpKey.isPressed();
        boolean down = mc.options.sneakKey.isPressed();
        boolean sprint = allowSprint.isEnabled() && mc.options.sprintKey.isPressed();

        double currentSpeed = speed.getValue() * (sprint ? sprintMultiplier.getValue() : 1.0);
        double currentVerticalSpeed = verticalSpeed.getValue();
        double accel = acceleration.getValue();
        double fric = friction.getValue();

        // Calculate movement direction relative to boat yaw
        float yaw = boat.getYaw();
        float pitch = boat.getPitch();

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = -forwardZ;
        double rightZ = forwardX;

        // Horizontal movement
        double moveX = 0, moveZ = 0;
        if (forward) { moveX += forwardX; moveZ += forwardZ; }
        if (back) { moveX -= forwardX; moveZ -= forwardZ; }
        if (left) { moveX += rightX; moveZ += rightZ; }
        if (right) { moveX -= rightX; moveZ -= rightZ; }

        // Normalize diagonal movement
        double moveLen = Math.sqrt(moveX * moveX + moveZ * moveZ);
        if (moveLen > 0) {
            moveX /= moveLen;
            moveZ /= moveLen;
        }

        // Apply acceleration
        velocityX += moveX * currentSpeed * accel;
        velocityZ += moveZ * currentSpeed * accel;

        // Apply friction when no input
        if (moveLen == 0) {
            velocityX *= fric;
            velocityZ *= fric;
        }

        // Clamp horizontal velocity
        double maxHSpeed = currentSpeed;
        double hSpeed = Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
        if (hSpeed > maxHSpeed) {
            velocityX = velocityX / hSpeed * maxHSpeed;
            velocityZ = velocityZ / hSpeed * maxHSpeed;
        }

        // Vertical movement
        if (up) velocityY += currentVerticalSpeed * accel;
        else if (down) velocityY -= currentVerticalSpeed * accel;
        else velocityY *= fric;

        // Clamp vertical velocity
        double maxVSpeed = currentVerticalSpeed;
        if (velocityY > maxVSpeed) velocityY = maxVSpeed;
        if (velocityY < -maxVSpeed) velocityY = -maxVSpeed;

        // Apply velocity to boat
        if (!noClip.isEnabled()) {
            // Normal physics - let Minecraft handle collision
            boat.setVelocity(velocityX, velocityY, velocityZ);
        } else {
            // No clip - bypass collision
            double newX = boat.getX() + velocityX;
            double newY = boat.getY() + velocityY;
            double newZ = boat.getZ() + velocityZ;
            boat.setPosition(newX, newY, newZ);
            boat.setVelocity(0, 0, 0); // Prevent vanilla physics interference
        }

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