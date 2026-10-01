package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.item.FishingRodItem;
import net.minecraft.util.Hand;

public class AutoFish extends Module {
    public AutoFish() {
        super("AutoFish", "Automatically catches and recasts fishing line when a fish bites", Category.WORLD);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.player.fishHook != null && mc.player.fishHook.getHookedEntity() != null) {
            if (mc.player.getMainHandStack().getItem() instanceof FishingRodItem) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
        }
    }
}
