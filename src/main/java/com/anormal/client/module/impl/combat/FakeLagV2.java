package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.DoubleRangeSetting;
import com.anormal.client.setting.IntRangeSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.MultiSelectSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * FakeLag v2 — Exact LiquidBounce Nextgen FakeLag port:
 * <ul>
 *   <li>Holds back outgoing movement packets so server sees your old position.</li>
 *   <li><b>Dynamic Mode:</b> only lags when enemies are nearby AND when your server position
 *       is further from the enemy than your client position (prevents getting hit at old pos).</li>
 *   <li><b>Auto Flush:</b> flushes on incoming damage, knockback velocity, explosions, teleports.</li>
 *   <li><b>FlushOn:</b> flushes on entity interact, block interact, or actions.</li>
 * </ul>
 */
public class FakeLagV2 extends Module {

    public final DoubleRangeSetting range = new DoubleRangeSetting("Range", "Enemy detection range",
            2.0, 5.0, 0.0, 10.0, 0.1);

    public final IntRangeSetting delay = new IntRangeSetting("Delay", "Packet hold delay range in ms",
            300, 600, 0, 1000);

    public final NumberSetting recoilTime = new NumberSetting("RecoilTime", "Minimum gap between two lag bursts (ms)",
            250.0, 0.0, 1000.0, 10.0);

    public final ModeSetting mode = new ModeSetting("Mode", "FakeLag execution mode",
            "Dynamic", "Dynamic", "Constant");

    public final MultiSelectSetting flushOn = new MultiSelectSetting("FlushOn", "Outgoing packets that flush the queue",
            new String[]{"EntityInteract", "BlockInteract", "Action"},
            "EntityInteract", "BlockInteract", "Action");

    private static final int MAX_QUEUED = 40;

    private long chronometer = 0L;
    private long nextDelay = 400L;
    private boolean isEnemyNearby = false;

    // Track position sent in first held packet (server's perception of player)
    private Vec3d serverPosition = null;

    public FakeLagV2() {
        super("FakeLagV2", "LiquidBounce-style FakeLag (holds outgoing movement packets)", Category.COMBAT);
        addSetting(range);
        addSetting(delay);
        addSetting(recoilTime);
        addSetting(mode);
        addSetting(flushOn);
    }

    public static BlinkManager getBlinkManager() {
        return BlinkManager.getInstance();
    }

    @Override
    public void onEnable() {
        chronometer = System.currentTimeMillis();
        nextDelay = delay.random();
        serverPosition = null;
    }

    @Override
    public void onDisable() {
        getBlinkManager().flush();
        isEnemyNearby = false;
        serverPosition = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        double maxCheckRange = range.getMax();
        isEnemyNearby = false;

        for (Entity e : mc.world.getEntities()) {
            if (e instanceof LivingEntity living && living != mc.player && living.isAlive()) {
                if (mc.player.distanceTo(living) <= maxCheckRange) {
                    isEnemyNearby = true;
                    break;
                }
            }
        }
    }

    /**
     * Decides whether an outgoing packet should be held in the BlinkManager queue.
     * Matches LiquidBounce ModuleFakeLag.kt packet handler.
     */
    public boolean shouldQueuePacket(Packet<?> packet) {
        if (mc.player == null || mc.world == null) return false;

        BlinkManager bm = BlinkManager.getInstance();
        if (bm.isFlushing()) return false;

        long now = System.currentTimeMillis();

        // 1. Non-movement packets: check if they trigger a configured flush
        if (!(packet instanceof PlayerMoveC2SPacket movePacket)) {
            if (flushMatches(packet)) {
                flushNow(bm, now);
            }
            return false;
        }

        // 2. Disqualifying conditions: dead, swimming/in water, menu open, or falling fast
        if (mc.player.isDead() || mc.player.isTouchingWater() || mc.currentScreen != null
                || mc.player.getVelocity().y < -0.5) {
            flushNow(bm, now);
            return false;
        }

        // 3. Recoil cooldown between bursts
        if (now - chronometer < recoilTime.getValue().intValue()) {
            flushNow(bm, now);
            return false;
        }

        // 4. Time limit per burst
        if (bm.getQueuedTime() >= nextDelay) {
            nextDelay = delay.random();
            flushNow(bm, now);
            return false;
        }

        // 5. Do not lag while consuming food / potions
        if (mc.player.isUsingItem() && isConsumable(mc.player.getActiveItem())) {
            flushNow(bm, now);
            return false;
        }

        // 6. Max queue cap to prevent server disconnects
        if (bm.size() >= MAX_QUEUED) {
            flushNow(bm, now);
            return false;
        }

        // 7. Dynamic Mode check: only lag when server position is safer than client position
        if (mode.is("Dynamic")) {
            if (!isEnemyNearby) {
                flushNow(bm, now);
                return false;
            }

            // Determine server position from first queued packet or current player pos
            if (serverPosition == null) {
                serverPosition = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            }

            // Find closest enemy
            LivingEntity closestEnemy = null;
            double closestClientDist = Double.MAX_VALUE;

            for (Entity e : mc.world.getEntities()) {
                if (e instanceof LivingEntity living && living != mc.player && living.isAlive()) {
                    double d = mc.player.squaredDistanceTo(living);
                    if (d < closestClientDist) {
                        closestClientDist = d;
                        closestEnemy = living;
                    }
                }
            }

            if (closestEnemy == null) {
                flushNow(bm, now);
                return false;
            }

            double serverDistanceSq = closestEnemy.squaredDistanceTo(serverPosition);
            double clientDistanceSq = closestEnemy.squaredDistanceTo(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()));

            // Check bounding box intersection
            Box playerBox = mc.player.getBoundingBox();
            boolean intersects = closestEnemy.getBoundingBox().intersects(playerBox);

            // If server position is closer to the enemy than our client position,
            // or if we intersect, lagging would hurt us. Flush immediately!
            if (serverDistanceSq < clientDistanceSq || intersects) {
                flushNow(bm, now);
                return false;
            }
        }

        return true;
    }

    /**
     * Called from ConnectionMixin on incoming packets:
     * Teleports, knockback, explosions, and damage flush FakeLag instantly (matching LiquidBounce).
     */
    public void onIncomingPacket(Packet<?> packet) {
        if (mc.player == null) return;

        // Teleport / rubberband
        if (packet instanceof PlayerPositionLookS2CPacket) {
            flushNow(BlinkManager.getInstance(), System.currentTimeMillis());
            return;
        }

        // Server knockback
        if (packet instanceof EntityVelocityUpdateS2CPacket velocity) {
            if (velocity.getEntityId() == mc.player.getId()) {
                flushNow(BlinkManager.getInstance(), System.currentTimeMillis());
                return;
            }
        }

        // Explosion knockback
        if (packet instanceof ExplosionS2CPacket) {
            flushNow(BlinkManager.getInstance(), System.currentTimeMillis());
            return;
        }

        // Health damage
        if (packet instanceof HealthUpdateS2CPacket) {
            flushNow(BlinkManager.getInstance(), System.currentTimeMillis());
        }
    }

    private void flushNow(BlinkManager bm, long now) {
        if (bm.hasQueuedOutgoing()) {
            bm.flush();
            chronometer = now;
            serverPosition = null;
        }
    }

    private boolean flushMatches(Packet<?> packet) {
        if (flushOn.isSelected("EntityInteract")
                && (packet instanceof PlayerInteractEntityC2SPacket)) return true;
        if (flushOn.isSelected("BlockInteract")
                && (packet instanceof PlayerInteractBlockC2SPacket)) return true;
        if (flushOn.isSelected("Action")
                && (packet instanceof PlayerActionC2SPacket)) return true;
        return packet instanceof ResourcePackStatusC2SPacket;
    }

    private boolean isConsumable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            if (stack.contains(DataComponentTypes.FOOD)) return true;
        } catch (Throwable ignored) {}
        return stack.isOf(Items.MILK_BUCKET)
                || stack.isOf(Items.POTION)
                || stack.isOf(Items.SPLASH_POTION)
                || stack.isOf(Items.LINGERING_POTION);
    }

    // ---------------------------------------------------------------------
    //  BlinkManager: Outgoing packet queue
    // ---------------------------------------------------------------------
    public static final class BlinkManager {
        private static final BlinkManager INSTANCE = new BlinkManager();

        private final Deque<Packet<?>> outgoing = new ArrayDeque<>();
        private long firstQueuedAt = 0L;
        private boolean flushing = false;

        private BlinkManager() {}

        public static BlinkManager getInstance() {
            return INSTANCE;
        }

        public boolean isFlushing() {
            return flushing;
        }

        public void queueOutgoing(Packet<?> packet) {
            if (outgoing.isEmpty()) firstQueuedAt = System.currentTimeMillis();
            outgoing.add(packet);
        }

        public long getQueuedTime() {
            return outgoing.isEmpty() ? 0L : System.currentTimeMillis() - firstQueuedAt;
        }

        public boolean hasQueuedOutgoing() {
            return !outgoing.isEmpty();
        }

        public int size() {
            return outgoing.size();
        }

        public void flush() {
            if (flushing || outgoing.isEmpty()) return;
            flushing = true;
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.getNetworkHandler() != null) {
                    while (!outgoing.isEmpty()) {
                        Packet<?> packet = outgoing.poll();
                        if (packet != null) {
                            mc.getNetworkHandler().sendPacket(packet);
                        }
                    }
                } else {
                    outgoing.clear();
                }
            } catch (Throwable ignored) {
            } finally {
                outgoing.clear();
                firstQueuedAt = 0L;
                flushing = false;
            }
        }
    }
}
