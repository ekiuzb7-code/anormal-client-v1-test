package dev.zaza.client.mixin.render;

import dev.zaza.client.rotation.Rotation;
import dev.zaza.client.rotation.ZazaRotations;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a silent rotation is held, the local player's first- and third-person
 * body follows the sent rotation (Cold Client EntityRenderStateEvent port).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("RETURN"))
    private void zaza$extracted(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> info) {
        if (ZazaRotations.manager() == null) return;
        Rotation rotation = ZazaRotations.manager().rendered(partialTicks);
        if (rotation == null || entity != net.minecraft.client.Minecraft.getInstance().player) return;
        if (info.getReturnValue() instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
            state.bodyRot = rotation.yaw();
            state.yRot = 0f;
            state.xRot = rotation.pitch();
        }
    }
}
