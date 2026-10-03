package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.BooleanSetting;

public class Criticals extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Critical mode", "Packet", "Packet", "Jump", "Motion");
    public final BooleanSetting onlyGround = new BooleanSetting("Only Ground", "Only on ground", true);

    public Criticals() {
        super("Criticals", "Always gets critical hits", Category.UZNY11);
        addSetting(mode);
        addSetting(onlyGround);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (onlyGround.isEnabled() && !mc.player.isOnGround()) return;

        if (mc.options.attackKey.isPressed()) {
            if (mode.is("Packet")) {
                // Packet criticals
            } else if (mode.is("Jump")) {
                mc.player.jump();
            }
        }
    }
}