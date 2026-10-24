package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.module.impl.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Freecam (Freecam-mod implementation): while the camera entity is detached,
 * the real player still reports itself as the camera — needed for Baritone
 * compatibility and vanilla code paths that ask "is this entity the camera".
 */
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Inject(method = "isCamera", at = @At("HEAD"), cancellable = true)
    private void lucid$freecamIsCamera(CallbackInfoReturnable<Boolean> cir) {
        try {
            if (Freecam.isActive() && (Object) this == MinecraftClient.getInstance().player) {
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid ClientPlayerEntityMixin threw", t);
        }
    }
}
