package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.XRay;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides water / lava when they are not part of the X-Ray whitelist.
 *
 * <p>Fluids are drawn by {@link FluidRenderer}, not through the block model
 * pipeline, so the {@code getRenderType()} hook never sees them. Without this
 * you would still see every ocean and lava lake in X-Ray mode.</p>
 */
@Mixin(FluidRenderer.class)
public class FluidRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$render(BlockRenderView world, BlockPos pos, VertexConsumer vertexConsumer,
                                BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        try {
            XRay xray = ModuleManager.getModule(XRay.class);
            if (xray == null || !xray.isEnabled() || !xray.isRealMode()) return;

            Block fluidBlock = fluidState.getBlockState().getBlock();
            if (!xray.isVisibleBlock(fluidBlock)) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}
