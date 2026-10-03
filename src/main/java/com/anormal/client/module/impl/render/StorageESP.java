package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class StorageESP extends Module {
    public final BooleanSetting outlineOpen = new BooleanSetting("Outline Open", "Contrasting outline while a container is open", true);
    public final BooleanSetting chests = new BooleanSetting("Chests", "ESP for chests", true);
    public final ColorSetting chestColor = new ColorSetting("Chest Color", "ESP color for chests", ColorUtils.rgba(255, 170, 0, 255));
    public final BooleanSetting trapped = new BooleanSetting("Trapped Chests", "ESP for trapped chests", true);
    public final ColorSetting trappedColor = new ColorSetting("Trapped Color", "ESP color for trapped chests", ColorUtils.rgba(255, 60, 60, 255));
    public final BooleanSetting ender = new BooleanSetting("Ender Chests", "ESP for ender chests", true);
    public final ColorSetting enderColor = new ColorSetting("Ender Color", "ESP color for ender chests", ColorUtils.rgba(180, 80, 255, 255));
    public final BooleanSetting hoppers = new BooleanSetting("Hoppers", "ESP for hoppers", false);
    public final ColorSetting hopperColor = new ColorSetting("Hopper Color", "ESP color for hoppers", ColorUtils.rgba(150, 150, 150, 255));
    public final BooleanSetting furnaces = new BooleanSetting("Furnaces", "ESP for furnaces", false);
    public final ColorSetting furnaceColor = new ColorSetting("Furnace Color", "ESP color for furnaces", ColorUtils.rgba(120, 120, 120, 255));
    public final BooleanSetting dispensers = new BooleanSetting("Dispensers", "ESP for dispensers", false);
    public final ColorSetting dispenserColor = new ColorSetting("Dispenser Color", "ESP color for dispensers", ColorUtils.rgba(80, 160, 255, 255));
    public final BooleanSetting droppers = new BooleanSetting("Droppers", "ESP for droppers", false);
    public final ColorSetting dropperColor = new ColorSetting("Dropper Color", "ESP color for droppers", ColorUtils.rgba(80, 255, 200, 255));
    public final BooleanSetting shulkers = new BooleanSetting("Shulkers", "ESP for shulker boxes", true);
    public final ColorSetting shulkerColor = new ColorSetting("Shulker Color", "ESP color for shulker boxes", ColorUtils.rgba(200, 120, 255, 255));
    public final BooleanSetting barrels = new BooleanSetting("Barrels", "ESP for barrels", true);
    public final ColorSetting barrelColor = new ColorSetting("Barrel Color", "ESP color for barrels", ColorUtils.rgba(160, 110, 60, 255));
    public final NumberSetting range = new NumberSetting("Range", "Only renders storage within this range", 32.0, 8.0, 64.0, 4.0);

    private final List<BlockPos> cache = new ArrayList<>();
    private final List<Integer> colors = new ArrayList<>();
    private int ticks = 0;

    public StorageESP() {
        super("StorageESP", "Renders glowing highlights around chests, barrels, and shulkers", Category.RENDER);
        addSetting(outlineOpen);
        addSetting(chests);
        addSetting(chestColor);
        addSetting(trapped);
        addSetting(trappedColor);
        addSetting(ender);
        addSetting(enderColor);
        addSetting(hoppers);
        addSetting(hopperColor);
        addSetting(furnaces);
        addSetting(furnaceColor);
        addSetting(dispensers);
        addSetting(dispenserColor);
        addSetting(droppers);
        addSetting(dropperColor);
        addSetting(shulkers);
        addSetting(shulkerColor);
        addSetting(barrels);
        addSetting(barrelColor);
        addSetting(range);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks < 20) return;
        ticks = 0;
        cache.clear();
        colors.clear();
        BlockPos origin = mc.player.getBlockPos();
        int r = range.getValue().intValue();
        try {
            for (int x = -r; x <= r && cache.size() < 128; x++)
                for (int y = -r; y <= r && cache.size() < 128; y++)
                    for (int z = -r; z <= r && cache.size() < 128; z++) {
                        if (x * x + y * y + z * z > r * r) continue;
                        BlockPos p = origin.add(x, y, z);
                        String path;
                        try {
                            if (!mc.world.isChunkLoaded(p)) continue;
                            path = Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).getPath();
                        } catch (Throwable t) {
                            continue;
                        }
                        int c = matchColor(path);
                        if (c == -1) continue;
                        cache.add(p.toImmutable());
                        colors.add(c);
                    }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || cache.isEmpty()) return;
        boolean open = false;
        try {
            open = outlineOpen.isEnabled() && mc.currentScreen != null && mc.player.currentScreenHandler != mc.player.playerScreenHandler;
        } catch (Throwable ignored) {}
        try {
            for (int i = 0; i < cache.size(); i++) {
                BlockPos p = cache.get(i);
                int[] s = com.anormal.client.util.ProjectionUtil.project(new Vec3d(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5), tickDelta);
                if (s == null) continue;
                int c = colors.get(i);
                context.fill(s[0] - 4, s[1] - 4, s[0] + 4, s[1] + 4, (c & 0x00FFFFFF) | 0x55000000);
                context.fill(s[0] - 4, s[1] - 4, s[0] + 4, s[1] - 3, c);
                context.fill(s[0] - 4, s[1] + 3, s[0] + 4, s[1] + 4, c);
                context.fill(s[0] - 4, s[1] - 3, s[0] - 3, s[1] + 3, c);
                context.fill(s[0] + 3, s[1] - 3, s[0] + 4, s[1] + 3, c);
                if (open) {
                    int o = 0xFFFFFFFF;
                    context.fill(s[0] - 5, s[1] - 5, s[0] + 5, s[1] - 4, o);
                    context.fill(s[0] - 5, s[1] + 4, s[0] + 5, s[1] + 5, o);
                    context.fill(s[0] - 5, s[1] - 4, s[0] - 4, s[1] + 4, o);
                    context.fill(s[0] + 4, s[1] - 4, s[0] + 5, s[1] + 4, o);
                }
            }
        } catch (Throwable ignored) {}
    }

    private int matchColor(String path) {
        if (path.equals("trapped_chest")) return trapped.isEnabled() ? trappedColor.getValue() : -1;
        if (path.equals("ender_chest")) return ender.isEnabled() ? enderColor.getValue() : -1;
        if (path.equals("chest")) return chests.isEnabled() ? chestColor.getValue() : -1;
        if (path.equals("hopper")) return hoppers.isEnabled() ? hopperColor.getValue() : -1;
        if (path.contains("furnace")) return furnaces.isEnabled() ? furnaceColor.getValue() : -1;
        if (path.equals("dispenser")) return dispensers.isEnabled() ? dispenserColor.getValue() : -1;
        if (path.equals("dropper")) return droppers.isEnabled() ? dropperColor.getValue() : -1;
        if (path.contains("shulker")) return shulkers.isEnabled() ? shulkerColor.getValue() : -1;
        if (path.equals("barrel")) return barrels.isEnabled() ? barrelColor.getValue() : -1;
        return -1;
    }


}
