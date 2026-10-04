package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;

public class Triggerbot extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Trigger range", 4.5, 2.0, 6.0, 0.1);
    public final NumberSetting delay = new NumberSetting("Delay", "Delay after attack (ticks)", 0, 0, 20, 1);
    public final BooleanSetting players = new BooleanSetting("Players", "Trigger on players", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", "Trigger on mobs", false);
    public final BooleanSetting swordOnly = new BooleanSetting("Sword Only", "Only with sword", true);

    private int delayTicks = 0;

    public Triggerbot() {
        super("Triggerbot", "Automatically attacks when crosshair on entity", Category.UZNY11);
        addSetting(range);
        addSetting(delay);
        addSetting(players);
        addSetting(mobs);
        addSetting(swordOnly);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (mc.targetedEntity == null || !(mc.targetedEntity instanceof LivingEntity)) return;
        if (swordOnly.isEnabled() && !isHoldingSword()) return;

        LivingEntity target = (LivingEntity) mc.targetedEntity;
        if (target == mc.player || !target.isAlive()) return;

        if (target instanceof net.minecraft.entity.player.PlayerEntity p) {
            if (!players.isEnabled()) return;
        } else {
            if (!mobs.isEnabled()) return;
        }

        double dist = mc.player.distanceTo(target);
        if (dist > range.getValue()) return;

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);

        if (delay.getValue() > 0) delayTicks = delay.getValue().intValue();
    }

    private boolean isHoldingSword() {
        Item item = mc.player.getMainHandStack().getItem();
        return item.isIn(ItemTags.SWORDS) || item.isIn(ItemTags.AXES) || item == Items.MACE;
    }
}