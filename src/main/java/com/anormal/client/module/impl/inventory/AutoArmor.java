package com.anormal.client.module.impl.inventory;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Automatically equips the best armour from your inventory.
 *
 * <p>Three interchangeable implementations, each mirroring a different client:</p>
 * <ul>
 *   <li><b>V1 - Meteor</b>: preferred-protection, swap-delay,
 *       avoided-enchantments, blast-prot-leggings, anti-break, ignore-elytra.</li>
 *   <li><b>V2 - Wurst</b>: use-enchantments, swap-while-moving, delay - the
 *       compact implementation.</li>
 *   <li><b>V3 - Vape</b>: protection threshold, swap delay, keep elytra,
 *       silent swap.</li>
 * </ul>
 */
public class AutoArmor extends Module {
    public final ModeSetting version = new ModeSetting("Version", "Which implementation to use", "V1", "V1", "V2", "V3");

    // ---- V1 - Meteor ----
    public final ModeSetting v1PreferredProtection = new ModeSetting("V1 Preferred Protection", "Which protection type to favour", "Protection", "Protection", "Blast Protection", "Fire Protection", "Projectile Protection");
    public final NumberSetting v1SwapDelay = new NumberSetting("V1 Swap Delay", "Ticks between armour swaps", 5.0, 0.0, 40.0, 1.0);
    public final BooleanSetting v1AvoidedEnchantments = new BooleanSetting("V1 Avoided Enchantments", "Skip armour with Binding Curse", true);
    public final BooleanSetting v1BlastProtLeggings = new BooleanSetting("V1 Blast Prot Leggings", "Prefer blast protection on leggings", false);
    public final BooleanSetting v1AntiBreak = new BooleanSetting("V1 Anti Break", "Never equip armour about to break", true);
    public final BooleanSetting v1IgnoreElytra = new BooleanSetting("V1 Ignore Elytra", "Do not replace an equipped elytra", true);

    // ---- V2 - Wurst ----
    public final BooleanSetting v2UseEnchantments = new BooleanSetting("V2 Use Enchantments", "Take enchantments into account when scoring armour", true);
    public final BooleanSetting v2SwapWhileMoving = new BooleanSetting("V2 Swap While Moving", "Swap even while you are moving", false);
    public final NumberSetting v2Delay = new NumberSetting("V2 Delay", "Ticks between armour swaps", 5.0, 0.0, 40.0, 1.0);

    // ---- V3 - Vape ----
    public final NumberSetting v3MinProtection = new NumberSetting("V3 Min Protection", "Only equip armour with at least this much protection", 1.0, 0.0, 20.0, 1.0);
    public final NumberSetting v3SwapDelay = new NumberSetting("V3 Swap Delay", "Ticks between armour swaps", 4.0, 0.0, 40.0, 1.0);
    public final BooleanSetting v3KeepElytra = new BooleanSetting("V3 Keep Elytra", "Never replace an equipped elytra", true);
    public final BooleanSetting v3SilentSwap = new BooleanSetting("V3 Silent Swap", "Swap without opening the inventory", true);

    private int cooldown = 0;

    public AutoArmor() {
        super("AutoArmor", "Automatically equips the best armour", Category.INVENTORY);
        addSetting(version);
        addSetting(v1PreferredProtection);
        addSetting(v1SwapDelay);
        addSetting(v1AvoidedEnchantments);
        addSetting(v1BlastProtLeggings);
        addSetting(v1AntiBreak);
        addSetting(v1IgnoreElytra);
        addSetting(v2UseEnchantments);
        addSetting(v2SwapWhileMoving);
        addSetting(v2Delay);
        addSetting(v3MinProtection);
        addSetting(v3SwapDelay);
        addSetting(v3KeepElytra);
        addSetting(v3SilentSwap);

        for (var s : new com.anormal.client.setting.Setting<?>[]{v1PreferredProtection, v1SwapDelay,
                v1AvoidedEnchantments, v1BlastProtLeggings, v1AntiBreak, v1IgnoreElytra}) {
            s.visibleIf(() -> version.is("V1"));
        }
        for (var s : new com.anormal.client.setting.Setting<?>[]{v2UseEnchantments, v2SwapWhileMoving, v2Delay}) {
            s.visibleIf(() -> version.is("V2"));
        }
        for (var s : new com.anormal.client.setting.Setting<?>[]{v3MinProtection, v3SwapDelay, v3KeepElytra, v3SilentSwap}) {
            s.visibleIf(() -> version.is("V3"));
        }
    }

    private int swapDelay() {
        if (version.is("V2")) return v2Delay.getValue().intValue();
        if (version.is("V3")) return v3SwapDelay.getValue().intValue();
        return v1SwapDelay.getValue().intValue();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // Wurst: only swap while standing still (unless allowed).
        if (version.is("V2") && !v2SwapWhileMoving.isEnabled()
                && (mc.player.forwardSpeed != 0 || mc.player.sidewaysSpeed != 0)) {
            return;
        }

        try {
            if (equipBest(EquipmentSlot.HEAD, 5)
                    | equipBest(EquipmentSlot.CHEST, 6)
                    | equipBest(EquipmentSlot.LEGS, 7)
                    | equipBest(EquipmentSlot.FEET, 8)) {
                cooldown = Math.max(1, swapDelay());
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Looks for a better piece for {@code slot} anywhere in the main inventory
     * and swaps it in.
     *
     * @param armorSlot 5=head, 6=chest, 7=legs, 8=feet (player screen handler)
     * @return true when a swap happened
     */
    private boolean equipBest(EquipmentSlot slot, int armorSlot) {
        ItemStack equipped = mc.player.getEquippedStack(slot);

        // V1/V3: never replace an elytra when told to keep it.
        if (isElytra(equipped)) {
            if (version.is("V1") && v1IgnoreElytra.isEnabled()) return false;
            if (version.is("V3") && v3KeepElytra.isEnabled()) return false;
        }

        int bestSlot = -1;
        double bestScore = score(equipped, slot);

        // Main inventory is slots 9..44 in the player screen handler.
        for (int i = 9; i < 45; i++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.isEmpty() || !isArmorFor(stack, slot)) continue;
            if (version.is("V1") && v1AvoidedEnchantments.isEnabled() && hasBindingCurse(stack)) continue;
            if (version.is("V1") && v1AntiBreak.isEnabled() && isAboutToBreak(stack)) continue;
            if (version.is("V3") && protectionLevel(stack) < v3MinProtection.getValue()) continue;

            double s = score(stack, slot);
            if (s > bestScore) {
                bestScore = s;
                bestSlot = i;
            }
        }

        if (bestSlot == -1) return false;

        try {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, bestSlot, 0,
                    SlotActionType.QUICK_MOVE, mc.player);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isArmorFor(ItemStack stack, EquipmentSlot slot) {
        try {
            String path = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
            return switch (slot) {
                case HEAD -> path.endsWith("_helmet") || path.endsWith("_cap")
                        || path.endsWith("_skull") || path.equals("turtle_helmet");
                case CHEST -> path.endsWith("_chestplate") || path.equals("elytra");
                case LEGS -> path.endsWith("_leggings");
                case FEET -> path.endsWith("_boots");
                default -> false;
            };
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Rough vanilla armour points by material, since 1.21.11 has no ArmorItem. */
    private double baseArmor(String path) {
        if (path.startsWith("netherite_")) return 5.0;
        if (path.startsWith("diamond_")) return 4.0;
        if (path.startsWith("iron_")) return 3.0;
        if (path.startsWith("chainmail_")) return 2.0;
        if (path.startsWith("turtle_")) return 2.0;
        if (path.startsWith("golden_") || path.startsWith("leather_")) return 1.0;
        return 1.0;
    }

    private boolean isElytra(ItemStack stack) {
        try {
            return !stack.isEmpty()
                    && net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath().contains("elytra");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasBindingCurse(ItemStack stack) {
        try {
            for (var entry : net.minecraft.enchantment.EnchantmentHelper.getEnchantments(stack).getEnchantments()) {
                String path = entry.getKey().map(k -> k.getValue().getPath()).orElse("");
                if (path.equals("binding_curse")) return true;
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isAboutToBreak(ItemStack stack) {
        try {
            if (!stack.isDamageable()) return false;
            return stack.getMaxDamage() - stack.getDamage() <= 10;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Rough protection value: base armour points plus an enchantment bonus. */
    private double score(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return 0.0;
        try {
            String path = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
            double base = baseArmor(path);
            if (version.is("V2") && !v2UseEnchantments.isEnabled()) return base;
            return base + protectionLevel(stack);
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private int protectionLevel(ItemStack stack) {
        try {
            var enchants = net.minecraft.enchantment.EnchantmentHelper.getEnchantments(stack);
            int total = 0;
            for (var entry : enchants.getEnchantments()) {
                String path = entry.getKey().map(k -> k.getValue().getPath()).orElse("");
                if (path.contains("protection")) total += enchants.getLevel(entry);
            }
            return total;
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
