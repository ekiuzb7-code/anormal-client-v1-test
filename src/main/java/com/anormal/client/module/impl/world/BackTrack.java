package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;

public class BackTrack extends Module {
    public final ModeSetting style = new ModeSetting("Style", "V1 or V2 implementation", "V1", "V1", "V2");

    // --- V1 settings ---
    public final NumberSetting latency = new NumberSetting("Latency", "Target position delay in ms", 100.0, 20.0, 500.0, 10.0);
    public final BooleanSetting renderServerPos = new BooleanSetting("Render Server Pos", "Keep delayed server-side position buffer", true);

    // --- V2 settings ---
    public final NumberSetting v2MinRange = new NumberSetting("V2 Min Range", "Min trigger distance", 1.0, 0.0, 10.0, 0.5);
    public final NumberSetting v2MaxRange = new NumberSetting("V2 Max Range", "Max trigger distance", 3.0, 0.0, 10.0, 0.5);
    public final NumberSetting v2MinDelay = new NumberSetting("V2 Min Delay", "Min position delay (ms)", 100.0, 0.0, 1000.0, 10.0);
    public final NumberSetting v2MaxDelay = new NumberSetting("V2 Max Delay", "Max position delay (ms)", 150.0, 0.0, 1000.0, 10.0);
    public final NumberSetting nextDelay = new NumberSetting("Next Delay", "Pause before next track (ms)", 10.0, 0.0, 2000.0, 10.0);
    public final NumberSetting trackingBuffer = new NumberSetting("Tracking Buffer", "Grace after leaving range (ms)", 500.0, 0.0, 2000.0, 25.0);
    public final NumberSetting chance = new NumberSetting("Chance", "Activation chance per target %", 50.0, 0.0, 100.0, 5.0);
    public final BooleanSetting pauseOnHurt = new BooleanSetting("Pause On Hurt", "Pause while target is hurt", false);
    public final NumberSetting hurtTime = new NumberSetting("Hurt Time", "Target hurt ticks to pause on", 3.0, 0.0, 10.0, 1.0);
    public final ModeSetting targetMode = new ModeSetting("Target Mode", "Attack uses crosshair, Range uses nearest", "Attack", "Attack", "Range");
    public final NumberSetting lastAttackWindow = new NumberSetting("Last Attack", "Track window after attack (ms)", 1000.0, 0.0, 5000.0, 50.0);

    public final ColorSetting color = new ColorSetting("Color", "Server position marker color", 0xFFFF5555);

    private static final class TrackPoint {
        final double x, y, z;
        final long time;
        TrackPoint(double x, double y, double z, long time) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.time = time;
        }
    }

    private final Deque<TrackPoint> buffer = new ArrayDeque<>();
    private int targetId = -1;
    // V2 state
    private Entity v2Target = null;
    private long lastSeenT = 0;
    private long lastAttackT = 0;
    private long nextOkT = 0;
    private boolean chanceOk = false;
    private int currentDelay = 125;

    public BackTrack() {
        super("BackTrack", "Delays target position updates for latency advantage", Category.WORLD);
        addSetting(style);
        addSetting(latency);
        addSetting(renderServerPos);
        addSetting(v2MinRange);
        addSetting(v2MaxRange);
        addSetting(v2MinDelay);
        addSetting(v2MaxDelay);
        addSetting(nextDelay);
        addSetting(trackingBuffer);
        addSetting(chance);
        addSetting(pauseOnHurt);
        addSetting(hurtTime);
        addSetting(targetMode);
        addSetting(lastAttackWindow);
        addSetting(color);
    }

    @Override
    public void onEnable() {
        buffer.clear();
        targetId = -1;
        v2Target = null;
        chanceOk = false;
        nextOkT = 0;
    }

    @Override
    public void onDisable() {
        buffer.clear();
        targetId = -1;
        v2Target = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (style.is("V2")) {
            tickV2();
            return;
        }
        tickV1();
    }

    // ---------- V1: fixed-latency position buffer ----------
    private void tickV1() {
        LivingEntity target = null;
        if (mc.targetedEntity instanceof LivingEntity living && living.isAlive()) {
            target = living;
        } else {
            double best = 6.0;
            for (var e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity l) || l == mc.player || !l.isAlive()) continue;
                double d = mc.player.distanceTo(l);
                if (d < best) {
                    best = d;
                    target = l;
                }
            }
        }
        if (target == null) {
            targetId = -1;
            if (!buffer.isEmpty()) buffer.clear();
            return;
        }
        if (target.getId() != targetId) {
            targetId = target.getId();
            buffer.clear();
        }
        buffer.addLast(new TrackPoint(target.getX(), target.getY(), target.getZ(), System.currentTimeMillis()));
        long window = Math.max(20L, latency.getValue().longValue());
        while (buffer.size() > 2) {
            TrackPoint first = buffer.peekFirst();
            if (first == null) break;
            if (System.currentTimeMillis() - first.time <= window) break;
            buffer.removeFirst();
        }
        if (!renderServerPos.isEnabled()) {
            while (buffer.size() > 5) buffer.removeFirst();
        }
    }

    // ---------- V2: attack/range gated tracking with chance + hurt pause ----------
    private void tickV2() {
        long now = System.currentTimeMillis();
        Entity candidate = null;
        if (targetMode.is("Attack")) {
            if (mc.targetedEntity instanceof LivingEntity living && living.isAlive() && living != mc.player) {
                candidate = living;
            }
        } else {
            double best = Double.MAX_VALUE;
            double maxR = Math.max(v2MinRange.getValue(), v2MaxRange.getValue());
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;
                double d = mc.player.distanceTo(living);
                if (d <= maxR && d < best) {
                    best = d;
                    candidate = living;
                }
            }
        }

        // Attack press on crosshair target refreshes the attack window
        if (candidate != null && mc.options.attackKey.isPressed()
                && mc.targetedEntity == candidate) {
            lastAttackT = now;
        }

        if (candidate == null || (v2Target != null && candidate.getId() != v2Target.getId())) {
            if (candidate != null) {
                // New target: roll chance, reset buffer
                chanceOk = Math.random() * 100.0 < chance.getValue();
                buffer.clear();
                int min = Math.min(v2MinDelay.getValue().intValue(), v2MaxDelay.getValue().intValue());
                int max = Math.max(v2MinDelay.getValue().intValue(), v2MaxDelay.getValue().intValue());
                currentDelay = min + (max > min ? (int) (Math.random() * (max - min + 1)) : 0);
            }
            v2Target = candidate;
        }
        if (v2Target == null || !v2Target.isAlive()) {
            v2Target = null;
            if (!buffer.isEmpty()) buffer.clear();
            return;
        }

        double dist = mc.player.distanceTo(v2Target);
        double minR = Math.min(v2MinRange.getValue(), v2MaxRange.getValue());
        double maxR = Math.max(v2MinRange.getValue(), v2MaxRange.getValue());
        boolean inRange = dist >= minR && dist <= maxR;
        if (inRange) lastSeenT = now;

        boolean paused = pauseOnHurt.isEnabled() && v2Target instanceof LivingEntity living
                && living.hurtTime >= hurtTime.getValue().intValue();
        boolean recentAttack = now - lastAttackT <= Math.max(0L, lastAttackWindow.getValue().longValue());
        boolean active = (inRange || now - lastSeenT <= Math.max(0L, trackingBuffer.getValue().longValue()))
                && chanceOk && !paused && recentAttack && now >= nextOkT;

        if (!active) {
            if (buffer.size() > 5) {
                while (buffer.size() > 5) buffer.removeFirst();
            }
            if (v2Target != null && !inRange && now - lastSeenT > Math.max(0L, trackingBuffer.getValue().longValue())) {
                nextOkT = now + Math.max(0L, nextDelay.getValue().longValue());
                v2Target = null;
                buffer.clear();
            }
            return;
        }

        buffer.addLast(new TrackPoint(v2Target.getX(), v2Target.getY(), v2Target.getZ(), now));
        while (buffer.size() > 2) {
            TrackPoint first = buffer.peekFirst();
            if (first == null) break;
            if (now - first.time <= Math.max(20, currentDelay)) break;
            buffer.removeFirst();
        }
    }

    public Vec3d getDelayedPos() {
        TrackPoint first = buffer.peekFirst();
        if (first == null) return null;
        return new Vec3d(first.x, first.y, first.z);
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null) return;
        if (style.is("V1") && !renderServerPos.isEnabled()) return;
        Vec3d delayed = getDelayedPos();
        if (delayed == null) return;
        int[] s = com.anormal.client.util.ProjectionUtil.project(new Vec3d(delayed.x, delayed.y + 1.0, delayed.z), tickDelta);
        if (s == null) return;
        int col = color.getValue();
        context.fill(s[0] - 4, s[1] - 14, s[0] + 4, s[1] - 13, col);
        context.fill(s[0] - 4, s[1] - 14, s[0] - 3, s[1] - 4, col);
        context.fill(s[0] + 3, s[1] - 14, s[0] + 4, s[1] - 4, col);
        context.fill(s[0] - 4, s[1] - 4, s[0] + 4, s[1] - 3, col);
    }

}
