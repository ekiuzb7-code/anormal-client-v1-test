package dev.zaza.client.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.zaza.client.rotation.ZazaRotations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gameplay that reads the local player's look direction (line-of-sight checks,
 * picking, raycasts) sees the rotation the manager is holding (Cold Client port).
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getViewVector", at = @At("HEAD"), cancellable = true)
    private void zaza$viewVector(float partialTick, CallbackInfoReturnable<Vec3> info) {
        Vec3 view = ZazaRotations.viewVector((Entity) (Object) this);
        if (view != null) info.setReturnValue(view);
    }
}
