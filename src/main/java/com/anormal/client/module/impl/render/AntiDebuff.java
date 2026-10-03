package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.entity.effect.StatusEffects;

public class AntiDebuff extends Module {
    public final BooleanSetting removeNausea = new BooleanSetting("Remove Nausea", "Eliminates the swirling nausea camera effect", true);
    public final BooleanSetting removeBlindness = new BooleanSetting("Remove Blindness", "Restores view distance and removes blinding fog", true);
    public final BooleanSetting removeSlowness = new BooleanSetting("Remove Slowness", "Clears slowness to prevent its FOV change", true);
    public final BooleanSetting removeEffects = new BooleanSetting("Remove Effects", "Removes all negative status effects", false);

    public AntiDebuff() {
        super("AntiDebuff", "Removes camera nausea, blindness fog and slowness FOV distort", Category.RENDER);
        addSetting(removeNausea);
        addSetting(removeBlindness);
        addSetting(removeSlowness);
        addSetting(removeEffects);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (removeNausea.isEnabled() && mc.player.hasStatusEffect(StatusEffects.NAUSEA))
                mc.player.removeStatusEffect(StatusEffects.NAUSEA);
            if (removeBlindness.isEnabled()) {
                if (mc.player.hasStatusEffect(StatusEffects.BLINDNESS))
                    mc.player.removeStatusEffect(StatusEffects.BLINDNESS);
                if (mc.player.hasStatusEffect(StatusEffects.DARKNESS))
                    mc.player.removeStatusEffect(StatusEffects.DARKNESS);
            }
            if (removeSlowness.isEnabled() && mc.player.hasStatusEffect(StatusEffects.SLOWNESS))
                mc.player.removeStatusEffect(StatusEffects.SLOWNESS);
            if (removeEffects.isEnabled()) {
                if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE))
                    mc.player.removeStatusEffect(StatusEffects.MINING_FATIGUE);
                if (mc.player.hasStatusEffect(StatusEffects.WEAKNESS))
                    mc.player.removeStatusEffect(StatusEffects.WEAKNESS);
                if (mc.player.hasStatusEffect(StatusEffects.HUNGER))
                    mc.player.removeStatusEffect(StatusEffects.HUNGER);
                if (mc.player.hasStatusEffect(StatusEffects.POISON))
                    mc.player.removeStatusEffect(StatusEffects.POISON);
                if (mc.player.hasStatusEffect(StatusEffects.WITHER))
                    mc.player.removeStatusEffect(StatusEffects.WITHER);
                if (mc.player.hasStatusEffect(StatusEffects.UNLUCK))
                    mc.player.removeStatusEffect(StatusEffects.UNLUCK);
            }
        } catch (Throwable ignored) {}
    }
}
