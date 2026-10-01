package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.entity.effect.StatusEffects;

public class AntiDebuff extends Module {
    public AntiDebuff() {
        super("AntiDebuff", "Removes camera nausea, blindness fog and slowness FOV distort", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.player.hasStatusEffect(StatusEffects.NAUSEA)) {
            mc.player.removeStatusEffect(StatusEffects.NAUSEA);
        }
        if (mc.player.hasStatusEffect(StatusEffects.BLINDNESS)) {
            mc.player.removeStatusEffect(StatusEffects.BLINDNESS);
        }
        if (mc.player.hasStatusEffect(StatusEffects.DARKNESS)) {
            mc.player.removeStatusEffect(StatusEffects.DARKNESS);
        }
    }
}
