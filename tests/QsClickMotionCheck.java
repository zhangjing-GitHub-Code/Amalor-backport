package me.acardia.amalor.xposed.systemui;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

public final class QsClickMotionCheck {
    public interface Click { void onClick(Object view); }
    public interface LongClick { boolean onLongClick(Object view); }
    public abstract static class Tile {
        public abstract void bindClickListener(Click click, Click secondary, LongClick longClick, Runnable viewClick);
    }
    public static class ConcreteTile extends Tile {
        public Click listener;
        @Override public void bindClickListener(Click click, Click secondary, LongClick longClick, Runnable viewClick) {
            listener = click;
        }
        public void bindClickListener(Click click) { throw new AssertionError("Wrong overload"); }
    }
    public static class InheritedTile extends ConcreteTile {}

    public static void main(String[] args) throws Exception {
        var rules = QsClickMotionRules.INSTANCE;
        var declaration = Tile.class.getDeclaredMethod("bindClickListener", Click.class, Click.class, LongClick.class, Runnable.class);
        check(Modifier.isAbstract(declaration.getModifiers()), "Reproduce the C17 abstract base declaration");
        check(rules.clickBinding(Tile.class, declaration) == null, "Never submit an abstract method to LSPosed");
        for (ConcreteTile tile : new ConcreteTile[]{new ConcreteTile(), new InheritedTile()}) {
            var binding = rules.clickBinding(tile.getClass(), declaration);
            check(binding != null && !Modifier.isAbstract(binding.getModifiers()), "Resolve concrete and inherited implementations");
            int[] clicks = {0};
            binding.invoke(tile, (Click) view -> clicks[0]++, null, null, null);
            tile.listener.onClick(tile);
            check(clicks[0] == 1, "The selected four-argument binding installs the actual click callback");
        }
        check(rules.shouldPulse(true, false), "A handled click can trigger feedback");
        check(!rules.shouldPulse(true, true), "A click nested in long-click handling must not trigger feedback");
        check(!rules.shouldPulse(false, false), "Unhandled clicks must not trigger feedback");
        for (float[] widths : new float[][]{{100, 100}, {100, 220, 120}, {217, 217, 480, 217}, {700, 180, 320}}) {
            float[] original = widths.clone();
            for (int clicked = 0; clicked < widths.length; clicked++) {
                float[] delta = rules.deltas(widths, clicked, 3.5f);
                check(delta[clicked] > 0f, "Clicked tile expands when it has neighbours");
                float sum = 0f;
                for (int i = 0; i < widths.length; i++) {
                    sum += delta[i];
                    check(widths[i] + delta[i] > 0f, "Compression never inverts a tile");
                    if (Math.abs(i - clicked) > 1) check(delta[i] == 0f, "Only immediate neighbours are compressed");
                }
                check(Math.abs(sum) < 0.001f, "Row width and gaps are conserved");
            }
            check(Arrays.equals(widths, original), "Native geometry inputs must not be mutated");
        }
        check(rules.deltas(new float[]{120}, 0, 2f)[0] < 0f, "Isolated tiles compress safely instead of growing into other widgets");
        for (float[] invalid : new float[][]{{0, 120}, {Float.NaN, 120}, {-1, 120}})
            check(Arrays.equals(rules.deltas(invalid, 0, 2f), new float[2]), "Invalid geometry produces no motion");
        for (float invalid : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY})
            check(rules.glyphScaleX(invalid) == 1f, "Malformed deformation cannot invert or hide a glyph");

        long start = 1_000_000_000L;
        float[] widths = {217, 480, 217, 217};
        QsClickPulse pulse = new QsClickPulse(widths, 3.5f);
        check(!pulse.isRunning(), "No clock runs before a click");
        pulse.click(1, start, 1f);
        boolean changed = false;
        for (int frame = 1; frame <= 250; frame++) {
            long now = start + frame * 8_000_000L;
            pulse.sample(now);
            if (frame == 7 || frame == 12 || frame == 17) {
                float[] before = pulse.getDeltas().clone();
                pulse.click((frame / 5) % widths.length, now, 1f);
                check(Arrays.equals(before, pulse.getDeltas()), "Rapid clicks continue at the current position");
            }
            float sum = 0f;
            float translation = 0f;
            for (int i = 0; i < widths.length; i++) {
                float delta = pulse.getDeltas()[i];
                changed |= Math.abs(delta) > 0.01f;
                sum += delta;
                check(Float.isFinite(delta) && Math.abs(delta) <= widths[i] * 0.25f + 0.01f, "Spring overshoot remains bounded");
                for (float glyphWidth : new float[]{24f, 48f, 120f})
                    checkGlyphTransform((widths[i] + delta) / widths[i], translation, widths[i] * 0.2f, glyphWidth);
                translation += delta;
            }
            check(Math.abs(sum) < 0.1f, "Rapid-click spring motion preserves the row span");
        }
        check(changed && !pulse.isRunning(), "Click feedback has intermediate frames and stops automatically");
        check(Arrays.equals(pulse.getDeltas(), new float[widths.length]), "Completion returns exactly to native geometry");
        for (float scale : new float[]{0.75f, 0.94f, 1f, 1.15f, 1.25f})
            checkGlyphTransform(scale, -(scale - 1f) * 217f / 2f, (217f - 48f) / 2f, 48f);

        QsClickPulse neighbour = new QsClickPulse(widths, 3.5f);
        neighbour.click(0, start, 1f);
        neighbour.sample(start + 40_000_000L);
        check(neighbour.getDeltas()[1] < 0f, "First click compresses its neighbour");
        neighbour.click(1, start + 40_000_000L, 1f);
        float peak = 0f;
        for (int frame = 6; frame < 150; frame++) {
            neighbour.sample(start + frame * 8_000_000L);
            peak = Math.max(peak, neighbour.getDeltas()[1]);
        }
        check(peak > rules.deltas(widths, 1, 3.5f)[1] * 0.5f,
                "Clicking a compressed neighbour must expand it before releasing, not treat negative progress as a completed press");

        QsClickPulse normal = new QsClickPulse(widths, 3.5f);
        QsClickPulse slow = new QsClickPulse(widths, 3.5f);
        normal.click(0, start, 1f);
        slow.click(0, start, 2f);
        for (int step = 0; step <= 100; step++) {
            normal.sample(start + step * 10_000_000L);
            slow.sample(start + step * 20_000_000L);
            for (int i = 0; i < widths.length; i++)
                check(Math.abs(normal.getDeltas()[i] - slow.getDeltas()[i]) < 0.001f, "System duration scale preserves the pulse shape");
        }
        slow.click(1, start, 0f);
        check(!slow.isRunning(), "Disabled animations do not schedule a pulse");
        pulse.click(0, start, 1f);
        pulse.reset();
        check(!pulse.isRunning() && Arrays.equals(pulse.getDeltas(), new float[widths.length]), "Detach/cancellation leaves no motion");
        for (float scale : new float[]{0.5f, 1f, 2f}) {
            QsClickPulse widthMotion = new QsClickPulse(new float[]{217, 217}, 3.5f);
            QsShapeSpring corner = new QsShapeSpring(0f);
            widthMotion.click(0, start, scale);
            corner.retarget(1f, start + 8_000_000L, scale);
            boolean settled = false;
            for (int frame = 1; frame <= 500; frame++) {
                long now = start + frame * 8_000_000L;
                widthMotion.sample(now);
                corner.sample(now);
                if (!widthMotion.isRunning()) {
                    check(!corner.isRunning(), "An uninterrupted width pulse outlasts its click-driven corner transition");
                    settled = true;
                    break;
                }
            }
            check(settled, "Click and corner animations settle with the system duration scale");
        }
        System.out.println("QS concrete binding/background-width/glyph-only/spring/cancellation checks passed");
    }

    private static void checkGlyphTransform(float scale, float translation, float left, float width) {
        float inverse = QsClickMotionRules.INSTANCE.glyphScaleX(scale);
        AffineTransform transform = AffineTransform.getTranslateInstance(translation, 0);
        transform.scale(scale, 1);
        Rectangle2D background = transform.createTransformedShape(new Rectangle2D.Float(0, 0, 217, 217)).getBounds2D();
        check(Math.abs(background.getWidth() - 217 * scale) < 0.001, "Background keeps the full slot deformation");
        transform.translate(left, 12);
        transform.translate((1f - inverse) * width / 2f, 0);
        transform.scale(inverse, 1);
        Rectangle2D bounds = transform.createTransformedShape(new Rectangle2D.Float(0, 0, width, 24)).getBounds2D();
        check(Math.abs(bounds.getWidth() - width) < 0.001, "Only glyph pixels cancel the slot compression and expansion");
        check(Math.abs(bounds.getHeight() - 24) < 0.001, "Glyph height stays native");
        check(Math.abs(bounds.getCenterX() - (translation + scale * (left + width / 2f))) < 0.001,
                "Glyph centre follows slot motion instead of staying behind");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
