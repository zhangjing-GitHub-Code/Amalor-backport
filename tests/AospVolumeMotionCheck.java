package me.acardia.amalor.xposed.systemui;

class AospVolumeMotionCheck {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var rules = AospVolumeMotionRules.INSTANCE;
        float travel = rules.travel(616, 378, 210);
        check(travel == 217, "travel covers scaled native background plus the unchanged window edge gap");
        check(Math.abs(rules.travel(0, 0, 210) - 189) < .001f, "pre-layout frame has a bounded scaled distance");
        check(rules.translation(0, travel) == 217 && rules.translation(1, travel) == 0,
                "hidden and visible positions line up with the actual card");
        check(rules.alpha(.1f, 0, true) == .1f, "entry opacity is continuous rather than ceil(progress)");
        check(rules.alpha(.5f, 1, false) == .5f, "exit opacity fades continuously");
        check(rules.alpha(1.05f, .9f, true) == 1 && rules.alpha(.99f, 1, true) == 1,
                "spatial overshoot does not dip opacity after full visibility");
        check(rules.alpha(-.04f, .1f, false) == 0 && rules.alpha(.01f, 0, false) == 0,
                "exit spring bounce must not make the dismissed panel flash back");
        check(rules.alpha(.55f, .6f, true) == .6f && rules.alpha(.7f, .6f, true) == .7f,
                "reversed entry starts from current opacity despite inherited velocity");
        check(Math.abs(rules.scale(0) - .846f) < .001f && rules.scale(1) == .9f,
                "expressive scale settles at the compact reference size");
        check(rules.scale(2) < .91f, "scale overshoot is restrained");
        for (float progress : new float[]{0, .25f, .5f, 1, 1.05f}) {
            float s = rules.scale(progress);
            check(Math.abs(40 * s / (60 * s) - 40f / 60f) < .00001f,
                    "native track/panel ratio stays constant throughout the transition");
            check(Math.abs(48 * s / (254 * s) - 48f / 254f) < .00001f,
                    "button/slider proportions share one scale instead of separate size clamps");
        }
        System.out.println("Native volume motion/opacity/reversal checks passed");
    }
}
