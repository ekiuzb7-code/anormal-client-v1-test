package dev.zaza.client.module;

import java.util.List;
import java.util.ArrayList;

public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        modules.add(new dev.zaza.client.module.impl.CombatModule("aim assist", "aimassist"));
        modules.add(new dev.zaza.client.module.impl.MovementModule("sprint", "sprint"));
        modules.add(new dev.zaza.client.module.impl.MovementModule("no fall", "nofall"));
        modules.add(new dev.zaza.client.module.impl.MovementModule("safe walk", "safewalk"));
        modules.add(new dev.zaza.client.module.impl.VisualModule("full bright", "fullbright"));
        modules.add(new dev.zaza.client.module.impl.VisualModule("no hurt cam", "nohurtcam"));
        modules.add(new dev.zaza.client.module.impl.VisualModule("custom fov", "customfov"));
        modules.add(new dev.zaza.client.module.impl.VisualModule("no rain", "norain"));
        modules.add(new dev.zaza.client.module.impl.UtilityModule("fps", "fps"));
        modules.add(new dev.zaza.client.module.impl.UtilityModule("coordinates", "coordinates"));
        modules.add(new dev.zaza.client.module.impl.UtilityModule("auto tool", "autotool"));
        modules.add(new dev.zaza.client.module.impl.UtilityModule("fast place", "fastplace"));
    }

    public List<Module> all() {
        return List.copyOf(modules);
    }

    public List<Module> byCategory(dev.zaza.client.module.Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : modules) {
            if (module.category() == category) result.add(module);
        }
        return result;
    }

    public Module byId(String id) {
        for (Module module : modules) {
            if (module.id().equals(id)) return module;
        }
        return null;
    }

    public void tick() {
        for (Module module : modules) {
            if (module.enabled()) module.tick();
        }
    }
}
