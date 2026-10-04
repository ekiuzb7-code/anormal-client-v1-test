package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class AutoHeal extends Module {
    public final NumberSetting health = new NumberSetting("Health", "Health threshold to heal", 14, 1, 20, 1);
    public final BooleanSetting pots = new BooleanSetting("Potions", "Use healing potions", true);
    public final BooleanSetting gapples = new BooleanSetting("Golden Apples", "Use golden apples", true);
    public final BooleanSetting food = new BooleanSetting("Food", "Eat food", false);
    public final NumberSetting delay = new NumberSetting("Delay", "Delay between healing (ticks)", 4, 0, 20, 1);

    private int ticks = 0;

    public AutoHeal() {
        super("AutoHeal", "Automatically heals you", Category.UZNY11);
        addSetting(health);
        addSetting(pots);
        addSetting(gapples);
        addSetting(food);
        addSetting(delay);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.player.getHealth() > health.getValue()) return;

        if (++ticks < delay.getValue().intValue()) return;
        ticks = 0;

        if (pots.isEnabled()) {
            int slot = findItem(Items.POTION, "healing");
            if (slot != -1) {
                int prev = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = slot;
                mc.interactionManager.useItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.player.getInventory().selectedSlot = prev;
                return;
            }
        }

        if (gapples.isEnabled()) {
            int slot = findItem(Items.ENCHANTED_GOLDEN_APPLE);
            if (slot == -1) slot = findItem(Items.GOLDEN_APPLE);
            if (slot != -1) {
                int prev = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = slot;
                mc.interactionManager.useItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.player.getInventory().selectedSlot = prev;
                return;
            }
        }

        if (food.isEnabled()) {
            int slot = findFood();
            if (slot != -1) {
                int prev = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = slot;
                mc.interactionManager.useItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.player.getInventory().selectedSlot = prev;
            }
        }
    }

    private int findItem(net.minecraft.item.Item item, String... nbt) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) return i;
        }
        return -1;
    }

    private int findFood() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
<<<<<<< HEAD
            if (!stack.isEmpty() && stack.isFood()) return i;
=======
            if (!stack.isEmpty() && stack.contains(DataComponentTypes.FOOD)) return i;
>>>>>>> c4f8d80510c2c22a5c4d94fa3757f915e0baf9ad
        }
        return -1;
    }
}