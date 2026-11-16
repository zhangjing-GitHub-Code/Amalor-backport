package me.acardia.amalor.xposed.systemui;

import java.util.Arrays;

class BatteryTextGeometryCheck {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void fill(int[] image, int width, int left, int top, int right, int bottom, int color) {
        for (int y = top; y < bottom; y++) Arrays.fill(image, y * width + left, y * width + right, color);
    }

    public static void main(String[] args) {
        var rules = BatteryTextGeometry.INSTANCE;
        for (int scale : new int[]{1, 2, 4}) {
            int width = 30 * scale, height = 16 * scale;
            int[] mask = new int[width * height];
            fill(mask, width, 2 * scale, scale, 24 * scale, 15 * scale, 0x80ffffff);
            fill(mask, width, 26 * scale, 5 * scale, 28 * scale, 11 * scale, 0xffffffff);
            check(Arrays.equals(rules.bodyBounds(mask, width, height), new int[]{2 * scale, scale, 24 * scale, 15 * scale}),
                    "Use the rendered body, excluding terminal and padding at every density");
            int[] mirror = new int[mask.length];
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) mirror[y * width + width - x - 1] = mask[y * width + x];
            check(Arrays.equals(rules.bodyBounds(mirror, width, height), new int[]{6 * scale, scale, 28 * scale, 15 * scale}),
                    "RTL artwork selects the body rather than the left terminal");
        }
        check(rules.bodyBounds(new int[100], 10, 10) == null, "Empty/transparent artwork falls back to the native layout");
        int[] withoutTerminal = new int[100];
        fill(withoutTerminal, 10, 0, 2, 10, 8, 0xffffffff);
        check(Arrays.equals(rules.bodyBounds(withoutTerminal, 10, 10), new int[]{0, 2, 10, 8}), "A body reaching the last column is retained");
        for (boolean rtl : new boolean[]{false, true}) for (float body : new float[]{44f, 47.5f, 55f}) {
            float offset = rules.centerOffset(51f, body, rtl);
            float anchor = 51f - (rtl ? -offset : offset);
            for (float[] ink : new float[][]{{1, 22, 23}, {1, 45, 46}, {-4, 75, 78}, {6, 99, 104}}) {
                float bias = (ink[0] + ink[1]) / 2 - ink[2] / 2;
                float visibleCenter = anchor - bias - ink[2] / 2 + (ink[0] + ink[1]) / 2;
                check(Math.abs(visibleCenter - body) < 0.001f, "One, two and three digits/wide fonts share the same visual center");
            }
        }
        System.out.println("Battery rendered-body/terminal/RTL/wide-text centering checks passed");
    }
}
