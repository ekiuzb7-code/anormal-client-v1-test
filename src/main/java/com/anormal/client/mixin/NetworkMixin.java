package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.combat.FakeLagV2;
import com.anormal.client.module.impl.combat.BackTrackV2;
import com.anormal.client.module.impl.world.Freecam;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class NetworkMixin {

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true, require = 0)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        try {
            // Hold movement packets only: attacks/interacts flush through
            // so you keep hitting while the server sees your old position.
            if (!(packet instanceof PlayerMoveC2SPacket)) return;

            // Freecam: cancel ALL movement packets while active (body is frozen on server)
            Freecam freecam = ModuleManager.getModule(Freecam.class);
            if (freecam != null && freecam.isEnabled() && freecam.isCameraActive()) {
                ci.cancel();
                return;
            }

            // FakeLagV2: queue outgoing movement packets
            FakeLagV2 fakeLagV2 = ModuleManager.getModule(FakeLagV2.class);
            if (fakeLagV2 != null && fakeLagV2.isEnabled() && fakeLagV2.shouldQueuePacket(packet)) {
                FakeLagV2.getBlinkManager().queueOutgoing(packet);
                ci.cancel();
                return;
            }

            // Original FakeLag (keep for compatibility)
            if (com.anormal.client.module.impl.world.FakeLag.isFlushing()) return;
            com.anormal.client.module.impl.world.FakeLag fakeLag = ModuleManager.getModule(com.anormal.client.module.impl.world.FakeLag.class);
            if (fakeLag != null && fakeLag.isEnabled() && fakeLag.isHolding()) {
                com.anormal.client.module.impl.world.FakeLag.queue(packet);
                ci.cancel();
                return;
            }
        } catch (Throwable ignored) {}
    }

    @Inject(method = "onPacketReceived", at = @At("HEAD"), cancellable = true, require = 0)
    private void onPacketReceived(net.minecraft.network.packet.Packet<?> packet, CallbackInfo ci) {
        try {
            // BackTrackV2: queue incoming packets
            BackTrackV2 backTrackV2 = ModuleManager.getModule(BackTrackV2.class);
            if (backTrackV2 != null && backTrackV2.isEnabled()) {
                if (backTrackV2.shouldQueueIncomingPacket(packet)) {
                    BackTrackV2.getBlinkManager().queueIncoming(packet);
                    ci.cancel();
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }
}
