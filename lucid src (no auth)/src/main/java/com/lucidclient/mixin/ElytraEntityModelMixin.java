package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.module.impl.FakeItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fake Item (rebuilt elytra pose system): the WINGS half of the cosmetic
 * glide, driven at the innermost point of the render chain — the tail of
 * {@code ElytraEntityModel.setAngles}, immediately before the wing parts
 * are submitted for drawing.
 *
 * <p>Telemetry proved that on DonutSMP vanilla's {@code ElytraFlightController}
 * never updates for the local player (its update is tied to the server-owned
 * glide state), so the wings stayed at the raw model default — dead flat,
 * embedded INTO the back. The controller's exact state machine (verified from
 * the 1.21.11 bytecode) is therefore re-run clientside by
 * {@link FakeItem#stepWingController()} once per tick — idle fold
 * {@code pitch=0.2618, yaw=0, roll=-0.2618}, vanilla sneak pose
 * {@code pitch=0.6981, yaw=0.0873, roll=-0.7854} for crouch, and the
 * velocity-driven glide spread {@code 1 - (-vyNorm)^1.5} lerping
 * {@code pitch 0.2618->0.3491} / {@code roll -0.2618->-1.5708}.
 *
 * <p>This hook simply copies that smoothed state onto the wing parts and
 * mirrors it exactly the way vanilla's own setter does: the right wing
 * negates YAW and ROLL, shares PITCH and ORIGIN.
 *
 * <p>Gating: writes only happen while ours is active AND the local player has
 * the fake elytra equipped, so other players' genuine elytra renders keep
 * their own vanilla pose untouched.
 */
@Mixin(ElytraEntityModel.class)
public abstract class ElytraEntityModelMixin {

    @Shadow @Final private ModelPart leftWing;
    @Shadow @Final private ModelPart rightWing;

    @Inject(
        method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
        at = @At("TAIL")
    )
    private void lucid$driveWings(BipedEntityRenderState state, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;
            if (!FakeItem.isFakeElytraEquipped()) return;  // not ours — leave vanilla angles

            // Interpolate last tick's and this tick's controller state by the
            // PARTIAL tick (vanilla lerps its controller the same way), so the
            // wings render at fully smooth frame rate instead of 20Hz steps.
            MinecraftClient client = MinecraftClient.getInstance();
            float partial = client == null
                ? 0.0f
                : client.getRenderTickCounter().getTickProgress(true);
            float pitch = MathHelper.lerp(partial,
                FakeItem.getElytraWingPitchPrev(), FakeItem.getElytraWingPitch());
            float yaw = MathHelper.lerp(partial,
                FakeItem.getElytraWingYawPrev(), FakeItem.getElytraWingYaw());
            float roll = MathHelper.lerp(partial,
                FakeItem.getElytraWingRollPrev(), FakeItem.getElytraWingRoll());
            leftWing.pitch = pitch;
            leftWing.yaw = yaw;
            leftWing.roll = roll;
            rightWing.pitch = pitch;
            rightWing.yaw = -yaw;
            rightWing.roll = -roll;   // mirror, exactly like vanilla's setter
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid ElytraEntityModelMixin threw", t);
        }
    }
}