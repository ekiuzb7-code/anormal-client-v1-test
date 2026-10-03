package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.XRay;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class XRayMixin {

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true, require = 0)
    private void onGetRenderType(CallbackInfoReturnable<BlockRenderType> cir) {
        try {
            XRay xray = ModuleManager.getModule(XRay.class);
            if (xray != null && xray.isEnabled() && xray.isRealMode()) {
                BlockState self = (BlockState) (Object) this;
                Block block = self.getBlock();

                // Hide all non-selected blocks including water/lava
                if (!xray.isVisibleBlock(block)) {
                    cir.setReturnValue(BlockRenderType.INVISIBLE);
                }
            }
        } catch (Throwable ignored) {}
    }
}

// Also mixin fluid states to hide water/lava fluids
@Mixin(FluidState.class)
class FluidStateMixin {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true, require = 0)
    private void onGetFluidRenderType(CallbackInfoReturnable<BlockRenderType> cir) {
        try {
            XRay xray = ModuleManager.getModule(XRay.class);
            if (xray != null && xray.isEnabled() && xray.isRealMode()) {
                FluidState self = (FluidState) (Object) this;
                // Check if this fluid's block is selected
                Block block = self.getBlock();
                if (!xray.isVisibleBlock(block)) {
                    cir.setReturnValue(BlockRenderType.INVISIBLE);
                }
            }
        } catch (Throwable ignored) {}
    }
}
