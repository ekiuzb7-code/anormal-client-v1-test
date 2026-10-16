package com.anormal.client.module.impl.optimization;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.NumberSetting;

/**
 * Shared helpers for the Optimization category.
 *
 * <p>Every optimization module is <b>off by default</b> and is expected to put
 * back whatever it changed when it is switched off, so these helpers exist to
 * make that safe: they remember the value a {@code SimpleOption} had when the
 * module took it over and restore it afterwards.</p>
 */
public final class OptimizerUtil {

    private OptimizerUtil() {}

    /** Applies an integer option and returns the previous value (-1 when unavailable). */
    @SuppressWarnings("unchecked")
    public static int pushInt(net.minecraft.client.option.SimpleOption<Integer> option, int value) {
        try {
            int prev = option.getValue();
            option.setValue(value);
            return prev;
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void restoreInt(net.minecraft.client.option.SimpleOption<Integer> option, int prev) {
        if (prev < 0) return;
        try {
            option.setValue(prev);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    public static double pushDouble(net.minecraft.client.option.SimpleOption<Double> option, double value) {
        try {
            double prev = option.getValue();
            option.setValue(value);
            return prev;
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void restoreDouble(net.minecraft.client.option.SimpleOption<Double> option, double prev) {
        if (prev < 0) return;
        try {
            option.setValue(prev);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean pushBool(net.minecraft.client.option.SimpleOption<Boolean> option, boolean value) {
        try {
            boolean prev = option.getValue();
            option.setValue(value);
            return prev;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void restoreBool(net.minecraft.client.option.SimpleOption<Boolean> option, boolean prev) {
        try {
            option.setValue(prev);
        } catch (Throwable ignored) {
        }
    }

    /** Convenience for the boilerplate of an on/off setting. */
    public static boolean on(BooleanSetting setting) {
        return setting.isEnabled();
    }

    public static int i(NumberSetting setting) {
        return setting.getValue().intValue();
    }

    public static double d(NumberSetting setting) {
        return setting.getValue();
    }

    /** Base class so every optimizer shares the same constructor shape. */
    public abstract static class Optimizer extends Module {
        protected Optimizer(String name, String description) {
            super(name, description, Category.OPTIMIZATION);
        }
    }
}
