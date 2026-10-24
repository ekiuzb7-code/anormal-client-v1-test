package dev.zaza.client.mixin.input;

import dev.zaza.client.rotation.ZazaRotations;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void zaza$turned(double seconds, CallbackInfo info) {
        ZazaRotations.turn(net.minecraft.client.Minecraft.getInstance(), seconds);
    }
}
