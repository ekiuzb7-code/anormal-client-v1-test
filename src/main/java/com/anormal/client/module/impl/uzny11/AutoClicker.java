package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class AutoClicker extends Module {
    public final NumberSetting minCPS = new NumberSetting("Min CPS", "Minimum clicks per second", 8, 1, 20, 1);
    public final NumberSetting maxCPS = new NumberSetting("Max CPS", "Maximum clicks per second", 12, 1, 20, 1);
    public final BooleanSetting rightClick = new NumberSetting("Right Click", "Right click CPS", 0, 0, 20, 1);
    public final ModeSetting mode = new ModeSetting("Mode", "Click mode", "Normal", "Normal", "Random", "Extra");
    public final BooleanSetting swordOnly = new BooleanSetting("Sword Only", "Only click with sword", false);
    public final BooleanSetting block = new BooleanSetting("Block", "Auto block when not clicking", false);
    public final BooleanSetting click = new BooleanSetting("Left Click", "Left click", true);
    public final BooleanSetting hold = new BooleanSetting("Hold", "Hold to click", false);

    private int delay = 0;
    private boolean wasClicking = false;

    public AutoClicker() {
        super("AutoClicker", "Automatically clicks for you", Category.UZNY11);
        addSetting(minCPS);
        addSetting(maxCPS);
        addSetting(rightClick);
        addSetting(mode);
        addSetting(swordOnly);
        addSetting(block);
        addSetting(click);
        addSetting(hold);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (hold.isEnabled() && !mc.options.attackKey.isPressed()) return;

        if (delay > 0) {
            delay--;
            if (wasClicking) {
                mc.options.attackKey.setPressed(false);
                mc.options.useKey.setPressed(false);
                wasClicking = false;
            }
            return;
        }

        boolean shouldClick = click.isEnabled() && mc.options.attackKey.isPressed();
        boolean shouldRightClick = rightClick.getValue() > 0 && mc.options.useKey.isPressed();

        if (swordOnly.isEnabled() && !isHoldingWeapon()) return;

        if (shouldClick) {
            mc.interactionManager.attackEntity(mc.player, mc.targetedEntity);
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        if (shouldRightClick) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, mc.crosshairTarget);
        }

        double min = Math.min(minCPS.getValue(), maxCPS.getValue());
        double max = Math.max(minCPS.getValue(), maxCPS.getValue());
        double cps = min + Math.random() * (max - min);
        delay = (int) Math.max(1, Math.round(20.0 / Math.max(0.5, cps)));
        wasClicking = true;
    }

    private boolean isHoldingWeapon() {
        var stack = mc.player.getMainHandStack();
        return stack.getItem() instanceof net.minecraft.item.SwordItem ||
               stack.getItem() instanceof net.minecraft.item.AxeItem ||
               stack.getItem() instanceof net.minecraft.item.MaceItem;
    }
}