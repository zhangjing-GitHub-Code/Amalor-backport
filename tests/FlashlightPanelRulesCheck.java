package me.acardia.amalor.xposed.systemui;

class FlashlightPanelRulesCheck {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var rules = FlashlightPanelRules.INSTANCE;
        check(rules.adjustable(1, 5), "Camera2 multi-level torch is supported");
        for (Integer max : new Integer[]{null, 0, 1}) check(!rules.adjustable(1, max), "No strength support falls back");
        check(!rules.adjustable(null, 5) && !rules.adjustable(0, 5) && !rules.adjustable(6, 5), "Invalid camera defaults fall back");
        check(rules.canOpen(true, false, false, false), "Enabled/unlocked/unrestricted entry opens native panel");
        check(!rules.canOpen(false, false, false, false), "Disabled feature keeps the OEM route");
        for (int flags = 1; flags < 8; flags++) {
            boolean locked = (flags & 1) != 0, limited = (flags & 2) != 0, power = (flags & 4) != 0;
            check(!rules.canOpen(true, locked, limited, power), "Every native restriction prevents redirection");
            check(rules.blockLevel(1, locked, limited, power), "Restrictions are rechecked during adjustment");
            check(!rules.blockLevel(0, locked, limited, power), "Turning the light off remains available");
        }
        check(!rules.blockLevel(4, false, false, false), "Valid unrestricted levels reach the native repository");
        System.out.println("Flashlight capability/entry/live-restriction checks passed");
    }
}
