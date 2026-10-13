package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.util.math.MathHelper;

/**
 * Ported from Wurst's AntiAfkHack.
 *
 * <p>Wurst's settings, verbatim:</p>
 * <ul>
 *   <li>"Use AI" - wander around like a real player instead of a fixed pattern
 *       (default on)</li>
 *   <li>"AI range" - how far the AI is allowed to wander</li>
 *   <li>"Non-AI range" - how far the simple pattern wanders</li>
 *   <li>"Wait time" - seconds to stand still between movements</li>
 *   <li>"Wait time randomization" - extra random seconds added to the wait</li>
 *   <li>"Show wait time" - print the countdown in the HUD</li>
 * </ul>
 *
 * <p>The module walks to a random nearby point, then waits, then repeats. With
 * "Use AI" it also looks around and jumps occasionally, which is what keeps a
 * real anti-AFK check from flagging the account.</p>
 */
public class AntiAFK extends Module {
    public final BooleanSetting useAi = new BooleanSetting("Use AI", "Wander around like a real player instead of a fixed pattern", true);
    public final NumberSetting aiRange = new NumberSetting("AI range", "How far the AI is allowed to wander", 12.0, 3.0, 64.0, 1.0);
    public final NumberSetting nonAiRange = new NumberSetting("Non-AI range", "How far the simple pattern wanders", 5.0, 1.0, 32.0, 1.0);
    public final NumberSetting waitTime = new NumberSetting("Wait time", "Seconds to stand still between movements", 10.0, 0.0, 120.0, 1.0);
    public final NumberSetting waitTimeRand = new NumberSetting("Wait time randomization", "Extra random seconds added to the wait time", 5.0, 0.0, 60.0, 1.0);
    public final BooleanSetting showWaitTime = new BooleanSetting("Show wait time", "Show the remaining wait time above the hotbar", false);

    private int waitTicks = 0;
    private int moveTicks = 0;
    private double startX, startY, startZ;
    private float targetYaw;
    private boolean initialized = false;

    public AntiAFK() {
        super("AntiAFK", "Moves you around so you are not kicked for being AFK", Category.WORLD);
        addSetting(useAi);
        addSetting(aiRange);
        addSetting(nonAiRange);
        addSetting(waitTime);
        addSetting(waitTimeRand);
        addSetting(showWaitTime);

        aiRange.visibleIf(useAi::isEnabled);
        nonAiRange.visibleIf(() -> !useAi.isEnabled());
        waitTimeRand.visibleIf(() -> waitTime.getValue() > 0);
    }

    @Override
    public void onEnable() {
        waitTicks = 0;
        moveTicks = 0;
        initialized = false;
    }

    @Override
    public void onDisable() {
        release();
        initialized = false;
    }

    private void release() {
        try {
            if (mc.options != null) {
                mc.options.forwardKey.setPressed(false);
                mc.options.backKey.setPressed(false);
                mc.options.leftKey.setPressed(false);
                mc.options.rightKey.setPressed(false);
                mc.options.jumpKey.setPressed(false);
            }
            if (mc.player != null) mc.player.setSprinting(false);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.options == null) return;

        if (!initialized) {
            startX = mc.player.getX();
            startY = mc.player.getY();
            startZ = mc.player.getZ();
            initialized = true;
            beginWait();
            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            release();

            // Wurst's "Use AI": look around while waiting.
            if (useAi.isEnabled() && waitTicks % 20 == 0) {
                try {
                    mc.player.setYaw(mc.player.getYaw() + (float) ((Math.random() - 0.5) * 60.0));
                } catch (Throwable ignored) {
                }
            }
            return;
        }

        if (moveTicks <= 0) {
            // Pick a new destination.
            double range = useAi.isEnabled() ? aiRange.getValue() : nonAiRange.getValue();
            targetYaw = (float) (Math.random() * 360.0 - 180.0);
            double rad = Math.toRadians(targetYaw);
            startX = mc.player.getX() - Math.sin(rad) * range;
            startZ = mc.player.getZ() + Math.cos(rad) * range;
            moveTicks = 20 + (int) (Math.random() * 20);
            return;
        }

        moveTicks--;

        // Walk towards the destination.
        double dx = startX - mc.player.getX();
        double dz = startZ - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 1.5 || mc.player.getY() < startY - 4) {
            beginWait();
            return;
        }

        try {
            float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float diff = MathHelper.wrapDegrees(want - mc.player.getYaw());
            mc.player.setYaw(mc.player.getYaw() + MathHelper.clamp(diff, -20.0f, 20.0f));

            mc.options.forwardKey.setPressed(true);
            mc.options.sprintKey.setPressed(true);

            // Jump over obstacles / occasionally, like a real player.
            boolean shouldJump = useAi.isEnabled()
                    ? mc.player.horizontalCollision || Math.random() < 0.02
                    : mc.player.horizontalCollision;
            if (shouldJump && mc.player.isOnGround()) {
                mc.options.jumpKey.setPressed(true);
            } else {
                mc.options.jumpKey.setPressed(false);
            }
        } catch (Throwable ignored) {
        }
    }

    private void beginWait() {
        double base = waitTime.getValue();
        double rand = waitTimeRand.getValue();
        double secs = base + (rand > 0 ? Math.random() * rand : 0.0);
        waitTicks = (int) Math.round(secs * 20.0);
        moveTicks = 0;
        release();
    }

    /** Seconds left before the next movement, for HUD display. */
    public double getRemainingWaitSeconds() {
        return waitTicks / 20.0;
    }

    public boolean shouldShowWaitTime() {
        return showWaitTime.isEnabled();
    }
}
