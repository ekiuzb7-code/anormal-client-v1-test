package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.movement.SafeWalk;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SafeWalk, ported 1:1 from Wurst's LocalPlayerMixin.
 *
 * <p>Wurst does not emulate sneak-key presses on a timer - it hooks the two
 * vanilla methods that already implement "don't walk off the edge":</p>
 *
 * <ul>
 *   <li>{@code Player.isStayingOnGroundSurface()} (Yarn: {@code clipAtLedge()})
 *       - returns true when the player is sneak-holding. Forcing it true makes
 *       vanilla itself clip your movement at ledges.</li>
 *   <li>{@code Player.maybeBackOffFromEdge(Vec3, MoverType)}
 *       (Yarn: {@code adjustMovementForSneaking(Vec3d, MovementType)}) - the
 *       method that actually performs the clipping. Comparing its input with
 *       its output tells us whether you were clipped, which is what drives the
 *       optional visible sneak.</li>
 * </ul>
 */
@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {

    /**
     * Wurst: {@code isStayingOnGroundSurface()}. Yarn renamed it to
     * {@code clipAtLedge()} and its body is literally {@code return isSneaking();}.
     * Forcing it to true while SafeWalk is on makes vanilla apply its own
     * ledge protection, so you simply cannot walk off a block edge.
     */
    @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$clipAtLedge(CallbackInfoReturnable<Boolean> cir) {
        try {
            SafeWalk safeWalk = ModuleManager.getModule(SafeWalk.class);
            if (safeWalk != null && safeWalk.isEnabled())
                cir.setReturnValue(true);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Wurst: {@code maybeBackOffFromEdge()}. The original movement vector and
     * the returned (possibly clipped) vector differ exactly when vanilla
     * refused part of your movement because of the ledge - that is the
     * "clipping" flag SafeWalk uses for its visible sneak.
     */
    @Inject(method = "adjustMovementForSneaking", at = @At("RETURN"), require = 0)
    private void anormal$adjustMovementForSneaking(Vec3d movement, MovementType type,
                                                  CallbackInfoReturnable<Vec3d> cir) {
        if (movement == null) return;
        try {
            SafeWalk safeWalk = ModuleManager.getModule(SafeWalk.class);
            if (safeWalk == null) return;

            Vec3d result = cir.getReturnValue();
            safeWalk.onClipAtLedge(result == null || !movement.equals(result));
        } catch (Throwable ignored) {
        }
    }
}
