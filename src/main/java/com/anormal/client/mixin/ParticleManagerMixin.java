package com.anormal.client.mixin;

import com.anormal.client.module.impl.optimization.NoRender;
import com.anormal.client.module.impl.optimization.ParticleLimit;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Single entry point for every particle: {@code NoRender} decides which kinds
 * to drop and {@code ParticleLimit} enforces the global cap.
 *
 * <p>Cancelling here means the particle is never even allocated, which is much
 * cheaper than creating it and hiding it later.</p>
 */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$addParticle(Particle particle, CallbackInfo ci) {
        try {
            if (NoRender.shouldHideParticle(particle) || ParticleLimit.shouldSkip(particle)) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}
