package com.anormal.client.mixin;

import com.anormal.client.module.impl.render.AntiBlind;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AntiBlind hook.
 *
 * <p>Blindness fog, the darkness overlay, the nausea camera wobble and the slowness FOV change all
 * go through {@code hasStatusEffect}. Reporting {@code false} for the effects the user chose makes
 * the game skip their visuals entirely, without touching any packet.</p>
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(
            method = "hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void anormal$antiBlind(RegistryEntry<StatusEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        if (AntiBlind.shouldHide(effect)) cir.setReturnValue(false);
    }
}
