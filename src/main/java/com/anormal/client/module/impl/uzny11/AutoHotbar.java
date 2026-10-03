package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

public class AutoHotbar extends Module {
    public final ModeSetting activation = new ModeSetting("Activation", "Toggle sorts always, On Key sorts once per press", "Toggle", "Toggle", "On Key");
    public final BooleanSetting openInventory = new BooleanSetting("Open Inventory", "Opens inventory while reorganizing", false);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between inventory clicks", 2.0, 0.0, 20.0, 1.0);
    public final ModeSetting hotbars = new ModeSetting("Hotbars", "Hotbar loadout to maintain", "Fighter", "Fighter", "Archer", "Potion");

    private static final String[] FIGHTER = {"_sword", "mace", "ender_pearl", "golden_apple", "splash_potion", "_pickaxe", "water_bucket", "cobweb", "torch"};
    private static final String[] ARCHER = {"bow", "arrow", "_sword", "ender_pearl", "golden_apple", "splash_potion", "_pickaxe", "water_bucket", "cobweb"};
    private static final String[] POTION = {"splash_potion", "mushroom_stew", "_sword", "ender_pearl", "golden_apple", "_pickaxe", "water_bucket", "cobweb", "bow"};

    private int cooldown = 0;

    public AutoHotbar() {
        super("AutoHotbar", "Automatically organizes and slots hotbar items into preferred slots", Category.UZNY11);
        addSetting(activation);
        addSetting(openInventory);
        addSetting(delay);
        addSetting(hotbars);
    }

    @Override
    public void onEnable() {
        cooldown = 0;
        if (activation.is("On Key") && mc.player != null && mc.interactionManager != null) {
            if (openInventory.isEnabled() && mc.currentScreen == null) mc.setScreen(new InventoryScreen(mc.player));
            for (int i = 0; i < 9 && sortOne(); i++) {
            }
            setEnabled(false);
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!activation.is("Toggle")) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (openInventory.isEnabled() && mc.currentScreen == null) {
            mc.setScreen(new InventoryScreen(mc.player));
            return;
        }
        if (sortOne()) cooldown = delay.getValue().intValue();
    }

    private boolean sortOne() {
        String[] want = hotbars.is("Archer") ? ARCHER : hotbars.is("Potion") ? POTION : FIGHTER;
        for (int hot = 0; hot < 9; hot++) {
            ItemStack cur = mc.player.getInventory().getStack(hot);
            String frag = want[hot];
            if (!cur.isEmpty() && Registries.ITEM.getId(cur.getItem()).getPath().contains(frag)) continue;
            int src = findSource(frag, hot);
            if (src == -1) continue;
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, src, hot, SlotActionType.SWAP, mc.player);
            return true;
        }
        return false;
    }

    private int findSource(String frag, int skipHot) {
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().contains(frag)) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (i == skipHot) continue;
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).getPath().contains(frag)) return i + 36;
        }
        return -1;
    }
}
