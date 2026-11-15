package me.acardia.amalor.xposed.systemui

import android.app.Dialog
import android.app.KeyguardManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.view.View
import io.github.libxposed.api.XposedInterface
import me.acardia.amalor.NativeFeatureOptions
import java.lang.ref.WeakReference

/** Audited adaptation of local AOSP_Panels FlashlightPanel and PanelMotion. */
internal class AospFlashlightHook(private val r: NativeFeatures) {
    private val main = Handler(Looper.getMainLooper())
    private val key = NativeFeatureOptions.FLASHLIGHT_PANEL
    private val graphType = r.type("com.oplus.systemui.dagger.DaggerOplusReferenceGlobalRootComponent\$OplusReferenceSysUIComponentImpl")
    private val providerFields = listOf("flashlightRepositoryImplProvider", "flashlightDialogDelegateProvider",
        "flashlightControllerImplProvider", "superPowerSaveRepositoryImplProvider").associateWith { r.field(graphType, it) }
    private val getProvider = r.type("dagger.internal.Provider").getMethod("get")
    private val repositoryType = r.type("com.android.systemui.flashlight.data.repository.FlashlightRepositoryImpl")
    private val cameras = r.field(repositoryType, "cameraManager")
    private val supportsFlashlight = repositoryType.getDeclaredMethod("getDeviceSupportsFlashlight")
    private val start = repositoryType.getDeclaredMethod("start")
    private val limited = r.field(r.type("com.android.systemui.statusbar.policy.FlashlightControllerImpl"), "mTorchLimited")
    private val powerSave = r.type("com.oplus.systemui.qs.pipeline.data.repository.SuperPowerSaveRepositoryImpl")
        .getDeclaredMethod("getInSuperPowerSaveMode")
    private val delegateType = r.type("com.android.systemui.flashlight.ui.dialog.FlashlightDialogDelegate")
    private val currentDialog = r.field(delegateType, "currentDialog")
    private val createDialog = delegateType.getDeclaredMethod("createDialog")
    private val transitionAnimator = r.field(delegateType, "dialogTransitionAnimator")
    private val expandable = r.type("com.android.systemui.animation.Expandable")
    private val fromView = expandable.getDeclaredMethod("fromView", View::class.java)
    private val launchable = r.type("com.android.systemui.animation.LaunchableView")
    private val cuj = r.type("com.android.systemui.animation.DialogCuj")
    private val marker = cuj.getDeclaredConstructor(Int::class.javaPrimitiveType, String::class.java)
    private val transitionController = expandable.getDeclaredMethod("dialogTransitionController", cuj)
    private val animateShow = r.type("com.android.systemui.animation.DialogTransitionAnimator")
        .getDeclaredMethod("show", Dialog::class.java,
            r.type("com.android.systemui.animation.DialogTransitionAnimator\$Controller"), Boolean::class.javaPrimitiveType)
    private var graph: Any? = null
    private var appContext: Context? = null
    private var startedRepository: Any? = null
    private var startedByModule: Any? = null
    private var validatedRepository: Any? = null
    private var active = WeakReference<Dialog>(null)

    private fun provider(name: String): Any = requireNotNull(getProvider.invoke(providerFields.getValue(name).get(graph)))
    private fun locked(context: Context): Boolean = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != false
    private fun isLimited(): Boolean = limited.getBoolean(provider("flashlightControllerImplProvider"))
    private fun superPowerSave(): Boolean = powerSave.invoke(provider("superPowerSaveRepositoryImplProvider")) == true

    fun install() {
        r.hook("flashlight:repository-start", start) { chain ->
            synchronized(this) {
                if (startedByModule === chain.thisObject) null
                else chain.proceed().also { startedRepository = chain.thisObject }
            }
        }
        val slider = r.type("com.android.systemui.flashlight.ui.viewmodel.FlashlightSliderViewModel")
        val setLevel = slider.getDeclaredMethod("setFlashlightLevel\$1", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        r.hook("flashlight:level-policy", setLevel) { chain ->
            val dialog = active.get()
            val context = appContext
            if (dialog?.isShowing != true || context == null || chain.getArg(0) == 0) return@hook chain.proceed()
            if (FlashlightPanelRules.blockLevel(chain.getArg(0) as Int, locked(context), isLimited(), superPowerSave())) {
                dialog.dismiss()
                r.info("Native flashlight panel closed: system torch restriction")
                null
            } else chain.proceed()
        }
        slider.declaredMethods.filter { it.name.startsWith("setFlashlightLevel") }.forEach { r.module.deoptimize(it) }

        val adapter = r.type("com.oplus.systemui.qs.detail.flashlight.OplusQSFlashlightDetailProvider")
        val contextField = r.field(adapter, "context")
        val anchor = adapter.getDeclaredMethod("getExpandableView")
        val showDialog = r.type("com.oplusos.systemui.viewdialog.domain.SysUIViewDialogInteractor")
            .getDeclaredMethod("showDialog", r.type("com.oplusos.systemui.common.viewdialog.model.ViewDialogAdapter"))
        check(showDialog.returnType == Void.TYPE)
        r.hook("flashlight:detail-route", showDialog) { chain ->
            val detail = chain.getArg(0)
            if (!adapter.isInstance(detail) || !r.enabled(key)) return@hook chain.proceed()
            val context = contextField.get(detail) as Context
            fun open(): Boolean = runCatching {
                if (locked(context) || !r.enabled(key)) return@runCatching false
                show(context, anchor.invoke(detail) as? View)
            }.getOrElse { r.error("Native flashlight panel fallback", it.cause ?: it); false }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                if (open()) null else chain.proceed()
            } else {
                // QSTile handlers run on their background looper. Never retain a Chain across threads.
                val original = r.module.getInvoker(showDialog).setType(XposedInterface.Invoker.Type.ORIGIN)
                val receiver = chain.thisObject
                val args = chain.args.toTypedArray()
                val caller = Looper.myLooper()?.let(::Handler) ?: main
                main.post {
                    if (!open()) caller.post {
                        runCatching { original.invoke(receiver, *args) }
                            .onFailure { r.error("Stock flashlight detail fallback failed", it) }
                    }
                }
                null
            }
        }
        r.module.deoptimize(showDialog)
        r.type("com.oplus.systemui.qs.tiles.OplusFlashlightTile").declaredMethods
            .filter { it.name == "handleLongClick" || it.name == "handleSecondaryClick" }
            .forEach { r.module.deoptimize(it) }
    }

    private fun show(context: Context, anchor: View?): Boolean {
        val application = context.applicationContext
        if (graph == null) graph = requireNotNull(r.field(application.javaClass, "mSysUIComponent").get(application))
        if (!FlashlightPanelRules.canOpen(r.enabled(key), locked(context), isLimited(), superPowerSave())) return false
        val repository = provider("flashlightRepositoryImplProvider")
        if (validatedRepository !== repository) {
            if (!adjustable(cameras.get(repository) as CameraManager)) return false
            validatedRepository = repository
        }
        synchronized(this) {
            if (startedRepository !== repository && supportsFlashlight.invoke(repository) != true) {
                start.invoke(repository)
                startedByModule = repository
                startedRepository = repository
            }
        }
        val delegate = provider("flashlightDialogDelegateProvider")
        // createDialog owns this field and its ON_STOP cleanup, including an in-flight show.
        if (currentDialog.get(delegate) != null) return true
        val dialog = createDialog.invoke(delegate) as Dialog
        active = WeakReference(dialog)
        appContext = application
        try {
            dialog.window?.setWindowAnimations(android.R.style.Animation_Dialog)
            var source = anchor
            while (source != null && !(source.isAttachedToWindow && launchable.isInstance(source))) source = source.parent as? View
            val controller = source?.let { view ->
                transitionController.invoke(fromView.invoke(null, view), marker.newInstance(58, "flashlight"))
            }
            val shown = controller != null && animateShow.invoke(transitionAnimator.get(delegate), dialog, controller, false) == true
            if (!shown && !dialog.isShowing) dialog.show()
            r.info("Native flashlight panel opened")
            return true
        } catch (error: Throwable) {
            dialog.dismiss()
            if (currentDialog.get(delegate) === dialog) currentDialog.set(delegate, null)
            active = WeakReference(null)
            throw error
        }
    }

    private fun adjustable(manager: CameraManager): Boolean {
        // Match the repository's first rear-facing flash camera; do not advertise invented levels.
        for (id in manager.cameraIdList) {
            val info = manager.getCameraCharacteristics(id)
            if (info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                info.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                return FlashlightPanelRules.adjustable(info.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL),
                    info.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL))
            }
        }
        return false
    }
}
