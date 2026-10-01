package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;

public class ESP extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "ESP visual mode", "Glow", "2D Box", "3D Box", "Glow", "Corner");
    public final BooleanSetting players = new BooleanSetting("Players", "Highlight other players", true);
    public final BooleanSetting mobs = new BooleanSetting("Monsters", "Highlight hostile mobs", false);
    public final BooleanSetting animals = new BooleanSetting("Animals", "Highlight passive animals", false);
    public final BooleanSetting items = new BooleanSetting("Items", "Highlight dropped items", false);
    public final BooleanSetting healthBar = new BooleanSetting("Health Bar", "Show health indicators", true);
    public final ColorSetting color = new ColorSetting("Color", "ESP highlight color", ColorUtils.rgba(255, 60, 60, 255));

    public ESP() {
        super("ESP", "Highlights players, mobs, and items through walls with customizable styles", Category.RENDER);
        addSetting(mode);
        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
        addSetting(items);
        addSetting(healthBar);
        addSetting(color);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;

            boolean shouldHighlight = false;
            if (entity instanceof PlayerEntity && players.isEnabled()) shouldHighlight = true;
            else if (entity instanceof Monster && mobs.isEnabled()) shouldHighlight = true;
            else if (entity instanceof AnimalEntity && animals.isEnabled()) shouldHighlight = true;
            else if (entity instanceof ItemEntity && items.isEnabled()) shouldHighlight = true;

            entity.setGlowing(shouldHighlight);
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
