package dev.zaza.client.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.zaza.client.rotation.Rotation;
import dev.zaza.client.rotation.ZazaRotations;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends the rotation that the rotation manager is holding instead of the real
 * camera rotation — the core of silent aim (Cold Client LocalPlayerMixin port).
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Shadow
    private float yRotLast;
    @Shadow
    private float xRotLast;

    @ModifyExpressionValue(method = "sendPosition",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float zaza$sentYaw(float yaw) {
        return ZazaRotations.sentYaw(yaw);
    }

    @ModifyExpressionValue(method = "sendPosition",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float zaza$sentPitch(float pitch) {
        return ZazaRotations.sentPitch(pitch);
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void zaza$sent(CallbackInfo info) {
        ZazaRotations.sent(new Rotation(yRotLast, xRotLast));
    }
}
