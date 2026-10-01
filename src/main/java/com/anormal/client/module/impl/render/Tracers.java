package com.anormal.client.module.impl.render;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;
import com.anormal.client.setting.ColorSetting;
import com.anormal.client.setting.NumberSetting;
import com.anormal.client.util.ColorUtils;

public class Tracers extends Module {
    public final BooleanSetting players = new BooleanSetting("Players", "Trace lines to players", true);
    public final BooleanSetting mobs = new BooleanSetting("Monsters", "Trace lines to monsters", false);
    public final BooleanSetting animals = new BooleanSetting("Animals", "Trace lines to animals", false);
    public final NumberSetting distance = new NumberSetting("Max Distance", "Maximum tracer distance", 64.0, 10.0, 128.0, 5.0);
    public final BooleanSetting colorByDist = new BooleanSetting("Color By Dist", "Dynamically changes color based on range", true);
    public final ColorSetting color = new ColorSetting("Color", "Tracer line color", ColorUtils.rgba(255, 200, 50, 200));

    public Tracers() {
        super("Tracers", "Renders directional lines from crosshair towards nearby entities", Category.RENDER);
        addSetting(players);
        addSetting(mobs);
        addSetting(animals);
        addSetting(distance);
        addSetting(colorByDist);
        addSetting(color);
    }
}
