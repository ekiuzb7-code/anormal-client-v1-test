package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

public class ESP extends Module {
    public final BooleanSetting playersOnly = new BooleanSetting("Players Only", "Only outline other players", false);
    public final ColorSetting color = new ColorSetting("Color", "ESP box color", ColorUtils.rgba(255, 60, 60, 255));

    public ESP() {
        super("ESP", "Highlights players and entities through walls with glowing outlines", Category.RENDER);
        addSetting(playersOnly);
        addSetting(color);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity && entity != mc.player) {
                if (playersOnly.isEnabled() && !(entity instanceof PlayerEntity)) {
                    entity.setGlowing(false);
                    continue;
                }
                entity.setGlowing(true);
            }
        }
    }

    @Override
    public void onDisable() {
        if (mc.world == null) return;
        for (Entity entity : mc.world.getEntities()) {
            entity.setGlowing(false);
        }
    }
}
