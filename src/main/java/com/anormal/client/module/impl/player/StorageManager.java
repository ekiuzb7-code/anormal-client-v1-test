package com.anormal.client.module.impl.player;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StorageManager extends Module {
    public final NumberSetting maxChests = new NumberSetting("Max Chests", "Max remembered containers", 20.0, 5.0, 40.0, 1.0);
    public final BooleanSetting chatDump = new BooleanSetting("Chat Dump", "List remembered chests in chat", false);

    private final Map<String, List<String>> memory = new LinkedHashMap<>();

    public StorageManager() {
        super("StorageManager", "Remembers container contents per position", Category.PLAYER);
        addSetting(maxChests);
        addSetting(chatDump);
    }

    private boolean isContainer(String path) {
        return path.contains("chest") || path.equals("barrel") || path.contains("shulker")
                || path.contains("hopper") || path.contains("furnace");
    }

    private String keyFor() {
        try {
            BlockPos origin = mc.player.getBlockPos();
            BlockPos best = null;
            double bestD = 36.0;
            for (int x = -6; x <= 6; x++)
                for (int y = -6; y <= 6; y++)
                    for (int z = -6; z <= 6; z++) {
                        BlockPos p = origin.add(x, y, z);
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            String path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                            if (!isContainer(path)) continue;
                            double d = x * x + y * y + z * z;
                            if (d < bestD) {
                                bestD = d;
                                best = p.toImmutable();
                            }
                        } catch (Throwable ignored) {}
                    }
            if (best != null) return best.getX() + "," + best.getY() + "," + best.getZ();
            return origin.getX() + "," + origin.getY() + "," + origin.getZ();
        } catch (Throwable ignored) {
            return "0,0,0";
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        try {
            if (chatDump.isEnabled()) {
                chatDump.setValue(false);
                if (mc.inGameHud != null) {
                    if (memory.isEmpty()) mc.inGameHud.getChatHud().addMessage(Text.literal("Storage: nothing remembered"));
                    for (Map.Entry<String, List<String>> e : memory.entrySet()) {
                        mc.inGameHud.getChatHud().addMessage(Text.literal("[" + e.getKey() + "] " + String.join(", ", e.getValue())));
                    }
                }
                return;
            }
            if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) return;
            int size = handler.getInventory().size();
            List<String> items = new ArrayList<>();
            for (int i = 0; i < size && items.size() < 5; i++) {
                try {
                    ItemStack s = handler.getSlot(i).getStack();
                    if (!s.isEmpty()) items.add(s.getName().getString() + " x" + s.getCount());
                } catch (Throwable ignored) {}
            }
            if (items.isEmpty()) return;
            String key = keyFor();
            memory.remove(key);
            memory.put(key, items);
            while (memory.size() > maxChests.getValue().intValue()) memory.remove(memory.keySet().iterator().next());
        } catch (Throwable ignored) {}
    }
}
