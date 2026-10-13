package com.anormal.client.module.impl.combat;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Forces critical hits.
 *
 * <p>Three interchangeable implementations, each mirroring a different client:</p>
 * <ul>
 *   <li><b>V1 - Meteor</b>: mode (Packet / Jump / MiniJump), only-killaura,
 *       smash-attack, additional-height.</li>
 *   <li><b>V2 - Wurst</b>: a single mode setting (Packet / Jump).</li>
 *   <li><b>V3 - LiquidBounce</b>: StopSprinting, Range, Fake, Critical, Magic.</li>
 * </ul>
 *
 * <p>All variants work by making the server believe you are falling, which is
 * what turns a normal hit into a critical hit.</p>
 */
public class Criticals extends Module {
    public final ModeSetting version = new ModeSetting("Version", "Which implementation to use", "V1", "V1", "V2", "V3");

    // ---- V1 - Meteor ----
    public final ModeSetting v1Mode = new ModeSetting("V1 Mode", "How to fake the fall", "Packet", "Packet", "Jump", "MiniJump");
    public final BooleanSetting v1OnlyKillAura = new BooleanSetting("V1 Only KillAura", "Only critical while a KillAura target exists", false);
    public final BooleanSetting v1SmashAttack = new BooleanSetting("V1 Smash Attack", "Critical on every attack tick", false);
    public final NumberSetting v1AdditionalHeight = new NumberSetting("V1 Additional Height", "Extra fall distance faked", 0.0, 0.0, 1.0, 0.05);

    // ---- V2 - Wurst ----
    public final ModeSetting v2Mode = new ModeSetting("V2 Mode", "How to fake the fall", "Packet", "Packet", "Jump");

    // ---- V3 - LiquidBounce ----
    public final ModeSetting v3StopSprinting = new ModeSetting("V3 StopSprinting", "When to stop sprinting for the crit", "Legit", "Legit", "Always", "Never");
    public final NumberSetting v3Range = new NumberSetting("V3 Range", "Only crit when an enemy is within this range", 4.0, 0.0, 10.0, 0.5);
    public final BooleanSetting v3Fake = new BooleanSetting("V3 Fake", "Only send the fake packets, do not actually move", true);
    public final NumberSetting v3Critical = new NumberSetting("V3 Critical", "How many critical packets to send", 1.0, 0.0, 20.0, 1.0);
    public final NumberSetting v3Magic = new NumberSetting("V3 Magic", "Extra magic packets to send", 0.0, 0.0, 20.0, 1.0);

    public Criticals() {
        super("Criticals", "Forces critical hits", Category.COMBAT);
        addSetting(version);
        addSetting(v1Mode);
        addSetting(v1OnlyKillAura);
        addSetting(v1SmashAttack);
        addSetting(v1AdditionalHeight);
        addSetting(v2Mode);
        addSetting(v3StopSprinting);
        addSetting(v3Range);
        addSetting(v3Fake);
        addSetting(v3Critical);
        addSetting(v3Magic);

        for (var s : new com.anormal.client.setting.Setting<?>[]{v1Mode, v1OnlyKillAura, v1SmashAttack, v1AdditionalHeight}) {
            s.visibleIf(() -> version.is("V1"));
        }
        v2Mode.visibleIf(() -> version.is("V2"));
        for (var s : new com.anormal.client.setting.Setting<?>[]{v3StopSprinting, v3Range, v3Fake, v3Critical, v3Magic}) {
            s.visibleIf(() -> version.is("V3"));
        }
    }

    /**
     * Called by the attack path just before the hit is dealt.
     *
     * @return true when a critical was successfully faked.
     */
    public boolean doCrit() {
        if (!isEnabled() || mc.player == null || mc.world == null) return false;
        try {
            if (!mc.player.isOnGround()) return false;
            if (mc.player.isTouchingWater() || mc.player.isClimbing() || mc.player.isGliding()) return false;
            if (mc.player.hasVehicle()) return false;

            String mode = version.is("V2") ? v2Mode.getValue() : v1Mode.getValue();

            // LiquidBounce: optionally stop sprinting first.
            if (version.is("V3")) {
                if (v3StopSprinting.is("Always") || (v3StopSprinting.is("Legit") && mc.player.isSprinting())) {
                    mc.player.setSprinting(false);
                }
                if (v3Range.getValue() > 0 && !enemyInRange(v3Range.getValue())) return false;
                int count = Math.max(1, v3Critical.getValue().intValue());
                for (int i = 0; i < count; i++) sendFallPackets(0.0625, 0.0);
                if (v3Magic.getValue() > 0) {
                    sendFallPackets(0.11, 0.0);
                }
                return true;
            }

            double extra = version.is("V1") ? v1AdditionalHeight.getValue() : 0.0;
            switch (mode) {
                case "Jump" -> {
                    if (mc.player.isOnGround()) mc.player.jump();
                }
                case "MiniJump" -> sendFallPackets(0.11 + extra, 0.0);
                default -> sendFallPackets(0.0625 + extra, 0.0);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Sends the classic "tiny fall" position packets the server accepts as a crit. */
    private void sendFallPackets(double up, double down) {
        try {
            var handler = mc.getNetworkHandler();
            if (handler == null) return;
            double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
            handler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround(
                    x, y + up, z, false, mc.player.horizontalCollision));
            handler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround(
                    x, y + down, z, false, mc.player.horizontalCollision));
        } catch (Throwable ignored) {
        }
    }

    private boolean enemyInRange(double range) {
        try {
            for (var e : mc.world.getEntities()) {
                if (!(e instanceof net.minecraft.entity.player.PlayerEntity p) || p == mc.player) continue;
                if (mc.player.distanceTo(p) <= range) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
