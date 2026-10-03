package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public class WindCharge extends Module {
    public final NumberSetting aimSpeed = new NumberSetting("Aim Speed", "Speed of aim rotation to feet", 10.0, 1.0, 30.0, 0.5);
    public final BooleanSetting silentAim = new BooleanSetting("Silent Aim", "Throw without moving client camera", false);

    private int stage = 0;
    private int timer = 0;
    private int originalSlot = -1;

    public WindCharge() {
        super("WindCharge", "Uses wind charge at feet and jumps for maximum height", Category.UZNY11);
        addSetting(aimSpeed);
        addSetting(silentAim);
    }

    @Override
    public void onEnable() {
        stage = 0;
        timer = 0;
        originalSlot = -1;
    }

    @Override
    public void onDisable() {
        if (mc.options != null) mc.options.jumpKey.setPressed(false);
        timer = 0;
        stage = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (stage == 0) {
            int slot = findSlot("wind_charge");
            if (slot == -1) {
                setEnabled(false);
                return;
            }
            originalSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(slot);
            if (!silentAim.isEnabled()) {
                float cur = mc.player.getPitch();
                float step = (float) Math.max(-aimSpeed.getValue(), Math.min(aimSpeed.getValue(), 90.0f - cur));
                mc.player.setPitch(Math.max(-90.0f, Math.min(90.0f, cur + step)));
            }
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.options.jumpKey.setPressed(true);
            mc.player.setVelocity(new Vec3d(mc.player.getVelocity().x, Math.max(mc.player.getVelocity().y, 0.1), mc.player.getVelocity().z));
            timer = 3;
            stage = 1;
        } else {
            if (--timer <= 0) {
                mc.options.jumpKey.setPressed(false);
                if (originalSlot >= 0 && originalSlot < 9) mc.player.getInventory().setSelectedSlot(originalSlot);
                originalSlot = -1;
                stage = 0;
                setEnabled(false);
            }
        }
    }

    private int findSlot(String path) {
        for (int i = 0; i < 9; i++) {
            try {
                Identifier id = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem());
                if (id != null && id.getPath().equals(path)) return i;
            } catch (Throwable ignored) {}
        }
        return -1;
    }
}
