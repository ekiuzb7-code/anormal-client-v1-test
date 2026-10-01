package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class FastPlace extends Module {
    public final NumberSetting delay = new NumberSetting("Delay", "Place tick delay", 0.0, 0.0, 4.0, 1.0);
    public final BooleanSetting blocksOnly = new BooleanSetting("Blocks Only", "Only fast-place blocks", true);

    public FastPlace() {
        super("FastPlace", "Removes placement delay for fast building and projectiles", Category.PLAYER);
        addSetting(delay);
        addSetting(blocksOnly);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.options.useKey.isPressed()) {
            ItemStack held = mc.player.getMainHandStack();
            if (blocksOnly.isEnabled() && !(held.getItem() instanceof BlockItem)) return;

            if (mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bhr) {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }
}
