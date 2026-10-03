package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class CrystalAura extends Module {
    public final ModeSetting mode = new ModeSetting("Mode", "Aura mode", "Auto", "Auto", "Manual");
    public final NumberSetting range = new NumberSetting("Range", "Crystal placement range", 4.5, 2.0, 6.0, 0.1);
    public final NumberSetting placeDelay = new NumberSetting("Place Delay", "Delay between placements (ticks)", 2, 0, 20, 1);
    public final NumberSetting breakDelay = new NumberSetting("Break Delay", "Delay before breaking (ticks)", 4, 0, 20, 1);
    public final NumberSetting maxSelfDamage = new NumberSetting("Max Self Damage", "Max damage to self", 6.0, 0.0, 20.0, 0.5);
    public final BooleanSetting antiSuicide = new BooleanSetting("Anti-Suicide", "Don't place if fatal to self", true);
    public final BooleanSetting players = new BooleanSetting("Players", "Target players", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", "Target mobs", false);
    public final BooleanSetting facePlace = new BooleanSetting("Face Place", "Place on face for max damage", false);
    public final ModeSetting targetMode = new ModeSetting("Target Mode", "Target selection", "Damage", "Damage", "Distance", "Health");

    private LivingEntity target;
    private EndCrystalEntity lastCrystal;
    private int placeTicks = 0;
    private int breakTicks = 0;

    public CrystalAura() {
        super("CrystalAura", "Places and breaks end crystals", Category.UZNY11);
        addSetting(mode);
        addSetting(range);
        addSetting(placeDelay);
        addSetting(breakDelay);
        addSetting(maxSelfDamage);
        addSetting(antiSuicide);
        addSetting(players);
        addSetting(mobs);
        addSetting(facePlace);
        addSetting(targetMode);
    }

    private int getSelectedSlot() {
        try {
            return mc.player.getInventory().selectedSlot;
        } catch (Exception e) {
            return 0;
        }
    }

    private void setSelectedSlot(int slot) {
        try {
            mc.player.getInventory().selectedSlot = slot;
        } catch (Exception e) {
            // Ignore
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (mode.is("Manual")) {
            if (!mc.options.useKey.isPressed()) return;
            manualPlace();
            return;
        }

        target = findTarget();
        if (target == null) return;

        // Break existing crystals
        if (breakTicks > 0) breakTicks--;
        else if (breakCrystal()) {
            breakTicks = breakDelay.getValue().intValue();
            return;
        }

        // Place crystals
        if (placeTicks > 0) placeTicks--;
        else if (placeCrystal()) {
            placeTicks = placeDelay.getValue().intValue();
        }
    }

    private boolean breakCrystal() {
        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (!crystal.isAlive()) continue;
            if (mc.player.distanceTo(crystal) > range.getValue() + 1) continue;

            double selfDamage = calculateSelfDamage(crystal);
            if (antiSuicide.isEnabled() && selfDamage > maxSelfDamage.getValue()) continue;

            mc.interactionManager.attackEntity(mc.player, crystal);
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private boolean placeCrystal() {
        int crystalSlot = findItem(Items.END_CRYSTAL);
        if (crystalSlot == -1) return false;

        BlockPos targetPos = target.getBlockPos();
        BlockPos bestPos = null;
        double bestDamage = 0;

        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos pos = targetPos.add(x, y, z);
                    if (mc.player.distanceTo(Vec3d.ofCenter(pos)) > range.getValue()) continue;

                    BlockPos below = pos.down();
                    if (!mc.world.getBlockState(below).isOf(Blocks.OBSIDIAN) &&
                        !mc.world.getBlockState(below).isOf(Blocks.BEDROCK)) continue;
                    if (!mc.world.isAir(pos) || !mc.world.isAir(pos.up())) continue;

                    double damage = calculateDamage(pos);
                    double selfDmg = calculateSelfDamage(pos);
                    if (antiSuicide.isEnabled() && selfDmg > maxSelfDamage.getValue()) continue;
                    if (damage > bestDamage) {
                        bestDamage = damage;
                        bestPos = pos;
                    }
                }
            }
        }

        if (bestPos == null) return false;

        int prev = getSelectedSlot();
        setSelectedSlot(crystalSlot);

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
            new BlockHitResult(Vec3d.ofCenter(bestPos.up()), Direction.UP, bestPos, false));
        mc.player.swingHand(Hand.MAIN_HAND);
        setSelectedSlot(prev);
        return true;
    }

    private void manualPlace() {
        if (mc.crosshairTarget instanceof BlockHitResult hit) {
            BlockPos pos = hit.getBlockPos();
            int slot = findItem(Items.END_CRYSTAL);
            if (slot == -1) return;

            int prev = getSelectedSlot();
            setSelectedSlot(slot);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
            setSelectedSlot(prev);
        }
    }

    private double calculateDamage(BlockPos crystalPos) {
        if (target == null) return 0;
        double dx = target.getX() - (crystalPos.getX() + 0.5);
        double dy = target.getY() - (crystalPos.getY() + 1);
        double dz = target.getZ() - (crystalPos.getZ() + 0.5);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > 6) return 0;
        return (6 - dist) * 12; // Approximate damage
    }

    private double calculateSelfDamage(EndCrystalEntity crystal) {
        return calculateSelfDamage(crystal.getBlockPos());
    }

    private double calculateSelfDamage(BlockPos pos) {
        double dx = mc.player.getX() - (pos.getX() + 0.5);
        double dy = mc.player.getY() - (pos.getY() + 1);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > 6) return 0;
        return (6 - dist) * 12;
    }

    private LivingEntity findTarget() {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player || !living.isAlive()) continue;
            if (living instanceof net.minecraft.entity.player.PlayerEntity) {
                if (!players.isEnabled()) continue;
            } else if (!mobs.isEnabled()) continue;

            double dist = mc.player.distanceTo(living);
            if (dist > range.getValue() + 2) continue;

            double score = targetMode.is("Health") ? living.getHealth() : dist;
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    private int findItem(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private int getSelectedSlot() {
        try {
            return mc.player.getInventory().selectedSlot;
        } catch (Exception e) {
            return 0;
        }
    }

    private void setSelectedSlot(int slot) {
        try {
            mc.player.getInventory().selectedSlot = slot;
        } catch (Exception e) {
            // Ignore
        }
    }
}