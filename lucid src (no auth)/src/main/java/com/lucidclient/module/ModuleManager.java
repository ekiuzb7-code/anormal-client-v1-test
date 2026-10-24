package com.lucidclient.module;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry for Lucid Client modules.
 * Currently empty — modules will be added in future versions.
 * The ClickGUI automatically populates each category column from this registry.
 */
public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);

    public ModuleManager() {
        for (Category c : Category.values()) {
            byCategory.put(c, new ArrayList<>());
        }
    }

    public void register(Module module) {
        modules.add(module);
        byCategory.get(module.getCategory()).add(module);
    }

    public List<Module> getAll() { return modules; }

    /**
     * Look up a registered module by its class. Returns the first instance of
     * the given class, or {@code null} if none is registered. Used for
     * cross-module coordination (e.g. ShieldBreaker reading TriggerBot state).
     */
    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        for (Module m : modules) {
            if (clazz.isInstance(m)) return (T) m;
        }
        return null;
    }

    public List<Module> getByCategory(Category category) {
        return byCategory.getOrDefault(category, new ArrayList<>());
    }

    public int countByCategory(Category category) {
        return byCategory.getOrDefault(category, new ArrayList<>()).size();
    }

    public int totalCount() { return modules.size(); }

    public boolean isEmpty() { return modules.isEmpty(); }
}
