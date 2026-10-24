package dev.zaza.client.ui;

public record ZazaLayout(float scale, int x, int y, int width, int height, int tabBarHeight, int statusBarHeight) {
    public static final int WIDTH = 375;
    public static final int HEIGHT = 318;
    public static final int RADIUS = 5;
    public static final int TAB_BAR_HEIGHT = 22;
    public static final int STATUS_BAR_HEIGHT = 15;
    public static final int PADDING = 7;
    public static final int GAP = 7;
    public static final int SCREEN_MARGIN = 24;

    public static ZazaLayout of(int screenWidth, int screenHeight, int offsetX, int offsetY, float uiScale) {
        double scale = Math.min(uiScale, Math.min(
                Math.max(1, screenWidth - SCREEN_MARGIN) / (double) WIDTH,
                Math.max(1, screenHeight - SCREEN_MARGIN) / (double) HEIGHT));
        int width = Math.max(1, (int) Math.round(WIDTH * scale));
        int height = Math.max(1, (int) Math.round(HEIGHT * scale));
        int x = Math.clamp((screenWidth - width) / 2 + offsetX, 0, Math.max(0, screenWidth - width));
        int y = Math.clamp((screenHeight - height) / 2 + offsetY, 0, Math.max(0, screenHeight - height));
        return new ZazaLayout((float) scale, x, y, width, height,
                (int) Math.max(1, Math.round(TAB_BAR_HEIGHT * scale)),
                (int) Math.max(1, Math.round(STATUS_BAR_HEIGHT * scale)));
    }

    public int scaled(float units) {
        return Math.round(units * scale);
    }

    public int atLeastOne(float units) {
        return Math.max(1, scaled(units));
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int contentY() {
        return y + tabBarHeight;
    }

    public int contentHeight() {
        return height - tabBarHeight - statusBarHeight;
    }

    public int contentBottom() {
        return bottom() - statusBarHeight;
    }

    public int padding() {
        return atLeastOne(PADDING);
    }

    public int leftPanelX() {
        return x + padding();
    }

    public int panelWidth() {
        int total = width - 2 * padding() - atLeastOne(GAP);
        return Math.max(1, total / 2);
    }

    public int rightPanelX() {
        return leftPanelX() + panelWidth() + atLeastOne(GAP);
    }

    public int panelY() {
        return contentY() + padding();
    }

    public int panelHeight() {
        return contentHeight() - 2 * padding();
    }

    public boolean inTitleBar(double px, double py) {
        return px >= x && px < right() && py >= y && py < contentY();
    }

    public boolean inPanel(double px, double py) {
        return px >= leftPanelX() && px < right() && py >= panelY() && py < panelY() + panelHeight();
    }
}
