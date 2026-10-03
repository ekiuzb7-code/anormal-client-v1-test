package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.util.Hand;

public class LeftClicker extends Module {
    public final NumberSetting cps = new NumberSetting("CPS", "Clicks per second", 10, 1, 20, 1);
    public final BooleanSetting hold = new BooleanSetting("Hold", "Hold to click", false);

    private int delay = 0;

    public LeftClicker() {
        super("LeftClicker", "Automatically left clicks", Category.UZNY11);
        addSetting(cps);
        addSetting(hold);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (hold.isEnabled() && !mc.options.attackKey.isPressed()) return;

        if (delay > 0) { delay--; return; }

        mc.interactionManager.attackEntity(mc.player, mc.targetedEntity);
        mc.player.swingHand(Hand.MAIN_HAND);

        delay = (int) Math.max(1, Math.round(20.0 / cps.getValue()));
    }
}