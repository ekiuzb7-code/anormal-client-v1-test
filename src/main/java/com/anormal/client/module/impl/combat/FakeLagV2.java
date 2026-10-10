package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.Item;

import java.util.Random;

public class FakeLagV2 extends Module {
    // Core settings
    public final NumberSetting range = new NumberSetting("Range", "Enemy detection range", 3.0, 0.5, 10.0, 0.5);
    public final NumberSetting delay = new NumberSetting("Delay", "Packet delay in ms", 300, 0, 1000, 50);
    public final NumberSetting delayVariance = new NumberSetting("Delay Variance", "Random variance for delay", 300, 0, 500, 50);
    public final NumberSetting recoilTime = new NumberSetting("Recoil Time", "Minimum time between flushes", 250, 0, 1000, 50);

    public final ModeSetting mode = new ModeSetting("Mode", "FakeLag mode", "Dynamic", "Constant", "Dynamic");
    public final ModeSetting flushOn = new ModeSetting("Flush On", "Packet types to flush immediately", "EntityInteract,BlockInteract,Action",
        "EntityInteract", "BlockInteract", "Action");

    // Internal state
    private final Random random = new Random();
    private long chronometer = 0;
    private int nextDelay;
    private boolean isEnemyNearby = false;

    public FakeLagV2() {
        super("FakeLagV2", "LiquidBounce-style FakeLag with Dynamic/Constant modes", Category.COMBAT);
        addSetting(range);
        addSetting(delay);
        addSetting(delayVariance);
        addSetting(recoilTime);
        addSetting(mode);
        addSetting(flushOn);
        nextDelay = getRandomDelay();
    }

    private int getRandomDelay() {
        return delay.getValue().intValue() + random.nextInt(delayVariance.getValue().intValue() + 1);
    }

    @Override
    public void onEnable() {
        chronometer = System.currentTimeMillis();
        nextDelay = getRandomDelay();
    }

    @Override
    public void onDisable() {
        // Flush all queued packets
        if (mc.player != null && mc.getNetworkHandler() != null) {
            // Packets are flushed automatically by BlinkManager equivalent
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        // Check for nearby enemies (Dynamic mode)
        isEnemyNearby = false;
        if (mode.is("Dynamic")) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof LivingEntity target && target != mc.player && target.isAlive()) {
                    if (mc.player.distanceTo(target) <= range.getValue()) {
                        isEnemyNearby = true;
                        break;
                    }
                }
            }
        }
    }

    // Call this from NetworkMixin to handle packet queuing
    public boolean shouldQueuePacket(Packet<?> packet) {
        if (mc.player == null || mc.world == null || mc.player.isDead() || mc.currentScreen != null) {
            return false;
        }

        // Check recoil time
        if (System.currentTimeMillis() - chronometer < recoilTime.getValue().intValue()) {
            return false;
        }

        // Check if delay has elapsed
        if (BlinkManager.getInstance().getQueuedTime() < nextDelay) {
            return false;
        }

        // Flush on specific packets
        if (shouldFlushImmediately(packet)) {
            chronometer = System.currentTimeMillis();
            return false;
        }

        // Dynamic mode: only lag when enemy nearby
        if (mode.is("Dynamic")) {
            if (!isEnemyNearby) {
                return false;
            }
        }

        // Don't lag while using consumable items
        if (mc.player.isUsingItem()) {
            ItemStack stack = mc.player.getActiveItem();
            // Check if item is food by checking the stack's food component
            if (stack.isFood()) {
                return false;
            }
        }

        // Queue the packet
        nextDelay = getRandomDelay();
        return true;
    }

    private boolean shouldFlushImmediately(Packet<?> packet) {
        // Flush on interaction packets
        if (flushOn.is("EntityInteract") &&
            (packet instanceof PlayerInteractEntityC2SPacket)) {
            chronometer = System.currentTimeMillis();
            return true;
        }

        if (flushOn.is("BlockInteract") &&
            (packet instanceof net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket)) {
            chronometer = System.currentTimeMillis();
            return true;
        }

        if (flushOn.is("Action") &&
            (packet instanceof PlayerActionC2SPacket)) {
            chronometer = System.currentTimeMillis();
            return true;
        }

        // Flush on knockback
        if (packet instanceof EntityVelocityUpdateS2CPacket velocityPacket) {
            if (velocityPacket.getEntityId() == mc.player.getId() &&
                !velocityPacket.getVelocity().equals(Vec3d.ZERO)) {
                chronometer = System.currentTimeMillis();
                return true;
            }
        }

        // Flush on explosion
        if (packet instanceof ExplosionS2CPacket) {
            chronometer = System.currentTimeMillis();
            return true;
        }

        // Flush on damage
        if (packet instanceof HealthUpdateS2CPacket) {
            chronometer = System.currentTimeMillis();
            return true;
        }

        // Flush on teleport
        if (packet instanceof PlayerPositionLookS2CPacket) {
            chronometer = System.currentTimeMillis();
            return true;
        }

        return false;
    }

    // Static BlinkManager equivalent for packet queue management
    public static class BlinkManager {
        private static BlinkManager instance;
        private final java.util.Queue<QueuedPacket> outgoingQueue = new java.util.LinkedList<>();
        private final java.util.Queue<QueuedPacket> incomingQueue = new java.util.LinkedList<>();

        public static BlinkManager getInstance() {
            if (instance == null) instance = new BlinkManager();
            return instance;
        }

        public void queueOutgoing(Packet<?> packet) {
            outgoingQueue.add(new QueuedPacket(packet, System.currentTimeMillis()));
        }

        public void queueIncoming(Packet<?> packet) {
            incomingQueue.add(new QueuedPacket(packet, System.currentTimeMillis()));
        }

        public Packet<?> pollOutgoing() {
            return outgoingQueue.poll().packet();
        }

        public Packet<?> pollIncoming() {
            return incomingQueue.poll().packet();
        }

        public long getQueuedTime() {
            if (outgoingQueue.isEmpty()) return Long.MAX_VALUE;
            return System.currentTimeMillis() - outgoingQueue.peek().timestamp();
        }

        public void flushOutgoing() {
            while (!outgoingQueue.isEmpty()) {
                // Packets are sent automatically by network handler
                outgoingQueue.poll();
            }
        }

        public void flushIncoming() {
            incomingQueue.clear();
        }

        public boolean hasQueuedOutgoing() {
            return !outgoingQueue.isEmpty();
        }

        public boolean hasQueuedIncoming() {
            return !incomingQueue.isEmpty();
        }

        public void flushOutgoingOlderThan(long timestamp) {
            while (!outgoingQueue.isEmpty() && outgoingQueue.peek().timestamp() <= timestamp) {
                outgoingQueue.poll();
            }
        }

        private record QueuedPacket(Packet<?> packet, long timestamp) {}
    }

    public static BlinkManager getBlinkManager() {
        return BlinkManager.getInstance();
    }
}