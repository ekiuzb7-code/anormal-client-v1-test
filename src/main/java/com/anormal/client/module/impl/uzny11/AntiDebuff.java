package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class AntiDebuff extends Module {
    public final BooleanSetting nausea = new BooleanSetting("Nausea", "Remove nausea effect", true);
    public final BooleanSetting blindness = new BooleanSetting("Blindness", "Remove blindness effect", true);
    public final BooleanSetting slowness = new BooleanSetting("Slowness", "Remove slowness effect", true);
    public final BooleanSetting poison = new BooleanSetting("Poison", "Remove poison effect", false);
    public final BooleanSetting wither = new BooleanSetting("Wither", "Remove wither effect", false);

    public AntiDebuff() {
        super("AntiDebuff", "Removes negative potion effects", Category.UZNY11);
        addSetting(nausea);
        addSetting(blindness);
        addSetting(slowness);
        addSetting(poison);
        addSetting(wither);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (nausea.isEnabled()) mc.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.NAUSEA);
        if (blindness.isEnabled()) mc.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
        if (slowness.isEnabled()) mc.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
        if (poison.isEnabled()) mc.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.POISON);
        if (wither.isEnabled()) mc.player.removeStatusEffect(net.minecraft.entity.effect.StatusEffects.WITHER);
    }
}