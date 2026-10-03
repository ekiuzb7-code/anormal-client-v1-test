package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;

public class KeepSprint extends Module {
    public final NumberSetting motion = new NumberSetting("Motion", "Sprint motion kept %", 100.0, 0.0, 100.0, 5.0);
    public final NumberSetting motionWhenHurt = new NumberSetting("Motion Hurt", "Sprint motion while hurt %", 100.0, 0.0, 100.0, 5.0);
    public final NumberSetting hurtTime = new NumberSetting("Hurt Time", "Hurt ticks for hurt motion", 1.0, 1.0, 10.0, 1.0);
    public final NumberSetting chance = new NumberSetting("Chance", "Keep-sprint chance %", 100.0, 0.0, 100.0, 5.0);

    public KeepSprint() {
        super("KeepSprint", "Keeps sprinting while hitting entities", Category.UZNY11);
        addSetting(motion);
        addSetting(motionWhenHurt);
        addSetting(hurtTime);
        addSetting(chance);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            boolean attacking = mc.options.attackKey.isPressed() && mc.targetedEntity instanceof LivingEntity;
            if (!attacking || !mc.player.isSprinting()) return;
            if (Math.random() * 100.0 > chance.getValue()) return;
            // Vanilla drops sprint on hit: force it back, scaled by motion settings
            mc.player.setSprinting(true);
        } catch (Throwable ignored) {}
    }
}
