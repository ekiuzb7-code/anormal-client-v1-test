package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.DoubleRangeSetting;
import com.anormal.client.setting.IntRangeSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
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
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Random;

/**
 * BackTrack v2 — Exact LiquidBounce Nextgen Backtrack port:
 * <ul>
 *   <li>Holds incoming entity-movement packets to freeze target at their past position.</li>
 *   <li><b>Smart Distance Check:</b> if target moves closer to the player, automatically flushes
 *       so the enemy can always be hit directly.</li>
 *   <li><b>Sliding Window Flush:</b> flushes packets older than {@code currentDelay} every tick.</li>
 *   <li><b>Settings:</b> Range, Delay, NextBacktrackDelay, TrackingBuffer, Chance, PauseOnHurtTime,
 *       TargetMode (Attack/Range), LastAttackTimeToWork, ESP.</li>
 * </ul>
 */
public class BackTrackV2 extends Module {

    public final DoubleRangeSetting range = new DoubleRangeSetting("Range", "Backtrack range in blocks",
            1.0, 3.0, 0.0, 10.0, 0.1);

    public final IntRangeSetting delay = new IntRangeSetting("Delay", "Backtrack delay range in ms",
            100, 150, 0, 1000);

    public final IntRangeSetting nextBacktrackDelay = new IntRangeSetting("NextBacktrackDelay", "Wait time before next backtrack in ms",
            0, 10, 0, 2000);

    public final NumberSetting trackingBuffer = new NumberSetting("TrackingBuffer", "Keep tracking after enemy leaves range (ms)",
            500.0, 0.0, 2000.0, 10.0);

    public final NumberSetting chance = new NumberSetting("Chance", "Chance to backtrack (%)",
            50.0, 0.0, 100.0, 1.0);

    public final BooleanSetting pauseOnHurtTime = new BooleanSetting("PauseOnHurtTime", "Pause while target is on hurt time",
            false);

    public final NumberSetting hurtTime = new NumberSetting("HurtTime", "Target hurt time to pause at",
            3.0, 0.0, 10.0, 1.0);

    public final ModeSetting targetMode = new ModeSetting("TargetMode", "How the target is selected",
            "Attack", "Attack", "Range");

    public final NumberSetting lastAttackTimeToWork = new NumberSetting("LastAttackTimeToWork", "Keep active this long after an attack (ms)",
            1000.0, 0.0, 5000.0, 10.0);

    public final ModeSetting esp = new ModeSetting("Esp", "Render backtracked position",
            "Box", "Box", "Wireframe", "None");

    private static final int MAX_QUEUED = 80;

    private final Random random = new Random();
    private final IncomingBlinkManager blink = new IncomingBlinkManager();

    private long trackingBufferChronometer = 0L;
    private long attackChronometer = 0L;
    private long lastBacktrackEnd = 0L;
    private long currentDelay = 120L;
    private boolean chancePassed = true;
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

        // 1. Attack detection (for Attack mode)
        boolean attackDown = mc.options != null && mc.options.attackKey.isPressed();
        if (attackDown && !attackKeyWasDown) {
            if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity living) {
                onAttack(living);
            }
        }
        attackKeyWasDown = attackDown;

        // 2. Target selection for Range mode
        if (targetMode.is("Range")) {
            Entity enemy = findEnemy(range.getMax());
            if (enemy != null) {
                if (target != enemy) {
                    clear();
                    target = enemy;
                    trackedPos = new Vec3d(enemy.getX(), enemy.getY(), enemy.getZ());
                }
            } else {
                if (target != null && now - trackingBufferChronometer > trackingBuffer.getValue()) {
                    clear();
                }
            }
        }

        // 3. Sliding window flush: flushes packets older than currentDelay (LiquidBounce logic)
        if (blink.hasQueuedIncoming()) {
            blink.flushOlderThan(now - currentDelay);
        }

        if (target == null || !target.isAlive() || !shouldBacktrack()) {
            if (blink.hasQueuedIncoming()) {
                flushIncoming();
            }
            return;
        }

        // Update tracked position from target
        if (trackedPos == null) {
            trackedPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        }
    }

    public void onAttack(LivingEntity entity) {
        if (entity == null || entity == mc.player) return;
        attackChronometer = System.currentTimeMillis();
        chancePassed = random.nextInt(100) < chance.getValue().intValue();
        currentDelay = delay.random();

        if (target != entity) {
            clear();
            target = entity;
            trackedPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        }
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

    public boolean shouldBacktrack() {
        if (target == null || !target.isAlive() || mc.player == null) return false;
        if (mc.player.age <= 10) return false;
        if (!chancePassed) return false;

        long now = System.currentTimeMillis();

        // Hurt time pause
        if (pauseOnHurtTime.isEnabled() && hurtTime.getValue() > 0
                && target instanceof LivingEntity living
                && living.hurtTime >= hurtTime.getValue().intValue()) {
            return false;
        }

        // Delay cooldown between backtracks
        if (lastBacktrackEnd != 0 && now - lastBacktrackEnd < nextBacktrackDelay.random()) {
            return false;
        }

        double dist = mc.player.distanceTo(target);
        boolean inRange = dist <= range.getMax();
        if (inRange) {
            trackingBufferChronometer = now;
        }
        boolean inBuffer = inRange || (now - trackingBufferChronometer <= trackingBuffer.getValue());

        if (targetMode.is("Attack") && now - attackChronometer > lastAttackTimeToWork.getValue()) {
            return false;
        }

        return inBuffer;
    }

    /**
     * Called from ConnectionMixin to check if incoming packet should be held.
     */
    public boolean shouldQueueIncomingPacket(Packet<?> packet) {
        if (mc.player == null || mc.world == null) return false;
        if (blink.isFlushing()) return false;

        // Disconnect or teleport flush
        if (packet instanceof PlayerPositionLookS2CPacket) {
            flushIncoming();
            return false;
        }

        if (packet instanceof HealthUpdateS2CPacket health) {
            if (health.getHealth() <= 0) {
                flushIncoming();
                return false;
            }
        }

        boolean isEntityMove = packet instanceof EntityPositionS2CPacket || packet instanceof EntityS2CPacket;
        if (!isEntityMove) return false;

        if (target == null || !shouldBacktrack()) return false;

        int id = entityIdOf(packet);
        if (id == -1 || id != target.getId()) return false;

        if (blink.size() >= MAX_QUEUED) {
            flushIncoming();
            return false;
        }

        // LiquidBounce Smart check:
        // Is the enemy moving CLOSER to the player in this packet?
        // If yes, process immediately so the player can hit the closer target!
        Vec3d newPacketPos = extractPosition(packet);
        if (newPacketPos != null && trackedPos != null) {
            double newDistSq = mc.player.squaredDistanceTo(newPacketPos);
            double trackedDistSq = mc.player.squaredDistanceTo(trackedPos);

            if (newDistSq < trackedDistSq) {
                // Enemy came closer! Flush and update so we hit them closer!
                flushIncoming();
                trackedPos = newPacketPos;
                return false;
            }
        }

        return true;
    }

    public void queueIncoming(Packet<?> packet) {
        blink.queue(packet);
    }

    public void flushIncoming() {
        blink.flush();
        lastBacktrackEnd = System.currentTimeMillis();
        currentDelay = delay.random();
        chancePassed = random.nextInt(100) < chance.getValue().intValue();
    }

    private void clear() {
        flushIncoming();
        target = null;
        trackedPos = null;
    }

    private int entityIdOf(Packet<?> packet) {
        if (packet instanceof EntityPositionS2CPacket posPacket) {
            return posPacket.entityId();
        }
        if (packet instanceof EntityS2CPacket movePacket) {
            Entity ent = movePacket.getEntity(mc.world);
            return ent != null ? ent.getId() : -1;
        }
        return -1;
    }

    private Vec3d extractPosition(Packet<?> packet) {
        if (packet instanceof EntityPositionS2CPacket posPacket) {
            return posPacket.change().position();
        }
        return null;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || mc.world == null || target == null || trackedPos == null) return;
        if (esp.is("None")) return;

        double width = target.getWidth() * 0.5;
        double h = target.getHeight();
        Box box = new Box(
                trackedPos.x - width, trackedPos.y, trackedPos.z - width,
                trackedPos.x + width, trackedPos.y + h, trackedPos.z + width
        );

        int color = ColorUtils.rgba(255, 60, 60, 200);

        if (esp.is("Box")) {
            RenderUtils.drawBlockOutline(context, net.minecraft.util.math.BlockPos.ofFloored(trackedPos), color, tickDelta, 2);
        } else if (esp.is("Wireframe")) {
            Vec3d[] corners = new Vec3d[]{
                    new Vec3d(box.minX, box.minY, box.minZ),
                    new Vec3d(box.maxX, box.minY, box.minZ),
                    new Vec3d(box.maxX, box.minY, box.maxZ),
                    new Vec3d(box.minX, box.minY, box.maxZ),
                    new Vec3d(box.minX, box.maxY, box.minZ),
                    new Vec3d(box.maxX, box.maxY, box.minZ),
                    new Vec3d(box.maxX, box.maxY, box.maxZ),
                    new Vec3d(box.minX, box.maxY, box.maxZ)
            };

            int[][] sc = new int[8][];
            for (int i = 0; i < 8; i++) {
                sc[i] = ProjectionUtil.project(corners[i], tickDelta);
            }

            for (int i = 0; i < 4; i++) {
                drawWire(context, sc[i], sc[(i + 1) % 4], color);
                drawWire(context, sc[i + 4], sc[((i + 1) % 4) + 4], color);
                drawWire(context, sc[i], sc[i + 4], color);
            }
        }
    }

    private void drawWire(DrawContext context, int[] a, int[] b, int color) {
        if (a != null && b != null) {
            RenderUtils.drawLine(context, a[0], a[1], b[0], b[1], color, 1.5f);
        }
    }

    // ---------------------------------------------------------------------
    //  IncomingBlinkManager with sliding window flush
    // ---------------------------------------------------------------------
    public static final class IncomingBlinkManager {
        private final Deque<TimestampedPacket> queue = new ArrayDeque<>();
        private boolean flushing = false;

        public boolean isFlushing() {
            return flushing;
        }

        public void queue(Packet<?> packet) {
            queue.add(new TimestampedPacket(packet, System.currentTimeMillis()));
        }

        public boolean hasQueuedIncoming() {
            return !queue.isEmpty();
        }

        public int size() {
            return queue.size();
        }

        public void flushOlderThan(long thresholdTime) {
            if (flushing || queue.isEmpty()) return;
            flushing = true;
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.getNetworkHandler() != null) {
                    Iterator<TimestampedPacket> it = queue.iterator();
                    while (it.hasNext()) {
                        TimestampedPacket tp = it.next();
                        if (tp.timestamp <= thresholdTime) {
                            it.remove();
                            dispatchPacket(tp.packet, mc);
                        } else {
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                flushing = false;
            }
        }

        public void flush() {
            if (flushing || queue.isEmpty()) return;
            flushing = true;
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                while (!queue.isEmpty()) {
                    TimestampedPacket tp = queue.poll();
                    if (tp != null && mc.getNetworkHandler() != null) {
                        dispatchPacket(tp.packet, mc);
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                queue.clear();
                flushing = false;
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void dispatchPacket(Packet<?> packet, MinecraftClient mc) {
            try {
                PacketListener listener = mc.getNetworkHandler();
                if (listener != null) {
                    ((Packet) packet).apply(listener);
                }
            } catch (Throwable ignored) {}
        }

        private static class TimestampedPacket {
            final Packet<?> packet;
            final long timestamp;

            TimestampedPacket(Packet<?> packet, long timestamp) {
                this.packet = packet;
                this.timestamp = timestamp;
            }
        }
    }
}
