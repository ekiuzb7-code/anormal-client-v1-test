package com.anormal.client.module.impl.combat;

import com.anormal.client.lucid.LucidClient;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.lucid.FloatSetting;
import com.anormal.client.lucid.IntSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Shield Breaker — auto-disable an opponent's shield by swapping to an axe for
 * the hit, then swapping back to your main weapon.
 *
 * ── How it works ───────────────────────────────────────────────
 * The exact moment you strike an opponent who is actively raising and blocking
 * with a shield, the module:
 *   1. Swaps your hotbar slot to an axe (the vanilla tool that disables
 *      shields — a sword or mace hit will NOT break a shield).
 *   2. Performs the axe hit via {@link ClientPlayerInteractionManager#attackEntity}.
 *   3. (Optional) If "Stun Slam" is on and a mace is in the hotbar, swaps to the
 *      mace and performs a follow-up smash on the same target.
 *   4. Swaps back to your original hotbar slot (sword/mace/whatever you held).
 *
 * The whole cycle is a few ticks — fast enough that you resume your normal
 * attack pattern without manually fumbling through the hotbar.
 *
 * ── State machine (modeled on AnchorMacro) ─────────────────────
 *   IDLE → SWAP_TO_AXE → AXE_HIT → (STUN_SLAM →) SWITCH_BACK → IDLE
 *
 * Tick staggering + pre-selection (the AnchorMacro race-condition fix): the
 * slot for each phase is PRE-SELECTED at the end of the previous phase (and the
 * first phase's slot is pre-selected in the IDLE trigger). This gives the
 * server a full {@code delay} ticks to process the slot-change packet before
 * the attack packet arrives — preventing the "wrong item used" race where the
 * attack lands with the previous weapon instead of the axe.
 *
 * ── Attack + cooldown (modeled on AutoClicker) ─────────────────
 * The attack bypasses {@code MinecraftClient.doAttack} entirely and calls
 * {@code interactionManager.attackEntity(player, target)} +
 * {@code player.swingHand(Hand.MAIN_HAND)} directly, so the module owns the
 * cooldown gate. When "Respect Cooldown" is on the axe hit waits for
 * {@code player.getAttackCooldownProgress(0.5f) >= cooldownThreshold} so it
 * lands at full charge (clean shield break + max damage). A partial axe hit
 * still disables shields in vanilla, but full-charge is cleaner.
 *
 * ── Trigger gating ─────────────────────────────────────────────
 * "Only while attacking": fires only when the player's left mouse is held
 * ({@code mc.options.attackKey.isPressed()}) and the crosshair target is a
 * living entity that {@link LivingEntity#isBlocking()} — i.e. actively raising
 * a shield. Avoids firing on passive mobs or out-of-combat situations.
 *
 * Settings:
 *   Delay, Switch Back, Switch Back Delay, Cooldown, Respect Cooldown,
 *   Cooldown Threshold, Stun Slam, Slam Delay, Players Only
 */
public class ShieldBreakerV2 extends Module {

    // ── Settings ──────────────────────────────────────────────
    private final IntSetting delay = addSetting(
        new IntSetting("Delay", "Ticks between each phase (swap → hit → slam/switchback). 1 = fastest (50ms). 2+ is safer vs anti-cheat.", 1, 5, 2, 1));
    private final BooleanSetting switchBack = addSetting(
        new BooleanSetting("Switch Back", "Restore your original hotbar slot after the axe hit.", true));
    private final IntSetting switchBackDelay = addSetting(
        new IntSetting("Switch Back Delay", "Ticks to wait after the final hit before switching back. Lets the hit register while the axe/mace is still held.", 0, 10, 2, 1));
    private final IntSetting cooldown = addSetting(
        new IntSetting("Cooldown", "Ticks to wait after a cycle before auto-retriggering on a still-blocking enemy.", 0, 40, 8, 1));
    private final BooleanSetting respectCooldown = addSetting(
        new BooleanSetting("Respect Cooldown", "Only fire the axe hit when the attack cooldown is charged, so it lands at full power (clean shield break).", true));
    private final FloatSetting cooldownThreshold = addSetting(
        new FloatSetting("Cooldown Threshold", "Minimum attack-cooldown progress before the axe hit. 1.0 = full charge.", 0.1f, 1.0f, 1.0f, 2));
    private final BooleanSetting stunSlam = addSetting(
        new BooleanSetting("Stun Slam", "After the axe breaks the shield, swap to a mace and perform a falling smash on the same target (requires a mace in the hotbar).", false));
    private final IntSetting slamDelay = addSetting(
        new IntSetting("Slam Delay", "Ticks between the axe hit and the mace smash.", 1, 10, 2, 1));
    private final BooleanSetting playersOnly = addSetting(
        new BooleanSetting("Players Only", "Only trigger on other players (skip mobs/animals).", true));
    private final BooleanSetting workWithTriggerBot = addSetting(
        new BooleanSetting("Work With Triggerbot", "Also trigger when Triggerbot attacks a blocking enemy, even without left-click held. Requires Triggerbot enabled.", true));

    // ── State machine ────────────────────────────────────────
    private enum State { IDLE, SWAP_TO_AXE, AXE_HIT, STUN_SLAM, SWITCH_BACK }

    private State state = State.IDLE;
    private int ticksInState = 0;
    private int totalTicks = 0;
    private static final int MAX_TOTAL_TICKS = 60; // 3s safety timeout (cycle is normally <1s)

    // Cooldown after a cycle — prevents instant re-trigger on a still-blocking enemy.
    private int cooldownTicks = 0;

    // Inventory slots (resolved at trigger time).
    private int originalSlot = -1;
    private int axeSlot = -1;
    private int maceSlot = -1;     // -1 = no mace / slam disabled for this run
    private boolean slamPlanned = false;

    // The target locked at trigger time (re-verified each phase).
    private LivingEntity target = null;

    // ── Work With Triggerbot state ──────────────────────────────
    // Wall-clock ms of the last Triggerbot attack we've already handled. Lets
    // us detect "Triggerbot just attacked a blocker we haven't reacted to"
    // without re-triggering on the same attack every tick.
    private long lastHandledTriggerBotAttackMs = 0L;

    public ShieldBreakerV2() {
        super("ShieldBreaker V2", "Auto-swaps to an axe to break a blocking enemy's shield, then swaps back. Optional mace stun-slam.",
            Category.COMBAT);
    }

    @Override
    public void onEnable() { resetState(); }

    @Override
    public void onDisable() { resetState(); }

    private void resetState() {
        state = State.IDLE;
        ticksInState = 0;
        totalTicks = 0;
        originalSlot = -1;
        axeSlot = -1;
        maceSlot = -1;
        slamPlanned = false;
        target = null;
        // Arm the post-completion cooldown so the module doesn't instantly
        // re-fire on the same still-blocking enemy the tick after a cycle.
        cooldownTicks = cooldown.get();
    }

    // ════════════════════════════════════════════════════════════════
    //  TICK — auto-trigger + advance the state machine (20Hz)
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick() {
        // ── Auto-trigger check (only when idle) ─────────────────
        if (state == State.IDLE) {
            handleAutoTrigger();
            return;
        }
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            resetState();
            return;
        }

        // While OUR cycle owns the hits, suppress the vanilla attack key —
        // the player is holding left-click (that's the trigger), so vanilla
        // doAttack would fire an ADDITIONAL attack on top of our axe/slam
        // attackEntity in the same window. Attack packets at impossible
        // click rates are an instant anticheat flag. Same takeover pattern
        // AnchorMacro uses for the use key.
        if (mc.options.attackKey.isPressed()) {
            mc.options.attackKey.setPressed(false);
        }

        // Safety timeout — never get permanently stuck.
        totalTicks++;
        if (totalTicks > MAX_TOTAL_TICKS) {
            resetState();
            return;
        }

        ticksInState++;
        ClientPlayerEntity player = mc.player;
        ClientPlayerInteractionManager im = mc.interactionManager;

        switch (state) {
            // ── State 1: Wait for the slot-swap to settle, then go hit ──
            case SWAP_TO_AXE: {
                if (ticksInState < delay.get()) return;
                // Slot (axe) was pre-selected in handleAutoTrigger. The server
                // has now had a full `delay` ticks to process it.
                state = State.AXE_HIT;
                ticksInState = 0;
                break;
            }
            // ── State 2: Perform the axe hit (shield-break) ───────────
            case AXE_HIT: {
                if (ticksInState < delay.get()) return;

                LivingEntity t = revalidateTarget();
                if (t == null) {
                    goToSwitchBack(player);
                    break;
                }
                target = t;

                // Cooldown gate — full-charge axe hit cleanly disables shields.
                if (respectCooldown.isEnabled()) {
                    float progress = player.getAttackCooldownProgress(0.5f);
                    if (progress < cooldownThreshold.get()) return; // wait in-state
                }

                dbg("ATTACK [AXE_HIT] target=" + target.getName().getString() + " cd=" + player.getAttackCooldownProgress(0.5f) + " slot=" + player.getInventory().getSelectedSlot() + " hand=" + player.getMainHandStack().getItem());
                im.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);

                if (slamPlanned && maceSlot >= 0) {
                    // Pre-select the mace for STUN_SLAM — gives the server a
                    // full slamDelay to process the swap before the smash.
                    selectSlot(player, maceSlot);
                    state = State.STUN_SLAM;
                } else {
                    goToSwitchBack(player);
                }
                ticksInState = 0;
                break;
            }
            // ── State 3: Stun Slam — mace follow-up smash ─────────────
            case STUN_SLAM: {
                if (ticksInState < slamDelay.get()) return;

                // NOTE: the axe hit in AXE_HIT already BROKE the shield, so the
                // target is no longer blocking here. We must NOT require
                // isBlocking() (revalidateTarget does, which bails ~62% of the
                // time and is why the slam only worked 38% of the time). We just
                // need the locked target to still be alive — then slam it.
                LivingEntity t = revalidateTargetForSlam();
                if (t == null) {
                    goToSwitchBack(player);
                    break;
                }
                target = t;

                // Same cooldown gate for the mace hit.
                if (respectCooldown.isEnabled()) {
                    float progress = player.getAttackCooldownProgress(0.5f);
                    if (progress < cooldownThreshold.get()) return; // wait in-state
                }

                dbg("ATTACK [STUN_SLAM] target=" + target.getName().getString() + " cd=" + player.getAttackCooldownProgress(0.5f) + " slot=" + player.getInventory().getSelectedSlot() + " hand=" + player.getMainHandStack().getItem());
                im.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);
                goToSwitchBack(player);
                ticksInState = 0;
                break;
            }
            // ── State 4: Restore the original hotbar slot ─────────────
            case SWITCH_BACK: {
                if (ticksInState < switchBackDelay.get()) return;
                if (switchBack.isEnabled() && originalSlot >= 0) {
                    selectSlot(player, originalSlot);
                }
                resetState();
                break;
            }
            default: resetState();
        }
    }

    /** Pre-select the original slot and transition to SWITCH_BACK. */
    private void goToSwitchBack(ClientPlayerEntity player) {
        // Pre-select the original slot now so the server has switchBackDelay
        // ticks to process it before SWITCH_BACK's restore fires.
        if (switchBack.isEnabled() && originalSlot >= 0) {
            selectSlot(player, originalSlot);
        }
        state = State.SWITCH_BACK;
        ticksInState = 0;
    }

    // ════════════════════════════════════════════════════════════════
    //  AUTO-TRIGGER — fires when the player is attacking (left mouse
    //  held) and the crosshair target is a blocking living entity, OR
    //  when "Work With Triggerbot" is on and Triggerbot just attacked a
    //  blocking enemy (Triggerbot calls attackEntity directly, never
    //  pressing the left mouse button, so the attackKey gate alone would
    //  miss it).
    // ════════════════════════════════════════════════════════════════
    private void handleAutoTrigger() {
        // Cooldown — prevents instant re-trigger after a cycle completes.
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return; // don't fire while in menus

        ClientPlayerEntity player = mc.player;

        // ── Trigger source ───────────────────────────────────────────
        // Two ways to fire:
        //   (a) "Only while attacking" — the player is actively left-clicking.
        //   (b) "Work With Triggerbot" — Triggerbot just attacked a blocking
        //       enemy we haven't reacted to yet. Triggerbot bypasses the
        //       physical left-click (it calls attackEntity directly), so this
        //       path is the only way to chain a shield-break off a Triggerbot hit.
        boolean leftClicking = mc.options.attackKey.isPressed();
        boolean triggerBotJustHit = false;
        if (workWithTriggerBot.isEnabled()) {
            triggerBotJustHit = checkTriggerBotHit();
        }
        if (!leftClicking && !triggerBotJustHit) return;

        // Crosshair target must be a blocking living entity. When Triggerbot
        // just hit, the target is still under the crosshair (Triggerbot only
        // attacks crosshair targets), so this check covers both paths.
        LivingEntity t = getBlockingTarget();
        if (t == null) return;

        // Need an axe in the hotbar — only axe hits disable shields in vanilla.
        int axe = findAxeSlot(player);
        if (axe < 0) {
            return;
        }
        // Don't bother if we're ALREADY holding an axe (nothing to swap to).
        if (player.getInventory().getSelectedSlot() == axe) return;

        // Optional mace for the stun-slam chain.
        int mace = -1;
        boolean slam = false;
        if (stunSlam.isEnabled()) {
            mace = findMaceSlot(player);
            if (mace >= 0) slam = true;
        }

        // Lock in the run.
        originalSlot = player.getInventory().getSelectedSlot();
        axeSlot = axe;
        maceSlot = mace;
        slamPlanned = slam;
        dbg("CYCLE START target=" + t.getName().getString() + " axe=" + axe + (slam ? " slam=" + mace : ""));
        target = t;

        // Pre-select the axe NOW — gives the server a full `delay` ticks to
        // process the slot-change packet before AXE_HIT's attack fires. This is
        // the AnchorMacro race-condition fix: prevents the attack landing with
        // the sword instead of the axe.
        selectSlot(player, axeSlot);

        state = State.SWAP_TO_AXE;
        ticksInState = 0;
        totalTicks = 0;
    }

    /**
     * Re-validate the locked target for the post-shield-break STUN_SLAM phase.
     *
     * <p>Unlike {@link #revalidateTarget}, this does NOT require
     * {@code isBlocking()} — by the time STUN_SLAM runs, the axe hit has already
     * broken the shield, so the target is no longer blocking. Requiring it (as
     * the old code did) made the slam bail ~62% of the time (the 38% success
     * rate). Here we only need the locked target to still be alive and under the
     * crosshair, or — as a fallback — any valid living entity under the
     * crosshair (the slam should fire regardless of shield state).
     *
     * @return the target to slam, or null if the target is gone
     */
    private LivingEntity revalidateTargetForSlam() {
        // Prefer the SAME entity we locked (so a second entity crossing the
        // crosshair mid-cycle doesn't redirect the slam). Only requirement:
        // still alive — the shield is already broken by this point.
        if (target != null && target.isAlive() && !target.isRemoved()) {
            // Still alive — slam it whether or not it's still under the
            // crosshair (the axe knockback can move it out of the crosshair
            // but it's still a valid attack target via the locked reference).
            if (target instanceof PlayerEntity || !playersOnly.isEnabled()) {
                return target;
            }
        }
        // Locked target is gone — fall back to whatever living entity is under
        // the crosshair (no isBlocking requirement — we're past the break).
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) return null;
        if (!(hit instanceof EntityHitResult ehr)) return null;
        Entity e = ehr.getEntity();
        if (e == null || !(e instanceof LivingEntity le)) return null;
        if (!le.isAlive() || le.isRemoved()) return null;
        if (playersOnly.isEnabled() && !(le instanceof PlayerEntity)) return null;
        if (le == mc.player) return null; // never target ourselves
        return le;
    }

    /**
     * Re-validate the locked target for the current phase. Returns the target
     * if it's still the crosshair entity, still alive, and still blocking;
     * otherwise null (the caller bails to SWITCH_BACK so the slot is restored).
     */
    private LivingEntity revalidateTarget() {
        LivingEntity t = getBlockingTarget();
        if (t == null) return null;
        // Prefer the SAME entity we locked (so a second entity crossing the
        // crosshair mid-cycle doesn't redirect the slam). If the locked target
        // is no longer under the crosshair, fall back to whatever valid
        // blocker is there — but only if it's a different, still-valid entity.
        if (target != null && target.isAlive() && target.isBlocking()) {
            return target;
        }
        return t;
    }

    /**
     * Read the crosshair target and return it as a {@link LivingEntity} if it's
     * an entity hit on a living, blocking entity (and, when Players Only is on,
     * a {@link PlayerEntity}). Otherwise null.
     */
    private LivingEntity getBlockingTarget() {
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) return null;
        if (!(hit instanceof EntityHitResult ehr)) return null;
        Entity e = ehr.getEntity();
        if (e == null || !(e instanceof LivingEntity le)) return null;
        if (!le.isAlive() || le.isRemoved()) return null;
        if (!le.isBlocking()) return null; // actively raising a shield
        if (playersOnly.isEnabled() && !(le instanceof PlayerEntity)) return null;
        if (le == mc.player) return null; // never target ourselves
        return le;
    }

    // ════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════
    private void selectSlot(ClientPlayerEntity player, int slot) {
        if (slot < 0 || slot > 8) return;
        PlayerInventory inv = player.getInventory();
        if (inv.getSelectedSlot() != slot) {
            inv.setSelectedSlot(slot); // vanilla sends its own slot packet — never duplicate it
        }
    }

    /** First hotbar slot (0..8) holding an axe, or -1 if none. */
    private int findAxeSlot(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.isIn(ItemTags.AXES)) return i;
        }
        return -1;
    }

    /** First hotbar slot (0..8) holding a mace, or -1 if none. */
    private int findMaceSlot(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() == Items.MACE) return i;
        }
        return -1;
    }

    /**
     * Check whether Triggerbot has just attacked a blocking enemy we haven't
     * reacted to yet. Returns true once per such attack (subsequent ticks for
     * the same attack return false until a new attack lands). Marks the attack
     * as handled so we don't re-trigger on it.
     *
     * Triggerbot bypasses the physical left-click (it calls attackEntity
     * directly), so the normal attackKey gate can't detect its hits — this is
     * the bridge that lets ShieldBreaker chain off a Triggerbot hit.
     */
    private boolean checkTriggerBotHit() {
        LucidClient instance = LucidClient.getInstance();
        if (instance == null || instance.getModuleManager() == null) return false;
        Triggerbot tb = instance.getModuleManager().getModule(Triggerbot.class);
        if (tb == null || !tb.isEnabled()) return false;

        long lastAttack = tb.getLastAttackMs();
        if (lastAttack <= lastHandledTriggerBotAttackMs) return false; // already handled / no new attack

        // The entity Triggerbot just attacked must still be a blocking target.
        // (Triggerbot only attacks crosshair entities, and shields stay up
        // until an axe hit disables them, so the target should still be
        // blocking right after a Triggerbot sword/mace hit.)
        Entity attacked = tb.getLastAttackedEntity();
        if (attacked == null || !(attacked instanceof LivingEntity le)) {
            lastHandledTriggerBotAttackMs = lastAttack; // consume even if unusable
            return false;
        }
        if (!le.isAlive() || le.isRemoved() || !le.isBlocking()) {
            lastHandledTriggerBotAttackMs = lastAttack;
            return false;
        }
        if (playersOnly.isEnabled() && !(le instanceof PlayerEntity)) {
            lastHandledTriggerBotAttackMs = lastAttack;
            return false;
        }
        // New, unhandled Triggerbot attack on a valid blocker — consume + fire.
        lastHandledTriggerBotAttackMs = lastAttack;
        return true;
    }

    private static final boolean DEBUG = true;

    private void dbg(String msg) {
        if (DEBUG) com.anormal.client.lucid.LucidLog.LOGGER.info("[ShieldBreaker-DBG] " + msg);
    }

    // ── Public state for UI / keybind hooks ─────────────────
    public boolean isRunning() { return state != State.IDLE; }
    public String getStateName() { return state.name(); }
}