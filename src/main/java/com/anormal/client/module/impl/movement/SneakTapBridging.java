package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.HeaderSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Sneak-tap bridging with three levels of automation.
 *
 * <p>The module never turns the camera: movement is produced by pressing the ordinary WASD
 * keys with a forward/strafe decomposition of the wanted world direction, so whatever the
 * player is looking at stays untouched.</p>
 *
 * <p>Three modes:
 * <ul>
 *   <li><b>Manual Sneak-Tap</b> - only the shift key is driven; walking and placing stay manual.</li>
 *   <li><b>Assisted Bridging</b> - shift timing is automatic, optional auto place / auto switch.</li>
 *   <li><b>Full Auto Bridging</b> - walks, places and cycles shift by itself.</li>
 * </ul>
 */
public class SneakTapBridging extends Module {

    // ------------------------------------------------------------------
    //  General
    // ------------------------------------------------------------------
    public final ModeSetting bridgingMode = new ModeSetting("Bridging Mode",
            "How much of the bridging the module takes over",
            "Assisted Bridging", "Manual Sneak-Tap", "Assisted Bridging", "Full Auto Bridging");

    public final ModeSetting directionMode = new ModeSetting("Direction Mode",
            "Lock on Activation keeps the heading you had when enabling, Follow View tracks the camera",
            "Follow View", "Lock on Activation", "Follow View");

    public final ModeSetting buildDirection = new ModeSetting("Build Direction",
            "Follow Input builds where you are actually walking (S builds behind you), Follow View always builds ahead",
            "Follow Input", "Follow Input", "Follow View");

    public final ModeSetting rotationMode = new ModeSetting("Rotation Mode",
            "Silent turns to the block only for the click, Visible keeps the camera there, None never turns (detectable)",
            "Silent", "Silent", "Visible", "None");

    public final NumberSetting startDelay = new NumberSetting("Start Delay",
            "Milliseconds to wait after the module turns on", 0.0, 0.0, 3000.0, 50.0);

    public final BooleanSetting onlyWhileMoving = new BooleanSetting("Only While Moving",
            "Only run the sneak cycle while the player is actually moving", false);

    public final BooleanSetting onlyWithBlocks = new BooleanSetting("Only With Blocks",
            "Do nothing while the hotbar has no placeable block", true);

    public final BooleanSetting requirePlaceInput = new BooleanSetting("Require Place Input",
            "Only act while the use key is held down", false);

    public final BooleanSetting pauseOnGui = new BooleanSetting("Pause On GUI",
            "Release every synthetic key while a screen is open", true);

    public final BooleanSetting debugMode = new BooleanSetting("Debug Mode",
            "Show the internal state on screen", false);

    // ------------------------------------------------------------------
    //  Sneak timing
    // ------------------------------------------------------------------
    public final ModeSetting sneakMode = new ModeSetting("Sneak Mode",
            "Which sneak behaviour to use", "Sneak-Tap",
            "Manual", "Sneak-Tap", "Continuous Sneak", "Auto Edge Sneak");

    public final ModeSetting timingMode = new ModeSetting("Timing Mode",
            "Fixed uses the durations as-is, Adaptive learns from placements, Place Sync waits for the block",
            "Fixed", "Fixed", "Adaptive", "Place Sync");

    public final NumberSetting sneakDuration = new NumberSetting("Sneak Duration",
            "How long shift stays pressed", 120.0, 30.0, 1000.0, 10.0);

    public final NumberSetting releaseDuration = new NumberSetting("Release Duration",
            "How long shift stays released", 80.0, 20.0, 1000.0, 10.0);

    public final NumberSetting initialSneakDelay = new NumberSetting("Initial Sneak Delay",
            "Delay before the first sneak of the session", 0.0, 0.0, 2000.0, 25.0);

    public final NumberSetting cycleDelay = new NumberSetting("Cycle Delay",
            "Extra pause between two sneak cycles", 0.0, 0.0, 1000.0, 10.0);

    public final NumberSetting minSneakDuration = new NumberSetting("Minimum Sneak Duration",
            "Lower bound for the sneak phase", 50.0, 20.0, 1000.0, 10.0);

    public final NumberSetting maxSneakDuration = new NumberSetting("Maximum Sneak Duration",
            "Upper bound for the sneak phase", 400.0, 50.0, 2000.0, 10.0);

    public final NumberSetting minReleaseDuration = new NumberSetting("Minimum Release Duration",
            "Lower bound for the release phase", 30.0, 10.0, 1000.0, 10.0);

    public final NumberSetting maxReleaseDuration = new NumberSetting("Maximum Release Duration",
            "Upper bound for the release phase", 300.0, 20.0, 1500.0, 10.0);

    public final NumberSetting timingAdjustment = new NumberSetting("Timing Adjustment",
            "Milliseconds added to both phases (- slows down, + speeds up the cycle)", 0.0, -100.0, 100.0, 5.0);

    public final BooleanSetting adaptiveTiming = new BooleanSetting("Adaptive Timing",
            "Learn the phase lengths from placement results", true);

    public final BooleanSetting respectManualSneak = new BooleanSetting("Respect Manual Sneak",
            "Never release a shift the player is holding by hand", true);

    public final BooleanSetting manualInputPriority = new BooleanSetting("Manual Input Priority",
            "Hand the controls back the moment the player presses a movement key", true);

    // ------------------------------------------------------------------
    //  Movement
    // ------------------------------------------------------------------
    public final BooleanSetting autoForward = new BooleanSetting("Auto Forward",
            "Walk forward automatically", false);

    public final ModeSetting movementMode = new ModeSetting("Movement Mode",
            "Manual Input keeps your own keys, Auto Forward drives them", "Manual Input",
            "Manual Input", "Auto Forward");

    public final BooleanSetting movementCheck = new BooleanSetting("Movement Check",
            "Do not walk when the next block is missing and cannot be placed", true);

    public final BooleanSetting groundCheck = new BooleanSetting("Ground Check",
            "Only walk while standing on the ground", true);

    public final BooleanSetting edgeAwareness = new BooleanSetting("Edge Awareness",
            "Detect the bridge edge in front of you", true);

    public final BooleanSetting autoCenter = new BooleanSetting("Auto Center",
            "Keep you in the middle of the bridge while you walk (only when the module is not driving the keys)", true);

    public final NumberSetting movementStartDelay = new NumberSetting("Movement Start Delay",
            "Milliseconds before auto forward starts", 0.0, 0.0, 2000.0, 25.0);

    public final NumberSetting movementStopDelay = new NumberSetting("Movement Stop Delay",
            "Milliseconds to wait before auto forward resumes after a safety stop", 150.0, 0.0, 2000.0, 25.0);

    public final BooleanSetting autoSprint = new BooleanSetting("Auto Sprint",
            "Hold sprint while walking", false);

    public final BooleanSetting pauseWhenUnsafe = new BooleanSetting("Pause When Unsafe",
            "Stop the whole cycle when the path ahead cannot be continued", true);

    public final BooleanSetting stopOnDeath = new BooleanSetting("Stop On Death",
            "Turn the module off when the player dies", true);

    public final BooleanSetting stopOnDisconnect = new BooleanSetting("Stop On Disconnect",
            "Turn the module off when the world is gone", true);

    // ------------------------------------------------------------------
    //  Placement
    // ------------------------------------------------------------------
    public final BooleanSetting autoPlaceBlock = new BooleanSetting("Auto Place Block",
            "Place the bridge blocks for the player", true);

    public final ModeSetting placementMode = new ModeSetting("Placement Mode",
            "Manual leaves placing to the player", "Automatic", "Manual", "Automatic");

    public final ModeSetting placementTarget = new ModeSetting("Placement Target",
            "Bridge Edge places the next step, Underfoot fills below you", "Bridge Edge",
            "Bridge Edge", "Underfoot");

    public final BooleanSetting syncWithPlacement = new BooleanSetting("Sync With Placement",
            "Never walk onto a block that has not been placed yet", true);

    public final NumberSetting placementDelay = new NumberSetting("Placement Delay",
            "Milliseconds between two placements", 50.0, 0.0, 1000.0, 10.0);

    public final NumberSetting minPlacementDelay = new NumberSetting("Minimum Placement Delay",
            "Lower bound used by adaptive placement delay", 20.0, 0.0, 500.0, 5.0);

    public final NumberSetting maxPlacementDelay = new NumberSetting("Maximum Placement Delay",
            "Upper bound used by adaptive placement delay", 200.0, 10.0, 2000.0, 5.0);

    public final BooleanSetting waitForPlacementResult = new BooleanSetting("Wait For Placement Result",
            "Do not send the next click before the previous one resolved", true);

    public final BooleanSetting placementConfirmation = new BooleanSetting("Placement Confirmation",
            "Count a placement only when the client world really shows the new block", true);

    public final NumberSetting placementRetryDelay = new NumberSetting("Placement Retry Delay",
            "Milliseconds before the same spot is tried again", 250.0, 20.0, 3000.0, 10.0);

    public final NumberSetting maxFailedAttempts = new NumberSetting("Maximum Failed Attempts",
            "Give up on the current spot after this many failures", 4.0, 1.0, 20.0, 1.0);

    public final BooleanSetting pauseWhenNoValidTarget = new BooleanSetting("Pause When No Valid Target",
            "Stop the cycle instead of running forward into nothing", true);

    public final BooleanSetting respectManualPlacement = new BooleanSetting("Respect Manual Placement",
            "Never place while the player is using the item key by hand", true);

    // ------------------------------------------------------------------
    //  Auto switch
    // ------------------------------------------------------------------
    public final BooleanSetting autoSwitchBlock = new BooleanSetting("Auto Switch Block",
            "Switch to another hotbar slot when the blocks run out", true);

    public final ModeSetting switchTrigger = new ModeSetting("Switch Trigger",
            "What has to happen before a switch is considered", "Current Stack Empty",
            "Current Stack Empty", "Block Unavailable");

    public final BooleanSetting searchEntireHotbar = new BooleanSetting("Search Entire Hotbar",
            "Scan all nine slots instead of the ones after the current one", true);

    public final ModeSetting blockSelection = new ModeSetting("Block Selection",
            "How the next block is chosen", "First Valid",
            "First Valid", "Most Blocks", "Preferred Block");

    public final ModeSetting preferredBlock = new ModeSetting("Preferred Block",
            "Block that wins over everything else when it is available", "Cobblestone",
            "Cobblestone", "Stone", "Dirt", "Netherrack", "End Stone", "Obsidian",
            "Planks", "Wool", "Concrete", "Terracotta", "Deepslate");

    public final NumberSetting minimumStackAmount = new NumberSetting("Minimum Stack Amount",
            "Ignore stacks smaller than this", 1.0, 1.0, 64.0, 1.0);

    public final BooleanSetting skipInvalidItems = new BooleanSetting("Skip Invalid Items",
            "Ignore tools, food, potions and anything that is not a block", true);

    public final NumberSetting switchDelay = new NumberSetting("Switch Delay",
            "Milliseconds between two automatic slot switches", 150.0, 0.0, 2000.0, 10.0);

    public final BooleanSetting switchBack = new BooleanSetting("Switch Back",
            "Return to the slot you started on when the module turns off", true);

    public final BooleanSetting continueBridging = new BooleanSetting("Continue Bridging",
            "Keep placing right after a switch", true);

    public final BooleanSetting stopIfNoBlocks = new BooleanSetting("Stop If No Blocks",
            "Turn the module off when no placeable block is left", false);

    public final BooleanSetting respectManualSlotSwitching = new BooleanSetting("Respect Manual Slot Switching",
            "Do not override a slot the player picked by hand", true);

    // ------------------------------------------------------------------
    //  Safety
    // ------------------------------------------------------------------
    public final BooleanSetting safeDisable = new BooleanSetting("Safe Disable",
            "Release every synthetic input when the module stops", true);

    public final BooleanSetting restoreInputState = new BooleanSetting("Restore Input State",
            "Put the hotbar slot and the keys back the way they were", true);

    public final BooleanSetting stopOnInvalidPlayer = new BooleanSetting("Stop On Invalid Player",
            "Turn the module off when the player entity disappears", true);

    public final BooleanSetting pauseOnWorldChange = new BooleanSetting("Pause On World Change",
            "Turn the module off when the dimension or world changes", true);

    // ------------------------------------------------------------------
    //  Runtime state
    // ------------------------------------------------------------------
    private static final long CONFIRM_WINDOW_MS = 250L;

    private long enabledAt;
    private long lastPlaceAt;
    private long lastSwitchAt;
    private long lastStopAt;
    private long manualSlotUntil;

    private boolean cycleStarted;
    private boolean sneakPhase;
    private long phaseStart;
    private long phaseEnd;

    private boolean awaitingResult;
    private BlockPos pendingTarget;
    private long pendingSince;
    private int failedAttempts;
    private long failedUntil;
    /** Spot that already used up its attempts; a new spot resets the counter. */
    private BlockPos failedTarget;

    private double adaptiveOffset;

    private int stepX;
    private int stepZ;
    private float bridgeYaw;
    private float lockedYaw;

    private int originalSlot = -1;
    private int lastOurSlot = -1;

    // Input ownership: a key is only released when this module was the one that pressed it,
    // so a key the player holds by hand is never taken away from them.
    private static final int K_FORWARD = 0;
    private static final int K_BACK = 1;
    private static final int K_LEFT = 2;
    private static final int K_RIGHT = 3;
    private static final int K_SNEAK = 4;
    private static final int K_SPRINT = 5;
    private final boolean[] owned = new boolean[6];
    private final boolean[] centerOwned = new boolean[4];

    private World enabledWorld;
    private String debugLine = "";
    private String status = "";
    private boolean unsafe;

    public SneakTapBridging() {
        super("Sneak-Tap Bridging", "Sneak-tap bridging with manual, assisted and full auto modes", Category.MOVEMENT);

        addSetting(new HeaderSetting("General"));
        addSetting(bridgingMode);
        addSetting(directionMode);
        addSetting(buildDirection);
        addSetting(rotationMode);
        addSetting(startDelay);
        addSetting(onlyWhileMoving);
        addSetting(onlyWithBlocks);
        addSetting(requirePlaceInput);
        addSetting(pauseOnGui);
        addSetting(debugMode);

        addSetting(new HeaderSetting("Sneak Timing"));
        addSetting(sneakMode);
        addSetting(timingMode);
        addSetting(sneakDuration);
        addSetting(releaseDuration);
        addSetting(initialSneakDelay);
        addSetting(cycleDelay);
        addSetting(minSneakDuration);
        addSetting(maxSneakDuration);
        addSetting(minReleaseDuration);
        addSetting(maxReleaseDuration);
        addSetting(timingAdjustment);
        addSetting(adaptiveTiming);
        addSetting(respectManualSneak);
        addSetting(manualInputPriority);

        addSetting(new HeaderSetting("Movement"));
        addSetting(autoForward);
        addSetting(movementMode);
        addSetting(movementCheck);
        addSetting(groundCheck);
        addSetting(edgeAwareness);
        addSetting(autoCenter);
        addSetting(movementStartDelay);
        addSetting(movementStopDelay);
        addSetting(autoSprint);
        addSetting(pauseWhenUnsafe);
        addSetting(stopOnDeath);
        addSetting(stopOnDisconnect);

        addSetting(new HeaderSetting("Placement"));
        addSetting(autoPlaceBlock);
        addSetting(placementMode);
        addSetting(placementTarget);
        addSetting(syncWithPlacement);
        addSetting(placementDelay);
        addSetting(minPlacementDelay);
        addSetting(maxPlacementDelay);
        addSetting(waitForPlacementResult);
        addSetting(placementConfirmation);
        addSetting(placementRetryDelay);
        addSetting(maxFailedAttempts);
        addSetting(pauseWhenNoValidTarget);
        addSetting(respectManualPlacement);

        addSetting(new HeaderSetting("Auto Switch"));
        addSetting(autoSwitchBlock);
        addSetting(switchTrigger);
        addSetting(searchEntireHotbar);
        addSetting(blockSelection);
        addSetting(preferredBlock);
        addSetting(minimumStackAmount);
        addSetting(skipInvalidItems);
        addSetting(switchDelay);
        addSetting(switchBack);
        addSetting(continueBridging);
        addSetting(stopIfNoBlocks);
        addSetting(respectManualSlotSwitching);

        addSetting(new HeaderSetting("Safety"));
        addSetting(safeDisable);
        addSetting(restoreInputState);
        addSetting(stopOnInvalidPlayer);
        addSetting(pauseOnWorldChange);

        // Visibility gates: only show what actually applies to the selected mode.
        sneakDuration.visibleIf(() -> !sneakMode.is("Continuous Sneak"));
        releaseDuration.visibleIf(() -> !sneakMode.is("Continuous Sneak"));
        minSneakDuration.visibleIf(() -> sneakMode.is("Sneak-Tap"));
        maxSneakDuration.visibleIf(() -> sneakMode.is("Sneak-Tap"));
        minReleaseDuration.visibleIf(() -> sneakMode.is("Sneak-Tap"));
        maxReleaseDuration.visibleIf(() -> sneakMode.is("Sneak-Tap"));
        timingAdjustment.visibleIf(() -> sneakMode.is("Sneak-Tap"));
        adaptiveTiming.visibleIf(() -> sneakMode.is("Sneak-Tap"));
        cycleDelay.visibleIf(() -> sneakMode.is("Sneak-Tap"));

        movementMode.visibleIf(() -> bridgingMode.is("Assisted Bridging"));
        autoForward.visibleIf(() -> bridgingMode.is("Assisted Bridging"));

        placementMode.visibleIf(() -> autoPlaceBlock.isEnabled());
        placementTarget.visibleIf(() -> autoPlaceBlock.isEnabled());
        syncWithPlacement.visibleIf(() -> autoPlaceBlock.isEnabled());
        placementDelay.visibleIf(() -> autoPlaceBlock.isEnabled());
        waitForPlacementResult.visibleIf(() -> autoPlaceBlock.isEnabled());
        placementConfirmation.visibleIf(() -> autoPlaceBlock.isEnabled());
        placementRetryDelay.visibleIf(() -> autoPlaceBlock.isEnabled());
        maxFailedAttempts.visibleIf(() -> autoPlaceBlock.isEnabled());

        preferredBlock.visibleIf(() -> autoSwitchBlock.isEnabled() && blockSelection.is("Preferred Block"));
        searchEntireHotbar.visibleIf(() -> autoSwitchBlock.isEnabled());
        blockSelection.visibleIf(() -> autoSwitchBlock.isEnabled());
        minimumStackAmount.visibleIf(() -> autoSwitchBlock.isEnabled());
        switchDelay.visibleIf(() -> autoSwitchBlock.isEnabled());
    }

    // ==================================================================
    //  Lifecycle
    // ==================================================================

    @Override
    public void onEnable() {
        long now = System.currentTimeMillis();
        enabledAt = now;
        lastPlaceAt = 0L;
        lastSwitchAt = 0L;
        lastStopAt = 0L;
        manualSlotUntil = 0L;

        cycleStarted = false;
        sneakPhase = false;
        phaseStart = now;
        phaseEnd = now;

        awaitingResult = false;
        pendingTarget = null;
        pendingSince = 0L;
        failedAttempts = 0;
        failedUntil = 0L;
        adaptiveOffset = 0.0;

        weAllReleased();
        unsafe = false;
        status = "";
        debugLine = "";

        enabledWorld = mc.world;
        lockedYaw = mc.player == null ? 0.0f : mc.player.getYaw();
        bridgeYaw = lockedYaw;

        originalSlot = -1;
        lastOurSlot = -1;
        try {
            if (mc.player != null) originalSlot = mc.player.getInventory().getSelectedSlot();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDisable() {
        releaseInputs();
        try {
            if (switchBack.isEnabled() && restoreInputState.isEnabled()
                    && originalSlot >= 0 && mc.player != null) {
                PlayerInventory inv = mc.player.getInventory();
                if (inv.getSelectedSlot() != originalSlot) inv.setSelectedSlot(originalSlot);
            }
        } catch (Throwable ignored) {
        }
        originalSlot = -1;
        lastOurSlot = -1;
        manualSlotUntil = 0L;
        failedTarget = null;
        awaitingResult = false;
        pendingTarget = null;
        cycleStarted = false;
        status = "";
        debugLine = "";
    }

    // ==================================================================
    //  Tick
    // ==================================================================

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();

        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            if (stopOnDisconnect.isEnabled() || stopOnInvalidPlayer.isEnabled()) {
                stop("No world");
            }
            return;
        }
        if (pauseOnWorldChange.isEnabled() && enabledWorld != null && mc.world != enabledWorld) {
            stop("World changed");
            return;
        }
        if (stopOnDeath.isEnabled() && (mc.player.isDead() || mc.player.getHealth() <= 0.0f)) {
            stop("Player died");
            return;
        }
        if (mc.currentScreen != null && pauseOnGui.isEnabled()) {
            releaseInputs();
            status = "Paused: GUI open";
            return;
        }
        if (now - enabledAt < ms(startDelay)) {
            status = "Waiting: start delay";
            return;
        }
        if (requirePlaceInput.isEnabled() && !mc.options.useKey.isPressed()) {
            releaseMovement();
            status = "Waiting: use key";
            return;
        }

        updateDirection();

        // Drifting off the one-block-wide bridge is the main way sneak-tap bridging fails, so keep
        // the player centred on the bridge axis with the perpendicular movement keys.
        if (autoCenter.isEnabled() && !autoMovementWanted()) centerOnBridge();

        tickSwitch(now);

        boolean hasBlocks = hasPlaceableBlock();
        if (onlyWithBlocks.isEnabled() && !hasBlocks) {
            releaseMovement();
            status = "No blocks in the hotbar";
            return;
        }

        tickSneak(now, hasBlocks);
        tickPlacement(now, hasBlocks);
        tickMovement(now, hasBlocks);

        status = buildStatus();
        if (debugMode.isEnabled()) debugLine = status;
    }

    // ==================================================================
    //  Direction
    // ==================================================================

    /**
     * Splits the heading into a world step. Both components can be non-zero, which is what makes
     * diagonal bridging work: the two orthogonal blocks of the diagonal are both placed.
     */
    private void updateDirection() {
        float yaw = directionMode.is("Lock on Activation") ? lockedYaw : mc.player.getYaw();
        float viewYaw = MathHelper.wrapDegrees(yaw);

        // The bridge must follow the way the player is actually walking, otherwise pressing S
        // still builds forwards and the blocks end up on the wrong side.
        double fx;
        double fz;
        double[] input = movementInput();
        if (buildDirection.is("Follow Input") && input != null) {
            bridgeYaw = (float) Math.toDegrees(Math.atan2(-input[0], input[1]));
            fx = input[0];
            fz = input[1];
        } else {
            bridgeYaw = viewYaw;
            double rad = Math.toRadians(bridgeYaw);
            fx = -Math.sin(rad);
            fz = Math.cos(rad);
        }

        // sin(22.5 deg): below this the axis counts as "no movement".
        final double dead = 0.3826834;
        stepX = Math.abs(fx) < dead ? 0 : (fx > 0 ? 1 : -1);
        stepZ = Math.abs(fz) < dead ? 0 : (fz > 0 ? 1 : -1);
        if (stepX == 0 && stepZ == 0) {
            stepX = Math.abs(fx) >= Math.abs(fz) ? (fx > 0 ? 1 : -1) : 0;
            stepZ = stepX == 0 ? (fz > 0 ? 1 : -1) : 0;
        }
    }

    /**
     * Normalised world-space direction of the player's own WASD input, or {@code null} when no
     * movement key is held. In Full Auto the module drives the keys itself, so the camera heading
     * is used there instead.
     */
    private double[] movementInput() {
        if (bridgingMode.is("Full Auto Bridging")) return null;

        double rad = Math.toRadians(mc.player.getYaw());
        double fwdX = -Math.sin(rad);
        double fwdZ = Math.cos(rad);
        double rightX = -fwdZ;
        double rightZ = fwdX;

        double x = 0;
        double z = 0;
        if (mc.options.forwardKey.isPressed()) { x += fwdX; z += fwdZ; }
        if (mc.options.backKey.isPressed())    { x -= fwdX; z -= fwdZ; }
        if (mc.options.rightKey.isPressed())   { x += rightX; z += rightZ; }
        if (mc.options.leftKey.isPressed())    { x -= rightX; z -= rightZ; }

        double len = Math.sqrt(x * x + z * z);
        if (len < 1.0E-4) return null;
        return new double[]{x / len, z / len};
    }

    private BlockPos ground() {
        return mc.player.getBlockPos().down();
    }

    /** Blocks that must exist for the next step, in placement order. */
    private List<BlockPos> stepTargets() {
        List<BlockPos> out = new ArrayList<>(2);
        BlockPos ground = ground();
        if (placementTarget.is("Underfoot")) {
            out.add(ground);
            return out;
        }
        if (stepX != 0) out.add(ground.add(stepX, 0, 0));
        if (stepZ != 0) out.add(ground.add(0, 0, stepZ));
        if (out.isEmpty()) out.add(ground);
        return out;
    }

    // ==================================================================
    //  Sneak
    // ==================================================================

    private void tickSneak(long now, boolean hasBlocks) {
        boolean want;

        if (sneakMode.is("Manual")) {
            want = false;
        } else if (sneakMode.is("Continuous Sneak")) {
            want = true;
        } else if (sneakMode.is("Auto Edge Sneak")) {
            want = edgeAwareness.isEnabled() && atEdge();
        } else {
            if (onlyWhileMoving.isEnabled() && !playerMoving()) {
                want = false;
            } else {
                want = sneakTapPhase(now);
            }
        }

        // The player holding shift by hand always wins.
        if (respectManualSneak.isEnabled() && mc.options.sneakKey.isPressed() && !owned[K_SNEAK]) {
            return;
        }
        applyKey(K_SNEAK, want);
    }

    private boolean sneakTapPhase(long now) {
        if (!cycleStarted) {
            if (now - enabledAt < ms(initialSneakDelay)) return false;
            cycleStarted = true;
            sneakPhase = true;
            phaseStart = now;
            phaseEnd = now + (long) sneakDurationFor();
            return true;
        }

        // Place Sync: keep holding shift while the block we are about to stand on is still missing.
        if (sneakPhase && timingMode.is("Place Sync") && !stepIsReady()
                && now - phaseStart < ms(maxSneakDuration)) {
            phaseEnd = now + 15L;
            return true;
        }

        if (now >= phaseEnd) {
            if (sneakPhase) {
                sneakPhase = false;
                phaseStart = now;
                phaseEnd = now + (long) releaseDurationFor() + ms(cycleDelay);
            } else {
                sneakPhase = true;
                phaseStart = now;
                phaseEnd = now + (long) sneakDurationFor();
            }
        }
        return sneakPhase;
    }

    private static long ms(NumberSetting setting) {
        Double value = setting.getValue();
        return value == null ? 0L : value.longValue();
    }

    private static int iv(NumberSetting setting) {
        Double value = setting.getValue();
        return value == null ? 0 : value.intValue();
    }

    private double sneakDurationFor() {
        double base = sneakDuration.getValue();
        if (adaptiveTiming.isEnabled() || timingMode.is("Adaptive")) base += adaptiveOffset;
        base += timingAdjustment.getValue();
        return Math.max(minSneakDuration.getValue(), Math.min(maxSneakDuration.getValue(), base));
    }

    private double releaseDurationFor() {
        double base = releaseDuration.getValue();
        if (adaptiveTiming.isEnabled() || timingMode.is("Adaptive")) base -= adaptiveOffset * 0.5;
        base += timingAdjustment.getValue();
        return Math.max(minReleaseDuration.getValue(), Math.min(maxReleaseDuration.getValue(), base));
    }

    /** True when every block of the next step is already solid. */
    private boolean stepIsReady() {
        for (BlockPos pos : stepTargets()) {
            if (isReplaceableAt(pos)) return false;
        }
        return true;
    }

    /** The block in front of the player's feet is air: one more step and they fall. */
    private boolean atEdge() {
        try {
            BlockPos feet = mc.player.getBlockPos();
            if (!mc.world.getBlockState(feet).isAir()) return false;
            BlockPos front = feet.add(stepX, 0, stepZ);
            return mc.world.getBlockState(front.down()).isAir();
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================================================================
    //  Movement
    // ==================================================================

    private void tickMovement(long now, boolean hasBlocks) {
        if (!autoMovementWanted()) {
            releaseMovement();
            return;
        }
        if (now - enabledAt < ms(movementStartDelay)) {
            releaseMovement();
            return;
        }
        if (now - lastStopAt < ms(movementStopDelay)) {
            releaseMovement();
            return;
        }
        if (manualInputPriority.isEnabled() && playerMovingByHand()) {
            releaseMovement();
            return;
        }
        if (groundCheck.isEnabled() && !mc.player.isOnGround()) {
            releaseMovement();
            status = "Waiting: not on ground";
            return;
        }

        boolean safe = isPathSafe(hasBlocks);
        unsafe = !safe;
        if (!safe) {
            releaseMovement();
            if (pauseWhenUnsafe.isEnabled()) {
                lastStopAt = now;
                status = "Stopped: path unsafe";
            }
            return;
        }

        double rad = Math.toRadians(bridgeYaw);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        steer(dx, dz);

        if (autoSprint.isEnabled()) applyKey(K_SPRINT, true);
        else applyKey(K_SPRINT, false);
    }

    private boolean autoMovementWanted() {
        if (bridgingMode.is("Manual Sneak-Tap")) return false;
        if (bridgingMode.is("Full Auto Bridging")) return true;
        return movementMode.is("Auto Forward") || autoForward.isEnabled();
    }

    /** Forward/strafe decomposition so the camera never has to move. */
    private void steer(double dx, double dz) {
        double rad = Math.toRadians(mc.player.getYaw());
        double fwdX = -Math.sin(rad);
        double fwdZ = Math.cos(rad);
        double rightX = -fwdZ;
        double rightZ = fwdX;

        double f = dx * fwdX + dz * fwdZ;
        double s = dx * rightX + dz * rightZ;

        applyKey(K_FORWARD, f > 0.25);
        applyKey(K_BACK, f < -0.25);
        applyKey(K_RIGHT, s > 0.25);
        applyKey(K_LEFT, s < -0.25);
    }

    /**
     * The next step must either be solid already or be placeable right now. With
     * {@code Sync With Placement} the block has to exist before we step onto it.
     */
    private boolean isPathSafe(boolean hasBlocks) {
        if (!movementCheck.isEnabled()) return true;

        List<BlockPos> targets = stepTargets();
        boolean allSolid = true;
        for (BlockPos pos : targets) {
            if (isReplaceableAt(pos)) {
                allSolid = false;
                break;
            }
        }
        if (allSolid) return true;

        if (syncWithPlacement.isEnabled()) return false;
        if (!autoPlaceBlock.isEnabled() || placementMode.is("Manual")) return false;
        if (!hasBlocks) return false;
        return findSupport(targets) != null;
    }

    private boolean playerMoving() {
        return mc.player.getVelocity().horizontalLengthSquared() > 1.0E-4
                || mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }

    private boolean playerMovingByHand() {
        return (!owned[K_FORWARD] && mc.options.forwardKey.isPressed())
                || (!owned[K_BACK] && mc.options.backKey.isPressed())
                || (!owned[K_LEFT] && mc.options.leftKey.isPressed())
                || (!owned[K_RIGHT] && mc.options.rightKey.isPressed());
    }

    // ==================================================================
    //  Placement
    // ==================================================================

    private void tickPlacement(long now, boolean hasBlocks) {
        if (!autoPlaceBlock.isEnabled() || placementMode.is("Manual")) return;
        if (!hasBlocks) return;
        // The player is placing by hand right now - do not double up on the same spot.
        if (respectManualPlacement.isEnabled() && mc.options.useKey.isPressed()) return;

        // Resolve the spot first: a new spot always gets a fresh attempt budget, otherwise one
        // bad position would block every position that comes after it.
        BlockPos target = null;
        for (BlockPos pos : stepTargets()) {
            if (isReplaceableAt(pos)) {
                target = pos;
                break;
            }
        }
        if (target == null) {
            failedAttempts = 0;
            failedTarget = null;
            return;
        }
        if (!target.equals(failedTarget)) {
            failedAttempts = 0;
            failedTarget = target;
        }

        if (awaitingResult) {
            if (!waitForPlacementResult.isEnabled() && !placementConfirmation.isEnabled()) {
                awaitingResult = false;
            } else if (now - pendingSince < CONFIRM_WINDOW_MS) {
                return;
            } else {
                boolean confirmed = pendingTarget != null && !isReplaceableAt(pendingTarget);
                awaitingResult = false;
                if (confirmed) {
                    failedAttempts = 0;
                    adaptiveOffset = Math.max(-100.0, adaptiveOffset - 8.0);
                } else {
                    failedAttempts++;
                    adaptiveOffset = Math.min(150.0, adaptiveOffset + 12.0);
                    failedUntil = now + ms(placementRetryDelay);
                    pendingTarget = null;
                }
            }
        }

        if (failedAttempts >= iv(maxFailedAttempts)) {
            status = "Gave up on this spot";
            if (pauseWhenNoValidTarget.isEnabled()) lastStopAt = now;
            return;
        }
        if (now < failedUntil) return;
        if (now - lastPlaceAt < placementDelayFor()) return;

        int slot = ensureBlockSlot();
        if (slot < 0) {
            status = "No placeable block";
            return;
        }

        Support support = findSupportFor(target);
        if (support == null) {
            status = "No valid target";
            if (pauseWhenNoValidTarget.isEnabled() && pauseWhenUnsafe.isEnabled()) lastStopAt = now;
            return;
        }

        if (placeAt(target, support)) {
            lastPlaceAt = now;
            pendingTarget = target;
            pendingSince = now;
            awaitingResult = placementConfirmation.isEnabled() || waitForPlacementResult.isEnabled();
        } else {
            failedAttempts++;
            failedUntil = now + ms(placementRetryDelay);
        }
    }

    private double placementDelayFor() {
        double base = placementDelay.getValue();
        if (timingMode.is("Adaptive") || adaptiveTiming.isEnabled()) {
            base += Math.max(0.0, adaptiveOffset);
        }
        return Math.max(minPlacementDelay.getValue(), Math.min(maxPlacementDelay.getValue(), base));
    }

    private static final class Support {
        final BlockPos neighbour;
        final Direction face;
        final Vec3d hit;

        Support(BlockPos neighbour, Direction face, Vec3d hit) {
            this.neighbour = neighbour;
            this.face = face;
            this.hit = hit;
        }
    }

    private Support findSupport(List<BlockPos> targets) {
        for (BlockPos pos : targets) {
            Support s = findSupportFor(pos);
            if (s != null) return s;
        }
        return null;
    }

    /** First reachable solid neighbour of {@code target} whose face we may right click. */
    private Support findSupportFor(BlockPos target) {
        Direction[] order = {
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST,
                Direction.DOWN, Direction.UP
        };
        Vec3d eye = mc.player.getEyePos();
        double reach = 4.5;

        for (Direction side : order) {
            BlockPos neighbour = target.offset(side);
            try {
                BlockState state = mc.world.getBlockState(neighbour);
                if (state.isAir() || state.isReplaceable()) continue;
                if (isInteractive(state)) continue;
                if (!state.isSolidBlock(mc.world, neighbour)) continue;
            } catch (Throwable t) {
                continue;
            }

            Direction face = side.getOpposite();
            Vec3d hit = Vec3d.ofCenter(neighbour).add(
                    face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            if (eye.squaredDistanceTo(hit) > reach * reach) continue;
            return new Support(neighbour, face, hit);
        }
        return null;
    }

    private boolean placeAt(BlockPos target, Support support) {
        try {
            BlockHitResult hit = new BlockHitResult(support.hit, support.face, support.neighbour, false);

            float oldYaw = mc.player.getYaw();
            float oldPitch = mc.player.getPitch();
            boolean rotate = !rotationMode.is("None");
            boolean silent = rotationMode.is("Silent");

            if (rotate) {
                // Looking at the block is what a real mc does; placing without ever turning
                // is exactly what anti-cheats flag.
                Vec3d eye = mc.player.getEyePos();
                double dx = support.hit.x - eye.x;
                double dy = support.hit.y - eye.y;
                double dz = support.hit.z - eye.z;
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
                mc.player.setPitch((float) (-Math.toDegrees(Math.atan2(dy, horizontal))));
            }

            var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);

            if (rotate && silent) {
                mc.player.setYaw(oldYaw);
                mc.player.setPitch(oldPitch);
            }

            if (result.isAccepted()) {
                mc.player.swingHand(Hand.MAIN_HAND);
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================================================================
    //  Auto switch
    // ==================================================================

    private void tickSwitch(long now) {
        if (!autoSwitchBlock.isEnabled()) return;

        PlayerInventory inv = mc.player.getInventory();
        int current = inv.getSelectedSlot();
        ItemStack held = inv.getStack(current);

        boolean need;
        if (switchTrigger.is("Block Unavailable")) {
            need = !isPlaceable(held);
        } else {
            need = held.isEmpty() || held.getCount() <= 0;
            if (!need && skipInvalidItems.isEnabled() && !isPlaceable(held)) need = true;
        }
        if (!need) return;

        // The player picked a different slot by hand: back off instead of fighting them.
        if (respectManualSlotSwitching.isEnabled() && lastOurSlot >= 0 && current != lastOurSlot) {
            manualSlotUntil = now + Math.max(500L, ms(switchDelay) * 4L);
            lastOurSlot = current;
        }
        if (respectManualSlotSwitching.isEnabled() && now < manualSlotUntil) return;
        if (now - lastSwitchAt < ms(switchDelay)) return;

        int slot = findBlockSlot(current);
        if (slot < 0) {
            status = "No placeable block left";
            if (stopIfNoBlocks.isEnabled()) stop("Out of blocks");
            return;
        }
        if (slot == current) return;

        inv.setSelectedSlot(slot);
        lastOurSlot = slot;
        lastSwitchAt = now;
        if (!continueBridging.isEnabled()) {
            lastPlaceAt = now + ms(switchDelay);
        }
    }

    /** Makes sure the held item is a block, switching when needed. Returns the slot or -1. */
    private int ensureBlockSlot() {
        PlayerInventory inv = mc.player.getInventory();
        int current = inv.getSelectedSlot();
        if (isPlaceable(inv.getStack(current))) return current;
        if (!autoSwitchBlock.isEnabled()) return -1;

        int slot = findBlockSlot(current);
        if (slot < 0) return -1;
        if (slot != current) {
            inv.setSelectedSlot(slot);
            lastOurSlot = slot;
            lastSwitchAt = System.currentTimeMillis();
        }
        return slot;
    }

    /**
     * Scans the hotbar for a usable block. Every slot from 1 to 9 is considered; empty slots,
     * tools, food and other non block items are skipped.
     */
    private int findBlockSlot(int current) {
        PlayerInventory inv = mc.player.getInventory();
        int hotbar = Math.max(1, inv.getHotbarSize());
        int min = iv(minimumStackAmount);

        int firstValid = -1;
        int mostSlot = -1;
        int mostCount = -1;
        int preferredSlot = -1;

        String want = preferredBlock.getValue().toLowerCase().replace(" ", "_");

        // "First Valid" continues with the slot after the current one, exactly like a player
        // cycling through the hotbar; the empty slots in between are simply skipped.
        boolean wrap = searchEntireHotbar.isEnabled();
        int start = (current + 1) % hotbar;
        int count = wrap ? hotbar : Math.max(0, hotbar - start);

        for (int i = 0; i < count; i++) {
            int slot = wrap ? (start + i) % hotbar : start + i;
            if (slot < 0 || slot >= hotbar) break;
            ItemStack stack = inv.getStack(slot);
            if (stack == null || stack.isEmpty()) continue;
            if (!isPlaceable(stack)) continue;
            if (stack.getCount() < min) continue;

            if (firstValid < 0) firstValid = slot;
            if (stack.getCount() > mostCount) {
                mostCount = stack.getCount();
                mostSlot = slot;
            }
            if (preferredSlot < 0 && blockPath(stack).contains(want)) preferredSlot = slot;
        }

        if (blockSelection.is("Preferred Block") && preferredSlot >= 0) return preferredSlot;
        if (blockSelection.is("Most Blocks") && mostSlot >= 0) return mostSlot;
        return firstValid;
    }

    private boolean hasPlaceableBlock() {
        PlayerInventory inv = mc.player.getInventory();
        int hotbar = Math.max(1, inv.getHotbarSize());
        for (int i = 0; i < hotbar; i++) {
            if (isPlaceable(inv.getStack(i))) return true;
        }
        return false;
    }

    private String blockPath(ItemStack stack) {
        try {
            if (!(stack.getItem() instanceof BlockItem bi)) return "";
            return Registries.BLOCK.getId(bi.getBlock()).getPath();
        } catch (Throwable t) {
            return "";
        }
    }

    private boolean isPlaceable(ItemStack stack) {
        return isPlaceable(stack, skipInvalidItems.isEnabled());
    }

    /**
     * @param strict when true the block has to be something that can actually bridge
     *               (no barriers, light blocks, command blocks, fire...)
     */
    private boolean isPlaceable(ItemStack stack, boolean strict) {
        try {
            if (stack == null || stack.isEmpty()) return false;
            if (!(stack.getItem() instanceof BlockItem bi)) return false;
            if (!strict) return true;

            Block block = bi.getBlock();
            String path = Registries.BLOCK.getId(block).getPath();
            if (path.equals("air") || path.contains("barrier") || path.contains("command_block")
                    || path.contains("structure_block") || path.contains("jigsaw")
                    || path.equals("light") || path.equals("fire") || path.contains("portal")
                    || path.equals("water") || path.equals("lava") || path.contains("cobweb")) {
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================================================================
    //  World helpers
    // ==================================================================

    private boolean isReplaceableAt(BlockPos pos) {
        try {
            BlockState state = mc.world.getBlockState(pos);
            return state.isAir() || state.isReplaceable();
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isInteractive(BlockState state) {
        try {
            if (state.hasBlockEntity()) return true;
            Block b = state.getBlock();
            return b instanceof net.minecraft.block.DoorBlock
                    || b instanceof net.minecraft.block.TrapdoorBlock
                    || b instanceof net.minecraft.block.FenceGateBlock
                    || b instanceof net.minecraft.block.ButtonBlock
                    || b instanceof net.minecraft.block.LeverBlock
                    || b instanceof net.minecraft.block.CraftingTableBlock
                    || b instanceof net.minecraft.block.AnvilBlock
                    || b instanceof net.minecraft.block.NoteBlock
                    || b instanceof net.minecraft.block.AbstractRedstoneGateBlock;
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================================================================
    //  Input plumbing
    // ==================================================================

    /**
     * Presses or releases a key without stealing one the player holds by hand. A key is only
     * released when this module was the one that pressed it.
     */
    private void applyKey(int which, boolean want) {
        try {
            KeyBinding key = keyFor(which);
            if (key == null) return;
            if (want) {
                if (!key.isPressed()) {
                    key.setPressed(true);
                    owned[which] = true;
                }
            } else if (owned[which]) {
                key.setPressed(false);
                owned[which] = false;
            }
        } catch (Throwable ignored) {
        }
    }

    private KeyBinding keyFor(int which) {
        return switch (which) {
            case K_FORWARD -> mc.options.forwardKey;
            case K_BACK -> mc.options.backKey;
            case K_LEFT -> mc.options.leftKey;
            case K_RIGHT -> mc.options.rightKey;
            case K_SNEAK -> mc.options.sneakKey;
            default -> mc.options.sprintKey;
        };
    }

    private void weAllReleased() {
        for (int i = 0; i < owned.length; i++) owned[i] = false;
    }

    /**
     * Nudges the player towards the centre line of the bridge using the movement keys that are
     * perpendicular to the bridge direction. Forward is left alone, so walking speed is unchanged.
     *
     * @return {@code true} while a correction is still being applied
     */
    private boolean centerOnBridge() {
        try {
            if (!mc.player.isOnGround()) {
                releaseCentering();
                return false;
            }

            double px = mc.player.getX();
            double pz = mc.player.getZ();
            double cx = Math.floor(px) + 0.5;
            double cz = Math.floor(pz) + 0.5;

            // Axis perpendicular to the bridge: for a north/south bridge that is east/west.
            double perpX = -stepZ;
            double perpZ = stepX;
            if (perpX == 0 && perpZ == 0) {
                releaseCentering();
                return false;
            }

            double offset = (px - cx) * perpX + (pz - cz) * perpZ;
            if (Math.abs(offset) < 0.03) {
                releaseCentering();
                return false;
            }

            // World direction we want to walk: back towards the centre line.
            double wantX = -Math.signum(offset) * perpX;
            double wantZ = -Math.signum(offset) * perpZ;

            double rad = Math.toRadians(mc.player.getYaw());
            double fwdX = -Math.sin(rad);
            double fwdZ = Math.cos(rad);
            double rightX = -fwdZ;
            double rightZ = fwdX;

            double strafe = wantX * rightX + wantZ * rightZ;

            centerKey(K_RIGHT, strafe > 0.05);
            centerKey(K_LEFT, strafe < -0.05);
            return true;
        } catch (Throwable t) {
            releaseCentering();
            return false;
        }
    }

    private void centerKey(int which, boolean want) {
        try {
            var key = switch (which) {
                case K_FORWARD -> mc.options.forwardKey;
                case K_BACK -> mc.options.backKey;
                case K_LEFT -> mc.options.leftKey;
                default -> mc.options.rightKey;
            };
            if (want) {
                if (!key.isPressed()) {
                    key.setPressed(true);
                    centerOwned[which] = true;
                }
            } else if (centerOwned[which]) {
                key.setPressed(false);
                centerOwned[which] = false;
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseCentering() {
        for (int i = 0; i < centerOwned.length; i++) centerKey(i, false);
    }

    private void releaseMovement() {
        releaseCentering();
        applyKey(K_FORWARD, false);
        applyKey(K_BACK, false);
        applyKey(K_LEFT, false);
        applyKey(K_RIGHT, false);
        applyKey(K_SPRINT, false);
    }

    private void releaseInputs() {
        releaseCentering();
        releaseMovement();
        if (safeDisable.isEnabled() || restoreInputState.isEnabled()) {
            applyKey(K_SNEAK, false);
        }
    }

    private void stop(String reason) {
        status = reason;
        setEnabled(false);
    }

    // ==================================================================
    //  HUD
    // ==================================================================

    private String buildStatus() {
        StringBuilder sb = new StringBuilder("§6Bridge §7| ");
        String ns = stepZ < 0 ? "N" : stepZ > 0 ? "S" : "";
        String ew = stepX < 0 ? "W" : stepX > 0 ? "E" : "";
        sb.append(ns.isEmpty() && ew.isEmpty() ? "-" : ns + ew);
        if (sneakPhase) sb.append(" §aSNEAK");
        else sb.append(" §7release");
        if (unsafe) sb.append(" §cunsafe");
        if (debugMode.isEnabled()) {
            sb.append(" §8[").append(bridgingMode.getValue()).append(']');
            sb.append(" §8slot=").append(mc.player.getInventory().getSelectedSlot());
            sb.append(" §8fail=").append(failedAttempts);
        }
        return sb.toString();
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.textRenderer == null || mc.getWindow() == null) return;
        if (!debugMode.isEnabled()) return;
        String text = debugLine.isEmpty() ? status : debugLine;
        if (text.isEmpty()) return;
        try {
            context.drawText(mc.textRenderer, text, 6, mc.getWindow().getScaledHeight() - 40, 0xFFFFFFFF, true);
        } catch (Throwable ignored) {
        }
    }
}
