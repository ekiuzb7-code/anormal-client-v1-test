package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.player.AutoTool;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Clears a whole area instead of one tunnel: a box, a cylinder or a corridor in front of you.
 *
 * <p>The area is collected once into a queue and then mined block by block, always staying on the
 * current block until it is gone. That is what makes it work in survival - switching targets every
 * tick would reset the break progress and nothing would ever break.</p>
 */
public class Excavator extends Module {

    public final ModeSetting shape = new ModeSetting("Shape", "Area to clear", "Box",
            "Box", "Cylinder", "Tunnel");
    public final NumberSetting radius = new NumberSetting("Radius",
            "Horizontal radius in blocks", 4.0, 1.0, 16.0, 1.0);
    public final NumberSetting height = new NumberSetting("Height",
            "Vertical extent above your feet", 3.0, 1.0, 16.0, 1.0);
    public final NumberSetting depth = new NumberSetting("Depth",
            "Forward depth (Tunnel shape only)", 8.0, 1.0, 32.0, 1.0);

    public final BooleanSetting onlyOres = new BooleanSetting("Only Ores",
            "Only break ore blocks", false);
    public final BooleanSetting skipFluids = new BooleanSetting("Skip Fluids",
            "Never break water or lava", true);
    public final BooleanSetting skipUnbreakable = new BooleanSetting("Skip Unbreakable",
            "Never break bedrock and similar", true);

    public final NumberSetting blocksPerTick = new NumberSetting("Blocks Per Tick",
            "Blocks broken each tick in creative", 4.0, 1.0, 20.0, 1.0);
    public final NumberSetting reach = new NumberSetting("Reach",
            "Only break blocks within this distance", 4.5, 1.0, 6.0, 0.1);
    public final NumberSetting rescanDelay = new NumberSetting("Rescan Delay",
            "Ticks between area rescans", 40.0, 5.0, 200.0, 5.0);

    public final BooleanSetting autoTool = new BooleanSetting("Auto Tool",
            "Switch to the fastest tool for each block", true);
    public final BooleanSetting swing = new BooleanSetting("Swing",
            "Swing the hand while breaking", true);
    public final BooleanSetting showHud = new BooleanSetting("Show HUD",
            "Show progress on screen", true);

    private final List<BlockPos> queue = new ArrayList<>();
    private BlockPos currentTarget;
    private int rescanTimer;
    private int broken;

    public Excavator() {
        super("Excavator", "Clears a large area around or in front of you", Category.WORLD);
        addSetting(shape);
        addSetting(radius);
        addSetting(height);
        addSetting(depth);
        addSetting(onlyOres);
        addSetting(skipFluids);
        addSetting(skipUnbreakable);
        addSetting(blocksPerTick);
        addSetting(reach);
        addSetting(rescanDelay);
        addSetting(autoTool);
        addSetting(swing);
        addSetting(showHud);
    }

    @Override
    public void onEnable() {
        queue.clear();
        currentTarget = null;
        rescanTimer = 0;
        broken = 0;
    }

    @Override
    public void onDisable() {
        queue.clear();
        currentTarget = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (--rescanTimer <= 0 || queue.isEmpty()) {
            rebuildQueue();
            rescanTimer = rescanDelay.getValue().intValue();
        }
        if (queue.isEmpty()) return;

        if (mc.player.isCreative()) {
            int budget = Math.max(1, blocksPerTick.getValue().intValue());
            for (int i = 0; i < budget; i++) {
                BlockPos pos = nextTarget();
                if (pos == null) break;
                mine(pos, true);
                broken++;
            }
            return;
        }

        BlockPos target = nextTarget();
        if (target == null) return;
        currentTarget = target;
        mine(target, false);
    }

    private void mine(BlockPos pos, boolean instant) {
        try {
            BlockState state = mc.world.getBlockState(pos);
            if (autoTool.isEnabled()) selectTool(state);

            Vec3d delta = mc.player.getEyePos().subtract(Vec3d.ofCenter(pos));
            Direction side = Direction.getFacing(delta.x, delta.y, delta.z);

            if (instant) {
                mc.interactionManager.attackBlock(pos, side);
            } else {
                mc.interactionManager.updateBlockBreakingProgress(pos, side);
            }
            if (swing.isEnabled()) mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
    }

    /** First queued block that is still there and within reach; air entries are dropped. */
    private BlockPos nextTarget() {
        try {
            if (currentTarget != null && isCandidate(currentTarget) && inReach(currentTarget)) {
                return currentTarget;
            }
            currentTarget = null;

            for (int i = 0; i < queue.size(); i++) {
                BlockPos pos = queue.get(i);
                if (!isCandidate(pos)) {
                    queue.remove(i);
                    i--;
                    continue;
                }
                if (!inReach(pos)) continue;
                return pos;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean isCandidate(BlockPos pos) {
        try {
            BlockState state = mc.world.getBlockState(pos);
            if (state.isAir()) return false;
            if (skipFluids.isEnabled() && !state.getFluidState().isEmpty()) return false;
            if (skipUnbreakable.isEnabled() && state.getHardness(mc.world, pos) < 0.0f) return false;
            if (onlyOres.isEnabled() && !isOre(state)) return false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean inReach(BlockPos pos) {
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= reach.getValue() * reach.getValue();
    }

    private void rebuildQueue() {
        queue.clear();
        if (mc.player == null || mc.world == null) return;

        BlockPos origin = mc.player.getBlockPos();
        int r = radius.getValue().intValue();
        int h = height.getValue().intValue();
        int d = depth.getValue().intValue();

        boolean tunnel = shape.is("Tunnel");
        boolean cylinder = shape.is("Cylinder");

        // For the tunnel shape the area starts one block in front of the player.
        Direction facing = Direction.fromHorizontalDegrees(MathHelper.wrapDegrees(mc.player.getYaw()));

        for (int dy = 0; dy < h; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (cylinder && dx * dx + dz * dz > r * r) continue;

                    BlockPos pos;
                    if (tunnel) {
                        // Rotate the local offset so the corridor follows the view direction.
                        int forward = dz + r + 1;
                        if (forward > d) continue;
                        int ox = facing.getOffsetX() * forward + facing.getOffsetZ() * dx;
                        int oz = facing.getOffsetZ() * forward - facing.getOffsetX() * dx;
                        pos = origin.add(ox, dy, oz);
                    } else {
                        pos = origin.add(dx, dy, dz);
                    }

                    if (pos.getY() < mc.world.getBottomY() || pos.getY() > mc.world.getTopYInclusive()) continue;
                    queue.add(pos.toImmutable());
                }
            }
        }
    }

    private boolean isOre(BlockState state) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return path.endsWith("_ore") || path.equals("ancient_debris")
                || path.equals("raw_iron_block") || path.equals("raw_copper_block")
                || path.equals("raw_gold_block");
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

    @Override
    public void onRender2D(net.minecraft.client.gui.DrawContext context, float tickDelta) {
        if (!showHud.isEnabled() || !isEnabled() || mc.textRenderer == null) return;
        try {
            String line = "§6Excavator §7| §f" + broken + " §7broken §8| §7" + queue.size() + " §7queued";
            int w = mc.textRenderer.getWidth(line);
            int x = mc.getWindow().getScaledWidth() / 2 - w / 2;
            int y = 42;
            com.anormal.client.util.RenderUtils.fill(context, x - 5, y - 3, x + w + 5, y + 11,
                    com.anormal.client.util.ColorUtils.rgba(14, 16, 22, 200));
            com.anormal.client.util.RenderUtils.drawText(context, mc.textRenderer, line, x, y, 0xFFFFFFFF, true);
        } catch (Throwable ignored) {
        }
    }
}
