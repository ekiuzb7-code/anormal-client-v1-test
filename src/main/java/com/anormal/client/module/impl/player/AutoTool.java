package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Ported from Wurst's AutoToolHack.
 *
 * <p>Wurst's settings, verbatim:</p>
 * <ul>
 *   <li>"Use swords" - use swords to break leaves, cobwebs, etc. (default off)</li>
 *   <li>"Use hands" - fall back to an empty hand / non-damageable item when no
 *       applicable tool is found (default on)</li>
 *   <li>"Repair mode" - stop using a tool once its remaining durability drops
 *       to this value, so you can repair it before it breaks (0 = off)</li>
 *   <li>"Switch back" - return to the previously selected slot afterwards</li>
 * </ul>
 *
 * <p>Behaviour matches Wurst: it only swaps while a block is actually being
 * broken, it never touches your slot if the current one is already the best,
 * and it restores the previous slot when you stop mining.</p>
 */
public class AutoTool extends Module {
    public final BooleanSetting useSwords = new BooleanSetting("Use swords", "Uses swords to break leaves, cobwebs, etc.", false);
    public final BooleanSetting useHands = new BooleanSetting("Use hands", "Uses an empty hand or a non-damageable item when no applicable tool is found", true);
    public final NumberSetting repairMode = new NumberSetting("Repair mode", "Prevents tools from being used when their durability reaches this value (0 = off)", 0.0, 0.0, 100.0, 1.0);
    public final BooleanSetting switchBack = new BooleanSetting("Switch back", "After using a tool, automatically switch back to the previously selected slot", false);

    private int prevSelectedSlot = -1;

    public AutoTool() {
        super("AutoTool", "Automatically selects the best tool for the block you are breaking", Category.PLAYER);
        addSetting(useSwords);
        addSetting(useHands);
        addSetting(repairMode);
        addSetting(switchBack);
    }

    @Override
    public void onEnable() {
        prevSelectedSlot = -1;
    }

    @Override
    public void onDisable() {
        prevSelectedSlot = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // Wurst only acts while a block is actually being broken.
        if (!mc.options.attackKey.isPressed()
                || !(mc.crosshairTarget instanceof BlockHitResult bhr)
                || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            if (switchBack.isEnabled()) restore();
            return;
        }

        BlockState state = mc.world.getBlockState(bhr.getBlockPos());
        if (state.isAir()) {
            if (switchBack.isEnabled()) restore();
            return;
        }

        int best = findBestSlot(state);
        if (best == -1) {
            if (switchBack.isEnabled()) restore();
            return;
        }

        int current = mc.player.getInventory().getSelectedSlot();
        if (best == current) return;

        if (prevSelectedSlot == -1) prevSelectedSlot = current;
        try {
            mc.player.getInventory().setSelectedSlot(best);
        } catch (Throwable ignored) {
        }
    }

    private void restore() {
        if (prevSelectedSlot == -1 || mc.player == null) return;
        try {
            mc.player.getInventory().setSelectedSlot(prevSelectedSlot);
        } catch (Throwable ignored) {
        }
        prevSelectedSlot = -1;
    }

    /** @return the hotbar slot with the highest mining speed for {@code state}, or -1. */
    private int findBestSlot(BlockState state) {
        int bestSlot = -1;
        float bestSpeed = 1.0f; // bare hand speed

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (!isAllowed(stack)) continue;
            if (isBroken(stack)) continue;

            float speed;
            try {
                speed = stack.getMiningSpeedMultiplier(state);
            } catch (Throwable t) {
                speed = 1.0f;
            }
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        // "Use hands": only fall back to a non-damageable slot when nothing
        // useful was found and the option is on.
        if (bestSlot == -1 && useHands.isEnabled()) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.isDamageable()) continue;
                bestSlot = i;
                break;
            }
        }
        return bestSlot;
    }

    private boolean isAllowed(ItemStack stack) {
        try {
            String path = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
            if (path.endsWith("_sword") && !useSwords.isEnabled()) return false;
            return true;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Wurst's repair mode: skip tools whose remaining durability is at/below the threshold. */
    private boolean isBroken(ItemStack stack) {
        double threshold = repairMode.getValue();
        if (threshold <= 0) return false;
        try {
            if (!stack.isDamageable()) return false;
            int remaining = stack.getMaxDamage() - stack.getDamage();
            return remaining <= threshold;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
