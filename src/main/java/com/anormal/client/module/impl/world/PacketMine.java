package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.player.AutoTool;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Breaks the block you are looking at in a single tick by sending the start and stop actions
 * back to back.
 *
 * <p>Vanilla waits for the mc to finish the break animation before telling the server, so the
 * server only removes the block after the full mining time. Sending {@code STOP_DESTROY_BLOCK}
 * immediately makes the server complete the break right away, while the mc still plays the
 * normal animation.</p>
 */
public class PacketMine extends Module {

    public final NumberSetting range = new NumberSetting("Range",
            "Maximum distance to the block", 4.5, 1.0, 6.0, 0.1);
    public final NumberSetting delay = new NumberSetting("Delay",
            "Milliseconds between two blocks", 100.0, 0.0, 2000.0, 10.0);
    public final BooleanSetting onlyOres = new BooleanSetting("Only Ores",
            "Only break ore blocks", true);
    public final BooleanSetting requireLooking = new BooleanSetting("Require Looking",
            "Only the block under the crosshair", true);
    public final BooleanSetting lineOfSight = new BooleanSetting("Line Of Sight",
            "Require an unobstructed ray to the block", false);
    public final BooleanSetting ignoreUnbreakable = new BooleanSetting("Ignore Unbreakable",
            "Never try bedrock, barriers or other unbreakable blocks", true);
    public final BooleanSetting skipFluids = new BooleanSetting("Skip Fluids",
            "Never target water or lava", true);
    public final BooleanSetting autoTool = new BooleanSetting("Auto Tool",
            "Switch to the fastest tool before breaking", true);
    public final BooleanSetting swing = new BooleanSetting("Swing",
            "Swing the hand while breaking", true);
    public final BooleanSetting showHud = new BooleanSetting("Show HUD",
            "Show the last broken block on screen", false);

    private long lastBreak;
    private String lastBlock = "";

    public PacketMine() {
        super("PacketMine", "Breaks the targeted block instantly with packets", Category.WORLD);
        addSetting(range);
        addSetting(delay);
        addSetting(onlyOres);
        addSetting(requireLooking);
        addSetting(lineOfSight);
        addSetting(ignoreUnbreakable);
        addSetting(skipFluids);
        addSetting(autoTool);
        addSetting(swing);
        addSetting(showHud);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        long now = System.currentTimeMillis();
        if (now - lastBreak < delay.getValue()) return;

        BlockPos pos = target();
        if (pos == null) return;

        BlockState state;
        try {
            state = mc.world.getBlockState(pos);
        } catch (Throwable t) {
            return;
        }
        if (state.isAir()) return;

        if (skipFluids.isEnabled()) {
            try {
                if (!state.getFluidState().isEmpty()) return;
            } catch (Throwable ignored) {
            }
        }

        if (ignoreUnbreakable.isEnabled()) {
            try {
                if (state.getHardness(mc.world, pos) < 0.0f) return;
            } catch (Throwable ignored) {
            }
        }

        if (onlyOres.isEnabled() && !isOre(state)) return;

        if (lineOfSight.isEnabled() && !hasLineOfSight(pos)) return;

        if (autoTool.isEnabled()) selectTool(state);

        try {
            net.minecraft.util.math.Direction side = sideFor(pos);
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, side));
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, side));
            }
            if (swing.isEnabled()) mc.player.swingHand(Hand.MAIN_HAND);
            lastBreak = now;
            lastBlock = Registries.BLOCK.getId(state.getBlock()).getPath();
        } catch (Throwable ignored) {
        }
    }

    /**
     * The block under the crosshair. When {@code Require Looking} is off and the crosshair is not
     * on a block, the nearest ore within range is picked instead.
     */
    private BlockPos target() {
        HitResult hit = mc.crosshairTarget;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > range.getValue() * range.getValue()) {
                return null;
            }
            return pos;
        }
        if (requireLooking.isEnabled()) return null;
        return nearestOre();
    }

    private BlockPos nearestOre() {
        try {
            BlockPos origin = mc.player.getBlockPos();
            int r = (int) Math.ceil(range.getValue());
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;

            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos pos = origin.add(x, y, z);
                        double dist = mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
                        if (dist > range.getValue() * range.getValue()) continue;
                        if (dist >= bestDist) continue;

                        BlockState state = mc.world.getBlockState(pos);
                        if (state.isAir()) continue;
                        if (onlyOres.isEnabled() && !isOre(state)) continue;
                        best = pos;
                        bestDist = dist;
                    }
                }
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean hasLineOfSight(BlockPos pos) {
        try {
            Vec3d from = mc.player.getEyePos();
            Vec3d to = Vec3d.ofCenter(pos);
            BlockHitResult result = mc.world.raycast(new net.minecraft.world.RaycastContext(
                    from, to, net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player));
            return result.getBlockPos().equals(pos);
        } catch (Throwable t) {
            return true;
        }
    }

    private net.minecraft.util.math.Direction sideFor(BlockPos pos) {
        try {
            Vec3d delta = mc.player.getEyePos().subtract(Vec3d.ofCenter(pos));
            return net.minecraft.util.math.Direction.getFacing(delta.x, delta.y, delta.z);
        } catch (Throwable t) {
            return net.minecraft.util.math.Direction.UP;
        }
    }

    private boolean isOre(BlockState state) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return path.endsWith("_ore") || path.equals("ancient_debris") || path.equals("raw_iron_block")
                || path.equals("raw_copper_block") || path.equals("raw_gold_block")
                || path.contains("amethyst") || path.equals("deepslate");
    }

    private void selectTool(BlockState state) {
        try {
            AutoTool tool = ModuleManager.getModule(AutoTool.class);
            if (tool == null) return;
            int slot = tool.bestSlotFor(state);
            if (slot >= 0) mc.player.getInventory().setSelectedSlot(slot);
        } catch (Throwable ignored) {
        }
    }
}
