package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;

import java.util.Random;

public class AutoClicker extends Module {
    public final NumberSetting minCps = new NumberSetting("Min CPS", "Minimum clicks per second", 9.0, 1.0, 20.0, 0.5);
    public final NumberSetting maxCps = new NumberSetting("Max CPS", "Maximum clicks per second", 14.0, 1.0, 20.0, 0.5);
    public final BooleanSetting holdToClick = new BooleanSetting("Hold to Click", "Only click while attack button is held", true);
    public final BooleanSetting breakBlocks = new BooleanSetting("Break Blocks", "Pauses clicking when breaking blocks", true);
    public final BooleanSetting swordsOnly = new BooleanSetting("Swords Only", "Only clicks when holding a weapon", false);
    public final BooleanSetting jitter = new BooleanSetting("Jitter", "Simulates hand jitter while clicking", false);
    public final NumberSetting jitterStrength = new NumberSetting("Jitter Strength", "Intensity of jitter aim offset", 0.5, 0.1, 2.0, 0.1);

    private final Random random = new Random();
    private long lastClickTime = 0;
    private long nextDelay = 0;

    public AutoClicker() {
        super("AutoClicker", "Automates left-clicking with randomized CPS", Category.COMBAT);
        addSetting(minCps);
        addSetting(maxCps);
        addSetting(holdToClick);
        addSetting(breakBlocks);
        addSetting(swordsOnly);
        addSetting(jitter);
        addSetting(jitterStrength);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        if (holdToClick.isEnabled() && !mc.options.attackKey.isPressed()) {
            return;
        }

        if (swordsOnly.isEnabled() && !(mc.player.getMainHandStack().getItem() instanceof net.minecraft.item.SwordItem || mc.player.getMainHandStack().getItem() instanceof net.minecraft.item.AxeItem)) {
            return;
        }

        if (breakBlocks.isEnabled() && mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK && mc.options.attackKey.isPressed()) {
            // Player is actively mining
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastClickTime >= nextDelay) {
            lastClickTime = now;
            double min = Math.min(minCps.getValue(), maxCps.getValue());
            double max = Math.max(minCps.getValue(), maxCps.getValue());
            double targetCps = min + (max - min) * random.nextDouble();
            nextDelay = (long) (1000.0 / targetCps);

            // Jitter simulation
            if (jitter.isEnabled()) {
                float j = jitterStrength.getValue().floatValue();
                mc.player.setYaw(mc.player.getYaw() + (random.nextFloat() - 0.5f) * j);
                mc.player.setPitch(mc.player.getPitch() + (random.nextFloat() - 0.5f) * j);
            }

            // Execute click
            if (mc.interactionManager != null) {
                mc.player.swingHand(Hand.MAIN_HAND);
                if (mc.targetedEntity != null) {
                    mc.interactionManager.attackEntity(mc.player, mc.targetedEntity);
                }
            }
        }
    }
}
