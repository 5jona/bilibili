package pochita.history.hook

import pochita.bilibili.contract.BiliContracts.HistoryMoss
import pochita.bilibili.proto.HistoryProtoCodec
import pochita.history.HistoryModuleContext

/**
 * Intercepts Bilibili's `HistoryMoss` gRPC calls (cursor pagination, search, tabs, delete, clear)
 * when official cloud history is closed so the History screen serves local SQLite records.
 */
internal class HistoryMossHooks(
    private val context: HistoryModuleContext,
    private val protoCodec: HistoryProtoCodec,
) {
    private val runtime = context.runtime

    fun install() {
        hookRpcPair("cursorV2", "executeCursorV2", protoCodec::buildCursorV2Reply)
        hookRpcPair("search", "executeSearch", protoCodec::buildSearchReply)
        hookRpcPair("delete", "executeDelete", protoCodec::handleDelete)
        hookRpcPair("clear", "executeClear", protoCodec::handleClear)
        hookRpcPair("historyTab", "executeHistoryTab", protoCodec::buildHistoryTabReply)
        hookRpcPair("historyTabV2", "executeHistoryTabV2", protoCodec::buildHistoryTabReply)
    }

    private fun hookRpcPair(
        asyncMethod: String,
        syncMethod: String,
        replyBuilder: (Any?) -> Any?,
    ) {
        runtime.hookAllMethodsByName(HistoryMoss.SERVICE, asyncMethod) { chain ->
            if (!context.isCloudHistoryClosed) return@hookAllMethodsByName chain.proceed()
            protoCodec.handleAsyncMoss(chain, replyBuilder)
            null
        }
        runtime.hookAllMethodsByName(HistoryMoss.SERVICE, syncMethod) { chain ->
            if (!context.isCloudHistoryClosed) return@hookAllMethodsByName chain.proceed()
            replyBuilder(chain.getArg(0))
        }
    }
}
