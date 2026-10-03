package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChestStealer extends Module {
    public final BooleanSetting checkInMenu = new BooleanSetting("Check In Menu", "Only loot vanilla chests, skip custom menus", true);
    public final BooleanSetting bestOnly = new BooleanSetting("Best Only", "Only take items better than yours", false);
    public final BooleanSetting keepOpen = new BooleanSetting("Keep Open", "Keep chest open after looting", false);
    public final BooleanSetting shuffle = new BooleanSetting("Shuffle", "Take items in random order", true);
    public final NumberSetting minDelay = new NumberSetting("Min Delay", "Min ticks between clicks", 1.0, 0.0, 10.0, 1.0);
    public final NumberSetting maxDelay = new NumberSetting("Max Delay", "Max ticks between clicks", 3.0, 0.0, 10.0, 1.0);
    public final BooleanSetting blacklist = new BooleanSetting("Blacklist", "Skip junk items (dirt, cobble, seeds)", true);

    private int timer = 0;

    public ChestStealer() {
        super("ChestStealer", "Automatically loots items from chests and containers", Category.PLAYER);
        addSetting(checkInMenu);
        addSetting(bestOnly);
        addSetting(keepOpen);
        addSetting(shuffle);
        addSetting(minDelay);
        addSetting(maxDelay);
        addSetting(blacklist);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) return;
        // Check In Menu: only vanilla container sizes (27/54), skip custom server menus
        if (checkInMenu.isEnabled()) {
            int rows = handler.getInventory().size();
            if (rows != 27 && rows != 54) return;
        }
        if (timer > 0) {
            timer--;
            return;
        }

        int containerSlots = handler.getInventory().size();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < containerSlots; i++) order.add(i);
        if (shuffle.isEnabled()) Collections.shuffle(order);

        boolean tookSomething = false;
        for (int i : order) {
            try {
                if (!handler.getSlot(i).hasStack()) continue;
                ItemStack stack = handler.getSlot(i).getStack();
                if (blacklist.isEnabled() && isJunk(stack)) continue;
                if (bestOnly.isEnabled() && !isUpgrade(stack)) continue;
                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                tookSomething = true;
                break;
            } catch (Throwable ignored) {}
        }

        if (tookSomething) {
            int min = Math.min(minDelay.getValue().intValue(), maxDelay.getValue().intValue());
            int max = Math.max(minDelay.getValue().intValue(), maxDelay.getValue().intValue());
            timer = min + (max > min ? (int) (Math.random() * (max - min + 1)) : 0);
        } else if (!keepOpen.isEnabled()) {
            // Chest empty (or nothing worth taking): close it
            try {
                mc.player.closeHandledScreen();
            } catch (Throwable ignored) {}
        }
    }

    private boolean isJunk(ItemStack stack) {
        try {
            String path = Registries.ITEM.getId(stack.getItem()).getPath();
            return path.equals("dirt") || path.equals("coarse_dirt") || path.equals("cobblestone")
                    || path.equals("gravel") || path.equals("sand") || path.equals("wheat_seeds")
                    || path.equals("beetroot_seeds") || path.equals("pumpkin_seeds") || path.equals("melon_seeds")
                    || path.equals("stick") || path.equals("string") || path.equals("rotten_flesh")
                    || path.equals("poisonous_potato") || path.equals("cobweb") || path.equals("torch")
                    || path.equals("arrow") && stack.getCount() < 8;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isUpgrade(ItemStack stack) {
        try {
            // Tools/armor: take if better material tier than what we hold anywhere
            String path = Registries.ITEM.getId(stack.getItem()).getPath();
            boolean isGear = path.endsWith("_sword") || path.endsWith("_axe") || path.endsWith("_pickaxe")
                    || path.endsWith("_helmet") || path.endsWith("_chestplate")
                    || path.endsWith("_leggings") || path.endsWith("_boots") || path.equals("bow")
                    || path.equals("crossbow") || path.equals("mace") || path.equals("trident")
                    || path.equals("shield");
            if (!isGear) return true; // non-gear always worth taking
            int tier = tierOf(path);
            for (int i = 0; i < 36; i++) {
                try {
                    String have = Registries.ITEM.getId(mc.player.getInventory().getStack(i).getItem()).getPath();
                    if (tierOf(have) >= tier && sameKind(have, path)) return false;
                } catch (Throwable ignored) {}
            }
            return true;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private int tierOf(String path) {
        if (path.startsWith("netherite")) return 6;
        if (path.startsWith("diamond")) return 5;
        if (path.startsWith("iron")) return 4;
        if (path.startsWith("golden")) return 3;
        if (path.startsWith("stone")) return 2;
        if (path.startsWith("copper")) return 2;
        if (path.startsWith("chainmail")) return 2;
        if (path.startsWith("wooden") || path.startsWith("leather")) return 1;
        return 0;
    }

    private boolean sameKind(String a, String b) {
        String ka = a.substring(a.lastIndexOf('_') + 1);
        String kb = b.substring(b.lastIndexOf('_') + 1);
        return ka.equals(kb);
    }
}
