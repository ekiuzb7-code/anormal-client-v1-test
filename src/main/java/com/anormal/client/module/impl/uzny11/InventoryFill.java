package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.screen.slot.SlotActionType;

public class InventoryFill extends Module {
    public final NumberSetting cps = new NumberSetting("CPS", "Shift-clicks per second while filling", 8.0, 1.0, 20.0, 1.0);

    private int timer = 0;

    public InventoryFill() {
        super("InventoryFill", "Automatically rapid clicks while holding shift in container screens", Category.UZNY11);
        addSetting(cps);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!mc.options.sneakKey.isPressed()) {
            timer = 0;
            return;
        }
        if (mc.player.currentScreenHandler == mc.player.playerScreenHandler) return;
        int size = mc.player.currentScreenHandler.slots.size();
        if (size <= 36) return;
        if (timer > 0) {
            timer--;
            return;
        }
        for (int i = size - 36; i < size; i++) {
            if (mc.player.currentScreenHandler.getSlot(i).hasStack()) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                double clicks = cps.getValue();
                timer = clicks >= 20 ? 0 : Math.max(0, (int) (20.0 / clicks) - 1);
                return;
            }
        }
    }
}
