package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.IntSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * AutoTool — automatically swaps to the best mining tool for the block
 * you're currently looking at, so your held-mouse breaking is always fast.
 *
 * ── How it works ──────────────────────────────────────────────────
 * Each tick, the module reads the player's crosshair target:
 *   - If you're looking at a block, it scans the hotbar (slots 0-8) for
 *     the best tool for that block's {@link BlockState}.
 *   - "Best" = the slot whose {@link ItemStack#isSuitableFor} returns
 *     true (right tool type — pickaxe for stone, shovel for dirt, axe for
 *     wood, shears for leaves, sword for cobwebs/plants) AND whose
 *     {@link ItemStack#getMiningSpeedMultiplier} is highest (netherite >
 *     diamond > iron > stone > wood). If no hotbar tool is suitable, the
 *     module does nothing — you keep mining with whatever you're holding.
 *   - When the best slot differs from the currently-selected slot, it
 *     swaps to it (sends {@link UpdateSelectedSlotC2SPacket} so the server
 *     sees the change). The player's held left-mouse does the actual
 *     breaking — AutoTool just makes sure the right tool is in hand.
 *
 * ── Switch-back ───────────────────────────────────────────────────
 * With "Switch Back" on, the module remembers the slot you were holding
 * before the swap and restores it after you stop looking at a breakable
 * block (or the block breaks), waiting `Switch Back Delay` ticks so the
 * final break-progress packet flushes before the slot-change arrives
 * (same race-fix pattern as AnchorMacro / KeyPearl). Off = you switch
 * back manually (the default, matching the user's request).
 *
 * ── Why not auto-mine ──────────────────────────────────────────────
 * AutoTool is a TOOL SWITCHER, not an auto-miner. The player holds the
 * mouse button; AutoTool just keeps the optimal tool equipped so the
 * breaking is always at max speed. This keeps it feeling natural (you
 * control when to mine) while removing the "open inventory, drag the
 * pickaxe to slot 1, close inventory" friction.
 *
 * Settings:
 *   Switch Back       — restore the original slot after you stop mining
 *   Switch Back Delay  — ticks to wait before switching back (race-fix)
 *   Prefer Drops       — only swap to tools that will actually drop the
 *                        block (Item.isCorrectForDrops), not just mine
 *                        it faster. Off = swap for speed even if no drops.
 *   Only Tools         — only swap when the block REQUIRES a tool (e.g.
 *                        stone needs a pickaxe; dirt doesn't need a
 *                        shovel to drop). Off = swap for any block where
 *                        a tool is faster than the hand.
 */
public class AutoTool extends Module {

    /** Restore the original hotbar slot after you stop mining a tool-needing
     *  block. Off = you switch back manually. */
    private final BooleanSetting switchBack = addSetting(
        new BooleanSetting("Switch Back", "Restore the original hotbar slot after you stop mining (off = manual switch-back).",
            false));
    /** Ticks to wait after the last mining target before switching back, so
     *  the final break-progress packet flushes before the slot-change arrives
     *  (server race-fix, same pattern as AnchorMacro). */
    private final IntSetting switchBackDelay = addSetting(
        new IntSetting("Switch Back Delay", "Ticks to wait before switching back (server race-fix).",
            0, 20, 2, 1));
    /** Only swap to tools that will actually DROP the block (not just mine it
     *  faster). Uses {@link net.minecraft.item.Item#isCorrectForDrops}. */
    private final BooleanSetting preferDrops = addSetting(
        new BooleanSetting("Prefer Drops", "Only swap to tools that will actually drop the block (not just mine it faster).",
            true));
    /** Only swap when the block REQUIRES a tool to drop (e.g. stone needs a
     *  pickaxe; dirt doesn't need a shovel). Off = swap for any block where a
     *  tool is faster than the bare hand. */
    private final BooleanSetting onlyTools = addSetting(
        new BooleanSetting("Only Tools", "Only swap when the block requires a tool to drop (off = swap for any speed gain).",
            false));
    /** The slot the player was holding before AutoTool swapped in a tool.
     *  -1 = no swap is active (we're holding whatever the player chose). */
    private int originalSlot = -1;
    /** The slot AutoTool swapped to (so we know we're "in tool mode"). */
    private int toolSlot = -1;
    /** The BlockPos we last swapped-for. When the crosshair moves off this
     *  block (or it breaks), we start the switch-back timer. */
    private BlockPos lastTargetPos = null;
    /** Switch-back countdown: ticks remaining before we restore the original
     *  slot. -1 = not counting down. */
    private int switchBackCountdown = -1;
    public AutoTool() {
        super("AutoTool",
            "Automatically swaps to the best mining tool for the block you're looking at (hold mouse to break).",
            Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        // If we were mid-swap, snap back to the original slot so we don't
        // strand the player holding a tool they didn't choose.
        restoreOriginalSlot();
        resetState();
    }

    private void resetState() {
        originalSlot = -1;
        toolSlot = -1;
        lastTargetPos = null;
        switchBackCountdown = -1;
    }

    // ════════════════════════════════════════════════════════════════
    //  onTick — runs every tick (20Hz) to track the crosshair target
    // ════════════════════════════════════════════════════════════════
    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) return;
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager im = client.interactionManager;
        PlayerInventory inv = player.getInventory();

        // ── Read the crosshair target ───────────────────────────────
        HitResult hit = client.crosshairTarget;
        BlockPos targetPos = null;
        BlockState targetState = null;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
            targetPos = bhr.getBlockPos();
            targetState = client.world.getBlockState(targetPos);
        }

        // ── If we have a target block, find the best tool ────────────
        if (targetPos != null && targetState != null) {
            // Cancel the switch-back countdown — we're actively looking at a
            // block again, so don't switch back yet.
            switchBackCountdown = -1;

            // Skip air / unbreakable blocks (bedrock, barriers) — they don't
            // benefit from a tool swap. Unbreakable blocks have negative
            // hardness (bedrock = -1, barrier = -1).
            if (targetState.isAir() || targetState.getBlock().getHardness() < 0.0f) {
                handleNoTarget(inv);
                return;
            }

            // Find the best tool slot for this block.
            int bestSlot = findBestToolSlot(inv, targetState);
            if (bestSlot < 0) {
                // No suitable tool in the hotbar — don't swap. Don't start a
                // switch-back yet either: the player might still be breaking
                // the block by hand, and we only switch back when the target
                // changes / clears.
                return;
            }

            int currentSlot = inv.getSelectedSlot();
            if (bestSlot != currentSlot) {
                // ── Swap to the best tool ─────────────────────────────
                // Save the original slot ONLY if we're not already in a
                // swap (so a chain of block→block swaps keeps the very first
                // original slot, not the previous tool).
                if (originalSlot < 0) {
                    originalSlot = currentSlot;
                }
                toolSlot = bestSlot;
                lastTargetPos = targetPos;
                selectSlot(player, bestSlot);

            } else {
                // Already holding the best tool — just remember the target
                // so we know when to switch back.
                lastTargetPos = targetPos;
            }
            return;
        }

        // ── No target block (looking at air/entity) ─────────────────
        handleNoTarget(inv);
    }

    /**
     * Handle the "no mining target" case — start the switch-back countdown
     * if we were holding a swapped tool, and restore the original slot when
     * the countdown expires.
     */
    private void handleNoTarget(PlayerInventory inv) {
        if (originalSlot < 0) return; // no swap was active

        // Start the countdown if it's not already running.
        if (switchBackCountdown < 0) {
            switchBackCountdown = switchBackDelay.get();
        }
        if (switchBackCountdown > 0) {
            switchBackCountdown--;
            return;
        }
        // Countdown expired — switch back.
        if (switchBack.isEnabled()) {
            restoreOriginalSlot();
        }
        // Either way, clear the swap state (we're no longer holding a
        // swapped tool, or we just restored the original).
        resetState();
    }

    /**
     * Find the best hotbar slot for the given block state.
     *
     * "Best" = the slot whose tool is suitable for the block (right tool
     * type) AND has the highest mining speed. If {@link #preferDrops} is on,
     * the tool must also actually drop the block (Item.isCorrectForDrops).
     * If {@link #onlyTools} is on, we only consider swapping when the block
     * requires a tool (isToolRequired).
     *
     * @return the best slot index (0-8), or -1 if no suitable tool is found
     */
    private int findBestToolSlot(PlayerInventory inv, BlockState state) {
        // If the block doesn't require a tool AND onlyTools is on, don't swap
        // (the player can mine it by hand at full speed — e.g. dirt, grass).
        if (onlyTools.isEnabled() && !state.isToolRequired()) {
            return -1;
        }

        int bestSlot = -1;
        float bestSpeed = -1f;
        // The bare-hand mining speed — we only swap if a tool is FASTER than
        // this (so we don't swap to a tool that's actually slower than hand).
        float handSpeed = Items.AIR.getMiningSpeed(ItemStack.EMPTY, state);

        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;

            // Suitability: is this the right tool TYPE for the block?
            // (pickaxe for stone, shovel for dirt, axe for wood, shears for
            // leaves, sword for cobwebs.)
            if (!stack.isSuitableFor(state)) continue;

            // Drops check (optional): will this tool actually drop the block?
            // For "Prefer Drops" we require the tool to be correct for drops.
            if (preferDrops.isEnabled() && !stack.getItem().isCorrectForDrops(stack, state)) {
                continue;
            }

            float speed = stack.getMiningSpeedMultiplier(state);
            // Only swap if the tool is faster than the bare hand.
            if (speed <= handSpeed) continue;

            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    // ════════════════════════════════════════════════════════════════
    //  Helpers (same pattern as KeyPearl / AnchorMacro)
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
    }
}