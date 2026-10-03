package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

public class ArmorSwitch extends Module {
    public final ModeSetting sets = new ModeSetting("Sets", "Armor material pair to swap between", "Diamond <> Gold", "Diamond <> Gold", "Netherite <> Gold", "Iron <> Diamond");
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between equipping pieces", 3.0, 0.0, 20.0, 1.0);

    private String target = "";
    private int cooldown = 0;

    public ArmorSwitch() {
        super("ArmorSwitch", "Switches between two configured sets of armor on keypress", Category.INVENTORY);
        addSetting(sets);
        addSetting(delay);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) {
            setEnabled(false);
            return;
        }
        String[] pair = sets.getValue().toLowerCase().split("<>");
        String a = pair[0].trim();
        String current = majorityWorn();
        target = current.equals(a) && pair.length > 1 ? pair[1].trim() : a;
        cooldown = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            setEnabled(false);
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        int src = findPiece();
        if (src == -1) {
            setEnabled(false);
            return;
        }
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, src, 0, SlotActionType.QUICK_MOVE, mc.player);
        cooldown = delay.getValue().intValue();
    }

    private int findPiece() {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            ItemStack worn = mc.player.getEquippedStack(slot);
            if (!worn.isEmpty() && materialOf(worn).equals(target)) continue;
            for (int i = 9; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isEmpty() || !isArmorFor(s, slot) || !materialOf(s).equals(target)) continue;
                return i;
            }
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isEmpty() || !isArmorFor(s, slot) || !materialOf(s).equals(target)) continue;
                return i + 36;
            }
        }
        return -1;
    }

    private String majorityWorn() {
        String best = "";
        int bestCount = 0;
        String[] mats = sets.getValue().toLowerCase().split("<>");
        for (String m : mats) {
            int n = 0;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack worn = mc.player.getEquippedStack(slot);
                if (!worn.isEmpty() && materialOf(worn).equals(m.trim())) n++;
            }
            if (n > bestCount) {
                bestCount = n;
                best = m.trim();
            }
        }
        return best.isEmpty() ? mats[0].trim() : best;
    }

    private static boolean isArmorFor(ItemStack s, EquipmentSlot slot) {
        EquippableComponent eq = s.get(DataComponentTypes.EQUIPPABLE);
        return eq != null && eq.slot() == slot;
    }

    private static String materialOf(ItemStack s) {
        String p = Registries.ITEM.getId(s.getItem()).getPath();
        int cut = p.lastIndexOf('_');
        return cut <= 0 ? p : p.substring(0, cut);
    }
}
