package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.SoundS2CPacket;
import net.minecraft.network.packet.s2c.play.SystemChatS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Random;
import java.util.function.Predicate;

public class BackTrackV2 extends Module {
    // Core settings
    public final NumberSetting range = new NumberSetting("Range", "Backtrack range in blocks", 3.0, 0.5, 10.0, 0.5);
    public final NumberSetting delay = new NumberSetting("Delay", "Backtrack delay in ms", 150, 0, 1000, 10);
    public final NumberSetting nextBacktrackDelay = new NumberSetting("Next Backtrack Delay", "Delay before next backtrack", 0, 0, 2000, 50);
    public final NumberSetting trackingBuffer = new NumberSetting("Tracking Buffer", "Time to keep tracking after enemy leaves range", 500, 0, 2000, 50);
    public final NumberSetting chance = new NumberSetting("Chance", "Backtrack chance percentage", 50, 0, 100, 1);

    public final ModeSetting targetMode = new ModeSetting("Target Mode", "How to select target", "Attack", "Attack", "Range");
    public final NumberSetting lastAttackTimeToWork = new NumberSetting("Last Attack Time To Work", "Time after attack to keep backtrack active", 1000, 0, 5000, 50);

    // Pause on hurt time
    public final BooleanSetting pauseOnHurtTime = new BooleanSetting("Pause On Hurt Time", "Pause backtrack when target is hurt", false);
    public final NumberSetting pauseHurtTime = new NumberSetting("Hurt Time", "Minimum hurt time to pause", 3, 0, 10, 1);

    // ESP settings
    public final ModeSetting espMode = new ModeSetting("ESP", "ESP render mode", "Box", "Box", "Model", "Wireframe", "None");

    // Internal state
    private final Random random = new Random();
    private long chronometer = 0;
    private long trackingBufferChronometer = 0;
    private long attackChronometer = 0;
    private int currentDelay;
    private boolean shouldPause = false;
    private Entity target = null;
    private TrackedPosition position = new TrackedPosition();
    private boolean chancePassed;

    public BackTrackV2() {
        super("BackTrackV2", "LiquidBounce-style Backtrack with packet manipulation", Category.COMBAT);
        addSetting(range);
        addSetting(delay);
        addSetting(nextBacktrackDelay);
        addSetting(trackingBuffer);
        addSetting(chance);
        addSetting(targetMode);
        addSetting(lastAttackTimeToWork);
        addSetting(pauseOnHurtTime);
        addSetting(pauseHurtTime);
        addSetting(espMode);

        chancePassed = random.nextInt(100) < chance.getValue().intValue();
        currentDelay = getRandomDelay();
    }

    private int getRandomDelay() {
        return delay.getValue().intValue() + random.nextInt(100);
    }

    @Override
    public void onEnable() {
        clear(false, false, false);
        chancePassed = random.nextInt(100) < chance.getValue().intValue();
        currentDelay = getRandomDelay();
    }

    @Override
    public void onDisable() {
        clear(true);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // Update chance
        if (attackChronometer == 0 || System.currentTimeMillis() - attackChronometer > lastAttackTimeToWork.getValue().intValue()) {
            chancePassed = random.nextInt(100) < chance.getValue().intValue();
        }

        // Handle target selection based on mode
        if (mc.player == null || mc.world == null) return;

        if (targetMode.is("Range")) {
            // Range mode: auto-target nearest enemy
            Entity enemy = findEnemy(range.getValue().floatValue());
            if (enemy == null) {
                clear(true);
                return;
            }
            processTarget(enemy);
        }
    }

    // Called from NetworkMixin for incoming packets
    public boolean shouldQueueIncomingPacket(Packet<?> packet) {
        if (mc.player == null || mc.world == null || !isEnabled()) return false;
        if (target == null) return false;
        if (!target.isAlive()) {
            clear(true);
            return false;
        }

        // Flush on teleport/disconnect
        if (packet instanceof PlayerPositionLookS2CPacket || packet instanceof DisconnectS2CPacket) {
            clear(true);
            return false;
        }

        // Ignore hurt sounds
        if (packet instanceof SoundS2CPacket soundPacket) {
            if (soundPacket.getSound().value() == SoundEvents.PLAYER_HURT) {
                return false; // Pass through
            }
        }

        // Flush on death
        if (packet instanceof HealthUpdateS2CPacket healthPacket) {
            if (healthPacket.getHealth() <= 0) {
                clear(true);
                return false;
            }
        }

        // Process target position
        if (target != null) {
            Vec3d pos = position.handlePacket(packet, mc.world, target);
            if (pos != null) {
                // Check if target's actual position is closer than tracked position
                double actualDist = target.squaredDistanceTo(pos);
                double trackedDist = target.squaredDistanceTo(position.getBase());
                if (actualDist < trackedDist) {
                    // Target is closer to actual position, flush queue
                    return false; // FLUSH - pass through
                }
            }
        }

        return true; // QUEUE - hold packet
    }

    // Called from NetworkMixin for tick processing
    public void onTickProcess() {
        if (mc.player == null || mc.world == null || !isEnabled()) return;

        boolean hadQueuedIncoming = BlinkManager.getInstance().hasQueuedIncoming();

        if (shouldCancelPackets()) {
            long now = System.currentTimeMillis();
            BlinkManager.getInstance().flushIncomingOlderThan(now - currentDelay);
        } else if (hadQueuedIncoming) {
            BlinkManager.getInstance().flushIncoming();
            clear(false, true);
        }

        if (!BlinkManager.getInstance().hasQueuedIncoming()) {
            currentDelay = getRandomDelay();
        }
    }

    // Called when player attacks
    public void onAttack(Entity enemy) {
        attackChronometer = System.currentTimeMillis();
        chancePassed = random.nextInt(100) < chance.getValue().intValue();

        if (targetMode.is("Attack")) {
            processTarget(enemy);
        }
    }

    private void processTarget(Entity enemy) {
        // Check pause on hurt time
        shouldPause = enemy instanceof LivingEntity living && living.hurtTime >= pauseHurtTime.getValue().intValue();

        if (!shouldBacktrack(enemy)) {
            return;
        }

        // Reset on enemy change
        if (enemy != target) {
            clear(true, false, false);

            // Instantly set new position
            position.setBaseFrom(enemy);
        }

        target = enemy;
    }

    private boolean shouldBacktrack(Entity target) {
        if (target == null || !target.isAlive()) return false;

        double distance = mc.player.distanceTo(target);
        boolean inRange = distance <= range.getValue();

        if (inRange) {
            trackingBufferChronometer = System.currentTimeMillis();
        }

        boolean inBuffer = (inRange || !hasTrackingBufferExpired()) &&
            target.isAlive() &&
            mc.player.age > 10 &&
            chancePassed &&
            !shouldPause() &&
            (System.currentTimeMillis() - attackChronometer < lastAttackTimeToWork.getValue().intValue());

        return inBuffer;
    }

    private boolean hasTrackingBufferExpired() {
        return System.currentTimeMillis() - trackingBufferChronometer > trackingBuffer.getValue().intValue();
    }

    private boolean shouldPause() {
        return pauseOnHurtTime.isEnabled() && shouldPause;
    }

    public void clear() {
        clear(true);
    }

    private void clear(boolean handlePackets, boolean clearOnly, boolean resetChronometer) {
        if (handlePackets && !clearOnly) {
            BlinkManager.getInstance().flushIncoming();
        } else if (clearOnly) {
            BlinkManager.getInstance().clearIncomingOnly();
        }

        if (target != null && resetChronometer) {
            // Wait for next backtrack delay
        }

        target = null;
        position.clear();
    }

    public boolean isLagging() {
        return isEnabled() && BlinkManager.getInstance().hasQueuedIncoming();
    }

    // Tracked position class (equivalent to LiquidBounce's TrackedEntityPosition)
    private static class TrackedPosition {
        private Vec3d base = Vec3d.ZERO;

        public Vec3d getBase() {
            return base;
        }

        public void setBaseFrom(Entity entity) {
            base = entity.getPos();
        }

        public void clear() {
            base = Vec3d.ZERO;
        }

        public Vec3d handlePacket(Packet<?> packet, World world, Entity target) {
            // Simplified - in reality this would parse packets to track position
            // For now just return null to indicate no position update
            return null;
        }
    }

    // Use existing BlinkManager
    public static class BlinkManager {
        private static BlinkManager instance;
        private final java.util.Queue<QueuedPacket> incomingQueue = new java.util.LinkedList<>();

        public static BlinkManager getInstance() {
            if (instance == null) instance = new BlinkManager();
            return instance;
        }

        public void queueIncoming(Packet<?> packet) {
            incomingQueue.add(new QueuedPacket(packet, System.currentTimeMillis()));
        }

        public void flushIncoming() {
            incomingQueue.clear();
        }

        public void flushIncomingOlderThan(long timestamp) {
            while (!incomingQueue.isEmpty() && incomingQueue.peek().timestamp() <= timestamp) {
                incomingQueue.poll();
            }
        }

        public void clearIncomingOnly() {
            incomingQueue.clear();
        }

        public boolean hasQueuedIncoming() {
            return !incomingQueue.isEmpty();
        }

        public void flushIncoming(Predicate<QueuedPacket> predicate) {
            incomingQueue.removeIf(predicate);
        }

        private record QueuedPacket(Packet<?> packet, long timestamp) {}
    }

    public static BlinkManager getBlinkManager() {
        return BlinkManager.getInstance();
    }
}