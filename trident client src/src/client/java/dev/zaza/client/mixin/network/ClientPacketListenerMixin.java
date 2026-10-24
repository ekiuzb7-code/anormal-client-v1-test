package dev.zaza.client.mixin.network;

import dev.zaza.client.rotation.Rotation;
import dev.zaza.client.rotation.ZazaRotations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When the server forcibly corrects our rotation, re-anchor the rotator so the
 * next step starts from the corrected look (Cold Client port, subset).
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void zaza$syncMove(ClientboundPlayerPositionPacket packet, CallbackInfo info) {
        if (!packet.relatives().contains(Relative.Y_ROT) || !packet.relatives().contains(Relative.X_ROT)) zaza$sync();
    }

    @Inject(method = "handleRotatePlayer", at = @At("TAIL"))
    private void zaza$syncRotate(ClientboundPlayerRotationPacket packet, CallbackInfo info) {
        zaza$sync();
    }

    private static void zaza$sync() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) ZazaRotations.sync(Rotation.of(player));
    }
}
