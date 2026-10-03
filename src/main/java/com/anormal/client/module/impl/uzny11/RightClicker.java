package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.util.Hand;

public class RightClicker extends Module {
    public final NumberSetting cps = new NumberSetting("CPS", "Right clicks per second", 10, 1, 20, 1);
    public final BooleanSetting hold = new BooleanSetting("Hold", "Hold to click", false);

    private int delay = 0;

    public RightClicker() {
        super("RightClicker", "Automatically right clicks", Category.UZNY11);
        addSetting(cps);
        addSetting(hold);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (hold.isEnabled() && !mc.options.useKey.isPressed()) return;

        if (delay > 0) { delay--; return; }

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, mc.crosshairTarget);

        delay = (int) Math.max(1, Math.round(20.0 / cps.getValue()));
    }
}