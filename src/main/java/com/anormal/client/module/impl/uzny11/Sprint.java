package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class Sprint extends Module {
    public final BooleanSetting cancelInvis = new BooleanSetting("Cancel Invis", "Disable while invisible", false);

    public Sprint() {
        super("Sprint", "Automatically sprints", Category.UZNY11);
        addSetting(cancelInvis);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (cancelInvis.isEnabled() && mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY)) return;

        mc.player.setSprinting(true);
    }
}