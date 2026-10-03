package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;

import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

public class FastPlace extends Module {
    public final NumberSetting delay = new NumberSetting("Delay", "Vanilla use cooldown override", 1.0, 0.0, 4.0, 1.0);
    public final BooleanSetting blocksOnly = new BooleanSetting("Blocks Only", "Only fast-place blocks", true);

    public FastPlace() {
        super("FastPlace", "Speeds up vanilla placement, never places in air", Category.PLAYER);
        addSetting(delay);
        addSetting(blocksOnly);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (!mc.options.useKey.isPressed()) return;
        ItemStack held = mc.player.getMainHandStack();
        if (blocksOnly.isEnabled() && !(held.getItem() instanceof BlockItem)) return;

        // Let VANILLA place the block (same validation, same faces, never air),
        // only shrink its own cooldown so it fires faster.
        try {
            int want = delay.getValue().intValue();
            com.anormal.client.mixin.ClientAccessor acc = (com.anormal.client.mixin.ClientAccessor) mc;
            if (acc.getItemUseCooldown() > want) acc.setItemUseCooldown(want);
        } catch (Throwable ignored) {}
    }
}
