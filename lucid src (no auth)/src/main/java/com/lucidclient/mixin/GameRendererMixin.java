package com.lucidclient.mixin;

import com.lucidclient.module.impl.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Freecam (Freecam-mod implementation) render hooks:
 *
 *   - renderHand: hides the first-person hand while the camera is detached
 *     (Show Hand = off, the default).
 *   - shouldRenderBlockOutline: suppresses the block outline when
 *     interactions are disabled so the crosshair doesn't imply you can click.
 *   - updateCrosshairTarget: while Interaction Mode is PLAYER (or the user is
 *     controlling their body), the pick ray originates from the player body
 *     rather than the detached camera.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void lucid$freecamHand(CallbackInfo ci) {
        if (Freecam.isActive() && Freecam.shouldHideHand()) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void lucid$freecamOutline(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.isActive() && !Freecam.isPlayerControlEnabled() && Freecam.shouldPreventInteractions()) {
            cir.setReturnValue(false);
        }
    }

    @ModifyVariable(
            method = "updateCrosshairTarget",
            at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/client/MinecraftClient;getCameraEntity()Lnet/minecraft/entity/Entity;")
    )
    private Entity lucid$freecamHitSource(Entity entity) {
        if (Freecam.isActive() && (Freecam.isPlayerControlEnabled() || Freecam.allowInteractionsFromPlayer())) {
            return MinecraftClient.getInstance().player;
        }
        return entity;
    }
}
