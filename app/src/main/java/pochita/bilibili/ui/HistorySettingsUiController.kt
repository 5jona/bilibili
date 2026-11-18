package pochita.bilibili.ui

import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import pochita.bilibili.contract.BiliContracts.HistoryMoss
import pochita.bilibili.contract.BiliContracts.HistoryUi
import pochita.bilibili.contract.BiliContracts.KOTLIN_FUNCTION2
import pochita.bilibili.contract.BiliContracts.KOTLIN_LAZY
import pochita.bilibili.contract.BiliContracts.KOTLIN_UNIT
import pochita.bilibili.contract.BiliUiStrings
import pochita.history.HistoryModuleContext
import pochita.xposed.ReflectOps
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap

/**
 * Manages History UI integration inside Bilibili's `HistoryFragmentV3`:
 * - Suppresses the closed-history warning banner.
 * - Rewrites and injects switches into the three-dot **History Settings** menu (`HistoryMoreDialog`
 *   and `ListThreePointMenuDialogFragment`).
 * - Synchronizes Compose switch states cleanly under cloud/local mutual exclusion.
 * - Triggers reliable real-time refreshes across active `HistoryContentFragment` instances.
 */
internal class HistorySettingsUiController(
    private val context: HistoryModuleContext,
) {
    private val runtime = context.runtime
    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var activeHistoryFragmentRef: WeakReference<Any>? = null

    @Volatile
    private var activeSettingsDialogRef: WeakReference<Any>? = null

    @Volatile
    private var cloudSwitchItemRef: WeakReference<Any>? = null

    @Volatile
    private var localSwitchItemRef: WeakReference<Any>? = null

    private val activeContentFragments: MutableSet<Any> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    private val switchStateByModel: MutableMap<Any, WeakReference<Any>> =
        Collections.synchronizedMap(WeakHashMap())

    fun onHistoryFragmentActive(fragment: Any?) {
        if (fragment == null) return
        activeHistoryFragmentRef = WeakReference(fragment)
        hideHistoryBanner(fragment)
    }

    fun isCloudSwitchIdle(): Boolean = context.switchCoordinator.pendingCloudTarget == null

    fun onContentFragmentActive(contentFragment: Any?) {
        if (contentFragment != null) {
            activeContentFragments.add(contentFragment)
        }
    }

    fun onSettingsDialogCreated(dialog: Any?) {
        if (dialog != null) {
            activeSettingsDialogRef = WeakReference(dialog)
        }
    }

    fun registerSwitchStateBinding(wrapper: Any?, modelField: String, stateField: String) {
        val model = ReflectOps.getField(wrapper, modelField) ?: return
        val mutableState = ReflectOps.getField(wrapper, stateField) ?: return
        switchStateByModel[model] = WeakReference(mutableState)
    }

    fun updateCloudStateFromFmJ(stateObj: Any?, fromSwitchCompletion: Boolean) {
        val name = stateObj?.javaClass?.name ?: return
        val cloudClosed = when {
            name.endsWith("\$a") -> true
            name.endsWith("\$b") -> false
            else -> return
        }
        val effect = context.switchCoordinator.onCloudStateObserved(
            cloudClosedFromVm = cloudClosed,
            localEnabled = context.prefs.isEnabled(),
            fromSwitchCompletion = fromSwitchCompletion,
        ) ?: return

        if (effect.disableLocal) {
            context.prefs.setEnabled(false)
            syncSwitchItemChecked(localSwitchItemRef?.get(), false)
        }
        if (fromSwitchCompletion) {
            syncSwitchItemChecked(cloudSwitchItemRef?.get(), !context.isCloudHistoryClosed)
        }
        if (effect.shouldRefreshTabs) {
            refreshHistoryFragmentList(activeHistoryFragmentRef?.get(), reloadTabs = true)
        }
    }

    fun updateCloudStateFromViewModel(viewModel: Any?, fromSwitchCompletion: Boolean) {
        if (viewModel == null) return
        val stateFlow = ReflectOps.getField(viewModel, "d") ?: return
        val hmA = ReflectOps.invokeZeroArg(stateFlow, "getValue") ?: return
        updateCloudStateFromFmJ(ReflectOps.getField(hmA, "a"), fromSwitchCompletion)
    }

    fun onCloudHistorySwitchToggled(turnOn: Boolean) {
        val effect = context.switchCoordinator.onCloudSwitchToggled(turnOn, context.prefs.isEnabled())
        if (effect.disableLocal) {
            context.prefs.setEnabled(false)
            syncSwitchItemChecked(localSwitchItemRef?.get(), false)
            dismissActiveSettingsDialog()
        }
        if (effect.refreshImmediate) {
            refreshHistoryFragmentList(activeHistoryFragmentRef?.get(), reloadTabs = false)
        }
    }

    fun enhanceHistorySettingsModel(modelG: Any?) {
        if (modelG == null) return
        val items = ReflectOps.getField(modelG, "b") as? List<*> ?: return
        val isHistorySettings = items.any { item ->
            ReflectOps.getField(item, "d")?.javaClass?.name
                ?.startsWith(HistoryUi.PREFIX_DIALOG_SWITCH) == true
        }
        if (!isHistorySettings) return

        val switchOwnerFragment = items.firstNotNullOfOrNull { item ->
            val callback = ReflectOps.getField(item, "d")
            ReflectOps.getField(callback, "this\$0")
        }
        if (switchOwnerFragment != null) {
            activeHistoryFragmentRef = WeakReference(switchOwnerFragment)
        }
        val fragment = switchOwnerFragment ?: activeHistoryFragmentRef?.get()

        if (context.switchCoordinator.pendingCloudTarget == null && fragment != null) {
            updateCloudStateFromViewModel(
                ReflectOps.invokeZeroArg(fragment, "cg"),
                fromSwitchCompletion = false,
            )
        }
        if (!context.isCloudHistoryClosed && context.prefs.isEnabled()) {
            context.prefs.setEnabled(false)
        }

        val modifiedItems = ArrayList<Any>(items.size + 1)
        for (item in items) {
            if (item == null) continue
            val callbackName = ReflectOps.getField(item, "d")?.javaClass?.name.orEmpty()
            when {
                callbackName.startsWith("${HistoryUi.PREFIX_DIALOG_SWITCH}1") -> {
                    cloudSwitchItemRef = WeakReference(item)
                    ReflectOps.setField(item, "a", BiliUiStrings.TITLE_CLOUD_HISTORY)
                    ReflectOps.setField(item, "b", BiliUiStrings.SUBTITLE_CLOUD_HISTORY)
                    ReflectOps.setField(item, "c", !context.isCloudHistoryClosed)
                }
                callbackName.startsWith("${HistoryUi.PREFIX_DIALOG_SWITCH}2") -> {
                    ReflectOps.setField(item, "a", BiliUiStrings.TITLE_LOCAL_DEVICE_CLOUD_FILTER)
                    ReflectOps.setField(item, "b", BiliUiStrings.SUBTITLE_LOCAL_DEVICE_CLOUD_FILTER)
                }
            }
            modifiedItems.add(item)
        }

        createLocalHistorySwitchItem(fragment)?.let { localItem ->
            localSwitchItemRef = WeakReference(localItem)
            modifiedItems.add(localItem)
        }
        ReflectOps.setField(modelG, "b", modifiedItems)
    }

    private fun onLocalHistorySwitchToggled(fragment: Any?, enableLocal: Boolean) {
        context.prefs.setEnabled(enableLocal)
        val effect = context.switchCoordinator.onLocalSwitchToggled(enableLocal)
        val ownerFragment = fragment ?: activeHistoryFragmentRef?.get()
        if (effect.mustCloseCloud) {
            syncSwitchItemChecked(cloudSwitchItemRef?.get(), false)
            dismissActiveSettingsDialog()
            val viewModel = ReflectOps.invokeZeroArg(ownerFragment, "cg")
            if (viewModel != null) {
                ReflectOps.invokeOneArg(viewModel, "g1", Boolean::class.javaPrimitiveType!!, false)
            } else {
                refreshHistoryFragmentList(ownerFragment, reloadTabs = true)
            }
        } else if (effect.refreshImmediate) {
            refreshHistoryFragmentList(ownerFragment, reloadTabs = false)
        }
    }

    private fun hideHistoryBanner(fragment: Any?) {
        if (fragment == null) return
        val binding = ReflectOps.getField(fragment, "M") ?: return
        val bannerContainer = ReflectOps.getField(binding, "d") as? ViewGroup ?: return
        bannerContainer.visibility = View.GONE
        bannerContainer.post { bannerContainer.visibility = View.GONE }
    }

    private fun syncSwitchItemChecked(item: Any?, checked: Boolean) {
        if (item == null) return
        ReflectOps.setField(item, "c", checked)
        val mutableState = switchStateByModel[item]?.get() ?: return
        val apply = {
            ReflectOps.invokeOneArg(mutableState, "setValue", Any::class.java, java.lang.Boolean.valueOf(checked))
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            apply()
        } else {
            mainHandler.post { apply() }
        }
    }

    private fun createLocalHistorySwitchItem(fragment: Any?): Any? {
        val clsF = runtime.loadClassOrNull(HistoryUi.DRAG_MODEL_F) ?: return null
        val clsFn2 = runtime.loadClassOrNull(KOTLIN_FUNCTION2) ?: return null
        val clsUnit = runtime.loadClassOrNull(KOTLIN_UNIT) ?: return null
        val unitInstance = runCatching { clsUnit.getField("INSTANCE").get(null) }.getOrNull() ?: Unit
        val ctor = runCatching {
            clsF.getDeclaredConstructor(
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType!!,
                clsFn2,
            ).apply { isAccessible = true }
        }.getOrNull() ?: return null

        val fragmentRef = WeakReference(fragment)
        val callback = Proxy.newProxyInstance(runtime.classLoader, arrayOf(clsFn2)) { proxy, method, args ->
            when (method.name) {
                "invoke" -> {
                    val enableLocal = args?.getOrNull(0) as? Boolean ?: false
                    onLocalHistorySwitchToggled(
                        fragmentRef.get() ?: activeHistoryFragmentRef?.get(),
                        enableLocal,
                    )
                    unitInstance
                }
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "LocalHistorySwitchCallback"
                else -> null
            }
        }
        val initialChecked = context.isCloudHistoryClosed && context.prefs.isEnabled()
        return runCatching {
            ctor.newInstance(
                BiliUiStrings.TITLE_LOCAL_HISTORY,
                BiliUiStrings.SUBTITLE_LOCAL_HISTORY,
                initialChecked,
                callback,
            )
        }.onFailure { runtime.logWarn("Failed to instantiate switch item", it) }.getOrNull()
    }

    private fun dismissActiveSettingsDialog() {
        val dialog = activeSettingsDialogRef?.get() ?: return
        mainHandler.postDelayed({
            runCatching {
                when (dialog.javaClass.name) {
                    HistoryUi.HISTORY_MORE_DIALOG -> {
                        val dismissFlow = ReflectOps.getField(dialog, "h")
                        ReflectOps.invokeOneArg(dismissFlow, "setValue", Any::class.java, java.lang.Boolean.TRUE)
                    }
                    HistoryUi.THREE_POINT_DIALOG -> {
                        val tag = ReflectOps.invokeZeroArg(dialog, "getTag") as? String
                        if (tag == HistoryUi.THREE_POINT_DIALOG_HISTORY_TAG) {
                            ReflectOps.invokeZeroArg(dialog, "dismissAllowingStateLoss")
                        }
                    }
                }
            }.onFailure { runtime.logWarn("dismissActiveSettingsDialog failed", it) }
        }, DIALOG_DISMISS_DELAY_MS)
    }

    private fun refreshHistoryFragmentList(fragment: Any?, reloadTabs: Boolean) {
        val fragmentRef = WeakReference(fragment ?: activeHistoryFragmentRef?.get())
        mainHandler.post {
            val targetFragment = fragmentRef.get() ?: activeHistoryFragmentRef?.get()
            runCatching {
                var shouldScheduleFollowUp = reloadTabs
                if (targetFragment != null) {
                    hideHistoryBanner(targetFragment)
                    val pagerAdapter = ReflectOps.invokeZeroArg(targetFragment, "ag")
                    val tabCount = (ReflectOps.invokeZeroArg(pagerAdapter, "getCount") as? Number)?.toInt() ?: 0
                    val shouldReloadTabs = reloadTabs || tabCount == 0
                    shouldScheduleFollowUp = shouldReloadTabs
                    if (tabCount > 0) {
                        (ReflectOps.getField(targetFragment, "R") as? View)?.visibility = View.GONE
                    }
                    if (shouldReloadTabs) {
                        val viewModel = ReflectOps.invokeZeroArg(targetFragment, "cg")
                        val reloadTabMethod = viewModel?.javaClass?.declaredMethods
                            ?.firstOrNull { it.name == "f1" && it.parameterTypes.size == 3 }
                            ?.apply { isAccessible = true }
                        reloadTabMethod?.invoke(viewModel, null, historySourceForFragment(targetFragment), null)
                    }
                }

                val refreshed = refreshContentFragments(targetFragment)
                if (shouldScheduleFollowUp || refreshed == 0) {
                    // Tab reload calls notifyDataSetChanged() with POSITION_NONE asynchronously,
                    // so schedule a follow-up refresh once newly instantiated pages attach.
                    mainHandler.postDelayed({
                        runCatching {
                            val latestTarget = fragmentRef.get() ?: activeHistoryFragmentRef?.get()
                            hideHistoryBanner(latestTarget)
                            refreshContentFragments(latestTarget)
                        }.onFailure { runtime.logWarn("refreshHistoryFragmentList delayed retry failed", it) }
                    }, TAB_RELOAD_RETRY_DELAY_MS)
                }
            }.onFailure { runtime.logWarn("refreshHistoryFragmentList failed", it) }
        }
    }

    private fun refreshContentFragments(targetFragment: Any?): Int {
        val candidates = ArrayList<Any>(4)
        fun addCandidate(candidate: Any?) {
            if (candidate == null || candidate.javaClass.name != HistoryUi.HISTORY_CONTENT_FRAGMENT) return
            if (candidates.none { it === candidate }) {
                candidates.add(candidate)
            }
        }

        if (targetFragment != null) {
            addCandidate(ReflectOps.invokeZeroArg(targetFragment, "Zf"))
            val pagerAdapter = ReflectOps.invokeZeroArg(targetFragment, "ag")
            val lazySparse = ReflectOps.getField(pagerAdapter, "a")
            val sparse = ReflectOps.unwrapKotlinLazy(
                lazySparse,
                runtime.loadClassOrNull(KOTLIN_LAZY),
            ) as? SparseArray<*>
            if (sparse != null) {
                for (i in 0 until sparse.size()) {
                    addCandidate(sparse.valueAt(i))
                }
            }
            val childFm = ReflectOps.invokeZeroArg(targetFragment, "getChildFragmentManager")
            val childFragments = ReflectOps.invokeZeroArg(childFm, "getFragments") as? List<*>
            childFragments?.forEach(::addCandidate)
        }

        synchronized(activeContentFragments) {
            activeContentFragments.forEach(::addCandidate)
        }

        var refreshed = 0
        for (contentFragment in candidates) {
            val isAdded = ReflectOps.invokeZeroArg(contentFragment, "isAdded") as? Boolean ?: false
            val hasView = ReflectOps.invokeZeroArg(contentFragment, "getView") != null
            if (isAdded || hasView) {
                ReflectOps.invokeZeroArg(contentFragment, "Wf")
                refreshed++
            }
        }
        return refreshed
    }

    private fun historySourceForFragment(fragment: Any?): Any? {
        val sourceNum = ReflectOps.getInt(fragment, "X", 0)
        val sourceCls = runtime.loadClassOrNull(HistoryMoss.HISTORY_SOURCE) ?: return null
        return ReflectOps.invokeStaticOneArg(sourceCls, "forNumber", Int::class.javaPrimitiveType!!, sourceNum)
    }

    companion object {
        private const val DIALOG_DISMISS_DELAY_MS = 120L
        private const val TAB_RELOAD_RETRY_DELAY_MS = 250L
    }
}
