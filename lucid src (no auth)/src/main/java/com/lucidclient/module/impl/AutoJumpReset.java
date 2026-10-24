package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.IntSetting;
import com.lucidclient.module.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;

/**
 * AutoJumpReset — automates the vanilla "jump reset" knockback-reduction
 * technique.
 *
 * ── The technique ────────────────────────────────────────────────
 * In Minecraft PvP, if you jump on the exact tick you take damage, the
 * jump's upward impulse (0.42 b/t) overrides part of the attacker's
 * horizontal knockback. Your momentum shifts up instead of back, so you
 * stay in attack range and keep ground control. Skilled players do this
 * by reflex; this module does it frame-accurately every time (subject to
 * the Chance setting).
 *
 * ── How it detects a hit ─────────────────────────────────────────
 * {@code player.hurtTime} is set to 10 ticks the moment damage lands and
 * counts down each tick. We watch for the tick where
 * {@code hurtTime == (10 - delayTicks)} and the player is on solid ground,
 * then inject a jump. The {@code onTick} framework fires on
 * {@code END_CLIENT_TICK}, so we read hurtTime at the same point in the
 * tick loop every frame — identical timing to a {@code MinecraftClient.tick()}
 * mixin, without needing one.
 *
 * ── State guards ─────────────────────────────────────────────────
 * Jump reset only helps on solid ground in the open. We skip when the
 * player is in water, lava, on a ladder/vine, or already airborne — a
 * jump there is wasted or counterproductive.
 *
 * ── Input injection ──────────────────────────────────────────────
 * We simulate a REAL jump key press via {@code options.jumpKey.setPressed}
 * (the same input path the game reads for vanilla jumping) and also call
 * {@code player.jump()} directly so the impulse is guaranteed to apply on
 * this tick regardless of vanilla's internal input-consumption order. No
 * packets are forged, no velocity is spoofed — this is plain client input,
 * the same category as the existing AutoClicker.
 *
 * ── Consume-once ─────────────────────────────────────────────────
 * A single damage event leaves hurtTime counting 10→9→…→1 across many
 * ticks. {@code lastProcessedHurtTime} ensures each damage event triggers
 * exactly one jump, not one per tick of the hurt animation.
 *
 * Settings:
 *   Toggle Key, Chance, Delay Ticks, Require Sprint, Require Attack Key,
 *   Cancel Sneak.
 */
public class AutoJumpReset extends Module {

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to start/stop jump-resetting. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final FloatSetting chance = addSetting(
        new FloatSetting("Chance", "Probability of jump-resetting a qualifying hit (1.0 = every hit).",
            0f, 1f, 1f, 2));
    private final IntSetting delayTicks = addSetting(
        new IntSetting("Delay Ticks", "Ticks to wait after damage before jumping. 0 = instant.",
            0, 5, 0, 1));
    private final BooleanSetting requireSprint = addSetting(
        new BooleanSetting("Require Sprint", "Only jump-reset while sprinting (stacks with sprint-reset KB reduction).",
            true));
    private final BooleanSetting requireAttackKey = addSetting(
        new BooleanSetting("Require Attack Key", "Only jump-reset while holding the attack (left-click) key.",
            true));
    private final BooleanSetting cancelSneak = addSetting(
        new BooleanSetting("Cancel Sneak", "Release sneak on the jump tick so it doesn't suppress the hop.",
            true));

    // ── State ────────────────────────────────────────────────────
    /** hurtTime value we last acted on, so one damage event = one jump. */
    private int lastProcessedHurtTime = 0;
    /** Ticks remaining to hold the injected jump key before releasing. */
    private int jumpHeldTicks = 0;

    /** Toggle key edge detection. */
    private boolean wasKeyDown = false;

    public AutoJumpReset() {
        super("AutoJumpReset",
            "Automatically jumps the instant you take a hit to convert horizontal knockback into vertical rise.",
            Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        lastProcessedHurtTime = 0;
        jumpHeldTicks = 0;
        wasKeyDown = false;
    }

    @Override
    protected void onDisable() {
        // Release any injected jump/sneak state so we don't leave keys stuck.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) {
            client.options.jumpKey.setPressed(false);
        }
        jumpHeldTicks = 0;
    }

    /**
     * onTickAlways — toggle key works even when the module is disabled,
     * matching AutoClicker's pattern.
     */
    @Override
    public void onTickAlways(MinecraftClient client) {
        if (client.player == null || client.getWindow() == null) return;
        int key = toggleKey.getKey();
        if (key < 0) return;

        long handle = client.getWindow().getHandle();
        boolean isDown;
        if (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_8) {
            isDown = GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS;
        } else {
            isDown = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        }

        if (isDown && !wasKeyDown) {
            setEnabled(!isEnabled());
        }
        wasKeyDown = isDown;
    }

    @Override
    public void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        if (client.currentScreen != null) return;

        // ── Release any previously-injected jump key ───────────────
        // We hold the injected press for a couple ticks so the vanilla
        // input path definitely registers it, then release to avoid the
        // vanilla "held jump = auto-bounce" behaviour.
        if (jumpHeldTicks > 0) {
            jumpHeldTicks--;
            if (jumpHeldTicks == 0) {
                client.options.jumpKey.setPressed(false);
            }
        }

        int hurtTime = player.hurtTime;
        if (hurtTime <= 0) {
            // No recent damage — clear the dedup so the next hit can trigger.
            lastProcessedHurtTime = 0;
            return;
        }

        // ── State guards ───────────────────────────────────────────
        // Jump reset only helps on solid ground in the open.
        if (!player.isOnGround()) return;
        if (player.isTouchingWater() || player.isInLava()) return;
        if (player.isClimbing()) return;

        if (requireSprint.isEnabled() && !player.isSprinting()) return;
        if (requireAttackKey.isEnabled() && !client.options.attackKey.isPressed()) return;

        // ── Damage-tick match ──────────────────────────────────────
        // hurtTime starts at 10 on the damage tick and counts down. The
        // target tick is (10 - delay). delay=0 → trigger same tick damage
        // lands; delay=1 → hurtTime==9 (the reference's value), etc.
        int targetHurtTime = 10 - delayTicks.get();
        if (hurtTime != targetHurtTime) return;

        // ── Consume-once: one jump per damage event ────────────────
        // If we already processed this exact hurtTime for this hit, skip.
        // (lastProcessedHurtTime resets to 0 once hurtTime returns to 0,
        // so the next hit is fresh.)
        if (lastProcessedHurtTime == hurtTime) return;

        // ── Chance roll ────────────────────────────────────────────
        if (chance.get() < 1f && Math.random() > chance.get()) {
            // Missed the roll — still mark consumed so we don't retry this
            // hit on the next tick of the hurt animation.
            lastProcessedHurtTime = hurtTime;
            return;
        }

        // ── Inject the jump ────────────────────────────────────────
        lastProcessedHurtTime = hurtTime;
        if (cancelSneak.isEnabled()) {
            client.options.sneakKey.setPressed(false);
        }
        // Real key-simulated input — the vanilla input path.
        client.options.jumpKey.setPressed(true);
        // Direct impulse for frame-accuracy on this tick.
        player.jump();
        // Hold the injected press briefly, then release (handled above).
        jumpHeldTicks = 2;
    }
}