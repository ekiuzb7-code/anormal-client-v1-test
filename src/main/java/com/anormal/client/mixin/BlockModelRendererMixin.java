package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.XRay;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces every face of a whitelisted block to be drawn, even when the block is
 * completely buried.
 *
 * <p>Hiding the other blocks is not enough on its own: the chunk mesher culls
 * a face whenever the neighbour is an opaque full cube. A diamond ore inside
 * stone would therefore have all 6 faces culled and stay invisible. Returning
 * {@code true} here for whitelisted blocks makes them render through walls.</p>
 */
@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {

    @Inject(
            method = "shouldDrawFace(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;ZLnet/minecraft/util/math/Direction;Lnet/minecraft/util/math/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void anormal$shouldDrawFace(BlockRenderView world, BlockState state, boolean bl,
                                               Direction direction, BlockPos pos,
                                               CallbackInfoReturnable<Boolean> cir) {
        try {
            XRay xray = ModuleManager.getModule(XRay.class);
            if (xray == null || !xray.isEnabled() || !xray.isRealMode()) return;
            if (xray.isVisibleBlock(state.getBlock())) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
        }
    }
}
