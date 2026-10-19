package com.anormal.client.mixin;

import com.anormal.client.module.impl.client.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Timer hook.
 *
 * <p>{@code beginRenderTick} returns how many client ticks should run for the frame that was just
 * drawn. Scaling that number changes the simulation speed without touching the frame rate.</p>
 */
@Mixin(targets = "net.minecraft.client.render.RenderTickCounter$Dynamic")
public class RenderTickCounterMixin {

    @Inject(method = "beginRenderTick(JZ)I", at = @At("RETURN"), cancellable = true, require = 0)
    private void anormal$timer(long timeMillis, boolean paused, CallbackInfoReturnable<Integer> cir) {
        try {
            float speed = Timer.activeSpeed();
            if (speed == 1.0f) return;
            cir.setReturnValue(Timer.scaleTicks(cir.getReturnValue(), speed));
        } catch (Throwable ignored) {
        }
    }
}
