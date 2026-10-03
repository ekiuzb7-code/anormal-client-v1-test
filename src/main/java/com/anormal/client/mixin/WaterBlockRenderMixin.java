package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.ClearWater;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.BlockEntityRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
abstract class WaterBlockRenderMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void onRenderBlock(net.minecraft.block.BlockState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, CallbackInfo ci) {
        try {
            ClearWater cw = ModuleManager.getModule(ClearWater.class);
            if (cw != null && cw.isEnabled()) {
                Block block = (Block) (Object) this;
                // Skip rendering water/lava blocks entirely
                if (block == Blocks.WATER || block == Blocks.LAVA) {
                    ci.cancel();
                }
            }
        } catch (Throwable ignored) {}
    }
}