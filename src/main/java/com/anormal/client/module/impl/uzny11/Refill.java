package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Refill extends Module {
    public final BooleanSetting vertical = new BooleanSetting("Vertical", "Takes items column by column instead of row by row", false);
    public final BooleanSetting scatter = new BooleanSetting("Scatter", "Takes items in a scattered random order", false);
    public final BooleanSetting hotbarClear = new BooleanSetting("Hotbar Clear", "Clears junk from hotbar before refilling", true);
    public final BooleanSetting nonJunkItems = new BooleanSetting("Non Junk Items", "Keeps food, pearls and arrows when clearing", true);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between refill moves", 2.0, 0.0, 20.0, 1.0);
    public final ModeSetting type = new ModeSetting("Type", "Healing item kind to refill", "Both", "Both", "Pots", "Soup");

    private final Random random = new Random();
    private int cooldown = 0;

    public Refill() {
        super("Refill", "Refills hotbar with healing potions and soups from inventory", Category.UZNY11);
        addSetting(vertical);
        addSetting(scatter);
        addSetting(hotbarClear);
        addSetting(nonJunkItems);
        addSetting(delay);
        addSetting(type);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (hotbarClear.isEnabled() && clearOne()) {
            cooldown = delay.getValue().intValue();
            return;
        }
        if (refillOne()) cooldown = delay.getValue().intValue();
    }

    private boolean clearOne() {
        for (int hot = 0; hot < 9; hot++) {
            ItemStack s = mc.player.getInventory().getStack(hot);
            if (s.isEmpty() || isHeal(s) || isKept(s)) continue;
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, hot + 36, 1, SlotActionType.THROW, mc.player);
            return true;
        }
        return false;
    }

    private boolean refillOne() {
        int hot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                hot = i;
                break;
            }
        }
        if (hot == -1) return false;
        for (int idx : sourceOrder()) {
            ItemStack s = mc.player.getInventory().getStack(idx);
            if (!s.isEmpty() && isHeal(s)) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, idx, 0, SlotActionType.QUICK_MOVE, mc.player);
                return true;
            }
        }
        return false;
    }

    private List<Integer> sourceOrder() {
        List<Integer> order = new ArrayList<>();
        if (vertical.isEnabled()) {
            for (int c = 0; c < 9; c++) {
                for (int r = 0; r < 3; r++) order.add(9 + r * 9 + c);
            }
        } else {
            for (int i = 9; i < 36; i++) order.add(i);
        }
        if (scatter.isEnabled()) Collections.shuffle(order, random);
        return order;
    }

    private boolean isHeal(ItemStack s) {
        String p = Registries.ITEM.getId(s.getItem()).getPath();
        boolean soup = p.equals("mushroom_stew");
        boolean pot = (p.equals("splash_potion") || p.equals("lingering_potion")) && hasHealing(s);
        if (type.is("Pots")) return pot;
        if (type.is("Soup")) return soup;
        return pot || soup;
    }

    private boolean isKept(ItemStack s) {
        String p = Registries.ITEM.getId(s.getItem()).getPath();
        if (p.endsWith("_sword") || p.equals("mace") || p.endsWith("_axe") || p.endsWith("_pickaxe")
                || p.endsWith("_helmet") || p.endsWith("_chestplate") || p.endsWith("_leggings") || p.endsWith("_boots")
                || p.equals("bow") || p.equals("ender_pearl") || p.equals("golden_apple") || p.equals("enchanted_golden_apple")) return true;
        return nonJunkItems.isEnabled() && (s.get(DataComponentTypes.FOOD) != null || p.equals("arrow") || p.equals("water_bucket"));
    }

    private static boolean hasHealing(ItemStack s) {
        PotionContentsComponent c = s.get(DataComponentTypes.POTION_CONTENTS);
        if (c == null) return false;
        boolean[] found = {false};
        c.forEachEffect(e -> {
            if (e.getEffectType().value() == StatusEffects.INSTANT_HEALTH.value()) found[0] = true;
        }, 1.0f);
        return found[0];
    }
}
