package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

public class AutoArmor extends Module {
    public final BooleanSetting openInventory = new BooleanSetting("Open Inventory", "Opens inventory to equip better armor", true);
    public final BooleanSetting inventoryOnly = new BooleanSetting("Inventory Only", "Only equips while inventory is open", false);
    public final BooleanSetting checkDurability = new BooleanSetting("Check Durability", "Weighs remaining durability into armor choice", true);
    public final BooleanSetting dropEquipped = new BooleanSetting("Drop Equipped", "Drops replaced armor pieces", false);
    public final BooleanSetting combatCheck = new BooleanSetting("Combat Check", "Skips swapping while taking damage", true);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between armor moves", 3.0, 0.0, 20.0, 1.0);

    private int cooldown = 0;
    private String pendingDrop = null;

    public AutoArmor() {
        super("AutoArmor", "Automatically equips the highest protection armor available", Category.INVENTORY);
        addSetting(openInventory);
        addSetting(inventoryOnly);
        addSetting(checkDurability);
        addSetting(dropEquipped);
        addSetting(combatCheck);
        addSetting(delay);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (combatCheck.isEnabled() && mc.player.hurtTime > 0) return;
        if (inventoryOnly.isEnabled() && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (pendingDrop != null) {
            dropStack(pendingDrop);
            pendingDrop = null;
            cooldown = delay.getValue().intValue();
            return;
        }
        int src = findUpgrade();
        if (src == -1) return;
        if (openInventory.isEnabled() && mc.currentScreen == null) {
            mc.setScreen(new InventoryScreen(mc.player));
            return;
        }
        ItemStack before = mc.player.getEquippedStack(handlerToArmor(src)).copy();
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, src, 0, SlotActionType.QUICK_MOVE, mc.player);
        if (dropEquipped.isEnabled() && !before.isEmpty()) {
            pendingDrop = Registries.ITEM.getId(before.getItem()).getPath();
        }
        cooldown = delay.getValue().intValue();
    }

    private int findUpgrade() {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            int wornScore = score(mc.player.getEquippedStack(slot));
            int best = -1;
            int bestScore = wornScore;
            for (int i = 9; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (!isArmorFor(s, slot)) continue;
                int sc = score(s);
                if (sc > bestScore) {
                    bestScore = sc;
                    best = i;
                }
            }
            if (best != -1) return best;
        }
        return -1;
    }

    private void dropStack(String path) {
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().equals(path)) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, 1, SlotActionType.THROW, mc.player);
                return;
            }
        }
    }

    private EquipmentSlot handlerToArmor(int handlerSlot) {
        ItemStack s = mc.player.getInventory().getStack(handlerSlot);
        EquippableComponent eq = s.get(DataComponentTypes.EQUIPPABLE);
        return eq == null ? EquipmentSlot.CHEST : eq.slot();
    }

    private int score(ItemStack s) {
        if (s.isEmpty()) return -1;
        if (!isAnyArmor(s)) return -1;
        String p = Registries.ITEM.getId(s.getItem()).getPath();
        int mat = p.startsWith("netherite_") ? 6 : p.startsWith("diamond_") ? 5
                : p.startsWith("turtle_") ? 4 : p.startsWith("iron_") || p.startsWith("chainmail_") ? 3
                : p.startsWith("golden_") ? 2 : 1;
        int score = mat * 1000;
        if (checkDurability.isEnabled() && s.getMaxDamage() > 0) {
            score += (int) (500.0 * (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage());
        }
        return score;
    }

    private static boolean isArmorFor(ItemStack s, EquipmentSlot slot) {
        EquippableComponent eq = s.get(DataComponentTypes.EQUIPPABLE);
        return eq != null && eq.slot() == slot;
    }

    private static boolean isAnyArmor(ItemStack s) {
        EquippableComponent eq = s.get(DataComponentTypes.EQUIPPABLE);
        if (eq == null) return false;
        EquipmentSlot slot = eq.slot();
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }
}
