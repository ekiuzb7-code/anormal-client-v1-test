package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.uzny11.FakeLag;
import com.anormal.client.module.impl.uzny11.Freecam;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
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

            // Uzny11 Freecam
            Freecam uCam = ModuleManager.getModule(Freecam.class);
            if (uCam != null && uCam.isEnabled() && uCam.isCameraActive()) {
                ci.cancel();
                return;
            }

            if (FakeLag.isFlushing()) return;
            FakeLag uFakeLag = ModuleManager.getModule(FakeLag.class);
            boolean hold = false;
            if (uFakeLag != null && uFakeLag.isEnabled() && uFakeLag.isHolding()) {
                uFakeLag.queue(packet);
                hold = true;
            }
            if (hold) ci.cancel();
        } catch (Throwable ignored) {}
    }
}
