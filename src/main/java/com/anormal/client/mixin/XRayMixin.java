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

/**
 * Real X-Ray: hides every block that is not in the X-Ray whitelist.
 *
 * <p><b>Important:</b> {@code getRenderType()} is declared in
 * {@code AbstractBlock.AbstractBlockState}, NOT in {@code BlockState}.
 * The previous version targeted {@code BlockState}, so Mixin could not find
 * the method and silently skipped the injection (require = 0) - which is why
 * X-Ray did absolutely nothing. Targeting the declaring class fixes it.</p>
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public class XRayMixin {

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$getRenderType(CallbackInfoReturnable<BlockRenderType> cir) {
        try {
            XRay xray = ModuleManager.getModule(XRay.class);
            if (xray == null || !xray.isEnabled() || !xray.isRealMode()) return;

            // Never hide the whole world if the whitelist is empty.
            if (xray.getSelectedBlocks().isEmpty()) return;

            BlockState self = (BlockState) (Object) this;
            if (!xray.isVisibleBlock(self.getBlock())) {
                cir.setReturnValue(BlockRenderType.INVISIBLE);
            }
        } catch (Throwable ignored) {
        }
    }
}
