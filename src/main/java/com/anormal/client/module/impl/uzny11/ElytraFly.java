package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class ElytraFly extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Vanilla glide control or rocket Boost", "Vanilla", "Vanilla", "Boost");
    public final NumberSetting hSpeed = new NumberSetting("H-Speed", "Horizontal glide speed", 1.2, 0.2, 3.0, 0.1);
    public final NumberSetting vSpeed = new NumberSetting("V-Speed", "Vertical glide speed", 0.8, 0.1, 2.0, 0.1);
    public final NumberSetting rocketDelay = new NumberSetting("Rocket Delay", "Ticks between fireworks (Boost)", 20.0, 5.0, 100.0, 5.0);

    private int rocketTicks = 0;

    public ElytraFly() {
        super("ElytraFly", "Enhanced elytra gliding with speed control", Category.UZNY11);
        addSetting(mode);
        addSetting(hSpeed);
        addSetting(vSpeed);
        addSetting(rocketDelay);
    }

    private boolean hasElytra() {
        try {
            String path = Registries.ITEM.getId(mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem()).getPath();
            return path.contains("elytra");
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!hasElytra() || !mc.player.isGliding()) {
            rocketTicks = 0;
            return;
        }
        try {
            if (mode.is("Boost")) {
                if (++rocketTicks < rocketDelay.getValue().intValue()) return;
                rocketTicks = 0;
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                return;
            }
            // Vanilla: strafe glide + vertical control
            double h = hSpeed.getValue();
            double rad = Math.toRadians(mc.player.getYaw());
            double mx = 0.0, mz = 0.0;
            if (mc.options.forwardKey.isPressed()) {
                mx += -Math.sin(rad) * h;
                mz += Math.cos(rad) * h;
            }
            if (mc.options.backKey.isPressed()) {
                mx -= -Math.sin(rad) * h;
                mz -= Math.cos(rad) * h;
            }
            Vec3d v = mc.player.getVelocity();
            double my = v.y;
            if (mc.options.jumpKey.isPressed()) my = vSpeed.getValue();
            else if (mc.options.sneakKey.isPressed()) my = -vSpeed.getValue();
            mc.player.setVelocity(mx != 0.0 || mz != 0.0 ? mx : v.x * 0.98, my, mx != 0.0 || mz != 0.0 ? mz : v.z * 0.98);
        } catch (Throwable ignored) {}
    }
}
