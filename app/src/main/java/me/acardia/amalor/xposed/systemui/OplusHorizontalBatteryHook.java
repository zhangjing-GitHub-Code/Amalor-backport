package me.acardia.amalor.xposed.systemui;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import me.acardia.amalor.BatteryOptions;
import me.acardia.amalor.NetworkTypeOptions;
import me.acardia.amalor.xposed.HookLog;

/** Normalize both ColorOS horizontal battery progress paths after native updates. */
public final class OplusHorizontalBatteryHook {
    private OplusHorizontalBatteryHook() {}

    public static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        SharedPreferences preferences = module.getRemotePreferences(NetworkTypeOptions.GROUP);
        Class<?> base = loader.loadClass(
                "com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.BatteryBarDrawableBase");
        Class<?> horizontal = loader.loadClass(
                "com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable");
        Method batteryLevel = horizontal.getMethod("getBatteryLevel");
        Field inside = base.getDeclaredField("insideDrawable");
        Field outside = horizontal.getDeclaredField("progressDrawable");
        inside.setAccessible(true);
        outside.setAccessible(true);
        AtomicBoolean loggedInside = new AtomicBoolean();
        AtomicBoolean loggedOutside = new AtomicBoolean();
        AtomicBoolean enabled = new AtomicBoolean(preferences.getBoolean(BatteryOptions.ENABLED, BatteryOptions.DEFAULT_ENABLED));
        preferences.registerOnSharedPreferenceChangeListener((prefs, key) -> {
            if (key == null || BatteryOptions.ENABLED.equals(key)) {
                enabled.set(prefs.getBoolean(BatteryOptions.ENABLED, BatteryOptions.DEFAULT_ENABLED));
            }
        });

        // Internal percent: superclass builds insideDrawable. External/no percent:
        // updateProgressDrawable builds its own layer and bypasses getProgressLevel.
        for (Method update : new Method[]{base.getMethod("updateBatteryLevel"),
                horizontal.getMethod("updateProgressDrawable")}) {
            boolean internal = update.getDeclaringClass() == base;
            Field progress = internal ? inside : outside;
            AtomicBoolean logged = internal ? loggedInside : loggedOutside;
            module.hook(update)
                    .setId("amalor-battery-progress:" + update.getName())
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        Object owner = chain.getThisObject();
                        if (!horizontal.isInstance(owner)) return result;
                        try {
                            Object value = progress.get(owner);
                            if (value instanceof LayerDrawable) {
                                int level = (Integer) batteryLevel.invoke(owner);
                                if (level >= 0 && level <= 100) {
                                    boolean enabledValue = enabled.get();
                                    int mapped = enabledValue ? BatteryProgress.level(level) : level * 100;
                                    ((LayerDrawable) value).setLevel(mapped);
                                    if (logged.compareAndSet(false, true)) {
                                        HookLog.write(module, Log.DEBUG, "Amalor", "battery progress update active path="
                                                + (internal ? "inside" : "outside") + " level=" + level
                                                 + " drawableLevel=" + mapped + " enabled=" + enabledValue);
                                    }
                                }
                            }
                        } catch (Throwable error) {
                            HookLog.write(module, Log.WARN, "Amalor", "battery progress refresh skipped", error);
                        }
                        return result;
                    });
        }
        // These small update methods can be inlined in their callers by ART.
        module.deoptimize(horizontal.getMethod("updateBatteryLevel"));
        module.deoptimize(horizontal.getMethod("updateDrawable"));
        module.deoptimize(base.getMethod("setBatteryLevel", int.class));
        installPercentCentering(module, base, horizontal);
    }

    private record Body(int width, int height, int[] bounds) {}

    private static void installPercentCentering(XposedModule module, Class<?> base, Class<?> horizontal)
            throws ReflectiveOperationException {
        Field outside = base.getDeclaredField("outsideDrawable");
        outside.setAccessible(true);
        Method bodyRect = horizontal.getMethod("getBodyRectForLayout", RectF.class);
        Method rtl = horizontal.getMethod("isRTLLayout");
        var measured = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<Drawable, Body>());
        module.hook(bodyRect).setId("amalor-battery-visible-body")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    RectF result = (RectF) chain.proceed();
                    Object owner = chain.getThisObject();
                    Drawable drawable = (Drawable) outside.get(owner);
                    if (drawable == null) return result;
                    Rect area = drawable.getBounds();
                    int width = area.width(), height = area.height();
                    if (width <= 0 || height <= 0 || width > 512 || height > 512) return result;
                    Body body = measured.get(drawable);
                    if (body == null || body.width != width || body.height != height) {
                        Drawable.ConstantState state = drawable.getConstantState();
                        if (state == null) return result;
                        // Measure an isolated copy once per shape/size; native tint, alpha and bounds stay owned by SystemUI.
                        Drawable copy = state.newDrawable().mutate();
                        copy.setState(drawable.getState());
                        copy.setLevel(drawable.getLevel());
                        copy.setLayoutDirection(drawable.getLayoutDirection());
                        copy.setBounds(0, 0, width, height);
                        copy.setAlpha(255);
                        copy.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                        Bitmap mask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        try {
                            copy.draw(new Canvas(mask));
                            int[] pixels = new int[width * height];
                            mask.getPixels(pixels, 0, width, 0, 0, width, height);
                            body = new Body(width, height, BatteryTextGeometry.INSTANCE.bodyBounds(pixels, width, height));
                            measured.put(drawable, body);
                        } finally {
                            mask.recycle();
                        }
                    }
                    if (body.bounds == null) return result;
                    float left = area.left + body.bounds[0], right = area.left + body.bounds[2];
                    if ((Boolean) rtl.invoke(owner)) {
                        RectF content = (RectF) chain.getArg(0);
                        float axis = content.centerX() * 2;
                        float mirroredLeft = axis - right;
                        right = axis - left;
                        left = mirroredLeft;
                    }
                    result.set(left, area.top + body.bounds[1], right, area.top + body.bounds[3]);
                    return result;
                });

        // Normal text already uses glyph ink bounds. Make the wide-font/100 fallback use the same measured body.
        var offset = new ThreadLocal<Float>();
        Method drawContent = horizontal.getMethod("drawContent", Canvas.class, RectF.class);
        module.hook(drawContent).setId("amalor-battery-text-center-scope")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    RectF content = (RectF) chain.getArg(1);
                    RectF body = (RectF) bodyRect.invoke(chain.getThisObject(), content);
                    Float previous = offset.get();
                    offset.set(BatteryTextGeometry.INSTANCE.centerOffset(content.centerX(), body.centerX(),
                            (Boolean) rtl.invoke(chain.getThisObject())));
                    try { return chain.proceed(); }
                    finally { if (previous == null) offset.remove(); else offset.set(previous); }
                });
        Method getOffset = horizontal.getDeclaredMethod("getOffsetX");
        getOffset.setAccessible(true);
        module.hook(getOffset).setId("amalor-battery-wide-text-center")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> offset.get() != null ? offset.get() : chain.proceed());
        module.deoptimize(bodyRect);
        module.deoptimize(drawContent);
        module.deoptimize(getOffset);
    }
}
