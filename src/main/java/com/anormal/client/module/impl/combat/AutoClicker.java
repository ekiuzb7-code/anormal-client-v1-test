package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
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
    public final ModeSetting randomization = new ModeSetting("Randomization", "Human-like CPS pattern", "Normal", "Normal", "Extra", "Extra+");

    private final Random random = new Random();
    private long lastClickTime = 0;
    private long nextDelay = 0;
    private double fatigue = 0.0; // slow CPS drift (Extra+)
    private int microBreak = 0;

    public AutoClicker() {
        super("AutoClicker", "Automates left-clicking with randomized CPS", Category.COMBAT);
        addSetting(minCps);
        addSetting(maxCps);
        addSetting(holdToClick);
        addSetting(breakBlocks);
        addSetting(swordsOnly);
        addSetting(jitter);
        addSetting(jitterStrength);
        addSetting(randomization);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        if (holdToClick.isEnabled() && !mc.options.attackKey.isPressed()) {
            return;
        }

        if (swordsOnly.isEnabled() && !isWeaponHeld()) {
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
            if (randomization.is("Extra")) {
                // Gaussian human jitter around target instead of flat uniform
                targetCps = min + (max - min) * Math.min(1.0, Math.max(0.0, 0.5 + random.nextGaussian() * 0.3));
            } else if (randomization.is("Extra+")) {
                // Fatigue drift: CPS slowly waves over minutes like a real hand
                fatigue += (random.nextDouble() - 0.5) * 0.06;
                fatigue = Math.max(-1.2, Math.min(1.2, fatigue));
                targetCps = Math.min(max + 0.5, Math.max(min - 0.5, targetCps + fatigue));
                // Occasional micro-breaks (60-160ms) break statistical rhythm
                if (microBreak > 0) {
                    microBreak--;
                    nextDelay = 60 + (long) (random.nextDouble() * 100);
                    return;
                }
                if (random.nextDouble() < 0.012) {
                    microBreak = 1 + random.nextInt(3);
                    nextDelay = 60 + (long) (random.nextDouble() * 100);
                    return;
                }
            }
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

    private boolean isWeaponHeld() {
        Identifier id = Registries.ITEM.getId(mc.player.getMainHandStack().getItem());
        if (id == null) return false;
        String path = id.getPath();
        return path.endsWith("_sword") || path.endsWith("_axe")
                || path.equals("mace") || path.equals("trident");
    }
}
