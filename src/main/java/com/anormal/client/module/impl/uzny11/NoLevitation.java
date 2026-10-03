package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

public class NoLevitation extends Module {
    public NoLevitation() {
        super("NoLevitation", "Prevents levitation effect", Category.UZNY11);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        mc.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.LEVITATION);
    }
}