package me.acardia.amalor.xposed.uxdesign;

import com.materialkolor.dynamiccolor.ColorSpec;
import com.materialkolor.dynamiccolor.DynamicScheme;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WallpaperColorPaletteCheck {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        var palettes = WallpaperColorPalettes.INSTANCE;
        int normalSeed = 0xff739b61;
        var families = WallpaperColorFamily.values();
        check(Arrays.equals(Arrays.stream(families).map(WallpaperColorFamily::getXmlName).toArray(String[]::new),
                new String[]{"Green", "Red", "Yellow", "Blue", "Orange"}), "Native XML order must remain N2,A2,N1,A1,A3");
        for (int seed : new int[]{normalSeed, 0xff1b6ef3, 0xfff44336, 0xff9c27b0, 0xffffc107, 0xff777777}) {
            for (int index = 21; index <= 24; index++) {
                String style = palettes.styleForIndex(index);
                for (boolean dark : new boolean[]{false, true}) {
                    for (double level : new double[]{-1.0, 0.0, 1.0}) {
                        var scheme = palettes.scheme(seed, style, dark, level);
                        check(scheme.getSpecVersion() == ColorSpec.SpecVersion.SPEC_2025, "All four wallpaper styles use 2025");
                        var palette = new WallpaperColorRoles(scheme);
                        int[][] expected = expectedGroups(scheme);
                        for (int group = 0; group < families.length; group++) {
                            var roles = palette.family(families[group]);
                            int[] colors = roles.getValues();
                            String where = Integer.toHexString(seed) + "/" + style + "/" + dark + "/" + level + "/" + families[group];
                            check(colors.length == 7, "Keep the seven COUI/NX resource slots: " + where);
                            check(roles.getNormal() == expected[group][0] && colors[0] == expected[group][0], "Correct normal role: " + where);
                            check(roles.getOnNormal() == expected[group][1], "Correct on-normal role: " + where);
                            check(roles.getContainer() == expected[group][2] && colors[2] == expected[group][2], "Correct container role: " + where);
                            check(roles.getOnContainer() == expected[group][3], "Correct on-container role: " + where);
                            check((colors[4] & 0xffffff) == (expected[group][0] & 0xffffff), "Highlight uses its own family, not primary: " + where);
                            check((colors[4] >>> 24) == 102, "40% selection layer: " + where);
                            check((colors[5] >>> 24) == 31 && (colors[6] >>> 24) == 97, "12%/38% disabled layers: " + where);
                            check(contrast(roles.getNormal(), roles.getOnNormal()) >= (level < 0 ? 2.95 : 4.45), "Paired foreground contrast: " + where);
                            check(contrast(roles.getNormal(), palette.getSharedOnColor()) >= (level < 0 ? 2.95 : 4.45), "Shared COUI foreground contrast: " + where);
                            for (int i = 0; i < 4; i++) check((colors[i] >>> 24) == 255, "Opaque enabled roles: " + where);
                        }
                    }
                }
            }
        }
        check(palettes.scheme(normalSeed, "MONOCHROMATIC", false, 0.0) == null, "Keep unsupported styles native");
        var tonal = new WallpaperColorRoles(palettes.scheme(normalSeed, "TONAL_SPOT", false, 0.0));
        check(tonal.family(WallpaperColorFamily.PRIMARY).getNormal() == 0xff476739,
                "The reported wallpaper keeps its 2025 primary");
        check(tonal.family(WallpaperColorFamily.SECONDARY).getNormal() == 0xff55634d,
                "Red/A2 must not be flattened to primary");
        check(tonal.family(WallpaperColorFamily.TERTIARY).getNormal() == 0xff64612a,
                "Orange/A3 must not be flattened to primary");
        check(tonal.family(WallpaperColorFamily.NEUTRAL).getNormal() != 0xff476739 &&
                tonal.family(WallpaperColorFamily.NEUTRAL_VARIANT).getNormal() != 0xff476739,
                "Yellow/N1 and Green/N2 retain neutral roles");

        var bindings = new WallpaperColorBindings();
        var a = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        var b = new ArrayList<>(a);
        bindings.remember(a, normalSeed);
        bindings.remember(b, 0xffb33144);
        a.set(0, 99); // The native HSL conversion/reordering changes List.equals/hashCode.
        check(bindings.getCurrent() == null, "Normal Monet has no active vendor XML scope");
        bindings.duringWrite(a, 21, 0.0, true, current -> {
            check(current.getSeed() == normalSeed, "Identity survives mutation of the vendor color list");
            check(current.getLight().family(WallpaperColorFamily.PRIMARY).getNormal() == 0xff476739,
                    "Resolve actual role values before writing the files");
            bindings.duringWrite(b, 22, 0.0, true, nested -> {
                check(nested.getSeed() == 0xffb33144, "Equal or similar lists cannot exchange source metadata");
                return null;
            });
            check(bindings.getCurrent() == current, "Restore nested writer state");
            bindings.duringWrite(a, 25, 0.0, true, nativeWrite -> {
                check(nativeWrite == null && bindings.getCurrent() == null, "Unsupported styles cannot inherit an outer override");
                return null;
            });
            bindings.duringWrite(a, 21, 0.0, false, disabled -> {
                check(disabled == null && bindings.getCurrent() == null, "Disabled feature uses the native XML writer");
                return null;
            });
            var isolated = new AtomicBoolean();
            Thread thread = new Thread(() -> isolated.set(bindings.getCurrent() == null));
            thread.start();
            try { thread.join(); } catch (InterruptedException error) { throw new AssertionError(error); }
            check(isolated.get(), "Unrelated threads do not share the XML writer scope");
            return null;
        });
        try {
            bindings.duringWrite(a, 21, 0.0, true, ignored -> { throw new IllegalStateException("failed file"); });
            throw new AssertionError("Writer failures must propagate");
        } catch (IllegalStateException expected) { }
        check(bindings.getCurrent() == null, "No scope leak after file failure");
        for (int i = 0; i < 65; i++) bindings.remember(new ArrayList<>(), normalSeed);
        bindings.duringWrite(a, 21, 0.0, true, evicted -> {
            check(evicted == null, "Transient source metadata is bounded; missing metadata keeps native behavior");
            return null;
        });
        System.out.println("COUI wallpaper five-family roles, contrast, identity and XML-scope checks passed");
    }

    private static int[][] expectedGroups(DynamicScheme s) {
        return new int[][]{
                {s.getOnSurfaceVariant(), s.getSurfaceVariant(), s.getSurfaceVariant(), s.getOnSurfaceVariant()},
                {s.getSecondary(), s.getOnSecondary(), s.getSecondaryContainer(), s.getOnSecondaryContainer()},
                {s.getOnSurface(), s.getSurface(), s.getSurfaceContainer(), s.getOnSurface()},
                {s.getPrimary(), s.getOnPrimary(), s.getPrimaryContainer(), s.getOnPrimaryContainer()},
                {s.getTertiary(), s.getOnTertiary(), s.getTertiaryContainer(), s.getOnTertiaryContainer()},
        };
    }

    private static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }
    private static double luminance(int color) {
        double result = 0;
        double[] weights = {0.2126, 0.7152, 0.0722};
        for (int i = 0; i < 3; i++) {
            double channel = ((color >>> (16 - i * 8)) & 255) / 255.0;
            result += weights[i] * (channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4));
        }
        return result;
    }
}
