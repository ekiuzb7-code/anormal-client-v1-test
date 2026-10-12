package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.DoubleRangeSetting;
import com.anormal.client.setting.IntRangeSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ProjectionUtil;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Holds back incoming entity-movement packets so the enemy keeps rendering at
 * its past position, making it easier to hit (LiquidBounce-style Backtrack).
 *
 * <p>Settings mirror LiquidBounce's Backtrack module: {@code Range},
 * {@code Delay}, {@code NextBacktrackDelay}, {@code TrackingBuffer},
 * {@code Chance}, {@code PauseOnHurtTime + HurtTime}, {@code TargetMode},
 * {@code LastAttackTimeToWork}, {@code Esp}.</p>
 *
 * <p><b>Fix:</b> the old version queued packets into a manager whose "flush" only
 * dropped them, and the mixin it relied on hooked a method that does not exist in
 * 1.21.11. Held packets are now re-dispatched to the packet listener, and the
 * hook lives on {@code ClientConnection.channelRead0}.</p>
 */
public class BackTrackV2 extends Module {
    public final DoubleRangeSetting range = new DoubleRangeSetting("Range", "Backtrack range in blocks", 1.0, 3.0, 0.0, 10.0, 0.1);
    public final IntRangeSetting delay = new IntRangeSetting("Delay", "Backtrack delay range in ms", 100, 150, 0, 1000);
    public final IntRangeSetting nextBacktrackDelay = new IntRangeSetting("NextBacktrackDelay", "Wait time before the next backtrack in ms", 0, 10, 0, 2000);
    public final NumberSetting trackingBuffer = new NumberSetting("TrackingBuffer", "Keep tracking this long after the enemy leaves range (ms)", 500.0, 0.0, 2000.0, 10.0);
    public final NumberSetting chance = new NumberSetting("Chance", "Chance to backtrack (%)", 50.0, 0.0, 100.0, 1.0);
    public final BooleanSetting pauseOnHurtTime = new BooleanSetting("PauseOnHurtTime", "Pause while the target is on hurt time", false);
    public final NumberSetting hurtTime = new NumberSetting("HurtTime", "Hurt time to pause at", 3.0, 0.0, 10.0, 1.0);
    public final ModeSetting targetMode = new ModeSetting("TargetMode", "How the target is chosen", "Attack", "Attack", "Range");
    public final NumberSetting lastAttackTimeToWork = new NumberSetting("LastAttackTimeToWork", "Keep working this long after an attack (ms)", 1000.0, 0.0, 5000.0, 10.0);
    public final ModeSetting esp = new ModeSetting("Esp", "Render the backtracked position", "Box", "Box", "Wireframe", "None");

    private final Random random = new Random();
    private final BlinkManager blink = new BlinkManager();

    private long chronometer = 0L;
    private long trackingBufferChronometer = 0L;
    private long attackChronometer = 0L;
    private long lastBacktrackEnd = 0L;
    private long currentDelay = 0L;
    private boolean chancePassed = false;
    private boolean attackKeyWasDown = false;

    private Entity target = null;
    private Vec3d trackedPos = null;

    public BackTrackV2() {
        super("BackTrackV2", "LiquidBounce-style Backtrack (holds enemy movement packets)", Category.COMBAT);
        addSetting(range);
        addSetting(delay);
        addSetting(nextBacktrackDelay);
        addSetting(trackingBuffer);
        addSetting(chance);
        addSetting(pauseOnHurtTime);
        addSetting(hurtTime);
        addSetting(targetMode);
        addSetting(lastAttackTimeToWork);
        addSetting(esp);
    }

    @Override
    public void onEnable() {
        clear();
        currentDelay = delay.random();
        chancePassed = random.nextInt(100) < chance.getValue().intValue();
    }

    @Override
    public void onDisable() {
        clear();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();

        // Attack detection (Attack target mode).
        boolean attackDown = mc.options != null && mc.options.attackKey.isPressed();
        if (attackDown && !attackKeyWasDown) {
            if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity living) {
                onAttack(living);
            }
        }
        attackKeyWasDown = attackDown;

        // Target selection.
        if (targetMode.is("Range")) {
            Entity enemy = findEnemy(range.getMax());
            if (enemy != null) target = enemy;
        }

        // Stop holding once the configured delay elapsed, then wait before the next one.
        if (blink.hasQueuedIncoming() && blink.getQueuedTime() >= currentDelay) {
            flushIncoming();
            lastBacktrackEnd = now;
            currentDelay = delay.random();
        }

        if (target == null || !target.isAlive()) {
            if (blink.hasQueuedIncoming()) flushIncoming();
            return;
        }

        if (!shouldBacktrack()) {
            if (blink.hasQueuedIncoming()) flushIncoming();
            return;
        }

        // Remember where we are holding the enemy.
        trackedPos = new Vec3d(target.getX(), target.getY(), target.getZ());
    }

    private Entity findEnemy(double maxRange) {
        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity living && living != mc.player && living.isAlive()) {
                double dist = mc.player.distanceTo(living);
                if (dist <= maxRange && dist < closestDist) {
                    closest = living;
                    closestDist = dist;
                }
            }
        }
        return closest;
    }

    private boolean shouldBacktrack() {
        long now = System.currentTimeMillis();
        if (target == null || !target.isAlive()) return false;
        if (mc.player.age <= 10) return false;
        if (!chancePassed) return false;
        if (pauseOnHurtTime.isEnabled() && target instanceof LivingEntity living
                && living.hurtTime >= hurtTime.getValue().intValue()) return false;

        // Cooldown between two backtracks.
        if (lastBacktrackEnd != 0 && now - lastBacktrackEnd < nextBacktrackDelay.random()) return false;

        double dist = mc.player.distanceTo(target);
        boolean inRange = dist <= range.getMax();
        if (inRange) trackingBufferChronometer = now;
        boolean inBuffer = inRange || (now - trackingBufferChronometer <= trackingBuffer.getValue());

        if (targetMode.is("Attack") && now - attackChronometer > lastAttackTimeToWork.getValue()) {
            return false;
        }
        return inBuffer;
    }

    /** Called from ConnectionMixin. @return true to hold the packet. */
    public boolean shouldQueueIncomingPacket(Packet<?> packet) {
        if (mc.player == null || mc.world == null) return false;
        if (blink.isFlushing()) return false;

        boolean isEntityMove = packet instanceof EntityPositionS2CPacket || packet instanceof EntityS2CPacket;
        if (!isEntityMove) {
            // Position corrections and teleports always end the backtrack.
            if (packet instanceof PlayerPositionLookS2CPacket) flushIncoming();
            return false;
        }

        if (target == null || !shouldBacktrack()) return false;

        int id = entityIdOf(packet);
        return id == -1 || id == target.getId();
    }

    private int entityIdOf(Packet<?> packet) {
        try {
            if (packet instanceof EntityPositionS2CPacket p) return p.entityId();
            if (packet instanceof EntityS2CPacket p) {
                Entity e = p.getEntity(mc.world);
                return e != null ? e.getId() : -1;
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /** Called from NetworkMixin. */
    public void queueIncoming(Packet<?> packet) {
        blink.queueIncoming(packet);
    }

    public void onAttack(Entity enemy) {
        attackChronometer = System.currentTimeMillis();
        chancePassed = random.nextInt(100) < chance.getValue().intValue();
        if (targetMode.is("Attack")) target = enemy;
    }

    /** Re-dispatches every held packet to the network listener. */
    private void flushIncoming() {
        blink.flush(mc);
    }

    private void clear() {
        blink.clear();
        target = null;
        trackedPos = null;
        lastBacktrackEnd = 0L;
    }

    public boolean isLagging() {
        return isEnabled() && blink.hasQueuedIncoming();
    }

    // ---------------------------------------------------------------------
    //  Render
    // ---------------------------------------------------------------------
    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (esp.is("None") || target == null || mc.textRenderer == null) return;
        if (targetMode.is("Attack") && !blink.hasQueuedIncoming()) return;

        try {
            Box box = target.getBoundingBox();
            Vec3d lerp = ProjectionUtil.lerpPos(target, tickDelta);
            double ox = lerp.x - target.getX(), oy = lerp.y - target.getY(), oz = lerp.z - target.getZ();

            double x0 = box.minX + ox, y0 = box.minY + oy, z0 = box.minZ + oz;
            double x1 = box.maxX + ox, y1 = box.maxY + oy, z1 = box.maxZ + oz;

            int[][] c = new int[8][];
            c[0] = ProjectionUtil.project(new Vec3d(x0, y0, z0), tickDelta);
            c[1] = ProjectionUtil.project(new Vec3d(x1, y0, z0), tickDelta);
            c[2] = ProjectionUtil.project(new Vec3d(x1, y0, z1), tickDelta);
            c[3] = ProjectionUtil.project(new Vec3d(x0, y0, z1), tickDelta);
            c[4] = ProjectionUtil.project(new Vec3d(x0, y1, z0), tickDelta);
            c[5] = ProjectionUtil.project(new Vec3d(x1, y1, z0), tickDelta);
            c[6] = ProjectionUtil.project(new Vec3d(x1, y1, z1), tickDelta);
            c[7] = ProjectionUtil.project(new Vec3d(x0, y1, z1), tickDelta);

            int col = 0xFFFF5555;
            int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
            for (int[] e : edges) {
                int[] a = c[e[0]], b = c[e[1]];
                if (a == null || b == null) continue;
                RenderUtils.drawLine(context, a[0], a[1], b[0], b[1], col, 1.0f);
            }
        } catch (Throwable ignored) {}
    }

    // ---------------------------------------------------------------------
    //  Packet queue
    // ---------------------------------------------------------------------
    public static final class BlinkManager {
        private final Deque<Packet<?>> incoming = new ArrayDeque<>();
        private long firstQueuedAt = 0L;
        private boolean flushing = false;

        public boolean isFlushing() {
            return flushing;
        }

        public void queueIncoming(Packet<?> packet) {
            if (incoming.isEmpty()) firstQueuedAt = System.currentTimeMillis();
            incoming.add(packet);
        }

        public long getQueuedTime() {
            return incoming.isEmpty() ? 0L : System.currentTimeMillis() - firstQueuedAt;
        }

        public boolean hasQueuedIncoming() {
            return !incoming.isEmpty();
        }

        public int size() {
            return incoming.size();
        }

        public void clear() {
            incoming.clear();
            firstQueuedAt = 0L;
        }

        /** Actually re-dispatches every held packet (the missing part). */
        public void flush(MinecraftClient mc) {
            if (flushing || incoming.isEmpty()) return;
            flushing = true;
            try {
                var handler = mc.getNetworkHandler();
                if (handler == null) {
                    incoming.clear();
                    return;
                }
                while (!incoming.isEmpty()) {
                    Packet<?> p = incoming.poll();
                    try {
                        dispatch(p, handler);
                    } catch (Throwable ignored) {
                    }
                }
            } finally {
                flushing = false;
                firstQueuedAt = 0L;
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void dispatch(Packet<?> packet, PacketListener listener) {
            ((Packet) packet).apply(listener);
        }
    }

    public BlinkManager getBlinkManager() {
        return blink;
    }
}
