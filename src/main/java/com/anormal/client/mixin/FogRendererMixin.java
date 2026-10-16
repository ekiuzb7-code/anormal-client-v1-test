package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.world.NoFog;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Removes fog by rewriting the six fog distances that are uploaded to the GPU.
 *
 * <p>In 1.21.11 fog is described by six floats (environmental start/end,
 * render-distance start/end, sky end, cloud end) which are written into a
 * uniform buffer by {@code FogRenderer.applyFog(ByteBuffer, int, Vector4f,
 * float x6)}. Overriding those arguments is the most direct way to disable fog -
 * far more reliable than cancelling each individual fog modifier.</p>
 *
 * <p>The target method is an <b>instance</b> method, so the handlers are too.
 * Every injector uses {@code require = 0} so a future mapping change degrades to
 * "no effect" instead of crashing the game.</p>
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float anormal$fogSlot0(float original) {
        return adjust(0, original);
    }

    @ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private float anormal$fogSlot1(float original) {
        return adjust(1, original);
    }

    @ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private float anormal$fogSlot2(float original) {
        return adjust(2, original);
    }

    @ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
    private float anormal$fogSlot3(float original) {
        return adjust(3, original);
    }

    @ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, ordinal = 4, require = 0)
    private float anormal$fogSlot4(float original) {
        return adjust(4, original);
    }

    @ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, ordinal = 5, require = 0)
    private float anormal$fogSlot5(float original) {
        return adjust(5, original);
    }

    private static float adjust(int slot, float original) {
        try {
            NoFog noFog = ModuleManager.getModule(NoFog.class);
            if (noFog == null || !noFog.isEnabled()) return original;
            return noFog.adjustFog(slot, original);
        } catch (Throwable ignored) {
            return original;
        }
    }
}
