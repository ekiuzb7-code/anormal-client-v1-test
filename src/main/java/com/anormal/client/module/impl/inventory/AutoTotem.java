package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotem extends Module {
    public AutoTotem() {
        super("AutoTotem", "Automatically replaces depleted Totems of Undying in offhand", Category.INVENTORY);
        setEnabled(true);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;

        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i >= 36 ? i - 36 : i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                mc.interactionManager.clickSlot(0, i >= 36 ? i - 36 : i, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(0, 45, 0, SlotActionType.PICKUP, mc.player);
                return;
            }
        }
    }
}
