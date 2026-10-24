package com.anormal.client.mixin;

import com.anormal.client.lucid.LucidLog;
import com.anormal.client.module.impl.misc.FakeItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drives the elytra wings for a forged elytra, interpolated by the partial tick. */
@Mixin(ElytraEntityModel.class)
public abstract class FakeItemElytraModelMixin {

    @Shadow
    @Final
    private ModelPart leftWing;

    @Shadow
    @Final
    private ModelPart rightWing;

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
            at = @At("TAIL"), require = 0)
    private void anormal$driveWings(BipedEntityRenderState state, CallbackInfo ci) {
        try {
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;
            if (!FakeItem.isFakeElytraEquipped()) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            float partial = mc == null ? 0.0f : mc.getRenderTickCounter().getTickProgress(true);

            float pitch = MathHelper.lerp(partial, FakeItem.getElytraWingPitchPrev(), FakeItem.getElytraWingPitch());
            float yaw = MathHelper.lerp(partial, FakeItem.getElytraWingYawPrev(), FakeItem.getElytraWingYaw());
            float roll = MathHelper.lerp(partial, FakeItem.getElytraWingRollPrev(), FakeItem.getElytraWingRoll());

            leftWing.pitch = pitch;
            leftWing.yaw = yaw;
            leftWing.roll = roll;
            rightWing.pitch = pitch;
            rightWing.yaw = -yaw;
            rightWing.roll = -roll;
        } catch (Throwable t) {
            LucidLog.LOGGER.error("elytra wing drive threw", t);
        }
    }
}
