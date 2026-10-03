package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;

public class AutoEat extends Module {
    public final NumberSetting hungerAt = new NumberSetting("Hunger At", "Start eating below this", 14.0, 1.0, 20.0, 1.0);
    public final NumberSetting stopAt = new NumberSetting("Stop At", "Stop eating above this", 18.0, 1.0, 20.0, 1.0);
    public final ModeSetting priority = new ModeSetting("Priority", "Which food first", "Golden First", "Golden First", "Most Stacks", "First Found");
    public final BooleanSetting goldenPriority = new BooleanSetting("Golden Priority", "Always prefer golden foods", true);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between food checks", 10.0, 1.0, 60.0, 1.0);
    public final BooleanSetting switchBack = new BooleanSetting("Switch Back", "Restore slot after eating", true);
    public final BooleanSetting pauseInCombat = new BooleanSetting("Pause In Combat", "Don't eat while hurt recently", true);
    public final BooleanSetting useOffhandToo = new BooleanSetting("Offhand Too", "Also eat from offhand", true);
    public final BooleanSetting blacklistRotten = new BooleanSetting("No Rotten", "Never eat rotten flesh", true);

    private int timer = 0;
    private int savedSlot = -1;
    private boolean eating = false;
    private int eatTimeout = 0;

    public AutoEat() {
        super("AutoEat", "Eats food automatically when hungry", Category.PLAYER);
        addSetting(hungerAt);
        addSetting(stopAt);
        addSetting(priority);
        addSetting(goldenPriority);
        addSetting(delay);
        addSetting(switchBack);
        addSetting(pauseInCombat);
        addSetting(useOffhandToo);
        addSetting(blacklistRotten);
    }

    private int food() {
        try {
            return mc.player.getHungerManager().getFoodLevel();
        } catch (Throwable ignored) {
            return 20;
        }
    }

    private boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            if (blacklistRotten.isEnabled()) {
                String path = Registries.ITEM.getId(stack.getItem()).getPath();
                if (path.contains("rotten_flesh") || path.contains("poisonous_potato")
                        || path.contains("pufferfish") || path.contains("spider_eye")) return false;
            }
            return stack.get(DataComponentTypes.FOOD) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isGolden(ItemStack stack) {
        try {
            String path = Registries.ITEM.getId(stack.getItem()).getPath();
            return path.contains("golden_");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int findFood() {
        int best = -1;
        int bestCount = -1;
        try {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!isFood(stack)) continue;
                if (priority.is("Golden First") || goldenPriority.isEnabled()) {
                    if (isGolden(stack)) return i;
                }
                if (priority.is("Most Stacks")) {
                    if (stack.getCount() > bestCount) {
                        bestCount = stack.getCount();
                        best = i;
                    }
                } else if (best == -1) {
                    best = i;
                }
            }
        } catch (Throwable ignored) {}
        return best;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (pauseInCombat.isEnabled() && mc.player.hurtTime > 0) {
            stopEating();
            return;
        }
        if (eating) {
            if (food() >= stopAt.getValue().intValue() || --eatTimeout <= 0) {
                stopEating();
            } else {
                mc.options.useKey.setPressed(true);
            }
            if (++timer < delay.getValue().intValue()) return;
            timer = 0;
            return;
        }
        if (++timer < delay.getValue().intValue()) return;
        timer = 0;
        if (food() > hungerAt.getValue().intValue()) return;

        // Offhand first if allowed
        if (useOffhandToo.isEnabled()) {
            try {
                if (isFood(mc.player.getOffHandStack())) {
                    savedSlot = -2;
                    eating = true;
                    eatTimeout = 100;
                    mc.options.useKey.setPressed(true);
                    return;
                }
            } catch (Throwable ignored) {}
        }
        int slot = findFood();
        if (slot == -1) return;
        try {
            savedSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(slot);
            eating = true;
            eatTimeout = 100;
            mc.options.useKey.setPressed(true);
        } catch (Throwable ignored) {
            stopEating();
        }
    }

    private void stopEating() {
        try {
            mc.options.useKey.setPressed(false);
            if (switchBack.isEnabled() && savedSlot >= 0) {
                mc.player.getInventory().setSelectedSlot(savedSlot);
            }
        } catch (Throwable ignored) {}
        eating = false;
        savedSlot = -1;
    }

    @Override
    public void onDisable() {
        stopEating();
    }
}
