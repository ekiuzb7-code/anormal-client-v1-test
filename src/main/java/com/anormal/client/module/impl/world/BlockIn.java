package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class BlockIn extends Module {
    public final NumberSetting aimSpeed = new NumberSetting("Aim Speed", "View rotation speed while building", 12.0, 1.0, 30.0, 0.5);
    public final NumberSetting placeDelay = new NumberSetting("Place Delay", "Ticks between placements", 2.0, 0.0, 20.0, 1.0);
    public final BooleanSetting silentAim = new BooleanSetting("Silent Aim", "Build without moving client camera", false);
    public final BooleanSetting sneak = new BooleanSetting("Sneak", "Sneak while placing blocks", true);
    public final BooleanSetting keepSneak = new BooleanSetting("Keep Sneak", "Stay sneaking after building", false);
    public final BooleanSetting bedFinder = new BooleanSetting("Bed Finder", "Leave opening toward nearby bed", true);
    public final ModeSetting blockPriority = new ModeSetting("Block Priority", "Lowest cost saves valuables, Hardest uses tough blocks", "Lowest cost", "Lowest cost", "Hardest");
    public final BooleanSetting returnToLastSlot = new BooleanSetting("Return to Last Slot", "Restore hotbar slot after building", true);
    public final BooleanSetting useBlacklist = new BooleanSetting("Use Blacklist", "Skip utility blocks while building", true);
    private final List<BlockPos> queue = new ArrayList<>();
    private int delay = 0, originalSlot = -1;
    public BlockIn() {
        super("BlockIn", "Builds protective walls around you on demand", Category.WORLD);
        addSetting(aimSpeed); addSetting(placeDelay); addSetting(silentAim);
        addSetting(sneak); addSetting(keepSneak); addSetting(bedFinder);
        addSetting(blockPriority); addSetting(returnToLastSlot); addSetting(useBlacklist);
    }
    @Override
    public void onEnable() {
        queue.clear(); delay = 0; originalSlot = -1;
        if (mc.player == null || mc.world == null) return;
        originalSlot = mc.player.getInventory().getSelectedSlot();
        BlockPos base = mc.player.getBlockPos();
        Direction bedSide = bedFinder.isEnabled() ? findBedSide(base) : null;
        for (int h = 0; h <= 2; h++) {
            for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                if (d == bedSide && h >= 1) continue;
                BlockPos p = base.offset(d).up(h);
                if (mc.world.isAir(p)) queue.add(p);
            }
        }
        if (queue.isEmpty()) setEnabled(false);
    }
    @Override
    public void onDisable() {
        if (sneak.isEnabled() && !keepSneak.isEnabled() && mc.options != null) mc.options.sneakKey.setPressed(false);
        if (returnToLastSlot.isEnabled() && mc.player != null && originalSlot >= 0 && originalSlot < 9) {
            try {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            } catch (Throwable ignored) {}
        }
        queue.clear();
        delay = 0;
    }
    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            setEnabled(false);
            return;
        }
        if (sneak.isEnabled()) mc.options.sneakKey.setPressed(true);
        if (queue.isEmpty()) { setEnabled(false); return; }
        if (delay > 0) { delay--; return; }
        BlockPos target = queue.get(0);
        if (!mc.world.isAir(target)) {
            queue.remove(0);
            return;
        }
        int slot = findBlockSlot();
        if (slot == -1) {
            setEnabled(false);
            return;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        for (Direction d : Direction.values()) {
            BlockPos n = target.offset(d);
            if (mc.world.isAir(n)) continue;
            if (!silentAim.isEnabled()) aimAt(n.getX() + 0.5, n.getY() + 0.5, n.getZ() + 0.5, aimSpeed.getValue());
            BlockHitResult bhr = new BlockHitResult(new Vec3d(n.getX() + 0.5, n.getY() + 0.5, n.getZ() + 0.5), d.getOpposite(), n, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            mc.player.swingHand(Hand.MAIN_HAND);
            queue.remove(0);
            delay = Math.max(0, placeDelay.getValue().intValue());
            return;
        }
        queue.remove(0);
    }

    private Direction findBedSide(BlockPos base) {
        for (int dx = -8; dx <= 8; dx++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -8; dz <= 8; dz++) {
                    try {
                        Identifier id = Registries.BLOCK.getId(mc.world.getBlockState(base.add(dx, dy, dz)).getBlock());
                        if (id != null && id.getPath().contains("_bed")) {
                            if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
                            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
                        }
                    } catch (Throwable ignored) {}
                }
        return null;
    }

    private void aimAt(double x, double y, double z, double speed) {
        Vec3d eye = mc.player.getEyePos();
        float ty = (float) Math.toDegrees(Math.atan2(-(x - eye.x), z - eye.z));
        float cur = mc.player.getYaw(), d = ty - cur;
        while (d > 180.0f) d -= 360.0f;
        while (d < -180.0f) d += 360.0f;
        mc.player.setYaw(cur + (float) Math.max(-speed, Math.min(speed, d)));
    }

    private int findBlockSlot() {
        int fallback = -1;
        for (int i = 0; i < 9; i++) {
            try {
                if (!(mc.player.getInventory().getStack(i).getItem() instanceof net.minecraft.item.BlockItem)) continue;
                Identifier id = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem());
                if (id == null) continue;
                if (useBlacklist.isEnabled() && isBlacklisted(id.getPath())) continue;
                if (blockPriority.is("Hardest") && isHard(id.getPath())) return i;
                if (fallback == -1) fallback = i;
            } catch (Throwable ignored) {}
        }
        return fallback;
    }

    private boolean isBlacklisted(String p) {
        return p.contains("tnt") || p.contains("glass") || p.contains("chest") || p.contains("crafting") || p.contains("enchant") || p.contains("furnace") || p.contains("hopper") || p.contains("beacon");
    }

    private boolean isHard(String p) {
        return p.contains("obsidian") || p.contains("netherite") || p.contains("anvil");
    }
}
