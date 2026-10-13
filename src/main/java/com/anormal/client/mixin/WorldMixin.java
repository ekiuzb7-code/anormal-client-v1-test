package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.NoWeather;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the NoWeather module the same way Wurst does - by overriding the
 * vanilla getters rather than faking anything in the renderer.
 *
 * <p>Guarded with a {@link ClientWorld} check so the integrated server keeps
 * simulating real weather and real time; only the client's view is changed.</p>
 */
@Mixin(World.class)
public class WorldMixin {

    private boolean anormal$isClientWorld() {
        return (Object) this instanceof ClientWorld;
    }

    /** Wurst: {@code isRainDisabled()} -> rain gradient 0. */
    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$getRainGradient(float delta, CallbackInfoReturnable<Float> cir) {
        try {
            if (!anormal$isClientWorld()) return;
            NoWeather noWeather = ModuleManager.getModule(NoWeather.class);
            if (noWeather != null && noWeather.isRainDisabled()) {
                cir.setReturnValue(0.0f);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Wurst: {@code isTimeChanged()} -> forced time of day. */
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true, require = 0)
    private void anormal$getTimeOfDay(CallbackInfoReturnable<Long> cir) {
        try {
            if (!anormal$isClientWorld()) return;
            NoWeather noWeather = ModuleManager.getModule(NoWeather.class);
            if (noWeather != null && noWeather.isTimeChanged()) {
                cir.setReturnValue(noWeather.getChangedTime());
            }
        } catch (Throwable ignored) {
        }
    }
}
