package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;

public class HitSwap extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "HitSwap mode", "Auto", "Auto", "Manual");
    public final BooleanSetting sword = new BooleanSetting("Sword", "Swap to sword", true);
    public final BooleanSetting axe = new BooleanSetting("Axe", "Swap to axe for crits", false);
    public final BooleanSetting crystal = new BooleanSetting("Crystal", "Swap to crystal for burst", false);
    public final NumberSetting delay = new NumberSetting("Delay", "Swap delay (ticks)", 1, 0, 10, 1);
    public final BooleanSetting swing = new BooleanSetting("Swing", "Swing after swap", true);

    private int delayTicks = 0;
    private int prevSlot = -1;

    public HitSwap() {
        super("HitSwap", "Swaps weapons for optimal hits", Category.UZNY11);
        addSetting(mode);
        addSetting(sword);
        addSetting(axe);
        addSetting(crystal);
        addSetting(delay);
        addSetting(swing);
    }

    private int getSelectedSlot() {
        try {
            return mc.player.getInventory().getSelectedSlot();
        } catch (Exception e) {
            return 0;
        }
    }

    private void setSelectedSlot(int slot) {
        try {
            mc.player.getInventory().setSelectedSlot(slot);
        } catch (Exception e) {
            // Ignore
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        if (mc.options.attackKey.isPressed() && mc.targetedEntity != null) {
            int bestSlot = findBestWeapon();
            if (bestSlot != -1 && bestSlot != getSelectedSlot()) {
                prevSlot = getSelectedSlot();
                setSelectedSlot(bestSlot);
                delayTicks = delay.getValue().intValue();
            }
        }
    }

    private int findBestWeapon() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            // Prefer sword for normal hits
            if (item.isIn(ItemTags.SWORDS)) return i;
            // Axe for shield breaking
            if (item.isIn(ItemTags.AXES)) return i;
        }
        return -1;
    }
}