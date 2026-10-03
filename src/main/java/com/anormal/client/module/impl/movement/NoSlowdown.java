package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

public class NoSlowdown extends Module {
    public final BooleanSetting items = new BooleanSetting("Items", "No slow while using items", true);
    public final BooleanSetting soulSand = new BooleanSetting("Soul Sand", "Hop over soul sand", true);
    public final BooleanSetting slime = new BooleanSetting("Slime Blocks", "Hop over slime blocks", true);

    public NoSlowdown() {
        super("NoSlowdown", "Keeps speed while slowed", Category.MOVEMENT);
        addSetting(items);
        addSetting(soulSand);
        addSetting(slime);
    }

    private boolean onBlock(String part) {
        try {
            BlockPos under = mc.player.getBlockPos().down();
            String path = Registries.BLOCK.getId(mc.world.getBlockState(under).getBlock()).getPath();
            return path.contains(part);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            boolean using = mc.player.isUsingItem() || mc.player.isBlocking();
            if (items.isEnabled() && using && !mc.player.isSprinting() && mc.player.forwardSpeed > 0) {
                mc.player.setSprinting(true);
            }
            if (soulSand.isEnabled() && onBlock("soul_sand") && mc.player.isOnGround() && mc.player.forwardSpeed > 0) {
                mc.player.jump();
            }
            if (slime.isEnabled() && onBlock("slime") && mc.player.isOnGround() && mc.player.forwardSpeed > 0) {
                mc.player.jump();
            }
        } catch (Throwable ignored) {}
    }
}
