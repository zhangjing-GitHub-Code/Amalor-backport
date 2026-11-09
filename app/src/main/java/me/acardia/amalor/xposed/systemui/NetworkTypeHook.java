package me.acardia.amalor.xposed.systemui;

import android.util.Log;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import me.acardia.amalor.NetworkTypeOptions;
import me.acardia.amalor.xposed.HookLog;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Merged dual-SIM icon layout and network type font.
 *
 * <p>ColorOS 17 uses a Compose pipeline (layout strategy factories under
 * {@code statusbar.pipeline.shared.ui.composable}); ColorOS 15 uses a View pipeline
 * ({@code OplusMobileViewBinderFactory} selecting a {@code *StatusBarMobileViewBinder}).
 * Both are probed; the first one present is installed. A missing class is normal, since each
 * release ships only one of the two pipelines.
 */
public final class NetworkTypeHook {
    private final XposedModule module;
    private static final String TAG = "Amalor";
    private static final String COMPOSABLE =
            "com.android.systemui.statusbar.pipeline.shared.ui.composable.";
    private static final String VIEW_15 =
            "com.oplus.systemui.statusbar.pipeline.mobile.ui.view.";

    // Compose uses three changed bits and one default bit per source parameter.
    private static final int FONT_CHANGED_MASK = (7 << 13) | (7 << 16) | (7 << 19);
    private static final int FONT_DEFAULT_MASK = (1 << 4) | (1 << 5) | (1 << 6);

    private boolean layoutInstalled;
    private boolean fontInstalled;
    private boolean artworkInstalled;
    private boolean optionsLoaded;
    private boolean active;
    private boolean useBold;
    private int fontStyle;

    public NetworkTypeHook(XposedModule module) {
        this.module = module;
    }

    public synchronized void install(ClassLoader loader) {
        if (loader == null) return;
        if (!optionsLoaded) {
            try {
                SharedPreferences preferences = module.getRemotePreferences(NetworkTypeOptions.GROUP);
                int mode = preferences.getInt(NetworkTypeOptions.MODE, 0);
                fontStyle = NetworkTypeOptions.supportedFont(
                        preferences.getInt(NetworkTypeOptions.FONT, NetworkTypeOptions.OFFICIAL));
                // Enabling the modification always implies separate display.
                active = NetworkTypeOptions.isActive(mode);
                useBold = NetworkTypeOptions.canUseBold(mode, fontStyle)
                        && preferences.getBoolean(NetworkTypeOptions.BOLD, false);
                optionsLoaded = true; // Settings are applied on the next SystemUI restart.
            } catch (Throwable error) {
                HookLog.write(module, Log.ERROR, TAG, "could not read network type settings; keeping system defaults", error);
                return;
            }
        }
        if (!active) return;
        // Track each hook separately so a later callback only retries the failed part.
        if (!layoutInstalled) {
            try {
                installLayout(loader);
                layoutInstalled = true;
            } catch (Throwable error) {
                HookLog.write(module, Log.ERROR, TAG, "merged dual-SIM layout hook failed", error);
            }
        }
        if (!artworkInstalled) {
            try {
                PixelNetworkArtworkHook.install(module, loader);
                artworkInstalled = true;
            } catch (Throwable error) {
                HookLog.write(module, Log.ERROR, TAG, "Pixel network artwork hook failed", error);
            }
        }
        if (!fontInstalled && (fontStyle == NetworkTypeOptions.SYSTEM || fontStyle == NetworkTypeOptions.GOOGLE_ROUNDED)) {
            try {
                fontInstalled = installFont(loader);
            } catch (Throwable error) {
                HookLog.write(module, Log.ERROR, TAG, "merged dual-SIM font hook failed", error);
            }
        }
    }

    /** Probes the ColorOS 17 pipeline first, then the ColorOS 15 one. */
    private void installLayout(ClassLoader loader) throws ReflectiveOperationException {
        if (hasClass(loader, COMPOSABLE + "OplusStackedMobileIconLayoutStrategyFactory")) {
            installStackedStrategy(loader);
            module.log(Log.INFO, TAG, "installed ColorOS 17 merged dual-SIM BigType strategy");
            return;
        }
        if (hasClass(loader, VIEW_15 + "OplusMobileViewBinderFactory")) {
            installBigTypeViewBinder(loader);
            module.log(Log.INFO, TAG, "installed ColorOS 15 merged dual-SIM BigType view binder");
            return;
        }
        throw new ClassNotFoundException("no known stacked mobile icon pipeline");
    }

    /**
     * Font is only meaningful where the network type is rendered as text. ColorOS 15 draws it
     * as vector icons, so there is nothing to re-typeface and this is skipped silently.
     */
    private boolean installFont(ClassLoader loader) throws ReflectiveOperationException, IOException {
        if (!hasClass(loader, COMPOSABLE + "OplusStackedMobileIconLayoutStrategyKt")) {
            module.log(Log.INFO, TAG, "network type is not text on this build; font change skipped");
            return true; // Nothing to retry.
        }
        installStackedNetworkFont(loader);
        module.log(Log.INFO, TAG, "installed ColorOS 17 merged dual-SIM network font");
        return true;
    }

    private void installStackedStrategy(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> factory = loader.loadClass(COMPOSABLE + "OplusStackedMobileIconLayoutStrategyFactory");
        Object bigStrategy = loader.loadClass(COMPOSABLE + "BigTypeStackedMobileIconLayoutStrategy")
                .getField("INSTANCE").get(null);
        // Deoptimize the caller, where ART may have inlined the factory getter.
        deoptimizeNamedMethods(loader, "OplusStackedMobileIconKt", "OplusStackedMobileIcon");
        module.hook(factory.getDeclaredMethod("getStrategy"))
                .setId("amalor-big-network-type-strategy")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> bigStrategy);
    }

    /**
     * ColorOS 15: pick the BigType binder instead of the plain one.
     *
     * <p>{@code getMobileViewBinder()} returns one of four binders based on
     * {@code CustomizeFeatureOption} flags. ART may inline the getter into the signal pipeline,
     * so its callers are deoptimized before hooking. {@code sIsShowBig5G} is also set, but
     * {@code CustomizeFeatureOption.loadAppFeature()} reassigns that field at runtime and can
     * overwrite it, so the flag alone is not reliable.
     */
    private void installBigTypeViewBinder(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> factory = loader.loadClass(VIEW_15 + "OplusMobileViewBinderFactory");
        Method getBinder = factory.getMethod("getMobileViewBinder");
        Field bigTypeLazy = findBigTypeField(factory);
        forceBig5GFlag(loader);
        // The selection is inlined into the signal pipeline; deoptimize its callers.
        deoptimizeNamedMethods(loader,
                "com.android.systemui.statusbar.pipeline.mobile.ui.binder.MobileIconBinder", "bind");
        deoptimizeNamedMethods(loader,
                "com.oplus.systemui.statusbar.pipeline.OplusMobileSignalExImpl", "bindEx");
        module.hook(getBinder)
                .setId("amalor-big-network-type-view-binder")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object stored = bigTypeLazy.get(chain.getThisObject());
                    Object binder = unwrapLazy(stored);
                    return binder == null ? chain.proceed() : binder;
                });
    }

    /** Sets the feature flag the factory reads; the getter hook above remains the primary path. */
    private void forceBig5GFlag(ClassLoader loader) {
        try {
            Class<?> options = loader.loadClass("com.oplusos.systemui.common.feature.CustomizeFeatureOption");
            Field flag = options.getField("sIsShowBig5G");
            flag.setBoolean(null, true);
            module.log(Log.INFO, TAG, "forced CustomizeFeatureOption.sIsShowBig5G");
        } catch (Throwable unavailable) {
            // Older or differently-featured builds: the getter hook below still applies.
            module.log(Log.DEBUG, TAG, "sIsShowBig5G unavailable; relying on the factory hook");
        }
    }

    /** The factory keeps the BigType binder in a field, usually a {@code dagger.Lazy}. */
    private static Field findBigTypeField(Class<?> factory) throws NoSuchFieldException {
        for (Field field : factory.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            if (name.contains("bigtype") && !name.contains("sprint") && !name.contains("tmo")) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new NoSuchFieldException("no BigType binder field on " + factory.getName());
    }

    private void installStackedNetworkFont(ClassLoader loader) throws ReflectiveOperationException, IOException {
        Class<?> fontFamily = loader.loadClass("androidx.compose.ui.text.font.FontFamily");
        Object selectedFont;
        if (fontStyle == NetworkTypeOptions.GOOGLE_ROUNDED) {
            selectedFont = loader.loadClass("androidx.compose.ui.text.font.AndroidTypeface_androidKt")
                    .getMethod("FontFamily", Typeface.class).invoke(null, loadGoogleRounded());
        } else {
            Object familyCompanion = fontFamily.getField("Companion").get(null);
            selectedFont = familyCompanion.getClass().getMethod("getSansSerif").invoke(familyCompanion);
        }
        Class<?> fontWeight = loader.loadClass("androidx.compose.ui.text.font.FontWeight");
        Object weightCompanion = fontWeight.getField("Companion").get(null);
        Object selectedWeight = weightCompanion.getClass()
                .getMethod(useBold || fontStyle == NetworkTypeOptions.GOOGLE_ROUNDED ? "getBold" : "getNormal")
                .invoke(weightCompanion);

        // Match the complete APK signature before replacing positional arguments.
        Method networkText = loader.loadClass(COMPOSABLE + "OplusStackedMobileIconLayoutStrategyKt")
                .getDeclaredMethod("NetworkTypeText-Y85pibM",
                        loader.loadClass("com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.StackedMobileIconViewModel"),
                        loader.loadClass("com.android.systemui.statusbar.pipeline.mobile.domain.model.OplusNetworkTypeTextModel"),
                        long.class, loader.loadClass("androidx.compose.ui.Modifier"),
                        fontFamily, fontWeight, fontFamily,
                        loader.loadClass(COMPOSABLE + "OplusStackedMobileIconDimensions"),
                        loader.loadClass("androidx.compose.ui.unit.TextUnit"),
                        loader.loadClass("androidx.compose.ui.unit.TextUnit"),
                        loader.loadClass("androidx.compose.runtime.Composer"), int.class, int.class);

        deoptimizeNamedMethods(loader, "BigTypeStackedMobileIconLayoutStrategy", "StackedMobileIcon");
        // The default strategy also calls NetworkTypeText if the strategy hook is unavailable.
        deoptimizeNamedMethods(loader, "DefaultStackedMobileIconLayoutStrategy", "StackedMobileIcon");
        deoptimizeNamedMethods(loader,
                "OplusStackedMobileIconLayoutStrategyKt$$ExternalSyntheticLambda0", "invoke");
        module.hook(networkText)
                .setId("amalor-system-network-font")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    args[4] = selectedFont;
                    args[5] = selectedWeight;
                    args[6] = selectedFont; // NetworkTypeText.isLarge() selects this family.
                    // Recalculate font changes; prevent defaults from replacing our values.
                    args[11] = (Integer) args[11] & ~FONT_CHANGED_MASK;
                    args[12] = (Integer) args[12] & ~FONT_DEFAULT_MASK;
                    // Keep the native model, including null: visibility is owned by its Flow.
                    return chain.proceed(args);
                });
    }

    private Typeface loadGoogleRounded() throws IOException {
        // Read the module APK directly: no target Context, hidden AssetManager API or temp file.
        try (ZipFile apk = new ZipFile(module.getModuleApplicationInfo().sourceDir)) {
            ZipEntry entry = apk.getEntry("assets/fonts/GoogleSansRounded-Bold.ttf");
            if (entry == null) throw new IOException("Missing bundled Google Sans Rounded font");
            try (InputStream input = apk.getInputStream(entry)) {
                byte[] bytes = readAll(input);
                ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
                buffer.put(bytes).flip();
                Font font = new Font.Builder(buffer).build();
                return new Typeface.CustomFallbackBuilder(new FontFamily.Builder(font).build())
                        .setStyle(font.getStyle()).setSystemFallback("sans-serif").build();
            }
        }
    }

    /** {@code InputStream.readAllBytes()} is API 33; this module also runs on older releases. */
    private static byte[] readAll(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) != -1) out.write(chunk, 0, read);
        return out.toByteArray();
    }

    /** The factory field may hold a {@code dagger.Lazy}; returns the underlying binder. */
    private static Object unwrapLazy(Object stored) {
        if (stored == null) return null;
        try {
            Method get = stored.getClass().getMethod("get");
            if (get.getReturnType() != void.class && get.getParameterCount() == 0) {
                Object unwrapped = get.invoke(stored);
                if (unwrapped != null) return unwrapped;
            }
        } catch (Throwable ignored) {
            // Not a Lazy: the stored value is the binder itself.
        }
        return stored;
    }

    private static boolean hasClass(ClassLoader loader, String name) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** A missing target is not an error: it only means the caller was never inlined. */
    private void deoptimizeNamedMethods(ClassLoader loader, String simpleOrFullName, String methodName) {
        String className = simpleOrFullName.contains(".") ? simpleOrFullName : COMPOSABLE + simpleOrFullName;
        Class<?> type;
        try {
            type = loader.loadClass(className);
        } catch (Throwable missing) {
            module.log(Log.DEBUG, TAG, "deoptimize target absent: " + className);
            return;
        }
        boolean found = false;
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                if (!module.deoptimize(method)) {
                    HookLog.write(module, Log.WARN, TAG, "could not deoptimize " + className + "." + methodName);
                }
                found = true;
            }
        }
        if (!found) module.log(Log.DEBUG, TAG, "nothing to deoptimize in " + className);
    }
}
