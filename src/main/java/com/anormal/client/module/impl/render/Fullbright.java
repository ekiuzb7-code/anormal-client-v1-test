package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class Fullbright extends Module {
    public Fullbright() {
        super("Fullbright", "Brightens the entire world to 100% night vision brightness", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (mc.player != null) {
            mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false, false));
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }
}
