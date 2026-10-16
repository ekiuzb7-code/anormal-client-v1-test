package com.anormal.client.mixin;

import com.anormal.client.util.InputForcer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-applies keys that modules force down (Auto Walk in Tunnel, etc.).
 *
 * <p>{@code KeyBinding.updatePressedStates()} rebuilds every binding from the
 * physical hardware state, so a fake press would be wiped before the player's
 * movement is processed. Re-pressing at the end of that refresh makes forced
 * keys behave exactly like real ones.</p>
 */
@Mixin(KeyBinding.class)
public class KeyBindingMixin {

    @Inject(method = "updatePressedStates", at = @At("RETURN"), require = 0)
    private static void anormal$reapplyForcedKeys(CallbackInfo ci) {
        InputForcer.apply(MinecraftClient.getInstance());
    }
}
