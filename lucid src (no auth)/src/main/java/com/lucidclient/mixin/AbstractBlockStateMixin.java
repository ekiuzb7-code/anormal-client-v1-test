package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.freecam.FreeCamera;
import com.lucidclient.module.impl.Freecam;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Freecam collision ignores (Freecam-mod implementation): while the camera
 * entity's collision shape is queried for a block the user opted to ignore
 * (transparent / openable / all), the shape is reported as empty so the
 * camera flies straight through.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {

    @Shadow
    public abstract Block getBlock();

    @Inject(
            method = "getCollisionShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lucid$freecamCollision(BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
        try {
            if (context instanceof EntityShapeContext entityContext
                    && entityContext.getEntity() instanceof FreeCamera
                    && Freecam.isActive()
                    && Freecam.ignoreCollisionWith(getBlock())) {
                cir.setReturnValue(VoxelShapes.empty());
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid AbstractBlockStateMixin threw", t);
        }
    }
}
