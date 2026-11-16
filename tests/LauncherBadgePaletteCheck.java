package me.acardia.amalor.xposed.launcher;

import java.util.Arrays;
import com.materialkolor.hct.Hct;

public final class LauncherBadgePaletteCheck {
    public static void main(String[] args) {
        var factory = LauncherBadgePalettes.INSTANCE;
        for (int seed : new int[]{0xfff44336, 0xff4caf50, 0xff2196f3, 0xffffc107, 0xff009688, 0xff9c27b0}) {
            var palette = factory.fromSeed(seed);
            require(palette == factory.fromSeed(seed), "Repeated seeds reuse their cached palette");
            for (var colors : new LauncherBadgeColors[]{palette.getLight(), palette.getDark()}) {
                require((colors.getBackground() >>> 24) == 255 && (colors.getForeground() >>> 24) == 255, "Role colors are opaque");
                require(contrast(colors.getForeground(), colors.getBackground()) >= 4.45, "Badge numbers retain readable contrast");
                double hue = Math.abs(Hct.fromInt(seed).getHue() - Hct.fromInt(colors.getBackground()).getHue());
                require(Math.min(hue, 360 - hue) < 12, "Tonal Spot retains the icon hue");
            }
            require(!palette.getLight().equals(palette.getDark()), "Light/dark roles are generated together and differ");
        }
        for (int seed : new int[]{0xff000000, 0xff777777, 0xffffffff}) {
            var palette = factory.fromSeed(seed);
            for (var colors : new LauncherBadgeColors[]{palette.getLight(), palette.getDark()}) {
                int rgb = colors.getBackground();
                require(((rgb >> 16) & 255) == ((rgb >> 8) & 255) && ((rgb >> 8) & 255) == (rgb & 255), "Achromatic icons stay neutral");
            }
        }
        int[] pixels = new int[1024];
        Arrays.fill(pixels, 0xffffffff);
        Arrays.fill(pixels, 0, 256, 0xfff44336);
        require(factory.fromPixels(pixels) == factory.fromSeed(0xfff44336), "White icon padding must not displace the colored logo");
        Arrays.fill(pixels, 0x00ff0000);
        pixels[0] = 0xff2196f3;
        require(factory.fromPixels(pixels) == factory.fromSeed(0xff2196f3), "Transparent RGB does not become the theme seed");
        require(factory.fromPixels(new int[1024]) == null, "An empty icon keeps native rendering");
        var original = factory.fromSeed(0xffdf214d);
        for (int i = 0; i < 160; i++) factory.fromSeed(0xff000000 | (i * 104729 & 0xffffff));
        var regenerated = factory.fromSeed(0xffdf214d);
        require(regenerated != original && regenerated.equals(original), "The seed cache is bounded and regeneration is deterministic");
        System.out.println("Launcher MCU palette/hue/contrast/transparency/cache checks passed");
    }

    private static double luminance(int color) {
        double result = 0;
        double[] weights = {0.2126, 0.7152, 0.0722};
        for (int i = 0; i < 3; i++) {
            double channel = ((color >> (16 - i * 8)) & 255) / 255.0;
            result += weights[i] * (channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4));
        }
        return result;
    }

    private static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
