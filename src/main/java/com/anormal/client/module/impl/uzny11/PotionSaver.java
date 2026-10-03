package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class PotionSaver extends Module {
    public final BooleanSetting onlyWhenStill = new BooleanSetting("Only When Still", "Save only while standing", true);
    public final BooleanSetting requireGround = new BooleanSetting("Require Ground", "Save only on ground", true);

    public PotionSaver() {
        super("PotionSaver", "Saves potion duration while still", Category.UZNY11);
        addSetting(onlyWhenStill); addSetting(requireGround);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        try {
            if (mc.player.getStatusEffects().isEmpty()) return;
            boolean moving = mc.player.forwardSpeed != 0 || mc.player.sidewaysSpeed != 0;
            if (onlyWhenStill.getValue() && moving) return;
            if (requireGround.getValue() && !mc.player.isOnGround()) return;
            mc.player.fallDistance = 0.0f;
        } catch (Throwable ignored) {}
    }

    private boolean saving = false;

    public boolean isSaving() {
        return saving;
    }

    @Override
    public void onDisable() {
        saving = false;
    }
}
