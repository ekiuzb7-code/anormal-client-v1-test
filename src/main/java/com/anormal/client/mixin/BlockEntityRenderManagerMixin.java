package com.anormal.client.mixin;

import com.anormal.client.module.impl.optimization.BlockEntityOptimizer;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips distant block entities.
 *
 * <p>Block entities (chests, signs, banners, shulkers...) are re-submitted
 * every frame outside the chunk mesh, so a storage room full of them is far
 * more expensive than the same volume of stone. The render state carries both
 * the position and the block entity type, which is exactly what the module
 * needs to decide.</p>
 */
@Mixin(BlockEntityRenderManager.class)
public class BlockEntityRenderManagerMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$render(BlockEntityRenderState state, MatrixStack matrices,
                                OrderedRenderCommandQueue queue, CameraRenderState camera,
                                CallbackInfo ci) {
        try {
            if (state == null || state.pos == null) return;
            String type = state.type == null ? "" : state.type.toString();
            if (BlockEntityOptimizer.shouldCull(state.pos, type)) ci.cancel();
        } catch (Throwable ignored) {
        }
    }
}
