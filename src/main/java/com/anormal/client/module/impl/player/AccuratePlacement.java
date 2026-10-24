package com.anormal.client.module.impl.player;

import com.anormal.client.mixin.MinecraftClientAccessor;
import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.KeybindSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

/**
 * AccuratePlacement — replaces Minecraft's tick-based block-placement cooldown
 * with spatial raycast detection, so holding right-click places a block the
 * instant your crosshair enters a NEW block coordinate or face (instead of
 * waiting the vanilla 4-tick {@code itemUseCooldown}).
 *
 * <h3>Vanilla vs. this module</h3>
 * Vanilla: hold right-click → place a block → set {@code itemUseCooldown = 4}
 * → even if you flick to a fresh air pocket 1 tick later, no placement until
 * the timer elapses → fast bridging leaves gaps.
 * This module: every mc tick while you hold right-click, compare the
 * current crosshair target (BlockPos + face) against the last one we placed
 * against. If they differ, zero {@code itemUseCooldown} and re-fire
 * {@code interactionManager.interactBlock(...)} so a block drops immediately.
 * If they're the same, leave the vanilla cooldown alone (no spam-placing into
 * the same block).
 *
 * <h3>Safeguards (per the reference spec)</h3>
 * <ul>
 *   <li><b>Container guard</b> — if the targeted block has a non-{@code PASS}
 *       {@code onUseWithItem} outcome (chest/shulker/furnace/etc.), we DON'T
 *       force-fire on it; we let vanilla handle the right-click so we don't
 *       spam-open inventories. Optionally auto-sneak to suppress the open.</li>
 *   <li><b>Self-hitbox guard</b> — before re-firing, we check the place-position
 *       (the neighbour air block on the clicked face) isn't inside the player's
 *       bounding box, so FPS lag can't double-place a block into yourself.</li>
 *   <li><b>GUI guard</b> — never fires while any screen is open.</li>
 *   <li><b>Real input only</b> — only acts while you are physically holding the
 *       vanilla use key; no packet spoofing, no forged interactions.</li>
 * </ul>
 *
 * <h3>Implementation note</h3>
 * No new mixin required — {@code itemUseCooldown} is already exposed by
 * {@link MinecraftClientAccessor#lucid$setItemUseCooldown(int)} (the same
 * accessor CrystalOptimizer/AutoCrystal use). We call the public
 * {@code interactionManager.interactBlock(player, Hand.MAIN_HAND, bhr)} —
 * the exact path vanilla {@code doItemUse} takes.
 *
 * Settings:
 *   Toggle Key, Require Sneak On Containers, Cancel On Self Hit.
 */
public class AccuratePlacement extends Module {

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to enable/disable accurate placement. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final BooleanSetting sneakOnContainers = addSetting(
        new BooleanSetting("Auto-Sneak On Containers", "When targeting a container (chest/shulker/etc.), hold sneak so right-click places against it instead of opening the GUI.",
            true));
    public final NumberSetting placeDelay = addSetting(
            new NumberSetting("Place Delay", "Ticks to wait between placements (0 = instant)", 0.0, 0.0, 8.0, 1.0));

    public final BooleanSetting requireReplaceable = addSetting(
            new BooleanSetting("Require Air", "Only place into air/replaceable blocks, so no block is wasted", true));

    public final BooleanSetting ignoreFaceJitter = addSetting(
            new BooleanSetting("Ignore Face Jitter", "Do not re-place when the crosshair only flickers between faces of the same block", true));

    public final BooleanSetting swing = addSetting(
            new BooleanSetting("Swing", "Swing the hand after placing", true));

    private final BooleanSetting cancelOnSelfHit = addSetting(
        new BooleanSetting("Cancel On Self Hit", "Skip placement if the target position would land inside your own hitbox (FPS-lag double-place guard).",
            true));

    // Last (blockPos + side) we forced a placement against. When the crosshair
    // moves to a different block or face, we re-fire. Null = none yet.
    private BlockPos lastPos = null;
    private Direction lastSide = null;

    // Was the vanilla sneak key pressed by us last tick? We restore the real
    // state on disable / when we stop auto-sneaking so we never leave sneak
    // stuck on.
    private boolean autoSneaking = false;

    /** Tick counter for the placement throttle. */
    private int placeCooldown = 0;

    /** Last tick a placement actually went out, used to damp face flicker. */
    private long lastPlaceTick = -100L;

    // Edge-detection for the toggle key (set in onTickAlways).
    private boolean wasToggleKeyDown = false;

    public AccuratePlacement() {
        super("AccuratePlacement",
            "Removes the 4-tick placement cooldown — places a block the instant your crosshair moves to a new block/face. Fast bridging without gaps.",
            Category.PLAYER);
    }

    @Override
    public void onEnable() {
        lastPos = null;
        lastSide = null;
        autoSneaking = false;
    }

    @Override
    public void onDisable() {
        // Release any auto-sneak we imposed.
        releaseAutoSneak();
        lastPos = null;
        lastSide = null;
    }

    @Override
    public void onTickAlways() {
        if (mc.player == null || mc.getWindow() == null) return;
        int key = toggleKey.getKey();
        if (key < 0) return;

        long handle = mc.getWindow().getHandle();
        boolean isDown;
        if (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_8) {
            isDown = GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS;
        } else {
            isDown = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        }
        if (isDown && !wasToggleKeyDown) {
            setEnabled(!isEnabled());
        }
        wasToggleKeyDown = isDown;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return; // never fire while a GUI is open

        // Only act while the user is physically holding the vanilla use key.
        boolean useKeyDown = mc.options.useKey.isPressed();
        if (!useKeyDown) {
            // Use key released — clear cached target so the next press re-fires
            // on the first block under the crosshair, and release auto-sneak.
            lastPos = null;
            lastSide = null;
            releaseAutoSneak();
            return;
        }

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult bhr)) {
            // Aiming at nothing or an entity — let vanilla handle it, just
            // clear our cache so we re-fire when a block comes back into view.
            lastPos = null;
            lastSide = null;
            releaseAutoSneak();
            return;
        }

        BlockPos currentPos = bhr.getBlockPos();
        Direction currentSide = bhr.getSide();

        // ── Container guard ──────────────────────────────────────
        // If the targeted block is a container (onUseWithItem returns an
        // interactive ActionResult), holding right-click would open its GUI.
        // We auto-sneak so vanilla treats the click as a placement-against
        // instead of an open — but we DON'T force-fire interactBlock on it,
        // because the sneak-shifted vanilla path is the safe one.
        BlockState targetState = mc.world.getBlockState(currentPos);
        boolean isContainer = isInteractableContainer(targetState, mc, currentPos, bhr);
        if (isContainer && sneakOnContainers.isEnabled()) {
            applyAutoSneak();
            // Still keep the cache fresh so we re-fire on the NEXT non-container
            // block the crosshair moves to.
            lastPos = currentPos;
            lastSide = currentSide;
            // Let vanilla handle the (now sneak-shifted) placement.
            return;
        }
        // Not a container, or container-guard off — make sure we're not
        // holding sneak from a previous container.
        releaseAutoSneak();

        // ── Self-hitbox guard ────────────────────────────────────
        // The position a block would be placed at is the air block adjacent
        // to the clicked face. If that's inside the player's box, skip —
        // prevents FPS-lag double-placing a block into yourself.
        if (cancelOnSelfHit.isEnabled()) {
            BlockPos placeAt = currentPos.offset(currentSide);
            if (wouldIntersectPlayer(mc, placeAt)) {
                lastPos = currentPos;
                lastSide = currentSide;
                return;
            }
        }

        // ── Only place into air / replaceable blocks ──────────────
        // Without this the module happily clicks a solid face forever, which is what made it
        // feel like a spam-place module instead of an accurate one.
        if (requireReplaceable.isEnabled()) {
            BlockPos placeAt = currentPos.offset(currentSide);
            BlockState placeState = mc.world.getBlockState(placeAt);
            if (!placeState.isAir() && !placeState.isReplaceable()) {
                lastPos = currentPos;
                lastSide = currentSide;
                return;
            }
        }

        // ── Throttle ──────────────────────────────────────────────
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        // ── Spatial placement: re-fire only when the target moved ──
        if (currentPos.equals(lastPos) && currentSide == lastSide) {
            // Same block + same face as last placement — let vanilla's
            // 4-tick cooldown govern repeat placement (no spam).
            return;
        }

        // Same block, different face, immediately after a placement: that is the crosshair
        // flickering on an edge, not the player choosing a new spot. Re-placing here is exactly
        // what wastes blocks and looks like FastPlace.
        if (ignoreFaceJitter.isEnabled()
                && currentPos.equals(lastPos)
                && System.currentTimeMillis() - lastPlaceTick < 90L) {
            return;
        }

        // Crosshair entered a new block/face → zero the cooldown and re-fire.
        ((MinecraftClientAccessor) mc).lucid$setItemUseCooldown(0);
        ClientPlayerInteractionManager im = mc.interactionManager;
        ClientPlayerEntity player = mc.player;
        im.interactBlock(player, Hand.MAIN_HAND, bhr);
        if (swing.isEnabled()) {
            player.swingHand(Hand.MAIN_HAND);
        }

        lastPos = currentPos;
        lastSide = currentSide;
        lastPlaceTick = System.currentTimeMillis();
        placeCooldown = placeDelay.getValue().intValue();
    }

    // ── Helpers ────────────────────────────────────────────────────

    /**
     * Best-effort container detection. We can't call {@code onUseWithItem}
     * ourselves safely (it has side effects on real worlds), so we use the
     * block's class name as a heuristic — the common interactable storage
     * blocks all extend {@code BlockWithEntity} / implement
     * {@code InventoryProvider} / are named *Chest/*ShulkerBox/*Barrel/
     * *Furnace/*Hopper/*Dropper/*Dispenser.
     */
    private boolean isInteractableContainer(BlockState state, MinecraftClient mc, BlockPos pos, BlockHitResult bhr) {
        if (state == null || state.isAir()) return false;
        String cls = state.getBlock().getClass().getSimpleName();
        return cls.contains("Chest")
            || cls.contains("ShulkerBox")
            || cls.contains("Barrel")
            || cls.contains("Furnace")
            || cls.contains("BlastFurnace")
            || cls.contains("Smoker")
            || cls.contains("Hopper")
            || cls.contains("Dropper")
            || cls.contains("Dispenser")
            || cls.contains("Lectern")
            || cls.contains("BrewingStand")
            || cls.contains("Beacon")
            || cls.contains("Anvil")
            || cls.contains("CraftingTable")
            || cls.contains("EnchantingTable")
            || cls.contains("Door")
            || cls.contains("FenceGate")
            || cls.contains("Trapdoor")
            || cls.contains("Bed");
    }

    /**
     * True if placing a full 1×1×1 block at {@code pos} would intersect the
     * player's bounding box (the FPS-lag "place into yourself" case).
     */
    private boolean wouldIntersectPlayer(MinecraftClient mc, BlockPos pos) {
        if (mc.player == null) return false;
        net.minecraft.util.math.Box blockBox = new net.minecraft.util.math.Box(
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        return mc.player.getBoundingBox().intersects(blockBox);
    }

    /** Hold the sneak key down (auto-sneak against containers). */
    private void applyAutoSneak() {
        if (mc.player == null) return;
        if (!autoSneaking) {
            mc.options.sneakKey.setPressed(true);
            autoSneaking = true;
        }
    }

    /** Release the auto-sneak if we imposed it, restoring the player's real
     *  physical sneak state by polling GLFW directly (so we don't leave sneak
     *  stuck on if the user wasn't holding shift). */
    private void releaseAutoSneak() {
        if (!autoSneaking || mc.options == null || mc.getWindow() == null) return;
        long handle = mc.getWindow().getHandle();
        boolean physicallyHeld =
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        mc.options.sneakKey.setPressed(physicallyHeld);
        autoSneaking = false;
    }
}