package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ModeSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class InvCleaner extends Module {
    public final ModeSetting activation = new ModeSetting("Activation", "Toggle cleans always, On Key cleans in bursts", "Toggle", "Toggle", "On Key");
    public final BooleanSetting openInventory = new BooleanSetting("Open Inventory", "Opens inventory when a clean burst starts", true);
    public final BooleanSetting inventoryOnly = new BooleanSetting("Inventory Only", "Only cleans while inventory is open", false);
    public final NumberSetting delay = new NumberSetting("Delay", "Ticks between drops", 3.0, 0.0, 20.0, 1.0);
    public final BooleanSetting bestItems = new BooleanSetting("Best Items", "Keeps the best sword, axe, pickaxe, bow and armor", true);
    public final BooleanSetting removeNegatives = new BooleanSetting("Remove Negative Potions", "Drops potions with harmful effects", true);
    public final BooleanSetting removeFood = new BooleanSetting("Remove Food", "Drops food except golden apples", false);
    public final BooleanSetting blacklist = new BooleanSetting("Blacklist", "Drops common junk items", true);

    private static final Set<String> JUNK = Set.of("stick", "string", "flint", "compass", "feather",
            "glass_bottle", "enchanting_table", "chest", "trapped_chest", "anvil", "chipped_anvil",
            "damaged_anvil", "arrow", "rotten_flesh", "spider_eye", "bone", "snowball", "egg");

    private int cooldown = 0;
    private int burst = 0;

    public InvCleaner() {
        super("InvCleaner", "Automatically cleans and drops junk items from inventory", Category.UZNY11);
        addSetting(activation);
        addSetting(openInventory);
        addSetting(inventoryOnly);
        addSetting(delay);
        addSetting(bestItems);
        addSetting(removeNegatives);
        addSetting(removeFood);
        addSetting(blacklist);
    }

    @Override
    public void onEnable() {
        burst = 80;
        cooldown = 0;
        if (openInventory.isEnabled() && mc.currentScreen == null && mc.player != null) {
            mc.setScreen(new InventoryScreen(mc.player));
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (activation.is("On Key")) {
            if (burst <= 0) return;
            burst--;
        }
        if (inventoryOnly.isEnabled() && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        int slot = findJunkSlot();
        if (slot == -1) return;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, 1, SlotActionType.THROW, mc.player);
        cooldown = delay.getValue().intValue();
    }

    private int findJunkSlot() {
        int bestSword = -1, bestAxe = -1, bestPick = -1, bestBow = -1;
        int[] bestArmor = {-1, -1, -1, -1};
        if (bestItems.isEnabled()) {
            int[] armorScore = {-1, -1, -1, -1};
            for (int i = 0; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s.isEmpty()) continue;
                String p = Registries.ITEM.getId(s.getItem()).getPath();
                if (p.endsWith("_sword") || p.equals("mace")) {
                    if (bestSword == -1 || toolRank(p) > toolRank(pathOf(bestSword))) bestSword = i;
                } else if (p.endsWith("_axe")) {
                    if (bestAxe == -1 || toolRank(p) > toolRank(pathOf(bestAxe))) bestAxe = i;
                } else if (p.endsWith("_pickaxe")) {
                    if (bestPick == -1 || toolRank(p) > toolRank(pathOf(bestPick))) bestPick = i;
                } else if (p.equals("bow")) {
                    bestBow = i;
                } else {
                    int a = armorIndex(s);
                    if (a != -1) {
                        int sc = armorScore(s);
                        if (sc > armorScore[a]) {
                            armorScore[a] = sc;
                            bestArmor[a] = i;
                        }
                    }
                }
            }
            for (int a = 0; a < 4; a++) {
                ItemStack worn = wornArmor(a);
                if (!worn.isEmpty() && armorScore(worn) >= armorScore[a]) bestArmor[a] = -2;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (isJunk(i, bestSword, bestAxe, bestPick, bestBow, bestArmor)) return i;
        }
        for (int i = 0; i < 9; i++) {
            if (isJunk(i, bestSword, bestAxe, bestPick, bestBow, bestArmor)) return i + 36;
        }
        return -1;
    }

    private boolean isJunk(int idx, int sword, int axe, int pick, int bow, int[] armor) {
        ItemStack s = mc.player.getInventory().getStack(idx);
        if (s.isEmpty()) return false;
        String p = Registries.ITEM.getId(s.getItem()).getPath();
        if (idx == sword || idx == axe || idx == pick) return false;
        if (p.equals("bow") && idx == bow) return false;
        int a = armorIndex(s);
        if (a != -1) {
            if (!bestItems.isEnabled()) return false;
            return idx != armor[a];
        }
        if ((p.endsWith("_sword") || p.equals("mace") || p.endsWith("_axe") || p.endsWith("_pickaxe")) && bestItems.isEnabled()) return true;
        if (removeNegatives.isEnabled() && isNegativeSplash(s)) return true;
        if (removeFood.isEnabled() && s.get(DataComponentTypes.FOOD) != null && !p.contains("golden_apple")) return true;
        return blacklist.isEnabled() && JUNK.contains(p);
    }

    private static String pathOf(int idx) {
        ItemStack s = mc.player.getInventory().getStack(idx);
        return Registries.ITEM.getId(s.getItem()).getPath();
    }

    private static int toolRank(String p) {
        if (p.startsWith("netherite_")) return 6;
        if (p.startsWith("diamond_")) return 5;
        if (p.startsWith("iron_")) return 4;
        if (p.startsWith("stone_")) return 3;
        if (p.startsWith("golden_")) return 2;
        return 1;
    }

    private static int armorIndex(ItemStack s) {
        EquippableComponent eq = s.get(DataComponentTypes.EQUIPPABLE);
        if (eq == null) return -1;
        if (eq.slot() == EquipmentSlot.HEAD) return 0;
        if (eq.slot() == EquipmentSlot.CHEST) return 1;
        if (eq.slot() == EquipmentSlot.LEGS) return 2;
        if (eq.slot() == EquipmentSlot.FEET) return 3;
        return -1;
    }

    private static int armorScore(ItemStack s) {
        String p = Registries.ITEM.getId(s.getItem()).getPath();
        int mat = p.startsWith("netherite_") ? 6 : p.startsWith("diamond_") ? 5
                : p.startsWith("turtle_") ? 4 : p.startsWith("iron_") || p.startsWith("chainmail_") ? 3
                : p.startsWith("golden_") ? 2 : 1;
        int score = mat * 1000;
        if (s.getMaxDamage() > 0) score += (int) (100.0 * (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage());
        return score;
    }

    private ItemStack wornArmor(int a) {
        EquipmentSlot slot = a == 0 ? EquipmentSlot.HEAD : a == 1 ? EquipmentSlot.CHEST : a == 2 ? EquipmentSlot.LEGS : EquipmentSlot.FEET;
        return mc.player.getEquippedStack(slot);
    }

    private static boolean isNegativeSplash(ItemStack s) {
        String p = Registries.ITEM.getId(s.getItem()).getPath();
        if (!p.equals("splash_potion") && !p.equals("lingering_potion")) return false;
        PotionContentsComponent c = s.get(DataComponentTypes.POTION_CONTENTS);
        if (c == null) return false;
        List<RegistryEntry<StatusEffect>> bad = List.of(StatusEffects.INSTANT_DAMAGE, StatusEffects.POISON, StatusEffects.SLOWNESS, StatusEffects.WEAKNESS);
        List<StatusEffectInstance> fx = new ArrayList<>();
        c.forEachEffect(fx::add, 1.0f);
        for (StatusEffectInstance i : fx) {
            for (RegistryEntry<StatusEffect> b : bad) {
                if (i.getEffectType().value() == b.value()) return true;
            }
        }
        return false;
    }
}
