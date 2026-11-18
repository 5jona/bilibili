package pochita.bilibili.contract

/**
 * Target class, field, and package descriptors verified against Bilibili Android (`tv.danmaku.bili` v9.18.0, `9180300`).
 *
 * Isolating obfuscated host symbols here keeps feature hooks decoupled from version-specific DEX names.
 */
internal object BiliContracts {
    const val TARGET_PACKAGE = "tv.danmaku.bili"

    const val PKG_INTERFACES_V1 = "com.bapis.bilibili.app.interfaces.v1"
    const val PKG_PLAYERSHARED = "com.bapis.bilibili.playershared"

    const val KOTLIN_UNIT = "kotlin.Unit"
    const val KOTLIN_LAZY = "kotlin.Lazy"
    const val KOTLIN_FUNCTION2 = "kotlin.jvm.functions.Function2"
    const val ANDROIDX_FRAGMENT = "androidx.fragment.app.Fragment"

    object CloudIsolation {
        const val CLOUD_SYNC_HELPER = "kntr.common.inline.history.PlayerHistoryCloudSyncHelper"
        const val THESEUS2_HISTORY_SERVICE = "com.bilibili.ship.theseus2.united.player.history.TheseusHistoryService"
        const val LEGACY_THESEUS_HISTORY_SERVICE = "com.bilibili.ship.theseus.united.player.history.b"
        const val LEGACY_THESEUS_HISTORY_CALLBACK = "com.bilibili.ship.theseus.united.player.history.c"
        const val BILI_CALL = "com.bilibili.okretro.call.BiliCall"
    }

    object MetadataCapture {
        const val THESEUS2_VIEW_DATA = "com.bilibili.ship.theseus2.united.page.view.a"
        const val THESEUS2_VIEW_REPLY = "com.bilibili.ship.theseus2.united.page.view.TheseusViewReply"
        const val THESEUS2_UGC_SUPPLEMENT = "com.bilibili.ship.theseus2.ugc.play.UGCSupplementService"
        const val THESEUS2_OGV_SUPPLEMENT = "com.bilibili.ship.theseus2.ogv.ep.OgvSupplementService"

        const val LEGACY_VIEW_DATA = "com.bilibili.ship.theseus.united.page.view.a"
        const val LEGACY_UGC_SUPPLEMENT = "com.bilibili.ship.theseus.ugc.play.UGCSupplementService"
        const val LEGACY_OGV_SUPPLEMENT = "com.bilibili.ship.theseus.ogv.ep.OgvSupplementService"
    }

    object ResumePlayback {
        const val UGC_START_POS = "com.bilibili.ship.theseus2.ugc.n"
        const val PLAY_VIEW_REPLY = "com.bapis.bilibili.app.playerunite.v1.KPlayViewUniteReply"

        const val CLS_TOAST_INFO = "$PKG_PLAYERSHARED.f0"
        const val CLS_HISTORY_INFO = "$PKG_PLAYERSHARED.B"
        const val CLS_HISTORY_WRAPPER = "$PKG_PLAYERSHARED.A"
    }

    object HistoryMoss {
        const val SERVICE = "$PKG_INTERFACES_V1.HistoryMoss"
        const val CURSOR_V2_REPLY = "$PKG_INTERFACES_V1.CursorV2Reply"
        const val SEARCH_REPLY = "$PKG_INTERFACES_V1.SearchReply"
        const val HISTORY_TAB_REPLY = "$PKG_INTERFACES_V1.HistoryTabReply"
        const val CURSOR_TAB = "$PKG_INTERFACES_V1.CursorTab"
        const val CURSOR_ITEM = "$PKG_INTERFACES_V1.CursorItem"
        const val CURSOR = "$PKG_INTERFACES_V1.Cursor"
        const val PAGE = "$PKG_INTERFACES_V1.Page"
        const val DEVICE_TYPE = "$PKG_INTERFACES_V1.DeviceType"
        const val CARD_UGC = "$PKG_INTERFACES_V1.CardUGC"
        const val CARD_OGV = "$PKG_INTERFACES_V1.CardOGV"
        const val NO_REPLY = "$PKG_INTERFACES_V1.NoReply"
        const val HISTORY_SOURCE = "$PKG_INTERFACES_V1.HistorySource"
    }

    object HistoryUi {
        const val ROUTE_INTERCEPTOR = "mm.b"
        const val HISTORY_FRAGMENT = "com.bilibili.app.history.HistoryFragmentV3"
        const val HISTORY_CONTENT_FRAGMENT = "com.bilibili.app.history.ui.HistoryContentFragment"
        const val HISTORY_VIEW_MODEL = "com.bilibili.app.history.search.presenter.HistorySearchTabViewModel"

        const val HISTORY_MORE_DIALOG = "com.bilibili.app.history.widget.HistoryMoreDialog"
        const val HISTORY_DIALOG_SWITCH_WRAPPER = "com.bilibili.app.history.widget.l"
        const val THREE_POINT_DIALOG =
            "com.bilibili.app.comm.list.common.widget.bottomsheet.drag.ListThreePointMenuDialogFragment"
        const val BOTTOM_SHEET_SWITCH_WRAPPER =
            "com.bilibili.app.comm.list.common.widget.bottomsheet.drag.adapter.s"
        const val DRAG_MODEL_G = "com.bilibili.app.comm.list.common.widget.bottomsheet.drag.model.g"
        const val DRAG_MODEL_F = "com.bilibili.app.comm.list.common.widget.bottomsheet.drag.model.f"

        const val PREFIX_DIALOG_SWITCH = "com.bilibili.app.history.HistoryFragmentV3\$getDialogSwitchList\$"
        const val THREE_POINT_DIALOG_HISTORY_TAG = "history"
    }
}
