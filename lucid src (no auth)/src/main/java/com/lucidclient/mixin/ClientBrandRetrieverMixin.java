package com.lucidclient.mixin;

import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Brand spoof — the client brand string is the FIRST thing Bukkit/Paper
 * anticheat plugins read about a client ({@code player.getClientBrandName()}
 * on the server, fed by the brand custom payload sent during login).
 * Fabric's loader rebrands it to "fabric", which aggressive anticheats
 * (APPLEMC's in-testing one, for example) treat as a cheat-client
 * fingerprint and kick as "Unfair Advantage" — even when the player is
 * doing nothing. This reports the vanilla brand instead.
 */
@Mixin(ClientBrandRetriever.class)
public abstract class ClientBrandRetrieverMixin {

    @Inject(method = "getClientModName()Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private static void lucid$vanillaBrand(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("vanilla");
    }
}
