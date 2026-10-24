package com.anormal.client.mixin;

import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.combat.BackTrackV2;
import com.anormal.client.module.impl.world.AutoFish;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks incoming packets (the netty read callback).
 *
 * <p>BackTrackV2 needs to hold back entity movement packets so the enemy keeps
 * rendering at its past position. The previous implementation hooked a method
 * that does not exist in 1.21.11, so it never ran.</p>
 */
@Mixin(ClientConnection.class)
public class ConnectionMixin {

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void onChannelRead(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        try {
            com.anormal.client.module.impl.combat.FakeLagV2 fakeLag = ModuleManager.getModule(com.anormal.client.module.impl.combat.FakeLagV2.class);
            if (fakeLag != null && fakeLag.isEnabled()) {
                fakeLag.onIncomingPacket(packet);
            }

            BackTrackV2 backTrack = ModuleManager.getModule(BackTrackV2.class);
            if (backTrack != null && backTrack.isEnabled() && backTrack.shouldQueueIncomingPacket(packet)) {
                backTrack.queueIncoming(packet);
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }

        // AutoFish (sound mode): the bobber splash is the bite. Wurst does exactly this and it is
        // more reliable than guessing from the bobber's velocity.
        try {
            if (packet instanceof PlaySoundS2CPacket sound
                    && sound.getSound().value().id().equals(SoundEvents.ENTITY_FISHING_BOBBER_SPLASH.id())) {
                AutoFish autoFish = ModuleManager.getModule(AutoFish.class);
                if (autoFish != null && autoFish.isEnabled()) {
                    autoFish.onBiteSound(sound.getX(), sound.getY(), sound.getZ());
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
