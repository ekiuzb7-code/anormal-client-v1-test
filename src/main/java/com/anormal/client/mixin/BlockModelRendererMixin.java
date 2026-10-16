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
 * The actual block-hiding hook for Real X-Ray.
 *
 * <p>In 1.21.11 the chunk mesher ({@code SectionBuilder}) no longer honours
 * {@code BlockRenderType.INVISIBLE} - it only uses {@code getRenderType()} to
 * choose a render <i>layer</i>. So the old approach of returning
 * {@code INVISIBLE} from {@code AbstractBlockState.getRenderType()} silently
 * stopped hiding anything, which is why X-Ray looked like it did nothing.</p>
 *
 * <p>{@code BlockModelRenderer.shouldDrawFace} is the method the mesher really
 * consults for every single face. Returning {@code false} for a block that is
 * not on the whitelist removes it completely, and returning {@code true} for a
 * whitelisted block keeps all six faces so ores stay visible through walls.
 * (This mirrors Wurst's {@code ModelBlockRendererMixin}.)</p>
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

            // Never blank the whole world if the whitelist is empty.
            if (xray.getSelectedBlocks().isEmpty()) return;

            // true  -> whitelisted: keep every face (visible through walls)
            // false -> everything else: hide the block entirely
            cir.setReturnValue(xray.isVisibleBlock(state.getBlock()));
        } catch (Throwable ignored) {
        }
    }
}
