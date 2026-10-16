package com.anormal.client.mixin;

import com.anormal.client.module.impl.optimization.EntityLodOptimizer;
import com.anormal.client.module.impl.optimization.FogCull;
import com.anormal.client.module.impl.optimization.SmartCulling;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Central entity-culling hook shared by {@code FogCull}, {@code SmartCulling}
 * and {@code EntityLodOptimizer}.
 *
 * <p>{@code shouldRender} is asked once per entity per frame, before anything is
 * pushed to the render queue - returning {@code false} here is the cheapest
 * possible rejection.</p>
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$shouldRender(Entity entity, Frustum frustum, double x, double y, double z,
                                      CallbackInfoReturnable<Boolean> cir) {
        try {
            if (entity == null) return;
            if (FogCull.shouldCull(entity)
                    || SmartCulling.shouldCull(entity)
                    || EntityLodOptimizer.shouldHide(entity)) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
        }
    }
}
