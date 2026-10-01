package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class Sprint extends Module {
    public final BooleanSetting cancelInvis = new BooleanSetting("Cancel Invis", "Disables sprint when under Invisibility potion", true);

    public Sprint() {
        super("Sprint", "Automatically sprints when walking forward", Category.MOVEMENT);
        addSetting(cancelInvis);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (cancelInvis.isEnabled() && mc.player.isInvisible()) {
            return;
        }
        if (mc.player.forwardSpeed > 0 && !mc.player.isSneaking() && !mc.player.horizontalCollision) {
            mc.player.setSprinting(true);
        }
    }
}
