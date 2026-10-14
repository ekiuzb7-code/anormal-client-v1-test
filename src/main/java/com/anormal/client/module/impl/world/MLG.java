package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * MLG - Places water or cobwebs beneath you to prevent fall damage.
 * Replicated verbatim from Vape V4.21 specification.
 */
public class MLG extends Module {
    public final NumberSetting onDamage = new NumberSetting("On at least X damage", "Min fall damage to trigger (accounting for feather falling)", 5.0, 1.0, 20.0, 0.5);
    public final BooleanSetting onLethal = new BooleanSetting("On lethal fall", "Activates MLG when fall damage would kill you", true);
    public final NumberSetting aimSpeed = new NumberSetting("Aim speed", "How quickly MLG changes your look angles", 20.0, 1.0, 40.0, 1.0);
    public final BooleanSetting silentAim = new BooleanSetting("Silent Aim", "Uses the Silent Aim system without moving client camera", true);
    public final BooleanSetting checkInventory = new BooleanSetting("Check inventory", "Quick-moves MLG items from inventory to hotbar when needed", true);
    public final BooleanSetting useBuckets = new BooleanSetting("Use Buckets", "Uses a Water Bucket from hotbar to place water and picks it up", true);
    public final BooleanSetting pickUp = new BooleanSetting("Pick up water", "Picks up the water after you're done MLG'ing", true);
    public final BooleanSetting useCobwebs = new BooleanSetting("Use Cobwebs", "Uses Cobwebs from hotbar to place them beneath you", true);

    private boolean placedWater = false;
    private BlockPos placedPos = null;
    private int originalSlot = -1;
    private int swappedInvSlot = -1;
    private int swappedHotbarSlot = -1;

    private boolean pendingPlace = false;
    private int pendingSlot = -1;
    private BlockPos pendingGround = null;
    private boolean pendingIsCobweb = false;
    private int pickupCooldown = 0;

    public MLG() {
        super("MLG", "Places water or cobwebs beneath you to prevent fall damage", Category.WORLD);
        addSetting(onDamage);
        addSetting(onLethal);
        addSetting(aimSpeed);
        addSetting(silentAim);
        addSetting(checkInventory);
        addSetting(useBuckets);
        addSetting(pickUp);
        addSetting(useCobwebs);
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        placedWater = false;
        placedPos = null;
        originalSlot = -1;
        swappedInvSlot = -1;
        swappedHotbarSlot = -1;
        pendingPlace = false;
        pendingSlot = -1;
        pendingGround = null;
        pendingIsCobweb = false;
        pickupCooldown = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // 1. Handle water retrieval after landing
        if (placedWater) {
            pickupCooldown++;
            boolean landed = mc.player.isOnGround() || mc.player.isTouchingWater() || pickupCooldown > 15;
            if (landed && pickUp.isEnabled() && placedPos != null) {
                try {
                    int emptyBucketSlot = findItem("bucket", true);
                    if (emptyBucketSlot != -1) {
                        mc.player.getInventory().setSelectedSlot(emptyBucketSlot);
                        BlockHitResult bhr = new BlockHitResult(
                                new Vec3d(placedPos.getX() + 0.5, placedPos.getY() + 0.5, placedPos.getZ() + 0.5),
                                Direction.UP, placedPos, false);
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                } catch (Throwable ignored) {}
                restoreSlotAndInventory();
                placedWater = false;
                placedPos = null;
                pickupCooldown = 0;
                return;
            } else if (landed && !pickUp.isEnabled()) {
                restoreSlotAndInventory();
                placedWater = false;
                placedPos = null;
                pickupCooldown = 0;
                return;
            }
        }

        // 2. Execute pending delayed place (tick delay ensures server has received slot packet)
        if (pendingPlace && pendingGround != null) {
            BlockPos targetAir = pendingGround.up();
            try {
                if (mc.world.isAir(targetAir) || mc.world.getBlockState(targetAir).isReplaceable()) {
                    if (!silentAim.isEnabled()) {
                        mc.player.setPitch(90.0f);
                    }
                    BlockHitResult bhr = new BlockHitResult(
                            new Vec3d(pendingGround.getX() + 0.5, pendingGround.getY() + 1.0, pendingGround.getZ() + 0.5),
                            Direction.UP, pendingGround, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                    mc.player.swingHand(Hand.MAIN_HAND);

                    if (!pendingIsCobweb) {
                        placedWater = true;
                        placedPos = targetAir;
                        pickupCooldown = 0;
                    } else {
                        restoreSlotAndInventory();
                    }
                }
            } catch (Throwable ignored) {
                restoreSlotAndInventory();
            }
            pendingPlace = false;
            pendingSlot = -1;
            pendingGround = null;
            pendingIsCobweb = false;
            return;
        }

        // 3. Fall damage & state validation
        if (mc.player.isOnGround() || mc.player.getVelocity().y >= -0.2) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) return;
        if (mc.player.getAbilities().flying || mc.player.isGliding()) return;
        if (mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)) return;
        if (mc.player.isTouchingWater() || mc.player.isClimbing()) return;

        // Calculate expected damage taking feather falling and resistance into account
        float fallDist = (float) mc.player.fallDistance;
        float safeDist = 3.0f;
        if (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            var jb = mc.player.getStatusEffect(StatusEffects.JUMP_BOOST);
            if (jb != null) safeDist += (jb.getAmplifier() + 1);
        }
        float rawDamage = Math.max(0.0f, fallDist - safeDist);
        if (rawDamage <= 0.0f) return;

        float estimatedDamage = rawDamage;
        if (mc.player.hasStatusEffect(StatusEffects.RESISTANCE)) {
            var res = mc.player.getStatusEffect(StatusEffects.RESISTANCE);
            if (res != null) {
                float resFactor = Math.max(0.0f, 1.0f - (res.getAmplifier() + 1) * 0.2f);
                estimatedDamage *= resFactor;
            }
        }
        try {
            ItemStack boots = mc.player.getEquippedStack(EquipmentSlot.FEET);
            if (!boots.isEmpty() && boots.toString().toLowerCase().contains("feather_falling")) {
                estimatedDamage *= 0.52f;
            }
        } catch (Throwable ignored) {}

        float currentHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean lethal = estimatedDamage >= currentHealth;
        boolean trigger = (onLethal.isEnabled() && lethal) || (estimatedDamage >= onDamage.getValue());
        if (!trigger) return;

        // 4. Find solid ground beneath
        BlockPos ground = null;
        BlockPos feet = mc.player.getBlockPos();
        for (int i = 1; i <= 14; i++) {
            BlockPos p = feet.down(i);
            try {
                var state = mc.world.getBlockState(p);
                if (!state.isAir() && state.isSolidBlock(mc.world, p)) {
                    ground = p;
                    break;
                }
            } catch (Throwable ignored) {
                return;
            }
        }
        if (ground == null) return;

        double drop = mc.player.getY() - (ground.getY() + 1.0);
        // Only trigger within reactive distance to ground (approx 0.5 - 5.5 blocks)
        if (drop > 5.5 || drop < 0.4) return;

        BlockPos targetAir = ground.up();
        try {
            if (!mc.world.isAir(targetAir) && !mc.world.getBlockState(targetAir).isReplaceable()) return;
        } catch (Throwable ignored) {
            return;
        }

        // Aim logic
        if (!silentAim.isEnabled()) {
            float cur = mc.player.getPitch();
            float step = (float) Math.min(aimSpeed.getValue(), Math.abs(90.0f - cur));
            mc.player.setPitch(cur + step);
        }

        // Check if ultra-warm dimension (Nether) where water cannot be placed
        boolean nether = false;
        try {
            nether = mc.world.getRegistryKey().getValue().getPath().contains("nether");
        } catch (Throwable ignored) {}

        // Try water bucket first (unless in Nether)
        if (useBuckets.isEnabled() && !nether) {
            int waterSlot = findItem("water_bucket", true);
            if (waterSlot == -1 && checkInventory.isEnabled()) {
                waterSlot = pullToHotbar("water_bucket");
            }
            if (waterSlot != -1) {
                prepareAndQueuePlace(waterSlot, ground, false);
                return;
            }
        }

        // Try cobweb
        if (useCobwebs.isEnabled()) {
            int webSlot = findItem("cobweb", true);
            if (webSlot == -1 && checkInventory.isEnabled()) {
                webSlot = pullToHotbar("cobweb");
            }
            if (webSlot != -1) {
                prepareAndQueuePlace(webSlot, ground, true);
            }
        }
    }

    private void prepareAndQueuePlace(int slot, BlockPos ground, boolean isCobweb) {
        if (originalSlot == -1) {
            originalSlot = mc.player.getInventory().getSelectedSlot();
        }
        if (mc.player.getInventory().getSelectedSlot() != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
            pendingPlace = true;
            pendingSlot = slot;
            pendingGround = ground;
            pendingIsCobweb = isCobweb;
        } else {
            // Already holding, can place immediately
            try {
                if (!silentAim.isEnabled()) {
                    mc.player.setPitch(90.0f);
                }
                BlockHitResult bhr = new BlockHitResult(
                        new Vec3d(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5),
                        Direction.UP, ground, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);
                if (!isCobweb) {
                    placedWater = true;
                    placedPos = ground.up();
                    pickupCooldown = 0;
                } else {
                    restoreSlotAndInventory();
                }
            } catch (Throwable ignored) {
                restoreSlotAndInventory();
            }
        }
    }

    private void restoreSlotAndInventory() {
        if (mc.player == null) return;
        try {
            // If item was swapped from inventory to hotbar, swap back
            if (swappedInvSlot != -1 && swappedHotbarSlot != -1 && mc.interactionManager != null) {
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        swappedInvSlot,
                        swappedHotbarSlot,
                        SlotActionType.SWAP,
                        mc.player);
                swappedInvSlot = -1;
                swappedHotbarSlot = -1;
            }
            if (originalSlot >= 0 && originalSlot < 9) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            }
        } catch (Throwable ignored) {}
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

    private int pullToHotbar(String path) {
        if (mc.player == null || mc.interactionManager == null) return -1;
        try {
            // Check main inventory (slots 9 to 35)
            for (int i = 9; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.isEmpty()) continue;
                Identifier id = Registries.ITEM.getId(stack.getItem());
                if (id == null || !id.getPath().equals(path)) continue;

                // Pick destination hotbar slot: first empty, or current selected slot
                int targetHotbar = -1;
                for (int h = 0; h < 9; h++) {
                    if (mc.player.getInventory().getStack(h).isEmpty()) {
                        targetHotbar = h;
                        break;
                    }
                }
                if (targetHotbar == -1) {
                    targetHotbar = mc.player.getInventory().getSelectedSlot();
                }

                // SWAP slot action swaps slot i with targetHotbar slot
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        i,
                        targetHotbar,
                        SlotActionType.SWAP,
                        mc.player);

                swappedInvSlot = i;
                swappedHotbarSlot = targetHotbar;
                return targetHotbar;
            }
        } catch (Throwable ignored) {}
        return -1;
    }
}
