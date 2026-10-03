package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import java.util.Random;

public class LeftClicker extends Module {
    public final BooleanSetting holdToClick = new BooleanSetting("Hold to click", "Only while attack held", true);
    public final BooleanSetting triggerMode = new BooleanSetting("Trigger mode", "Only while hovering entity", false);
    public final BooleanSetting breakBlocks = new BooleanSetting("Break blocks", "Pause while breaking blocks", false);
    public final NumberSetting breakBlocksDelay = new NumberSetting("Break blocks delay", "Delay before pause ms", 0.0, 0.0, 2000.0, 50.0);
    public final NumberSetting minCps = new NumberSetting("Min CPS", "Minimum clicks per second", 6.0, 1.0, 20.0, 0.5);
    public final NumberSetting maxCps = new NumberSetting("Max CPS", "Maximum clicks per second", 13.0, 1.0, 20.0, 0.5);
    public final ModeSetting randomization = new ModeSetting("Randomization", "CPS pattern", "Extra", "Normal", "Extra", "Extra+");
    public final BooleanSetting jitter = new BooleanSetting("Jitter", "Jitter cursor while clicking", false);
    public final BooleanSetting limitItems = new BooleanSetting("Limit items", "Only with swords held", false);
    private final Random random = new Random();
    private long lastClick = 0;
    private long nextDelay = 0;

    public LeftClicker() {
        super("LeftClicker", "Automates left clicking", Category.UZNY11);
        addSetting(holdToClick); addSetting(triggerMode); addSetting(breakBlocks);
        addSetting(breakBlocksDelay); addSetting(minCps); addSetting(maxCps);
        addSetting(randomization); addSetting(jitter); addSetting(limitItems);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        try {
            if (holdToClick.getValue() && !mc.options.attackKey.isPressed()) return;
            if (triggerMode.getValue() && mc.targetedEntity == null) return;
            if (limitItems.getValue()) {
                try { if (!Registries.ITEM.getId(mc.player.getMainHandStack().getItem()).getPath().endsWith("sword")) return; }
                catch (Throwable t) { return; }
            }
            if (breakBlocks.getValue() && mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK && mc.options.attackKey.isPressed()) { long bd = breakBlocksDelay.getValue().longValue(); if (bd >= 0) return; }
            long now = System.currentTimeMillis();
            if (now - lastClick < nextDelay) return;
            lastClick = now;
            double min = Math.min(minCps.getValue(), maxCps.getValue());
            double max = Math.max(minCps.getValue(), maxCps.getValue());
            double cps = min + (max - min) * random.nextDouble();
            if (randomization.is("Extra")) cps = min + (max - min) * (0.5 + random.nextGaussian() * 0.25);
            nextDelay = (long) (1000.0 / Math.max(1.0, cps));
            if (jitter.getValue()) mc.player.setYaw(mc.player.getYaw() + (random.nextFloat() - 0.5f) * 0.5f);
            mc.player.swingHand(Hand.MAIN_HAND);
            if (mc.targetedEntity != null && mc.interactionManager != null) mc.interactionManager.attackEntity(mc.player, mc.targetedEntity);
        } catch (Throwable ignored) {}
    }
}
