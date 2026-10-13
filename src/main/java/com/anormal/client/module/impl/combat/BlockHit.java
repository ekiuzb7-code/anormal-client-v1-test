package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.ItemStack;

/**
 * Ported from Vape V4's BlockHit module.
 *
 * <p>Vape's BlockHit is item-gated: its only value is an "Allowed items" list
 * containing swords, food and potions. This port mirrors that with the same
 * three item groups as individual toggles, plus the behaviour settings Vape
 * exposes on its block-hit path (when to block, and how long to hold).</p>
 */
public class BlockHit extends Module {
    // Vape: LimitValue("Allowed items") = [swords, food, potions]
    public final BooleanSetting allowSwords = new BooleanSetting("Swords", "Allow block-hit while holding a sword", true);
    public final BooleanSetting allowFood = new BooleanSetting("Food", "Allow block-hit while holding food", true);
    public final BooleanSetting allowPotions = new BooleanSetting("Potions", "Allow block-hit while holding a potion", true);

    public final ModeSetting mode = new ModeSetting("Mode", "When to block", "After Attack", "After Attack", "Always", "In Combat");
    public final NumberSetting blockTicks = new NumberSetting("Block Ticks", "How long to hold the block after attacking", 2.0, 1.0, 10.0, 1.0);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks to wait after the attack before blocking", 0.0, 0.0, 10.0, 1.0);
    public final BooleanSetting onlyWhileSprinting = new BooleanSetting("Only While Sprinting", "Only block-hit while sprinting", false);

    private int blockTimer = 0;
    private int delayTimer = 0;

    public BlockHit() {
        super("BlockHit", "Automatically blocks with your sword after attacking", Category.COMBAT);
        addSetting(allowSwords);
        addSetting(allowFood);
        addSetting(allowPotions);
        addSetting(mode);
        addSetting(blockTicks);
        addSetting(delay);
        addSetting(onlyWhileSprinting);
    }

    @Override
    public void onDisable() {
        blockTimer = 0;
        delayTimer = 0;
        try {
            if (mc.options != null) mc.options.useKey.setPressed(false);
        } catch (Throwable ignored) {
        }
    }

    /** Called by the attack path right after a hit lands. */
    public void onAttack() {
        if (!isEnabled()) return;
        if (mode.is("After Attack") || mode.is("In Combat")) {
            delayTimer = delay.getValue().intValue();
            blockTimer = blockTicks.getValue().intValue();
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.options == null) return;

        if (!isAllowedItem()) {
            release();
            return;
        }
        if (onlyWhileSprinting.isEnabled() && !mc.player.isSprinting()) {
            release();
            return;
        }

        if (mode.is("Always")) {
            try {
                mc.options.useKey.setPressed(true);
            } catch (Throwable ignored) {
            }
            return;
        }

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }
        if (blockTimer > 0) {
            blockTimer--;
            try {
                mc.options.useKey.setPressed(true);
            } catch (Throwable ignored) {
            }
            return;
        }
        release();
    }

    private void release() {
        try {
            mc.options.useKey.setPressed(false);
        } catch (Throwable ignored) {
        }
    }

    /** Vape's "Allowed items" gate. */
    private boolean isAllowedItem() {
        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) return false;
        try {
            String path0 = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
            if (allowSwords.isEnabled() && path0.endsWith("_sword")) return true;
            String path = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
            if (allowFood.isEnabled() && (path.contains("apple") || path.contains("bread")
                    || path.contains("beef") || path.contains("porkchop") || path.contains("chicken")
                    || path.contains("mutton") || path.contains("cod") || path.contains("salmon")
                    || path.contains("carrot") || path.contains("potato") || path.contains("stew")
                    || path.contains("cookie") || path.contains("melon") || path.contains("berry"))) {
                return true;
            }
            if (allowPotions.isEnabled() && path.contains("potion")) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }
}
