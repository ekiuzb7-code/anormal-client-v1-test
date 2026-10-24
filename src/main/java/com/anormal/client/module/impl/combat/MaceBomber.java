package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.lucid.FloatSetting;
import com.anormal.client.lucid.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;

import java.util.List;

/**
 * MaceBomber — smash-timing assist for mace dive attacks.
 *
 * YOU fly manually (elytra up, cut the glide, dive); the module watches your
 * fall and does the mechanical part at the perfect moment.
 *
 * ── Early-arm design (v1.14.8 — fixes the v1.14.6 timing bug) ────
 * The old version armed only when the target was already inside strike
 * range — a ~4-tick window at dive speed — and then waited ~12 ticks for
 * the attack cooldown to recharge after the item swap. The swing could
 * never charge before impact, so every dive silently aborted (and most
 * dives never even triggered).
 *
 * Now the sequence is:
 *
 *   1. ARM (high up): while free-falling (NOT gliding — the smash needs a
 *      real fall) with at least `Min Fall` blocks of fall distance, and a
 *      living player is below you within `Arm Radius` blocks of your
 *      hitbox, the mace goes into your hand — plain hotbar select if it's
 *      in the hotbar, or one SWAP slot-click packet if it's deeper in the
 *      inventory (the same packet pressing 1-9 in the grid sends).
 *
 *   2. CHARGE (during the dive): the vanilla attack cooldown recharges
 *      while you fall — smash damage scales with the charged swing, and
 *      the item swap resets it, so arming early is what makes a full
 *      swing possible at all. Fall distance only grows meanwhile.
 *
 *   3. STRIKE (the instant it's valid): the moment the target is inside
 *      `Range` blocks of your hitbox AND (optionally) the swing is fully
 *      charged — {@code attackEntity} + swing, one attack packet at
 *      maximum fall distance, a heartbeat before impact.
 *
 *   4. RESTORE: swaps the mace back to where it was and re-selects your
 *      previous hotbar slot.
 *
 * ── Anti-cheat profile ───────────────────────────────────────────
 * The whole chain is ordinary packets: a hotbar select (or one inventory
 * SWAP), an attack at vanilla-ish melee reach (default 3.5), and a select
 * back — no movement packets, no rotation spoofing.
 *
 * ── Combos with ElytraSwap ───────────────────────────────────────
 * ElytraSwap handles the armor; this handles the weapon + the hit. Cut
 * the glide above your target and free-fall — the arm happens high, the
 * smash happens at the bottom.
 *
 * Settings:
 *   Arm Radius, Range, Min Fall, Cooldown, Wait Full Swing, Swap Back
 */
public class MaceBomber extends Module {

    /** How far (to-hitbox) a player below you can be for the dive to ARM. */
    private final FloatSetting armRadius = addSetting(
        new FloatSetting("Arm Radius", "Distance (blocks) at which a falling dive ARMS — swaps to the mace early so the swing recharges mid-fall. Should be well above Range.",
            2f, 32f, 12f, 1));
    private final FloatSetting range = addSetting(
        new FloatSetting("Range", "Strike reach — the smash fires the moment the target's hitbox is this close. Keep near vanilla melee reach for anti-cheat safety.",
            1f, 6f, 3.5f, 1));
    private final FloatSetting minFall = addSetting(
        new FloatSetting("Min Fall", "Fall distance (blocks) required before the dive can arm — the mace smash bonus scales with fall distance.",
            1.5f, 30f, 5f, 1));
    private final IntSetting cooldownTicks = addSetting(
        new IntSetting("Cooldown", "Ticks to wait after a smash before re-arming.", 0, 40, 10, 1));
    private final BooleanSetting waitFullSwing = addSetting(
        new BooleanSetting("Wait Full Swing", "Only strike when the attack cooldown is fully charged (smash damage scales with it). Arming early makes this nearly always ready.",
            true));
    private final BooleanSetting swapBack = addSetting(
        new BooleanSetting("Swap Back", "Return the mace to its original place and re-select your previous hotbar slot after the smash.",
            true));
    /** Test/convenience toggle: also target mobs. Off = players only (the
     *  default DonutSMP pvp configuration). On = any living mob counts as a
     *  valid target, which makes the module testable in a singleplayer
     *  world (summon a zombie, dive onto it). */
    private final BooleanSetting targetMobs = addSetting(
        new BooleanSetting("Target Mobs", "Also target living mobs (zombies etc.) instead of players only — lets you test the dive in a singleplayer world.",
            false));

    /** Hard cap on the whole ARMED dive — a 5s free-fall is longer than any real dive. */
    private static final int MAX_ARMED_TICKS = 100;
    /** Ticks to wait for the mace swap to show up in the mc hand. */
    private static final int MAX_SWAP_WAIT_TICKS = 10;

    // ── State machine ────────────────────────────────────────
    private enum State { IDLE, ARMED, STRIKE, RESTORE }

    private State state = State.IDLE;
    private int ticksInState = 0;
    private int cooldown = 0;

    // The locked target (re-checked each tick while ARMED).
    private LivingEntity target = null;

    // Swap-back bookkeeping (captured at arm time).
    private int originalSlot = -1;
    /** True if the mace was brought in from the main inventory via a SWAP click. */
    private boolean usedInvSwap = false;
    /** The main-inventory slot the mace came from (for the reverse swap). */
    private int maceInvSlot = -1;

    public MaceBomber() {
        super("MaceBomber", "Mace smash assist — arms the mace when you dive, strikes the player below at maximum fall damage.",
            Category.COMBAT);
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        // Mid-sequence disable: undo the mace swap so the inventory is left
        // exactly as it was found.
        restoreSwap();
        resetState();
    }

    private void resetState() {
        state = State.IDLE;
        ticksInState = 0;
        target = null;
        originalSlot = -1;
        usedInvSwap = false;
        maceInvSlot = -1;
    }

    // ════════════════════════════════════════════════════════════════
    //  onTick — trigger detection + smash sequencing (20Hz)
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick() {
        if (cooldown > 0) cooldown--;

        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
            if (state != State.IDLE) { restoreSwap(); resetState(); }
            return;
        }

        ClientPlayerEntity player = mc.player;
        ClientPlayerInteractionManager im = mc.interactionManager;
        ticksInState++;

        switch (state) {
            case IDLE: {
                if (mc.currentScreen != null) return;
                if (cooldown > 0) return;

                // ── Trigger: free-falling with enough fall distance ──
                // NOT gliding (gliding cancels the fall / the smash), not
                // in water or on a ladder, not on the ground.
                if (player.isOnGround() || player.isGliding()
                        || player.isTouchingWater() || player.isClimbing()) return;
                if (player.fallDistance < minFall.get()) return;

                // Arm against the nearest player below within the ARM
                // RADIUS (generous — the swap happens high up, and the
                // swing recharges during the rest of the dive).
                LivingEntity t = findTarget(player, mc.world, armRadius.get());
                if (t == null) return;

                if (arm(mc, player, im, t)) {
                    state = State.ARMED;
                    ticksInState = 0;
                }
                break;
            }
            case ARMED: {
                // ── Dive abort conditions ──────────────────────────
                if (player.isOnGround() || player.isTouchingWater()
                        || player.isClimbing() || player.isGliding()) {
                    // Landed / bailed / re-engaged the glide — dive over.
                    abort(mc, null);
                    return;
                }
                if (!target.isAlive() || target.isRemoved() || target.isSpectator()) {
                    abort(mc, "Target lost");
                    return;
                }
                if (ticksInState > MAX_ARMED_TICKS) {
                    abort(mc, "Dive timed out");
                    return;
                }
                // The mace must actually be in hand on the mc before we
                // swing (swap-sync guard, same idea as KeyPearl).
                if (player.getMainHandStack().getItem() != Items.MACE) {
                    if (ticksInState > MAX_SWAP_WAIT_TICKS) {
                        abort(mc, "Mace swap didn't sync");
                    }
                    return;
                }

                // ── Strike conditions ─────────────────────────────
                double distSq = distToBoxSq(player, target.getBoundingBox());
                boolean inRange = distSq <= (double) range.get() * range.get();
                boolean charged = !waitFullSwing.isEnabled()
                        || player.getAttackCooldownProgress(0.5f) >= 0.999f;
                if (!inRange || !charged) return; // keep diving — still armed

                // ── STRIKE: one attack at max fall distance ──
                im.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);
                cooldown = cooldownTicks.get();
                state = State.STRIKE;
                ticksInState = 0;
                break;
            }
            case STRIKE: {
                // One tick for the attack packet to flush.
                if (ticksInState >= 1) {
                    state = State.RESTORE;
                    ticksInState = 0;
                }
                break;
            }
            case RESTORE: {
                if (ticksInState >= 3) {
                    if (swapBack.isEnabled()) restoreSwap();
                    resetState();
                }
                break;
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Targeting — nearest valid target within a radius, below the player
    //  (players by default; living mobs too when Target Mobs is on)
    // ════════════════════════════════════════════════════════════════
    private LivingEntity findTarget(ClientPlayerEntity player, ClientWorld world, double r) {
        double rSq = r * r;

        // Broad-phase: AABB search box around the player (AimAssist pattern).
        Box searchBox = new Box(
            player.getX() - r, player.getY() - r, player.getZ() - r,
            player.getX() + r, player.getY() + r, player.getZ() + r);
        boolean mobs = targetMobs.isEnabled();
        List<Entity> candidates = world.getOtherEntities(player, searchBox, e ->
            (e instanceof PlayerEntity
                || (mobs && e instanceof LivingEntity && !(e instanceof ArmorStandEntity)))
            && e.isAlive() && !e.isRemoved() && !e.isSpectator());
        if (candidates.isEmpty()) return null;

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity e : candidates) {
            LivingEntity other = (LivingEntity) e;

            // Dive geometry: the target's bounding-box TOP must be at (or
            // barely above) the player's feet — we smash what's beneath us.
            Box box = other.getBoundingBox();
            if (box.maxY > player.getY() + 1.5) continue;

            double distSq = distToBoxSq(player, box);
            if (distSq > rSq) continue;

            if (distSq < bestDist) {
                bestDist = distSq;
                best = other;
            }
        }
        return best;
    }

    /**
     * Squared distance from the player's feet position to an AABB — each
     * axis clamped to the box, the standard melee-reach "distance to
     * hitbox" measure.
     */
    private static double distToBoxSq(ClientPlayerEntity player, Box box) {
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        double dx = Math.max(Math.max(box.minX - px, 0.0), px - box.maxX);
        double dy = Math.max(Math.max(box.minY - py, 0.0), py - box.maxY);
        double dz = Math.max(Math.max(box.minZ - pz, 0.0), pz - box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    // ════════════════════════════════════════════════════════════════
    //  Arm — get the mace into the player's hand
    // ════════════════════════════════════════════════════════════════
    private boolean arm(MinecraftClient mc, ClientPlayerEntity player,
                        ClientPlayerInteractionManager im, LivingEntity t) {
        this.target = t;

        // Already holding the mace — nothing to swap.
        if (player.getMainHandStack().getItem() == Items.MACE) {
            originalSlot = -1;
            usedInvSwap = false;
            maceInvSlot = -1;
            return true;
        }

        PlayerInventory inv = player.getInventory();
        originalSlot = inv.getSelectedSlot();
        int syncId = player.currentScreenHandler.syncId;

        // Hotbar: plain select.
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() == Items.MACE) {
                selectSlot(player, i);
                usedInvSwap = false;
                maceInvSlot = -1;
                return true;
            }
        }
        // Main inventory: one SWAP click exchanges the mace with whatever is
        // in the selected hotbar slot (the packet pressing 1-9 sends).
        for (int i = 9; i < 36; i++) {
            if (inv.getStack(i).getItem() == Items.MACE) {
                im.clickSlot(syncId, i, inv.getSelectedSlot(), SlotActionType.SWAP, player);
                usedInvSwap = true;
                maceInvSlot = i;
                return true;
            }
        }
        return false; // no mace anywhere — don't arm
    }

    /** Undo the mace swap: reverse the inventory SWAP or re-select the old hotbar slot. */
    private void restoreSwap() {
        if (mc.player == null) return;
        ClientPlayerEntity player = mc.player;
        if (usedInvSwap && maceInvSlot >= 9 && maceInvSlot < 36
                && mc.interactionManager != null) {
            // Reverse swap: mace goes back to its inventory slot, the
            // previously held item returns to the hand.
            mc.interactionManager.clickSlot(
                player.currentScreenHandler.syncId, maceInvSlot,
                player.getInventory().getSelectedSlot(), SlotActionType.SWAP, player);
        } else if (originalSlot >= 0 && originalSlot <= 8) {
            selectSlot(player, originalSlot);
        }
        originalSlot = -1;
        usedInvSwap = false;
        maceInvSlot = -1;
    }

    /** Hotbar select with the explicit slot packet (KeyPearl pattern). */
    private void selectSlot(ClientPlayerEntity player, int slot) {
        if (slot < 0 || slot > 8) return;
        PlayerInventory inv = player.getInventory();
        if (inv.getSelectedSlot() == slot) return;
        inv.setSelectedSlot(slot); // vanilla sends its own slot packet — never duplicate it
    }

    private void abort(MinecraftClient mc, String reason) {
        restoreSwap();
        resetState();
    }
}
