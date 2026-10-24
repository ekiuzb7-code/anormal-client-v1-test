package com.anormal.client.mixin;

import com.anormal.client.rotation.Rotation;
import com.anormal.client.rotation.RotationController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns the local player's <em>model</em> towards the silent rotation.
 *
 * <p>Silent rotations only exist in the packets: the camera never moves, so the client's own body
 * and head keep facing wherever the player is looking. From anybody else's point of view your
 * character walks around shooting while staring at nothing, which is the most obvious tell there
 * is.</p>
 *
 * <p>This copies the rotation the server already received into the render state, so your own model
 * turns its head and body exactly the way the server thinks it is turned - the same thing Vape's
 * SilentAura does.</p>
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"),
            require = 0
    )
    private void anormal$silentBodyRotation(LivingEntity entity, LivingEntityRenderState state,
                                            float tickDelta, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (entity != mc.player) return;

            Rotation silent = RotationController.serverRotation();
            if (silent == null) return;

            // bodyYaw drives the model; relativeHeadYaw keeps the head aligned with it.
            state.bodyYaw = silent.yaw();
            state.relativeHeadYaw = 0.0f;
            state.pitch = silent.pitch();
        } catch (Throwable ignored) {
        }
    }
}
