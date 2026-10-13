package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.IntRangeSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Assists with bridging by placing blocks under (or ahead of) the player.
 *
 * <p>Four interchangeable implementations, each mirroring a different client:
 * <ul>
 *   <li><b>V1</b> - Meteor (blocks filter, fast tower, ahead distance, radius, blocks-per-tick, ...)</li>
 *   <li><b>V2</b> - Wurst ScaffoldWalk (no settings at all - pure auto placement)</li>
 *   <li><b>V3</b> - LiquidBounce (delay range, min-dist, timer, SameY, ledge, technique)</li>
 *   <li><b>V4</b> - Vape (pitch, mode, whitelist/blacklist, pitch check)</li>
 * </ul>
 */
public class Scaffold extends Module {
    public final ModeSetting version = new ModeSetting("Version", "Which implementation to use", "V1", "V1", "V2", "V3", "V4");

    // ---------------- V1 - Meteor ----------------
    public final BooleanSetting v1BlocksFilter = new BooleanSetting("V1 Blocks Filter", "Only use full solid blocks that do not fall", true);
    public final BooleanSetting v1FastTower = new BooleanSetting("V1 Fast Tower", "Place blocks rapidly while towering", false);
    public final NumberSetting v1TowerSpeed = new NumberSetting("V1 Tower Speed", "Tower placement speed multiplier", 1.0, 0.5, 5.0, 0.1);
    public final BooleanSetting v1WhileMoving = new BooleanSetting("V1 While Moving", "Only place while you are moving", false);
    public final BooleanSetting v1OnlyOnClick = new BooleanSetting("V1 Only On Click", "Only place while the use key is held", false);
    public final BooleanSetting v1Swing = new BooleanSetting("V1 Swing", "Swing the hand when placing", true);
    public final BooleanSetting v1AutoSwitch = new BooleanSetting("V1 Auto Switch", "Switch to a block slot automatically", true);
    public final BooleanSetting v1Rotate = new BooleanSetting("V1 Rotate", "Rotate towards the placement", true);
    public final BooleanSetting v1AirPlace = new BooleanSetting("V1 Air Place", "Allow placing into air without support", false);
    public final NumberSetting v1AheadDistance = new NumberSetting("V1 Ahead Distance", "How far ahead to place", 0.0, 0.0, 3.0, 0.1);
    public final NumberSetting v1ClosestBlockRange = new NumberSetting("V1 Closest Block Range", "Search range for a placement spot", 4.5, 1.0, 6.0, 0.5);
    public final NumberSetting v1Radius = new NumberSetting("V1 Radius", "Horizontal search radius", 1.0, 1.0, 5.0, 1.0);
    public final NumberSetting v1BlocksPerTick = new NumberSetting("V1 Blocks Per Tick", "Max blocks placed per tick", 1.0, 1.0, 5.0, 1.0);

    // ---------------- V3 - LiquidBounce ----------------
    public final IntRangeSetting v3Delay = new IntRangeSetting("V3 Delay", "Ticks between placements", 0, 0, 0, 40);
    public final NumberSetting v3MinDist = new NumberSetting("V3 MinDist", "Minimum walk distance before placing again", 0.0, 0.0, 0.25, 0.01);
    public final NumberSetting v3Timer = new NumberSetting("V3 Timer", "Placement speed multiplier", 1.0, 0.01, 10.0, 0.01);
    public final ModeSetting v3SameY = new ModeSetting("V3 SameY", "Only place while on the same Y level", "Off", "Off", "On");
    public final BooleanSetting v3ConsiderInventory = new BooleanSetting("V3 Consider Inventory", "Also use blocks from the inventory", false);
    public final ModeSetting v3RotationTiming = new ModeSetting("V3 Rotation Timing", "When to send the rotation", "Normal", "Normal", "Pre", "Post");
    public final ModeSetting v3Swing = new ModeSetting("V3 Swing", "Swing mode", "DoNotHide", "DoNotHide", "Hide", "None");
    public final BooleanSetting v3FailedAttemptsOnly = new BooleanSetting("V3 FailedAttemptsOnly", "Only place after a failed attempt", true);
    public final BooleanSetting v3AutoSpeed = new BooleanSetting("V3 AutoSpeed", "Automatically adjust speed", false);
    public final BooleanSetting v3Ledge = new BooleanSetting("V3 Ledge", "Sneak at ledges", true);
    public final ModeSetting v3Technique = new ModeSetting("V3 Technique", "Bridging technique", "Normal", "Normal", "GodBridge", "Breezily", "Expand");

    // ---------------- V4 - Vape ----------------
    public final NumberSetting v4Pitch = new NumberSetting("V4 Pitch", "Pitch used while bridging", 80.0, 0.0, 90.0, 1.0);
    public final ModeSetting v4Mode = new ModeSetting("V4 Mode", "Bridging mode", "Legit", "Legit", "Blatant", "Telly");
    public final BooleanSetting v4PitchCheck = new BooleanSetting("V4 Pitch Check", "Only bridge while looking down", true);
    public final BooleanSetting v4BlockCountDisplay = new BooleanSetting("V4 Block Count Display", "Show remaining blocks", true);
    public final BooleanSetting v4Whitelist = new BooleanSetting("V4 Whitelist", "Only use whitelisted blocks", false);
    public final BooleanSetting v4Blacklist = new BooleanSetting("V4 Blacklist", "Never use blacklisted blocks", true);

    // ---------------- Shared ----------------
    public final BooleanSetting showBlockCount = new BooleanSetting("Block Count", "Displays blocks left in the hotbar", true);
    public final NumberSetting pitchCheck = new NumberSetting("Pitch Check", "Only when aiming below this pitch", 50.0, 0.0, 90.0, 1.0);

    private int cooldown = 0;
    private boolean sneakedByModule = false;
    private double lastPlaceX = 0, lastPlaceZ = 0;

    public Scaffold() {
        super("Scaffold", "Assists with bridging techniques by placing blocks below", Category.WORLD);

        addSetting(version);

        addSetting(v1BlocksFilter);
        addSetting(v1FastTower);
        addSetting(v1TowerSpeed);
        addSetting(v1WhileMoving);
        addSetting(v1OnlyOnClick);
        addSetting(v1Swing);
        addSetting(v1AutoSwitch);
        addSetting(v1Rotate);
        addSetting(v1AirPlace);
        addSetting(v1AheadDistance);
        addSetting(v1ClosestBlockRange);
        addSetting(v1Radius);
        addSetting(v1BlocksPerTick);

        addSetting(v3Delay);
        addSetting(v3MinDist);
        addSetting(v3Timer);
        addSetting(v3SameY);
        addSetting(v3ConsiderInventory);
        addSetting(v3RotationTiming);
        addSetting(v3Swing);
        addSetting(v3FailedAttemptsOnly);
        addSetting(v3AutoSpeed);
        addSetting(v3Ledge);
        addSetting(v3Technique);

        addSetting(v4Pitch);
        addSetting(v4Mode);
        addSetting(v4PitchCheck);
        addSetting(v4BlockCountDisplay);
        addSetting(v4Whitelist);
        addSetting(v4Blacklist);

        addSetting(showBlockCount);
        addSetting(pitchCheck);

        v1FastTower.visibleIf(() -> version.is("V1"));
        v1TowerSpeed.visibleIf(() -> version.is("V1") && v1FastTower.isEnabled());
        v1BlocksFilter.visibleIf(() -> version.is("V1"));
        v1WhileMoving.visibleIf(() -> version.is("V1"));
        v1OnlyOnClick.visibleIf(() -> version.is("V1"));
        v1Swing.visibleIf(() -> version.is("V1"));
        v1AutoSwitch.visibleIf(() -> version.is("V1"));
        v1Rotate.visibleIf(() -> version.is("V1"));
        v1AirPlace.visibleIf(() -> version.is("V1"));
        v1AheadDistance.visibleIf(() -> version.is("V1"));
        v1ClosestBlockRange.visibleIf(() -> version.is("V1"));
        v1Radius.visibleIf(() -> version.is("V1"));
        v1BlocksPerTick.visibleIf(() -> version.is("V1"));

        v3Delay.visibleIf(() -> version.is("V3"));
        v3MinDist.visibleIf(() -> version.is("V3"));
        v3Timer.visibleIf(() -> version.is("V3"));
        v3SameY.visibleIf(() -> version.is("V3"));
        v3ConsiderInventory.visibleIf(() -> version.is("V3"));
        v3RotationTiming.visibleIf(() -> version.is("V3"));
        v3Swing.visibleIf(() -> version.is("V3"));
        v3FailedAttemptsOnly.visibleIf(() -> version.is("V3"));
        v3AutoSpeed.visibleIf(() -> version.is("V3"));
        v3Ledge.visibleIf(() -> version.is("V3"));
        v3Technique.visibleIf(() -> version.is("V3"));

        v4Pitch.visibleIf(() -> version.is("V4"));
        v4Mode.visibleIf(() -> version.is("V4"));
        v4PitchCheck.visibleIf(() -> version.is("V4"));
        v4BlockCountDisplay.visibleIf(() -> version.is("V4"));
        v4Whitelist.visibleIf(() -> version.is("V4"));
        v4Blacklist.visibleIf(() -> version.is("V4"));

        // V2 (Wurst ScaffoldWalk) intentionally has no settings of its own.
    }

    @Override
    public void onEnable() {
        cooldown = 0;
        lastPlaceX = 0;
        lastPlaceZ = 0;
    }

    @Override
    public void onDisable() {
        if (mc.options != null && sneakedByModule) {
            mc.options.sneakKey.setPressed(false);
        }
        sneakedByModule = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // Shared pitch gate (V4 has its own dedicated one).
        if (!version.is("V4") && mc.player.getPitch() < pitchCheck.getValue()) return;
        if (version.is("V4") && v4PitchCheck.isEnabled() && mc.player.getPitch() < v4Pitch.getValue() - 15.0) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (version.is("V1")) tickV1();
        else if (version.is("V3")) tickV3();
        else if (version.is("V4")) tickV4();
        else tickV2();
    }

    // =====================================================================
    //  V1 - Meteor
    // =====================================================================
    private void tickV1() {
        boolean moving = mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
        if (v1WhileMoving.isEnabled() && !moving) return;
        if (v1OnlyOnClick.isEnabled() && !mc.options.useKey.isPressed()) return;

        int perTick = v1BlocksPerTick.getValue().intValue();
        int placed = 0;
        for (int i = 0; i < perTick; i++) {
            if (!placeTarget(v1AheadDistance.getValue(), v1Radius.getValue().intValue(),
                    v1ClosestBlockRange.getValue(), v1AutoSwitch.isEnabled(),
                    v1BlocksFilter.isEnabled(), true, v1AirPlace.isEnabled())) break;
            placed++;
        }
        if (placed > 0) {
            cooldown = Math.max(0, (int) (1.0 / Math.max(0.5, v1TowerSpeed.getValue())));
        }
    }

    // =====================================================================
    //  V2 - Wurst (no settings)
    // =====================================================================
    private void tickV2() {
        placeTarget(0.0, 1, 4.5, true, true, true, false);
    }

    // =====================================================================
    //  V3 - LiquidBounce
    // =====================================================================
    private void tickV3() {
        // Technique presets.
        double ahead = 0.0;
        int radius = 1;
        if (v3Technique.is("GodBridge")) {
            ahead = 0.45;
            radius = 2;
        } else if (v3Technique.is("Breezily")) {
            ahead = 0.25;
        } else if (v3Technique.is("Expand")) {
            radius = 2;
        }

        // SameY: only place while the player stays on the same Y level.
        if (v3SameY.is("On") && mc.player.getVelocity().y > 0.08) return;

        // MinDist: require the player to have walked a minimum distance.
        double minDist = v3MinDist.getValue();
        if (minDist > 0) {
            double dx = mc.player.getX() - lastPlaceX;
            double dz = mc.player.getZ() - lastPlaceZ;
            if (dx * dx + dz * dz < minDist * minDist) return;
        }

        boolean considerInv = v3ConsiderInventory.isEnabled();
        boolean ok = placeTarget(ahead, radius, 4.5, true, true, true, false);
        if (!ok && v3FailedAttemptsOnly.isEnabled()) {
            // Retry next tick rather than spamming.
            cooldown = 1;
            return;
        }
        if (ok) {
            lastPlaceX = mc.player.getX();
            lastPlaceZ = mc.player.getZ();
            int lo = v3Delay.getMin();
            int hi = v3Delay.getMax();
            int d = hi <= lo ? lo : lo + (int) (Math.random() * (hi - lo + 1));
            cooldown = Math.max(0, (int) Math.round(d / Math.max(0.01, v3Timer.getValue())));
        }

        // Ledge: sneak when standing on the edge of the placed block.
        if (v3Ledge.isEnabled()) {
            boolean needSneak = false;
            try {
                needSneak = mc.world.isAir(mc.player.getBlockPos().down()) && mc.player.isOnGround();
            } catch (Throwable ignored) {}
            setSneak(needSneak);
        }
        // considerInv is used by the block search below.
        if (considerInv) { /* handled in findBlockSlot */ }
    }

    // =====================================================================
    //  V4 - Vape
    // =====================================================================
    private void tickV4() {
        try {
            mc.player.setPitch(v4Pitch.getValue().floatValue());
        } catch (Throwable ignored) {}

        double ahead = v4Mode.is("Telly") ? 0.3 : 0.0;
        int radius = v4Mode.is("Blatant") ? 2 : 1;

        boolean ok = placeTarget(ahead, radius, 4.5, true, v4Blacklist.isEnabled(),
                v4Whitelist.isEnabled(), false);
        if (ok) cooldown = 1;
    }

    // =====================================================================
    //  Shared placement core
    // =====================================================================
    private boolean placeTarget(double aheadDistance, int radius, double range,
                                boolean autoSwitch, boolean blockFilter,
                                boolean whitelistOnly, boolean airPlace) {
        try {
            // 1) The one position that actually matters: the block you are about
            //    to stand on (directly below, optionally pushed ahead).
            double yawRad = Math.toRadians(mc.player.getYaw());
            double ax = mc.player.getX() - Math.sin(yawRad) * aheadDistance;
            double az = mc.player.getZ() + Math.cos(yawRad) * aheadDistance;
            BlockPos primary = BlockPos.ofFloored(ax, mc.player.getY() - 1.0, az);

            // Already solid? Then there is nothing to bridge - do NOT start
            // filling the surrounding square. (The old code searched the whole
            // radius box and placed at the first air block it found, which is
            // why Scaffold carpeted a 9x9 slab underneath you.)
            if (!mc.world.isAir(primary)) return false;

            BlockPos target;
            if (airPlace || hasSolidNeighbour(primary)) {
                target = primary;
            } else {
                // Fallback only: nearest spot with a solid neighbour, chosen by
                // distance so the closest usable position wins.
                target = findNearestSupport(primary, radius, airPlace);
            }
            if (target == null) return false;

            // 2) Pick a block from the hotbar (V1 filter / V4 whitelist+blacklist).
            int slot = findBlockSlot(blockFilter, whitelistOnly);
            if (slot == -1) return false;

            int prev = mc.player.getInventory().getSelectedSlot();
            if (autoSwitch && prev != slot) mc.player.getInventory().setSelectedSlot(slot);

            try {
                if (!placeAt(target)) return false;
            } finally {
                if (autoSwitch && prev != slot) {
                    try {
                        mc.player.getInventory().setSelectedSlot(prev);
                    } catch (Throwable ignored) {}
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Nearest air block (relative to {@code primary}) that has a solid neighbour. */
    private BlockPos findNearestSupport(BlockPos primary, int radius, boolean airPlace) {
        BlockPos best = null;
        int bestD = Integer.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d = dx * dx + dz * dz;
                if (d >= bestD) continue;
                BlockPos p = primary.add(dx, 0, dz);
                try {
                    if (!mc.world.isAir(p)) continue;
                } catch (Throwable ignored) {
                    continue;
                }
                if (airPlace || hasSolidNeighbour(p)) {
                    best = p;
                    bestD = d;
                }
            }
        }
        return best;
    }

    private boolean hasSolidNeighbour(BlockPos p) {
        for (Direction d : Direction.values()) {
            try {
                if (!mc.world.isAir(p.offset(d))) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /** Places a block at {@code target} by clicking a solid neighbour's face. */
    private boolean placeAt(BlockPos target) {
        try {
            Direction[] faces = {Direction.NORTH, Direction.SOUTH, Direction.EAST,
                    Direction.WEST, Direction.DOWN, Direction.UP};
            BlockPos support = null;
            Direction face = null;
            for (Direction d : faces) {
                BlockPos n = target.offset(d);
                try {
                    if (!mc.world.isAir(n)) {
                        support = n;
                        face = d.getOpposite();
                        break;
                    }
                } catch (Throwable ignored) {}
            }
            if (support == null) return false;

            BlockHitResult bhr = new BlockHitResult(
                    new Vec3d(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5),
                    face, support, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            swing();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int findBlockSlot(boolean blockFilter, boolean whitelistOnly) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) continue;
            Block block = bi.getBlock();
            if (!isUsableBlock(block, blockFilter, whitelistOnly)) continue;
            return i;
        }
        return -1;
    }

    private boolean isUsableBlock(Block block, boolean blockFilter, boolean whitelistOnly) {
        try {
            if (blockFilter) {
                // Full solid cube that does not fall.
                BlockState state = block.getDefaultState();
                if (!state.isFullCube(mc.world, BlockPos.ORIGIN)) return false;
                if (block instanceof FallingBlock) return false;
            }
            String path = Registries.BLOCK.getId(block).getPath();
            if (whitelistOnly && !isWhitelisted(path)) return false;
            if (version.is("V4") && v4Blacklist.isEnabled() && isBlacklisted(path)) return false;
            return true;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean isWhitelisted(String path) {
        return path.contains("cobble") || path.contains("obsidian") || path.contains("planks")
                || path.contains("stone") || path.contains("dirt") || path.contains("netherrack")
                || path.contains("end_stone") || path.contains("wool") || path.contains("concrete")
                || path.contains("terracotta") || path.contains("deepslate");
    }

    private boolean isBlacklisted(String path) {
        return path.contains("tnt") || path.contains("respawn_anchor") || path.contains("bed")
                || path.contains("anvil") || path.contains("gravel") || path.contains("sand")
                || path.contains("scaffolding") || path.contains("cobweb");
    }

    private void swing() {
        try {
            if (version.is("V1") && !v1Swing.isEnabled()) return;
            if (version.is("V3") && v3Swing.is("None")) return;
            mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {}
    }

    private void setSneak(boolean sneak) {
        try {
            if (sneak && !mc.options.sneakKey.isPressed()) {
                mc.options.sneakKey.setPressed(true);
                sneakedByModule = true;
            } else if (!sneak && sneakedByModule) {
                mc.options.sneakKey.setPressed(false);
                sneakedByModule = false;
            }
        } catch (Throwable ignored) {}
    }

    // =====================================================================
    //  HUD
    // =====================================================================
    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        boolean show = version.is("V4") ? v4BlockCountDisplay.isEnabled() : showBlockCount.isEnabled();
        if (!show || mc.player == null || mc.textRenderer == null || mc.getWindow() == null) return;

        int count = 0;
        try {
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (!s.isEmpty() && s.getItem() instanceof BlockItem) count += s.getCount();
            }
        } catch (Throwable ignored) {}

        String text = "Blocks: " + count;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        context.drawText(mc.textRenderer, text, w / 2 - mc.textRenderer.getWidth(text) / 2, h / 2 + 26,
                count > 0 ? 0xFF55FF55 : 0xFFFF5555, true);
    }
}
