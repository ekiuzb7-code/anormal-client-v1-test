package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.XRay;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
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

                // Hide all non-selected blocks
                if (!xray.isVisibleBlock(block)) {
                    cir.setReturnValue(BlockRenderType.INVISIBLE);
                    return;
                }

                // Exposed only check (LiquidBounce style)
                if (xray.isExposedOnly()) {
                    // We need world and pos for this check, but we only have BlockState
                    // The shouldRenderBlock method will be called from client-side rendering
                    // For now, just skip exposed check here - it's handled client-side
                }
            }
        } catch (Throwable ignored) {}
    }
}

// Mixin for face culling (LiquidBounce style - modifyDrawSide equivalent)
@Mixin(AbstractBlock.AbstractBlockState.class)
class XRayFaceCullingMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 0)
    private void onShouldRenderFace(World world, BlockPos pos, Direction side, BlockState neighborState, CallbackInfoReturnable<Boolean> cir) {
        try {
            XRay xray = ModuleManager.getModule(XRay.class);
            if (xray != null && xray.isEnabled() && xray.isRealMode()) {
                BlockState self = (BlockState) (Object) this;
                Block block = self.getBlock();

                // If block is not visible, don't render any face
                if (!xray.isVisibleBlock(block)) {
                    cir.setReturnValue(false);
                    return;
                }

                // Exposed only face culling (LiquidBounce style)
                if (xray.isExposedOnly()) {
                    // Don't cull faces if neighbor is not solid or not same block
                    if (!neighborState.isSolid() || neighborState.getBlock() != block) {
                        cir.setReturnValue(true);
                        return;
                    }
                }

                // Transparent background: don't cull faces if we want to see through
                if (xray.isTransparentBackground()) {
                    if (!xray.isVisibleBlock(block) && xray.getBackgroundOpacity() > 0) {
                        cir.setReturnValue(true);
                        return;
                    }
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
                Fluid fluid = self.getFluid();
                Block block = fluid == Fluids.WATER ? Blocks.WATER : (fluid == Fluids.LAVA ? Blocks.LAVA : null);
                if (block != null && !xray.isVisibleBlock(block)) {
                    cir.setReturnValue(BlockRenderType.INVISIBLE);
                }
            }
        } catch (Throwable ignored) {}
    }
}
