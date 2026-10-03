package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import java.util.Random;

public class AutoHeal extends Module {
    public final ModeSetting useMode = new ModeSetting("Mode", "Heal method", "Legit", "Silent", "Legit");
    public final BooleanSetting useSoup = new BooleanSetting("Soup", "Use soups to heal", false);
    public final ModeSetting bowlMode = new ModeSetting("Bowl Mode", "Bowl handling", "Stack", "Throw", "Stack", "None");
    public final BooleanSetting usePotions = new BooleanSetting("Potions", "Use splash healing", false);
    public final BooleanSetting useRegen = new BooleanSetting("Regen", "Use regeneration pots", true);
    public final BooleanSetting useSpeed = new BooleanSetting("Speed", "Use speed pots", false);
    public final BooleanSetting useResistance = new BooleanSetting("Resistance", "Use resistance pots", false);
    public final NumberSetting delay = new NumberSetting("Delay", "Delay between heals ms", 500.0, 50.0, 1000.0, 50.0);
    public final NumberSetting health = new NumberSetting("Health", "Heal below HP", 17.0, 1.0, 20.0, 1.0);
    public final NumberSetting slot = new NumberSetting("Slot", "Hotbar slot for potion", 6.0, 1.0, 9.0, 1.0);
    public final BooleanSetting replaceItems = new BooleanSetting("Replace", "Refill empty heal slots", false);
    public final BooleanSetting inventoryOnly = new BooleanSetting("Inventory Only", "Only with inventory open", false);
    private final Random random = new Random();
    private long lastHeal = 0;

    public AutoHeal() {
        super("AutoHeal", "Automatically heals below threshold", Category.UZNY11);
        addSetting(useMode); addSetting(useSoup); addSetting(bowlMode); addSetting(usePotions);
        addSetting(useRegen); addSetting(useSpeed); addSetting(useResistance);
        addSetting(delay); addSetting(health); addSetting(slot);
        addSetting(replaceItems); addSetting(inventoryOnly);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null) return;
        try {
            if (inventoryOnly.getValue() && !(mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen)) return;
            if (mc.player.getHealth() > health.getValue()) return;
            if (System.currentTimeMillis() - lastHeal < delay.getValue()) return;
            int healSlot = findHeal();
            if (healSlot == -1) return;
            int prev = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(healSlot);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            if (useMode.is("Legit")) mc.player.swingHand(Hand.MAIN_HAND);
            if (!bowlMode.is("Stack") && useSoup.getValue()) mc.player.swingHand(Hand.MAIN_HAND);
            if (!useMode.is("Silent")) mc.player.getInventory().setSelectedSlot(prev);
            else mc.player.getInventory().setSelectedSlot(prev);
            lastHeal = System.currentTimeMillis() + (replaceItems.getValue() ? random.nextInt(80) : 0);
        } catch (Throwable ignored) {}
    }

    private int findHeal() {
        for (int i = 0; i < 9; i++) {
            try {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isEmpty()) continue;
                String p = net.minecraft.registry.Registries.ITEM.getId(s.getItem()).getPath();
                String n = s.getName().getString().toLowerCase();
                if (useSoup.getValue() && p.contains("stew")) return i;
                if (usePotions.getValue() && p.contains("potion") && n.contains("heal")) return i;
                if (useRegen.getValue() && p.contains("potion") && n.contains("regen")) return i;
                if (useSpeed.getValue() && p.contains("potion") && n.contains("swift")) return i;
                if (useResistance.getValue() && p.contains("potion") && n.contains("resist")) return i;
            } catch (Throwable ignored) {}
        }
        return -1;
    }
}
