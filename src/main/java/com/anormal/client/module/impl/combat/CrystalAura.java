package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.Hand;

public class CrystalAura extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Placement and break mode", "Auto", "Auto", "Manual");
    public final NumberSetting range = new NumberSetting("Range", "Break range in blocks", 4.5, 2.0, 6.0, 0.5);
    public final BooleanSetting antiSuicide = new BooleanSetting("Anti-Suicide", "Prevents lethal self-damage", true);

    public CrystalAura() {
        super("CrystalAura", "Automatically places and detonates End Crystals for explosion damage", Category.COMBAT);
        addSetting(mode);
        addSetting(range);
        addSetting(antiSuicide);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity crystal && crystal.isAlive()) {
                if (mc.player.distanceTo(crystal) <= range.getValue()) {
                    mc.interactionManager.attackEntity(mc.player, crystal);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    break;
                }
            }
        }
    }
}
