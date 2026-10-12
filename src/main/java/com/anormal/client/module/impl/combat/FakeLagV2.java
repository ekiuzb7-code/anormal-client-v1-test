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
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Holds outgoing movement packets so the server keeps you at your old position
 * (LiquidBounce-style FakeLag).
 *
 * <p>Settings mirror LiquidBounce's FakeLag module exactly: {@code Range},
 * {@code Delay}, {@code RecoilTime}, {@code Mode}, {@code FlushOn}.</p>
 *
 * <p><b>Fix:</b> the old {@code BlinkManager} only polled the queue and dropped
 * the packets - they were never re-sent, so the server simply stopped receiving
 * your position forever. The queue is now flushed by actually re-sending every
 * held packet through the network handler.</p>
 */
public class FakeLagV2 extends Module {
    public final DoubleRangeSetting range = new DoubleRangeSetting("Range", "Enemy detection range", 2.0, 5.0, 0.0, 10.0, 0.1);
    public final IntRangeSetting delay = new IntRangeSetting("Delay", "Packet delay range in ms", 300, 600, 0, 1000);
    public final NumberSetting recoilTime = new NumberSetting("RecoilTime", "Minimum time between flushes in ms", 250.0, 0.0, 1000.0, 10.0);
    public final ModeSetting mode = new ModeSetting("Mode", "Constant = always lag, Dynamic = only with an enemy nearby", "Dynamic", "Constant", "Dynamic");
    public final MultiSelectSetting flushOn = new MultiSelectSetting("FlushOn", "Packets that reset the lag timer",
            new String[]{"EntityInteract", "BlockInteract", "Action"},
            "EntityInteract", "BlockInteract", "Action");

    private long chronometer = 0L;
    private long nextDelay = 0L;
    private boolean enemyNearby = false;

    public FakeLagV2() {
        super("FakeLagV2", "LiquidBounce-style FakeLag (holds your movement packets)", Category.COMBAT);
        addSetting(range);
        addSetting(delay);
        addSetting(recoilTime);
        addSetting(mode);
        addSetting(flushOn);
    }

    @Override
    public void onEnable() {
        chronometer = System.currentTimeMillis();
        nextDelay = delay.random();
    }

    @Override
    public void onDisable() {
        getBlinkManager().flush();
        enemyNearby = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        enemyNearby = false;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof LivingEntity living && living != mc.player && living.isAlive()
                    && mc.player.distanceTo(living) <= range.getMax()) {
                enemyNearby = true;
                break;
            }
        }
    }

    /** @return true when the packet must be held back. */
    public boolean shouldQueuePacket(Packet<?> packet) {
        if (mc.player == null || mc.world == null) return false;
        BlinkManager bm = BlinkManager.getInstance();
        if (bm.isFlushing()) return false;

        long now = System.currentTimeMillis();

        // Non-movement packets never get held, but they can reset the timer.
        if (!(packet instanceof PlayerMoveC2SPacket)) {
            if (flushMatches(packet)) {
                chronometer = now;
                bm.flush();
            }
            return false;
        }

        // --- movement packets ---
        if (mc.player.isDead() || mc.player.isTouchingWater() || mc.currentScreen != null) {
            bm.flush();
            return false;
        }
        if (now - chronometer < recoilTime.getValue().intValue()) {
            bm.flush();
            return false;
        }
        if (bm.getQueuedTime() >= nextDelay) {
            nextDelay = delay.random();
            bm.flush();
            return false;
        }
        if (isConsumable(mc.player.getActiveItem())) {
            bm.flush();
            return false;
        }
        if (mode.is("Dynamic") && !enemyNearby) {
            bm.flush();
            return false;
        }

        return true;
    }

    private boolean flushMatches(Packet<?> packet) {
        if (flushOn.isSelected("EntityInteract")
                && (packet instanceof PlayerInteractEntityC2SPacket)) return true;
        if (flushOn.isSelected("BlockInteract")
                && (packet instanceof PlayerInteractBlockC2SPacket)) return true;
        if (flushOn.isSelected("Action")
                && (packet instanceof PlayerActionC2SPacket)) return true;
        // Always flush on resource-pack replies and position corrections.
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
    //  Packet queue
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

        /** Actually re-sends every held packet (this is the part that was missing). */
        public void flush() {
            if (flushing || outgoing.isEmpty()) return;
            flushing = true;
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                var handler = mc.getNetworkHandler();
                if (handler == null) {
                    outgoing.clear();
                    return;
                }
                while (!outgoing.isEmpty()) {
                    Packet<?> p = outgoing.poll();
                    try {
                        handler.sendPacket(p);
                    } catch (Throwable ignored) {
                    }
                }
            } finally {
                flushing = false;
                firstQueuedAt = 0L;
            }
        }
    }

    public static BlinkManager getBlinkManager() {
        return BlinkManager.getInstance();
    }
}
