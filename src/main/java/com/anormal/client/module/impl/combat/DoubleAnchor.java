package com.anormal.client.module.impl.combat;

import com.anormal.client.mixin.MinecraftClientAccessor;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.lucid.FloatSetting;
import com.anormal.client.lucid.IntSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.explosion.ExplosionImpl;

import java.util.List;

/**
 * DoubleAnchor — instant double-totem-pop combat aura using Respawn Anchors.
 *
 * ── What it does ─────────────────────────────────────────────────
 * Every IDLE tick, the aura scans a radius around the player for the best
 * Respawn Anchor placement against the nearest enemy — a spot that
 * maximizes enemy damage while keeping self-damage under a safe threshold.
 * When found, it runs a tick-staggered state machine that fires TWO anchor
 * bursts in succession, perfectly timed to the enemy's i-frame window:
 *
 *   IDLE → PLACE_ANCHOR → CHARGE → DETONATE → IFRAME_WAIT
 *        → PLACE_ANCHOR → CHARGE → DETONATE → SWITCH_BACK → IDLE
 *        (cycle 0)                  (cycle 1)
 *
 * The first detonation pops the enemy's first Totem of Undying and starts
 * their 10-tick (0.5s) invulnerability window. IFRAME_WAIT counts exactly
 * {@code I-Frame Wait} ticks (default 11) for that window to expire, then
 * places + charges + detonates a second anchor in the same spot the
 * instant their i-frames drop — popping the second totem (or killing them)
 * before they can react, heal, or pearl away.
 *
 * ── How each phase works ─────────────────────────────────────────
 *   1. PLACE_ANCHOR: pre-selected anchor slot; send interactBlock at the
 *      floor under the chosen spot → places the anchor there.
 *   2. CHARGE: pre-selected glowstone slot; send interactBlock at the
 *      anchor → charges it with one glowstone.
 *   3. DETONATE: pre-selected empty/weapon slot; send interactBlock at
 *      the charged anchor → vanilla onUse → explode (5.0f power).
 *   4. IFRAME_WAIT: count I-Frame Wait ticks (no packets sent) so the
 *      enemy's invulnerability lapses before the second burst.
 *   5. SWITCH_BACK: restore the original hotbar slot.
 *
 * ── Tick staggering ──────────────────────────────────────────────
 * Each phase waits {@code Delay} ticks before executing so the server sees
 * a natural, human-paced packet sequence rather than a single-frame flood.
 * The {@code Cooldown Bypass} setting zeroes MinecraftClient.itemUseCooldown
 * each tick so place→charge→detonate can run on consecutive 1-tick delays
 * instead of being throttled by the vanilla 4-tick item-use gate.
 *
 * ── Pre-selection (race-condition fix) ───────────────────────────
 * The slot for each phase is PRE-SELECTED at the end of the PREVIOUS phase
 * (and the first phase's slot is pre-selected when targeting succeeds).
 * This gives the server a full {@code Delay} ticks to process the
 * slot-change packet before the interaction packet arrives — preventing
 * the "wrong item used" race that would otherwise waste anchors.
 *
 * ── Self-damage safety ───────────────────────────────────────────
 * Each candidate placement is scored with vanilla's
 * {@link ExplosionImpl#calculateReceivedDamage} (pre-armor). A candidate
 * is rejected if its self-damage would be lethal (>= player.getHealth())
 * or exceed the {@code Max Self Damage} setting. The chosen spot maximizes
 * enemyDmg - selfDmg.
 */
public class DoubleAnchor extends Module {

    // ── Settings ──────────────────────────────────────────────
    private final FloatSetting range = addSetting(
        new FloatSetting("Range", "Enemy search radius (blocks). Anchors are placed at vanilla reach; this only widens the target scan.",
            3f, 6f, 5f, 1));
    private final IntSetting delay = addSetting(
        new IntSetting("Delay", "Ticks between each phase. 1 = fastest (50ms). 2+ is safer vs anti-cheat.", 1, 5, 1, 1));
    private final IntSetting iframeWait = addSetting(
        new IntSetting("I-Frame Wait", "Ticks to wait after detonate 1 before placing anchor 2 (waits out the enemy's 10-tick i-frames).",
            10, 15, 11, 1));
    private final BooleanSetting switchBack = addSetting(
        new BooleanSetting("Switch Back", "Restore your original hotbar slot after the double-pop cycle.", true));
    private final IntSetting switchBackDelay = addSetting(
        new IntSetting("Switch Back Delay", "Ticks to wait after detonate 2 before switching back.", 0, 10, 2, 1));
    private final IntSetting cooldown = addSetting(
        new IntSetting("Cooldown", "Ticks to wait after a full cycle before re-targeting.", 0, 40, 8, 1));
    private final BooleanSetting cooldownBypass = addSetting(
        new BooleanSetting("Cooldown Bypass", "Zero the vanilla 4-tick item-use gate each tick so placement isn't throttled.",
            true));
    private final BooleanSetting playersOnly = addSetting(
        new BooleanSetting("Players Only", "Only target other players (skip mobs).", true));
    private final BooleanSetting requireLOS = addSetting(
        new BooleanSetting("Require LOS", "Require line-of-sight from player to enemy.", true));
    private final BooleanSetting requireWeapon = addSetting(
        new BooleanSetting("Require Weapon", "Only detonate when a sword/axe is available for the detonate slot.", false));
    private final FloatSetting maxSelfDamage = addSetting(
        new FloatSetting("Max Self Damage", "Max acceptable self-damage (HP) per detonation. Conservative (pre-armor).",
            0f, 20f, 4f, 1));
    private final BooleanSetting autoTrigger = addSetting(
        new BooleanSetting("Auto Trigger", "Auto-fire when a valid target + placement is found. Off = wait for manual trigger().",
            true));

    // ── State machine ────────────────────────────────────────
    private enum State { IDLE, PLACE_ANCHOR, CHARGE, DETONATE, IFRAME_WAIT, SWITCH_BACK }

    private State state = State.IDLE;
    private int ticksInState = 0;
    private int totalTicks = 0;
    private static final int MAX_TOTAL_TICKS = 200; // 10s safety timeout
    private int cycle = 0; // 0 = first pop, 1 = second pop

    // Cooldown after a full double-pop cycle — prevents instant re-targeting.
    private int cooldownTicks = 0;

    // Inventory slots (found at trigger time)
    private int originalSlot = -1;
    private int anchorSlot = -1;
    private int glowstoneSlot = -1;
    private int detonateSlot = -1; // empty hand or weapon

    // Target state
    private PlayerEntity targetEnemy = null;
    private BlockPos anchorPos = null;        // where the anchor sits (and explodes from)
    private BlockPos placeTargetBlock = null; // the floor block we click to place it
    private Direction placeTargetFace = Direction.UP; // the face we click

    public DoubleAnchor() {
        super("DoubleAnchor", "Instant double anchor burst — pops two totems by timing a second anchor to the enemy's i-frame drop.",
            Category.COMBAT);
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        // If we're mid-cycle, try to restore the original slot on disable.
        if (state != State.IDLE && originalSlot >= 0 && clientPlayerSafe() != null) {
            ClientPlayerEntity player = clientPlayerSafe();
            ItemStack origStack = player.getInventory().getStack(originalSlot);
            // Don't restore onto an anchor slot (would re-arm instantly).
            if (origStack.getItem() != Items.RESPAWN_ANCHOR) {
                selectSlot(player, originalSlot);
            } else if (detonateSlot >= 0) {
                selectSlot(player, detonateSlot);
            }
        }
        resetState();
    }

    private void resetState() {
        state = State.IDLE;
        ticksInState = 0;
        totalTicks = 0;
        cycle = 0;
        originalSlot = -1;
        anchorSlot = -1;
        glowstoneSlot = -1;
        detonateSlot = -1;
        targetEnemy = null;
        anchorPos = null;
        placeTargetBlock = null;
        placeTargetFace = Direction.UP;
        // Arm the post-cycle cooldown so the aura doesn't instantly re-fire.
        cooldownTicks = cooldown.get();
    }

    /** Best-effort mc.player fetch that won't NPE from a static-ish context. */
    private ClientPlayerEntity clientPlayerSafe() {
        MinecraftClient c = MinecraftClient.getInstance();
        return c == null ? null : c.player;
    }

    // ════════════════════════════════════════════════════════════════
    //  Manual trigger — public so a keybind can fire it on demand when
    //  Auto Trigger is off. Returns true if the cycle started.
    // ════════════════════════════════════════════════════════════════
    public boolean trigger() {
        if (!isEnabled()) return false;
        if (state != State.IDLE) resetState();
        return tryStartCycle();
    }

    // ════════════════════════════════════════════════════════════════
    //  onTick — drive the state machine (20Hz)
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return; // don't fire while in menus

        // ── Cooldown bypass: zero the placement gate each tick while active ──
        if (cooldownBypass.isEnabled() && state != State.IDLE) {
            ((MinecraftClientAccessor) mc).lucid$setItemUseCooldown(0);
        }

        // ── IDLE: look for a target (auto-trigger) or wait for manual trigger ──
        if (state == State.IDLE) {
            if (autoTrigger.isEnabled()) {
                if (cooldownTicks > 0) { cooldownTicks--; return; }
                tryStartCycle();
            }
            return;
        }

        // Safety timeout — never get permanently stuck mid-cycle.
        totalTicks++;
        if (totalTicks > MAX_TOTAL_TICKS) {
            restoreOriginalSlot(mc.player);
            resetState();
            return;
        }

        ticksInState++;
        int requiredTicks = phaseTicks();

        ClientPlayerEntity player = mc.player;
        ClientPlayerInteractionManager im = mc.interactionManager;
        ClientWorld world = mc.world;

        switch (state) {
            // ── PLACE_ANCHOR: click the floor to place the anchor ────────
            case PLACE_ANCHOR: {
                if (ticksInState < requiredTicks) return;
                // Re-verify the spot is still placeable (cycle 1: the first
                // explosion may have destroyed the floor or left a block).
                if (!isPlacementValid(world, anchorPos)) {
                    restoreOriginalSlot(player);
                    resetState();
                    return;
                }
                // Anchor slot was pre-selected when the cycle (re)started.
                BlockHitResult bhr = makeHitResult(placeTargetBlock, placeTargetFace);
                im.interactBlock(player, Hand.MAIN_HAND, bhr);
                // Pre-select glowstone for CHARGE.
                selectSlot(player, glowstoneSlot);
                state = State.CHARGE;
                ticksInState = 0;
                break;
            }
            // ── CHARGE: right-click the anchor with glowstone ────────────
            case CHARGE: {
                if (ticksInState < requiredTicks) return;
                // Placement-sanity gate — abort if the server rejected the place.
                BlockState anchorState = world.getBlockState(anchorPos);
                if (!anchorState.isOf(Blocks.RESPAWN_ANCHOR)) {
                    restoreOriginalSlot(player);
                    resetState();
                    return;
                }
                // Glowstone slot was pre-selected at end of PLACE_ANCHOR.
                BlockHitResult bhr = makeHitResult(anchorPos, Direction.UP);
                im.interactBlock(player, Hand.MAIN_HAND, bhr);
                // Pre-select detonate slot (empty/weapon) for DETONATE.
                selectSlot(player, detonateSlot);
                state = State.DETONATE;
                ticksInState = 0;
                break;
            }
            // ── DETONATE: right-click the charged anchor → explode ───────
            case DETONATE: {
                if (ticksInState < requiredTicks) return;
                // Charge-sanity gate — only detonate if it actually charged.
                BlockState anchorState = world.getBlockState(anchorPos);
                if (!anchorState.isOf(Blocks.RESPAWN_ANCHOR)
                        || anchorState.getOrEmpty(RespawnAnchorBlock.CHARGES).orElse(0) <= 0) {
                    restoreOriginalSlot(player);
                    resetState();
                    return;
                }
                // Detonate slot was pre-selected at end of CHARGE.
                BlockHitResult bhr = makeHitResult(anchorPos, Direction.UP);
                im.interactBlock(player, Hand.MAIN_HAND, bhr);
                if (cycle == 0) {
                    // Pre-select the anchor slot for cycle 1's PLACE_ANCHOR.
                    selectSlot(player, anchorSlot);
                    state = State.IFRAME_WAIT;
                } else {
                    state = State.SWITCH_BACK;
                }
                ticksInState = 0;
                break;
            }
            // ── IFRAME_WAIT: count ticks for the enemy's i-frames to drop ─
            case IFRAME_WAIT: {
                // Use the longer of Delay and I-Frame Wait so we never fire
                // the second burst before the enemy's invulnerability lapses.
                int wait = Math.max(requiredTicks, iframeWait.get());
                if (ticksInState < wait) return;
                // Enemy still alive? If they're already dead, don't waste anchor 2.
                if (targetEnemy == null || !targetEnemy.isAlive() || targetEnemy.isRemoved()) {
                    state = State.SWITCH_BACK;
                    ticksInState = 0;
                    return;
                }
                // Re-audit inventory: anchor + glowstone must still be present.
                PlayerInventory inv = player.getInventory();
                if (findItemSlot(inv, Items.RESPAWN_ANCHOR) < 0
                        || findItemSlot(inv, Items.GLOWSTONE) < 0) {
                    restoreOriginalSlot(player);
                    resetState();
                    return;
                }
                cycle = 1;
                // Anchor slot was already pre-selected at end of DETONATE.
                state = State.PLACE_ANCHOR;
                ticksInState = 0;
                break;
            }
            // ── SWITCH_BACK: restore original slot ───────────────────────
            case SWITCH_BACK: {
                if (ticksInState < switchBackDelay.get()) return;
                restoreOriginalSlot(player);
                resetState();
                break;
            }
            default: resetState();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Cycle startup — target + placement selection + inventory audit
    // ════════════════════════════════════════════════════════════════

    /**
     * Find the best (enemy, placement) pair, audit inventory, pre-select the
     * anchor slot, and transition into PLACE_ANCHOR (cycle 0). Returns true
     * if the cycle started.
     */
    private boolean tryStartCycle() {
        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        if (player == null || world == null || mc.interactionManager == null) return false;

        // ── Inventory audit ───────────────────────────────────────
        PlayerInventory inv = player.getInventory();
        anchorSlot = findItemSlot(inv, Items.RESPAWN_ANCHOR);
        glowstoneSlot = findItemSlot(inv, Items.GLOWSTONE);
        if (anchorSlot < 0) {
return false; }
        if (glowstoneSlot < 0) {
return false; }
        detonateSlot = findDetonateSlot(player);
        if (detonateSlot < 0) {
return false; }

        // ── Targeting + placement selection ──────────────────────
        PlacementChoice best = findBestPlacement();
        if (best == null) return false; // nothing to do this tick — stay IDLE silently

        // ── Commit ────────────────────────────────────────────────
        targetEnemy = best.enemy;
        anchorPos = best.pos;
        placeTargetBlock = best.pos.down(); // click the floor under the anchor spot
        placeTargetFace = Direction.UP;
        originalSlot = inv.getSelectedSlot();
        cycle = 0;

        // Pre-select the anchor slot NOW so the server has a full `delay` ticks
        // to process the swap before PLACE_ANCHOR's interaction fires.
        selectSlot(player, anchorSlot);

        state = State.PLACE_ANCHOR;
        ticksInState = 0;
        totalTicks = 0;
        return true;
    }

    /** A candidate (enemy, anchor placement block, score). */
    private static final class PlacementChoice {
        final PlayerEntity enemy;
        final BlockPos pos;
        final float score;
        PlacementChoice(PlayerEntity enemy, BlockPos pos, float score) {
            this.enemy = enemy; this.pos = pos; this.score = score;
        }
    }

    /** Find the best (enemy, placement) pair, or null if none. */
    private PlacementChoice findBestPlacement() {
        ClientPlayerEntity player = mc.player;
        ClientWorld world = mc.world;
        double r = range.get();
        double r2 = r * r;

        // ── Enemy candidates ──────────────────────────────────────
        Box searchBox = new Box(
            player.getX() - r, player.getY() - r, player.getZ() - r,
            player.getX() + r, player.getY() + r, player.getZ() + r);
        List<Entity> candidates = world.getOtherEntities(player, searchBox, e -> {
            if (playersOnly.isEnabled() && !(e instanceof PlayerEntity)) return false;
            if (!e.isAlive() || e.isRemoved()) return false;
            if (e.isInvisible()) return false;
            if (e.equals(player)) return false;
            return e.squaredDistanceTo(player) <= r2;
        });

        PlacementChoice best = null;
        float bestScore = -Float.MAX_VALUE;

        for (Entity ent : candidates) {
            PlayerEntity enemy = (ent instanceof PlayerEntity) ? (PlayerEntity) ent : null;
            // When Players Only is off we still need a LivingEntity for damage
            // calc; calculateReceivedDamage takes any Entity, so pass through.
            Entity target = ent;

            // Optional LOS player → enemy.
            if (requireLOS.isEnabled()
                    && !hasLineOfSight(world, player.getEyePos(), target.getEyePos(), target)) {
                continue;
            }

            // ── Placement candidates: the 3×3 floor ring at the enemy's feet ──
            BlockPos feet = target.getBlockPos();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos cand = feet.add(dx, 0, dz);
                    PlacementChoice pc = evaluatePlacement(world, player, target, enemy, cand);
                    if (pc == null) continue;
                    if (pc.score > bestScore) {
                        bestScore = pc.score;
                        best = pc;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Evaluate a single anchor placement candidate. Returns null if invalid
     * (no solid floor, blocked, lethal self-damage, out of reach); otherwise
     * a PlacementChoice with score = enemyDmg - selfDmg.
     *
     * @param cand the block the anchor will occupy (and explode from)
     */
    private PlacementChoice evaluatePlacement(ClientWorld world, ClientPlayerEntity player,
                                              Entity target, PlayerEntity enemy, BlockPos cand) {
        // The anchor spot must be air (vanilla place check).
        if (!world.isAir(cand)) return null;
        // The block below must be a solid placeable surface.
        BlockState below = world.getBlockState(cand.down());
        if (!below.isOpaque()) return null;
        // The 1×2×1 occupancy box above the floor must be empty (the anchor
        // itself is 1 block tall, but leave headroom for the explosion logic).
        Box occ = new Box(
            cand.getX(), cand.getY(), cand.getZ(),
            cand.getX() + 1, cand.getY() + 1, cand.getZ() + 1);
        if (!world.getOtherEntities(null, occ).isEmpty()) return null;

        // Reach gate — anchors are placed at vanilla reach, NOT crystal range.
        double reach2 = player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(cand));
        if (reach2 > 4.5 * 4.5) return null;

        // Anchor explosion center = block center of cand (vanilla explode
        // removes the block then detonates at pos.toCenterPos()).
        Vec3d anchorCenter = Vec3d.ofCenter(cand);

        // Damage estimates via vanilla's exposure helper (pre-armor).
        float enemyDmg = ExplosionImpl.calculateReceivedDamage(anchorCenter, target);
        float selfDmg = ExplosionImpl.calculateReceivedDamage(anchorCenter, player);

        // Self-damage safety: lethal → reject. Above Max Self Damage → reject.
        if (selfDmg >= player.getHealth()) return null;
        if (selfDmg > maxSelfDamage.get()) return null;

        // Score: maximize enemy damage, minimize self damage.
        float score = enemyDmg - selfDmg;
        if (score <= 0f) return null; // don't bother if it hurts us more than them

        return new PlacementChoice(enemy, cand, score);
    }

    // ════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════

    /** True if the spot is still valid for placing an anchor (air + solid floor). */
    private boolean isPlacementValid(ClientWorld world, BlockPos anchorSpot) {
        if (anchorSpot == null) return false;
        if (!world.isAir(anchorSpot)) return false;
        return world.getBlockState(anchorSpot.down()).isOpaque();
    }

    /** Helper: build a BlockHitResult for direct packet injection (bypasses crosshair LOS). */
    /** Rolled per-phase delay threshold (humanized once per phase entry). */
    private int rolledPhaseTicks = 0;

    /**
     * Humanize a delay: mostly the configured value, sometimes a tick or
     * two slower — never faster. Uneven gaps between swap/place/attack
     * packets are what manual play looks like; a metronome is not.
     */
    private static int humanTicks(int base) {
        int r = java.util.concurrent.ThreadLocalRandom.current().nextInt(100);
        if (r < 70) return base;       // usually on time
        if (r < 90) return base + 1;   // sometimes a beat late
        return base + 2;               // rarely slower still
    }

    /** Stable per-phase roll: re-rolled only on the phase's first tick. */
    private int phaseTicks() {
        if (ticksInState == 1) rolledPhaseTicks = humanTicks(delay.get());
        return rolledPhaseTicks;
    }

    private BlockHitResult makeHitResult(BlockPos pos, Direction face) {
        Vec3d hitVec = Vec3d.of(pos).add(0, 0.5, 0);
        return new BlockHitResult(hitVec, face, pos, false);
    }

    private void selectSlot(ClientPlayerEntity player, int slot) {
        if (slot < 0 || slot > 8) return;
        PlayerInventory inv = player.getInventory();
        if (inv.getSelectedSlot() != slot) {
            inv.setSelectedSlot(slot); // vanilla sends its own slot packet — never duplicate it
        }
    }

    /** Find the first hotbar slot holding {@code item}, or -1. */
    private int findItemSlot(PlayerInventory inv, Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() == item) return i;
        }
        return -1;
    }

    /**
     * Find the detonate slot — first empty hotbar slot (clicking an anchor
     * with an empty hand detonates it). Falls back to a weapon (sword/axe).
     * If requireWeapon is on, only returns a weapon slot.
     */
    private int findDetonateSlot(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        int weaponSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) {
                if (!requireWeapon.isEnabled()) return i;
                continue;
            }
            if (weaponSlot < 0 && (stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES))) {
                weaponSlot = i;
            }
        }
        return weaponSlot;
    }

    /**
     * Restore the original hotbar slot, guarding against restoring onto an
     * anchor slot (which would re-arm the aura instantly in auto-trigger mode).
     */
    private void restoreOriginalSlot(ClientPlayerEntity player) {
        if (!switchBack.isEnabled() || originalSlot < 0) return;
        ItemStack origStack = player.getInventory().getStack(originalSlot);
        if (origStack.getItem() == Items.RESPAWN_ANCHOR) {
            // Original slot is the anchor — switch to the safe detonate slot.
            if (detonateSlot >= 0) selectSlot(player, detonateSlot);
        } else {
            selectSlot(player, originalSlot);
        }
    }

    /**
     * Raycast line-of-sight check (copied from Triggerbot / AutoCrystal).
     * Returns true if no block blocks the line from {@code from} to {@code to}.
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

    // ── Public state for UI / keybind hooks ─────────────────
    public boolean isRunning() { return state != State.IDLE; }
    public String getStateName() { return state.name(); }
}