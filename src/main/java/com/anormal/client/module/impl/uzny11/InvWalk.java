package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class InvWalk extends Module {
    public final BooleanSetting inventoryOnly = new BooleanSetting("Inventory Only", "Only in inventory screen", false);
    public final BooleanSetting sneak = new BooleanSetting("Sneak", "Allow sneaking in GUIs", false);
    public final BooleanSetting rotate = new BooleanSetting("Rotate", "Arrow keys rotate head", false);

    public InvWalk() {
        super("InvWalk", "Walk and look in interfaces", Category.UZNY11);
        addSetting(inventoryOnly); addSetting(sneak); addSetting(rotate);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.options == null || mc.currentScreen == null) return;
        try {
            boolean isInv = mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen;
            if (inventoryOnly.getValue() && !isInv) return;
            mc.options.forwardKey.setPressed(mc.options.forwardKey.isPressed() || isDown(mc.options.forwardKey));
            if (sneak.getValue()) mc.options.sneakKey.setPressed(mc.options.sneakKey.isPressed() || isDown(mc.options.sneakKey));
            if (rotate.getValue()) {
                if (mc.options.leftKey.isPressed()) mc.player.setYaw(mc.player.getYaw() - 3.0f);
                if (mc.options.rightKey.isPressed()) mc.player.setYaw(mc.player.getYaw() + 3.0f);
            }
        } catch (Throwable ignored) {}
    }

    private boolean isDown(net.minecraft.client.option.KeyBinding k) {
        try { return k.isPressed(); } catch (Throwable t) { return false; }
    }

    @Override
    public void onDisable() {
        try {
            if (mc.options != null) {
                mc.options.forwardKey.setPressed(false);
                if (sneak.getValue()) mc.options.sneakKey.setPressed(false);
            }
        } catch (Throwable ignored) {}
    }
}
