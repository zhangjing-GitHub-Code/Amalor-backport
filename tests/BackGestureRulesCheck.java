package me.acardia.amalor.xposed.systemui;

/** Standalone JVM check of the production route ownership rules; no Android/framework fixtures. */
class BackGestureRulesCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        BackGestureRules rules = BackGestureRules.INSTANCE;
        BackCornerScope corners = new BackCornerScope();
        Object firstTransaction = new Object();
        Object nestedTransaction = new Object();
        check(!corners.getActive() && !corners.matches(null), "Idle corner writes leave vendor behavior available");
        corners.withTarget(firstTransaction, () -> {
            check(corners.matches(firstTransaction) && !corners.matches(nestedTransaction), "Only the active transaction uses standard corners");
            corners.withTarget(nestedTransaction, () -> {
                check(corners.matches(nestedTransaction) && !corners.matches(firstTransaction), "Nested writes isolate their own transaction");
                return null;
            });
            check(corners.matches(firstTransaction), "Nested completion restores the outer corner write");
            return null;
        });
        check(!corners.getActive(), "Completing a corner write clears the override");
        try {
            corners.withTarget(firstTransaction, () -> { throw new IllegalStateException("failed native write"); });
            throw new AssertionError("Propagate native write errors");
        } catch (IllegalStateException expected) { }
        check(!corners.getActive(), "A failed native write cannot leak a global smoothing override");
        check(rules.cornerPolicy(false, false) == null, "Unowned transactions keep their original smoothing policy");
        check(Boolean.FALSE.equals(rules.cornerPolicy(false, true)), "Standard radius writes bypass OEM smoothing");
        check(Boolean.TRUE.equals(rules.cornerPolicy(true, true)), "Resetting retained curve metadata reaches the Ext writer before the standard write");
        check(Boolean.TRUE.equals(rules.cornerPolicy(true, false)), "A scoped curve reset is independent of the native smoothing preference");
        BackCornerScope resets = new BackCornerScope();
        corners.withTarget(firstTransaction, () -> {
            resets.withTarget(firstTransaction, () -> {
                check(Boolean.TRUE.equals(rules.cornerPolicy(resets.matches(firstTransaction), corners.matches(firstTransaction))), "Reset and write scopes share only their owned transaction");
                check(rules.cornerPolicy(resets.matches(nestedTransaction), corners.matches(nestedTransaction)) == null, "Resetting one layer cannot affect unrelated transactions");
                return null;
            });
            check(Boolean.FALSE.equals(rules.cornerPolicy(resets.matches(firstTransaction), corners.matches(firstTransaction))), "Leaving the reset restores standard writing before transaction submission");
            return null;
        });
        for (boolean master : new boolean[]{false, true}) {
            for (boolean predictive : new boolean[]{false, true}) {
                boolean enabled = master && predictive;
                check(rules.predictiveRoute(master, predictive, 0, 2, false) == enabled, "activity/master dependency");
                check(rules.predictiveRoute(master, predictive, 1, 3, false) == enabled, "task/master dependency");
                check(!rules.predictiveRoute(master, predictive, -1, 2, false), "unknown edge");
                check(!rules.predictiveRoute(master, predictive, 2, 3, false), "unsupported edge");
                check(!rules.predictiveRoute(master, predictive, 0, 1, false), "home/native callback retained");
                check(!rules.predictiveRoute(master, predictive, 0, 2, true), "cancelled runner retained");
            }
        }
        for (boolean nativeAosp : new boolean[]{false, true}) {
            check(rules.haptics(0, nativeAosp) == 0, "Explicit AOSP scheme survives native/OEM visual routing");
            check(rules.haptics(1, nativeAosp) == 1, "Explicit ColorOS scheme survives native/OEM visual routing");
            for (int automatic : new int[]{-1, 2})
                check(rules.haptics(automatic, nativeAosp) == (nativeAosp ? 0 : 1), "Donor automatic scheme follows the current visual");
        }
        Object original = new Object();
        Object nativeRunner = new Object();
        Object externalRunner = new Object();
        BackGestureRouteSlot<Object> slot = new BackGestureRouteSlot<>();
        check(slot.replace(original, nativeRunner) == nativeRunner, "selected runner");
        check(slot.restore(nativeRunner) == original, "restore after completion/cancellation");
        check(slot.restore(original) == original, "idempotent cleanup");
        slot.replace(original, nativeRunner);
        check(slot.restore(externalRunner) == externalRunner, "do not overwrite external replacement");
        check(slot.restore(nativeRunner) == nativeRunner, "retired ownership cannot restore later");
        slot.replace(externalRunner, nativeRunner);
        check(slot.restore(nativeRunner) == externalRunner, "new gesture retains new provider");
        slot.replace(original, nativeRunner);
        check(slot.restore(null) == null, "removed registry entry is not recreated");
        check(rules.closingAlpha(-1f) == 1f, "negative progress");
        check(rules.closingAlpha(0f) == 1f, "start alpha");
        check(Math.abs(rules.closingAlpha(0.1f) - 0.5f) < 0.0001f, "commit fade");
        check(rules.closingAlpha(0.2f) == 0f && rules.closingAlpha(1f) == 0f, "closing fade complete");
        check(rules.closingAlpha(Float.NaN) == 1f, "malformed progress retained");
        check(rules.closingAlpha(Float.POSITIVE_INFINITY) == 1f, "non-finite progress retained");
        for (int edge : new int[]{0, 1}) {
            BackGestureRules.HomeFrame start = rules.homePreview(1080f, 2400f, 0f, edge, 900f, 24f);
            check(start != null && start.getLeft() == 0f && start.getTop() == 0f &&
                    start.getWidth() == 1080f && start.getHeight() == 2400f, "home preview starts at full bounds");
            BackGestureRules.HomeFrame end = rules.homePreview(1080f, 2400f, 1f, edge, 0f, 24f);
            check(end != null && Math.abs(end.getWidth() - 918f) < 0.001f &&
                    Math.abs(end.getHeight() - 2040f) < 0.001f, "home preview ends at AOSP scale");
        }
        for (float progress : new float[]{-1f, 0f, 0.1f, 0.5f, 1f, 2f}) {
            for (float y : new float[]{-10000f, -200f, 0f, 200f, 10000f}) {
                BackGestureRules.HomeFrame left = rules.homePreview(1080f, 2400f, progress, 0, y, 24f);
                BackGestureRules.HomeFrame right = rules.homePreview(1080f, 2400f, progress, 1, y, 24f);
                check(left != null && right != null, "valid home geometry");
                check(Math.abs(1080f - left.getWidth() - left.getLeft() - right.getLeft()) < 0.001f &&
                        Math.abs(left.getTop() - right.getTop()) < 0.001f, "symmetric left/right home previews");
                check(left.getTop() >= -0.001f && left.getTop() + left.getHeight() <= 2400.001f &&
                        left.getLeft() >= -0.001f && left.getLeft() + left.getWidth() <= 1080.001f, "drag stays inside display");
            }
        }
        check(rules.homePreview(0f, 2400f, 0.5f, 0, 0f, 24f) == null, "empty home geometry retained");
        check(rules.homePreview(1080f, 2400f, Float.NaN, 0, 0f, 24f) == null, "malformed home progress retained");
        check(rules.homePreview(1080f, 2400f, 0.5f, 2, 0f, 24f) == null, "non-side home preview retained");
        System.out.println("Back gesture rule/ownership checks passed");
    }
}
