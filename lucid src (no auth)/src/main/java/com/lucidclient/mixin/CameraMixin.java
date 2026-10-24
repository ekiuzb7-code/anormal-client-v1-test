package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.freecam.FreeCamera;
import com.lucidclient.module.impl.Freecam;
import com.lucidclient.module.impl.Freelook;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Camera hooks for the two camera modules.
 *
 * <p><b>Freecam</b> (Freecam-mod implementation) no longer overrides the
 * camera transform here — the module makes the vanilla camera follow a
 * spawned {@link FreeCamera} entity via {@code setCameraEntity}, so vanilla
 * positions the camera on the detached entity itself. This mixin only:
 *
 * <ul>
 *   <li>snaps the camera's eye height (no smooth transition) when the camera
 *       entity switches to/from the FreeCamera, and</li>
 *   <li>suppresses the submersion overlay (water/lava/powder snow fog) while
 *       flying, per the Show Submersion Fog setting.</li>
 * </ul>
 *
 * <p><b>Freelook</b> (glazed mechanism, rotation-only owner): unchanged —
 * {@code @ModifyArgs} on the {@code setRotation(FF)} calls inside
 * {@code update()} overrides the focused entity's rotation args BEFORE
 * vanilla's third-person pullback, so vanilla's own orbit + wall clip runs.
 * {@code clipToSpace} HEAD-inject implements Freelook's no-clip opt-in.
 *
 * <p>Freecam takes priority over Freelook (checked first everywhere) so the
 * two never fight.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private Entity focusedEntity;

    @Shadow
    private float lastCameraY;

    @Shadow
    private float cameraY;

    /**
     * Freecam: when the camera entity switches to/from the FreeCamera, snap
     * the smoothed eye height so the view doesn't glide for a few frames.
     */
    @Inject(method = "update", at = @At("HEAD"))
    private void lucid$freecamEyeHeight(World world, Entity entity, boolean thirdPerson,
                                        boolean inverseView, float tickDelta, CallbackInfo ci) {
        try {
            if (entity == null || this.focusedEntity == null || entity.equals(this.focusedEntity)) {
                return;
            }
            if (entity instanceof FreeCamera || this.focusedEntity instanceof FreeCamera) {
                this.lastCameraY = this.cameraY = entity.getStandingEyeHeight();
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid CameraMixin freecamEyeHeight threw", t);
        }
    }

    /**
     * Freecam: removes the submersion overlay while underwater, in lava, or
     * in powdered snow (Show Submersion Fog = off).
     */
    @Inject(method = "getSubmersionType", at = @At("HEAD"), cancellable = true)
    private void lucid$freecamFog(CallbackInfoReturnable<CameraSubmersionType> cir) {
        try {
            if (Freecam.isActive() && Freecam.shouldHideSubmersionFog()) {
                cir.setReturnValue(CameraSubmersionType.NONE);
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid CameraMixin freecamFog threw", t);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Freelook (glazed mechanism): rotation-args override inside
    //  Camera.update, BEFORE vanilla's third-person pullback + clip.
    // ════════════════════════════════════════════════════════════════

    /**
     * Ordinal 0 of the entity-rotation {@code setRotation(FF)} call inside
     * {@code update} — the experimental-minecart lerp path. Same override as
     * the normal path so freelooking from a lerped minecart behaves
     * identically.
     */
    @ModifyArgs(
        method = "update",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V",
                 ordinal = 0))
    private void lucid$freelookRotationMinecart(Args args) {
        lucid$applyFreelookRotation(args);
    }

    /**
     * Ordinal 1 — the normal {@code setRotation(entity.getYaw(tickDelta),
     * entity.getPitch(tickDelta))} call. While Freelook is active the args
     * become the freelook camera angles; vanilla's subsequent third-person
     * pullback along this rotation produces the orbit.
     */
    @ModifyArgs(
        method = "update",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V",
                 ordinal = 1))
    private void lucid$freelookRotation(Args args) {
        lucid$applyFreelookRotation(args);
    }

    /** Shared arg override — skipped when Freecam owns the camera. */
    private void lucid$applyFreelookRotation(Args args) {
        try {
            // Freecam wins over Freelook (same precedence as before) — leave
            // vanilla's args alone; the freecam camera entity owns the view.
            if (Freecam.isActive() || !Freelook.isActive()) return;
            args.set(0, Freelook.getCameraYaw());
            args.set(1, Freelook.getCameraPitch());
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid CameraMixin freelook rotation threw", t);
        }
    }

    /**
     * glazed's see-through-walls: skip the vanilla 8-direction wall clip by
     * returning the desired pullback distance untouched. Only applies while
     * Freelook is active AND the user opted in; every other caller (vanilla
     * third person, freecam) is unaffected.
     */
    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void lucid$freelookNoClip(float desiredDistance, CallbackInfoReturnable<Float> cir) {
        try {
            if (Freelook.isActive() && Freelook.allowCameraNoClip()) {
                cir.setReturnValue(desiredDistance);
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid CameraMixin no-clip threw", t);
        }
    }
}
