package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class NoFall extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Catch slows fall, Spoof tells server grounded", "Catch", "Catch", "Spoof");
    public final NumberSetting catchSpeed = new NumberSetting("Catch Speed", "Max fall speed in Catch", 0.5, 0.1, 2.0, 0.1);

    public NoFall() {
        super("NoFall", "Prevents fall damage", Category.MOVEMENT);
        addSetting(mode);
        addSetting(catchSpeed);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.player.isOnGround() || mc.player.isGliding()) return;
        try {
            if (mode.is("Spoof")) {
                // Tell the server we're grounded every tick while airborne:
                // fall distance never accumulates server-side.
                Vec3d p = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(
                            new PlayerMoveC2SPacket.PositionAndOnGround(p, true, false));
                }
                mc.player.fallDistance = 0.0f;
            } else {
                // Catch: clamp descent + wipe accumulated distance before landing.
                Vec3d v = mc.player.getVelocity();
                double maxFall = -Math.abs(catchSpeed.getValue());
                if (v.y < maxFall) {
                    mc.player.setVelocity(v.x, maxFall, v.z);
                }
                mc.player.fallDistance = 0.0f;
            }
        } catch (Throwable ignored) {}
    }
}
