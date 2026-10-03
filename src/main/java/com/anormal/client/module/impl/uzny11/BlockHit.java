package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public class BlockHit extends Module {
    public final NumberSetting chance = new NumberSetting("Chance", "Blockhit chance %", 100.0, 10.0, 100.0, 5.0);
    public final ModeSetting mode = new ModeSetting("Mode", "BlockHit mode", "Manual", "Manual", "Auto", "Predict");

    public BlockHit() {
        super("BlockHit", "Automatically block hits when holding a sword", Category.UZNY11);
        addSetting(chance);
        addSetting(mode);
    }

    public void onAttack() {
        if (!isEnabled() || mc.player == null) return;
        // ID-based: SwordItem class no longer exists in 1.21.11
        Identifier id = Registries.ITEM.getId(mc.player.getMainHandStack().getItem());
        if (id != null && id.getPath().endsWith("_sword")) {
            if (Math.random() * 100.0 <= chance.getValue()) {
                mc.player.swingHand(Hand.OFF_HAND);
            }
        }
    }
}
