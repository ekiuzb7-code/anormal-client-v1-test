package com.anormal.client.module.impl.combat;

import com.anormal.client.lucid.LucidClient;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/**
 * CrystalOptimizer — the Argon implementation behind Lucid's module UI.
 *
 * <p>Makes your crystals disappear faster client-side so you can place
 * crystals faster: when an attack packet on an entity leaves the mc, the
 * outgoing {@link PlayerInteractEntityC2SPacket} is inspected and — if the
 * crosshair is on an End Crystal the player can actually break — the crystal
 * is discarded from the mc world immediately, instead of waiting for the
 * server's removal round-trip. The server still processes the damage; your
 * mc simply stops rendering the entity the instant you commit the hit,
 * which un-gates the next placement.
 *
 * <p>Argon's fairness gate is kept: the instant removal only fires when you
 * would actually break the crystal — i.e. you are not weakened (or your
 * strength beats the weakness), or you're holding a diamond/netherite tool.
 *
 * <p>The hook lives in {@code ClientConnectionMixin} (outgoing packet send),
 * which forwards every packet to {@link #onPacketSend(Packet)}.
 */
public class CrystalOptimizer extends Module {

    public CrystalOptimizer() {
        super("Crystal Optimizer",
            "Makes your crystals disappear faster client-side so you can place crystals faster",
            Category.COMBAT);
    }

    // ════════════════════════════════════════════════════════════════
    //  Packet-send hook — called from ClientConnectionMixin on every
    //  outgoing packet (Argon's PacketSendListener equivalent).
    // ════════════════════════════════════════════════════════════════

    public static void onPacketSend(net.minecraft.network.packet.Packet<?> packet) {
        try {
            CrystalOptimizer instance = instance();
            if (instance == null || !instance.isEnabled()) return;

            if (packet instanceof PlayerInteractEntityC2SPacket interactPacket) {
                interactPacket.handle(new PlayerInteractEntityC2SPacket.Handler() {
                    @Override
                    public void interact(Hand hand) {
                    }

                    @Override
                    public void interactAt(Hand hand, Vec3d pos) {
                    }

                    @Override
                    public void attack() {
                        instance.handleAttack();
                    }
                });
            }
        } catch (Throwable t) {
            com.anormal.client.lucid.LucidLog.LOGGER.error("Lucid CrystalOptimizer.onPacketSend threw", t);
        }
    }

    /** The attack branch of the packet handler — Argon's instant crystal removal. */
    private void handleAttack() {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.crosshairTarget == null) return;

        if (mc.crosshairTarget.getType() == HitResult.Type.ENTITY
                && mc.crosshairTarget instanceof EntityHitResult hit) {
            if (hit.getEntity() instanceof EndCrystalEntity) {
                StatusEffectInstance weakness = mc.player.getStatusEffect(StatusEffects.WEAKNESS);
                StatusEffectInstance strength = mc.player.getStatusEffect(StatusEffects.STRENGTH);
                if (!(weakness == null
                        || strength != null && strength.getAmplifier() > weakness.getAmplifier()
                        || isTool(mc.player.getMainHandStack()))) {
                    return;
                }

                Entity crystal = hit.getEntity();
                crystal.discard();
                crystal.setRemoved(Entity.RemovalReason.KILLED);
                crystal.onRemoved();
            }
        }
    }

    /** Diamond / netherite tools break crystals regardless of weakness. */
    private static boolean isTool(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.DIAMOND_SWORD
            || item == Items.DIAMOND_PICKAXE
            || item == Items.DIAMOND_AXE
            || item == Items.DIAMOND_SHOVEL
            || item == Items.DIAMOND_HOE
            || item == Items.NETHERITE_SWORD
            || item == Items.NETHERITE_PICKAXE
            || item == Items.NETHERITE_AXE
            || item == Items.NETHERITE_SHOVEL
            || item == Items.NETHERITE_HOE;
    }

    private static CrystalOptimizer instance() {
        LucidClient lucid = LucidClient.getInstance();
        if (lucid == null || lucid.getModuleManager() == null) return null;
        return lucid.getModuleManager().getModule(CrystalOptimizer.class);
    }
}
