package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class Sprint extends Module {
    public final BooleanSetting omniSprint = new BooleanSetting("Omni Sprint", "Sprint in all movement directions", true);
    public final BooleanSetting keepSprint = new BooleanSetting("Keep Sprint", "Prevents sprint cancellation on hits", true);
    public final BooleanSetting cancelInvis = new BooleanSetting("Cancel Invis", "Disables sprint when under Invisibility potion", false);
    public final BooleanSetting checkBlindness = new BooleanSetting("Check Blindness", "Disables sprint when affected by Blindness", true);

    public Sprint() {
        super("Sprint", "Automatically sprints when walking forward or moving", Category.MOVEMENT);
        addSetting(omniSprint);
        addSetting(keepSprint);
        addSetting(cancelInvis);
        addSetting(checkBlindness);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (cancelInvis.isEnabled() && mc.player.isInvisible()) return;
        if (checkBlindness.isEnabled() && mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)) return;

        boolean isMoving = omniSprint.isEnabled() 
                ? (mc.player.forwardSpeed != 0 || mc.player.sidewaysSpeed != 0) 
                : (mc.player.forwardSpeed > 0);

        if (isMoving && !mc.player.isSneaking() && !mc.player.horizontalCollision) {
            mc.player.setSprinting(true);
        }
    }
}
