package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Automatically equips a Totem of Undying in the offhand.
 *
 * <p>Four interchangeable implementations, each mirroring a different client:
 * <ul>
 *   <li><b>V1</b> - Meteor (Smart/Strict, health/elytra/fall/explosion prediction)</li>
 *   <li><b>V2</b> - Wurst (simple delay + hearts threshold, two-click move)</li>
 *   <li><b>V3</b> - LiquidBounce (health threshold + safety + damage prediction + switch back)</li>
 *   <li><b>V4</b> - Vape (open/silent inventory, combat only, refill, randomization)</li>
 * </ul>
 */
public class AutoTotem extends Module {
    public final ModeSetting version = new ModeSetting("Version", "Which implementation to use", "V4", "V1", "V2", "V3", "V4");

    // ---------------- V1 - Meteor ----------------
    public final ModeSetting v1Mode = new ModeSetting("V1 Mode", "Smart = only when in danger, Strict = always hold", "Smart", "Smart", "Strict");
    public final NumberSetting v1Delay = new NumberSetting("V1 Delay", "Ticks between slot movements", 0.0, 0.0, 20.0, 1.0);
    public final NumberSetting v1Health = new NumberSetting("V1 Health", "Health to hold a totem at", 10.0, 0.0, 36.0, 1.0);
    public final BooleanSetting v1Elytra = new BooleanSetting("V1 Elytra", "Always hold a totem while elytra flying", true);
    public final BooleanSetting v1Fall = new BooleanSetting("V1 Fall", "Hold a totem when fall damage could kill you", true);
    public final BooleanSetting v1Explosion = new BooleanSetting("V1 Explosion", "Hold a totem when explosion damage could kill you", true);

    // ---------------- V2 - Wurst ----------------
    public final BooleanSetting v2ShowCounter = new BooleanSetting("V2 Show Counter", "Display the number of totems you have", true);
    public final NumberSetting v2Delay = new NumberSetting("V2 Delay", "Ticks to wait before equipping the next totem", 0.0, 0.0, 20.0, 1.0);
    public final NumberSetting v2Health = new NumberSetting("V2 Health", "Won't equip until health reaches this (hearts, 0 = ignore)", 0.0, 0.0, 10.0, 0.5);

    // ---------------- V3 - LiquidBounce ----------------
    public final NumberSetting v3SwitchDelay = new NumberSetting("V3 Switch Delay", "Milliseconds before switching to a totem", 0.0, 0.0, 500.0, 5.0);
    public final NumberSetting v3SwitchBackDelay = new NumberSetting("V3 Switch Back Delay", "Milliseconds before switching back", 40.0, 0.0, 500.0, 5.0);
    public final BooleanSetting v3Health = new BooleanSetting("V3 Health", "Switch to a totem on low health", true);
    public final NumberSetting v3HealthThreshold = new NumberSetting("V3 Health Threshold", "Health to switch to a totem at", 14.0, 0.0, 20.0, 1.0);
    public final BooleanSetting v3Safety = new BooleanSetting("V3 Safety", "Use a separate threshold inside holes/burrows", true);
    public final NumberSetting v3SafeHealth = new NumberSetting("V3 Safe Health Threshold", "Threshold while safe in a hole", 10.0, 0.0, 20.0, 1.0);
    public final BooleanSetting v3SubtractCalculatedDamage = new BooleanSetting("V3 Subtract Calculated Damage", "Predict damage and hold a totem more aggressively", false);
    public final BooleanSetting v3PredictExplosionEntities = new BooleanSetting("V3 Predict Explosion Entities", "Predict damage from crystals/TNT", true);
    public final BooleanSetting v3PredictExplosionBlocks = new BooleanSetting("V3 Predict Explosion Blocks", "Predict damage from beds/respawn anchors", false);
    public final BooleanSetting v3PredictFallDamage = new BooleanSetting("V3 Predict Fall Damage", "Predict lethal fall damage", true);
    public final BooleanSetting v3IgnoreElytra = new BooleanSetting("V3 Ignore Elytra", "Ignore fall damage while elytra flying", false);
    public final BooleanSetting v3MissingArmor = new BooleanSetting("V3 Missing Armor", "Hold a totem while any armor slot is empty", true);
    public final BooleanSetting v3SwitchBack = new BooleanSetting("V3 Switch Back", "Switch back to the original offhand item", true);
    public final BooleanSetting v3SendDirectly = new BooleanSetting("V3 Send Directly", "Ignore switch settings and send clicks immediately", false);

    // ---------------- V4 - Vape ----------------
    public final BooleanSetting v4OpenInventory = new BooleanSetting("V4 Open Inventory", "Opens inventory to grab a totem", true);
    public final BooleanSetting v4SilentOpen = new BooleanSetting("V4 Silent Open", "Moves the totem without showing the GUI", true);
    public final NumberSetting v4SilentMoveDelay = new NumberSetting("V4 Silent Move Delay", "Ticks waited before a silent move", 2.0, 0.0, 20.0, 1.0);
    public final BooleanSetting v4CloseInventory = new BooleanSetting("V4 Close Inventory", "Closes inventory opened by AutoTotem", true);
    public final BooleanSetting v4InventoryOnly = new BooleanSetting("V4 Inventory Only", "Only equips while inventory is open", false);
    public final BooleanSetting v4CombatOnly = new BooleanSetting("V4 Combat Only", "Only equips after recent danger", true);
    public final NumberSetting v4ActivationDelay = new NumberSetting("V4 Activation Delay", "Danger ticks before equipping", 10.0, 0.0, 100.0, 1.0);
    public final BooleanSetting v4Refill = new BooleanSetting("V4 Refill", "Refills a consumed main-hand totem", true);
    public final BooleanSetting v4RandomSlot = new BooleanSetting("V4 Random Slot", "Picks a random totem instead of the first", false);
    public final NumberSetting v4Delay = new NumberSetting("V4 Delay", "Ticks before equipping a totem", 2.0, 0.0, 20.0, 1.0);
    public final BooleanSetting v4ExtraRandomization = new BooleanSetting("V4 Extra Randomization", "Adds human-like timing variance", true);
    public final BooleanSetting v4ShowTotemCount = new BooleanSetting("V4 Show Totem Count", "Shows remaining totems on screen", true);

    private final Random random = new Random();

    // Shared / per-version state
    private int ticks = 0;
    private int totems = 0;
    private boolean locked = false;

    private int v2NextTickSlot = -1;
    private int v2Timer = 0;
    private boolean v2WasTotemInOffhand = false;

    private long v3LastSwitch = 0L;
    private long v3LastSwitchBack = 0L;
    private int v3LastTotemScreenSlot = -1;
    private boolean v3Holding = false;

    private int v4Cooldown = 0;
    private int v4Danger = 0;
    private int v4SilentWait = 0;
    private boolean v4OpenedByUs = false;

    public AutoTotem() {
        super("AutoTotem", "Automatically equips a Totem of Undying in the offhand", Category.INVENTORY);
        addSetting(version);

        addSetting(v1Mode);
        addSetting(v1Delay);
        addSetting(v1Health);
        addSetting(v1Elytra);
        addSetting(v1Fall);
        addSetting(v1Explosion);

        addSetting(v2ShowCounter);
        addSetting(v2Delay);
        addSetting(v2Health);

        addSetting(v3SwitchDelay);
        addSetting(v3SwitchBackDelay);
        addSetting(v3Health);
        addSetting(v3HealthThreshold);
        addSetting(v3Safety);
        addSetting(v3SafeHealth);
        addSetting(v3SubtractCalculatedDamage);
        addSetting(v3PredictExplosionEntities);
        addSetting(v3PredictExplosionBlocks);
        addSetting(v3PredictFallDamage);
        addSetting(v3IgnoreElytra);
        addSetting(v3MissingArmor);
        addSetting(v3SwitchBack);
        addSetting(v3SendDirectly);

        addSetting(v4OpenInventory);
        addSetting(v4SilentOpen);
        addSetting(v4SilentMoveDelay);
        addSetting(v4CloseInventory);
        addSetting(v4InventoryOnly);
        addSetting(v4CombatOnly);
        addSetting(v4ActivationDelay);
        addSetting(v4Refill);
        addSetting(v4RandomSlot);
        addSetting(v4Delay);
        addSetting(v4ExtraRandomization);
        addSetting(v4ShowTotemCount);

        // Only show the settings that belong to the selected version.
        v1Mode.visibleIf(() -> version.is("V1"));
        v1Delay.visibleIf(() -> version.is("V1"));
        v1Health.visibleIf(() -> version.is("V1"));
        v1Elytra.visibleIf(() -> version.is("V1"));
        v1Fall.visibleIf(() -> version.is("V1"));
        v1Explosion.visibleIf(() -> version.is("V1"));

        v2ShowCounter.visibleIf(() -> version.is("V2"));
        v2Delay.visibleIf(() -> version.is("V2"));
        v2Health.visibleIf(() -> version.is("V2"));

        v3SwitchDelay.visibleIf(() -> version.is("V3"));
        v3SwitchBackDelay.visibleIf(() -> version.is("V3"));
        v3Health.visibleIf(() -> version.is("V3"));
        v3HealthThreshold.visibleIf(() -> version.is("V3"));
        v3Safety.visibleIf(() -> version.is("V3"));
        v3SafeHealth.visibleIf(() -> version.is("V3"));
        v3SubtractCalculatedDamage.visibleIf(() -> version.is("V3"));
        v3PredictExplosionEntities.visibleIf(() -> version.is("V3"));
        v3PredictExplosionBlocks.visibleIf(() -> version.is("V3"));
        v3PredictFallDamage.visibleIf(() -> version.is("V3"));
        v3IgnoreElytra.visibleIf(() -> version.is("V3"));
        v3MissingArmor.visibleIf(() -> version.is("V3"));
        v3SwitchBack.visibleIf(() -> version.is("V3"));
        v3SendDirectly.visibleIf(() -> version.is("V3"));

        v4OpenInventory.visibleIf(() -> version.is("V4"));
        v4SilentOpen.visibleIf(() -> version.is("V4"));
        v4SilentMoveDelay.visibleIf(() -> version.is("V4"));
        v4CloseInventory.visibleIf(() -> version.is("V4"));
        v4InventoryOnly.visibleIf(() -> version.is("V4"));
        v4CombatOnly.visibleIf(() -> version.is("V4"));
        v4ActivationDelay.visibleIf(() -> version.is("V4"));
        v4Refill.visibleIf(() -> version.is("V4"));
        v4RandomSlot.visibleIf(() -> version.is("V4"));
        v4Delay.visibleIf(() -> version.is("V4"));
        v4ExtraRandomization.visibleIf(() -> version.is("V4"));
        v4ShowTotemCount.visibleIf(() -> version.is("V4"));
    }

    @Override
    public void onDisable() {
        ticks = 0;
        totems = 0;
        locked = false;
        v2NextTickSlot = -1;
        v2Timer = 0;
        v2WasTotemInOffhand = false;
        v4Cooldown = 0;
        v4Danger = 0;
        v4SilentWait = 0;
        v4OpenedByUs = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (version.is("V1")) tickV1();
        else if (version.is("V2")) tickV2();
        else if (version.is("V3")) tickV3();
        else tickV4();
    }

    // =====================================================================
    //  V1 - Meteor
    // =====================================================================
    private void tickV1() {
        totems = countTotems();
        if (totems <= 0) {
            locked = false;
            return;
        }
        if (ticks >= v1Delay.getValue().intValue()) {
            boolean low = mc.player.getHealth() + mc.player.getAbsorptionAmount()
                    - possibleHealthReductions(v1Explosion.isEnabled(), v1Fall.isEnabled()) <= v1Health.getValue();
            boolean ely = v1Elytra.isEnabled()
                    && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA
                    && mc.player.isGliding();

            locked = v1Mode.is("Strict") || (v1Mode.is("Smart") && (low || ely));

            if (locked && mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                equipFirstTotem();
            }
            ticks = 0;
            return;
        }
        ticks++;
    }

    /** Rough estimate of damage that could be taken right now (Meteor's PlayerUtils). */
    private float possibleHealthReductions(boolean explosion, boolean fall) {
        float damage = 0f;
        try {
            if (fall && mc.player.fallDistance > 3.0) {
                damage += (float) (mc.player.fallDistance - 3.0);
            }
            if (explosion) {
                for (Entity e : mc.world.getEntities()) {
                    double dist = e.distanceTo(mc.player);
                    if (dist > 8.0) continue;
                    if (e instanceof EndCrystalEntity) damage += (float) Math.max(0, 12 - dist * 1.5);
                    else if (e instanceof TntEntity) damage += (float) Math.max(0, 10 - dist * 1.2);
                }
            }
        } catch (Throwable ignored) {
        }
        return damage;
    }

    // =====================================================================
    //  V2 - Wurst
    // =====================================================================
    private void tickV2() {
        // Finish a two-click move from the previous tick.
        if (v2NextTickSlot != -1) {
            clickSlot(v2NextTickSlot);
            v2NextTickSlot = -1;
        }

        int nextTotemSlot = findTotemScreenSlot();
        totems = countTotems();

        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            v2WasTotemInOffhand = true;
            return;
        }
        if (v2WasTotemInOffhand) {
            v2Timer = v2Delay.getValue().intValue();
            v2WasTotemInOffhand = false;
        }
        if (nextTotemSlot == -1) return;

        float healthF = v2Health.getValue().floatValue();
        if (healthF > 0 && mc.player.getHealth() > healthF * 2f) return;

        // Don't move items while a container other than the inventory is open.
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) {
            String n = mc.currentScreen.getClass().getSimpleName();
            if (n.contains("Container") || n.contains("Chest") || n.contains("Shulker")) return;
        }

        if (v2Timer > 0) {
            v2Timer--;
            return;
        }

        boolean offhandEmpty = mc.player.getOffHandStack().isEmpty();
        clickSlot(nextTotemSlot);
        clickSlot(45);
        if (!offhandEmpty) v2NextTickSlot = nextTotemSlot;
    }

    // =====================================================================
    //  V3 - LiquidBounce
    // =====================================================================
    private void tickV3() {
        long now = System.currentTimeMillis();
        totems = countTotems();

        if (mc.player.isCreative() || mc.player.isSpectator()) return;

        boolean should = healthBelowThresholdV3();

        if (should) {
            if (mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                if (now - v3LastSwitch >= v3SwitchDelay.getValue().longValue()) {
                    int slot = findTotemScreenSlot();
                    if (slot != -1) {
                        v3LastTotemScreenSlot = slot;
                        clickSlot(slot);
                        clickSlot(45);
                        if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) clickSlot(slot);
                        v3LastSwitch = now;
                        v3Holding = true;
                    }
                }
            } else {
                v3LastSwitch = now;
                v3Holding = true;
            }
        } else if (v3SwitchBack.isEnabled() && v3Holding
                && mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            if (now - v3LastSwitch >= v3SwitchBackDelay.getValue().longValue()) {
                // Swap the totem back out to where it came from.
                int slot = findTotemScreenSlot();
                if (slot != -1 && v3LastTotemScreenSlot != -1) {
                    clickSlot(45);
                    clickSlot(v3LastTotemScreenSlot);
                    if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) clickSlot(45);
                    v3Holding = false;
                    v3LastSwitchBack = now;
                }
            }
        }
    }

    private boolean healthBelowThresholdV3() {
        if (!v3Health.isEnabled()) return true;

        if (v3MissingArmor.isEnabled()) {
            for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                if (mc.player.getEquippedStack(s).isEmpty()) return true;
            }
        }

        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean safetyOperating = v3Safety.isEnabled() && (isBurrowed() || isInHole());
        float allowedDamage = health - (safetyOperating
                ? v3SafeHealth.getValue().floatValue()
                : v3HealthThreshold.getValue().floatValue());

        if (allowedDamage <= 0f) return true;
        if (!v3SubtractCalculatedDamage.isEnabled()) allowedDamage = health;

        float calculated = damageFromEntities();
        if (calculated >= allowedDamage) return true;
        calculated = Math.max(calculated, v3PredictExplosionBlocks.isEnabled() ? 0f : 0f);
        if (calculated >= allowedDamage) return true;
        if (v3PredictFallDamage.isEnabled()) calculated += predictedFallDamage();
        return calculated >= allowedDamage;
    }

    private float damageFromEntities() {
        if (!v3PredictExplosionEntities.isEnabled()) return 0f;
        float max = 0f;
        try {
            for (Entity e : mc.world.getEntities()) {
                double dist = e.distanceTo(mc.player);
                if (dist > 10.0) continue;
                if (e instanceof EndCrystalEntity) max = Math.max(max, (float) Math.max(0, 12 - dist * 1.5));
                else if (e instanceof TntEntity) max = Math.max(max, (float) Math.max(0, 10 - dist * 1.2));
            }
        } catch (Throwable ignored) {
        }
        return max;
    }

    private float predictedFallDamage() {
        if (v3IgnoreElytra.isEnabled() && mc.player.isGliding()) return 0f;
        double fall = mc.player.fallDistance;
        if (fall <= 3.0) return 0f;
        return (float) (fall - 3.0);
    }

    private boolean isBurrowed() {
        try {
            return !mc.world.getBlockState(mc.player.getBlockPos()).getCollisionShape(mc.world, mc.player.getBlockPos()).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isInHole() {
        try {
            net.minecraft.util.math.BlockPos p = mc.player.getBlockPos();
            for (net.minecraft.util.math.Direction d : new net.minecraft.util.math.Direction[]{
                    net.minecraft.util.math.Direction.NORTH, net.minecraft.util.math.Direction.SOUTH,
                    net.minecraft.util.math.Direction.EAST, net.minecraft.util.math.Direction.WEST}) {
                net.minecraft.util.math.BlockPos n = p.offset(d);
                if (mc.world.getBlockState(n).getCollisionShape(mc.world, n).isEmpty()) return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // =====================================================================
    //  V4 - Vape
    // =====================================================================
    private void tickV4() {
        if (mc.player.hurtTime > 0 || mc.player.getAttacker() != null) v4Danger++;
        else if (v4Danger > 0) v4Danger--;

        if (v4CombatOnly.isEnabled() && v4Danger < v4ActivationDelay.getValue().intValue()) return;
        if (v4Cooldown > 0) {
            v4Cooldown--;
            return;
        }

        boolean offhandTotem = mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING;
        if (!offhandTotem) {
            moveTotemV4();
            return;
        }
        if (v4OpenedByUs && v4CloseInventory.isEnabled() && mc.currentScreen instanceof InventoryScreen) {
            mc.setScreen(null);
            v4OpenedByUs = false;
        }
        if (v4Refill.isEnabled()) refillMainHandV4();
    }

    private void moveTotemV4() {
        List<Integer> slots = totemScreenSlots();
        if (slots.isEmpty()) return;
        if (v4InventoryOnly.isEnabled() && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (v4OpenInventory.isEnabled() && mc.currentScreen == null && !v4SilentOpen.isEnabled()) {
            mc.setScreen(new InventoryScreen(mc.player));
            v4OpenedByUs = true;
            return;
        }
        if (v4SilentOpen.isEnabled() && mc.currentScreen == null) {
            if (v4SilentWait < v4SilentMoveDelay.getValue().intValue()) {
                v4SilentWait++;
                return;
            }
            v4SilentWait = 0;
        }
        int src = v4RandomSlot.isEnabled() ? slots.get(random.nextInt(slots.size())) : slots.get(0);
        clickSlot(src);
        clickSlot(45);
        if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) clickSlot(src);
        int jitter = v4ExtraRandomization.isEnabled() ? random.nextInt(6) : random.nextInt(2);
        v4Cooldown = v4Delay.getValue().intValue() + jitter;
    }

    private void refillMainHandV4() {
        if (!mc.player.getMainHandStack().isEmpty()) return;
        List<Integer> slots = totemScreenSlots();
        if (slots.isEmpty()) return;
        int src = v4RandomSlot.isEnabled() ? slots.get(random.nextInt(slots.size())) : slots.get(0);
        int hotbar = mc.player.getInventory().getSelectedSlot();
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, src, hotbar, SlotActionType.SWAP, mc.player);
        v4Cooldown = v4Delay.getValue().intValue();
    }

    // =====================================================================
    //  Shared helpers
    // =====================================================================

    private void clickSlot(int slot) {
        try {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        } catch (Throwable ignored) {
        }
    }

    /** Screen slot of the first totem in the inventory (not the offhand), or -1. */
    private int findTotemScreenSlot() {
        List<Integer> slots = totemScreenSlots();
        return slots.isEmpty() ? -1 : slots.get(0);
    }

    private List<Integer> totemScreenSlots() {
        List<Integer> out = new ArrayList<>();
        for (int inv = 0; inv < 36; inv++) {
            ItemStack s = mc.player.getInventory().getStack(inv);
            if (!s.isEmpty() && s.getItem() == Items.TOTEM_OF_UNDYING) {
                out.add(inv < 9 ? 36 + inv : inv);
            }
        }
        return out;
    }

    private void equipFirstTotem() {
        int slot = findTotemScreenSlot();
        if (slot == -1) return;
        clickSlot(slot);
        clickSlot(45);
        if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) clickSlot(slot);
    }

    private int countTotems() {
        int n = mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING ? 1 : 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) n++;
        }
        return n;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        boolean show = version.is("V2") ? v2ShowCounter.isEnabled() : v4ShowTotemCount.isEnabled();
        if (!show || mc.player == null || mc.getWindow() == null) return;
        String text = "Totems: " + totems;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        context.drawText(mc.textRenderer, text, w / 2 - mc.textRenderer.getWidth(text) / 2, h / 2 + 12, 0xFFFF5555, true);
    }
}
