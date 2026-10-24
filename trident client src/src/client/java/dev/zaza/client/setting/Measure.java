package dev.zaza.client.setting;

public enum Measure {
    NONE,
    PERCENT,
    DEGREES;

    public String format(int value) {
        return switch (this) {
            case PERCENT -> value + "%";
            case DEGREES -> value + "°";
            case NONE -> Integer.toString(value);
        };
    }
}
