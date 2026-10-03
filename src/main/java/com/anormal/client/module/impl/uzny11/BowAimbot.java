package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

public class BowAimbot extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Target range", 50.0, 5.0, 100.0, 1.0);
    public final NumberSetting fov = new NumberSetting("FOV", "Field of view", 30.0, 5.0, 180.0, 1.0);
    public final NumberSetting speed = new NumberSetting("Speed", "Aim speed", 20.0, 1.0, 100.0, 1.0);
    public final BooleanSetting charge = new BooleanSetting("Auto Charge", "Auto charge bow", true);
    public final BooleanSetting players = new BooleanSetting("Players", "Target players", true);

    public BowAimbot() {
        super("BowAimbot", "Automatically aims bow at targets", Category.UZNY11);
        addSetting(range);
        addSetting(fov);
        addSetting(speed);
        addSetting(charge);
        addSetting(players);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.getMainHandStack().isOf(net.minecraft.item.Items.BOW)) return;

        // Find target and aim
    }
}