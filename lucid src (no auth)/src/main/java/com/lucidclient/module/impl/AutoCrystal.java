package com.lucidclient.module.impl;

import com.lucidclient.mixin.MinecraftClientAccessor;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.IntSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
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
 * AutoCrystal (Crystal Aura) — automates End Crystal PvP.
 *
 * ── What it does ─────────────────────────────────────────────────
 * Every tick (while IDLE), the aura scans a radius around the player for the
 * best crystal placement that maximizes enemy damage while keeping self-damage
 * under a safe threshold. When found, it runs a tick-staggered state machine:
 *
 *   IDLE → PLACE_OBSIDIAN → PLACE_CRYSTAL → DETONATE → SWITCH_BACK → IDLE
 *
 * (If the chosen placement surface already has obsidian/bedrock, the
 * PLACE_OBSIDIAN phase is skipped — the machine goes straight to PLACE_CRYSTAL.)
 *
 * ── The "sweet spot" math ────────────────────────────────────────
 * For each enemy in range, the 8 floor blocks around (and at) the enemy's feet
 * are candidate crystal positions. For each candidate, the aura computes:
 *
 *   enemyExposure  = ExplosionImpl.calculateReceivedDamage(crystalCenter, enemy)
 *   selfExposure   = ExplosionImpl.calculateReceivedDamage(crystalCenter, player)
 *
 * `ExplosionImpl.calculateReceivedDamage` is the exact vanilla helper — it
 * returns the pre-armor, post-terrain damage (raycast-based exposure). A
 * crystal's explosion power is 6 (range 12). Candidates where
 * `selfExposure >= player.getHealth()` (lethal) or `selfExposure > Max Self
 * Damage` are rejected. The best candidate maximizes
 * `enemyExposure - selfExposure`.
 *
 * ── Slot-swap race-fix (reused from AnchorMacro/ShieldBreaker) ────
 * Each phase pre-selects the NEXT phase's required slot at the END of the
 * current phase, then waits `Delay` ticks before sending the place/attack
 * packet. This gives the server time to process the slot-change packet
 * before the use-item packet — preventing the "wrong item used" race.
 *
 * ── Crystal removal ─────────────────────────────────────────────
 * AutoCrystal does NOT re-implement client-side crystal removal. The existing
 * ClientConnectionMixin outgoing-packet hook (gated by the Crystal Optimizer
 * module) discards the crystal on the client the moment the attack packet
 * leaves. AutoCrystal just calls attackEntity; the instant removal is
 * handled centrally.
 *
 * ── Cooldown bypass ─────────────────────────────────────────────
 * Each tick while active, optionally zeroes {@code MinecraftClient.itemUseCooldown}
 * via {@link MinecraftClientAccessor} so placement isn't slowed by the
 * vanilla 4-tick gate (same pattern as CrystalOptimizer).
 *
 * Settings:
 *   Range, Delay, Switch Back, Switch Back Delay, Cooldown, Players Only,
 *   Require LOS, Max Self Damage, Respect Cooldown, Cooldown Threshold,
 *   Cooldown Bypass, Place Obsidian
 */
public class AutoCrystal extends Module {

    private final FloatSetting range = addSetting(
        new FloatSetting("Range", "Scan radius for enemies + crystal placements (blocks).",
            4f, 12f, 6f, 1));
    private final IntSetting delay = addSetting(
        new IntSetting("Delay", "Ticks between each phase (server race-fix). 1 = fastest. 2+ is safer vs anti-cheat.",
            1, 5, 2, 1));
    private final BooleanSetting switchBack = addSetting(
        new BooleanSetting("Switch Back", "Restore your original hotbar slot after each cycle.", true));
    private final IntSetting switchBackDelay = addSetting(
        new IntSetting("Switch Back Delay", "Ticks after detonation before switching back.", 0, 10, 2, 1));
    private final IntSetting cooldown = addSetting(
        new IntSetting("Cooldown", "Ticks to wait after a cycle before re-firing.", 0, 40, 8, 1));
    private final BooleanSetting playersOnly = addSetting(
        new BooleanSetting("Players Only", "Only target other players (skip mobs).", true));
    private final BooleanSetting requireLOS = addSetting(
        new BooleanSetting("Require LOS", "Require line-of-sight from you to the enemy.", true));
    private final FloatSetting maxSelfDamage = addSetting(
        new FloatSetting("Max Self Damage", "Max acceptable self-damage (HP) per detonation. Conservative (pre-armor).",
            0f, 20f, 2f, 1));
    private final BooleanSetting respectCooldown = addSetting(
        new BooleanSetting("Respect Cooldown", "Gate detonation on attack-cooldown progress.", true));
    private final FloatSetting cooldownThreshold = addSetting(
        new FloatSetting("Cooldown Threshold", "Min attack-cooldown progress (0–1) required to detonate.",
            0.1f, 1f, 1f, 2));
    private final BooleanSetting cooldownBypass = addSetting(
        new BooleanSetting("Cooldown Bypass", "Zero the item-use cooldown each tick so placement isn't slowed.",
            true));
    private final BooleanSetting placeObsidian = addSetting(
        new BooleanSetting("Place Obsidian", "Auto-place obsidian when no surface exists. Off = only use existing obsidian/bedrock.",
            true));

    // ── State machine ────────────────────────────────────────
    private enum State { IDLE, PLACE_OBSIDIAN, PLACE_CRYSTAL, DETONATE, SWITCH_BACK }

    private State state = State.IDLE;
    private int ticksInState = 0;
    private int totalTicks = 0;
    private static final int MAX_TOTAL_TICKS = 100; // 5s safety timeout

    private int cooldownTicks = 0;

    // Inventory state (captured at trigger).
    private int originalSlot = -1;
    private int crystalSlot = -1;
    private int obsidianSlot = -1;

    // The chosen placement.
    private BlockPos placePos = null;     // the obsidian/bedrock block (the surface)
    private boolean surfaceExisted = false; // true = surface already obsidian/bedrock (skip PLACE_OBSIDIAN)
    private EndCrystalEntity pendingCrystal = null; // the placed crystal to detonate

    public AutoCrystal() {
        super("AutoCrystal", "Automates End Crystal placement + detonation for crystal PvP.",
            Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        // Snap back if mid-cycle so the player isn't left holding crystals/obsidian.
        restoreOriginalSlot();
        resetState();
    }

    private void resetState() {
        state = State.IDLE;
        ticksInState = 0;
        totalTicks = 0;
        originalSlot = -1;
        crystalSlot = -1;
        obsidianSlot = -1;
        placePos = null;
        surfaceExisted = false;
        pendingCrystal = null;
        cooldownTicks = cooldown.get(); // arm the post-cycle gate
    }

    // ════════════════════════════════════════════════════════════════
    //  onTick — drive the state machine (20Hz)
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) return;
        if (client.currentScreen != null) return; // don't fire while in menus

        // ── Cooldown bypass: zero the placement gate each tick while active ──
        if (cooldownBypass.isEnabled()) {
            ((MinecraftClientAccessor) client).lucid$setItemUseCooldown(0);
        }

        // Safety timeout — never get permanently stuck.
        if (state != State.IDLE) {
            totalTicks++;
            if (totalTicks > MAX_TOTAL_TICKS) {
                restoreOriginalSlot();
                resetState();
                return;
            }
        }

        ticksInState++;

        switch (state) {
            case IDLE: {
                // Post-cycle cooldown gate.
                if (cooldownTicks > 0) { cooldownTicks--; return; }

                // Find the best (enemy, placement) pair.
                PlacementChoice best = findBestPlacement(client);
                if (best == null) return; // nothing to do this tick

                // Inventory audit.
                ClientPlayerEntity player = client.player;
                PlayerInventory inv = player.getInventory();
                crystalSlot = findItemSlot(inv, Items.END_CRYSTAL);
                if (crystalSlot < 0) {
                    return;
                }
                if (!best.surfaceExisted && placeObsidian.isEnabled()) {
                    obsidianSlot = findItemSlot(inv, Items.OBSIDIAN);
                    if (obsidianSlot < 0) {
                        return;
                    }
                }

                // Lock the placement + save the original slot.
                placePos = best.pos;
                surfaceExisted = best.surfaceExisted;
                originalSlot = inv.getSelectedSlot();

                // Pre-select the first required slot now; the next phase waits
                // `Delay` ticks for the server to process the slot-change.
                int firstSlot = surfaceExisted ? crystalSlot : obsidianSlot;
                selectSlot(player, firstSlot);

                state = surfaceExisted ? State.PLACE_CRYSTAL : State.PLACE_OBSIDIAN;
                ticksInState = 0;
                totalTicks = 0;
                break;
            }
            case PLACE_OBSIDIAN: {
                if (ticksInState < phaseTicks()) return;
                // Guard: obsidian must be in hand on the client.
                if (client.player.getMainHandStack().getItem() != Items.OBSIDIAN) {
                    if (ticksInState > delay.get() + 10) {
                        restoreOriginalSlot(); resetState();
                    }
                    return;
                }
                // Place the obsidian at placePos (target its UP face).
                BlockHitResult bhr = makeHitResult(placePos, Direction.UP);
                client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, bhr);
                // Pre-select the crystal slot for the next phase.
                selectSlot(client.player, crystalSlot);
                state = State.PLACE_CRYSTAL;
                ticksInState = 0;
                break;
            }
            case PLACE_CRYSTAL: {
                if (ticksInState < phaseTicks()) return;
                // Placement-sanity gate: the surface must be obsidian/bedrock now.
                BlockState surface = client.world.getBlockState(placePos);
                if (!surface.isOf(Blocks.OBSIDIAN) && !surface.isOf(Blocks.BEDROCK)) {
                    restoreOriginalSlot(); resetState();
                    return;
                }
                // The air block above must still be empty.
                if (!client.world.isAir(placePos.up())) {
                    restoreOriginalSlot(); resetState();
                    return;
                }
                // Guard: crystal must be in hand.
                if (client.player.getMainHandStack().getItem() != Items.END_CRYSTAL) {
                    if (ticksInState > delay.get() + 10) {
                        restoreOriginalSlot(); resetState();
                    }
                    return;
                }
                // Place the crystal (target the surface's UP face).
                BlockHitResult bhr = makeHitResult(placePos, Direction.UP);
                client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, bhr);
                // Pre-select a weapon slot for the detonate (optional — bare hand works too).
                selectSlot(client.player, findWeaponSlot(client.player));
                state = State.DETONATE;
                ticksInState = 0;
                break;
            }
            case DETONATE: {
                if (ticksInState < phaseTicks()) return;
                // Cooldown gate.
                if (respectCooldown.isEnabled()) {
                    float progress = client.player.getAttackCooldownProgress(0.5f);
                    if (progress < cooldownThreshold.get()) return;
                }
                // Find the just-placed EndCrystalEntity at placePos.up() (its
                // standing position). Scan world entities for a matching crystal.
                EndCrystalEntity crystal = findCrystalAt(client.world, placePos);
                if (crystal == null || crystal.isRemoved()) {
                    // The crystal entity hasn't spawned client-side yet, or was
                    // already removed. Give a small grace window before aborting.
                    if (ticksInState > delay.get() + 10) {
                        restoreOriginalSlot(); resetState();
                    }
                    return;
                }
                // Detonate.
                client.interactionManager.attackEntity(client.player, crystal);
                client.player.swingHand(Hand.MAIN_HAND);
                // The predictive removal mixin (if CrystalOptimizer is on) will
                // discard the crystal client-side now.
                state = State.SWITCH_BACK;
                ticksInState = 0;
                break;
            }
            case SWITCH_BACK: {
                if (ticksInState < switchBackDelay.get()) return;
                restoreOriginalSlot();
                resetState();
                break;
            }
            default: resetState();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Targeting + placement selection (the "sweet spot" math)
    // ════════════════════════════════════════════════════════════════

    /** A candidate (enemy, placement block, surface-existed flag, score). */
    private static final class PlacementChoice {
        final PlayerEntity enemy;
        final BlockPos pos;
        final boolean surfaceExisted;
        final float score;
        PlacementChoice(PlayerEntity enemy, BlockPos pos, boolean surfaceExisted, float score) {
            this.enemy = enemy; this.pos = pos;
            this.surfaceExisted = surfaceExisted; this.score = score;
        }
    }

    /** Find the best (enemy, placement) pair, or null if none. */
    private PlacementChoice findBestPlacement(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        double r = range.get();
        double r2 = r * r;

        // ── Enemy candidates ──────────────────────────────────────
        Box searchBox = new Box(
            player.getX() - r, player.getY() - r, player.getZ() - r,
            player.getX() + r, player.getY() + r, player.getZ() + r);
        List<Entity> candidates = world.getOtherEntities(player, searchBox, e -> {
            if (!(e instanceof PlayerEntity)) return false;
            if (!e.isAlive() || e.isRemoved()) return false;
            if (e.isInvisible()) return false;
            return e.squaredDistanceTo(player) <= r2;
        });

        PlacementChoice best = null;
        float bestScore = -Float.MAX_VALUE;

        for (Entity ent : candidates) {
            PlayerEntity enemy = (PlayerEntity) ent;

            // Optional LOS player → enemy.
            if (requireLOS.isEnabled()
                    && !hasLineOfSight(world, player.getEyePos(), enemy.getEyePos(), enemy)) {
                continue;
            }

            // ── Placement candidates: the 3×3 floor around the enemy's feet ──
            BlockPos feet = enemy.getBlockPos();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos cand = feet.add(dx, 0, dz);
                    PlacementChoice pc = evaluatePlacement(world, player, enemy, cand);
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
     * Evaluate a single placement candidate. Returns null if invalid (wrong
     * surface, blocked, lethal self-damage, etc.); otherwise a PlacementChoice
     * with a score = enemyExposure - selfExposure.
     *
     * @param cand the floor block where the surface (obsidian/bedrock) sits or
     *             where obsidian would be placed. The crystal itself goes at
     *             {@code cand.up()}.
     */
    private PlacementChoice evaluatePlacement(ClientWorld world, ClientPlayerEntity player,
                                                PlayerEntity enemy, BlockPos cand) {
        // The block above must be air (vanilla EndCrystalItem check).
        if (!world.isAir(cand.up())) return null;
        // The 1×2×1 occupancy box above must be empty (no entities blocking).
        Box occ = new Box(
            cand.up().getX(), cand.up().getY(), cand.up().getZ(),
            cand.up().getX() + 1, cand.up().getY() + 2.0, cand.up().getZ() + 1);
        if (!world.getOtherEntities(null, occ).isEmpty()) return null;

        boolean surfaceExisted;
        if (isCrystalBase(world.getBlockState(cand))) {
            surfaceExisted = true;
        } else if (placeObsidian.isEnabled() && world.isAir(cand)) {
            // We could place obsidian here — but only if the block below cand
            // is solid (so the obsidian has something to sit on). Use the
            // block-below's collision/opaque check.
            BlockState below = world.getBlockState(cand.down());
            if (!below.isOpaque()) return null;
            surfaceExisted = false;
        } else {
            return null;
        }

        // The crystal stands at cand.up(); its center is the block center of cand.up().
        Vec3d crystalCenter = Vec3d.ofCenter(cand.up());

        // Out-of-range gate (crystal explosion radius = 12).
        if (enemy.squaredDistanceTo(crystalCenter) > 12.0 * 12.0) return null;

        // Damage estimates via vanilla's exposure helper.
        float enemyExposure = ExplosionImpl.calculateReceivedDamage(crystalCenter, enemy);
        float selfExposure = ExplosionImpl.calculateReceivedDamage(crystalCenter, player);

        // Self-damage safety: lethal → reject. Above Max Self Damage → reject.
        if (selfExposure >= player.getHealth()) return null;
        if (selfExposure > maxSelfDamage.get()) return null;

        // Score: maximize enemy damage, minimize self damage.
        float score = enemyExposure - selfExposure;
        if (score <= 0f) return null; // don't bother if it hurts us more than them

        return new PlacementChoice(enemy, cand, surfaceExisted, score);
    }

    /** True if the block state is a valid End Crystal base (obsidian/bedrock). */
    private boolean isCrystalBase(BlockState state) {
        return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
    }

    /**
     * Find an {@link EndCrystalEntity} standing at {@code surfacePos.up()} — i.e.
     * the crystal we just placed. Matches by position (with a small tolerance).
     */
    private EndCrystalEntity findCrystalAt(ClientWorld world, BlockPos surfacePos) {
        Vec3d target = Vec3d.ofCenter(surfacePos.up());
        double tol = 0.5;
        for (Entity e : world.getEntities()) {
            if (!(e instanceof EndCrystalEntity crystal)) continue;
            if (crystal.isRemoved() || !crystal.isAlive()) continue;
            Vec3d cp = new Vec3d(crystal.getX(), crystal.getY(), crystal.getZ());
            if (cp.squaredDistanceTo(target) > tol * tol) continue;
            return crystal;
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════
    //  Slot helpers (reused verbatim from AnchorMacro/ShieldBreaker)
    // ════════════════════════════════════════════════════════════════
    private void selectSlot(ClientPlayerEntity player, int slot) {
        if (slot < 0 || slot > 8) return;
        PlayerInventory inv = player.getInventory();
        if (inv.getSelectedSlot() == slot) return;
        inv.setSelectedSlot(slot); // vanilla sends its own slot packet — never duplicate it
    }

    /** First hotbar slot (0..8) holding the given item, or -1. */
    private int findItemSlot(PlayerInventory inv, net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) return i;
        }
        return -1;
    }

    /** First hotbar slot holding a sword/axe (or -1). Mace isn't in those tags in 1.21.x. */
    private int findWeaponSlot(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES)) return i;
        }
        return -1; // bare hand works for attackEntity
    }

    private void restoreOriginalSlot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (originalSlot >= 0 && originalSlot <= 8) {
            selectSlot(mc.player, originalSlot);
        }
        originalSlot = -1;
    }

    // ── BlockHitResult + LOS helpers (reused from AnchorMacro/TriggerBot) ──
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
        // Hit-vec at the top-face center of the surface block.
        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        return new BlockHitResult(hitVec, face, pos, false);
    }

    private boolean hasLineOfSight(ClientWorld world, Vec3d from, Vec3d to, Entity ignored) {
        RaycastContext ctx = new RaycastContext(from, to,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ignored);
        BlockHitResult hit = world.raycast(ctx);
        if (hit == null || hit.getType() == HitResult.Type.MISS) return true;
        double distToBlock = from.squaredDistanceTo(hit.getPos());
        double distToTarget = from.squaredDistanceTo(to);
        return distToBlock >= distToTarget - 0.5;
    }

    // ── Public state for UI hooks ─────────────────────────────
    public boolean isRunning() { return state != State.IDLE; }
    public String getStateName() { return state.name(); }
}