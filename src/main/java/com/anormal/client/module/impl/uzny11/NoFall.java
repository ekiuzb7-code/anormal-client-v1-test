package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;

public class NoFall extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "NoFall method", "Normal", "Normal", "AntiCheat");

    public NoFall() {
        super("NoFall", "Prevents fall damage", Category.UZNY11);
        addSetting(mode);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (mode.is("Normal")) {
            // Classic NoFall - cancel fall damage packets
            if (!mc.player.isOnGround() && mc.player.fallDistance > 3.0f) {
                mc.player.fallDistance = 0.0f;
            }
        } else if (mode.is("AntiCheat")) {
            // AntiCheat compatible - limit fall distance
            if (mc.player.fallDistance > 3.0f) {
                mc.player.fallDistance = 3.0f;
            }
        }
    }
}