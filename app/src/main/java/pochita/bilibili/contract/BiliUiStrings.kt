package pochita.bilibili.contract

import pochita.history.data.LocalHistoryDb

/**
 * Localized UI strings rendered inside Bilibili's History screens and playback resume toasts.
 */
internal object BiliUiStrings {
    const val TITLE_CLOUD_HISTORY = "云端历史记录"
    const val SUBTITLE_CLOUD_HISTORY = "同步并展示账号在所有设备的浏览足迹"

    const val TITLE_LOCAL_DEVICE_CLOUD_FILTER = "仅看本机云端记录"
    const val SUBTITLE_LOCAL_DEVICE_CLOUD_FILTER = "开启云端历史时，不展示其他设备的记录"

    const val TITLE_LOCAL_HISTORY = "本地历史记录"
    const val SUBTITLE_LOCAL_HISTORY = "仅保存在本机数据库，不上报云端"

    /**
     * Tab metadata `(business, displayTitle, deepLinkRouter)` served when cloud history is off.
     */
    val LOCAL_TABS: List<Triple<String, String, String>> = listOf(
        Triple("all", "全部", "bilibili://main/history/all"),
        Triple(LocalHistoryDb.BUSINESS_ARCHIVE, "视频", "bilibili://main/history/video"),
        Triple(LocalHistoryDb.BUSINESS_PGC, "番剧", "bilibili://main/history/video"),
    )

    fun formatEpisodeLabel(shortTitle: String, longTitle: String): String {
        val trimmedShort = shortTitle.trim()
        val trimmedLong = longTitle.trim()
        val epPrefix = when {
            trimmedShort.isEmpty() -> ""
            trimmedShort.all { it.isDigit() } -> "第${trimmedShort}话"
            else -> trimmedShort
        }
        return listOf(epPrefix, trimmedLong).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun formatResumeToast(formattedTime: String): String = "已为您定位至 $formattedTime"

    object PlaybackSpeed {
        const val SECTION_LONG_PRESS = "长按倍速"
        const val SECTION_NORMAL_SPEED = "播放倍速"
        const val ACTION_CUSTOM = "自定义"
        const val HINT_SPEED_RANGE = "0.1X ~ 8.0X"

        const val DIALOG_TITLE_NORMAL = "自定义播放倍速"
        const val DIALOG_SUBTITLE_NORMAL = "可设置倍率范围：0.1X ~ 8.0X"
        const val DIALOG_TITLE_LONG_PRESS = "自定义长按倍速"
        const val DIALOG_SUBTITLE_LONG_PRESS = "长按视频时触发（0.1X ~ 8.0X）"

        const val INPUT_HINT = "输入 0.1 ~ 8.0 之间的数值"
        const val ERROR_INVALID_RANGE = "请输入 0.1 ~ 8.0 之间的有效倍速"
        const val BUTTON_CONFIRM = "确定"
        const val BUTTON_CANCEL = "取消"
        const val BUTTON_RESET_NORMAL = "恢复 1.0X"
        const val BUTTON_RESET_LONG_PRESS = "重置 2.0X"

        fun formatNormalSwitchedToast(speedLabel: String): String = "已切换为 $speedLabel 倍速"
        fun formatLongPressUpdatedToast(speedLabel: String): String = "长按倍速已设为 $speedLabel"
    }
}
