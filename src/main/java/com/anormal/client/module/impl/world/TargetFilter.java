package com.anormal.client.module.impl.world;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class TargetFilter extends Module {
    public final BooleanSetting ignoreTeams = new BooleanSetting("Ignore Teams", "Ignore scoreboard team members", true);
    public final BooleanSetting antiBot = new BooleanSetting("AntiBot", "Filters out fake server anticheat bots", true);

    public TargetFilter() {
        super("TargetFilter", "Filters valid targets by team, friends, and bots", Category.WORLD);
        addSetting(ignoreTeams);
        addSetting(antiBot);
        setEnabled(true);
    }
}
