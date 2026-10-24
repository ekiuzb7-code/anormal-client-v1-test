package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.FastBreak;
import com.anormal.client.module.impl.combat.TargetLock;
import com.anormal.client.module.impl.world.HitSwap;
import com.anormal.client.module.impl.world.InstantMine;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    /**
     * HitSwap has to switch the slot BEFORE the attack packet leaves the client.
     *
     * <p>The old implementation did it from {@code onTick}, which runs after the click was already
     * processed - so the hit always went out with the previous weapon. This hook runs at the head
     * of the attack itself, which is the only place where the swap can still affect the packet.</p>
     */
    @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
    private void anormal$hitSwap(PlayerEntity player, Entity target, CallbackInfo ci) {
        try {
            HitSwap hitSwap = ModuleManager.getModule(HitSwap.class);
            if (hitSwap != null && hitSwap.isEnabled()) hitSwap.beforeAttack(target);

            // A manual attack is the natural way to choose a lock target.
            TargetLock targetLock = ModuleManager.getModule(TargetLock.class);
            if (targetLock != null && targetLock.isEnabled()) targetLock.lockOn(target);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("RETURN"), require = 0)
    private void anormal$removeBreakingCooldown(BlockPos pos, Direction direction,
                                                CallbackInfoReturnable<Boolean> cir) {
        try {
            InstantMine instantMine = ModuleManager.getModule(InstantMine.class);
            if (instantMine != null && instantMine.isFast()) {
                blockBreakingCooldown = 0;
                return;
            }
            // FastBreak uses the same cooldown removal; its early finish is sent from the module.
            FastBreak fastBreak = ModuleManager.getModule(FastBreak.class);
            if (fastBreak != null && fastBreak.isEnabled()) {
                blockBreakingCooldown = 0;
            }
        } catch (Throwable ignored) {
        }
    }
}
