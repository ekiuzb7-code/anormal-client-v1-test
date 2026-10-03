package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class MLG extends Module {
    public final NumberSetting onDamage = new NumberSetting("On At Least X Damage", "Min fall damage to trigger", 5.0, 1.0, 20.0, 0.5);
    public final BooleanSetting onLethal = new BooleanSetting("On Lethal Fall", "Trigger on lethal damage", true);
    public final NumberSetting aimSpeed = new NumberSetting("Aim Speed", "Look rotation speed", 12.0, 1.0, 30.0, 0.5);
    public final BooleanSetting silentAim = new BooleanSetting("Silent Aim", "Place without moving camera", false);
    public final BooleanSetting checkInventory = new BooleanSetting("Check Inventory", "Quick-move MLG item to hotbar", true);
    public final BooleanSetting useBuckets = new BooleanSetting("Use Buckets", "Places water bucket to break fall", true);
    public final BooleanSetting pickUp = new BooleanSetting("Pick Up Water", "Picks water back up after landing", true);
    public final BooleanSetting useCobwebs = new BooleanSetting("Use Cobwebs", "Places cobweb beneath you", true);
    private boolean placedWater = false;
    private BlockPos placedPos = null;
    private int originalSlot = -1;
    public MLG() {
        super("MLG", "Automatically uses water buckets or cobwebs to prevent fall damage", Category.UZNY11);
        addSetting(onDamage); addSetting(onLethal); addSetting(aimSpeed); addSetting(silentAim);
        addSetting(checkInventory); addSetting(useBuckets); addSetting(pickUp); addSetting(useCobwebs);
    }
    @Override
    public void onDisable() {
        placedWater = false;
        placedPos = null;
        originalSlot = -1;
    }
    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (placedWater && pickUp.isEnabled() && mc.player.isOnGround()) {
            if (placedPos != null) {
                try {
                    if (mc.world.getBlockState(placedPos).getBlock() == net.minecraft.block.Blocks.WATER) {
                        int emptyBucket = findItem("bucket", true);
                        if (emptyBucket != -1) {
                            mc.player.getInventory().setSelectedSlot(emptyBucket);
                            BlockHitResult bhr = new BlockHitResult(new Vec3d(placedPos.getX() + 0.5, placedPos.getY() + 0.5, placedPos.getZ() + 0.5), Direction.UP, placedPos, false);
                            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                            mc.player.swingHand(Hand.MAIN_HAND);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            placedWater = false;
            placedPos = null;
            restoreSlot();
            return;
        }
        if (mc.player.isOnGround() || mc.player.getVelocity().y >= -0.4) return;
        double falling = mc.player.fallDistance;
        boolean lethal = false;
        try {
            lethal = falling + 3.0f >= mc.player.getHealth() + mc.player.getAbsorptionAmount();
        } catch (Throwable ignored) {}
        if (onLethal.isEnabled() ? (falling < 3.0f && !lethal) : (falling + 3.0f < onDamage.getValue())) return;

        BlockPos groundPos = mc.player.getBlockPos().down();
        if (!mc.world.isAir(groundPos)) return;
        if (!silentAim.isEnabled()) {
            float cur = mc.player.getPitch();
            float step = (float) Math.max(-aimSpeed.getValue(), Math.min(aimSpeed.getValue(), 90.0f - cur));
            mc.player.setPitch(Math.max(-90.0f, Math.min(90.0f, cur + step)));
        }
        if (useBuckets.isEnabled()) {
            int waterSlot = findItem("water_bucket", true);
            if (waterSlot == -1 && checkInventory.isEnabled()) waterSlot = pullFromInventory("water_bucket");
            if (waterSlot != -1) {
                originalSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(waterSlot);
                BlockPos airPos = mc.player.getBlockPos();
                BlockHitResult bhr = new BlockHitResult(new Vec3d(airPos.getX() + 0.5, groundPos.getY() + 1.0, airPos.getZ() + 0.5), Direction.UP, groundPos, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);
                placedWater = true;
                placedPos = mc.player.getBlockPos();
                return;
            }
        }
        if (useCobwebs.isEnabled()) {
            int webSlot = findItem("cobweb", true);
            if (webSlot == -1 && checkInventory.isEnabled()) webSlot = pullFromInventory("cobweb");
            if (webSlot != -1) {
                originalSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(webSlot);
                BlockPos airPos = mc.player.getBlockPos();
                BlockHitResult bhr = new BlockHitResult(new Vec3d(airPos.getX() + 0.5, groundPos.getY() + 1.0, airPos.getZ() + 0.5), Direction.UP, groundPos, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);
                restoreSlot();
            }
        }
    }

    private void restoreSlot() {
        if (originalSlot >= 0 && originalSlot < 9 && mc.player != null) {
            try {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            } catch (Throwable ignored) {}
        }
        originalSlot = -1;
    }

    private int findItem(String path, boolean exact) {
        for (int i = 0; i < 9; i++) {
            try {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.isEmpty()) continue;
                Identifier id = Registries.ITEM.getId(stack.getItem());
                if (id == null) continue;
                if (exact ? id.getPath().equals(path) : id.getPath().endsWith(path)) return i;
            } catch (Throwable ignored) {}
        }
        return -1;
    }

    private int pullFromInventory(String path) {
        if (mc.player == null || mc.interactionManager == null) return -1;
        try {
            for (int i = 9; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.isEmpty()) continue;
                Identifier id = Registries.ITEM.getId(stack.getItem());
                if (id == null || !id.getPath().equals(path)) continue;
                for (int h = 0; h < 9; h++) {
                    if (mc.player.getInventory().getStack(h).isEmpty()) {
                        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                        return h;
                    }
                }
                return -1;
            }
        } catch (Throwable ignored) {}
        return -1;
    }
}
