package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

import com.anormal.client.setting.BooleanSetting;
import net.minecraft.util.math.BlockPos;

public class SafeWalk extends Module {
    public final BooleanSetting onGroundOnly = new BooleanSetting("On Ground Only", "Only prevent edge falls while on ground", true);
    public final BooleanSetting blocksOnly = new BooleanSetting("Blocks Only", "Only sneak on empty edges when holding blocks", false);

    public SafeWalk() {
        super("SafeWalk", "Prevents walking off block edges without slowing down", Category.UZNY11);
        addSetting(onGroundOnly);
        addSetting(blocksOnly);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (onGroundOnly.isEnabled() && !mc.player.isOnGround()) return;
        if (blocksOnly.isEnabled() && !(mc.player.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem)) return;

        BlockPos under = mc.player.getBlockPos().down();
        if (mc.world.isAir(under) && mc.player.isOnGround()) {
            mc.options.sneakKey.setPressed(true);
        } else if (!mc.options.sneakKey.isPressed()) {
            // Let default unpressed
        }
    }
}
