package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class ChestStealer extends Module {
    public final NumberSetting delayTicks = new NumberSetting("Delay (Ticks)", "Loot delay per slot", 1.0, 0.0, 5.0, 1.0);
    private int timer = 0;

    public ChestStealer() {
        super("ChestStealer", "Automatically loots items from chests and containers", Category.PLAYER);
        addSetting(delayTicks);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
            if (timer > 0) {
                timer--;
                return;
            }

            int containerSlots = handler.getInventory().size();
            for (int i = 0; i < containerSlots; i++) {
                if (handler.getSlot(i).hasStack()) {
                    mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    timer = delayTicks.getValue().intValue();
                    return;
                }
            }
        }
    }
}
