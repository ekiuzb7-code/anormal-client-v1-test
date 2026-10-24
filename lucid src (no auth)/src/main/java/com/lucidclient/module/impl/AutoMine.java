package com.lucidclient.module.impl;

import com.lucidclient.mixin.ClientPlayerInteractionManagerAccessor;
import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.EnumSetting;
import com.lucidclient.module.setting.FloatSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * AutoMine — fully automates block breaking by driving the vanilla mining
 * pipeline ({@link ClientPlayerInteractionManager#attackBlock attackBlock} +
 * {@link ClientPlayerInteractionManager#updateBlockBreakingProgress
 * updateBlockBreakingProgress}) on every tick. No left-click needed.
 *
 * ── How it works ──────────────────────────────────────────────────
 * Vanilla mining is packet-driven: when you hold left-click, the client
 * sends START_DESTROY_BLOCK, accumulates per-tick break progress
 * (block-hardness vs tool-speed), and sends a final sequenced START when
 * progress reaches 1.0 (the server confirms and removes the block).
 * AutoMine takes over the "hold left-click" part — each tick it picks a
 * target block, calls the interaction manager to start/continue breaking
 * it, and moves to the next block when the current one is gone. The
 * interaction manager still drives the sequenced packets, so client-
 * prediction sequences stay correct (no rubber-banding).
 *
 * ── Modes ─────────────────────────────────────────────────────────
 *   CROSSHAIR — breaks whatever block the crosshair touches. The classic
 *     AutoMine: useful in PvP for instantly breaking webs / obsidian /
 *     placed blocks without wearing out your mouse. Just look at a block
 *     and it breaks.
 *   TUNNEL — breaks blocks in a 1×2 (or 2×2) grid ahead of the player for
 *     strip-mining. As you walk forward (hold W), AutoMine clears whatever
 *     is in the tunnel profile in front of you, so you drill a straight
 *     tunnel hands-free.
 *   WHITELIST — only breaks blocks in a configurable whitelist (AutoOre
 *     style). It scans blocks within Range and only breaks whitelisted
 *     blocks (e.g. diamond ore, ancient debris), ignoring stone /
 *     netherrack — so you only stop to mine valuable targets.
 *
 * ── Tool swap ─────────────────────────────────────────────────────
 * With "Auto Tool" on, AutoMine reuses the AutoTool logic: before breaking
 * a block, it scans the hotbar for the best suitable tool (right tool type
 * + highest mining speed) and swaps to it. This keeps breaking at max
 * speed without manual inventory management. The original slot is restored
 * after the break completes (gated by "Switch Back").
 *
 * ── Speed multiplier ──────────────────────────────────────────────
 * Vanilla gates mining with a 5-tick cooldown after each break
 * ({@code blockBreakingCooldown}). With "Speed Multiplier" &gt; 1.0,
 * AutoMine resets this cooldown to 0 each tick (via the accessor mixin) so
 * breaking starts immediately on the next block. At 1.0 the cooldown is
 * left alone (legit-speed breaking). Higher values also bump the
 * accumulated progress toward 1.0 to force faster completion — the
 * "instant mine" flavor. Use with care: very high multipliers on
 * anti-cheat servers can flag you.
 *
 * Settings:
 *   Mode             — CROSSHAIR / TUNNEL / WHITELIST
 *   Range            — max scan / break distance (blocks)
 *   Auto Tool        — swap to the best tool before breaking
 *   Switch Back      — restore the original slot after a break
 *   Speed Multiplier — break-speed multiplier (1.0 = legit, higher = faster)
 *   Tunnel Width     — TUNNEL mode: 1×2 or 2×2 profile
 *   Whitelist        — WHITELIST mode: which blocks to break
 */
public class AutoMine extends Module {

    private final EnumSetting mode = addSetting(
        new EnumSetting("Mode", "AutoMine mode.",
            new String[]{"CROSSHAIR", "TUNNEL", "WHITELIST"}, "CROSSHAIR"));
    private final FloatSetting range = addSetting(
        new FloatSetting("Range", "Max scan / break distance (blocks).",
            3f, 8f, 5f, 1));
    private final BooleanSetting autoTool = addSetting(
        new BooleanSetting("Auto Tool", "Swap to the best tool before breaking (reuse AutoTool logic).",
            true));
    private final BooleanSetting switchBack = addSetting(
        new BooleanSetting("Switch Back", "Restore the original hotbar slot after a break completes.",
            true));
    /** Break-speed multiplier. 1.0 = legit vanilla speed. Higher = reset the
     *  post-break cooldown each tick + bump progress to force faster
     *  completion. Anti-cheat flag risk above ~2.0 on paranoid servers. */
    private final FloatSetting speedMultiplier = addSetting(
        new FloatSetting("Speed Multiplier", "Break-speed multiplier (1.0 = legit, higher = faster). Anti-cheat flag risk above ~2.0.",
            1.0f, 10.0f, 1.0f, 1));
    private final EnumSetting tunnelWidth = addSetting(
        new EnumSetting("Tunnel Width", "TUNNEL mode profile width.",
            new String[]{"1x2", "2x2"}, "1x2"));

    // ── Whitelist (WHITELIST mode) ────────────────────────────────
    // A curated set of valuable ores / blocks to break in WHITELIST mode.
    // Toggled individually; defaults to the common "AutoOre" set.
    private final BooleanSetting wlDiamond = addSetting(
        new BooleanSetting("Diamond Ore", "WHITELIST: break diamond ore + deepslate diamond ore.", true));
    private final BooleanSetting wlAncientDebris = addSetting(
        new BooleanSetting("Ancient Debris", "WHITELIST: break ancient debris.", true));
    private final BooleanSetting wlEmerald = addSetting(
        new BooleanSetting("Emerald Ore", "WHITELIST: break emerald ore + deepslate emerald ore.", true));
    private final BooleanSetting wlGold = addSetting(
        new BooleanSetting("Gold Ore", "WHITELIST: break gold ore + deepslate + nether gold ore.", true));
    private final BooleanSetting wlIron = addSetting(
        new BooleanSetting("Iron Ore", "WHITELIST: break iron ore + deepslate iron ore.", false));
    private final BooleanSetting wlCoal = addSetting(
        new BooleanSetting("Coal Ore", "WHITELIST: break coal ore + deepslate coal ore.", false));
    private final BooleanSetting wlLapis = addSetting(
        new BooleanSetting("Lapis Ore", "WHITELIST: break lapis ore + deepslate lapis ore.", false));
    private final BooleanSetting wlRedstone = addSetting(
        new BooleanSetting("Redstone Ore", "WHITELIST: break redstone ore + deepslate redstone ore.", false));
    private final BooleanSetting wlCopper = addSetting(
        new BooleanSetting("Copper Ore", "WHITELIST: break copper ore + deepslate copper ore.", false));
    private final BooleanSetting wlQuartz = addSetting(
        new BooleanSetting("Nether Quartz", "WHITELIST: break nether quartz ore.", false));

    /** The block currently being broken (so we keep breaking the same one
     *  across ticks until it's gone, then pick a new target). */
    private BlockPos currentTarget = null;
    /** The side we started breaking the current target on (vanilla needs the
     *  same Direction each tick for progress to accumulate). */
    private Direction currentSide = Direction.UP;
    /** The original hotbar slot before AutoMine swapped in a tool (for
     *  switch-back). -1 = no swap is active. */
    private int originalSlot = -1;

    /**
     * True while we're holding the vanilla attack (left-click) key down in
     * CROSSHAIR mode so vanilla's real held-click mining path runs. Tracked
     * so we release it cleanly on disable / when the crosshair leaves a
     * breakable block / when switching to a non-CROSSHAIR mode.
     */
    private boolean heldClickActive = false;

    public AutoMine() {
        super("AutoMine", "Automatically breaks blocks (crosshair / tunnel / whitelist modes). No left-click needed.",
            Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        // Release the injected attack key so we don't leave it held down
        // (same pattern Sprint/RtpBaseFinder use). Vanilla's held-click mining
        // path stops on its own once the key is released.
        if (mc.options != null) {
            mc.options.attackKey.setPressed(false);
        }
        heldClickActive = false;
        // Abort any in-progress break so we don't leave a half-broken block
        // that vanilla thinks is still being mined.
        if (mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) {
            try { mc.interactionManager.cancelBlockBreaking(); } catch (Throwable ignored) {}
        }
        restoreOriginalSlot();
        resetState();
    }

    private void resetState() {
        currentTarget = null;
        currentSide = Direction.UP;
        originalSlot = -1;
        heldClickActive = false;
    }

    // ════════════════════════════════════════════════════════════════
    //  onTick — pick a target + drive the breaking pipeline every tick
    //
    //  CROSSHAIR mode uses the REAL vanilla held-left-click code path
    //  (holding the attack key + firing KeyBinding.onKeyPressed on the
    //  first tick) so the server anti-cheat sees the exact packet sequence
    //  a human holding left-click produces — indistinguishable from manual
    //  mining. TUNNEL/WHITELIST keep the direct interactionManager calls
    //  (they target blocks away from the crosshair, so the held-key path
    //  can't reach them without aim rotation).
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) return;
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager im = client.interactionManager;

        // ── Speed multiplier: reset the post-break cooldown each tick so
        //    breaking starts immediately on the next block. At 1.0 we leave
        //    the vanilla cooldown alone (legit-speed mining). This is field-
        //    level manipulation; it zeroes/advances the same fields the
        //    held-click path reads, so it works for BOTH code paths. ──────
        if (speedMultiplier.get() > 1.0f && im instanceof ClientPlayerInteractionManagerAccessor acc) {
            if (acc.lucid$getBlockBreakingCooldown() > 0) {
                acc.lucid$setBlockBreakingCooldown(0);
            }
            // Bump the accumulated progress to force faster completion. We
            // add the per-tick delta scaled by the multiplier so a 2.0x
            // multiplier completes the block in half the ticks, etc.
            float prog = acc.lucid$getCurrentBreakingProgress();
            if (prog > 0f && prog < 1f) {
                float delta = currentTarget != null
                    ? client.world.getBlockState(currentTarget)
                        .calcBlockBreakingDelta(player, client.world, currentTarget)
                    : 0.1f;
                acc.lucid$setCurrentBreakingProgress(Math.min(0.999f,
                    prog + delta * (speedMultiplier.get() - 1.0f)));
            }
        }

        // ── CROSSHAIR mode: held-left-click path ──────────────────────
        if ("CROSSHAIR".equals(mode.get())) {
            tickCrosshairHeld(client, player, im);
            return;
        }

        // ── TUNNEL / WHITELIST: direct interactionManager calls ─────────
        // Make sure the held-click path isn't left running when we switch
        // out of CROSSHAIR mode — release the attack key so the two
        // approaches never fight.
        if (heldClickActive) {
            client.options.attackKey.setPressed(false);
            heldClickActive = false;
        }

        // ── Pick the target block for this tick ──────────────────────
        BlockPos target = pickTarget(client);
        if (target == null) {
            // No target — abort any in-progress break + switch back.
            if (im.isBreakingBlock()) {
                try { im.cancelBlockBreaking(); } catch (Throwable ignored) {}
                currentTarget = null;
            }
            restoreOriginalSlot();
            return;
        }

        // If the target changed, finish/abort the old one and start the new.
        if (currentTarget == null || !currentTarget.equals(target)) {
            // Abort the old break (if any) so vanilla doesn't think we're
            // still mining the old block.
            if (im.isBreakingBlock()) {
                try { im.cancelBlockBreaking(); } catch (Throwable ignored) {}
            }
            currentTarget = target;
            currentSide = bestSide(player, target);

            // ── Auto Tool: swap to the best tool before starting ──────
            if (autoTool.isEnabled()) {
                swapToBestTool(player, client.world.getBlockState(target));
            }

            // Start breaking: attackBlock sends the START_DESTROY_BLOCK
            // packet (via sendSequencedPacket) and sets up the break state.
            im.attackBlock(target, currentSide);
            player.swingHand(Hand.MAIN_HAND);
        } else {
            // Same target as last tick — continue breaking.
            // updateBlockBreakingProgress accumulates the per-tick delta
            // and sends the completion packet when progress >= 1.0.
            im.updateBlockBreakingProgress(target, currentSide);
            player.swingHand(Hand.MAIN_HAND);
        }

        // ── Check if the block is gone ──────────────────────────────
        if (client.world.getBlockState(target).isAir()) {
            // Block broke — clear the target so we pick a new one next tick,
            // and switch back to the original slot if enabled.
            currentTarget = null;
            if (switchBack.isEnabled()) {
                restoreOriginalSlot();
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  CROSSHAIR mode — held-left-click path
    //
    //  We hold the vanilla attack key down + fire KeyBinding.onKeyPressed
    //  on the first tick, mirroring the exact Mouse.onMouseButton mouse-down
    //  sequence. Vanilla's own handleInputEvents then runs the real held-
    //  click mining path: doAttack() → attackBlock (START_DESTROY_BLOCK)
    //  on the press edge, then handleBlockBreaking → updateBlockBreakingProgress
    //  every held tick. Because updateBlockBreakingProgress internally calls
    //  attackBlock when the crosshair moves to a NEW block, vanilla auto-
    //  starts the next block seamlessly — no manual attackBlock/cancel spam,
    //  no currentTarget sticky logic. The packets come from vanilla's code
    //  path, so the server anti-cheat sees a real held left-click.
    //
    //  We only read the crosshair target to decide hold vs release; we
    //  never drive the breaking ourselves.
    // ════════════════════════════════════════════════════════════════
    private void tickCrosshairHeld(MinecraftClient client, ClientPlayerEntity player,
                                   ClientPlayerInteractionManager im) {
        // Read the crosshair target — this is what vanilla will mine.
        HitResult hit = client.crosshairTarget;
        BlockPos target = null;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
            target = bhr.getBlockPos();
        }

        boolean hasBreakable = target != null && isBreakable(
            client.world.getBlockState(target), client, target);

        if (hasBreakable) {
            // ── Auto Tool: swap to the best tool for the crosshair block ──
            // (only when the crosshair block changes, so we don't spam swaps
            //  every tick while mining the same block).
            if (autoTool.isEnabled()
                && (currentTarget == null || !currentTarget.equals(target))) {
                swapToBestTool(player, client.world.getBlockState(target));
            }
            currentTarget = target;

            // First tick of a new hold — simulate the mouse-down edge via
            // KeyBinding.onKeyPressed (increments timesPressed so vanilla's
            // doAttack() fires attackBlock = START_DESTROY_BLOCK). This is
            // the exact call Mouse.onMouseButton makes on a real button-down.
            // Without it, setPressed alone never triggers the start-mining
            // packet. Subsequent ticks just keep the key held so vanilla's
            // handleBlockBreaking drives updateBlockBreakingProgress.
            if (!heldClickActive) {
                client.options.attackKey.setPressed(true);
                InputUtil.Key leftMouse = InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                KeyBinding.onKeyPressed(leftMouse);
                heldClickActive = true;
            } else {
                // Re-assert the held state in case anything cleared it.
                client.options.attackKey.setPressed(true);
            }
        } else {
            // No breakable crosshair target — release the key so vanilla's
            // handleBlockBreaking(false) cancels any in-progress break cleanly.
            if (heldClickActive) {
                client.options.attackKey.setPressed(false);
                heldClickActive = false;
            }
            // Clear the sticky target + switch back the tool when the
            // crosshair leaves a breakable block (matches the no-target path
            // of the direct-call modes).
            if (currentTarget != null && client.world.getBlockState(currentTarget).isAir()) {
                currentTarget = null;
                if (switchBack.isEnabled()) {
                    restoreOriginalSlot();
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Target selection — per-mode
    // ════════════════════════════════════════════════════════════════
    /**
     * Pick the block to break this tick, based on the active mode.
     * Returns null if there's nothing to break.
     */
    private BlockPos pickTarget(MinecraftClient client) {
        String m = mode.get();
        if ("TUNNEL".equals(m)) return pickTunnel(client);
        if ("WHITELIST".equals(m)) return pickWhitelist(client);
        return pickCrosshair(client); // default = CROSSHAIR
    }

    /**
     * CROSSHAIR mode: break whatever block the crosshair is touching.
     * If we're already breaking a block and it's still there, keep breaking
     * it (the crosshair may have drifted, but we want to finish the break).
     */
    private BlockPos pickCrosshair(MinecraftClient client) {
        // If we have a current target that's still solid, keep it — don't
        // re-target every tick or progress resets.
        if (currentTarget != null && !client.world.getBlockState(currentTarget).isAir()) {
            return currentTarget;
        }
        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK
            || !(hit instanceof BlockHitResult bhr)) return null;
        BlockPos pos = bhr.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        if (!isBreakable(state, client, pos)) return null;
        return pos;
    }

    /**
     * TUNNEL mode: break blocks in a 1×2 or 2×2 grid ahead of the player.
     * The grid is centered on the player's facing direction at head+feet
     * level, extending forward up to Range. We pick the closest unbroken
     * solid block in the profile.
     */
    private BlockPos pickTunnel(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        // If we have a current target that's still solid, keep it.
        if (currentTarget != null && !client.world.getBlockState(currentTarget).isAir()) {
            return currentTarget;
        }
        // Determine the facing axis (cardinal only — tunnels are straight).
        Direction facing = player.getHorizontalFacing();
        int maxReach = (int) Math.floor(range.get());
        int feetY = player.getBlockY();
        // Build the list of positions in the tunnel profile, sorted by
        // distance (closest first).
        java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
        for (int fwd = 1; fwd <= maxReach; fwd++) {
            for (BlockPos p : tunnelProfilePositions(player.getBlockPos(), feetY, facing, fwd)) {
                candidates.add(p);
            }
        }
        // Closest-first: prefer blocks at low fwd distance.
        for (BlockPos pos : candidates) {
            BlockState state = client.world.getBlockState(pos);
            if (isBreakable(state, client, pos)) return pos;
        }
        return null;
    }

    /**
     * Build the list of block positions in the tunnel profile at a given
     * forward distance from the player's feet pos.
     */
    private java.util.List<BlockPos> tunnelProfilePositions(BlockPos feet, int feetY,
                                                            Direction facing, int fwd) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        // Forward offset along the facing axis.
        int fx = facing.getOffsetX() * fwd;
        int fz = facing.getOffsetZ() * fwd;
        // Perpendicular axis (for 2×2 width).
        Direction perp = facing.rotateYClockwise();
        int px = perp.getOffsetX();
        int pz = perp.getOffsetZ();

        if ("1x2".equals(tunnelWidth.get())) {
            // Feet + head (feetY and feetY+1).
            out.add(new BlockPos(feet.getX() + fx, feetY,     feet.getZ() + fz));
            out.add(new BlockPos(feet.getX() + fx, feetY + 1, feet.getZ() + fz));
        } else {
            // 2×2: feet+head, each ±1 on the perpendicular axis.
            for (int dy = 0; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx += 2) {
                    out.add(new BlockPos(feet.getX() + fx + px * dx,
                                         feetY + dy,
                                         feet.getZ() + fz + pz * dx));
                }
                // The center column too (so it's a full 2-wide, not just the
                // two side columns).
                out.add(new BlockPos(feet.getX() + fx, feetY + dy, feet.getZ() + fz));
            }
        }
        return out;
    }

    /**
     * WHITELIST mode: scan blocks within Range for any whitelisted block.
     * Picks the closest one. This is the "AutoOre" — only stops to mine
     * valuable targets, ignores stone/netherrack.
     */
    private BlockPos pickWhitelist(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        // If we have a current target that's still solid AND still
        // whitelisted, keep it.
        if (currentTarget != null) {
            BlockState s = client.world.getBlockState(currentTarget);
            if (!s.isAir() && isWhitelisted(s.getBlock())) return currentTarget;
        }
        // Sphere scan around the player.
        BlockPos origin = player.getBlockPos();
        int r = (int) Math.floor(range.get());
        double r2 = range.get() * range.get();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > r2) continue;
                    BlockPos pos = origin.add(dx, dy, dz);
                    BlockState state = client.world.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (!isWhitelisted(state.getBlock())) continue;
                    if (!isBreakable(state, client, pos)) continue;
                    if (distSq < bestDist) {
                        bestDist = distSq;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    // ════════════════════════════════════════════════════════════════
    //  Whitelist check
    // ════════════════════════════════════════════════════════════════
    private boolean isWhitelisted(Block block) {
        if (wlDiamond.isEnabled() && (block == Blocks.DIAMOND_ORE
            || block == Blocks.DEEPSLATE_DIAMOND_ORE)) return true;
        if (wlAncientDebris.isEnabled() && block == Blocks.ANCIENT_DEBRIS) return true;
        if (wlEmerald.isEnabled() && (block == Blocks.EMERALD_ORE
            || block == Blocks.DEEPSLATE_EMERALD_ORE)) return true;
        if (wlGold.isEnabled() && (block == Blocks.GOLD_ORE
            || block == Blocks.DEEPSLATE_GOLD_ORE
            || block == Blocks.NETHER_GOLD_ORE)) return true;
        if (wlIron.isEnabled() && (block == Blocks.IRON_ORE
            || block == Blocks.DEEPSLATE_IRON_ORE)) return true;
        if (wlCoal.isEnabled() && (block == Blocks.COAL_ORE
            || block == Blocks.DEEPSLATE_COAL_ORE)) return true;
        if (wlLapis.isEnabled() && (block == Blocks.LAPIS_ORE
            || block == Blocks.DEEPSLATE_LAPIS_ORE)) return true;
        if (wlRedstone.isEnabled() && (block == Blocks.REDSTONE_ORE
            || block == Blocks.DEEPSLATE_REDSTONE_ORE)) return true;
        if (wlCopper.isEnabled() && (block == Blocks.COPPER_ORE
            || block == Blocks.DEEPSLATE_COPPER_ORE)) return true;
        if (wlQuartz.isEnabled() && block == Blocks.NETHER_QUARTZ_ORE) return true;
        return false;
    }

    // ════════════════════════════════════════════════════════════════
    //  Breakability + tool swap (reused from AutoTool)
    // ════════════════════════════════════════════════════════════════
    /**
     * Is this block breakable by the player? Skips air, unbreakable blocks
     * (bedrock, barriers — negative hardness), and blocks out of reach.
     */
    private boolean isBreakable(BlockState state, MinecraftClient client, BlockPos pos) {
        if (state.isAir()) return false;
        if (state.getBlock().getHardness() < 0.0f) return false; // bedrock/barrier
        // Reach check: must be within Range of the player's eye.
        double dist = Math.sqrt(client.player.squaredDistanceTo(
            Vec3d.ofCenter(pos)));
        return dist <= range.get();
    }

    /**
     * Swap to the best tool in the hotbar for the given block state.
     * Reuses the AutoTool ranking: suitable (right tool type) + highest
     * mining speed. Saves the original slot for switch-back.
     */
    private void swapToBestTool(ClientPlayerEntity player, BlockState state) {
        PlayerInventory inv = player.getInventory();
        int bestSlot = findBestToolSlot(inv, state);
        if (bestSlot < 0 || bestSlot == inv.getSelectedSlot()) return;
        if (originalSlot < 0) originalSlot = inv.getSelectedSlot();
        selectSlot(player, bestSlot);
    }

    private int findBestToolSlot(PlayerInventory inv, BlockState state) {
        int bestSlot = -1;
        float bestSpeed = -1f;
        float handSpeed = Items.AIR.getMiningSpeed(ItemStack.EMPTY, state);
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (!stack.isSuitableFor(state)) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed <= handSpeed) continue;
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /** Pick the side of the target block facing the player (for the
     *  START_DESTROY_BLOCK packet — vanilla uses the side the player is
     *  looking at, but for non-crosshair modes we compute it). */
    private Direction bestSide(ClientPlayerEntity player, BlockPos pos) {
        Vec3d eye = player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d diff = eye.subtract(center);
        double dx = Math.abs(diff.x), dy = Math.abs(diff.y), dz = Math.abs(diff.z);
        if (dx > dy && dx > dz) {
            return diff.x > 0 ? Direction.EAST : Direction.WEST;
        } else if (dy > dz) {
            return diff.y > 0 ? Direction.UP : Direction.DOWN;
        } else {
            return diff.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════
    private void selectSlot(ClientPlayerEntity player, int slot) {
        if (slot < 0 || slot > 8) return;
        PlayerInventory inv = player.getInventory();
        if (inv.getSelectedSlot() == slot) return;
        inv.setSelectedSlot(slot); // vanilla sends its own slot packet — never duplicate it
    }

    private void restoreOriginalSlot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (originalSlot >= 0 && originalSlot <= 8) {
            selectSlot(mc.player, originalSlot);
        }
        originalSlot = -1;
    }
}