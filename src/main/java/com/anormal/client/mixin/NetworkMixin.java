package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.FakeLag;
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
            if (FakeLag.isFlushing() || com.anormal.client.module.impl.uzny11.FakeLag.isFlushing()) return;
            FakeLag fakeLag = ModuleManager.getModule(FakeLag.class);
            com.anormal.client.module.impl.uzny11.FakeLag uFakeLag =
                    ModuleManager.getModule(com.anormal.client.module.impl.uzny11.FakeLag.class);
            boolean hold = false;
            if (fakeLag != null && fakeLag.isEnabled() && fakeLag.isHolding()) {
                FakeLag.queue(packet);
                hold = true;
            }
            if (uFakeLag != null && uFakeLag.isEnabled() && uFakeLag.isHolding()) {
                com.anormal.client.module.impl.uzny11.FakeLag.queue(packet);
                hold = true;
            }
            if (hold) ci.cancel();
        } catch (Throwable ignored) {}
    }
}
