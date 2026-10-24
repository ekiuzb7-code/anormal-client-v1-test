package dev.zaza.client.module.impl;

import dev.zaza.ZazaHook;
import dev.zaza.client.module.Category;
import dev.zaza.client.module.Module;
import dev.zaza.client.rotation.Bone;
import dev.zaza.client.rotation.Rotation;
import dev.zaza.client.rotation.RotationConfig;
import dev.zaza.client.rotation.RotationTarget;
import dev.zaza.client.rotation.Smoothing;
import dev.zaza.client.rotation.ZazaRotations;
import dev.zaza.client.setting.BoolSetting;
import dev.zaza.client.setting.EnumSetting;
import dev.zaza.client.setting.Measure;
import dev.zaza.client.setting.MultiSetting;
import dev.zaza.client.setting.SliderSetting;
import dev.zaza.client.setting.Setting;
import dev.zaza.client.util.Maths;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Aim assist, ported from Cold Client's combat module. Tracks the best target
 * inside the FOV cone and rotates toward the selected bone, either visibly
 * (camera) or silently (outgoing rotations only).
 */
public final class CombatModule extends Module {
    private static final String[] MODES = {"Regular", "Silent"};
    private static final int SILENT = 1;
    private static final String[] PRIORITIES = {"Closest to FOV", "Lowest health"};
    private static final int LOWEST_HEALTH = 1;
    private static final double RANGE = 6.0;

    private record Aim(LocalPlayer player, LivingEntity entity, Bone bone) implements RotationTarget {
        @Override
        public Rotation at(float partialTick) {
            return Rotation.toward(player.getEyePosition(partialTick), bone.point(player, entity, partialTick));
        }
    }

    public final SliderSetting fov = new SliderSetting("fov", 80, 10, 360, Measure.DEGREES);
    public final EnumSetting mode = new EnumSetting("mode", 0, MODES);
    public final EnumSetting rotation = new EnumSetting("rotation", 0, Smoothing.NAMES);
    public final SliderSetting smoothness = new SliderSetting("smoothness", 50, 0, 100, Measure.PERCENT);
    public final BoolSetting onHold = new BoolSetting("on_hold", false);
    public final BoolSetting weaponsOnly = new BoolSetting("weapons_only", false);
    public final EnumSetting target = new EnumSetting("target", 0, PRIORITIES);
    public final MultiSetting bones = new MultiSetting("bones", Bone.NAMES, Bone.HEAD.ordinal());

    private boolean targeting;
    private LivingEntity current;
    private long lastIdleLog;

    public CombatModule(String name, String id) {
        super(name, id, Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        ZazaHook.LOGGER.info("[zaza] aim assist enabled");
    }

    @Override
    protected void onDisable() {
        targeting = false;
        current = null;
        ZazaHook.LOGGER.info("[zaza] aim assist disabled");
    }

    public boolean targeting() {
        return enabled() && targeting;
    }

    public LivingEntity target() {
        return enabled() ? current : null;
    }

    @Override
    public void tick() {
        boolean wasTargeting = targeting;
        targeting = false;
        current = null;
        LocalPlayer player = mc().player;
        if (player == null) {
            logLost(wasTargeting);
            return;
        }
        if (onHold.get() && !mc().options.keyAttack.isDown()) {
            logLost(wasTargeting);
            return;
        }
        Aim aim = aim(player);
        if (aim == null) {
            logLost(wasTargeting);
            logIdle(player);
            return;
        }
        targeting = true;
        current = aim.entity();
        if (!wasTargeting) {
            ZazaHook.LOGGER.info("[zaza] aim assist tracking {} (bone {}, fov {}deg, mode {})",
                    current.getName().getString(), aim.bone(), fov.get(), mode.selected());
        }
        Smoothing smoothing = Smoothing.values()[rotation.get()];
        RotationConfig config = mode.get() == SILENT
                ? RotationConfig.silent(smoothness.get() / 100f, smoothing)
                : RotationConfig.visible(smoothness.get() / 100f, smoothing);
        ZazaRotations.manager().aim(aim, 0, config);
    }

    private Aim aim(LocalPlayer player) {
        if (!playing()) return null;
        if (weaponsOnly.get() && !holdsWeapon(player)) return null;
        Aim best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RANGE))) {
            if (!targeted(player, entity)) continue;
            Bone closest = null;
            double closestAngle = fov.get() * 0.5;
            for (Bone bone : Bone.ALL) {
                if (!bones.has(bone.ordinal())) continue;
                double angle = Maths.angle(player.getLookAngle(),
                        bone.point(player, entity, 1f).subtract(player.getEyePosition()));
                if (angle >= closestAngle) continue;
                closest = bone;
                closestAngle = angle;
            }
            if (closest == null) continue;
            double score = target.get() == LOWEST_HEALTH ? entity.getHealth() : closestAngle;
            if (score >= bestScore) continue;
            best = new Aim(player, entity, closest);
            bestScore = score;
        }
        return best;
    }

    private static void logLost(boolean wasTargeting) {
        if (wasTargeting) ZazaHook.LOGGER.info("[zaza] aim assist lost target");
    }

    /** Throttled: says why no target is being tracked so the log is self-explanatory. */
    private void logIdle(LocalPlayer player) {
        long now = System.currentTimeMillis();
        if (now - lastIdleLog < 2000L) return;
        lastIdleLog = now;
        if (!playing()) {
            ZazaHook.LOGGER.info("[zaza] aim assist idle: no game focus (screen open or mouse not grabbed)");
            return;
        }
        if (weaponsOnly.get() && !holdsWeapon(player)) {
            ZazaHook.LOGGER.info("[zaza] aim assist idle: not holding a weapon");
            return;
        }
        int candidates = 0;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RANGE))) {
            if (!targeted(player, entity)) continue;
            candidates++;
            for (Bone bone : Bone.ALL) {
                if (!bones.has(bone.ordinal())) continue;
                nearest = Math.min(nearest, Maths.angle(player.getLookAngle(),
                        bone.point(player, entity, 1f).subtract(player.getEyePosition())));
            }
        }
        if (candidates == 0) {
            ZazaHook.LOGGER.info("[zaza] aim assist idle: no candidates within {} blocks", RANGE);
        } else {
            ZazaHook.LOGGER.info("[zaza] aim assist idle: {} candidate(s), nearest {}deg vs cone +/-{}deg",
                    candidates, (int) nearest, fov.get() * 0.5);
        }
    }

    private boolean targeted(LocalPlayer player, LivingEntity entity) {
        if (entity == player || !entity.isAlive() || entity.isSpectator()) return false;
        return player.distanceToSqr(entity) <= RANGE * RANGE && player.hasLineOfSight(entity);
    }

    private boolean holdsWeapon(LocalPlayer player) {
        return player.getMainHandItem().has(DataComponents.WEAPON);
    }

    private boolean playing() {
        var client = mc();
        return client.player != null && client.gui.screen() == null && client.mouseHandler.isMouseGrabbed();
    }

    /** Setting definitions rendered by the GUI settings pane. */
    public List<Setting<?>> settings() {
        return List.of(fov, mode, rotation, smoothness, onHold, weaponsOnly, target, bones);
    }
}
