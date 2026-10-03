package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

public class InventoryManager extends Module {
    public final ModeSetting activation = new ModeSetting("Activation", "Toggle runs always, On Key bursts after press", "Toggle", "Toggle", "On Key");
    public final BooleanSetting openInventory = new BooleanSetting("Open Inventory", "Acts on its own without manual inventory", true);
    public final BooleanSetting combatCheck = new BooleanSetting("Combat Check", "Skips managing while in danger", true);
    public final NumberSetting clickDelay = new NumberSetting("Click Delay", "Ticks between inventory clicks", 2.0, 0.0, 20.0, 1.0);
    public final ModeSetting presets = new ModeSetting("Inventory Presets", "Loadout the manager maintains", "Balanced", "Balanced", "Rusher", "Fighter");

    private static final String[] BALANCED = {"_sword", "ender_pearl", "golden_apple", "splash_potion", "_pickaxe", "water_bucket", "cobweb", "torch", "mushroom_stew"};
    private static final String[] RUSHER = {"_pickaxe", "torch", "_sword", "ender_pearl", "golden_apple", "water_bucket", "cobweb", "splash_potion", "mushroom_stew"};
    private static final String[] FIGHTER = {"_sword", "mace", "ender_pearl", "golden_apple", "splash_potion", "_pickaxe", "water_bucket", "cobweb", "torch"};

    private int cooldown = 0;
    private int burst = 0;

    public InventoryManager() {
        super("InventoryManager", "Intelligent inventory manager combining cleaner, auto armor and hotbar sorting", Category.INVENTORY);
        addSetting(activation);
        addSetting(openInventory);
        addSetting(combatCheck);
        addSetting(clickDelay);
        addSetting(presets);
    }

    @Override
    public void onEnable() {
        burst = 100;
        cooldown = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (combatCheck.isEnabled() && (mc.player.hurtTime > 0 || mc.player.getAttacker() != null)) return;
        if (activation.is("On Key")) {
            if (burst <= 0) return;
            burst--;
        } else if (!openInventory.isEnabled() && !(mc.currentScreen instanceof InventoryScreen)) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (equipArmor() || sortHotbar() || dropJunk()) {
            cooldown = clickDelay.getValue().intValue();
        }
    }

    private boolean equipArmor() {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            int worn = score(mc.player.getEquippedStack(slot));
            int best = -1;
            int bestScore = worn;
            for (int i = 9; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                EquippableComponent eq = s.get(DataComponentTypes.EQUIPPABLE);
                if (eq == null || eq.slot() != slot) continue;
                int sc = score(s);
                if (sc > bestScore) {
                    bestScore = sc;
                    best = i;
                }
            }
            if (best != -1) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, best, 0, SlotActionType.QUICK_MOVE, mc.player);
                return true;
            }
        }
        return false;
    }

    private boolean sortHotbar() {
        String[] want = presets.is("Rusher") ? RUSHER : presets.is("Fighter") ? FIGHTER : BALANCED;
        for (int hot = 0; hot < 9; hot++) {
            ItemStack cur = mc.player.getInventory().getStack(hot);
            if (!cur.isEmpty() && Registries.ITEM.getId(cur.getItem()).getPath().contains(want[hot])) continue;
            for (int i = 9; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().contains(want[hot])) {
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, hot, SlotActionType.SWAP, mc.player);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean dropJunk() {
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) continue;
            String p = Registries.ITEM.getId(s.getItem()).getPath();
            if (p.equals("stick") || p.equals("string") || p.equals("flint") || p.equals("feather") || p.equals("glass_bottle")) {
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, 1, SlotActionType.THROW, mc.player);
                return true;
            }
        }
        return false;
    }

    private static int score(ItemStack s) {
        if (s.isEmpty()) return -1;
        String p = Registries.ITEM.getId(s.getItem()).getPath();
        return p.startsWith("netherite_") ? 6 : p.startsWith("diamond_") ? 5
                : p.startsWith("iron_") || p.startsWith("chainmail_") ? 4
                : p.startsWith("golden_") ? 3 : p.startsWith("turtle_") ? 2 : 1;
    }
}
