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
}
