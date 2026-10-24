package dev.zaza.client.mixin.input;

import dev.zaza.client.ZazaClient;
import dev.zaza.client.module.impl.CombatModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the aim assist is locked onto a target, holding attack tracks rather
 * than mines blocks (Cold Client MinecraftMixin.blocksBreaking port).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public HitResult hitResult;
    @Shadow
    public MultiPlayerGameMode gameMode;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void zaza$startAttack(CallbackInfoReturnable<Boolean> info) {
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) return;
        CombatModule aimAssist = aimAssist();
        if (aimAssist == null || !aimAssist.targeting()) return;
        if (gameMode != null) gameMode.stopDestroyBlock();
        info.setReturnValue(false);
    }

    private static CombatModule aimAssist() {
        return (CombatModule) ZazaClient.MODULES.byId("aimassist");
    }
}
