package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.FloatSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * AutoFirework — automatically spams firework rockets while you're gliding on
 * an elytra to keep you going at max pace.
 *
 * ── The vanilla gliding problem ─────────────────────────────────
 * Elytra flight only maintains speed if you keep re-boosting with firework
 * rockets. Doing that manually means watching your speed and right-clicking
 * on a rhythm — annoying, especially in combat or long flights.
 *
 * ── The optimizer solution ───────────────────────────────────────
 * Every tick we check:
 *   1. The chest slot has an Elytra (equipped).
 *   2. The main hand holds a Firework Rocket.
 *   3. (Optionally) the player is currently gliding ({@code isFallFlying()}).
 * When all conditions are met, we call {@code interactionManager.interactItem}
 * on a configurable delay. Each call consumes a rocket and fires the boost.
 * The delay is what controls the rhythm — too fast and you waste rockets,
 * too slow and you dip below max pace before the next boost.
 *
 * ── Why interactItem, not doItemUse ──────────────────────────────
 * {@code interactItem} sends the use-item packet and triggers the
 * firework-boost code path on the client. We don't need to hold the use key —
 * a single interact per boost is enough; vanilla handles the rest (the rocket
 * is consumed and the boost is applied to the player's velocity).
 *
 * Settings:
 *   Toggle Key, Use Delay, Only While Gliding, Require Elytra Equipped
 */
public class AutoFirework extends Module {

    private final FloatSetting useDelay = addSetting(
        new FloatSetting("Use Delay",
            "Seconds between each firework boost. Lower = faster (wastes more rockets). "
            + "~0.6s is roughly the boost duration of a flight-duration-1 firework.",
            0.1f, 5f, 0.6f, 1));
    private final BooleanSetting onlyWhileGliding = addSetting(
        new BooleanSetting("Only While Gliding",
            "Only fire fireworks while you're actually gliding (not grounded). "
            + "Turn off to fire them on the ground too (useful for takeoff spam).",
            true));
    private final BooleanSetting requireElytra = addSetting(
        new BooleanSetting("Require Elytra Equipped",
            "Only fire when an Elytra is equipped in the chest slot. "
            + "Turn off to spam rockets even without an elytra (creative/jetpack mods).",
            true));

    // Boost cooldown accumulator (ms). We use wall-clock time (not ticks) so
    // the rhythm stays consistent regardless of tick-rate fluctuations.
    private long lastBoostMs = 0L;

    public AutoFirework() {
        super("AutoFirework",
            "Auto-spams firework rockets while gliding on an elytra to keep you at max pace.",
            Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        lastBoostMs = 0L;
    }

    // ════════════════════════════════════════════════════════════════
    //  onTick — check conditions + fire on rhythm (20Hz)
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) return;
        // Don't fire while a screen is open (inventory, chat) — the use would
        // be eaten by the GUI and waste a rocket.
        if (client.currentScreen != null) return;

        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager im = client.interactionManager;

        // ── Condition 1: Elytra equipped (chest slot) ────────────
        if (requireElytra.isEnabled()) {
            ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
            if (chest.getItem() != Items.ELYTRA) return;
        }

        // ── Condition 2: Main hand holds a firework rocket ────────
        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.getItem() != Items.FIREWORK_ROCKET) return;

        // ── Condition 3: gliding (if required) ───────────────────
        if (onlyWhileGliding.isEnabled() && !player.isGliding()) return;

        // ── Rhythm: only fire once per Use Delay ──────────────────
        long now = System.currentTimeMillis();
        long delayMs = (long) (useDelay.get() * 1000);
        if (now - lastBoostMs < delayMs) return;

        // Fire the boost. interactItem sends the use-item packet and
        // triggers the firework boost on the client.
        im.interactItem(player, Hand.MAIN_HAND);
        lastBoostMs = now;
    }
}