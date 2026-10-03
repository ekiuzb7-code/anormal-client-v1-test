package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.XRay;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
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
                if (!xray.isVisibleBlock(self.getBlock())) {
                    cir.setReturnValue(BlockRenderType.INVISIBLE);
                }
            }
        } catch (Throwable ignored) {}
    }
}
