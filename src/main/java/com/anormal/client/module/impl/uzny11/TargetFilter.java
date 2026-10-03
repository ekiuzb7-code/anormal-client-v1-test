package com.anormal.client.module.impl.uzny11;

import com.anormal.client.module.Category;
import com.anormal.client.module.Module;
import com.anormal.client.setting.BooleanSetting;

public class TargetFilter extends Module {
    public final BooleanSetting teamsByServer = new BooleanSetting("Teams by Server", "Ignore players on your scoreboard team", true);
    public final BooleanSetting teamsByColor = new BooleanSetting("Teams by Color", "Ignore players sharing your nametag color", true);
    public final BooleanSetting antiBot = new BooleanSetting("AntiBot", "Filters out fake server anticheat bots", true);
    public final BooleanSetting ignoreInvisibles = new BooleanSetting("Invisibles", "Target invisible players", true);
    public final BooleanSetting ignoreSleeping = new BooleanSetting("Ignore Sleeping", "Ignore players sleeping in bed", true);

    public TargetFilter() {
        super("TargetFilter", "Filters which players can be targeted by combat modules", Category.UZNY11);
        addSetting(teamsByServer);
        addSetting(teamsByColor);
        addSetting(antiBot);
        addSetting(ignoreInvisibles);
        addSetting(ignoreSleeping);

    }
}
