package com.lucidclient.mixin;

import com.lucidclient.module.impl.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freecam (Freecam-mod implementation): makes the first-person hand follow
 * the FreeCamera's rotation and bob instead of the player body's, so the
 * hand swings/moves coherently with the detached camera.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    @Unique
    private float lucid$tickProgress;

    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"))
    private void lucid$storeTick(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, ClientPlayerEntity player, int light, CallbackInfo ci) {
        this.lucid$tickProgress = tickProgress;
    }

    @Redirect(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch(F)F")
    )
    private float lucid$pitch(ClientPlayerEntity player, float tickProgress) {
        return Freecam.isActive() && Freecam.getFreeCamera() != null
                ? Freecam.getFreeCamera().getPitch(tickProgress) : player.getPitch(tickProgress);
    }

    @Redirect(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw(F)F")
    )
    private float lucid$yaw(ClientPlayerEntity player, float tickProgress) {
        return Freecam.isActive() && Freecam.getFreeCamera() != null
                ? Freecam.getFreeCamera().getYaw(tickProgress) : player.getYaw(tickProgress);
    }

    @Redirect(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;renderPitch:F", opcode = Opcodes.GETFIELD)
    )
    private float lucid$renderPitch(ClientPlayerEntity player) {
        return Freecam.isActive() && Freecam.getFreeCamera() != null
                ? Freecam.getFreeCamera().renderPitchBob : player.renderPitch;
    }

    @Redirect(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;lastRenderPitch:F", opcode = Opcodes.GETFIELD)
    )
    private float lucid$lastRenderPitch(ClientPlayerEntity player) {
        return Freecam.isActive() && Freecam.getFreeCamera() != null
                ? Freecam.getFreeCamera().lastRenderPitchBob : player.lastRenderPitch;
    }

    @Redirect(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;renderYaw:F", opcode = Opcodes.GETFIELD)
    )
    private float lucid$renderYaw(ClientPlayerEntity player) {
        return Freecam.isActive() && Freecam.getFreeCamera() != null
                ? Freecam.getFreeCamera().renderYawBob : player.renderYaw;
    }

    @Redirect(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;lastRenderYaw:F", opcode = Opcodes.GETFIELD)
    )
    private float lucid$lastRenderYaw(ClientPlayerEntity player) {
        return Freecam.isActive() && Freecam.getFreeCamera() != null
                ? Freecam.getFreeCamera().lastRenderYawBob : player.lastRenderYaw;
    }

    @ModifyVariable(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private int lucid$handLight(int light) {
        if (Freecam.isActive() && Freecam.getFreeCamera() != null) {
            return MinecraftClient.getInstance().getEntityRenderDispatcher().getLight(Freecam.getFreeCamera(), lucid$tickProgress);
        }
        return light;
    }
}
