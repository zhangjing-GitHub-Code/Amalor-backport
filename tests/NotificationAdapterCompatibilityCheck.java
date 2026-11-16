package me.acardia.amalor.xposed.systemui;

import java.lang.reflect.Method;

/** Reproduces the runtime OEM ABI that lacks the typed TextView overload. */
public final class NotificationAdapterCompatibilityCheck {
    public static class Context {}
    public static class View {}
    public static class TextView extends View {}
    public static class Row extends View {}
    public static class GenericOnly {
        public boolean applied;
        public void applyMaterial(Context context, View view, Row row) { applied = true; }
    }
    public static class Inherited extends GenericOnly {}
    public static class Both extends GenericOnly {
        public void applyMaterial(Context context, TextView view, Row row) { throw new AssertionError("Typed overload must not be preferred"); }
    }
    public static class TypedOnly {
        public boolean applied;
        public void applyMaterial(Context context, TextView view, Row row) { applied = true; }
    }
    public abstract static class AbstractOnly {
        public abstract void applyMaterial(Context context, View view, Row row);
    }
    public static class WrongShape {
        public boolean applyMaterial(Context context, View view, Row row) { return true; }
        public void applyMaterial(Context context, View view) {}
        public void applyNormal(Context context, View view, Row row) {}
    }

    public static void main(String[] args) throws Exception {
        try {
            GenericOnly.class.getDeclaredMethod("applyMaterial", Context.class, TextView.class, Row.class);
            throw new AssertionError("Fixture must reproduce NoSuchMethodException for the old lookup");
        } catch (NoSuchMethodException expected) { }
        for (GenericOnly adapter : new GenericOnly[]{new GenericOnly(), new Inherited(), new Both()}) {
            Method method = resolve(adapter.getClass(), "applyMaterial");
            require(method != null && method.getParameterTypes()[1] == View.class, "Resolve the generic/inherited runtime ABI");
            method.invoke(adapter, new Context(), new TextView(), new Row());
            require(adapter.applied, "The selected method must execute the real generic implementation");
        }
        TypedOnly typed = new TypedOnly();
        resolve(TypedOnly.class, "applyMaterial").invoke(typed, new Context(), new TextView(), new Row());
        require(typed.applied, "Retain support for builds exposing only the typed implementation");
        require(resolve(WrongShape.class, "applyMaterial") == null, "Reject wrong return/parameter shapes");
        require(resolve(AbstractOnly.class, "applyMaterial") == null, "Do not hook an abstract declaration");
        require(resolve(GenericOnly.class, "applyNormal") == null, "Missing optional adapters return null instead of aborting installation");
        System.out.println("Notification runtime adapter compatibility checks passed");
    }

    private static Method resolve(Class<?> type, String name) {
        return NotificationAppearanceRules.INSTANCE.materialMethod(type.getMethods(), name, Context.class, View.class, Row.class);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
