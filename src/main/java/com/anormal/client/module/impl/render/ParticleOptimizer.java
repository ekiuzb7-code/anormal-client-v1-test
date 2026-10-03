package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.ModeSetting;

public class ParticleOptimizer extends Module {
    public final ModeSetting particles = new ModeSetting("Particles", "Particle density cap", "Minimal", "Minimal", "Decreased");

    public ParticleOptimizer() {
        super("ParticleOptimizer", "Caps particle density to reduce spikes", Category.RENDER);
        addSetting(particles);
    }

    @Override
    public void onTick() {
        apply(particles.is("Minimal") ? "MINIMAL" : "DECREASED");
    }

    @Override
    public void onDisable() {
        apply("ALL");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void apply(String mode) {
        try {
            Object opt = mc.options.getClass().getMethod("getParticles").invoke(mc.options);
            Class<? extends Enum> enumClass = (Class<? extends Enum>) Class.forName("net.minecraft.client.option.ParticlesMode");
            Object value = Enum.valueOf(enumClass, mode);
            opt.getClass().getMethod("setValue", Object.class).invoke(opt, value);
        } catch (Throwable ignored) {}
    }
}
