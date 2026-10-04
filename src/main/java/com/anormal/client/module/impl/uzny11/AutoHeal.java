package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
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
                int prev = getSelectedSlot();
                setSelectedSlot(slot);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                setSelectedSlot(prev);
                return;
            }
        }

        if (gapples.isEnabled()) {
            int slot = findItem(Items.ENCHANTED_GOLDEN_APPLE);
            if (slot == -1) slot = findItem(Items.GOLDEN_APPLE);
            if (slot != -1) {
                int prev = getSelectedSlot();
                setSelectedSlot(slot);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                setSelectedSlot(prev);
                return;
            }
        }

        if (food.isEnabled()) {
            int slot = findFood();
            if (slot != -1) {
                int prev = getSelectedSlot();
                setSelectedSlot(slot);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                setSelectedSlot(prev);
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
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.FoodItem) return i;
        }
        return -1;
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
}