package com.anormal.client.mixin;

import com.anormal.client.lucid.LucidLog;
import com.anormal.client.module.impl.misc.FakeItem;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blends the gliding head pose back towards the aim instead of snapping.
 *
 * <p>Vanilla's gliding branch pins the head to a fixed -pi/4, which looks frozen. Blending by the
 * smoothed pose progress keeps the head following your aim while the wings fold out.</p>
 */
@Mixin(PlayerEntityModel.class)
public abstract class FakeItemPlayerModelMixin {

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
            at = @At("TAIL"), require = 0)
    private void anormal$blendGlideHead(PlayerEntityRenderState state, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;

            ModelPart head = ((BipedEntityModel<PlayerEntityRenderState>) (Object) this).getHead();
            float aim = state.pitch * 0.017453292f;
            float k = Math.min(1.0f, Math.max(0.0f, FakeItem.getGlideProgress()));
            head.pitch = aim + (-0.7853982f - aim) * k;
        } catch (Throwable t) {
            LucidLog.LOGGER.error("glide head blend threw", t);
        }
    }
}
