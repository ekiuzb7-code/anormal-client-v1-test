package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.InstantMine;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces {@code getHardness} to 0 for blocks the InstantMine filter accepts,
 * which is what makes them break in a single click.
 *
 * <p>Targets the declaring class {@code AbstractBlock.AbstractBlockState}.</p>
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public class BlockHardnessMixin {

    @Inject(method = "getHardness", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$getHardness(BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        try {
            InstantMine instantMine = ModuleManager.getModule(InstantMine.class);
            if (instantMine == null || !instantMine.isInstant()) return;

            BlockState self = (BlockState) (Object) this;
            if (instantMine.shouldInstantBreak(self)) {
                cir.setReturnValue(0.0f);
            }
        } catch (Throwable ignored) {
        }
    }
}
