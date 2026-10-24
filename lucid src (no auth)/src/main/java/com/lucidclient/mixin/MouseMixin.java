package com.lucidclient.mixin;

import com.lucidclient.module.impl.Freecam;
import com.lucidclient.module.impl.Freelook;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin that routes mouse look-delta into the Freelook camera angles
 * instead of the player's body, so the body never turns while freelook
 * is active (glazed's freelook mechanism cancels {@code Entity.turn}
 * at HEAD — lucid intercepts the single call site instead).
 *
 * ── Look delta redirect ──────────────────────────────────────────
 * {@link Mouse#updateMouse} applies the accumulated cursor delta by calling
 * {@code client.player.changeLookDirection(double, double)} — which mutates
 * the player's {@code yaw}/{@code pitch}/{@code lastYaw}/{@code lastPitch}.
 * We {@code @Redirect} that single call:
 *
 *   - Freelook active  → the delta drives Freelook's camera angles (glazed's
 *                        updateRotation): the body keeps facing its frozen
 *                        direction, so the move packets keep reporting the
 *                        frozen rotation and no packet suppression is
 *                        needed; only the camera orbits.
 *   - otherwise        → vanilla {@code changeLookDirection} runs as normal
 *                        (Freecam manages its own look input).
 *
 * ── Argument order ───────────────────────────────────────────────
 * {@code Entity.changeLookDirection}'s bytecode (confirmed by decompilation)
 * is:
 *
 *   public void changeLookDirection(double a, double b) {
 *       float pitchDelta = (float) b * 0.15f;   // dload_3 (the SECOND arg)
 *       float yawDelta   = (float) a * 0.15f;   // dload_1 (the FIRST  arg)
 *       setPitch(getPitch() + pitchDelta);
 *       setYaw(getYaw() + yawDelta);
 *       ...
 *   }
 *
 * So the signature is {@code changeLookDirection(yawDeltaRaw, pitchDeltaRaw)}
 * — YAW first, PITCH second. {@code Mouse.updateMouse} passes
 * {@code (cursorDeltaX, cursorDeltaY)} into it, which matches: horizontal
 * cursor motion = yaw, vertical cursor motion = pitch. We forward the args
 * to {@link Freelook#applyMouseDelta} in that same order.
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Redirect(
        method = "updateMouse",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void lucid$freelookRedirect(ClientPlayerEntity player,
                                        double yawDeltaRaw, double pitchDeltaRaw) {
        if (Freelook.isActive()) {
            // Freelook (glazed mechanism): delta drives the camera angles,
            // the body keeps facing its frozen direction.
            // Args are (yaw, pitch) — see the class javadoc for the proof.
            Freelook.applyMouseDelta(yawDeltaRaw, pitchDeltaRaw);
        } else if (Freecam.isActive() && Freecam.getFreeCamera() != null
                && !Freecam.isPlayerControlEnabled()) {
            // Freecam (Freecam-mod implementation): the delta rotates the
            // detached FreeCamera entity, not the frozen player body.
            Freecam.getFreeCamera().changeLookDirection(yawDeltaRaw, pitchDeltaRaw);
        } else {
            // Vanilla look.
            player.changeLookDirection(yawDeltaRaw, pitchDeltaRaw);
        }
    }
}

