package com.anormal.client.actionwheel;

import com.anormal.client.module.Module;
import com.anormal.client.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class WheelAction {

    public enum Type {
        COMMAND("Command"),
        MODULE("Module"),
        MESSAGE("Chat");

        private final String display;
        Type(String display) { this.display = display; }
        public String getDisplay() { return display; }
    }

    private String label;
    private Type type;
    private String value;
    private String icon;
    private int color;

    public WheelAction(String label, Type type, String value, String icon, int color) {
        this.label = label;
        this.type = type;
        this.value = value;
        this.icon = icon;
        this.color = color;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    /** Executes the configured action in game */
    public void execute(MinecraftClient mc) {
        if (mc == null || mc.player == null) return;

        try {
            switch (type) {
                case COMMAND -> {
                    String cmd = value.trim();
                    if (cmd.startsWith("/")) cmd = cmd.substring(1);
                    if (mc.player.networkHandler != null && !cmd.isEmpty()) {
                        mc.player.networkHandler.sendChatCommand(cmd);
                    }
                }
                case MODULE -> {
                    Module m = ModuleManager.getModuleByName(value.trim());
                    if (m != null) {
                        m.toggle();
                        if (mc.inGameHud != null) {
                            String status = m.isEnabled() ? "§aENABLED" : "§cDISABLED";
                            mc.inGameHud.getChatHud().addMessage(Text.literal(
                                    "§6[Wheel] §f" + m.getName() + " is now " + status));
                        }
                    } else if (mc.inGameHud != null) {
                        mc.inGameHud.getChatHud().addMessage(Text.literal(
                                "§c[Wheel] Module not found: §f" + value));
                    }
                }
                case MESSAGE -> {
                    String msg = value.trim();
                    if (mc.player.networkHandler != null && !msg.isEmpty()) {
                        mc.player.networkHandler.sendChatMessage(msg);
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
