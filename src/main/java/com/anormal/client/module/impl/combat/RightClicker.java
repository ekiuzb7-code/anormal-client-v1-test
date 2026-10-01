package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Hand;

import java.util.Random;

public class RightClicker extends Module {
    public final NumberSetting minCps = new NumberSetting("Min CPS", "Minimum right clicks per second", 10.0, 1.0, 20.0, 0.5);
    public final NumberSetting maxCps = new NumberSetting("Max CPS", "Maximum right clicks per second", 14.0, 1.0, 20.0, 0.5);
    public final BooleanSetting holdOnly = new BooleanSetting("Hold Only", "Only clicks while right mouse button is held", true);

    private final Random random = new Random();
    private long lastClickTime = 0;
    private long nextDelay = 0;

    public RightClicker() {
        super("RightClicker", "Automates right-clicking for fast bridging or throwable spam", Category.COMBAT);
        addSetting(minCps);
        addSetting(maxCps);
        addSetting(holdOnly);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.currentScreen != null) return;
        if (holdOnly.isEnabled() && !mc.options.useKey.isPressed()) return;

        long now = System.currentTimeMillis();
        if (now - lastClickTime >= nextDelay) {
            lastClickTime = now;
            double min = Math.min(minCps.getValue(), maxCps.getValue());
            double max = Math.max(minCps.getValue(), maxCps.getValue());
            double cps = min + (max - min) * random.nextDouble();
            nextDelay = (long) (1000.0 / cps);

            if (mc.interactionManager != null) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }
}
