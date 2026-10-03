package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;
import com.anormal.client.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class Waypoints extends Module {
    public final BooleanSetting addHere = new BooleanSetting("Add Here", "Save current position (max 30)", false);
    public final NumberSetting slot = new NumberSetting("Slot", "Selected waypoint to edit", 1.0, 1.0, 30.0, 1.0);
    public final NumberSetting x = new NumberSetting("X", "Selected X", 0.0, -30000000.0, 30000000.0, 1.0);
    public final NumberSetting y = new NumberSetting("Y", "Selected Y", 64.0, -64.0, 320.0, 1.0);
    public final NumberSetting z = new NumberSetting("Z", "Selected Z", 0.0, -30000000.0, 30000000.0, 1.0);
    public final BooleanSetting on = new BooleanSetting("On", "Selected waypoint visible", true);
    public final ColorSetting color = new ColorSetting("Color", "Selected waypoint color", ColorUtils.rgba(85, 255, 85, 255));
    public final BooleanSetting delete = new BooleanSetting("Delete", "Delete selected waypoint", false);
    public final BooleanSetting clear = new BooleanSetting("Clear All", "Forget all waypoints", false);
    public final BooleanSetting showDistance = new BooleanSetting("Show Distance", "Show distance on marker", true);
    public final BooleanSetting showName = new BooleanSetting("Show Name", "Show name on marker", true);
    public final BooleanSetting showCoords = new BooleanSetting("Show Coords", "Show coordinates on marker", true);
    public final BooleanSetting chatConfirm = new BooleanSetting("Chat Confirm", "Chat message when adding", true);

    public static final class Waypoint {
        public double x, y, z;
        public String dim;
        public int color;
        public boolean on;
        public String name;
        Waypoint(double x, double y, double z, String dim, int color) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dim = dim;
            this.color = color;
            this.on = true;
            this.name = "WP";
        }
    }

    private final List<Waypoint> points = new ArrayList<>();
    private int lastSlot = -1;
    private boolean dirty = false;
    private int saveTimer = 0;

    public List<Waypoint> getPoints() {
        return points;
    }

    // Screen position of a waypoint marker for editor hit-testing/rename
    public int[] markerScreenPos(int i, float tickDelta) {
        if (i < 0 || i >= points.size() || mc.player == null) return null;
        Waypoint w = points.get(i);
        return com.anormal.client.util.ProjectionUtil.project(new Vec3d(w.x, w.y + 1.0, w.z), tickDelta);
    }

    public void togglePoint(int i) {
        if (i < 0 || i >= points.size()) return;
        Waypoint w = points.get(i);
        w.on = !w.on;
        try {
            if (slot.getValue().intValue() == i + 1) on.setValue(w.on);
        } catch (Throwable ignored) {}
        saveWaypoints();
    }

    public void renamePoint(int i, String name) {
        if (i < 0 || i >= points.size() || name == null) return;
        points.get(i).name = name.isEmpty() ? ("WP" + (i + 1)) : name;
        saveWaypoints();
    }

    // Auto-save: waypoints survive restarts like HUD layout
    public static void saveWaypoints() {
        try {
            com.anormal.client.module.Module m = com.anormal.client.module.ModuleManager.getModule(Waypoints.class);
            if (!(m instanceof Waypoints wp)) return;
            java.io.File dir = new java.io.File(net.minecraft.client.MinecraftClient.getInstance().runDirectory, "config/anormal");
            dir.mkdirs();
            java.util.List<String> lines = new java.util.ArrayList<>();
            for (Waypoint w : wp.points) {
                String safeName = w.name.replace(";", "").replace("\n", "");
                lines.add(safeName + ";" + w.x + ";" + w.y + ";" + w.z + ";" + w.dim + ";" + w.color + ";" + w.on);
            }
            java.nio.file.Files.write(new java.io.File(dir, "waypoints.txt").toPath(), lines);
        } catch (Throwable ignored) {}
    }

    public static void loadWaypoints() {
        try {
            com.anormal.client.module.Module m = com.anormal.client.module.ModuleManager.getModule(Waypoints.class);
            if (!(m instanceof Waypoints wp)) return;
            java.io.File f = new java.io.File(net.minecraft.client.MinecraftClient.getInstance().runDirectory, "config/anormal/waypoints.txt");
            if (!f.exists()) return;
            wp.points.clear();
            for (String line : java.nio.file.Files.readAllLines(f.toPath())) {
                try {
                    String[] p = line.split(";", -1);
                    if (p.length < 7) continue;
                    Waypoint w = new Waypoint(Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                            Double.parseDouble(p[3]), p[4], Integer.parseInt(p[5]));
                    w.name = p[0].isEmpty() ? ("WP" + (wp.points.size() + 1)) : p[0];
                    w.on = Boolean.parseBoolean(p[6]);
                    if (wp.points.size() < 30) wp.points.add(w);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    public Waypoints() {
        super("Waypoints", "Unlimited custom map markers", Category.RENDER);
        addSetting(addHere);
        addSetting(slot);
        addSetting(x);
        addSetting(y);
        addSetting(z);
        addSetting(on);
        addSetting(color);
        addSetting(delete);
        addSetting(clear);
        addSetting(showDistance);
        addSetting(showName);
        addSetting(showCoords);
        addSetting(chatConfirm);
    }

    private String curDim() {
        try {
            return mc.world.getRegistryKey().getValue().getPath();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private Waypoint selected() {
        int i = slot.getValue().intValue() - 1;
        if (i < 0 || i >= points.size()) return null;
        return points.get(i);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (clear.isEnabled()) {
            clear.setValue(false);
            points.clear();
            lastSlot = -1;
            saveWaypoints();
            return;
        }
        if (addHere.isEnabled()) {
            addHere.setValue(false);
            if (points.size() < 30) {
                try {
                    Waypoint w = new Waypoint(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                            curDim(), color.getValue());
                    w.name = "WP" + (points.size() + 1);
                    points.add(w);
                    slot.setValue((double) points.size());
                    if (chatConfirm.isEnabled() && mc.inGameHud != null) {
                        mc.inGameHud.getChatHud().addMessage(Text.literal(
                                String.format("§a[Waypoint %d] §f%.0f / %.0f / %.0f (%s)",
                                        points.size(), w.x, w.y, w.z, w.dim)));
                    }
                } catch (Throwable ignored) {}
            }
            saveWaypoints();
        }
        if (delete.isEnabled()) {
            delete.setValue(false);
            Waypoint w = selected();
            if (w != null) {
                points.remove(w);
                lastSlot = -1;
                saveWaypoints();
            }
            return;
        }
        // Push selected waypoint values into editors when selection changes
        int sel = slot.getValue().intValue();
        if (sel != lastSlot) {
            lastSlot = sel;
            Waypoint w = selected();
            if (w != null) {
                try {
                    x.setValue(w.x);
                    y.setValue(w.y);
                    z.setValue(w.z);
                    on.setValue(w.on);
                    color.setValue(w.color);
                } catch (Throwable ignored) {}
            }
        } else {
            // Pull editor values back into selected waypoint (saved periodically)
            Waypoint w = selected();
            if (w != null) {
                try {
                    double nx = x.getValue(), ny = y.getValue(), nz = z.getValue();
                    boolean no = on.isEnabled();
                    int nc = color.getValue();
                    if (nx != w.x || ny != w.y || nz != w.z || no != w.on || nc != w.color) {
                        w.x = nx;
                        w.y = ny;
                        w.z = nz;
                        w.on = no;
                        w.color = nc;
                        dirty = true;
                    }
                } catch (Throwable ignored) {}
            }
        }
        if (dirty && ++saveTimer > 100) {
            saveTimer = 0;
            dirty = false;
            saveWaypoints();
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (mc.player == null || points.isEmpty()) return;
        String dim = curDim();
        for (int i = 0; i < points.size(); i++) {
            Waypoint w = points.get(i);
            if (!w.on) continue;
            if (!w.dim.isEmpty() && !w.dim.equals(dim)) continue;
            int[] sc = com.anormal.client.util.ProjectionUtil.project(new Vec3d(w.x, w.y + 1.0, w.z), tickDelta);
            if (sc == null) continue;
            int col = w.color;
            context.fill(sc[0] - 1, sc[1] - 7, sc[0] + 1, sc[1] + 7, col);
            context.fill(sc[0] - 7, sc[1] - 1, sc[0] + 7, sc[1] + 1, col);
            context.fill(sc[0] - 4, sc[1] - 4, sc[0] + 4, sc[1] + 4, (col & 0x00FFFFFF) | 0x66000000);
            if (mc.textRenderer != null) {
                StringBuilder label = new StringBuilder();
                label.append("§6").append(i + 1).append(" ");
                if (showName.isEnabled()) label.append("§e").append(w.name).append(" ");
                if (showCoords.isEnabled()) {
                    label.append(String.format("§f%.0f/%.0f/%.0f", w.x, w.y, w.z));
                }
                if (showDistance.isEnabled()) {
                    double dx = w.x - mc.player.getX();
                    double dy = w.y - mc.player.getY();
                    double dz = w.z - mc.player.getZ();
                    label.append(" §7").append((int) Math.sqrt(dx * dx + dy * dy + dz * dz)).append("m");
                }
                String text = label.toString();
                RenderUtils.drawText(context, mc.textRenderer, text,
                        sc[0] - mc.textRenderer.getWidth(text) / 2, sc[1] + 9, 0xFFFFFFFF, true);
            }
        }
    }

}
