package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.BooleanSetting;

public class Phase extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Phase mode", "Normal", "Normal", "Packet", "Vanilla");
    public final BooleanSetting blink = new BooleanSetting("Blink", "Blink while phasing", false);

    public Phase() {
        super("Phase", "Walks through blocks", Category.UZNY11);
        addSetting(mode);
        addSetting(blink);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        mc.player.noClip = true;
    }

    @Override
    public void onDisable() {
        if (mc.player != null) mc.player.noClip = false;
    }
}