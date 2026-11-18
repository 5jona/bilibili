package pochita.history.domain

/**
 * Coordinates mutually exclusive state transitions between Bilibili's official cloud history
 * switch and the module's local history switch.
 *
 * Cloud and local history cannot both be active simultaneously; both may be turned off at once.
 */
internal class HistorySwitchCoordinator(
    initialCloudClosed: Boolean = true,
) {
    @Volatile
    var isCloudHistoryClosed: Boolean = initialCloudClosed
        private set

    @Volatile
    var pendingCloudTarget: Boolean? = null
        private set

    data class CloudToggleEffect(
        val disableLocal: Boolean,
        val refreshImmediate: Boolean,
    )

    data class LocalToggleEffect(
        val mustCloseCloud: Boolean,
        val refreshImmediate: Boolean,
    )

    data class ObservedStateEffect(
        val disableLocal: Boolean,
        val shouldRefreshTabs: Boolean,
    )

    /**
     * Records an explicit or programmatic toggle of the official cloud history switch.
     */
    fun onCloudSwitchToggled(turnOn: Boolean, localEnabled: Boolean): CloudToggleEffect {
        pendingCloudTarget = turnOn
        isCloudHistoryClosed = !turnOn
        return CloudToggleEffect(
            disableLocal = turnOn && localEnabled,
            refreshImmediate = !turnOn,
        )
    }

    /**
     * Records a user toggle of the module's local history switch and indicates whether cloud
     * history must be closed to preserve mutual exclusion.
     */
    fun onLocalSwitchToggled(enableLocal: Boolean): LocalToggleEffect {
        val wasCloudOpen = !isCloudHistoryClosed
        if (enableLocal && wasCloudOpen) {
            pendingCloudTarget = false
            isCloudHistoryClosed = true
            return LocalToggleEffect(
                mustCloseCloud = true,
                refreshImmediate = true,
            )
        }
        return LocalToggleEffect(
            mustCloseCloud = false,
            refreshImmediate = true,
        )
    }

    /**
     * Reconciles cloud switch state emitted by `HistorySearchTabViewModel`.
     *
     * Intermediate StateFlow emissions are ignored while [pendingCloudTarget] is in flight unless
     * [fromSwitchCompletion] confirms the underlying coroutine finished executing.
     */
    fun onCloudStateObserved(
        cloudClosedFromVm: Boolean,
        localEnabled: Boolean,
        fromSwitchCompletion: Boolean,
    ): ObservedStateEffect? {
        if (!fromSwitchCompletion && pendingCloudTarget != null) {
            return null
        }
        if (fromSwitchCompletion) {
            pendingCloudTarget = null
        }
        isCloudHistoryClosed = cloudClosedFromVm
        return ObservedStateEffect(
            disableLocal = !isCloudHistoryClosed && localEnabled,
            shouldRefreshTabs = fromSwitchCompletion,
        )
    }
}
