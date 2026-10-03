package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;

public class Animations extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Blockhit animation timing", "Manual", "Manual", "Auto", "Predict", "Lag");
    public final BooleanSetting requireMouseDown = new BooleanSetting("Require mouse down", "Require block held", true);
    public final BooleanSetting ignoreManualBlock = new BooleanSetting("Ignore manual block", "Prevent manual block", true);
    public final NumberSetting targetAngle = new NumberSetting("Angle", "Max target angle", 90.0, 0.0, 360.0, 5.0);
    public final NumberSetting targetDistance = new NumberSetting("Distance", "Max target distance", 5.0, 0.0, 6.0, 0.1);

    public Animations() {
        super("Animations", "Custom blockhit animations", Category.UZNY11);
        addSetting(mode); addSetting(requireMouseDown); addSetting(ignoreManualBlock);
        addSetting(targetAngle); addSetting(targetDistance);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            if (requireMouseDown.getValue() && !mc.options.useKey.isPressed()) return;
            boolean sword = false;
            try { sword = Registries.ITEM.getId(mc.player.getMainHandStack().getItem()).getPath().endsWith("sword"); } catch (Throwable t) {}
            if (!sword) return;
            boolean engaged = mc.targetedEntity != null && mc.player.distanceTo(mc.targetedEntity) <= targetDistance.getValue();
            if (mc.targetedEntity != null && targetAngle.getValue() < 360.0) engaged = engaged && mc.player.distanceTo(mc.targetedEntity) < 6.0;
            if (!engaged) return;
            if (mode.is("Auto")) mc.player.swingHand(Hand.OFF_HAND);
            else if (mode.is("Predict") && mc.player.getAttackCooldownProgress(0.5f) > 0.6f) mc.player.swingHand(Hand.OFF_HAND);
            else if (mode.is("Lag") && mc.player.hurtTime > 0) mc.player.swingHand(Hand.OFF_HAND);
            else if (mode.is("Manual") && mc.options.attackKey.isPressed()) mc.player.swingHand(Hand.OFF_HAND);
            if (ignoreManualBlock.getValue() && engaged) mc.player.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {}
    }
}
