package pochita.bilibili.ui

import android.content.Context
import android.view.View
import pochita.bilibili.contract.BiliContracts
import pochita.bilibili.contract.BiliContracts.PlaybackSpeed
import pochita.xposed.HookRuntime
import pochita.xposed.ReflectOps
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap

/**
 * Reflection bridge hosting native Android [PlaybackSpeedPanelView] instances inside host Jetpack
 * Compose `@Composable` speed panels via `AndroidView_androidKt.AndroidView`.
 */
internal class ComposeSpeedPanelBridge(
    private val runtime: HookRuntime,
) {
    /**
     * Caches stable factory/update proxies and the live [PlaybackSpeedPanelView] per host panel
     * instance so Compose recompositions update state in place without recreating Android views.
     */
    class SlotBinding(
        var factoryFn1: Any? = null,
        var updateFn1: Any? = null,
        var panelView: PlaybackSpeedPanelView? = null,
        var drawerContainer: PlaybackSpeedPanelView.FullscreenDrawerContainer? = null,
        var latestNormalSpeed: Float = 1.0f,
        var latestLongPressSpeed: Float = 2.0f,
        var latestDarkTheme: Boolean = false,
        var latestLandscape: Boolean = false,
        var onNormalSelect: (Float) -> Unit = {},
        var onLongPressSelect: (Float) -> Unit = {},
    ) {
        fun syncView() {
            drawerContainer?.updateOrientation(latestLandscape)
            val view = panelView ?: return
            view.onSelectNormalSpeed = onNormalSelect
            view.onSelectLongPressSpeed = onLongPressSelect
            view.bindState(latestNormalSpeed, latestLongPressSpeed, latestDarkTheme)
        }
    }

    private val bindings: MutableMap<Any, SlotBinding> =
        Collections.synchronizedMap(WeakHashMap())

    private val unitInstance: Any? by lazy {
        runtime.loadClassOrNull(BiliContracts.KOTLIN_UNIT)?.let { ReflectOps.getStaticField(it, "INSTANCE") }
    }

    private val fn1Class: Class<*>? by lazy { runtime.loadClassOrNull(BiliContracts.KOTLIN_FUNCTION1) }
    private val fn2Class: Class<*>? by lazy { runtime.loadClassOrNull(BiliContracts.KOTLIN_FUNCTION2) }
    private val composerClass: Class<*>? by lazy { runtime.loadClassOrNull(BiliContracts.COMPOSE_COMPOSER) }
    private val modifierClass: Class<*>? by lazy { runtime.loadClassOrNull(BiliContracts.COMPOSE_MODIFIER) }

    private val androidViewMethod: Method? by lazy {
        val cls = runtime.loadClassOrNull(BiliContracts.COMPOSE_ANDROID_VIEW) ?: return@lazy null
        val fn1 = fn1Class ?: return@lazy null
        val mod = modifierClass ?: return@lazy null
        val cmp = composerClass ?: return@lazy null
        ReflectOps.findMethod(
            cls,
            "AndroidView",
            fn1,
            mod,
            fn1,
            cmp,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
        )
    }

    private val fillMaxWidthMethod: Method? by lazy {
        val cls = runtime.loadClassOrNull(BiliContracts.COMPOSE_SIZE_KT) ?: return@lazy null
        val mod = modifierClass ?: return@lazy null
        ReflectOps.findMethod(cls, "fillMaxWidth", mod, Float::class.javaPrimitiveType!!)
    }

    private val modifierCompanion: Any? by lazy {
        modifierClass?.let { ReflectOps.getStaticField(it, "Companion") }
    }

    private val startRestartGroupMethod: Method? by lazy {
        composerClass?.let { ReflectOps.findMethod(it, "startRestartGroup", Int::class.javaPrimitiveType!!) }
    }

    private val endRestartGroupMethod: Method? by lazy {
        composerClass?.let { ReflectOps.findMethod(it, "endRestartGroup") }
    }

    private val updateScopeMethod: Method? by lazy {
        val scopeCls = runtime.loadClassOrNull(BiliContracts.COMPOSE_SCOPE_UPDATE) ?: return@lazy null
        val fn2 = fn2Class ?: return@lazy null
        ReflectOps.findMethod(scopeCls, "updateScope", fn2)
    }

    private val updateChangedFlagsMethod: Method? by lazy {
        val cls = runtime.loadClassOrNull(BiliContracts.COMPOSE_RECOMPOSE_SCOPE_IMPL_KT) ?: return@lazy null
        ReflectOps.findMethod(cls, "updateChangedFlags", Int::class.javaPrimitiveType!!)
    }

    private val biliThemeInstance: Any? by lazy {
        runtime.loadClassOrNull(PlaybackSpeed.THESEUS2_THEME_HELPER)?.let {
            ReflectOps.getStaticField(it, "INSTANCE")
        }
    }

    private val colorToArgbMethod: Method? by lazy {
        runtime.loadClassOrNull(PlaybackSpeed.COMPOSE_COLOR_KT)?.let {
            ReflectOps.findMethod(it, "toArgb-8_81llA", Long::class.javaPrimitiveType!!)
        }
    }

    fun isReady(): Boolean =
        androidViewMethod != null &&
            startRestartGroupMethod != null &&
            endRestartGroupMethod != null &&
            updateScopeMethod != null &&
            fn1Class != null &&
            fn2Class != null

    /**
     * Resolves `BiliTheme.getColors(composer, 6).getBg1_float-0d7_KjU()` luminance inside Compose
     * so light/dark sheets match in-app Bilibili theme switches as well as system night mode.
     */
    fun resolveSheetDarkTheme(composer: Any, fallbackDark: Boolean): Boolean {
        return runCatching {
            val theme = biliThemeInstance ?: return fallbackDark
            val cmpCls = composerClass ?: return fallbackDark
            val getColors = ReflectOps.findMethod(theme.javaClass, "getColors", cmpCls, Int::class.javaPrimitiveType!!)
                ?: return fallbackDark
            val colors = getColors.invoke(theme, composer, 6) ?: return fallbackDark
            val bgColorLong = ReflectOps.invokeZeroArg(colors, "getBg1_float-0d7_KjU") as? Long
                ?: return fallbackDark
            val argb = (colorToArgbMethod?.invoke(null, bgColorLong) as? Int) ?: return fallbackDark
            calculateRelativeLuminance(argb) < 0.5
        }.getOrDefault(fallbackDark)
    }

    fun fullWidthModifier(): Any? {
        val companion = modifierCompanion ?: return null
        return runCatching { fillMaxWidthMethod?.invoke(null, companion, 1.0f) }.getOrNull() ?: companion
    }

    /**
     * Emits a balanced Compose restart group containing a cached `AndroidView` node bound to [key].
     */
    fun composeAndroidView(
        key: Any,
        groupKey: Int,
        composer: Any,
        changed: Int,
        modifier: Any?,
        normalSpeed: Float,
        longPressSpeed: Float,
        darkTheme: Boolean,
        isLandscape: Boolean = false,
        viewFactory: (Context, SlotBinding) -> View,
        onNormalSelect: (Float) -> Unit,
        onLongPressSelect: (Float) -> Unit,
        onRecompose: (nextComposer: Any, nextChanged: Int) -> Unit,
    ) {
        val startMethod = startRestartGroupMethod ?: return
        val endMethod = endRestartGroupMethod ?: return
        val androidView = androidViewMethod ?: return
        val scopeUpdate = updateScopeMethod ?: return
        val fn1 = fn1Class ?: return
        val fn2 = fn2Class ?: return

        val binding = synchronized(bindings) {
            bindings.getOrPut(key) { SlotBinding() }
        }
        binding.latestNormalSpeed = normalSpeed
        binding.latestLongPressSpeed = longPressSpeed
        binding.latestDarkTheme = darkTheme
        binding.latestLandscape = isLandscape
        binding.onNormalSelect = onNormalSelect
        binding.onLongPressSelect = onLongPressSelect

        ensureProxies(binding, fn1, viewFactory)

        val restartedComposer = startMethod.invoke(composer, groupKey) ?: composer
        val resolvedModifier = modifier ?: fullWidthModifier() ?: modifierCompanion
        androidView.invoke(
            null,
            binding.factoryFn1,
            resolvedModifier,
            binding.updateFn1,
            restartedComposer,
            0,
            0,
        )

        val scope = endMethod.invoke(restartedComposer)
        if (scope != null) {
            val recomposeLambda = Proxy.newProxyInstance(runtime.classLoader, arrayOf(fn2)) { proxy, method, args ->
                if (method.name == "invoke") {
                    val nextComposer = args?.getOrNull(0)
                    val forceChanged = (args?.getOrNull(1) as? Int) ?: changed
                    val updatedChanged = runCatching {
                        updateChangedFlagsMethod?.invoke(null, forceChanged or 1) as? Int
                    }.getOrNull() ?: (forceChanged or 1)
                    if (nextComposer != null) {
                        onRecompose(nextComposer, updatedChanged)
                    }
                    unitInstance
                } else {
                    handleObjectMethod(proxy, method, args)
                }
            }
            scopeUpdate.invoke(scope, recomposeLambda)
        }
    }

    /**
     * Returns the cached [PlaybackSpeedPanelView] currently hosted under [key], if any.
     */
    fun findPanelView(key: Any): PlaybackSpeedPanelView? =
        synchronized(bindings) { bindings[key]?.panelView }

    private fun ensureProxies(
        binding: SlotBinding,
        fn1: Class<*>,
        viewFactory: (Context, SlotBinding) -> View,
    ) {
        if (binding.factoryFn1 == null) {
            binding.factoryFn1 = Proxy.newProxyInstance(runtime.classLoader, arrayOf(fn1)) { proxy, method, args ->
                if (method.name == "invoke") {
                    val ctx = args?.firstOrNull() as? Context
                        ?: error("Compose AndroidView factory invoked without Context")
                    val created = viewFactory(ctx, binding)
                    binding.syncView()
                    created
                } else {
                    handleObjectMethod(proxy, method, args)
                }
            }
        }
        if (binding.updateFn1 == null) {
            binding.updateFn1 = Proxy.newProxyInstance(runtime.classLoader, arrayOf(fn1)) { proxy, method, args ->
                if (method.name == "invoke") {
                    val root = args?.firstOrNull() as? View
                    if (root != null) {
                        binding.syncView()
                        root.requestLayout()
                    }
                    unitInstance
                } else {
                    handleObjectMethod(proxy, method, args)
                }
            }
        }
    }

    private fun handleObjectMethod(proxy: Any?, method: Method, args: Array<Any?>?): Any? =
        when (method.name) {
            "toString" -> "ComposeSpeedPanelProxy@${System.identityHashCode(proxy)}"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> unitInstance
        }

    private fun calculateRelativeLuminance(argb: Int): Double {
        val r = ((argb ushr 16) and 0xFF) / 255.0
        val g = ((argb ushr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}
