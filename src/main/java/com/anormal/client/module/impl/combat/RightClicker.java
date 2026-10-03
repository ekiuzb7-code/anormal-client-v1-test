package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;

import java.util.Random;

public class RightClicker extends Module {
    public final NumberSetting minCps = new NumberSetting("Min CPS", "Minimum right clicks per second", 10.0, 1.0, 20.0, 0.5);
    public final NumberSetting maxCps = new NumberSetting("Max CPS", "Maximum right clicks per second", 14.0, 1.0, 20.0, 0.5);
    public final NumberSetting startDelay = new NumberSetting("Start Delay", "Millis holding before clicking starts", 150.0, 0.0, 1000.0, 10.0);
    public final NumberSetting blockPlaceDelay = new NumberSetting("Block Place Delay", "Millis between block placements", 120.0, 0.0, 1000.0, 10.0);
    public final ModeSetting randomization = new ModeSetting("Randomization", "CPS randomness pattern", "Normal", "Normal", "Extra", "Extra+");
    public final BooleanSetting jitter = new BooleanSetting("Jitter", "Jitter cursor while clicking", false);
    public final BooleanSetting useItemWhitelist = new BooleanSetting("Use Item Whitelist", "Only click while holding blocks", false);
    private final Random random = new Random();
    private long holdStart;
    private long lastClick;
    private long nextDelay;

    public RightClicker() {
        super("RightClicker", "Automates right-clicking for bridging or throwable spam", Category.COMBAT);
        addSetting(minCps);
        addSetting(maxCps);
        addSetting(startDelay);
        addSetting(blockPlaceDelay);
        addSetting(randomization);
        addSetting(jitter);
        addSetting(useItemWhitelist);
    }

    @Override
    public void onEnable() {
        holdStart = 0;
        lastClick = 0;
        nextDelay = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (!mc.options.useKey.isPressed()) {
            holdStart = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (holdStart == 0) holdStart = now;
        if (now - holdStart < startDelay.getValue()) return;
        boolean placing = mc.player.getMainHandStack().getItem() instanceof BlockItem;
        if (useItemWhitelist.isEnabled() && !placing) return;
        if (now - lastClick < nextDelay) return;
        lastClick = now;
        if (placing) {
            nextDelay = blockPlaceDelay.getValue().longValue();
        } else {
            double min = Math.min(minCps.getValue(), maxCps.getValue());
            double max = Math.max(minCps.getValue(), maxCps.getValue());
            double cps = min + (max - min) * random.nextDouble();
            if (randomization.is("Extra")) cps *= 0.9 + random.nextDouble() * 0.2;
            if (randomization.is("Extra+")) {
                cps *= 0.85 + random.nextDouble() * 0.3;
                if (random.nextDouble() < 0.05) nextDelay = 120 + random.nextInt(120);
                else nextDelay = (long) (1000.0 / Math.max(0.5, cps));
            } else nextDelay = (long) (1000.0 / Math.max(0.5, cps));
        }
        if (jitter.isEnabled()) {
            mc.player.setYaw(mc.player.getYaw() + (random.nextFloat() - 0.5f) * 0.6f);
            mc.player.setPitch(mc.player.getPitch() + (random.nextFloat() - 0.5f) * 0.6f);
        }
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
