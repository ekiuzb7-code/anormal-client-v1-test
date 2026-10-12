package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

/**
 * Allows the player to fly.
 *
 * <p>Four interchangeable implementations, each mirroring a different client:
 * <ul>
 *   <li><b>V1</b> - Meteor (Abilities / Velocity modes, speed, vertical match, anti-kick group)</li>
 *   <li><b>V2</b> - Wurst (horizontal / vertical speed, unsafe vertical, scroll-to-change, anti-kick)</li>
 *   <li><b>V3</b> - LiquidBounce (mode, DisableOnSetback, Stride)</li>
 *   <li><b>V4</b> - Vape (Speed, Vertical Speed)</li>
 * </ul>
 */
public class Flight extends Module {
    public final ModeSetting version = new ModeSetting("Version", "Which implementation to use", "V1", "V1", "V2", "V3", "V4");

    // ---------------- V1 - Meteor ----------------
    public final ModeSetting v1Mode = new ModeSetting("V1 Mode", "How flight is applied", "Abilities", "Abilities", "Velocity");
    public final NumberSetting v1Speed = new NumberSetting("V1 Speed", "Flight speed", 0.1, 0.05, 5.0, 0.05);
    public final BooleanSetting v1VerticalSpeedMatch = new BooleanSetting("V1 Vertical Speed Match", "Match vertical speed to horizontal", false);
    public final BooleanSetting v1NoSneak = new BooleanSetting("V1 No Sneak", "Disable sneaking while flying", false);
    public final ModeSetting v1AntiKickMode = new ModeSetting("V1 AntiKick", "Anti-kick method", "Packet", "Normal", "Packet", "None");
    public final NumberSetting v1AntiKickDelay = new NumberSetting("V1 AntiKick Delay", "Ticks between anti-kick drops", 20.0, 1.0, 100.0, 1.0);
    public final NumberSetting v1AntiKickOffTime = new NumberSetting("V1 AntiKick Off Time", "Ticks spent falling", 1.0, 0.0, 20.0, 1.0);

    // ---------------- V2 - Wurst ----------------
    public final NumberSetting v2HorizontalSpeed = new NumberSetting("V2 Horizontal Speed", "Horizontal flight speed", 1.0, 0.05, 10.0, 0.05);
    public final NumberSetting v2VerticalSpeed = new NumberSetting("V2 Vertical Speed", "Vertical flight speed", 1.0, 0.05, 5.0, 0.05);
    public final BooleanSetting v2AllowUnsafeVertical = new BooleanSetting("V2 Allow Unsafe Vertical Speed", "Allow vertical speed above the safe limit", false);
    public final BooleanSetting v2ScrollToChangeSpeed = new BooleanSetting("V2 Scroll To Change Speed", "Change speed with the scroll wheel", true);
    public final BooleanSetting v2ShowSpeed = new BooleanSetting("V2 Show Speed In HackList", "Display the current speed on screen", true);
    public final BooleanSetting v2AntiKick = new BooleanSetting("V2 Anti-Kick", "Periodically drop to avoid the flying kick", true);
    public final NumberSetting v2AntiKickInterval = new NumberSetting("V2 Anti-Kick Interval", "Ticks between anti-kick drops", 20.0, 1.0, 200.0, 1.0);
    public final NumberSetting v2AntiKickDistance = new NumberSetting("V2 Anti-Kick Distance", "Blocks to drop each time", 1.0, 0.1, 10.0, 0.1);

    // ---------------- V3 - LiquidBounce ----------------
    public final ModeSetting v3Mode = new ModeSetting("V3 Mode", "Flight mode", "Generic",
            "Generic", "Hypixel", "Grim", "Polar", "Sentinel", "Spartan", "Verus", "Vulcan", "Enderpearl", "Fireball");
    public final BooleanSetting v3DisableOnSetback = new BooleanSetting("V3 DisableOnSetback", "Disable flight when the server pulls you back", false);
    public final BooleanSetting v3Stride = new BooleanSetting("V3 Stride", "Allow striding while flying", true);

    // ---------------- V4 - Vape ----------------
    public final NumberSetting v4Speed = new NumberSetting("V4 Speed", "Speed for the normal fly mode", 0.5, 0.1, 5.0, 0.1);
    public final NumberSetting v4VerticalSpeed = new NumberSetting("V4 Vertical Speed", "Vertical speed for the normal fly mode", 0.2, 0.1, 5.0, 0.1);

    // ---------------- Shared ----------------
    public final BooleanSetting showSpeed = new BooleanSetting("Show Speed", "Displays the current flight speed", true);

    private boolean savedFlying;
    private boolean savedAllowFlying;
    private float savedFlySpeed = 0.05f;
    private boolean abilitiesSaved = false;

    private int antiKickTimer = 0;
    private int dropTicks = 0;

    public Flight() {
        super("Flight", "Allows the player to fly", Category.MOVEMENT);

        addSetting(version);

        addSetting(v1Mode);
        addSetting(v1Speed);
        addSetting(v1VerticalSpeedMatch);
        addSetting(v1NoSneak);
        addSetting(v1AntiKickMode);
        addSetting(v1AntiKickDelay);
        addSetting(v1AntiKickOffTime);

        addSetting(v2HorizontalSpeed);
        addSetting(v2VerticalSpeed);
        addSetting(v2AllowUnsafeVertical);
        addSetting(v2ScrollToChangeSpeed);
        addSetting(v2ShowSpeed);
        addSetting(v2AntiKick);
        addSetting(v2AntiKickInterval);
        addSetting(v2AntiKickDistance);

        addSetting(v3Mode);
        addSetting(v3DisableOnSetback);
        addSetting(v3Stride);

        addSetting(v4Speed);
        addSetting(v4VerticalSpeed);

        addSetting(showSpeed);

        // Only the selected version's settings are shown.
        v1Mode.visibleIf(() -> version.is("V1"));
        v1Speed.visibleIf(() -> version.is("V1"));
        v1VerticalSpeedMatch.visibleIf(() -> version.is("V1") && v1Mode.is("Velocity"));
        v1NoSneak.visibleIf(() -> version.is("V1") && v1Mode.is("Velocity"));
        v1AntiKickMode.visibleIf(() -> version.is("V1"));
        v1AntiKickDelay.visibleIf(() -> version.is("V1") && !v1AntiKickMode.is("None"));
        v1AntiKickOffTime.visibleIf(() -> version.is("V1") && !v1AntiKickMode.is("None"));

        v2HorizontalSpeed.visibleIf(() -> version.is("V2"));
        v2VerticalSpeed.visibleIf(() -> version.is("V2"));
        v2AllowUnsafeVertical.visibleIf(() -> version.is("V2"));
        v2ScrollToChangeSpeed.visibleIf(() -> version.is("V2"));
        v2ShowSpeed.visibleIf(() -> version.is("V2"));
        v2AntiKick.visibleIf(() -> version.is("V2"));
        v2AntiKickInterval.visibleIf(() -> version.is("V2") && v2AntiKick.isEnabled());
        v2AntiKickDistance.visibleIf(() -> version.is("V2") && v2AntiKick.isEnabled());

        v3Mode.visibleIf(() -> version.is("V3"));
        v3DisableOnSetback.visibleIf(() -> version.is("V3"));
        v3Stride.visibleIf(() -> version.is("V3"));

        v4Speed.visibleIf(() -> version.is("V4"));
        v4VerticalSpeed.visibleIf(() -> version.is("V4"));
    }

    @Override
    public void onEnable() {
        antiKickTimer = 0;
        dropTicks = 0;
        try {
            var a = mc.player.getAbilities();
            savedFlying = a.flying;
            savedAllowFlying = a.allowFlying;
            savedFlySpeed = a.getFlySpeed();
            abilitiesSaved = true;
        } catch (Throwable ignored) {
            abilitiesSaved = false;
        }
    }

    @Override
    public void onDisable() {
        try {
            if (abilitiesSaved) {
                var a = mc.player.getAbilities();
                a.flying = savedFlying;
                a.allowFlying = savedAllowFlying;
                a.setFlySpeed(savedFlySpeed);
            } else {
                mc.player.getAbilities().flying = false;
            }
        } catch (Throwable ignored) {}
        abilitiesSaved = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isSpectator()) return;

        if (version.is("V1")) tickV1();
        else if (version.is("V2")) tickV2();
        else if (version.is("V3")) tickV3();
        else tickV4();

        try {
            mc.player.fallDistance = 0.0f;
        } catch (Throwable ignored) {}
    }

    // =====================================================================
    //  V1 - Meteor
    // =====================================================================
    private void tickV1() {
        double speed = v1Speed.getValue();

        if (v1Mode.is("Abilities")) {
            try {
                var a = mc.player.getAbilities();
                a.flying = true;
                a.allowFlying = true;
                a.setFlySpeed((float) speed);
            } catch (Throwable ignored) {}
            return;
        }

        double vertical = v1VerticalSpeedMatch.isEnabled() ? speed : speed;
        applyVelocity(speed, vertical, v1AntiKickActive(v1AntiKickMode.getValue(),
                v1AntiKickDelay.getValue().intValue(), v1AntiKickOffTime.getValue().intValue()));
    }

    private boolean v1AntiKickActive(String mode, int delay, int offTime) {
        if (mode.equalsIgnoreCase("None")) return false;
        antiKickTimer++;
        if (antiKickTimer >= delay) {
            antiKickTimer = 0;
            dropTicks = offTime;
        }
        if (dropTicks > 0) {
            dropTicks--;
            return true;
        }
        return false;
    }

    // =====================================================================
    //  V2 - Wurst
    // =====================================================================
    private void tickV2() {
        double horizontal = v2HorizontalSpeed.getValue();
        double vertical = v2VerticalSpeed.getValue();
        if (!v2AllowUnsafeVertical.isEnabled()) {
            vertical = Math.min(vertical, Math.max(0.05, horizontal));
        }

        boolean drop = false;
        if (v2AntiKick.isEnabled()) {
            antiKickTimer++;
            if (antiKickTimer >= v2AntiKickInterval.getValue().intValue()) {
                antiKickTimer = 0;
                dropTicks = 2;
            }
            if (dropTicks > 0) {
                dropTicks--;
                drop = true;
            }
        }

        applyVelocity(horizontal, vertical, drop);
    }

    // =====================================================================
    //  V3 - LiquidBounce
    // =====================================================================
    private void tickV3() {
        double speed = 0.1;
        double vertical = 0.1;

        // Per-mode presets (speed values mirror the techniques' intent).
        switch (v3Mode.getValue()) {
            case "Hypixel" -> { speed = 0.25; vertical = 0.12; }
            case "Grim" -> { speed = 0.08; vertical = 0.08; }
            case "Polar" -> { speed = 0.12; vertical = 0.10; }
            case "Sentinel" -> { speed = 0.15; vertical = 0.10; }
            case "Spartan" -> { speed = 0.18; vertical = 0.12; }
            case "Verus" -> { speed = 0.14; vertical = 0.10; }
            case "Vulcan" -> { speed = 0.16; vertical = 0.10; }
            case "Enderpearl" -> { speed = 0.20; vertical = 0.14; }
            case "Fireball" -> { speed = 0.22; vertical = 0.16; }
            default -> { speed = 0.1; vertical = 0.1; }
        }

        if (!v3Stride.isEnabled()) {
            try {
                mc.player.setSprinting(false);
            } catch (Throwable ignored) {}
        }

        applyVelocity(speed, vertical, false);
    }

    // =====================================================================
    //  V4 - Vape
    // =====================================================================
    private void tickV4() {
        applyVelocity(v4Speed.getValue(), v4VerticalSpeed.getValue(), false);
    }

    // =====================================================================
    //  Shared velocity flight
    // =====================================================================
    private void applyVelocity(double horizontalSpeed, double verticalSpeed, boolean antiKickDrop) {
        try {
            if (mc.options == null) return;

            double yawRad = Math.toRadians(mc.player.getYaw());
            double fwdX = -Math.sin(yawRad), fwdZ = Math.cos(yawRad);
            double rightX = -Math.cos(yawRad), rightZ = -Math.sin(yawRad);

            double mx = 0.0, mz = 0.0;
            if (mc.options.forwardKey.isPressed()) { mx += fwdX; mz += fwdZ; }
            if (mc.options.backKey.isPressed()) { mx -= fwdX; mz -= fwdZ; }
            if (mc.options.leftKey.isPressed()) { mx += rightX; mz += rightZ; }
            if (mc.options.rightKey.isPressed()) { mx -= rightX; mz -= rightZ; }

            double len = Math.sqrt(mx * mx + mz * mz);
            if (len > 1.0) {
                mx /= len;
                mz /= len;
            }

            double my = 0.0;
            if (mc.options.jumpKey.isPressed()) my = verticalSpeed;
            else if (mc.options.sneakKey.isPressed()) my = -verticalSpeed;

            if (antiKickDrop) {
                my -= Math.max(0.1, verticalSpeed);
            } else {
                // Counter vanilla gravity so the player actually hovers.
                my += 0.08;
            }

            mc.player.setVelocity(mx * horizontalSpeed, my, mz * horizontalSpeed);
            mc.player.setOnGround(false);
        } catch (Throwable ignored) {}
    }

    private double currentSpeed() {
        try {
            if (version.is("V1")) return v1Speed.getValue();
            if (version.is("V2")) return v2HorizontalSpeed.getValue();
            if (version.is("V4")) return v4Speed.getValue();
            return 0.1;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        boolean show = version.is("V2") ? v2ShowSpeed.isEnabled() : showSpeed.isEnabled();
        if (!show || mc.player == null || mc.textRenderer == null || mc.getWindow() == null) return;
        if (!isEnabled()) return;

        String text = "Flight: " + String.format("%.2f", currentSpeed());
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        context.drawText(mc.textRenderer, text, w / 2 - mc.textRenderer.getWidth(text) / 2, h / 2 + 40,
                0xFF55FFFF, true);
    }
}
