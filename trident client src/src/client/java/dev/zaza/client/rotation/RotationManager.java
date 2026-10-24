package dev.zaza.client.rotation;

import dev.zaza.client.util.Maths;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from Cold Client's rotation system, adapted to zaza.hook's direct
 * architecture (no event bus, no Lombok, no movement-fix module). Silent mode
 * rotates the packets sent to the server while the camera stays free; visible
 * mode drives the camera with humanized smoothing.
 */
public final class RotationManager {
    private static final float TURN_FACTOR = 0.15f;

    private record Request(RotationTarget target, RotationConfig config) {}

    private Request request;
    private Request visible;
    private Rotation current;
    private Rotation previous;
    private RotationConfig config;
    private Smoothing smoothing;
    private Rotator rotator;
    private Rotation lastSent = new Rotation(0f, 0f);

    /** Queue an aim request for this tick; highest priority wins. */
    public void aim(RotationTarget target, int priority, RotationConfig config) {
        if (request == null || priority > requestPriority) {
            request = new Request(target, config);
            requestPriority = priority;
        }
    }

    private int requestPriority;

    public void aim(Rotation target, int priority, RotationConfig config) {
        aim(RotationTarget.fixed(target), priority, config);
    }

    public boolean active() {
        return current != null;
    }

    public Rotation rotation(LocalPlayer player) {
        return current != null ? current : Rotation.of(player);
    }

    /** Camera rotation used for rendering while a silent rotation is active. */
    public Rotation rendered(float partialTick) {
        if (current == null) return null;
        Rotation from = previous != null ? previous : current;
        return new Rotation(Mth.rotLerp(partialTick, from.yaw(), current.yaw()),
                Mth.lerp(partialTick, from.pitch(), current.pitch()));
    }

    public float sentYaw(float actual) {
        return current != null ? current.yaw() : actual;
    }

    public float sentPitch(float actual) {
        return current != null ? current.pitch() : actual;
    }

    public void sent(Rotation rotation) {
        lastSent = rotation;
    }

    public Rotation lastSent() {
        return lastSent;
    }

    public Vec3 viewVector(Entity entity) {
        return current != null && entity == Minecraft.getInstance().player ? current.direction() : null;
    }

    /** Silent mode keeps movement relative to the real camera for vanilla feel. */
    public Vec2 correctInput(Vec2 input, float actualYaw) {
        return input;
    }

    /**
     * Re-anchor the rotator after a server rotation correction so the next
     * step starts from where the server says we are looking.
     */
    public void sync(Rotation rotation) {
        lastSent = rotation;
        if (current == null) return;
        current = rotation;
        previous = rotation;
        rotator = null;
    }

    public void tick(Minecraft client) {
        LocalPlayer player = client.player;
        Request request = this.request;
        this.request = null;
        requestPriority = 0;
        visible = null;
        previous = current;
        if (player == null) {
            current = null;
            previous = null;
            rotator = null;
            return;
        }
        Rotation actual = Rotation.of(player);
        Rotation from = current != null ? current : actual;
        if (request != null) {
            config = request.config();
            if (config.silent()) {
                current = quantized(step(from, request.target().at(1f), 1f));
            } else {
                current = null;
                visible = request;
            }
        } else if (current != null) {
            Rotation next = quantized(step(from, actual, 1f));
            current = next.distanceTo(actual) < gcd() ? null : next;
        } else {
            rotator = null;
        }
    }

    /** Called every frame after the mouse has been processed. */
    public void onTurn(Minecraft client, float deltaTicks) {
        if (visible == null) return;
        LocalPlayer player = client.player;
        if (player == null) return;
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Rotation actual = Rotation.of(player);
        Rotation next = step(actual, visible.target().at(partialTick), deltaTicks);
        player.turn(actual.yawTo(next) / TURN_FACTOR, actual.pitchTo(next) / TURN_FACTOR);
    }

    Rotation step(Rotation from, Rotation target, float deltaTicks) {
        if (rotator == null || smoothing != config.smoothing()) {
            smoothing = config.smoothing();
            rotator = smoothing.create();
        }
        return rotator.step(from, target, config, deltaTicks);
    }

    private static float quantized(float value, float base) {
        float gcd = gcd();
        return base + Math.round((value - base) / gcd) * gcd;
    }

    private Rotation quantized(Rotation rotation) {
        return new Rotation(quantized(rotation.yaw(), lastSent.yaw()),
                Mth.clamp(quantized(rotation.pitch(), lastSent.pitch()), -90f, 90f));
    }

    private static float gcd() {
        float factor = (float) (Minecraft.getInstance().options.sensitivity().get() * 0.6 + 0.2);
        return factor * factor * factor * 8f * TURN_FACTOR;
    }
}
