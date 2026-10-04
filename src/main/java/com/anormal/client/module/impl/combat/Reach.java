package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;

import com.anormal.client.setting.BooleanSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public class Reach extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Reach distance in blocks", 3.5, 3.0, 6.0, 0.1);
    public final BooleanSetting sprintOnly = new BooleanSetting("Sprint Only", "Only applies reach while sprinting", false);
    public final BooleanSetting weaponOnly = new BooleanSetting("Weapon Only", "Only applies reach when holding sword/axe", false);

    public Reach() {
        super("Reach", "Extends attack reach distance towards targets", Category.COMBAT);
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
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof LivingEntity target && entity != mc.player && target.isAlive()) {
                    double dist = mc.player.distanceTo(target);
                    if (dist > 3.0 && dist <= reachDist) {
                        mc.interactionManager.attackEntity(mc.player, target);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        break;
                    }
                }
            }
        }
    }

    private boolean isWeapon() {
        Identifier id = Registries.ITEM.getId(mc.player.getMainHandStack().getItem());
        if (id == null) return false;
        String path = id.getPath();
        return path.endsWith("_sword") || path.endsWith("_axe")
                || path.equals("mace") || path.equals("trident");
    }
}
