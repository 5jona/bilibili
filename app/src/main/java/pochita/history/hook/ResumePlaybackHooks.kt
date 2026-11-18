package pochita.history.hook

import pochita.bilibili.contract.BiliContracts.ResumePlayback
import pochita.bilibili.proto.HistoryProtoCodec
import pochita.history.HistoryModuleContext

/**
 * Neutralizes KVDB/memory cloud resume caches and injects local SQLite playback resume positions
 * when official cloud history is turned off.
 */
internal class ResumePlaybackHooks(
    private val context: HistoryModuleContext,
    private val protoCodec: HistoryProtoCodec,
) {
    private val runtime = context.runtime

    fun install() {
        runtime.hookAllMethodsByName(ResumePlayback.UGC_START_POS, "e") { chain ->
            if (context.isCloudHistoryClosed) null else chain.proceed()
        }
        runtime.hookAllMethodsByName(ResumePlayback.UGC_START_POS, "f") { chain ->
            if (context.isCloudHistoryClosed) null else chain.proceed()
        }

        runtime.hookMethod(ResumePlayback.PLAY_VIEW_REPLY, "getHistory") { chain ->
            if (!context.isCloudHistoryClosed) {
                return@hookMethod chain.proceed()
            }
            if (!context.shouldUseLocalHistory()) {
                return@hookMethod null
            }
            runCatching { protoCodec.buildLocalResumeHistory(chain.thisObject) }
                .onFailure { runtime.logWarn("getHistory hook error", it) }
                .getOrNull()
        }
    }
}
