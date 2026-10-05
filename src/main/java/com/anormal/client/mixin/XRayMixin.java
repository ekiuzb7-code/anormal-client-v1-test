package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.XRay;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockState.class)
public class XRayMixin {

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true, require = 0)
    private void onGetRenderType(CallbackInfoReturnable<BlockRenderType> cir) {
        try {
            XRay xray = ModuleManager.getModule(XRay.class);
            if (xray != null && xray.isEnabled() && xray.isRealMode()) {
                BlockState self = (BlockState) (Object) this;
                Block block = self.getBlock();

                // Hide all non-selected blocks
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
                net.minecraft.fluid.Fluid fluid = self.getFluid();
                Block block = fluid == net.minecraft.fluid.Fluids.WATER ? Blocks.WATER : (fluid == net.minecraft.fluid.Fluids.LAVA ? Blocks.LAVA : null);
                if (block != null && !xray.isVisibleBlock(block)) {
                    cir.setReturnValue(BlockRenderType.INVISIBLE);
                }
            }
        } catch (Throwable ignored) {}
    }
}
