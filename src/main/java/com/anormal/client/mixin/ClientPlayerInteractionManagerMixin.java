package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.InstantMine;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the vanilla delay between broken blocks ("Fast" mode of InstantMine).
 *
 * <p>Vanilla sets {@code blockBreakingCooldown} after each breaking tick, which
 * is what makes you wait ~5 ticks per block even with a good tool. Zeroing it
 * after every call makes mining run at the tool's real speed.</p>
 */
@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {

    @Shadow
    private int blockBreakingCooldown;

    @Inject(method = "updateBlockBreakingProgress", at = @At("RETURN"), require = 0)
    private void anormal$removeBreakingCooldown(BlockPos pos, Direction direction,
                                                CallbackInfoReturnable<Boolean> cir) {
        try {
            InstantMine instantMine = ModuleManager.getModule(InstantMine.class);
            if (instantMine != null && instantMine.isFast()) {
                blockBreakingCooldown = 0;
            }
        } catch (Throwable ignored) {
        }
    }
}
