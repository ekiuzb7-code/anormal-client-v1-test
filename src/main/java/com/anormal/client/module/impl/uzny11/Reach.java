package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;

public class Reach extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Reach distance in blocks", 6.0, 3.0, 20.0, 0.1);
    public final BooleanSetting sprintOnly = new BooleanSetting("Sprint Only", "Only applies while sprinting", false);
    public final BooleanSetting weaponOnly = new BooleanSetting("Weapon Only", "Only with sword/axe", false);

    public Reach() {
        super("Reach", "Extends attack reach distance", Category.UZNY11);
        addSetting(range);
        addSetting(sprintOnly);
        addSetting(weaponOnly);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (sprintOnly.isEnabled() && !mc.player.isSprinting()) return;
        if (weaponOnly.isEnabled() && !isWeapon()) return;

        if (mc.options.attackKey.isPressed()) {
            double reachDist = range.getValue();
            for (var entity : mc.world.getEntities()) {
                if (!(entity instanceof LivingEntity target)) continue;
                if (target == mc.player || !target.isAlive()) continue;
                double dist = mc.player.distanceTo(target);
                if (dist > 3.0 && dist <= reachDist) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    break;
                }
            }
        }
    }

    private boolean isWeapon() {
        var stack = mc.player.getMainHandStack();
        return stack.getItem() instanceof net.minecraft.item.SwordItem ||
               stack.getItem() instanceof net.minecraft.item.AxeItem ||
               stack.getItem() instanceof net.minecraft.item.MaceItem;
    }
}