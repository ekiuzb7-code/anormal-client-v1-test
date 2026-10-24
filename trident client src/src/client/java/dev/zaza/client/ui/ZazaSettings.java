package dev.zaza.client.ui;

public final class ZazaSettings {
    public enum CloseAnimation {
        COLLAPSE("collapse"),
        DISSOLVE("dissolve");

        public final String label;

        CloseAnimation(String label) {
            this.label = label;
        }
    }

    public enum SoundSet {
        GLASS("glass"),
        CLICK("click"),
        SYNTH("synth");

        public final String label;

        SoundSet(String label) {
            this.label = label;
        }
    }

    private static final CloseAnimation[] ANIMATIONS = CloseAnimation.values();
    private static final SoundSet[] SOUNDS = SoundSet.values();

    private static int themeIndex;
    private static int soundIndex;
    private static int volume = 15;
    private static CloseAnimation closeAnimation = CloseAnimation.DISSOLVE;

    private ZazaSettings() {}

    public static int themeIndex() {
        return themeIndex;
    }

    public static void applyTheme(int index) {
        themeIndex = Math.clamp(index, 0, Theme.definitions().size() - 1);
        Theme.select(themeIndex);
        ThemeAdjust.select();
    }

    public static SoundSet sound() {
        return SOUNDS[soundIndex];
    }

    public static int soundIndex() {
        return soundIndex;
    }

    public static void setSound(int index) {
        soundIndex = Math.clamp(index, 0, SOUNDS.length - 1);
    }

    public static int volume() {
        return volume;
    }

    public static void setVolume(int value) {
        volume = Math.clamp(value, 0, 100);
    }

    public static CloseAnimation closeAnimation() {
        return closeAnimation;
    }

    public static void setCloseAnimation(String name) {
        for (CloseAnimation animation : ANIMATIONS) {
            if (animation.name().equals(name)) closeAnimation = animation;
        }
    }

    public static void setCloseAnimation(CloseAnimation animation) {
        closeAnimation = animation;
    }

    public static CloseAnimation[] animations() {
        return ANIMATIONS;
    }

    public static SoundSet[] sounds() {
        return SOUNDS;
    }
}
