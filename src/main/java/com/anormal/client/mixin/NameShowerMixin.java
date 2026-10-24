package com.anormal.client.mixin;

import com.anormal.client.module.impl.player.NameShower;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps vanilla's own nametag out of the way for the local player.
 *
 * <p>The tag itself is drawn by {@link NameShower} in the HUD pass, because vanilla positions the
 * label from the render state and that state is only refreshed while the entity passes the
 * visibility check - at running / elytra speed the plate ends up a tick behind the head. So this
 * mixin only has to remember which render state belongs to us and cancel its label draw; the text
 * override stays as a fallback for any path that still renders through vanilla.</p>
 */
@Mixin(PlayerEntityRenderer.class)
public class NameShowerMixin {

    /** The render state of the local player, refreshed every frame in updateRenderState. */
    @Unique
    private static PlayerEntityRenderState anormal$localState;

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"),
            require = 0
    )
    private void anormal$trackLocalState(PlayerLikeEntity entity, PlayerEntityRenderState state,
                                         float tickDelta, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || entity != mc.player) return;

            anormal$localState = state;

            Text label = NameShower.buildLabel(mc.player);
            if (label != null) {
                state.displayName = label;
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void anormal$hideVanillaLabel(PlayerEntityRenderState state, MatrixStack matrices,
                                          OrderedRenderCommandQueue queue, CameraRenderState camera,
                                          CallbackInfo ci) {
        try {
            // Only our own state, and only while the HUD pass is drawing the tag - otherwise
            // the player would see two overlapping labels.
            if (state == anormal$localState && NameShower.drawsOwnTag()) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}
