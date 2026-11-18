package pochita.speed.hook

import android.content.Context
import android.view.View
import pochita.bilibili.contract.BiliContracts
import pochita.bilibili.contract.BiliContracts.PlaybackSpeed
import pochita.bilibili.ui.PlaybackSpeedUiController
import pochita.config.PlaybackSpeedPrefs
import pochita.speed.SpeedSpec
import pochita.xposed.HookRuntime
import pochita.xposed.ReflectOps
import java.util.ArrayList

/**
 * Installs LSPosed hooks that unlock playback speeds (`0.1X ~ 8.0X`), lift >=50fps caps,
 * override main-player long-press speed resolution, and delegate speed panel rendering to
 * [PlaybackSpeedUiController].
 */
internal class PlaybackSpeedHooks(
    private val runtime: HookRuntime,
    private val speedPrefs: PlaybackSpeedPrefs,
    private val uiController: PlaybackSpeedUiController,
) {
    fun install() {
        installTheseusPlayerHooks()
        installTheseus2PlayerHooks()
    }

    private fun installTheseusPlayerHooks() {
        updateStaticPresetList(PlaybackSpeed.PBC2_SPEED_HELPER, "a")
        updateStaticPresetList(PlaybackSpeed.PBC2_SPEED_ADAPTER, "d")

        // Remove >=50fps restriction across `theseus` speed panels and menu drawers.
        runtime.hookMethod(
            PlaybackSpeed.PBC2_SPEED_HELPER,
            "c",
            Float::class.javaPrimitiveType!!,
        ) { true }

        // Unlock extended descending presets (`4.0X .. 0.25X`) for `theseus` lists & menus.
        runtime.hookMethodIfPresent(
            PlaybackSpeed.PBC2_SPEED_HELPER,
            "a",
            PlaybackSpeed.MEDIA_RESOURCE,
            Integer::class.java.name,
            Boolean::class.javaPrimitiveType!!.name,
            Int::class.javaPrimitiveType!!.name,
        ) {
            buildOptionList(PlaybackSpeed.PBC2_SPEED_OPTION)
        }

        // Override `theseus` main player long-press speed while leaving Story mode intact.
        runtime.hookMethodIfPresent(
            PlaybackSpeed.PBC2_SPEED_HELPER,
            "b",
            PlaybackSpeed.MEDIA_RESOURCE,
        ) { chain ->
            if (isInvokedFromStoryLongPress()) {
                chain.proceed()
            } else {
                speedPrefs.getLongPressSpeed()
            }
        }

        runtime.hookMethod(
            PlaybackSpeed.THESEUS_SPEED_WIDGET,
            "createContentView",
            Context::class.java,
        ) { chain ->
            val widget = chain.thisObject ?: return@hookMethod chain.proceed()
            val originalView = chain.proceed() as? View ?: return@hookMethod null
            uiController.createTheseusDrawerContent(widget, originalView)
        }

        runtime.hookMethod(PlaybackSpeed.THESEUS_SPEED_WIDGET, "g") { chain ->
            val widget = chain.thisObject ?: return@hookMethod chain.proceed()
            if (uiController.refreshTheseusDrawer(widget)) null else chain.proceed()
        }

        if (!uiController.isComposeBridgeReady()) return

        runtime.hookMethodIfPresent(
            PlaybackSpeed.THESEUS_SPEED_DIALOG_PANEL,
            "a",
            Float::class.javaPrimitiveType!!.name,
            Int::class.javaPrimitiveType!!.name,
            Int::class.javaPrimitiveType!!.name,
            BiliContracts.COMPOSE_COMPOSER,
            List::class.java.name,
            BiliContracts.KOTLIN_FUNCTION1,
            BiliContracts.KOTLIN_FUNCTION1,
        ) { chain ->
            val panel = chain.thisObject ?: return@hookMethodIfPresent chain.proceed()
            val selectedSpeed = (chain.args[0] as? Number)?.toFloat() ?: SpeedSpec.DEFAULT_NORMAL_SPEED
            val themeArg = (chain.args[1] as? Number)?.toInt() ?: 0
            val changed = (chain.args[2] as? Number)?.toInt() ?: 0
            val composer = chain.args[3] ?: return@hookMethodIfPresent chain.proceed()
            uiController.composeTheseusDialogPanel(panel, selectedSpeed, themeArg, composer, changed)
            null
        }
    }

    private fun installTheseus2PlayerHooks() {
        updateStaticPresetList(PlaybackSpeed.PBC3_SPEED_HELPER, "a")

        // Remove >=50fps speed cap in `playerbizcommon3`.
        runtime.hookMethod(
            PlaybackSpeed.PBC3_SPEED_HELPER,
            "a",
            Float::class.javaPrimitiveType!!,
        ) { true }

        // Unlock extended presets (`4.0X .. 0.25X`) across `theseus2` panels & drawer menus.
        runtime.hookMethodIfPresent(
            PlaybackSpeed.PBC3_PANEL_HELPER,
            "d",
            PlaybackSpeed.PBC3_SPEED_CONTEXT,
            Int::class.javaPrimitiveType!!.name,
        ) {
            buildOptionList(PlaybackSpeed.PBC3_SPEED_OPTION)
        }

        // Complete long-press speed takeover in `theseus2`, bypassing A/B and scriptPace limits.
        runtime.hookMethodIfPresent(
            PlaybackSpeed.THESEUS2_LONG_PRESS_RESOLVER,
            "a",
            PlaybackSpeed.THESEUS2_KEEL_CONTROLLER,
            Float::class.java.name,
        ) {
            speedPrefs.getLongPressSpeed()
        }

        if (!uiController.isComposeBridgeReady()) return

        runtime.hookMethodIfPresent(
            PlaybackSpeed.THESEUS2_SPEED_DIALOG_PANEL,
            "a",
            BiliContracts.COMPOSE_MODIFIER,
            BiliContracts.COMPOSE_COMPOSER,
            Int::class.javaPrimitiveType!!.name,
        ) { chain ->
            val panel = chain.thisObject ?: return@hookMethodIfPresent chain.proceed()
            val modifier = chain.args[0]
            val composer = chain.args[1] ?: return@hookMethodIfPresent chain.proceed()
            val changed = (chain.args[2] as? Number)?.toInt() ?: 0
            uiController.composeTheseus2DialogPanel(panel, modifier, composer, changed)
            null
        }

        runtime.hookMethodIfPresent(
            PlaybackSpeed.PBC3_PANEL_HELPER,
            "b",
            PlaybackSpeed.PBC3_PANEL_STATE,
            Boolean::class.javaPrimitiveType!!.name,
            BiliContracts.KOTLIN_FUNCTION3,
            BiliContracts.COMPOSE_MODIFIER,
            BiliContracts.COMPOSE_COMPOSER,
            Int::class.javaPrimitiveType!!.name,
        ) { chain ->
            val panelState = chain.args[0] ?: return@hookMethodIfPresent chain.proceed()
            val isLandscape = (chain.args[1] as? Boolean) ?: true
            val onItemClick = chain.args[2] ?: return@hookMethodIfPresent chain.proceed()
            if (onItemClick.javaClass.name != PlaybackSpeed.THESEUS2_SPEED_CLICK_CALLBACK) {
                return@hookMethodIfPresent chain.proceed()
            }
            val modifier = chain.args[3]
            val composer = chain.args[4] ?: return@hookMethodIfPresent chain.proceed()
            val changed = (chain.args[5] as? Number)?.toInt() ?: 0
            uiController.composeTheseus2DrawerPanel(panelState, isLandscape, onItemClick, modifier, composer, changed)
            null
        }
    }

    private fun updateStaticPresetList(className: String, fieldName: String) {
        val cls = runtime.loadClassOrNull(className) ?: return
        ReflectOps.setStaticField(cls, fieldName, SpeedSpec.NORMAL_PRESETS_DESC.toList())
    }

    private fun buildOptionList(optionClassName: String): ArrayList<Any> {
        val result = ArrayList<Any>(SpeedSpec.NORMAL_PRESETS_DESC.size)
        val optionCls = runtime.loadClassOrNull(optionClassName) ?: return result
        val ctor = runCatching {
            optionCls.getDeclaredConstructor(
                Float::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!,
            ).apply { isAccessible = true }
        }.getOrNull() ?: return result
        for (speed in SpeedSpec.NORMAL_PRESETS_DESC) {
            runCatching { result.add(ctor.newInstance(speed, true)) }
        }
        return result
    }

    private fun isInvokedFromStoryLongPress(): Boolean {
        val trace = Throwable().stackTrace
        for (frame in trace) {
            if (frame.className.startsWith(PlaybackSpeed.STORY_LONG_PRESS_SERVICE)) {
                return true
            }
        }
        return false
    }
}
