package com.lucidclient.module.impl;

import com.lucidclient.LucidClient;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

/**
 * AutoClicker — automatic left-click attacker / block breaker.
 *
 * ── Toggle key ────────────────────────────────────────────────────
 * A KeybindSetting holds the toggle key. We check it in onTickAlways
 * (NOT onTick) so the clicker can be toggled on/off even when the module
 * is disabled. This matches how Freecam's toggle key works.
 *
 * ── Gradual CPS build up / down ──────────────────────────────────
 * Rather than instantly clicking at the target CPS, we ramp currentCPS
 * toward the (noisy) target CPS:
 *   - When increasing, currentCPS += buildUp per tick.
 *   - When decreasing, currentCPS -= buildDown per tick.
 * This produces a natural-sounding "warming up" + "cooling down" pattern.
 *
 * ── Respect Cooldown ─────────────────────────────────────────────
 * Vanilla 1.9+ combat uses an attack-cooldown that resets after each
 * swing. The vanilla client checks `player.getAttackCooldownProgress(0.5f)`
 * in `MinecraftClient.doAttack` and BLOCKS the click if < 1.0. We bypass
 * doAttack entirely — calling attackEntity / updateBlockBreakingProgress /
 * swingHand directly — but still gate the click on cooldown progress when
 * "Respect Cooldown" is on, so we don't waste weak hits.
 *
 * ── Humanize ──────────────────────────────────────────────────────
 * Adds a small random ± to the target CPS each tick so the click rate
 * isn't a perfect square wave (anti-cheat friendlier).
 *
 * Settings:
 *   Toggle Key, CPS, Build Up, Build Down, Respect Cooldown,
 *   Cooldown Threshold, Humanize.
 */
public class AutoClicker extends Module {

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to start/stop auto-clicking. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final FloatSetting cps = addSetting(
        new FloatSetting("CPS", "Target clicks per second.", 1f, 20f, 8f, 1));
    private final FloatSetting buildUp = addSetting(
        new FloatSetting("Build Up", "CPS gained per tick while ramping UP to target.", 0.1f, 5f, 1f, 1));
    private final FloatSetting buildDown = addSetting(
        new FloatSetting("Build Down", "CPS lost per tick while ramping DOWN toward target.", 0.1f, 5f, 1.5f, 1));
    private final BooleanSetting respectCooldown = addSetting(
        new BooleanSetting("Respect Cooldown", "Only click when the vanilla attack cooldown is sufficiently charged.", true));
    private final FloatSetting cooldownThreshold = addSetting(
        new FloatSetting("Cooldown Threshold", "Minimum attack-cooldown progress (0–1) required to click.", 0f, 1f, 0.9f, 2));
    private final BooleanSetting humanize = addSetting(
        new BooleanSetting("Humanize", "Add a small random variation to the target CPS each tick.", true));

    private final Random rng = new Random();

    // ── State ────────────────────────────────────────────────────
    private float currentCPS = 0f;        // smoothed CPS (ramps toward target)
    private float humanizedTargetCPS = 0f; // target with humanization noise applied
    private float clickAccumulator = 0f;   // fraction of a click carried across ticks
    private int humanizeCooldown = 0;       // ticks until next humanize shuffle

    // Toggle key edge detection
    private boolean wasKeyDown = false;

    public AutoClicker() {
        super("AutoClicker", "Auto-clicks attack / break with gradual CPS build-up and humanization.",
            Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        currentCPS = 0f;
        clickAccumulator = 0f;
        humanizedTargetCPS = cps.get();
        wasKeyDown = false;
    }

    @Override
    protected void onDisable() {
        currentCPS = 0f;
        clickAccumulator = 0f;
    }

    /**
     * onTickAlways — called for ALL modules regardless of enabled state.
     * Checks the toggle key so a disabled clicker can be re-enabled.
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
        ClientPlayerInteractionManager im = client.interactionManager;
        if (player == null || im == null) return;

        // ── Don't click while a screen is open ────────────────────
        if (client.currentScreen != null) return;

        // ── Update humanized target ────────────────────────────────
        if (humanize.isEnabled()) {
            if (humanizeCooldown <= 0) {
                // ±10% noise on the target CPS, re-shuffled every 5–10 ticks.
                float noise = 1f + (rng.nextFloat() * 0.2f - 0.1f);
                humanizedTargetCPS = cps.get() * noise;
                humanizeCooldown = 5 + rng.nextInt(6);
            } else {
                humanizeCooldown--;
            }
        } else {
            humanizedTargetCPS = cps.get();
        }

        // ── Ramp currentCPS toward the target ──────────────────────
        if (currentCPS < humanizedTargetCPS) {
            currentCPS += buildUp.get();
            if (currentCPS > humanizedTargetCPS) currentCPS = humanizedTargetCPS;
        } else if (currentCPS > humanizedTargetCPS) {
            currentCPS -= buildDown.get();
            if (currentCPS < humanizedTargetCPS) currentCPS = humanizedTargetCPS;
        }
        if (currentCPS < 0f) currentCPS = 0f;

        // ── Accumulate click fraction (1.0 = click this tick) ──────
        // 20 ticks per second, so clicks-per-tick = currentCPS / 20.
        clickAccumulator += currentCPS / 20f;
        if (clickAccumulator < 1f) return;

        // Only fire one click per tick (extra fraction carries over).
        clickAccumulator -= 1f;

        // ── Respect Cooldown ───────────────────────────────────────
        // Vanilla 1.9+ combat gates swings on this; we honour it when enabled.
        if (respectCooldown.isEnabled()) {
            float progress = player.getAttackCooldownProgress(0.5f);
            if (progress < cooldownThreshold.get()) return;
        }

        // While AutoClicker owns the clicking, suppress the vanilla attack
        // key — a physically-held click would stack a SECOND attack cadence
        // on top of ours (impossible combined CPS = instant flag).
        if (client.options.attackKey.isPressed()) {
            client.options.attackKey.setPressed(false);
        }
        performClick(client, player, im);
    }

    private static final boolean DEBUG = true;

    private void dbg(String msg) {
        if (DEBUG) LucidClient.LOGGER.info("[AutoClicker-DBG] " + msg);
    }

    /**
     * Performs a single left-click:
     *   - If looking at an entity → attackEntity + swingHand.
     *   - If looking at a block    → updateBlockBreakingProgress.
     *   - Otherwise (air)          → swingHand only.
     *
     * We bypass MinecraftClient.doAttack entirely so we control the
     * cooldown gate ourselves.
     */
    private void performClick(MinecraftClient client, ClientPlayerEntity player,
                              ClientPlayerInteractionManager im) {
        HitResult hit = client.crosshairTarget;
        if (hit == null) {
            player.swingHand(Hand.MAIN_HAND);
            return;
        }
        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult ehr) {
            Entity target = ehr.getEntity();
            if (target == null || !target.isAlive()) return;
            dbg("ATTACK-ENTITY target=" + target.getName().getString() + " cd=" + player.getAttackCooldownProgress(0.5f) + " slot=" + player.getInventory().getSelectedSlot());
            im.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
        } else if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
            im.updateBlockBreakingProgress(bhr.getBlockPos(), bhr.getSide());
            player.swingHand(Hand.MAIN_HAND);
        } else {
            // MISS / air
            player.swingHand(Hand.MAIN_HAND);
        }
    }
}
