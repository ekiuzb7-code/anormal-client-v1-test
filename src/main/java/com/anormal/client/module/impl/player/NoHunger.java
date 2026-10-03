package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

public class NoHunger extends Module {
    public final NumberSetting minFood = new NumberSetting("Min Food", "Act below this hunger", 10.0, 1.0, 20.0, 1.0);
    public final BooleanSetting disableSprint = new BooleanSetting("Disable Sprint", "Stop sprinting when hungry", true);
    public final BooleanSetting disableJump = new BooleanSetting("Disable Jump", "Stop jumping when starving", false);
    public final BooleanSetting warn = new BooleanSetting("Warn", "Chat warning when hungry", true);
    public final NumberSetting warnAt = new NumberSetting("Warn At", "Warn below this hunger", 6.0, 1.0, 20.0, 1.0);
    public final NumberSetting checkDelay = new NumberSetting("Check Delay", "Ticks between hunger checks", 20.0, 5.0, 100.0, 5.0);

    private int ticks = 0;
    private boolean warned = false;

    public NoHunger() {
        super("NoHunger", "Saves hunger by stopping drain actions", Category.PLAYER);
        addSetting(minFood);
        addSetting(disableSprint);
        addSetting(disableJump);
        addSetting(warn);
        addSetting(warnAt);
        addSetting(checkDelay);
    }

    private int food() {
        try {
            return mc.player.getHungerManager().getFoodLevel();
        } catch (Throwable ignored) {
            return 20;
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (++ticks < checkDelay.getValue().intValue()) return;
        ticks = 0;
        try {
            int f = food();
            if (f <= warnAt.getValue().intValue() && warn.isEnabled() && !warned) {
                warned = true;
                if (mc.inGameHud != null) {
                    mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(
                            "§c[Hunger] §fFood low (" + f + ") — eat something!"));
                }
            } else if (f > warnAt.getValue().intValue()) {
                warned = false;
            }
            if (f <= minFood.getValue().intValue()) {
                if (disableSprint.isEnabled() && mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                }
                if (disableJump.isEnabled()) {
                    mc.options.jumpKey.setPressed(false);
                }
            }
        } catch (Throwable ignored) {}
    }
}
