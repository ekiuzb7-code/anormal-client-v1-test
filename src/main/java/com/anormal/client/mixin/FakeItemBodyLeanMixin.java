package com.anormal.client.mixin;

import com.anormal.client.lucid.LucidLog;
import com.anormal.client.module.impl.misc.FakeItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fake Item (rebuilt elytra pose system): the BODY half of the cosmetic
 * glide for the local player.
 *
 * <p>Vanilla's glide body-lean lives in {@code PlayerEntityRenderer.setupTransforms}:
 * when {@code state.isGliding} is true it rotates the model by
 * {@code getGlidingProgress() * (-90° - pitch)} where
 * {@code getGlidingProgress() = clamp(glidingTicks² / 100)}. On DonutSMP the
 * server never starts a glide for a client with no real elytra, so both the
 * flag and the tick counter stay zero and the lean never fires on its own.
 * Our cosmetic lean additionally caps the slope via
 * {@link FakeItem#bodyLeanDeg} so the pose never tips into a full flat
 * nose-dive.
 *
 * <p>This hook runs at the TAIL of {@code updateGliding} (the LAST pass of
 * the player's render-state chain), and when {@link FakeItem#getGlideProgress}
 * is engaged it writes exactly what vanilla's own glide would have produced:
 * {@code isGliding = true} and {@code glidingTicks = 10·√progress} (so the
 * quadratic lean yields precisely our smoothed progress), plus the limb-swing
 * suppression vanilla applies in its gliding branch. Wing angles are NOT
 * touched here — {@link ElytraEntityModelMixin} owns the wings at draw time.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class FakeItemBodyLeanMixin {

    @Inject(method = "updateGliding", at = @At("TAIL"))
    private void anormal$forceGlideLean(PlayerLikeEntity entity,
                                      PlayerEntityRenderState state,
                                      float partial, CallbackInfo ci) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;
            if ((Object) entity != (Object) client.player) return;  // local player only

// Advance the pose animations per RENDER FRAME (this hook runs
            // every frame the local player's render state refreshes) so the
            // open/close animate at full frame rate �?" smooth 60fps �?" rather
            // than 20Hz tick steps. The body lean follows the cosmetic glide;
            // the WINGS run their own vanilla controller per TICK inside
            // FakeItem.stepWingController (see onTickAlways).
            boolean poseAirborne = false;
            try {
                poseAirborne = !entity.isOnGround()
                    && !entity.isTouchingWater()
                    && !entity.isClimbing();
            } catch (Throwable ignored) {}
            FakeItem.stepGlidePose(FakeItem.isCosmeticGliding() && poseAirborne);

            // GROUND-TRUTH TELEMETRY (both environments): raw entity flag +
            // tick counter vs our state writes. Throttled to once per 600
            // frames (~30s) so long sessions can't drown the [FakeItem-DBG]
            // container trace in the log.
            if (debugLeanCnt < 10 || debugLeanCnt % 600 == 0) {
                debugLeanCnt++;
                int rawTicks = 0;
                boolean rawGliding = false;
                try {
                    rawTicks = entity.getGlidingTicks();
                    rawGliding = ((net.minecraft.entity.LivingEntity) entity).isGliding();
                } catch (Throwable ignored) {}
                FakeItem.dbgLight("player-glide",
                    "cnt=" + debugLeanCnt
                    + " raw.isGliding=" + rawGliding
                    + " raw.ticks=" + rawTicks
                    + " state.isGliding=" + state.isGliding
                    + " state.ticks=" + String.format("%.1f", state.glidingTicks)
                    + " lean=" + String.format("%.2f", state.leaningPitch)
                    + " progress=" + String.format("%.2f", FakeItem.getGlideProgress()));
            } else {
                debugLeanCnt++;
            }
            if (!FakeItem.isActive()) return;
            if (!FakeItem.isDedicatedServer()) return;
            if (!FakeItem.isFakeElytra()) return;

            float progress = FakeItem.getGlideProgress();
            // Keep driving the lean to (nearly) zero so the body eases back
            // continuously at the end of the fold instead of snapping off at a
            // readable threshold. At progress ~0 the glidingTicks back-compute
            // yields a ~0 lean, which is indistinguishable from vanilla idle.
            if (progress <= 0.001f) return;  // fully folded — leave vanilla state

            state.isGliding = true;
            // Vanilla's lean = getGlidingProgress()·(-90° - pitch); we drive
            // our own gentler lean via bodyLeanDeg (45° base, deepens only
            // 0.5°/° of downward aim, capped at 75°) and back-compute ticks so
            // vanilla's own clamp(ticks²/100) yields exactly that lean through
            // its standard transform path. The head compensation
            // (PlayerEntityModelMixin) uses the SAME bodyLeanDeg, keeping head
            // and body in lock-step.
            float pitchDeg = state.pitch;
            float denom = -90.0f - pitchDeg;
            float desired = FakeItem.bodyLeanDeg(progress, pitchDeg);
            float effective = Math.abs(denom) < 1.0f
                ? progress
                : Math.min(1.0f, Math.max(0.0f, desired / denom));
            state.glidingTicks = 10.0f * (float) Math.sqrt(effective);
            // Freeze the walk-cycle limb swing while gliding. Use a very high
            // amplitude inverse to kill the rhythmic bobbing of the head/limbs.
            state.limbAmplitudeInverse = 100.0f;
        } catch (Throwable t) {
            LucidLog.LOGGER.error("Lucid PlayerEntityRendererMixin threw", t);
        }
    }

    private static volatile int debugLeanCnt = 0;
}
