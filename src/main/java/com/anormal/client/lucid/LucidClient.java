package com.anormal.client.lucid;

import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compatibility shim for code ported from Lucid Client: the modules reach the client singleton
 * for logging and module lookups, both of which are static on this client.
 */
public final class LucidClient {
    public static final Logger LOGGER = LucidLog.LOGGER;

    private static final LucidClient INSTANCE = new LucidClient();
    private static final ModuleManagerShim MODULES = new ModuleManagerShim();

    private LucidClient() {
    }

    public static LucidClient getInstance() {
        return INSTANCE;
    }

    public ModuleManagerShim getModuleManager() {
        return MODULES;
    }

    /** Static facade over our own {@link ModuleManager}. */
    public static final class ModuleManagerShim {
        public <T extends Module> T getModule(Class<T> type) {
            return ModuleManager.getModule(type);
        }

        public Module get(String name) {
            for (Module m : ModuleManager.getModules()) {
                if (m.getName().equalsIgnoreCase(name)) return m;
            }
            return null;
        }
    }
}
