package com.anormal.client.gui;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import com.anormal.client.module.impl.render.Waypoints;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.theme.ThemeManager;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class HudEditorScreen extends Screen {
    private final Screen parentScreen;
    private HudElement draggingElement = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public static class HudElement {
        public final String name;
        public final Module module;
        public final NumberSetting posX;
        public final NumberSetting posY;
        public int width;
        public int height;

        public HudElement(String name, Module module, NumberSetting posX, NumberSetting posY, int width, int height) {
            this.name = name;
            this.module = module;
            this.posX = posX;
            this.posY = posY;
            this.width = width;
            this.height = height;
        }

        public int getX() { return posX.getValue().intValue(); }
        public int getY() { return posY.getValue().intValue(); }

        public void setPos(int x, int y) {
            posX.setValue((double) x);
            posY.setValue((double) y);
        }

        public boolean isHovered(int mouseX, int mouseY) {
            int x = getX(), y = getY();
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private final List<HudElement> elements = new ArrayList<>();
    private int renameWpIdx = -1;
    private final StringBuilder renameBuf = new StringBuilder();

    public HudEditorScreen(Screen parentScreen) {
        super(Text.literal("HUD Editor"));
        this.parentScreen = parentScreen;
    }

    /** Finds posX/posY NumberSettings on any module via reflection. Null if not movable. */
    public static NumberSetting[] findPosSettings(Module module) {
        NumberSetting px = null, py = null;
        for (Field f : module.getClass().getFields()) {
            if (f.getType() == NumberSetting.class) {
                try {
                    NumberSetting s = (NumberSetting) f.get(module);
                    if (s == null) continue;
                    String n = s.getName().toLowerCase();
                    if (n.contains("pos") && n.contains("x")) px = s;
                    else if (n.contains("pos") && n.contains("y")) py = s;
                } catch (Throwable ignored) {}
            }
        }
        if (px == null || py == null) return null;
        return new NumberSetting[]{px, py};
    }

    // Live-measured boxes: same strings the modules draw, so frames match content.
    // Falls back to defaultSize when the format is dynamic or unknown.
    private int[] measureElement(Module module) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            switch (module.getName()) {
                case "Clock": {
                    String t = "TIME: " + java.time.LocalTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                    return new int[]{textRenderer.getWidth(t) + 8, 14};
                }
                case "FPS": {
                    int fps = 60;
                    try {
                        fps = mc.getCurrentFps();
                    } catch (Throwable ignored) {}
                    return new int[]{textRenderer.getWidth("FPS: " + fps) + 8, 14};
                }
                case "Coords": {
                    String c = "XYZ: 100.0 / 64.0 / 100.0 (N)";
                    try {
                        if (mc.player != null) {
                            c = String.format("XYZ: %.1f / %.1f / %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
                        }
                    } catch (Throwable ignored) {}
                    return new int[]{textRenderer.getWidth(c) + 8, 14};
                }
                case "ReachDisplay": {
                    return new int[]{textRenderer.getWidth("Reach: 3.50 blocks") + 8, 14};
                }
                case "MemoryHUD": {
                    long used = 0, total = 1;
                    try {
                        used = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576L;
                        total = Runtime.getRuntime().maxMemory() / 1048576L;
                    } catch (Throwable ignored) {}
                    String l1 = "RAM: " + used + "/" + total + " MB";
                    String l2 = "Max: " + total + " MB";
                    return new int[]{Math.max(textRenderer.getWidth(l1), textRenderer.getWidth(l2)) + 8, 28};
                }
                case "RenderStats": {
                    int fps = 60, ents = 0;
                    try {
                        fps = mc.getCurrentFps();
                    } catch (Throwable ignored) {}
                    try {
                        if (mc.world != null) {
                            for (Object e : mc.world.getEntities()) {
                                if (++ents > 500) break;
                            }
                        }
                    } catch (Throwable ignored) {}
                    String t = "FPS: " + fps + " " + String.format("%.1f", 1000.0 / Math.max(1, fps)) + "ms Ents: " + ents;
                    return new int[]{textRenderer.getWidth(t) + 8, 14};
                }
                case "DirectionHUD": {
                    float yaw = 0;
                    try {
                        if (mc.player != null) yaw = (mc.player.getYaw() % 360 + 360) % 360;
                    } catch (Throwable ignored) {}
                    String dir = yaw >= 315 || yaw < 45 ? "S" : (yaw >= 45 && yaw < 135 ? "W" : (yaw >= 135 && yaw < 225 ? "N" : "E"));
                    String full = dir.equals("N") ? "North" : dir.equals("S") ? "South" : dir.equals("E") ? "East" : "West";
                    return new int[]{textRenderer.getWidth("- " + dir + " - " + full + " [" + (int) yaw + "]") + 8, 14};
                }
                case "TravelDistance": {
                    return new int[]{textRenderer.getWidth("Walked: 10000m (total 10000m)") + 8, 14};
                }
                case "CoordinateShare": {
                    String t = "XYZ: 100 / -60 / 31 (overworld)";
                    try {
                        if (mc.player != null) {
                            String dim = "";
                            try {
                                dim = mc.world.getRegistryKey().getValue().getPath();
                            } catch (Throwable ignored) {}
                            t = String.format("XYZ: %.0f / %.0f / %.0f (%s)", mc.player.getX(), mc.player.getY(), mc.player.getZ(), dim);
                        }
                    } catch (Throwable ignored) {}
                    return new int[]{textRenderer.getWidth(t) + 8, 14};
                }
                case "PlayerLogger": {
                    return new int[]{textRenderer.getWidth("Seen: 99") + 8, 14};
                }
                case "FoodStatus": {
                    return new int[]{textRenderer.getWidth("Hunger: 20/20 Sat: 20.0") + 8, 14};
                }
                case "ItemCounter": {
                    String label = "Obsidian";
                    try {
                        if (mc.player != null && !mc.player.getMainHandStack().isEmpty()) {
                            label = mc.player.getMainHandStack().getName().getString();
                        }
                    } catch (Throwable ignored) {}
                    return new int[]{textRenderer.getWidth(label + ": 64") + 8, 14};
                }
                case "BlockCounter": {
                    return new int[]{textRenderer.getWidth("Obsidian: 64") + 8, 14};
                }
                case "ToolDurability": {
                    return new int[]{textRenderer.getWidth("Diamond Pickaxe 1561") + 8, 14};
                }
                case "ArmorDurability": {
                    return new int[]{textRenderer.getWidth("Elytra: 432/432 (100%)") + 8, 14};
                }
                case "EnchantHelper": {
                    return new int[]{textRenderer.getWidth("No enchantments") + 8, 14};
                }
                case "AnvilHelper": {
                    return new int[]{textRenderer.getWidth("Anvil: 12 levels") + 8, 14};
                }
                case "BiomeHUD": {
                    return new int[]{textRenderer.getWidth("Plains | 0.8") + 8, 14};
                }
                case "DuelInfo": {
                    return new int[]{120, 52};
                }
                case "TextGUI": {
                    int count = 0;
                    try {
                        for (Module m : ModuleManager.getModules()) {
                            if (m.isEnabled()) count++;
                        }
                    } catch (Throwable ignored) {}
                    return new int[]{170, 20 + Math.max(1, count) * 13};
                }
                case "Scoreboard": {
                    return new int[]{130, 60};
                }
                case "PartyOverlay": {
                    int n = 0;
                    try {
                        if (mc.world != null && mc.player != null) {
                            for (Object e : mc.world.getEntities()) {
                                if (e instanceof net.minecraft.entity.player.PlayerEntity
                                        && e != mc.player && n < 6) n++;
                            }
                        }
                    } catch (Throwable ignored) {}
                    return new int[]{150, 18 + Math.max(1, n) * 11};
                }
                case "PotionStatus": {
                    int n = 0;
                    try {
                        if (mc.player != null) n = mc.player.getStatusEffects().size();
                    } catch (Throwable ignored) {}
                    return new int[]{150, 14 + Math.max(1, n) * 12};
                }
                case "ArmorStatus": {
                    int n = 0;
                    try {
                        if (mc.player != null) {
                            for (net.minecraft.entity.EquipmentSlot s : new net.minecraft.entity.EquipmentSlot[]{
                                    net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                                    net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET}) {
                                if (!mc.player.getEquippedStack(s).isEmpty()) n++;
                            }
                        }
                    } catch (Throwable ignored) {}
                    return new int[]{72, Math.max(20, n * 18 + 4)};
                }
                case "Watermark": {
                    try {
                        com.anormal.client.module.impl.legit.Watermark wm =
                                ModuleManager.getModule(com.anormal.client.module.impl.legit.Watermark.class);
                        if (wm != null) {
                            double s = wm.scale.getValue();
                            boolean banner = wm.logo.is("Logo 2");
                            int tw = banner ? 1024 : 512;
                            int th = banner ? 256 : 128;
                            return new int[]{(int) (tw * s) + 4, (int) (th * s) + 4};
                        }
                    } catch (Throwable ignored) {}
                    return new int[]{132, 36};
                }
                case "PerformanceOverlay": {
                    int fps = 60;
                    try {
                        fps = mc.getCurrentFps();
                    } catch (Throwable ignored) {}
                    String l1 = "FPS: " + fps + " 1% low: " + fps;
                    String l2 = "Mem: 512 MB";
                    return new int[]{Math.max(textRenderer.getWidth(l1), textRenderer.getWidth(l2)) + 8, 52};
                }
                default:
                    return null;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int[] defaultSize(Module module) {
        if (module instanceof com.anormal.client.module.impl.legit.Keystrokes ks) {
            try {
                double scale = ks.scale.getValue();
                int size = (int) (20 * scale);
                int gap = (int) (2 * scale);
                int w = size * 3 + gap * 2;
                int h = (size + gap) * 2;
                if (ks.showLmbRmb.isEnabled()) h += (int) (22 * scale) + gap;
                if (ks.showSpace.isEnabled()) h += (int) (12 * scale) + gap;
                return new int[]{w + 4, h + 4};
            } catch (Throwable ignored) {}
            return new int[]{72, 114};
        }
        return switch (module.getName()) {
            case "Radar" -> new int[]{74, 74};
            case "TargetInfo" -> new int[]{124, 40};
            case "InventoryOverlay" -> new int[]{170, 66};
            case "ArmorStatus" -> new int[]{72, 80};
            case "PotionStatus" -> new int[]{150, 60};
            case "Compass" -> new int[]{104, 18};
            case "ReachDisplay" -> new int[]{130, 16};
            case "Clock" -> new int[]{96, 16};
            case "FPS" -> new int[]{70, 16};
            case "Coords" -> new int[]{200, 16};
            case "DuelInfo" -> new int[]{120, 50};
            case "PartyOverlay" -> new int[]{150, 50};
            case "Rearview" -> new int[]{130, 36};
            case "Scoreboard" -> new int[]{130, 100};
            case "Watermark" -> new int[]{132, 36};
            case "Waypoints" -> new int[]{150, 40};
            case "PerformanceOverlay" -> new int[]{150, 64};
            case "TextGUI" -> new int[]{130, 150};
            case "ToolDurability" -> new int[]{100, 16};
            case "ArmorDurability" -> new int[]{140, 16};
            case "FoodStatus" -> new int[]{140, 16};
            case "ItemCounter" -> new int[]{90, 16};
            case "BlockCounter" -> new int[]{90, 16};
            case "EnchantHelper" -> new int[]{90, 16};
            case "AnvilHelper" -> new int[]{90, 16};
            case "MemoryHUD" -> new int[]{120, 30};
            case "RenderStats" -> new int[]{160, 16};
            case "DirectionHUD" -> new int[]{70, 16};
            case "TravelDistance" -> new int[]{140, 16};
            case "CoordinateShare" -> new int[]{190, 16};
            case "PlayerLogger" -> new int[]{120, 30};
            case "EnemyArmor" -> new int[]{120, 92};
            case "EnemyInventory" -> new int[]{150, 74};
            default -> new int[]{90, 14};
        };
    }

    @Override
    protected void init() {
        elements.clear();
        // Auto-discover EVERY movable overlay in every section (not just Legit):
        // any module with posX/posY (TextGUI, Waypoints, PerformanceOverlay included)
        for (Module m : ModuleManager.getModules()) {
            if (m.getCategory() == Category.UZNY11) continue;
            NumberSetting[] pos = findPosSettings(m);
            if (pos == null) continue;
            int[] size = measureElement(m);
            if (size == null) size = defaultSize(m);
            elements.add(new HudElement(m.getName(), m, pos[0], pos[1], size[0], size[1]));
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderUtils.fill(context, 0, 0, width, height, ColorUtils.rgba(0, 0, 0, 160));

        String banner = "§6[HUD] §fDrag=move | RClick=on/off | §e[ESC]§f back";
        int bw = textRenderer.getWidth(banner);
        RenderUtils.fill(context, (width - bw) / 2 - 10, 10, (width + bw) / 2 + 10, 26, ColorUtils.rgba(15, 15, 20, 220));
        RenderUtils.drawBorder(context, (width - bw) / 2 - 10, 10, (width + bw) / 2 + 10, 26, 1, ThemeManager.getAccentColor());
        RenderUtils.drawText(context, textRenderer, banner, (width - bw) / 2, 15, 0xFFFFFFFF, true);

        // Drag follows mouse, clamped to screen
        if (draggingElement != null) {
            int nx = Math.max(0, Math.min(width - draggingElement.width, mouseX - dragOffsetX));
            int ny = Math.max(0, Math.min(height - draggingElement.height, mouseY - dragOffsetY));
            draggingElement.setPos(nx, ny);
        }

        // Live preview: draw each overlay at its real position, then selection frame on top
        for (HudElement el : elements) {
            boolean wasEnabled = el.module.isEnabled();
            try {
                el.module.setEnabled(true);
                el.module.onRender2D(context, delta);
            } catch (Throwable ignored) {
            } finally {
                if (!wasEnabled) el.module.setEnabled(false);
            }

            boolean hovered = el.isHovered(mouseX, mouseY) || el == draggingElement;
            int x = el.getX(), y = el.getY();
            if (!wasEnabled) {
                RenderUtils.fill(context, x, y, x + el.width, y + el.height, ColorUtils.rgba(60, 60, 60, 80));
            }
            int border = !wasEnabled ? ColorUtils.rgba(120, 120, 120, 180)
                    : (hovered ? ThemeManager.getAccentColor() : ColorUtils.rgba(100, 110, 130, 180));
            RenderUtils.drawBorder(context, x, y, x + el.width, y + el.height, 1, border);
            String label = (wasEnabled ? "§a● " : "§c○ ") + "§e" + el.name;
            int lx = x + 4, ly = y - 10 < 28 ? y + 4 : y - 10;
            int lw = textRenderer.getWidth(label);
            RenderUtils.fill(context, lx - 2, ly - 1, lx + lw + 2, ly + 10, ColorUtils.rgba(0, 0, 0, 170));
            RenderUtils.drawText(context, textRenderer, label, lx, ly, 0xFFFFFFFF, true);
        }

        // Waypoints preview + rename indicator (they have no posX/posY element)
        try {
            Waypoints wp = ModuleManager.getModule(Waypoints.class);
            if (wp != null) {
                try {
                    wp.onRender2D(context, delta);
                } catch (Throwable ignored) {}
                java.util.List<Waypoints.Waypoint> pts = wp.getPoints();
                for (int i = 0; i < pts.size(); i++) {
                    int[] sc = wp.markerScreenPos(i, delta);
                    if (sc == null) continue;
                    boolean sel = (i == renameWpIdx);
                    RenderUtils.drawBorder(context, sc[0] - 9, sc[1] - 9, sc[0] + 9, sc[1] + 9, 1,
                            sel ? 0xFFFFFF55 : ColorUtils.rgba(100, 110, 130, 180));
                    if (sel) {
                        String rn = "§e✎ " + renameBuf.toString() + "§6|";
                        RenderUtils.fill(context, sc[0] + 11, sc[1] - 6,
                                sc[0] + 13 + textRenderer.getWidth(rn), sc[1] + 6, ColorUtils.rgba(0, 0, 0, 170));
                        RenderUtils.drawText(context, textRenderer, rn, sc[0] + 12, sc[1] - 4, 0xFFFFFFFF, true);
                    }
                }
            }
        } catch (Throwable ignored) {}

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return handleEditorClick(click.x(), click.y(), click.button());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return handleEditorClick(mouseX, mouseY, button);
    }

    private boolean handleEditorClick(double mouseX, double mouseY, int button) {
        // Waypoint markers: left = select + rename, right = show/hide toggle
        try {
            Waypoints wp = ModuleManager.getModule(Waypoints.class);
            if (wp != null) {
                java.util.List<Waypoints.Waypoint> pts = wp.getPoints();
                for (int i = 0; i < pts.size(); i++) {
                    int[] sc = wp.markerScreenPos(i, 1.0f);
                    if (sc == null) continue;
                    if (Math.abs(mouseX - sc[0]) <= 10 && Math.abs(mouseY - sc[1]) <= 10) {
                        if (button == 0) {
                            try {
                                wp.slot.setValue((double) (i + 1));
                            } catch (Throwable ignored) {}
                            renameWpIdx = i;
                            renameBuf.setLength(0);
                            renameBuf.append(pts.get(i).name);
                        } else if (button == 1) {
                            wp.togglePoint(i);
                            if (renameWpIdx == i) renameWpIdx = -1;
                        }
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        if (button == 0) renameWpIdx = -1;
        for (HudElement el : elements) {
            if (el.isHovered((int) mouseX, (int) mouseY)) {
                if (button == 0) {
                    draggingElement = el;
                    dragOffsetX = (int) mouseX - el.getX();
                    dragOffsetY = (int) mouseY - el.getY();
                    return true;
                } else if (button == 1) {
                    el.module.toggle();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            draggingElement = null;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        // Rename mode: typing goes to waypoint name, ESC/Enter commits
        if (renameWpIdx >= 0) {
            try {
                if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_ENTER) {
                    Waypoints wp = ModuleManager.getModule(Waypoints.class);
                    if (wp != null) wp.renamePoint(renameWpIdx, renameBuf.toString().trim());
                    renameWpIdx = -1;
                    if (input.key() == GLFW.GLFW_KEY_ENTER) return true;
                } else if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                    if (renameBuf.length() > 0) renameBuf.setLength(renameBuf.length() - 1);
                    return true;
                } else {
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            savePositions();
            try {
                com.anormal.client.module.impl.render.Waypoints.saveWaypoints();
            } catch (Throwable ignored) {}
            if (client != null) {
                client.setScreen(parentScreen);
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (renameWpIdx >= 0) {
            try {
                String s = input.asString();
                if (s.length() == 1) {
                    char chr = s.charAt(0);
                    if (chr >= 32 && chr <= 126 && renameBuf.length() < 16) {
                        renameBuf.append(chr);
                    }
                }
            } catch (Throwable ignored) {}
            return true;
        }
        return super.charTyped(input);
    }

    // HUD auto-save: positions survive restarts and server switches
    public static void savePositions() {
        try {
            java.io.File dir = new java.io.File(net.minecraft.client.MinecraftClient.getInstance().runDirectory, "config/anormal");
            dir.mkdirs();
            List<String> lines = new ArrayList<>();
            for (Module m : ModuleManager.getModules()) {
                NumberSetting[] pos = findPosSettings(m);
                if (pos == null) continue;
                lines.add(m.getName() + "=" + pos[0].getValue().intValue() + "," + pos[1].getValue().intValue());
            }
            java.nio.file.Files.write(new java.io.File(dir, "hud.txt").toPath(), lines);
        } catch (Throwable ignored) {}
    }

    public static void loadPositions() {
        try {
            java.io.File f = new java.io.File(net.minecraft.client.MinecraftClient.getInstance().runDirectory, "config/anormal/hud.txt");
            if (!f.exists()) return;
            for (String line : java.nio.file.Files.readAllLines(f.toPath())) {
                try {
                    int eq = line.indexOf('=');
                    int comma = line.indexOf(',');
                    if (eq < 0 || comma < 0) continue;
                    String name = line.substring(0, eq).trim();
                    int px = Integer.parseInt(line.substring(eq + 1, comma).trim());
                    int py = Integer.parseInt(line.substring(comma + 1).trim());
                    for (Module m : ModuleManager.getModules()) {
                        if (!m.getName().equals(name)) continue;
                        NumberSetting[] pos = findPosSettings(m);
                        if (pos == null) break;
                        pos[0].setValue((double) px);
                        pos[1].setValue((double) py);
                        break;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
}
