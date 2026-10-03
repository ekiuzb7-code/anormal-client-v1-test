package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.util.math.MathHelper;

public class LongJump extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "LongJump mode", "Normal", "Normal", "BHop", "Flat");
    public final NumberSetting boost = new NumberSetting("Boost", "Jump boost", 1.5, 1.0, 3.0, 0.1);
    public final BooleanSetting autoJump = new BooleanSetting("Auto Jump", "Auto jump at edge", true);

    public LongJump() {
        super("LongJump", "Increases jump distance", Category.UZNY11);
        addSetting(mode);
        addSetting(boost);
        addSetting(autoJump);
    }

    @Override
    public void onTick() {
        if (mc.player == null || !mc.player.isOnGround()) return;

        if (mc.options.forwardKey.isPressed() && mc.options.jumpKey.isPressed()) {
            mc.player.jump();
            float yaw = mc.player.getYaw();
            double b = boost.getValue();
            mc.player.setVelocity(
                -MathHelper.sin(mc.player.getYaw() * MathHelper.RADIANS_PER_DEGREE) * b * 0.5,
                mc.player.getVelocity().y,
                MathHelper.cos(mc.player.getYaw() * MathHelper.RADIANS_PER_DEGREE) * b * 0.5
            );
        }
    }
}