package com.lucidclient.mixin;

import com.lucidclient.LucidClient;
import com.lucidclient.freecam.FlightMode;
import com.lucidclient.module.impl.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Freecam (Freecam-mod implementation) LivingEntity hooks:
 *
 *   - CREATIVE flight mode: the FreeCamera's horizontal speed is configured
 *     separately from its vertical speed.
 *   - Disable on Damage: turns freecam off when the real player takes
 *     damage (health decreased) while not in creative mode.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    public abstract float getHealth();

    @Inject(method = "getOffGroundSpeed", at = @At("HEAD"), cancellable = true)
    private void lucid$freecamCreativeSpeed(CallbackInfoReturnable<Float> cir) {
        try {
            if (Freecam.isActive() && Freecam.instance() != null
                    && Freecam.instance().flightMode() == FlightMode.CREATIVE
                    && (Object) this == Freecam.getFreeCamera()) {
                cir.setReturnValue((float) (Freecam.instance().horizontalSpeed() / 10)
                        * (Freecam.getFreeCamera().isSprinting() ? 2 : 1));
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid LivingEntityMixin.freecamCreativeSpeed threw", t);
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"))
    private void lucid$freecamDamage(float health, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (Freecam.isActive() && Freecam.shouldDisableOnDamage() && (Object) this == mc.player && mc.player != null) {
                if (!mc.player.isCreative() && getHealth() > health) {
                    Freecam.disableNextTick();
                }
            }
        } catch (Throwable t) {
            LucidClient.LOGGER.error("Lucid LivingEntityMixin.freecamDamage threw", t);
        }
    }
}
