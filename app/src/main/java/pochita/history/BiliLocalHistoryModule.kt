package pochita.history

import android.app.Application
import android.content.Context
import pochita.bilibili.proto.HistoryProtoCodec
import pochita.bilibili.ui.HistorySettingsUiController
import pochita.bilibili.ui.PlaybackSpeedUiController
import pochita.history.hook.CloudIsolationHooks
import pochita.history.hook.HistoryMossHooks
import pochita.history.hook.HistorySettingsUiHooks
import pochita.history.hook.PlaybackCaptureHooks
import pochita.history.hook.ResumePlaybackHooks
import pochita.speed.hook.PlaybackSpeedHooks
import pochita.xposed.HookRuntime

/**
 * Top-level module coordinator installing all Bilibili feature hook groups inside
 * `tv.danmaku.bili`.
 */
internal class BiliLocalHistoryModule(
    private val runtime: HookRuntime,
) {
    private val context = HistoryModuleContext(runtime)
    private val protoCodec = HistoryProtoCodec(context)
    private val uiController = HistorySettingsUiController(context)
    private val speedUiController = PlaybackSpeedUiController(
        runtime = runtime,
        speedPrefs = context.speedPrefs,
        appContextProvider = context::ensureContext,
    )

    /**
     * Installs application bootstrap, cloud isolation, metadata capture, resume override,
     * `HistoryMoss` bridge, settings UI hooks, and unlocked playback speed controls.
     */
    fun install() {
        installApplicationBootstrapHook()
        CloudIsolationHooks(context, uiController).install()
        PlaybackCaptureHooks(context).install()
        ResumePlaybackHooks(context, protoCodec).install()
        HistoryMossHooks(context, protoCodec).install()
        HistorySettingsUiHooks(runtime, uiController).install()
        PlaybackSpeedHooks(runtime, context.speedPrefs, speedUiController).install()
    }

    private fun installApplicationBootstrapHook() {
        runtime.hookMethod(Application::class.java.name, "onCreate") { chain ->
            val result = chain.proceed()
            val ctx = chain.thisObject as? Context
            if (ctx != null) {
                context.attachApplication(ctx)
            }
            result
        }
    }
}
