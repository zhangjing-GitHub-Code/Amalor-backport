package me.acardia.amalor.xposed.launcher;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/** Reproduces the retained/inlined launcher ABIs without Android or Xposed test stubs. */
public final class LauncherCompatibilityCheck {
    public static class Item {}
    public static class Bitmap {}
    public static class LegacyCell {
        int applied;
        public void applyFromCellItem(Item item, float scale, Consumer<Bitmap> preview, Bitmap cached) {
            require(scale == 1f && cached == null, "Legacy binding keeps scale 1 and no cached preview");
            applied = 4;
            preview.accept(new Bitmap());
        }
    }
    public static class ModernBase {
        int applied;
        public void applyFromCellItem(Item item, Consumer<Bitmap> preview) { throw new AssertionError("Use the concrete cell override"); }
    }
    public static class ModernCell extends ModernBase {
        @Override public void applyFromCellItem(Item item, Consumer<Bitmap> preview) {
            applied = 2;
            preview.accept(new Bitmap());
        }
    }
    public static class BothCell extends LegacyCell {
        public void applyFromCellItem(Item item, Consumer<Bitmap> preview) { applied = 2; }
    }
    public static class WrongCell { public void applyFromCellItem(Item item, String value) {} }

    public static class BaseState {}
    public static class State extends BaseState {}
    public static class OldLauncher { public boolean isInState(State state) { return true; } }
    public static class NewLauncher {
        public boolean isInState(BaseState state) { return true; }
        public boolean isInState(String unrelated) { return false; }
    }
    private static Object appContext;
    private static int stateInitializations;
    public static class StartupState extends BaseState {
        public static final StartupState NORMAL = create();
        private static StartupState create() {
            stateInitializations++;
            appContext.getClass();
            return new StartupState();
        }
    }
    public static class StartupLauncher {
        public boolean isInState(BaseState state) { return state == StartupState.NORMAL; }
    }
    private static Object missingContext;
    public static class PoisonedState {
        public static final Object NORMAL = missingContext.getClass();
    }
    public static class Rect {}
    public static class RectF {}
    public static class PointF {}
    public static class Motion {}
    public static class Event {}
    public static class Session { public float d; }
    public static class OldController {
        public Rect c, o;
        public RectF s;
        public PointF z;
        public Session q;
        public float y, d0;
        public WeakReference<Object> w;
        public OldAnimator animator;
        public float v() { return 20f; }
        public void s() {}
    }
    public static class NewController {
        public Rect r, u;
        public RectF y;
        public PointF F;
        public Session w;
        public float E, j0;
        public WeakReference<Object> C;
        public NewAnimator animator;
        public float k() { return 20f; }
        public void h() {}
    }
    public static class OldManager { private OldController mBackAnimationController; }
    public static class NewManager { private NewController mBackAnimationController; }
    public static class OldCallback {
        private OldController owner;
        public void progress(Event event) {}
        public static void unrelated(Event event) {}
    }
    public static class NewCallback { private NewController owner; }
    public static class OldAnimator { public void start(Motion motion, OldCallback callback) {} }
    public static class NewAnimator {
        public Motion motion;
        public NewCallback callback;
        public void start(Motion motion, NewCallback callback) {}
        public void dispatch(float value) {}
        public static void unrelated(float value) {}
    }

    public static void main(String[] args) throws Exception {
        var rules = LauncherCompatibilityRules.INSTANCE;
        require(rules.initialPage(0) == null && rules.initialPage(-1) == null && rules.initialPage(11) == null, "Default/invalid settings retain native routing");
        require(rules.initialPage(10) == 10, "Initialization retains the selected page before children are bound");
        require(rules.landingPage(10, 3) == 2 && rules.landingPage(1, 1) == 0, "Navigation clamps to actual children");
        require(rules.landingPage(1, 0) == null && rules.landingPage(0, 5) == null, "Empty/default workspaces do not redirect");
        for (int status : new int[]{1, 2, 3, 0x40000000, 0x40000008, 9})
            require(rules.isPromise(status), "Retain all OEM promise flags, even when promise UI is hidden");
        for (int status : new int[]{0, 4, 8, 64}) require(!rules.isPromise(status), "Other flags do not imply a promise app");

        LegacyCell old = new LegacyCell();
        ModernCell modern = new ModernCell();
        int[] previews = {0};
        Consumer<Bitmap> consumer = bitmap -> previews[0]++;
        Method legacy = rules.widgetBinding(LegacyCell.class, Item.class, Bitmap.class);
        legacy.invoke(old, rules.widgetArguments(legacy, new Item(), consumer));
        Method current = rules.widgetBinding(ModernBase.class, Item.class, Bitmap.class);
        current.invoke(modern, rules.widgetArguments(current, new Item(), consumer));
        require(old.applied == 4 && modern.applied == 2 && previews[0] == 2, "Both ABIs reach real cell binding and preview callbacks");
        require(rules.widgetBinding(BothCell.class, Item.class, Bitmap.class).getParameterCount() == 2, "Prefer the current public ABI when both exist");
        try { rules.widgetBinding(WrongCell.class, Item.class, Bitmap.class); throw new AssertionError("Reject an unrelated overload"); }
        catch (NoSuchMethodException expected) {}
        require((boolean) rules.stateCheck(OldLauncher.class, State.class).invoke(new OldLauncher(), new State()), "Old state signature");
        require((boolean) rules.stateCheck(NewLauncher.class, State.class).invoke(new NewLauncher(), new State()), "New BaseState signature");

        // A failed static initializer poisons the class even after the dependency becomes ready.
        try { PoisonedState.class.getDeclaredField("NORMAL").get(null); throw new AssertionError("Reproduce early host initialization failure"); }
        catch (ExceptionInInitializerError expected) { require(expected.getCause() instanceof NullPointerException, "Missing application context is the original failure"); }
        missingContext = new Object();
        try { PoisonedState.class.getDeclaredField("NORMAL").get(null); throw new AssertionError("The failed class must remain unusable"); }
        catch (NoClassDefFoundError expected) { }

        var normalState = rules.normalStateCheck(StartupLauncher.class, StartupState.class);
        require(stateInitializations == 0, "Resolving launcher targets must not initialize host state singletons");
        appContext = new Object();
        require(normalState.test(new StartupLauncher()), "Resolve NORMAL only after native application setup");
        require(normalState.test(new StartupLauncher()) && stateInitializations == 1, "Subsequent state checks reuse normal class initialization");

        require(rules.backController(OldManager.class) == OldController.class, "Resolve the original controller from the stable owner");
        require(rules.backController(NewManager.class) == NewController.class, "Resolve a renamed controller without its class name");
        require(rules.backLayout(OldController.class, Rect.class, RectF.class, PointF.class).getBounds().equals("c"), "Validate the legacy field layout");
        require(rules.backLayout(NewController.class, Rect.class, RectF.class, PointF.class).getBounds().equals("r"), "Validate the new field layout");
        try { rules.backLayout(NewController.class, PointF.class, RectF.class, Rect.class); throw new AssertionError("Reject mismatched field types"); }
        catch (NoSuchFieldException expected) {}
        require(rules.backBegin(OldController.class, Motion.class).getDeclaringClass() == OldAnimator.class, "Legacy begin contract");
        require(rules.backBegin(NewController.class, Motion.class).getDeclaringClass() == NewAnimator.class, "Begin still resolves after callback inlining");
        require(rules.backUpdate(OldCallback.class, Event.class).getName().equals("progress"), "Find the instance BackEvent callback by signature");
        require(rules.backUpdate(NewCallback.class, Event.class) == null, "Recognize a field-only callback carrier");
        require(rules.backDispatch(NewAnimator.class).getName().equals("dispatch"), "Resolve the actual float progress dispatcher");
        require(rules.backProgress(75f) == 0.75f && rules.backProgress(100f) == 1f, "Use native animated progress units");
        require(rules.backProgress(120f) > 1f, "Geometry rules, not the ABI adapter, handle spring overshoot");
        System.out.println("Launcher startup/17.3/17.5 landing/widget/predictive ABI checks passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
