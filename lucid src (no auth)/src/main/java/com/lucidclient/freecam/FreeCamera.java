package com.lucidclient.freecam;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * The clientside camera entity used while freecam is active, ported from the
 * Freecam mod (via Argon's Yarn mapping). Spawned into the client world with
 * a fixed negative entity id and made the camera entity by the Freecam
 * module — the real player body stays behind, frozen (optionally hard-frozen
 * by the freecam EntityMixin).
 */
public final class FreeCamera extends AbstractClientPlayerEntity {
    public Input input;
    public float renderPitchBob;
    public float renderYawBob;
    public float lastRenderPitchBob;
    public float lastRenderYawBob;

    public FreeCamera(int id) {
        super(MinecraftClient.getInstance().world, new GameProfile(UUID.randomUUID(), "FreeCamera"));
        setId(id);
        setPose(EntityPose.SWIMMING);
        getAbilities().flying = true;
        getAbilities().allowFlying = true;
        input = new KeyboardInput(MinecraftClient.getInstance().options);
    }

    @Override
    public void tick() {
        // Update input from keyboard directly (the real player's input is
        // blanked while freecam is active).
        updateInputFromKeyboard();

        // Apply motion and update velocity
        doMotion();

        // Update position based on velocity
        updatePosition();

        // Call parent tick for other logic
        super.tick();
    }

    /**
     * Reads keyboard input directly from MinecraftClient options — necessary
     * because the real player's input is blanked while freecam is active.
     */
    private void updateInputFromKeyboard() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options == null) return;

        input = new KeyboardInput(mc.options);
        input.tick();
    }

    /** Applies the velocity set by {@link #doMotion()} to move the camera. */
    private void updatePosition() {
        this.move(MovementType.SELF, this.getVelocity());
    }

    @Override
    public void copyPositionAndRotation(Entity entity) {
        applyPosition(new FreecamPosition(entity));
    }

    public void applyPosition(FreecamPosition position) {
        refreshPositionAndAngles(position.x, position.y, position.z, position.yaw, position.pitch);
        renderPitchBob = getPitch();
        renderYawBob = getYaw();
        lastRenderPitchBob = renderPitchBob;
        lastRenderYawBob = renderYawBob;
    }

    /**
     * Offset the camera per the chosen initial perspective. When
     * {@code checkCollision} is true the offset stops before suffocating
     * inside a block.
     */
    public void applyPerspective(InitialPerspective perspective, boolean checkCollision) {
        FreecamPosition position = new FreecamPosition(this);
        switch (perspective) {
            case INSIDE -> {
            }
            case FIRST_PERSON -> moveForwardUntilCollision(position, 0.4, checkCollision);
            case THIRD_PERSON_MIRROR -> {
                position.mirrorRotation();
                moveForwardUntilCollision(position, -4.0, checkCollision);
            }
            case THIRD_PERSON -> moveForwardUntilCollision(position, -4.0, checkCollision);
        }
    }

    private void moveForwardUntilCollision(FreecamPosition position, double distance, boolean checkCollision) {
        if (!checkCollision) {
            position.moveForward(distance);
            applyPosition(position);
            return;
        }
        boolean negative = distance < 0;
        double maxDistance = negative ? -distance : distance;
        double increment = 0.1;
        for (double travelled = 0.0; travelled < maxDistance; travelled += increment) {
            FreecamPosition old = new FreecamPosition(this);
            position.moveForward(negative ? -increment : increment);
            applyPosition(position);
            if (!wouldNotSuffocateInPose(getPose())) {
                applyPosition(old);
                return;
            }
        }
    }

    public void spawn() {
        ((ClientWorld) getEntityWorld()).addEntity(this);
    }

    public void despawn() {
        ((ClientWorld) getEntityWorld()).removeEntity(getId(), Entity.RemovalReason.DISCARDED);
    }

    // Prevents fall damage sound when FreeCamera touches ground with noClip disabled.
    @Override
    protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
    }

    // Needed for hand swings to be shown in freecam since the player is
    // replaced by FreeCamera in HeldItemRenderer.
    @Override
    public float getHandSwingProgress(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null ? mc.player.getHandSwingProgress(tickDelta) : 0f;
    }

    // Needed for item use animations to be shown in freecam.
    @Override
    public int getItemUseTimeLeft() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null ? mc.player.getItemUseTimeLeft() : 0;
    }

    @Override
    public boolean isUsingItem() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && mc.player.isUsingItem();
    }

    // Prevents slow down from ladders/vines.
    @Override
    public boolean isClimbing() {
        return false;
    }

    // Prevents slow down from water.
    @Override
    public boolean isTouchingWater() {
        return false;
    }

    // Makes night vision apply to FreeCamera when shaders are enabled.
    @Override
    public StatusEffectInstance getStatusEffect(RegistryEntry<StatusEffect> effect) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null ? mc.player.getStatusEffect(effect) : super.getStatusEffect(effect);
    }

    // Prevents pistons from moving FreeCamera when Ignore All Collision is on.
    @Override
    public PistonBehavior getPistonBehavior() {
        return com.lucidclient.module.impl.Freecam.ignoreAllCollision()
                ? PistonBehavior.IGNORE : PistonBehavior.NORMAL;
    }

    // Prevents collision with solid entities (shulkers, boats).
    @Override
    public boolean collidesWith(Entity other) {
        return false;
    }

    // Ensures the FreeCamera is always in the swimming pose (no hitbox friction).
    @Override
    public void setPose(EntityPose pose) {
        super.setPose(EntityPose.SWIMMING);
    }

    // Prevents water submersion sounds from playing.
    @Override
    protected boolean updateWaterSubmersionState() {
        this.submergedInWater = this.isSubmergedIn(FluidTags.WATER);
        return this.submergedInWater;
    }

    @Override
    protected void onSwimmingStart() {
    }

    private void doMotion() {
        com.lucidclient.module.impl.Freecam module = com.lucidclient.module.impl.Freecam.instance();
        if (module == null) return;

        // Always ensure flying state
        getAbilities().flying = true;
        getAbilities().allowFlying = true;
        setOnGround(false);

        switch (module.flightMode()) {
            case DEFAULT -> {
                // Static speed flight mode
                getAbilities().setFlySpeed(0);
                FreecamMotion.doMotion(this, module.horizontalSpeed(), module.verticalSpeed());
            }
            case CREATIVE -> {
                // Creative-style flight with acceleration
                getAbilities().setFlySpeed((float) (module.verticalSpeed() / 10.0));

                if (this.input.playerInput.sneak() ^ this.input.playerInput.jump()) {
                    int direction = this.input.playerInput.jump() ? 1 : -1;
                    Vec3d currentVelocity = this.getVelocity();
                    this.setVelocity(
                        currentVelocity.x,
                        currentVelocity.y + direction * this.getAbilities().getFlySpeed() * 3.0F,
                        currentVelocity.z
                    );
                } else {
                    // Still allow horizontal movement
                    FreecamMotion.doMotion(this, module.horizontalSpeed(), 0);
                }
            }
        }
    }

    @Override
    public float getPitch(float tickProgress) {
        return this.getPitch();
    }

    @Override
    public float getYaw(float tickProgress) {
        return this.getYaw();
    }

    /** Feeds the captured input into LivingEntity's movement simulation. */
    @Override
    protected void tickMovementInput() {
        Vec2f vec2 = this.input.getMovementInput();
        if (vec2.lengthSquared() != 0.0F) {
            vec2 = vec2.multiply(0.98F);
        }
        this.sidewaysSpeed = vec2.x;
        this.forwardSpeed = vec2.y;
        this.jumping = this.input.playerInput.jump();
        MinecraftClient mc = MinecraftClient.getInstance();
        this.setSprinting((mc.options.sprintKey.isPressed() && this.input.playerInput.forward())
                || (this.input.playerInput.forward() && this.isSprinting()));
        this.lastRenderYawBob = this.renderYawBob;
        this.lastRenderPitchBob = this.renderPitchBob;
        this.renderPitchBob = this.renderPitchBob + (this.getPitch() - this.renderPitchBob) * 0.5F;
        this.renderYawBob = this.renderYawBob + (this.getYaw() - this.renderYawBob) * 0.5F;
    }
}
