package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.combat.FakeLagV2;
import com.anormal.client.module.impl.world.Freecam;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks every outgoing packet.
 *
 * <p><b>Fix:</b> this used to target {@code ClientPlayNetworkHandler.sendPacket},
 * but that method is only <i>declared</i> on the parent class
 * {@link ClientCommonNetworkHandler}. Mixin could not find it in the target class,
 * so with {@code require = 0} the injection was silently skipped and FakeLagV2
 * never actually held a single packet. Targeting the declaring class fixes it.</p>
 */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class NetworkMixin {

    /** Guards the re-send below so the rewritten packet does not loop. */
    private static boolean anormal$reSending = false;

    @Shadow
    public abstract void sendPacket(Packet<?> packet);

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true, require = 0)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        try {
            // AntiHunger (Wurst port): rewrite the onGround flag before anything
            // else sees the packet, exactly like Wurst's PacketUtils does.
            if (packet instanceof PlayerMoveC2SPacket move) {
                com.anormal.client.module.impl.movement.AntiHunger antiHunger =
                        ModuleManager.getModule(com.anormal.client.module.impl.movement.AntiHunger.class);
                if (antiHunger != null && antiHunger.shouldSpoofOnGround()) {
                    PlayerMoveC2SPacket replaced =
                            com.anormal.client.module.impl.movement.AntiHunger.spoofOnGround(move);
                    if (replaced != null) {
                        ci.cancel();
                        if (!anormal$reSending) {
                            anormal$reSending = true;
                            try {
                                sendPacket(replaced);
                            } finally {
                                anormal$reSending = false;
                            }
                        }
                        return;
                    }
                }
            }

            // FakeLagV2 decides for itself which packets to hold.
            FakeLagV2 fakeLagV2 = ModuleManager.getModule(FakeLagV2.class);
            if (fakeLagV2 != null && fakeLagV2.isEnabled()) {
                if (fakeLagV2.shouldQueuePacket(packet)) {
                    FakeLagV2.getBlinkManager().queueOutgoing(packet);
                    ci.cancel();
                    return;
                }
            }

            // Freecam: freeze the body on the server by dropping movement packets.
            if (!(packet instanceof PlayerMoveC2SPacket)) return;

            Freecam freecam = ModuleManager.getModule(Freecam.class);
            if (freecam != null && freecam.isEnabled() && freecam.isCameraActive()) {
                ci.cancel();
                return;
            }

            // Legacy FakeLag (kept for compatibility)
            if (com.anormal.client.module.impl.world.FakeLag.isFlushing()) return;
            com.anormal.client.module.impl.world.FakeLag fakeLag =
                    ModuleManager.getModule(com.anormal.client.module.impl.world.FakeLag.class);
            if (fakeLag != null && fakeLag.isEnabled() && fakeLag.isHolding()) {
                com.anormal.client.module.impl.world.FakeLag.queue(packet);
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}
