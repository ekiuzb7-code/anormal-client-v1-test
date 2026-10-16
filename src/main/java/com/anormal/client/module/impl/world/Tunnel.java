package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Digs a walkable tunnel in the direction you are looking.
 *
 * <p>The module keeps a cursor one slice ahead of the player, clears the whole
 * cross-section (Width x Height) and only advances once that slice is empty, so
 * the tunnel is always walkable.</p>
 *
 * <p><b>Direction:</b> on {@code Auto} the tunnel follows your view - it snaps
 * to the nearest cardinal direction of your yaw and re-anchors whenever you
 * turn, so you are never locked in place. A manual direction pins the tunnel;
 * {@code Align View} then keeps your camera on it.</p>
 *
 * <p><b>Animation:</b> every block currently in the target slice is outlined /
 * filled with a pulsing colour so you can see exactly what is being mined.</p>
 */
public class Tunnel extends Module {

    public final ModeSetting mode = new ModeSetting("Mode", "Tunnel shape", "Tunnel",
            "Tunnel", "Stair Down", "Stair Up");
    public final NumberSetting width = new NumberSetting("Width", "Tunnel width in blocks (1 = classic 1-wide tunnel)", 1.0, 1.0, 5.0, 1.0);
    public final NumberSetting height = new NumberSetting("Height", "Tunnel height in blocks (2 = you fit)", 2.0, 1.0, 5.0, 1.0);
    public final ModeSetting direction = new ModeSetting("Direction", "Auto follows your view, the rest pin it", "Auto",
            "Auto", "North", "South", "East", "West");
    public final BooleanSetting alignView = new BooleanSetting("Align View", "Turn your camera to face the tunnel (ignored on Auto)", false);
    public final NumberSetting stairInterval = new NumberSetting("Stair Interval", "Slices per step when using a stair mode", 2.0, 1.0, 10.0, 1.0);

    public final BooleanSetting autoWalk = new BooleanSetting("Auto Walk", "Hold forward so you keep up with the tunnel", true);
    public final BooleanSetting jumpOnStairs = new BooleanSetting("Jump On Stairs", "Jump when the tunnel steps up", true);
    public final NumberSetting blocksPerTick = new NumberSetting("Blocks Per Tick", "Maximum blocks broken each tick", 2.0, 1.0, 20.0, 1.0);
    public final NumberSetting reach = new NumberSetting("Reach", "Only break blocks within this distance", 4.5, 1.0, 6.0, 0.5);
    public final BooleanSetting swing = new BooleanSetting("Swing", "Swing your arm while mining", true);

    public final BooleanSetting autoTorch = new BooleanSetting("Auto Torch", "Place a torch every N blocks", false);
    public final NumberSetting torchInterval = new NumberSetting("Torch Interval", "Blocks between torches", 8.0, 2.0, 40.0, 1.0);
    public final BooleanSetting autoScaffold = new BooleanSetting("Auto Scaffold", "Bridge gaps so you do not fall", false);

    public final BooleanSetting pauseOnObstacle = new BooleanSetting("Pause On Obstacle", "Stop at bedrock, barriers and portals", true);
    public final BooleanSetting ignoreFluids = new BooleanSetting("Ignore Fluids", "Leave water and lava alone", false);
    public final BooleanSetting stopAtLava = new BooleanSetting("Stop At Lava", "Stop digging when lava is in the way", true);
    public final BooleanSetting showHud = new BooleanSetting("Show HUD", "Show tunnel progress on screen", true);

    // ---- Mining animation ----
    public final BooleanSetting animation = new BooleanSetting("Mining Animation", "Highlight the blocks being mined", true);
    public final ModeSetting animationStyle = new ModeSetting("Animation Style", "How the target blocks are drawn", "Outline",
            "Outline", "Fill", "Both", "Pulse");
    public final ColorSetting animationColor = new ColorSetting("Animation Color", "Colour of the mining highlight", ColorUtils.rgba(255, 140, 0, 255));
    public final NumberSetting animationSpeed = new NumberSetting("Animation Speed", "How fast the highlight pulses", 1.0, 0.2, 4.0, 0.1);
    public final NumberSetting animationAlpha = new NumberSetting("Animation Opacity", "Fill opacity of the highlight", 90.0, 10.0, 255.0, 5.0);

    private BlockPos cursor = null;
    private int dirX = 0, dirZ = 1;
    private int steps = 0;
    private int lastTorchStep = 0;
    private boolean blocked = false;
    private boolean walking = false;

    /** Blocks that are part of the slice currently being dug (for the animation). */
    private final List<BlockPos> miningTargets = new ArrayList<>();

    public Tunnel() {
        super("Tunnel", "Digs a walkable tunnel (and stairs) in the direction you look", Category.WORLD);
        addSetting(mode);
        addSetting(width);
        addSetting(height);
        addSetting(direction);
        addSetting(alignView);
        addSetting(stairInterval);
        addSetting(autoWalk);
        addSetting(jumpOnStairs);
        addSetting(blocksPerTick);
        addSetting(reach);
        addSetting(swing);
        addSetting(autoTorch);
        addSetting(torchInterval);
        addSetting(autoScaffold);
        addSetting(pauseOnObstacle);
        addSetting(ignoreFluids);
        addSetting(stopAtLava);
        addSetting(showHud);
        addSetting(animation);
        addSetting(animationStyle);
        addSetting(animationColor);
        addSetting(animationSpeed);
        addSetting(animationAlpha);
    }

    @Override
    public void onEnable() {
        cursor = null;
        steps = 0;
        lastTorchStep = 0;
        blocked = false;
        miningTargets.clear();
    }

    @Override
    public void onDisable() {
        releaseWalk();
        cursor = null;
        blocked = false;
        miningTargets.clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (blocked) {
            releaseWalk();
            miningTargets.clear();
            return;
        }

        int prevX = dirX, prevZ = dirZ;
        resolveDirection();

        // Turning re-anchors the tunnel so it really follows your view.
        if (cursor == null || prevX != dirX || prevZ != dirZ) {
            cursor = mc.player.getBlockPos().offset(horizontalDir(dirX, dirZ));
        }

        // If the player has drifted far away (e.g. they walked past the cursor),
        // re-anchor instead of digging a hole behind them.
        BlockPos feet = mc.player.getBlockPos();
        int drift = Math.abs(feet.getX() - cursor.getX()) + Math.abs(feet.getZ() - cursor.getZ());
        if (drift > 6) {
            cursor = feet.offset(horizontalDir(dirX, dirZ));
        }

        if (mineSlice()) {
            advance();
        }

        if (alignView.isEnabled() && !direction.is("Auto")) {
            mc.player.setYaw(yawFor(dirX, dirZ));
        }

        if (autoWalk.isEnabled()) holdWalk();
        else releaseWalk();

        if (jumpOnStairs.isEnabled() && !mode.is("Tunnel") && mc.player.isOnGround()
                && cursor != null && cursor.getY() > feet.getY()) {
            mc.player.jump();
        }

        if (autoTorch.isEnabled()) tryPlaceTorch();
    }

    // ------------------------------------------------------------------
    //  Direction
    // ------------------------------------------------------------------

    private void resolveDirection() {
        switch (direction.getValue()) {
            case "North" -> { dirX = 0; dirZ = -1; }
            case "South" -> { dirX = 0; dirZ = 1; }
            case "East" -> { dirX = 1; dirZ = 0; }
            case "West" -> { dirX = -1; dirZ = 0; }
            default -> {
                // Auto: snap the current yaw to the nearest cardinal. No yaw is
                // written back, so the player can always turn and the tunnel
                // follows them instead of locking to one direction.
                float yaw = mc.player.getYaw();
                if (yaw >= -45 && yaw < 45) { dirX = 0; dirZ = 1; }
                else if (yaw >= 45 && yaw < 135) { dirX = -1; dirZ = 0; }
                else if (yaw >= -135 && yaw < -45) { dirX = 1; dirZ = 0; }
                else { dirX = 0; dirZ = -1; }
            }
        }
    }

    private static float yawFor(int dx, int dz) {
        if (dz > 0) return 0f;
        if (dz < 0) return 180f;
        if (dx > 0) return -90f;
        return 90f;
    }

    private static Direction horizontalDir(int dx, int dz) {
        if (dz > 0) return Direction.SOUTH;
        if (dz < 0) return Direction.NORTH;
        if (dx > 0) return Direction.EAST;
        return Direction.WEST;
    }

    // ------------------------------------------------------------------
    //  Mining
    // ------------------------------------------------------------------

    /** @return true when the whole slice is clear and the cursor may advance. */
    private boolean mineSlice() {
        int w = width.getValue().intValue();
        int h = height.getValue().intValue();
        int half = (w - 1) / 2;
        int perpX = dirZ;
        int perpZ = -dirX;

        miningTargets.clear();

        int broken = 0;
        int max = blocksPerTick.getValue().intValue();
        boolean allClear = true;

        for (int o = -half; o <= w - 1 - half; o++) {
            for (int y = 0; y < h; y++) {
                BlockPos p = cursor.add(perpX * o, y, perpZ * o);
                BlockState state = mc.world.getBlockState(p);
                if (state.isAir()) continue;

                if (!isMineable(p, state)) {
                    allClear = false;
                    continue;
                }

                if (miningTargets.size() < 64) miningTargets.add(p.toImmutable());

                if (broken >= max) {
                    allClear = false;
                    continue;
                }

                if (mine(p)) broken++;
                else allClear = false;
            }
        }

        if (autoScaffold.isEnabled()) scaffold();

        return allClear && broken == 0;
    }

    private boolean isMineable(BlockPos pos, BlockState state) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();

        if (pauseOnObstacle.isEnabled() && (path.equals("bedrock") || path.contains("barrier")
                || path.contains("portal") || path.contains("command_block")
                || path.contains("structure_block") || path.contains("jigsaw"))) {
            blocked = true;
            return false;
        }

        if (stopAtLava.isEnabled() && path.equals("lava")) {
            blocked = true;
            return false;
        }

        if (ignoreFluids.isEnabled()) {
            try {
                if (!state.getFluidState().isEmpty()) return false;
            } catch (Throwable ignored) {
            }
        }

        double dist = Math.sqrt(pos.getSquaredDistance(mc.player.getBlockPos()));
        return dist <= reach.getValue();
    }

    private boolean mine(BlockPos pos) {
        try {
            mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
            if (swing.isEnabled()) mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void advance() {
        steps++;
        cursor = cursor.offset(horizontalDir(dirX, dirZ));
        if (!mode.is("Tunnel") && steps % stairInterval.getValue().intValue() == 0) {
            cursor = mode.is("Stair Down") ? cursor.down() : cursor.up();
        }
    }

    private void scaffold() {
        int w = width.getValue().intValue();
        int half = (w - 1) / 2;
        int perpX = dirZ, perpZ = -dirX;
        for (int o = -half; o <= w - 1 - half; o++) {
            BlockPos floor = cursor.add(perpX * o, -1, perpZ * o);
            if (!mc.world.getBlockState(floor).isAir()) continue;
            if (!placeBlock(floor, Direction.UP)) return;
        }
    }

    // ------------------------------------------------------------------
    //  Movement / helpers
    // ------------------------------------------------------------------

    private void holdWalk() {
        try {
            mc.options.forwardKey.setPressed(true);
            // Re-asserted every input refresh by KeyBindingMixin, otherwise
            // KeyBinding.updatePressedStates() would wipe the fake press.
            com.anormal.client.util.InputForcer.forward = true;
            walking = true;
        } catch (Throwable ignored) {
        }
    }

    private void releaseWalk() {
        try {
            com.anormal.client.util.InputForcer.forward = false;
        } catch (Throwable ignored) {
        }
        if (!walking) return;
        try {
            mc.options.forwardKey.setPressed(false);
        } catch (Throwable ignored) {
        }
        walking = false;
    }

    private void tryPlaceTorch() {
        if (steps - lastTorchStep < torchInterval.getValue().intValue()) return;
        BlockPos floor = cursor.down();
        if (mc.world.getBlockState(floor).isAir()) return;

        int slot = findHotbarSlot(Items.TORCH);
        if (slot < 0) return;

        int prev = mc.player.getInventory().getSelectedSlot();
        if (prev != slot) mc.player.getInventory().setSelectedSlot(slot);
        boolean ok = placeBlock(floor, Direction.UP);
        if (prev != slot) mc.player.getInventory().setSelectedSlot(prev);
        if (ok) lastTorchStep = steps;
    }

    /** Places the current hotbar item on the given block face. */
    private boolean placeBlock(BlockPos target, Direction face) {
        try {
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(target), face, target, false);
            var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (swing.isEnabled()) mc.player.swingHand(Hand.MAIN_HAND);
            return result.isAccepted();
        } catch (Throwable t) {
            return false;
        }
    }

    /** @return a hotbar slot holding the given item, or -1. */
    private int findHotbarSlot(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.getItem() == item) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;

        if (animation.isEnabled() && !miningTargets.isEmpty()) {
            drawMiningAnimation(context, tickDelta);
        }

        if (!showHud.isEnabled() || mc.textRenderer == null || cursor == null) return;
        try {
            String line = "§6Tunnel §7| §f" + steps + " §7blocks";
            if (blocked) line += " §c(blocked)";
            int w = mc.textRenderer.getWidth(line);
            int x = mc.getWindow().getScaledWidth() / 2 - w / 2;
            int y = 30;
            RenderUtils.fill(context, x - 5, y - 3, x + w + 5, y + 11, ColorUtils.rgba(14, 16, 22, 200));
            RenderUtils.drawText(context, mc.textRenderer, line, x, y, 0xFFFFFFFF, true);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Pulses an outline and/or fill over every block in the current slice, so
     * you can see exactly which blocks the tunnel is chewing through.
     */
    private void drawMiningAnimation(DrawContext context, float tickDelta) {
        try {
            double speed = animationSpeed.getValue();
            float wave = (float) ((Math.sin(System.currentTimeMillis() / 220.0 * speed) + 1.0) / 2.0);
            int base = animationColor.getValue();

            boolean outline = animationStyle.is("Outline") || animationStyle.is("Both");
            boolean fill = animationStyle.is("Fill") || animationStyle.is("Both");
            if (animationStyle.is("Pulse")) {
                outline = true;
                fill = wave > 0.5f;
            }

            int outlineCol = withAlpha(base, (int) (140 + wave * 115));
            int fillCol = withAlpha(base, (int) (animationAlpha.getValue() * (0.4 + wave * 0.6)));

            for (BlockPos p : miningTargets) {
                if (fill) RenderUtils.drawBlockFill(context, p, fillCol, tickDelta, 255);
                if (outline) RenderUtils.drawBlockOutline(context, p, outlineCol, tickDelta, 2);
            }
        } catch (Throwable ignored) {
        }
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
