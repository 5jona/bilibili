package pochita.history.hook

import pochita.bilibili.contract.BiliContracts.CloudIsolation
import pochita.bilibili.contract.BiliContracts.HistoryUi
import pochita.bilibili.ui.HistorySettingsUiController
import pochita.history.HistoryModuleContext
import pochita.xposed.ReflectOps

/**
 * Intercepts Bilibili's cloud history upload pipelines (`PlayerHistoryCloudSyncHelper`, `theseus2`,
 * legacy `theseus`, and `BiliCall`) when official cloud history is closed, while tracking the
 * cloud switch state exposed by `HistorySearchTabViewModel`.
 */
internal class CloudIsolationHooks(
    private val context: HistoryModuleContext,
    private val uiController: HistorySettingsUiController,
) {
    private val runtime = context.runtime

    fun install() {
        installInlineSyncHooks()
        installPlayerServiceHooks()
        installCloudSwitchStateHooks()
    }

    private fun installInlineSyncHooks() {
        runtime.hookAllMethodsByName(CloudIsolation.CLOUD_SYNC_HELPER, "a") { chain ->
            val res = (chain.proceed() as? Number)?.toInt() ?: 0
            if (res > 0 || !context.shouldUseLocalHistory()) return@hookAllMethodsByName res
            val cid = (chain.getArg(0) as? Number)?.toLong() ?: 0L
            val localProgress = if (cid > 0L) context.store()?.getByCid(cid)?.progress?.toInt() ?: 0 else 0
            if (localProgress > 0) localProgress else res
        }

        runtime.hookAllMethodsByName(CloudIsolation.CLOUD_SYNC_HELPER, "b") { chain ->
            if (context.shouldUseLocalHistory()) {
                runCatching {
                    BiliPlaybackExtractor.fromInlineReportParams(chain.getArg(0))
                        ?.let(context.playbackRecorder::recordPlaybackProgress)
                }.onFailure { runtime.logWarn("PlayerHistoryCloudSyncHelper.b error", it) }
            }
            if (context.isCloudHistoryClosed) null else chain.proceed()
        }

        runtime.hookAllMethodsByName(CloudIsolation.CLOUD_SYNC_HELPER, "c") { chain ->
            if (context.isCloudHistoryClosed) null else chain.proceed()
        }

        runtime.hookAllMethodsByName(CloudIsolation.CLOUD_SYNC_HELPER, "f") { chain ->
            if (context.isCloudHistoryClosed) java.lang.Boolean.FALSE else chain.proceed()
        }

        runtime.hookAllMethodsByName(CloudIsolation.CLOUD_SYNC_HELPER, "g") { chain ->
            val res = chain.proceed()
            if (context.shouldUseLocalHistory()) {
                val posSec = (chain.getArg(0) as? Number)?.toLong() ?: 0L
                val cid = (chain.getArg(1) as? Number)?.toLong() ?: 0L
                if (cid > 0L) {
                    runCatching { context.playbackRecorder.recordInlineCidProgress(cid, posSec) }
                        .onFailure { runtime.logWarn("PlayerHistoryCloudSyncHelper.g error", it) }
                }
            }
            res
        }
    }

    private fun installPlayerServiceHooks() {
        runtime.hookAllMethodsByName(CloudIsolation.THESEUS2_HISTORY_SERVICE, "b") { chain ->
            val res = chain.proceed()
            if (context.shouldUseLocalHistory()) {
                runCatching {
                    val posObj = chain.getArg(0)
                    val service = chain.getArg(1)
                    val cid = (chain.getArg(2) as? Number)?.toLong() ?: 0L
                    val stateObj = chain.getArg(3)
                    if (service != null) {
                        BiliPlaybackExtractor.fromTheseus2Service(service, posObj, cid, 0L, stateObj)
                            ?.let(context.playbackRecorder::recordPlaybackProgress)
                    }
                }.onFailure { runtime.logWarn("TheseusHistoryService.b error", it) }
            }
            res
        }

        runtime.hookAllMethodsByName(CloudIsolation.THESEUS2_HISTORY_SERVICE, "c") { chain ->
            if (context.shouldUseLocalHistory()) {
                runCatching {
                    val service = chain.getArg(0)
                    val posObj = chain.getArg(1)
                    val cid = (chain.getArg(2) as? Number)?.toLong() ?: 0L
                    val aid = (chain.getArg(3) as? Number)?.toLong() ?: 0L
                    val stateObj = chain.getArg(4)
                    if (service != null) {
                        BiliPlaybackExtractor.fromTheseus2Service(service, posObj, cid, aid, stateObj)
                            ?.let(context.playbackRecorder::recordPlaybackProgress)
                    }
                }.onFailure { runtime.logWarn("TheseusHistoryService.c error", it) }
            }
            if (context.isCloudHistoryClosed) java.lang.Boolean.FALSE else chain.proceed()
        }

        runtime.hookAllMethodsByName(CloudIsolation.LEGACY_THESEUS_HISTORY_SERVICE, "a") { chain ->
            val res = chain.proceed()
            if (context.shouldUseLocalHistory()) {
                runCatching {
                    BiliPlaybackExtractor.fromLegacyTheseusService(chain.thisObject)
                        ?.let(context.playbackRecorder::recordPlaybackProgress)
                }.onFailure { runtime.logWarn("Legacy theseus history.b.a error", it) }
            }
            res
        }

        runtime.hookAllMethodsByName(CloudIsolation.BILI_CALL, "enqueue") { chain ->
            val callback = chain.args.firstOrNull()
            if (context.isCloudHistoryClosed &&
                callback?.javaClass?.name == CloudIsolation.LEGACY_THESEUS_HISTORY_CALLBACK
            ) {
                null
            } else {
                chain.proceed()
            }
        }
    }

    private fun installCloudSwitchStateHooks() {
        runtime.hookMethod(HistoryUi.HISTORY_VIEW_MODEL, "e1") { chain ->
            val res = chain.proceed()
            if (context.switchCoordinator.pendingCloudTarget == null) {
                uiController.updateCloudStateFromViewModel(chain.thisObject, fromSwitchCompletion = false)
            }
            res
        }

        runtime.hookMethod(HistoryUi.HISTORY_VIEW_MODEL, "g1", Boolean::class.javaPrimitiveType!!) { chain ->
            val turnOn = chain.getArg(0) as? Boolean ?: false
            uiController.onCloudHistorySwitchToggled(turnOn)
            chain.proceed()
        }

        val initSuspendCls = "${HistoryUi.HISTORY_VIEW_MODEL}\$initSwitchState\$1"
        if (runtime.loadClassOrNull(initSuspendCls) != null) {
            runtime.hookMethod(initSuspendCls, "invokeSuspend", Any::class.java) { chain ->
                val res = chain.proceed()
                if (context.switchCoordinator.pendingCloudTarget == null && !runtime.isCoroutineSuspended(res)) {
                    uiController.updateCloudStateFromViewModel(
                        ReflectOps.getField(chain.thisObject, "this\$0"),
                        fromSwitchCompletion = false,
                    )
                }
                res
            }
        }

        val switchSuspendCls = "${HistoryUi.HISTORY_VIEW_MODEL}\$switchHistoryRecord\$1"
        if (runtime.loadClassOrNull(switchSuspendCls) != null) {
            runtime.hookMethod(switchSuspendCls, "invokeSuspend", Any::class.java) { chain ->
                val res = chain.proceed()
                if (!runtime.isCoroutineSuspended(res)) {
                    uiController.updateCloudStateFromViewModel(
                        ReflectOps.getField(chain.thisObject, "this\$0"),
                        fromSwitchCompletion = true,
                    )
                }
                res
            }
        }
    }
}
