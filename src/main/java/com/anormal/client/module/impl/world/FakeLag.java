package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.Packet;

import java.util.ArrayList;
import java.util.List;

public class FakeLag extends Module {
    public final ModeSetting style = new ModeSetting("Style", "V1 or V2 implementation", "V1", "V1", "V2");

    // --- V1 settings ---
    public final ModeSetting mode = new ModeSetting("Mode", "Latency constant, Dynamic combat, Repel keeps distance", "Latency", "Latency", "Dynamic", "Repel");
    public final NumberSetting delay = new NumberSetting("Delay", "Packet hold time in milliseconds", 100.0, 20.0, 1000.0, 10.0);
    public final NumberSetting transmissionOffset = new NumberSetting("Transmission Offset", "Extra hold for Repel mode", 40.0, 0.0, 200.0, 10.0);

    // --- V2 settings ---
    public final ModeSetting lbMode = new ModeSetting("V2 Mode", "Constant lags always, Dynamic only near enemies", "Dynamic", "Constant", "Dynamic");
    public final NumberSetting lbRange = new NumberSetting("V2 Range", "Enemy distance triggering lag", 3.5, 1.0, 10.0, 0.5);
    public final NumberSetting lbMinDelay = new NumberSetting("V2 Min Delay", "Min hold time (ms)", 300.0, 0.0, 1000.0, 25.0);
    public final NumberSetting lbMaxDelay = new NumberSetting("V2 Max Delay", "Max hold time (ms)", 300.0, 0.0, 1000.0, 25.0);
    public final NumberSetting recoilTime = new NumberSetting("Recoil", "Cooldown after flush", 0.0, 0.0, 1000.0, 25.0);

    public final BooleanSetting flushOnHurt = new BooleanSetting("Flush On Hurt", "Release packets when damaged", true);

    private static final List<Packet<?>> QUEUE = new ArrayList<>();
    private static boolean flushing = false;
    private volatile boolean holding = false;
    private long holdStart = 0;
    private long holdFor = 100;
    private long recoilUntil = 0;

    public FakeLag() {
        super("FakeLag", "Holds movement packets: server sees your old position", Category.WORLD);
        addSetting(style);
        addSetting(mode);
        addSetting(delay);
        addSetting(transmissionOffset);
        addSetting(lbMode);
        addSetting(lbRange);
        addSetting(lbMinDelay);
        addSetting(lbMaxDelay);
        addSetting(recoilTime);
        addSetting(flushOnHurt);
    }

    @Override
    public void onEnable() {
        holding = false;
        holdStart = 0;
        recoilUntil = 0;
    }

    @Override
    public void onDisable() {
        flush();
        holding = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();

        if (flushOnHurt.isEnabled() && mc.player.hurtTime > 0 && !QUEUE.isEmpty()) {
            flush();
            holding = false;
            recoilUntil = now + (style.is("V2") ? recoilTime.getValue().longValue() : 0);
            return;
        }

        if (holding) {
            if (now - holdStart >= holdFor) {
                flush();
                holding = false;
                if (style.is("V2")) recoilUntil = now + recoilTime.getValue().longValue();
            }
            return;
        }

        if (now < recoilUntil) return;

        int holdMs = style.is("V2") ? lbHoldMs() : vapeHoldMs();
        if (holdMs > 0) {
            holdFor = holdMs;
            holdStart = now;
            holding = true;
        }
    }

    // ---------- V1 branch ----------
    private int vapeHoldMs() {
        int base = Math.max(20, delay.getValue().intValue());
        try {
            if (mode.is("Latency")) return base;
            LivingEntity near = nearestEnemy(8.0);
            if (mode.is("Dynamic")) {
                if (near == null) return 0;
                double d = mc.player.distanceTo(near);
                double factor = Math.max(0.4, Math.min(1.6, 1.6 - d / 8.0));
                return Math.max(20, (int) (base * factor));
            }
            if (near == null || mc.player.distanceTo(near) > 6.0) return 0;
            return base + Math.max(0, transmissionOffset.getValue().intValue());
        } catch (Throwable ignored) {
            return base;
        }
    }

    // ---------- V2 branch ----------
    private int lbHoldMs() {
        try {
            boolean wantHold = lbMode.is("Constant") || nearestEnemy(lbRange.getValue()) != null;
            if (!wantHold) return 0;
            double min = Math.min(lbMinDelay.getValue(), lbMaxDelay.getValue());
            double max = Math.max(lbMinDelay.getValue(), lbMaxDelay.getValue());
            return Math.max(20, (int) (min + Math.random() * Math.max(1.0, max - min)));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private LivingEntity nearestEnemy(double range) {
        try {
            LivingEntity best = null;
            double bestDist = range;
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof LivingEntity living && e != mc.player && living.isAlive()) {
                    double d = mc.player.distanceTo(living);
                    if (d < bestDist) {
                        bestDist = d;
                        best = living;
                    }
                }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // Called by NetworkMixin on the network thread
    public static synchronized void queue(Packet<?> packet) {
        if (QUEUE.size() < 2000) QUEUE.add(packet);
    }

    public static synchronized boolean isFlushing() {
        return flushing;
    }

    public boolean isHolding() {
        return isEnabled() && holding;
    }

    public static synchronized void flush() {
        if (QUEUE.isEmpty()) return;
        try {
            if (net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler() == null) {
                QUEUE.clear();
                return;
            }
            flushing = true;
            List<Packet<?>> copy = new ArrayList<>(QUEUE);
            QUEUE.clear();
            for (Packet<?> p : copy) {
                try {
                    net.minecraft.client.MinecraftClient.getInstance().getNetworkHandler().sendPacket(p);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            QUEUE.clear();
        } finally {
            flushing = false;
        }
    }
}
