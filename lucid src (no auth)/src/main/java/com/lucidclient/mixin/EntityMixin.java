package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.freecam.FreeCamera;
import com.lucidclient.module.impl.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Freecam (Freecam-mod implementation) entity hooks:
 *
 * <ul>
 *   <li>The frozen player stops updating its own movement server-side while
 *       the camera flies (isLogicalSideForUpdatingMovement redirect).</li>
 *   <li>Mouse look is routed into the FreeCamera entity instead of the
 *       player body.</li>
 *   <li>The player's yaw/pitch reads (crosshair ray, packets) reflect the
 *       camera when Interaction Mode is CAMERA.</li>
 *   <li>The FreeCamera never pushes or gets pushed by other entities.</li>
 *   <li>Freeze Player: hard-blocks position/velocity writes on the player
 *       body while the camera is detached.</li>
 * </ul>
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    // This redirect makes the game think the player is NOT controlled when
    // freecam is active, so the player doesn't update their movement
    // server-side while the camera is flying.
    @Redirect(method = "isLogicalSideForUpdatingMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isControlledByPlayer()Z"))
    private boolean lucid$fixPlayerControlWhileFreecam(Entity instance) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (Freecam.isActive() && instance == mc.player && !Freecam.isPlayerControlEnabled()) {
                return false;
            }
            return instance.isControlledByPlayer();
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid EntityMixin.fixPlayerControlWhileFreecam threw", t);
            return instance.isControlledByPlayer();
        }
    }

    // Makes mouse input rotate the FreeCamera instead of the player body.
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void lucid$freecamLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            FreeCamera camera = Freecam.getFreeCamera();
            if (Freecam.isActive() && lucid$this() == mc.player
                    && !Freecam.isPlayerControlEnabled() && camera != null) {
                camera.changeLookDirection(cursorDeltaX, cursorDeltaY);
                ci.cancel();
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid EntityMixin.freecamLook threw", t);
        }
    }

    @Inject(method = "getPitch(F)F", at = @At("HEAD"), cancellable = true)
    private void lucid$freecamPitch(float tickProgress, CallbackInfoReturnable<Float> cir) {
        try {
            FreeCamera camera = Freecam.getFreeCamera();
            if (Freecam.isActive() && lucid$this() == MinecraftClient.getInstance().player
                    && !Freecam.isPlayerControlEnabled() && !Freecam.allowInteractionsFromPlayer()
                    && camera != null) {
                cir.setReturnValue(camera.getPitch(tickProgress));
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid EntityMixin.freecamPitch threw", t);
        }
    }

    @Inject(method = "getYaw(F)F", at = @At("HEAD"), cancellable = true)
    private void lucid$freecamYaw(float tickProgress, CallbackInfoReturnable<Float> cir) {
        try {
            FreeCamera camera = Freecam.getFreeCamera();
            if (Freecam.isActive() && lucid$this() == MinecraftClient.getInstance().player
                    && !Freecam.isPlayerControlEnabled() && !Freecam.allowInteractionsFromPlayer()
                    && camera != null) {
                cir.setReturnValue(camera.getYaw(tickProgress));
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid EntityMixin.freecamYaw threw", t);
        }
    }

    // Prevents FreeCamera from pushing / getting pushed by entities.
    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void lucid$freecamPush(Entity entity, CallbackInfo ci) {
        try {
            if (Freecam.isActive() && (entity instanceof FreeCamera || lucid$this() instanceof FreeCamera)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid EntityMixin.freecamPush threw", t);
        }
    }

    // Freeze Player: block movement writes on the real player.
    @Inject(method = "setVelocity(DDD)V", at = @At("HEAD"), cancellable = true)
    private void lucid$blockPlayerVelocityWhenFrozen(CallbackInfo ci) {
        if (lucid$shouldFreezePlayerBody()) ci.cancel();
    }

    @Inject(method = "updateVelocity", at = @At("HEAD"), cancellable = true)
    private void lucid$blockPlayerMovementWhenFrozen(CallbackInfo ci) {
        if (lucid$shouldFreezePlayerBody()) ci.cancel();
    }

    @Inject(method = "setPosition(DDD)V", at = @At("HEAD"), cancellable = true)
    private void lucid$blockPlayerPositionWhenFrozen(CallbackInfo ci) {
        if (lucid$shouldFreezePlayerBody()) ci.cancel();
    }

    @Inject(method = "setPos", at = @At("HEAD"), cancellable = true)
    private void lucid$blockPlayerPosUpdateWhenFrozen(CallbackInfo ci) {
        if (lucid$shouldFreezePlayerBody()) ci.cancel();
    }

    @Unique
    private Entity lucid$this() {
        return (Entity) (Object) this;
    }

    @Unique
    private boolean lucid$shouldFreezePlayerBody() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            return Freecam.isActive() && lucid$this() == mc.player
                && Freecam.shouldFreezePlayer() && !Freecam.isPlayerControlEnabled();
        } catch (Throwable t) {
            return false;
        }
    }
}
