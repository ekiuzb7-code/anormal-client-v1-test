package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class OmniSprint extends Module {
    public final BooleanSetting cancelInvis = new BooleanSetting("Cancel Invis", "Disable while invisible", false);
    public final BooleanSetting sneak = new BooleanSetting("Sneak Sprint", "Sprint while sneaking", false);

    public OmniSprint() {
        super("OmniSprint", "Sprints in all directions", Category.UZNY11);
        addSetting(cancelInvis);
        addSetting(sneak);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        mc.player.setSprinting(true);
    }
}