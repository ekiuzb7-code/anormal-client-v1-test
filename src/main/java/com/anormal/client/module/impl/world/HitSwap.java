package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * Swaps to the right weapon for the swing that is about to happen.
 *
 * <p><b>Why the old version did nothing.</b> It ran from {@code onTick}, which fires at the end of
 * the mc tick - long after the click had already been turned into an attack packet. The swap
 * therefore always happened <em>after</em> the hit, so every hit used the weapon you were already
 * holding. It now runs from {@code ClientPlayerInteractionManager.attackEntity} (see
 * {@code ClientPlayerInteractionManagerMixin}), which is the only point where the slot can still
 * change the packet, and it tells the server about the slot change immediately.</p>
 */
public class HitSwap extends Module {

    public final BooleanSetting maces = new BooleanSetting("Maces", "Swap to a mace for the smash hit", true);
    public final BooleanSetting smashOnly = new BooleanSetting("Smash Only", "Only swap to a mace while falling", true);
    public final BooleanSetting axes = new BooleanSetting("Axes", "Swap to an axe against raised shields", true);
    public final BooleanSetting switchBack = new BooleanSetting("Switch Back", "Switch back after the hit", true);

    private int originalSlot = -1;
    private int swapTimer = 0;

    public HitSwap() {
        super("HitSwap", "Swaps weapon right as the attack connects to apply special weapon attributes", Category.WORLD);
        addSetting(maces);
        addSetting(smashOnly);
        addSetting(axes);
        addSetting(switchBack);
    }

    @Override
    public void onDisable() {
        restore();
    }

    /**
     * Called from the attack hook, before the attack packet is built.
     *
     * @param target the entity being attacked
     */
    public void beforeAttack(Entity target) {
        if (mc.player == null || mc.interactionManager == null || target == null) return;
        if (swapTimer > 0) return;

        boolean blocking = target instanceof LivingEntity living && living.isBlocking();
        boolean falling = mc.player.fallDistance > 1.0f || mc.player.getVelocity().y < -0.2;

        int slot = -1;
        if (axes.isEnabled() && blocking) {
            slot = findSlot("_axe", false);
        }
        if (slot == -1 && maces.isEnabled() && (!smashOnly.isEnabled() || falling)) {
            slot = findSlot("mace", true);
        }
        if (slot == -1) return;

        int current = mc.player.getInventory().getSelectedSlot();
        if (current == slot) return;

        originalSlot = current;
        mc.player.getInventory().setSelectedSlot(slot);

        // The server has to know about the slot before it processes the attack.
        syncSlot(slot);
        swapTimer = 2;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (swapTimer <= 0) return;

        if (--swapTimer == 0 && switchBack.isEnabled()) {
            restore();
        }
    }

    private void restore() {
        if (originalSlot >= 0 && originalSlot < 9 && mc.player != null) {
            try {
                mc.player.getInventory().setSelectedSlot(originalSlot);
                syncSlot(originalSlot);
            } catch (Throwable ignored) {
            }
        }
        originalSlot = -1;
        swapTimer = 0;
    }

    /** Tells the server which hotbar slot is selected (syncSelectedSlot is private). */
    private void syncSlot(int slot) {
        try {
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
        } catch (Throwable ignored) {
        }
    }

    private int findSlot(String path, boolean exact) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack == null || stack.isEmpty()) continue;
            var id = Registries.ITEM.getId(stack.getItem());
            if (id == null) continue;
            if (exact ? id.getPath().equals(path) : id.getPath().endsWith(path)) return i;
        }
        return -1;
    }
}
