package me.acardia.amalor.xposed.systemui;

class QsShapeRulesCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        QsShapeRules rules = QsShapeRules.INSTANCE;
        for (float density : new float[]{1f, 2.625f, 3.5f}) {
            check(rules.radius(QsShapeRules.Kind.Card, 138f * density, 60f * density, density, 0f) == 30f * density,
                    "inactive 2x1 is a true capsule");
            check(rules.radius(QsShapeRules.Kind.Card, 138f * density, 60f * density, density, 1f) == 20f * density,
                    "active 2x1 settles at 20dp");
            check(rules.radius(QsShapeRules.Kind.Single, 64f * density, 64f * density, density, 0f) == 32f * density &&
                    rules.radius(QsShapeRules.Kind.Single, 64f * density, 64f * density, density, 1f) == 16f * density,
                    "1x1 moves between a circle and a rounded rectangle");
            check(rules.radius(QsShapeRules.Kind.Inset, 42f * density, 42f * density, density, 0f) == 21f * density &&
                    rules.radius(QsShapeRules.Kind.Inset, 42f * density, 42f * density, density, 1f) == 12f * density,
                    "2x1 inset is a circle when inactive and a rounded rectangle when active");
            check(rules.radius(QsShapeRules.Kind.Inset, 42f * density, 42f * density, density, 0.5f) == 16.5f * density,
                    "inset shape also has intermediate spring frames");
            for (float fraction : new float[]{-0.15f, 0f, 0.25f, 0.5f, 0.75f, 1f, 1.15f, Float.NaN}) {
                float radius = rules.radius(QsShapeRules.Kind.Card, 138f * density, 60f * density, density, fraction);
                check(Float.isFinite(radius) && radius >= 0f && radius <= 30f * density, "spring overshoot stays within valid outline bounds");
                check(rules.radius(QsShapeRules.Kind.Media, 138f * density, 138f * density, density, fraction) == 20f * density,
                        "media keeps its standard 20dp corners");
                float inset = rules.radius(QsShapeRules.Kind.Inset, 42f * density, 42f * density, density, fraction);
                check(Float.isFinite(inset) && inset >= 0f && inset <= 21f * density,
                        "inset overshoot stays inside the circular bounds");
            }
            check(rules.radius(QsShapeRules.Kind.Card, 138f * density, 60f * density, density, 0.5f) == 25f * density,
                    "intermediate shape does not snap to either endpoint");
            check(rules.outlineRadius(36f * density, (int) (300 * density), (int) (200 * density), 20f * density) == 20f * density,
                    "steady album artwork cannot reintroduce the native 36dp top corners");
            for (float progress : new float[]{0f, 0.25f, 0.5f, 0.75f, 1f}) {
                float scale = 0.2f + 0.8f * progress;
                float visual = (8f + 12f * progress) * density;
                float local = visual / scale;
                float rendered = rules.outlineRadius(local, (int) (300 * density), (int) (200 * density), null) * scale;
                check(Math.abs(rendered - visual) < 0.001f,
                        "opening/closing keeps scale-compensated cover radii rather than capping local coordinates");
            }
        }
        check(rules.radius(QsShapeRules.Kind.Media, 12f, 8f, 1f, 1f) == 4f, "small bounds clamp the radius");
        check(rules.radius(QsShapeRules.Kind.Card, Float.NaN, 60f, 1f, 0f) == 0f &&
                rules.radius(QsShapeRules.Kind.Single, 64f, 64f, 0f, 1f) == 0f, "invalid measurements are rejected");
        check(rules.outlineRadius(100f, 12, 8, null) == 4f, "animated outline still fits its own bounds");
        check(rules.outlineRadius(-5f, 50, 50, null) == 0f && rules.outlineRadius(Float.NaN, 50, 50, null) == 0f &&
                rules.outlineRadius(20f, 0, 50, 20f) == 0f, "invalid outline geometry is bounded");
        System.out.println("QS standard-shape/transition-bounds checks passed");
    }
}
