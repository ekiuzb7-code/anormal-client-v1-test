package com.lucidclient.module.impl;

import com.lucidclient.module.Category;
import com.lucidclient.module.Module;
import com.lucidclient.module.setting.BooleanSetting;
import com.lucidclient.module.setting.FloatSetting;
import com.lucidclient.module.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Hitboxes — expands the client-side hitbox of nearby entities so they're
 * easier to click and the expansion is visible (F3+B shows it).
 *
 * ── How it works ──────────────────────────────────────────────────
 * Each client tick (while enabled) we walk the entities within
 * {@link #maxDistance} of the player and, for each one that passes the
 * filters, replace its bounding box with the default box grown symmetrically
 * by {@link #expand} blocks on all six faces. We set it via the public
 * {@link Entity#setBoundingBox(Box)} method — the same field F3+B, the
 * crosshair entity raycast, and any other reader of
 * {@link Entity#getBoundingBox()} consult. So the expansion is both
 * <b>visible</b> (F3+B grows) and <b>effective</b> (the crosshair locks on
 * even when you're off the entity's shoulder).
 *
 * ── Restoring on disable / out-of-range ───────────────────────────
 * Every entity we touch is remembered in {@link #tracked} so we can restore
 * its default box the instant it leaves range, the module is disabled, or
 * the entity is unloaded. The default box is recomputed fresh each tick from
 * {@code entity.getDimensions(entity.getPose()).getBoxAt(pos)} so it stays
 * correct even if the entity changed pose (crouching, swimming, etc.)
 * between when we first expanded it and when we restore it. We never persist
 * a stale default — we always recompute it.
 *
 * ── Why this is safe ───────────────────────────────────────────────
 * {@code setBoundingBox} only changes the field vanilla reads for rendering
 * the debug box and for broadphase queries (the crosshair raycast). It does
 * NOT change the entity's {@link EntityDimensions} or its collision shape —
 * vanilla's own movement code re-derives the collision shape from the
 * dimensions, not from this field, so the entity still collides normally
 * with the world and with you. (The field IS used for entity-vs-entity
 * broadphase in some paths, which is exactly the aim acceptance we want to
 * widen.) This is a client-side-only visual + targeting convenience in the
 * same category as AimAssist — it does not forge reach or bypass the
 * server's hitbox validation.
 *
 * ── Filter flags ───────────────────────────────────────────────────
 * Players/Mobs/Hostiles Only are union-style: if several are on, an entity
 * passes if it matches ANY enabled filter. With all three off (default),
 * every eligible entity is expanded. Self is always excluded.
 *
 * Settings:
 *   Toggle Key, Expand, Max Distance, Players Only, Mobs Only, Hostiles Only,
 *   Ignore Invisible.
 */
public class Hitboxes extends Module {

    private final KeybindSetting toggleKey = addSetting(
        new KeybindSetting("Toggle Key", "Press this key to enable/disable hitbox expansion. Click here to rebind.",
            GLFW.GLFW_KEY_UNKNOWN));
    private final FloatSetting expand = addSetting(
        new FloatSetting("Expand", "Blocks to grow each face of the target's hitbox outward (0.3 = +0.3 each side).",
            0f, 2f, 0.3f, 2));
    private final FloatSetting maxDistance = addSetting(
        new FloatSetting("Max Distance", "Only expand entities within this many blocks of the player.",
            1f, 64f, 6f, 1));
    private final BooleanSetting playersOnly = addSetting(
        new BooleanSetting("Players Only", "Only expand other players (PvP).",
            false));
    private final BooleanSetting mobsOnly = addSetting(
        new BooleanSetting("Mobs Only", "Only expand non-player entities (PvE).",
            false));
    private final BooleanSetting hostilesOnly = addSetting(
        new BooleanSetting("Hostiles Only", "Only expand hostile monsters (zombies, skeletons, etc.).",
            false));
    private final BooleanSetting ignoreInvisible = addSetting(
        new BooleanSetting("Ignore Invisible", "Don't expand invisible entities.",
            true));

    /** Toggle key edge detection (matches AutoClicker's onTickAlways pattern). */
    private boolean wasKeyDown = false;

    /**
     * Entities we currently have an expanded box on, so we can restore them
     * when they leave range / the module is disabled / they're unloaded.
     * Identity-based equality is correct here (we track specific instances).
     */
    private final List<Entity> tracked = new ArrayList<>();

    public Hitboxes() {
        super("Hitboxes",
            "Expands the client-side hitbox of nearby entities so they're easier to click. Visible with F3+B.",
            Category.RENDER);
    }

    @Override
    protected void onEnable() {
        wasKeyDown = false;
        tracked.clear();
    }

    @Override
    protected void onDisable() {
        // Restore every tracked entity to its real (default) box before going
        // dark. Recompute the default fresh so pose changes since we touched it
        // don't leave a stale box.
        for (Entity e : tracked) {
            restoreDefaultBox(e);
        }
        tracked.clear();
    }

    /**
     * onTickAlways — toggle key works even when the module is disabled,
     * matching AutoClicker's pattern.
     */
    @Override
    public void onTickAlways(MinecraftClient client) {
        if (client.player == null || client.getWindow() == null) return;
        int key = toggleKey.getKey();
        if (key < 0) return;

        long handle = client.getWindow().getHandle();
        boolean isDown;
        if (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_8) {
            isDown = GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS;
        } else {
            isDown = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        }

        if (isDown && !wasKeyDown) {
            setEnabled(!isEnabled());
        }
        wasKeyDown = isDown;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        float e = expand.get();
        double maxDist = maxDistance.get();
        double maxDistSq = maxDist * maxDist;

        // ── 1. Apply expansion to all currently-eligible entities ───────
        // Iterate nearby entities via the same getOtherEntities pattern used
        // by AimAssist / EntityESP. We re-expand every tick so movement /
        // pose changes keep the box centred on the entity's current position.
        Box search = new Box(
            client.player.getX() - maxDist, client.player.getY() - maxDist, client.player.getZ() - maxDist,
            client.player.getX() + maxDist, client.player.getY() + maxDist, client.player.getZ() + maxDist);

        List<Entity> nearby = client.world.getOtherEntities(client.player, search, ent -> true);

        for (Entity candidate : nearby) {
            if (!shouldExpand(candidate, client.player, maxDistSq)) continue;
            Box grown = defaultBox(candidate).expand(e);
            candidate.setBoundingBox(grown);
            if (!tracked.contains(candidate)) tracked.add(candidate);
        }

        // ── 2. Restore entities that have left range / failed filters ──
        // (so the box shrinks back to vanilla the moment you walk away or
        // toggle a filter that now excludes them). Iterate a copy so we can
        // remove from the live list safely.
        for (Entity t : new ArrayList<>(tracked)) {
            if (t.isRemoved() || !t.isAlive()
                || !shouldExpand(t, client.player, maxDistSq)) {
                restoreDefaultBox(t);
                tracked.remove(t);
            }
        }
    }

    /**
     * Decide whether the given candidate should currently have an expanded
     * box. Applies all filters (self-exclusion, alive/removed, invisible,
     * distance, Players/Mobs/Hostiles).
     */
    private boolean shouldExpand(Entity candidate, net.minecraft.entity.player.PlayerEntity self, double maxDistSq) {
        if (candidate == self) return false;
        if (!candidate.isAlive() || candidate.isRemoved()) return false;
        if (ignoreInvisible.isEnabled() && candidate.isInvisible()) return false;
        if (self.squaredDistanceTo(candidate) > maxDistSq) return false;

        boolean playersOnly = this.playersOnly.isEnabled();
        boolean mobsOnly = this.mobsOnly.isEnabled();
        boolean hostilesOnly = this.hostilesOnly.isEnabled();
        if (playersOnly || mobsOnly || hostilesOnly) {
            boolean isPlayer = candidate instanceof PlayerEntity;
            boolean isMonster = candidate instanceof Monster && !isPlayer;
            boolean passes = false;
            if (playersOnly && isPlayer) passes = true;
            if (mobsOnly && !isPlayer) passes = true;
            if (hostilesOnly && isMonster) passes = true;
            if (!passes) return false;
        }
        return true;
    }

    /**
     * The entity's real (un-expanded) box, recomputed from its current
     * dimensions and pose. We never persist a stale default — always derive
     * it fresh so pose changes (crouch/swim) are respected on restore.
     */
    private static Box defaultBox(Entity entity) {
        Vec3d pos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        return entity.getDimensions(entity.getPose()).getBoxAt(pos);
    }

    /** Restore {@code entity} to its real box, guarded against being unloaded. */
    private static void restoreDefaultBox(Entity entity) {
        if (entity == null || entity.isRemoved()) return;
        try {
            entity.setBoundingBox(defaultBox(entity));
        } catch (Throwable ignored) {
            // Entity may have become invalid between the check and the call.
        }
    }
}