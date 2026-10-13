package com.anormal.client.module.impl.movement;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/**
 * Stops the server from draining your hunger.
 *
 * <p>Two interchangeable implementations, each mirroring a different client:</p>
 * <ul>
 *   <li><b>V1 - Wurst.</b> Wurst's AntiHunger has <i>no settings at all</i>.
 *       It rewrites every outgoing {@code ServerboundMovePlayerPacket} so the
 *       {@code onGround} flag is {@code false} whenever you are standing on the
 *       ground (and not falling and not mining). The server therefore never
 *       applies the "walked on the ground" exhaustion, which is the only
 *       exhaustion source it can attribute to you.</li>
 *   <li><b>V2 - LiquidBounce.</b> LiquidBounce exposes "WhileSwimming"
 *       (default off) and "KeepFloating" (default on). It suppresses the
 *       sprint/on-ground state that would drain hunger while swimming or
 *       floating.</li>
 * </ul>
 */
public class AntiHunger extends Module {
    public final ModeSetting version = new ModeSetting("Version", "Which implementation to use", "V1", "V1", "V2");

    // ---- V2 - LiquidBounce ----
    public final BooleanSetting whileSwimming = new BooleanSetting("WhileSwimming", "Also apply while swimming", false);
    public final BooleanSetting keepFloating = new BooleanSetting("KeepFloating", "Keep the floating state hidden from the server", true);

    public AntiHunger() {
        super("AntiHunger", "Prevents the server from draining your hunger", Category.MOVEMENT);
        addSetting(version);
        addSetting(whileSwimming);
        addSetting(keepFloating);

        whileSwimming.visibleIf(() -> version.is("V2"));
        keepFloating.visibleIf(() -> version.is("V2"));
    }

    /**
     * Wurst's condition: only spoof while grounded, not falling, and not
     * currently destroying a block.
     */
    public boolean shouldSpoofOnGround() {
        if (!isEnabled() || mc.player == null) return false;
        try {
            if (!mc.player.isOnGround()) return false;
            if (mc.player.fallDistance > 0.5) return false;
            if (mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) return false;
            if (version.is("V2")) {
                if (mc.player.isTouchingWater() && !whileSwimming.isEnabled()) return false;
                if (mc.player.isSwimming() && !whileSwimming.isEnabled()) return false;
                if (!keepFloating.isEnabled() && mc.player.isSwimming()) return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Rebuilds a movement packet with {@code onGround = false}, mirroring
     * Wurst's {@code PacketUtils.modifyOnGround}.
     *
     * @return the replacement packet, or {@code null} when the packet is
     *         already correct or of an unknown type.
     */
    public static PlayerMoveC2SPacket spoofOnGround(PlayerMoveC2SPacket packet) {
        try {
            if (packet.isOnGround()) {
                if (packet instanceof PlayerMoveC2SPacket.OnGroundOnly p) {
                    return new PlayerMoveC2SPacket.OnGroundOnly(false, p.horizontalCollision());
                }
                if (packet instanceof PlayerMoveC2SPacket.LookAndOnGround p) {
                    return new PlayerMoveC2SPacket.LookAndOnGround(
                            p.getYaw(0.0f), p.getPitch(0.0f), false, p.horizontalCollision());
                }
                if (packet instanceof PlayerMoveC2SPacket.PositionAndOnGround p) {
                    return new PlayerMoveC2SPacket.PositionAndOnGround(
                            p.getX(0.0), p.getY(0.0), p.getZ(0.0), false, p.horizontalCollision());
                }
                if (packet instanceof PlayerMoveC2SPacket.Full p) {
                    return new PlayerMoveC2SPacket.Full(
                            p.getX(0.0), p.getY(0.0), p.getZ(0.0),
                            p.getYaw(0.0f), p.getPitch(0.0f), false, p.horizontalCollision());
                }
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
