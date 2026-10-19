package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Fall damage prevention, ported from Wurst's NoFall.
 *
 * <p>Instead of editing the fall distance locally (which the server never sees) it sends a
 * "status only" movement packet claiming {@code onGround = true}. The server resets its own fall
 * distance for that tick, so the landing produces no damage while nothing about the movement is
 * faked beyond the ground flag.</p>
 *
 * <p>The module deliberately pauses in creative flight, while gliding (unless allowed), while
 * holding a mace (so smash damage still applies) and on falls too short to hurt.</p>
 */
public class NoFall extends Module {

    public final BooleanSetting allowElytra = new BooleanSetting("Allow Elytra",
            "Keep NoFall active while gliding with an elytra", false);
    public final BooleanSetting pauseForMace = new BooleanSetting("Pause for Mace",
            "Pause while holding a mace so smash attacks still deal their fall bonus", false);
    public final NumberSetting minFall = new NumberSetting("Min Fall Distance",
            "Ignore falls shorter than this (0 = always active)", 1.0, 0.0, 10.0, 0.1);
    public final NumberSetting minElytraFall = new NumberSetting("Min Elytra Fall Distance",
            "Same threshold while gliding", 2.0, 0.0, 10.0, 0.1);

    public NoFall() {
        super("NoFall", "Prevents fall damage by reporting you as grounded", Category.MOVEMENT);
        addSetting(allowElytra);
        addSetting(pauseForMace);
        addSetting(minFall);
        addSetting(minElytraFall);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (isPaused()) return;

        try {
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
        } catch (Throwable ignored) {
        }
    }

    private boolean isPaused() {
        // Creative / spectator cannot take fall damage at all.
        if (mc.player.getAbilities().invulnerable) return true;

        boolean gliding = mc.player.isGliding();
        if (gliding && !allowElytra.isEnabled()) return true;

        if (pauseForMace.isEnabled() && mc.player.getMainHandStack().getItem() == Items.MACE) return true;

        float threshold = gliding
                ? minElytraFall.getValue().floatValue()
                : minFall.getValue().floatValue();
        if (mc.player.fallDistance <= threshold) return true;

        // While gliding, sneaking drops you out of flight; only intervene when the fall is
        // actually fast enough to hurt.
        if (gliding && mc.player.isSneaking() && mc.player.getVelocity().y >= -0.5) return true;

        return false;
    }
}
