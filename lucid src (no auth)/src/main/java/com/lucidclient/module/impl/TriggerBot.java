package com.lucidclient.module.impl;

import com.lucidclient.LucidClient;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Random;

/**
 * TriggerBot — auto-attacks whatever entity the player's crosshair is already
 * on, once all configured filters pass.
 *
 * ── Key distinction from an aura ───────────────────────────────
 * This module does NOT scan for nearby enemies. It relies entirely on
 * Minecraft's crosshair raycast ({@code client.crosshairTarget}) to determine
 * whether the player is looking directly at a target. It only reacts AFTER the
 * player has aimed — it never steers the view.
 *
 * ── Decision pipeline (per spec) ───────────────────────────────
 *   Module enabled?
 *     → Player in-game + screen closed?
 *       → Crosshair intersecting an ENTITY?
 *         → Entity passes filters (players/hostiles/passives/invisible/
 *           distance/line-of-sight)?
 *           → Weapon requirement met?
 *             → Activation gates (attack button / using item)?
 *               → Cooldown requirement met?
 *                 → Configured delay elapsed?
 *                   → Attack
 *
 * Every condition must be true in order. If ANY check fails, the acquire timer
 * resets and the pipeline restarts from the top next tick — the configured
 * delay is only counted while ALL conditions stay continuously true.
 *
 * ── Attack (modeled on AutoClicker) ────────────────────────────
 * Bypasses {@code MinecraftClient.doAttack} and calls
 * {@code interactionManager.attackEntity(player, target)} +
 * {@code player.swingHand(Hand.MAIN_HAND)} directly, so the module owns the
 * cooldown gate. No block-breaking, no air-swings — TriggerBot only ever
 * attacks entities (the crosshair must be on one).
 *
 * ── Filters (modeled on AimAssist) ─────────────────────────────
 * Players / Hostiles / Passives toggle which entity classes are valid.
 * Ignore Invisible, Max Distance, and Require Line of Sight add extra guards.
 * Line of sight reuses AimAssist's raycast helper (block-hit distance vs
 * target distance) so a target behind a wall doesn't trigger.
 *
 * ── Cooldown ───────────────────────────────────────────────────
 * Vanilla 1.9+ combat uses an attack-cooldown that affects damage. With
 * "Respect Cooldown" on, the module waits until
 * {@code player.getAttackCooldownProgress(0.5f) >= cooldownThreshold} before
 * attacking, so each hit lands at full strength. The cooldown itself naturally
 * paces successive hits; the Delay setting is an ADDITIONAL front-door wait
 * applied after the crosshair first lands on a valid target.
 *
 * ── Delay ──────────────────────────────────────────────────────
 * Rather than attacking the instant a target appears under the crosshair, the
 * module waits a user-defined number of ticks after conditions become true.
 * Randomized Delay adds a random extra (re-rolled each new acquire) so timing
 * isn't perfectly identical.
 *
 * Settings:
 *   Players, Hostiles, Passives, Ignore Invisible, Max Distance,
 *   Require Line of Sight, Only While Holding Weapon, Only While Attack Button,
 *   Ignore While Using Item, Respect Cooldown, Cooldown Threshold, Delay,
 *   Randomized Delay, Random Delay Range
 */
public class TriggerBot extends Module {

    // ── Target-type filters ─────────────────────────────────────
    private final BooleanSetting players = addSetting(
        new BooleanSetting("Players", "Trigger on other players.", true));
    private final BooleanSetting hostiles = addSetting(
        new BooleanSetting("Hostiles", "Trigger on hostile mobs (zombies, skeletons, etc.).", true));
    private final BooleanSetting passives = addSetting(
        new BooleanSetting("Passives", "Trigger on passive mobs (cows, sheep, etc.).", false));
    private final BooleanSetting ignoreInvisible = addSetting(
        new BooleanSetting("Ignore Invisible", "Skip invisible entities.", true));
    private final FloatSetting maxDistance = addSetting(
        new FloatSetting("Max Distance", "Maximum distance to a trigger target (blocks). Vanilla reach is ~4.5.", 1f, 6f, 4.5f, 1));
    private final BooleanSetting requireLineOfSight = addSetting(
        new BooleanSetting("Require Line of Sight", "Don't trigger on entities behind walls (raycast check).", true));

    // ── Activation gates ────────────────────────────────────────
    private final BooleanSetting onlyWhileHoldingWeapon = addSetting(
        new BooleanSetting("Only While Holding Weapon", "Only fire while holding a sword / axe / mace.", true));
    private final BooleanSetting onlyWhileAttackButton = addSetting(
        new BooleanSetting("Only While Attack Button", "Only fire while the left mouse button (attack) is held.", false));
    private final BooleanSetting ignoreWhileUsingItem = addSetting(
        new BooleanSetting("Ignore While Using Item", "Don't fire while eating, drinking, or blocking with a shield.", true));

    // ── Cooldown ────────────────────────────────────────────────
    private final BooleanSetting respectCooldown = addSetting(
        new BooleanSetting("Respect Cooldown", "Wait until the attack cooldown is charged so each hit lands at full strength.", true));
    private final FloatSetting cooldownThreshold = addSetting(
        new FloatSetting("Cooldown Threshold", "Minimum attack-cooldown progress (0–1) required to attack.", 0f, 1f, 0.9f, 2));

    // ── Delay ───────────────────────────────────────────────────
    private final IntSetting delay = addSetting(
        new IntSetting("Delay", "Extra ticks to wait after the crosshair acquires a valid target before the first attack.", 0, 20, 0, 1));
    private final BooleanSetting randomizedDelay = addSetting(
        new BooleanSetting("Randomized Delay", "Add a random extra delay (re-rolled each acquire) so timing isn't identical.", false));
    private final IntSetting randomDelayRange = addSetting(
        new IntSetting("Random Delay Range", "Max extra ticks added when Randomized Delay is on.", 0, 10, 2, 1));

    private final Random rng = new Random();

    // ── Acquire state ───────────────────────────────────────────
    // Ticks the current target has been continuously "acquired" (all filters
    // passing). Reset the instant any filter fails. The attack only fires once
    // this reaches the (possibly randomized) delay threshold.
    private int acquireTicks = 0;
    private int currentDelayThreshold = 0; // re-rolled per acquire when randomized

    // ── Last-attack state (read by ShieldBreaker for "Work With TriggerBot") ──
    // Set each time performAttack fires. ShieldBreaker uses these to detect
    // "TriggerBot just hit a blocking enemy" and chain the shield-break cycle
    // even though TriggerBot never physically presses the left mouse button.
    private volatile long lastAttackMs = 0L;
    private volatile Entity lastAttackedEntity = null;

    public TriggerBot() {
        super("TriggerBot", "Auto-attacks whatever entity your crosshair is on, when all filters pass.",
            Category.COMBAT);
    }

    @Override
    protected void onEnable() { resetAcquire(); }
    @Override
    protected void onDisable() { resetAcquire(); }

    private void resetAcquire() {
        acquireTicks = 0;
        currentDelayThreshold = 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  TICK — the decision pipeline (20Hz, enabled only)
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick(MinecraftClient client) {
        // ── Guard: player + interaction manager in-game, no screen open ──
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager im = client.interactionManager;
        if (player == null || im == null) { resetAcquire(); return; }
        if (client.currentScreen != null) { resetAcquire(); return; }

        // ── Crosshair must be on an entity (no block-break, no air-swing) ──
        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) { resetAcquire(); return; }
        if (!(hit instanceof EntityHitResult ehr)) { resetAcquire(); return; }
        Entity rawTarget = ehr.getEntity();
        if (rawTarget == null || !(rawTarget instanceof LivingEntity target)) { resetAcquire(); return; }

        // ── Entity filters (Players / Hostiles / Passives / Invisible /
        //    Max Distance / Line of Sight) ────────────────────────────────
        if (!passesFilters(client, player, target)) { resetAcquire(); return; }

        // ── Activation gates (weapon held / attack button / using item) ──
        if (!passesActivation(client, player)) { resetAcquire(); return; }

        // ── Cooldown gate ────────────────────────────────────────────────
        if (respectCooldown.isEnabled()) {
            float progress = player.getAttackCooldownProgress(0.5f);
            if (progress < cooldownThreshold.get()) return; // keep acquire alive
        }

        // ── Delay gate: count acquire ticks, fire once threshold reached ──
        // The threshold is re-rolled each NEW acquire when Randomized Delay is
        // on. acquireTicks == 0 means a fresh acquire → roll now.
        if (acquireTicks == 0) {
            int extra = randomizedDelay.isEnabled() ? rng.nextInt(randomDelayRange.get() + 1) : 0;
            currentDelayThreshold = delay.get() + extra;
        }
        acquireTicks++;
        if (acquireTicks <= currentDelayThreshold) return; // still waiting

        // ── All conditions met + delay elapsed → ATTACK ──────────────────
        performAttack(im, player, target);
        // Reset so the next hit requires a fresh delay to elapse. With Respect
        // Cooldown on, the cooldown itself also paces successive hits.
        resetAcquire();
    }

    /**
     * Entity filters: dead/self checks (always on) + the configurable
     * Players/Hostiles/Passives/Invisible/MaxDistance/LineOfSight gates.
     * Returns true if the entity is a valid trigger target.
     */
    private boolean passesFilters(MinecraftClient client, ClientPlayerEntity player, LivingEntity target) {
        // ── Always-on sanity checks ─────────────────────────────────────
        if (!target.isAlive() || target.isRemoved()) return false;     // ignore dead
        if (target == player) return false;                             // never self

        // ── Entity-type filters ─────────────────────────────────────────
        boolean isPlayer   = target instanceof PlayerEntity;
        boolean isHostile  = target instanceof Monster || target instanceof HostileEntity;
        boolean isPassive  = target instanceof PassiveEntity;
        if (isPlayer  && !players.isEnabled())   return false;
        if (isHostile && !hostiles.isEnabled())  return false;
        if (isPassive && !passives.isEnabled())  return false;
        // If it's none of the three enabled categories, skip it.
        if (!isPlayer && !isHostile && !isPassive) return false;

        // ── Invisible ───────────────────────────────────────────────────
        if (ignoreInvisible.isEnabled() && target.isInvisible()) return false;

        // ── Max distance (extra reach guard on top of the crosshair raycast) ──
        if (player.distanceTo(target) > maxDistance.get()) return false;

        // ── Line of sight ───────────────────────────────────────────────
        if (requireLineOfSight.isEnabled() && client.world != null) {
            Vec3d from = player.getEyePos();
            Vec3d to = target.getEyePos();
            if (!hasLineOfSight(client.world, from, to, target)) return false;
        }
        return true;
    }

    /**
     * Activation gates: weapon held / attack button held / not using an item.
     * Returns true if the player is in a state where attacking is allowed.
     */
    private boolean passesActivation(MinecraftClient client, ClientPlayerEntity player) {
        // ── Weapon requirement ──────────────────────────────────────────
        if (onlyWhileHoldingWeapon.isEnabled()) {
            ItemStack main = player.getMainHandStack();
            if (main.isEmpty()) return false;
            // Mace isn't in the SWORDS/AXES tags in 1.21.x, so check it directly.
            boolean isWeapon = main.isIn(ItemTags.SWORDS)
                || main.isIn(ItemTags.AXES)
                || main.getItem() == Items.MACE;
            if (!isWeapon) return false;
        }
        // ── Attack button ───────────────────────────────────────────────
        if (onlyWhileAttackButton.isEnabled() && !client.options.attackKey.isPressed()) return false;
        // ── Using item (eating / drinking / shield-blocking) ────────────
        if (ignoreWhileUsingItem.isEnabled() && player.isUsingItem()) return false;
        return true;
    }

    private static final boolean DEBUG = true;

    private void dbg(String msg) {
        if (DEBUG) LucidClient.LOGGER.info("[TriggerBot-DBG] " + msg);
    }

    /** The actual attack — AutoClicker's entity branch. */
    private void performAttack(ClientPlayerInteractionManager im, ClientPlayerEntity player, Entity target) {
        dbg("ATTACK target=" + target.getName().getString() + " cd=" + player.getAttackCooldownProgress(0.5f) + " slot=" + player.getInventory().getSelectedSlot());
        im.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        // Record the last attack so ShieldBreaker's "Work With TriggerBot" can
        // detect "TriggerBot just hit a blocking enemy" and chain the break.
        lastAttackMs = System.currentTimeMillis();
        lastAttackedEntity = target;
    }

    // ── Public state for cross-module coordination (ShieldBreaker) ──
    /** Wall-clock millis of the most recent attack TriggerBot performed. */
    public long getLastAttackMs() { return lastAttackMs; }
    /** The entity TriggerBot most recently attacked (may be null/removed). */
    public Entity getLastAttackedEntity() { return lastAttackedEntity; }

    /**
     * Raycast line-of-sight check (copied from AimAssist). Returns true if no
     * block blocks the line from {@code from} to {@code to} (i.e. the block hit
     * is at least as far as the target).
     */
    private boolean hasLineOfSight(ClientWorld world, Vec3d from, Vec3d to, Entity ignored) {
        RaycastContext ctx = new RaycastContext(from, to,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ignored);
        BlockHitResult hit = world.raycast(ctx);
        if (hit == null) return true;
        if (hit.getType() == HitResult.Type.MISS) return true;
        double distToBlock = from.squaredDistanceTo(hit.getPos());
        double distToTarget = from.squaredDistanceTo(to);
        return distToBlock >= distToTarget - 0.5;
    }
}