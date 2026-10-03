package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.entity.effect.StatusEffects;

public class NoLevitation extends Module {
    public NoLevitation() {
        super("NoLevitation", "Ignores levitation effect", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (mc.player.hasStatusEffect(StatusEffects.LEVITATION)) {
                mc.player.removeStatusEffect(StatusEffects.LEVITATION);
            }
        } catch (Throwable ignored) {}
    }
}
