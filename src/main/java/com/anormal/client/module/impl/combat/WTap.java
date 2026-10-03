package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;

public class WTap extends Module {
    public final NumberSetting chance = new NumberSetting("Chance", "Chance to trigger WTap when beneficial", 80.0, 0.0, 100.0, 1.0);
    public final NumberSetting releaseDelay = new NumberSetting("Release Delay", "Ticks before releasing W after a hit", 1.0, 0.0, 10.0, 1.0);
    public final NumberSetting repressDelay = new NumberSetting("Re-press Delay", "Ticks before pressing W again", 3.0, 0.0, 10.0, 1.0);
    public final BooleanSetting selectHits = new BooleanSetting("Select Hits", "Only tap when target is vulnerable", true);
    private int phase;
    private int phaseTicks;

    public WTap() {
        super("WTap", "Automates W-tapping by releasing and re-pressing forward after hits", Category.COMBAT);
        addSetting(chance);
        addSetting(releaseDelay);
        addSetting(repressDelay);
        addSetting(selectHits);
    }

    @Override
    public void onEnable() {
        phase = 0;
        phaseTicks = 0;
    }

    @Override
    public void onDisable() {
        if (mc.options != null && phase != 0) mc.options.forwardKey.setPressed(true);
        phase = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.options == null || mc.currentScreen != null) return;
        if (phase == 1) {
            if (--phaseTicks <= 0) {
                phase = 2;
                phaseTicks = Math.max(1, repressDelay.getValue().intValue());
            }
            return;
        }
        if (phase == 2) {
            if (--phaseTicks <= 0) {
                mc.options.forwardKey.setPressed(true);
                phase = 0;
            }
            return;
        }
        if (!mc.options.forwardKey.isPressed()) return;
        if (!(mc.targetedEntity instanceof LivingEntity target) || !target.isAlive()) return;
        if (mc.player.distanceTo(target) > 4.5) return;
        if (selectHits.isEnabled() && target.hurtTime <= 0) return;
        if (Math.random() * 100.0 >= chance.getValue()) return;
        mc.options.forwardKey.setPressed(false);
        phase = 1;
        phaseTicks = Math.max(1, releaseDelay.getValue().intValue());
    }
}
