package me.acardia.amalor.xposed.systemui;

class QsSliderRulesCheck {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        QsSliderRules rules = QsSliderRules.INSTANCE;
        for (float density : new float[]{1f, 2.5f, 2.625f, 3.5f}) {
            for (float width : new float[]{48f, 62f, 64f, 80f}) {
                for (float height : new float[]{104f, 138f, 160f}) {
                    var geometry = rules.geometry(width * density, height * density, density, 0, 0, 1f);
                    check(geometry != null, "supported slider has room for an inset icon");
                    check(geometry.getTop() == 0f && geometry.getBottom() == height * density,
                            "track uses the full slot height and aligns with the media card");
                    check(geometry.getTrackWidth() >= 40f * density, "track is wide enough for a 24dp inset icon");
                    check(geometry.getCenterX() - geometry.getHandleWidth() / 2f >= 0f &&
                            geometry.getCenterX() + geometry.getHandleWidth() / 2f <= width * density,
                            "wider handle remains inside the allocated slot");
                    boolean above = false, below = false;
                    float previous = Float.POSITIVE_INFINITY;
                    for (int step = -100; step <= 1100; step++) {
                        float progress = step / 1000f;
                        float y = rules.thumbY(geometry, progress);
                        float icon = rules.iconY(geometry, progress);
                        float epsilon = 0.001f * density;
                        check(y <= previous && y >= geometry.getTop() && y <= geometry.getBottom(),
                                "increasing values move upwards within the touch range");
                        check(y - 2f * density >= 0f && y + 2f * density <= height * density,
                                "full handle remains inside the slider at both endpoints");
                        check(icon - 12f * density >= geometry.getTop() + 8f * density - epsilon &&
                                icon + 12f * density <= geometry.getBottom() - 16f * density + epsilon,
                                "entire icon retains 16dp of bottom breathing room");
                        check(Math.abs(icon - y) >= 24f * density - epsilon,
                                "icon clears the full handle and its gap on both sides");
                        above |= icon < y;
                        below |= icon > y;
                        previous = y;
                    }
                    check(above && below, "icon switches between inactive and active track instead of reserving a bottom slot");
                    check(rules.thumbY(geometry, Float.NaN) == geometry.getBottom() - geometry.getThumbInset() &&
                            Float.isFinite(rules.iconY(geometry, Float.NaN)), "invalid progress has a bounded display");
                }
            }
        }
        var source = rules.geometry(62f, 138f, 1f, 0, 0, 1f);
        float detailWidth = rules.detailWidth(62f, 138f, 320f, 360f);
        float scale = 320f / 138f;
        var detail = rules.geometry(detailWidth, 320f, 1f, 0, 0, scale);
        check(detail != null && Math.abs(detailWidth / 320f - 62f / 138f) < 0.0001f,
                "detail slider preserves the source aspect ratio");
        check(Math.abs(detail.getTrackWidth() / scale - source.getTrackWidth()) < 0.001f &&
                Math.abs(detail.getHandleWidth() / scale - source.getHandleWidth()) < 0.001f &&
                Math.abs(detail.getCorner() / scale - source.getCorner()) < 0.001f,
                "track, handle and corners meet at the same visual size during the native handoff");
        for (float fraction : new float[]{0f, 0.1f, 0.5f, 0.9f, 1f}) {
            check(Math.abs(rules.thumbY(detail, fraction) / scale - rules.thumbY(source, fraction)) < 0.001f &&
                    Math.abs(rules.iconY(detail, fraction) / scale - rules.iconY(source, fraction)) < 0.001f,
                    "indicator and inset icon positions remain proportional during expansion");
        }
        check(rules.detailWidth(62f, 138f, 320f, 100f) == 100f, "detail width respects its available parent width");
        check(rules.detailWidth(62f, 0f, 320f, 360f) == null &&
                rules.detailWidth(62f, 138f, Float.NaN, 360f) == null, "invalid detail measurements keep native layout");
        var padded = rules.geometry(64f, 180f, 1f, 20, 24, 1f);
        check(padded.getTop() == 20f && padded.getBottom() == 156f, "existing layout padding remains respected");
        check(rules.geometry(64f, 95f, 1f, 0, 0, 1f) == null, "insufficient room cannot produce an overlapping icon");
        check(rules.geometry(47f, 160f, 1f, 0, 0, 1f) == null, "insufficient track width is rejected");
        check(rules.geometry(0f, 160f, 1f, 0, 0, 1f) == null, "unmeasured view is not configured");
        check(rules.geometry(64f, 160f, 0f, 0, 0, 1f) == null, "invalid density is rejected");
        check(rules.geometry(Float.NaN, 160f, 1f, 0, 0, 1f) == null &&
                rules.geometry(64f, Float.POSITIVE_INFINITY, 1f, 0, 0, 1f) == null, "nonfinite dimensions are rejected");
        check(rules.geometry(64f, 160f, 1f, 0, 0, Float.NaN) == null, "invalid animation scale is rejected");
        System.out.println("MD3E full-height slider/inset-icon/scale checks passed");
    }
}
