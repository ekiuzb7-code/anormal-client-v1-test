package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.SwordItem;
import net.minecraft.util.Hand;

public class BlockHit extends Module {
    public final NumberSetting chance = new NumberSetting("Chance", "Blockhit chance %", 100.0, 10.0, 100.0, 5.0);
    public final ModeSetting mode = new ModeSetting("Mode", "BlockHit mode", "Manual", "Manual", "Auto", "Predict");

    public BlockHit() {
        super("BlockHit", "Automatically block hits when holding a sword", Category.COMBAT);
        addSetting(chance);
        addSetting(mode);
    }

    public void onAttack() {
        if (!isEnabled() || mc.player == null) return;
        if (mc.player.getMainHandStack().getItem() instanceof SwordItem) {
            if (Math.random() * 100.0 <= chance.getValue()) {
                mc.player.swingHand(Hand.OFF_HAND);
            }
        }
    }
}
