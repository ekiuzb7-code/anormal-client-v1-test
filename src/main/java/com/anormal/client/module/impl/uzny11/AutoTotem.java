package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class AutoTotem extends Module {
    public final BooleanSetting offhand = new BooleanSetting("Offhand", "Equip to offhand", true);
    public final BooleanSetting inventory = new BooleanSetting("Inventory", "Search inventory", true);
    public final NumberSetting delay = new NumberSetting("Delay", "Equip delay (ticks)", 2, 0, 20, 1);

    private int ticks = 0;

    public AutoTotem() {
        super("AutoTotem", "Automatically equips totems", Category.UZNY11);
        addSetting(offhand);
        addSetting(inventory);
        addSetting(delay);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (++ticks < delay.getValue().intValue()) return;
        ticks = 0;

        if (mc.player.getOffHandStack().isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) return;

        // Find totem in inventory/hotbar
        int slot = -1;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) {
                slot = i;
                break;
            }
        }
        if (slot == -1) return;

        int prev = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot % 9;
        // Auto equip logic
        mc.player.getInventory().selectedSlot = prev;
    }
}