package pochita.bilibili.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import pochita.bilibili.contract.BiliContracts
import pochita.bilibili.contract.BiliContracts.PlaybackSpeed
import pochita.bilibili.contract.BiliUiStrings.PlaybackSpeed as Strings
import pochita.config.PlaybackSpeedPrefs
import pochita.speed.SpeedSpec
import pochita.xposed.HookRuntime
import pochita.xposed.ReflectOps
import java.util.Collections
import java.util.WeakHashMap

/**
 * Coordinates injected playback speed view/Compose UI panels and dispatches player speed changes
 * alongside persistent long-press speed configuration updates.
 */
internal class PlaybackSpeedUiController(
    private val runtime: HookRuntime,
    private val speedPrefs: PlaybackSpeedPrefs,
    private val appContextProvider: () -> Context?,
) {
    private data class TheseusDrawerBinding(
        val panelView: PlaybackSpeedPanelView,
        val drawerContainer: PlaybackSpeedPanelView.FullscreenDrawerContainer?,
    )

    private val composeBridge = ComposeSpeedPanelBridge(runtime)
    private val theseusDrawerPanels: MutableMap<Any, TheseusDrawerBinding> =
        Collections.synchronizedMap(WeakHashMap())

    fun isComposeBridgeReady(): Boolean = composeBridge.isReady()

    /**
     * Replaces or wraps the `theseus` fullscreen drawer content produced by
     * `TheseusSpeedFunctionWidget.createContentView` while preserving native right-drawer geometry.
     */
    fun createTheseusDrawerContent(widget: Any, originalView: View): View {
        val panelContext = (ReflectOps.invokeZeroArg(widget, "getMContext") as? Context)
            ?: originalView.context
        val panelView = createTheseusDrawerPanelView(widget, panelContext)

        return if (originalView is ViewGroup) {
            originalView.removeAllViews()
            originalView.addView(
                PlaybackSpeedPanelView.createVerticallyCenteredScroll(panelContext, panelView),
                PlaybackSpeedPanelView.drawerContentWidthPx(panelContext),
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            theseusDrawerPanels[widget] = TheseusDrawerBinding(panelView, drawerContainer = null)
            originalView
        } else {
            val container = PlaybackSpeedPanelView.wrapForFullscreenDrawer(
                panelContext,
                isTheseusLandscapeFullscreen(widget),
                panelView,
            )
            theseusDrawerPanels[widget] = TheseusDrawerBinding(panelView, container)
            container
        }
    }

    /**
     * Synchronizes a cached `theseus` drawer view with live playback and long-press speeds.
     *
     * @return `true` when an injected panel handled the refresh, or `false` to fall back to host logic.
     */
    fun refreshTheseusDrawer(widget: Any): Boolean {
        val binding = theseusDrawerPanels[widget] ?: return false
        binding.drawerContainer?.updateOrientation(isTheseusLandscapeFullscreen(widget))
        val currentSpeed = readTheseusCurrentSpeed(ReflectOps.getField(widget, "a"))
        binding.panelView.bindState(
            normalSpeed = currentSpeed,
            longPressSpeed = speedPrefs.getLongPressSpeed(),
            darkTheme = true,
        )
        return true
    }

    /**
     * Renders the `theseus` portrait bottom-sheet speed panel inside Compose via [ComposeSpeedPanelBridge].
     */
    fun composeTheseusDialogPanel(
        panel: Any,
        selectedSpeedArg: Float,
        themeArg: Int,
        composer: Any,
        changed: Int,
    ) {
        val hostContext = (ReflectOps.getField(panel, "b") as? Context) ?: appContextProvider()
        val systemDark = hostContext?.let(PlaybackSpeedPanelView::isSystemNightMode) ?: false
        val darkTheme = themeArg == 2 || composeBridge.resolveSheetDarkTheme(composer, systemDark)
        val liveSpeed = readTheseusCurrentSpeed(ReflectOps.getField(panel, "c"), selectedSpeedArg)
        val longPressSpeed = speedPrefs.getLongPressSpeed()

        composeBridge.composeAndroidView(
            key = panel,
            groupKey = -0x15de3cb2,
            composer = composer,
            changed = changed,
            modifier = null,
            normalSpeed = liveSpeed,
            longPressSpeed = longPressSpeed,
            darkTheme = darkTheme,
            viewFactory = ::createSheetPanelView,
            onNormalSelect = { speed ->
                applyTheseusNormalSpeed(
                    keelPlayer = ReflectOps.getField(panel, "c"),
                    speed = speed,
                    dismissAction = {
                        val dismissFlow = ReflectOps.getField(panel, "j")
                        ReflectOps.invokeOneArg(dismissFlow, "setValue", Any::class.java, java.lang.Boolean.TRUE)
                    },
                    toastAction = { msg ->
                        ReflectOps.invokeOneArg(panel, "c", String::class.java, msg)
                        resolvePanelToastContext(panel, hostContext)?.let { showShortToast(it, msg) }
                    },
                )
            },
            onLongPressSelect = { speed ->
                handleLongPressSelection(speed, resolvePanelToastContext(panel, hostContext))
            },
            onRecompose = { nextComposer, nextChanged ->
                composeTheseusDialogPanel(panel, liveSpeed, themeArg, nextComposer, nextChanged)
            },
        )
    }

    /**
     * Renders the `theseus2` portrait bottom-sheet speed panel inside Compose via [ComposeSpeedPanelBridge].
     */
    fun composeTheseus2DialogPanel(
        panel: Any,
        modifier: Any?,
        composer: Any,
        changed: Int,
    ) {
        val hostContext = appContextProvider()
        val systemDark = hostContext?.let(PlaybackSpeedPanelView::isSystemNightMode) ?: false
        val darkTheme = composeBridge.resolveSheetDarkTheme(composer, systemDark)
        val vodCommonO = ReflectOps.getField(panel, "b")
        val liveSpeed = readTheseus2CurrentSpeed(vodCommonO)
        val longPressSpeed = speedPrefs.getLongPressSpeed()

        composeBridge.composeAndroidView(
            key = panel,
            groupKey = 0x23092be1,
            composer = composer,
            changed = changed,
            modifier = modifier,
            normalSpeed = liveSpeed,
            longPressSpeed = longPressSpeed,
            darkTheme = darkTheme,
            viewFactory = ::createSheetPanelView,
            onNormalSelect = { speed ->
                applyTheseus2NormalSpeed(
                    settingD = ReflectOps.getField(panel, "e"),
                    vodCommonO = ReflectOps.getField(panel, "b"),
                    speed = speed,
                    dismissAction = {
                        val dismissFn = ReflectOps.getField(panel, "h")
                        ReflectOps.invokeZeroArg(dismissFn, "invoke")
                    },
                    toastContext = resolvePanelToastContext(panel, hostContext),
                )
            },
            onLongPressSelect = { speed ->
                handleLongPressSelection(speed, resolvePanelToastContext(panel, hostContext))
            },
            onRecompose = { nextComposer, nextChanged ->
                composeTheseus2DialogPanel(panel, modifier, nextComposer, nextChanged)
            },
        )
    }

    /**
     * Renders the `theseus2` fullscreen drawer speed panel inside Compose via [ComposeSpeedPanelBridge].
     */
    fun composeTheseus2DrawerPanel(
        panelState: Any,
        isLandscape: Boolean,
        onItemClick: Any,
        modifier: Any?,
        composer: Any,
        changed: Int,
    ) {
        val widgetState = ReflectOps.getField(onItemClick, "a") ?: onItemClick
        val panelScope = ReflectOps.getField(onItemClick, "b")
        val selectedSpeed = (ReflectOps.getField(panelState, "b") as? Number)?.toFloat()
            ?: SpeedSpec.DEFAULT_NORMAL_SPEED
        val longPressSpeed = speedPrefs.getLongPressSpeed()

        composeBridge.composeAndroidView(
            key = widgetState,
            groupKey = 0x639aa64d,
            composer = composer,
            changed = changed,
            modifier = modifier,
            normalSpeed = selectedSpeed,
            longPressSpeed = longPressSpeed,
            darkTheme = true,
            isLandscape = isLandscape,
            viewFactory = { ctx, binding ->
                val panelView = PlaybackSpeedPanelView(
                    context = ctx,
                    mode = PlaybackSpeedPanelView.DisplayMode.FULLSCREEN_DRAWER,
                    darkTheme = true,
                )
                binding.panelView = panelView
                PlaybackSpeedPanelView.wrapForFullscreenDrawer(
                    ctx,
                    binding.latestLandscape,
                    panelView,
                ).also { binding.drawerContainer = it }
            },
            onNormalSelect = { speed ->
                applyTheseus2NormalSpeed(
                    settingD = ReflectOps.getField(widgetState, "e"),
                    vodCommonO = ReflectOps.getField(widgetState, "b"),
                    speed = speed,
                    dismissAction = { cancelCoroutineScope(panelScope) },
                    toastContext = resolvePanelToastContext(widgetState, appContextProvider()),
                )
            },
            onLongPressSelect = { speed ->
                handleLongPressSelection(speed, resolvePanelToastContext(widgetState, appContextProvider()))
            },
            onRecompose = { nextComposer, nextChanged ->
                composeTheseus2DrawerPanel(
                    panelState = panelState,
                    isLandscape = isLandscape,
                    onItemClick = onItemClick,
                    modifier = modifier,
                    composer = nextComposer,
                    changed = nextChanged,
                )
            },
        )
    }

    private fun createTheseusDrawerPanelView(
        widget: Any,
        panelContext: Context,
    ): PlaybackSpeedPanelView {
        val keelPlayer = ReflectOps.getField(widget, "a")
        val initialNormal = readTheseusCurrentSpeed(keelPlayer)
        return PlaybackSpeedPanelView(
            context = panelContext,
            mode = PlaybackSpeedPanelView.DisplayMode.FULLSCREEN_DRAWER,
            darkTheme = true,
        ).apply {
            bindState(initialNormal, speedPrefs.getLongPressSpeed(), darkTheme = true)
            onSelectNormalSpeed = { speed ->
                applyTheseusNormalSpeed(
                    keelPlayer = ReflectOps.getField(widget, "a"),
                    speed = speed,
                    dismissAction = { dismissTheseusWidget(widget) },
                    toastAction = { msg -> showTheseusWidgetToast(widget, panelContext, msg) },
                )
            }
            onSelectLongPressSpeed = { speed ->
                handleLongPressSelection(speed, panelContext)
            }
        }
    }

    private fun createSheetPanelView(
        context: Context,
        binding: ComposeSpeedPanelBridge.SlotBinding,
    ): View {
        val panelView = PlaybackSpeedPanelView(
            context = context,
            mode = PlaybackSpeedPanelView.DisplayMode.PORTRAIT_SHEET,
            darkTheme = binding.latestDarkTheme,
        )
        binding.panelView = panelView
        return PlaybackSpeedPanelView.wrapForSheetDialog(context, panelView)
    }

    private fun handleLongPressSelection(speed: Float, toastContext: Context?) {
        val saved = speedPrefs.setLongPressSpeed(speed)
        val message = Strings.formatLongPressUpdatedToast(SpeedSpec.formatSpeedLabel(saved))
        toastContext?.let { showShortToast(it, message) }
    }

    private fun resolvePanelToastContext(key: Any, fallback: Context?): Context? =
        composeBridge.findPanelView(key)?.context ?: fallback

    private fun readTheseusCurrentSpeed(
        keelPlayer: Any?,
        fallback: Float = SpeedSpec.DEFAULT_NORMAL_SPEED,
    ): Float {
        val current = (ReflectOps.invokeZeroArg(keelPlayer, "i") as? Number)?.toFloat()
        return current?.takeIf { it.isFinite() && it > 0f } ?: fallback
    }

    private fun readTheseus2CurrentSpeed(vodCommonO: Any?): Float {
        val speedController = ReflectOps.getField(vodCommonO, "d") ?: return SpeedSpec.DEFAULT_NORMAL_SPEED
        val current = (ReflectOps.invokeZeroArg(speedController, "q") as? Number)?.toFloat()
        return current?.takeIf { it.isFinite() && it > 0f } ?: SpeedSpec.DEFAULT_NORMAL_SPEED
    }

    private fun applyTheseusNormalSpeed(
        keelPlayer: Any?,
        speed: Float,
        dismissAction: () -> Unit,
        toastAction: (String) -> Unit,
    ) {
        val normalized = SpeedSpec.normalizeSpeed(speed)
        if (keelPlayer != null) {
            ReflectOps.invokeOneArg(keelPlayer, "s", Float::class.javaPrimitiveType!!, normalized)
        }
        runCatching { dismissAction() }
            .onFailure { runtime.logWarn("Failed to dismiss theseus speed panel", it) }
        toastAction(Strings.formatNormalSwitchedToast(SpeedSpec.formatSpeedLabel(normalized)))
    }

    private fun applyTheseus2NormalSpeed(
        settingD: Any?,
        vodCommonO: Any?,
        speed: Float,
        dismissAction: () -> Unit,
        toastContext: Context?,
    ) {
        val normalized = SpeedSpec.normalizeSpeed(speed)
        if (settingD != null) {
            ReflectOps.invokeOneArg(settingD, "c", Float::class.javaPrimitiveType!!, normalized)
        }
        if (vodCommonO != null) {
            ReflectOps.invokeOneArg(vodCommonO, "n", Float::class.javaPrimitiveType!!, normalized)
        }
        runCatching { dismissAction() }
            .onFailure { runtime.logWarn("Failed to dismiss theseus2 speed panel", it) }
        toastContext?.let {
            showShortToast(it, Strings.formatNormalSwitchedToast(SpeedSpec.formatSpeedLabel(normalized)))
        }
    }

    private fun dismissTheseusWidget(widget: Any) {
        val widgetService = ReflectOps.getField(widget, "f") ?: return
        val token = ReflectOps.invokeZeroArg(widget, "getToken") ?: return
        val tokenCls = runtime.loadClassOrNull(PlaybackSpeed.PBC2_FUNCTION_WIDGET_TOKEN)
            ?: token.javaClass
        ReflectOps.invokeOneArg(widgetService, "hideWidget", tokenCls, token)
    }

    private fun showTheseusWidgetToast(widget: Any, fallbackContext: Context, message: String) {
        val shown = runCatching {
            val method = ReflectOps.findMethod(widget.javaClass, "h", String::class.java)
            method?.invoke(widget, message)
            method != null
        }.getOrDefault(false)
        if (!shown) {
            showShortToast(fallbackContext, message)
        }
    }

    private fun cancelCoroutineScope(scope: Any?) {
        if (scope == null) return
        val scopeKtCls = runtime.loadClassOrNull(BiliContracts.KOTLINX_COROUTINE_SCOPE_KT) ?: return
        val scopeCls = runtime.loadClassOrNull(BiliContracts.KOTLINX_COROUTINE_SCOPE) ?: return
        val exCls = runtime.loadClassOrNull(BiliContracts.JAVA_UTIL_CONCURRENT_CANCELLATION_EX) ?: return
        val cancelMethod = ReflectOps.findMethod(scopeKtCls, "cancel", scopeCls, exCls)
        cancelMethod?.invoke(null, scope, null)
    }

    private fun isTheseusLandscapeFullscreen(widget: Any): Boolean {
        val controlService = ReflectOps.getField(widget, "g") ?: return true
        val mode = ReflectOps.invokeZeroArg(controlService, "getScreenModeType") ?: return true
        return mode.toString() == PlaybackSpeed.PBC2_SCREEN_MODE_LANDSCAPE_FULLSCREEN
    }

    private fun showShortToast(context: Context, message: String) {
        Toast.makeText(context.applicationContext ?: context, message, Toast.LENGTH_SHORT).show()
    }
}
