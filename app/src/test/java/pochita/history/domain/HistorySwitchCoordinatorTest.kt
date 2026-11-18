package pochita.history.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for mutually exclusive switch state transitions in [HistorySwitchCoordinator].
 */
class HistorySwitchCoordinatorTest {

    @Test
    fun cloudOnToLocalOn_keepsLocalEnabledAcrossSuspendAndCloudOffCompletion() {
        val coordinator = HistorySwitchCoordinator(initialCloudClosed = false)
        val localEnabled = true

        val localEffect = coordinator.onLocalSwitchToggled(enableLocal = true)
        assertTrue(localEffect.mustCloseCloud)
        assertTrue(coordinator.isCloudHistoryClosed)
        assertEquals(false, coordinator.pendingCloudTarget)

        val cloudToggleEffect = coordinator.onCloudSwitchToggled(turnOn = false, localEnabled = localEnabled)
        assertFalse(cloudToggleEffect.disableLocal)
        assertTrue(cloudToggleEffect.refreshImmediate)

        // Intermediate Flow emission before switch coroutine finishes must be ignored.
        assertNull(
            coordinator.onCloudStateObserved(
                cloudClosedFromVm = false,
                localEnabled = localEnabled,
                fromSwitchCompletion = false,
            ),
        )
        assertTrue(coordinator.isCloudHistoryClosed)

        val completionEffect = coordinator.onCloudStateObserved(
            cloudClosedFromVm = true,
            localEnabled = localEnabled,
            fromSwitchCompletion = true,
        )!!
        assertFalse(completionEffect.disableLocal)
        assertTrue(completionEffect.shouldRefreshTabs)
        assertTrue(coordinator.isCloudHistoryClosed)
        assertNull(coordinator.pendingCloudTarget)
    }

    @Test
    fun cloudOnToLocalOn_disablesLocalIfServerRejectedCloudClose() {
        val coordinator = HistorySwitchCoordinator(initialCloudClosed = false)
        val localEffect = coordinator.onLocalSwitchToggled(enableLocal = true)
        assertTrue(localEffect.mustCloseCloud)

        val completionEffect = coordinator.onCloudStateObserved(
            cloudClosedFromVm = false,
            localEnabled = true,
            fromSwitchCompletion = true,
        )!!
        assertTrue(completionEffect.disableLocal)
        assertTrue(completionEffect.shouldRefreshTabs)
        assertFalse(coordinator.isCloudHistoryClosed)
    }

    @Test
    fun cloudOffLocalOffToLocalOn_refreshesWithoutClosingCloud() {
        val coordinator = HistorySwitchCoordinator(initialCloudClosed = true)

        val effect = coordinator.onLocalSwitchToggled(enableLocal = true)
        assertFalse(effect.mustCloseCloud)
        assertTrue(effect.refreshImmediate)
        assertTrue(coordinator.isCloudHistoryClosed)
        assertNull(coordinator.pendingCloudTarget)
    }

    @Test
    fun cloudOffLocalOnToCloudOn_disablesLocalImmediatelyAndRefreshesTabsOnCompletion() {
        val coordinator = HistorySwitchCoordinator(initialCloudClosed = true)

        val toggleEffect = coordinator.onCloudSwitchToggled(turnOn = true, localEnabled = true)
        assertTrue(toggleEffect.disableLocal)
        assertFalse(toggleEffect.refreshImmediate)
        assertFalse(coordinator.isCloudHistoryClosed)
        assertEquals(true, coordinator.pendingCloudTarget)

        val completionEffect = coordinator.onCloudStateObserved(
            cloudClosedFromVm = false,
            localEnabled = false,
            fromSwitchCompletion = true,
        )!!
        assertFalse(completionEffect.disableLocal)
        assertTrue(completionEffect.shouldRefreshTabs)
        assertFalse(coordinator.isCloudHistoryClosed)
        assertNull(coordinator.pendingCloudTarget)
    }

    @Test
    fun cloudOffLocalOnToLocalOff_keepsBothOffAndRefreshes() {
        val coordinator = HistorySwitchCoordinator(initialCloudClosed = true)

        val effect = coordinator.onLocalSwitchToggled(enableLocal = false)
        assertFalse(effect.mustCloseCloud)
        assertTrue(effect.refreshImmediate)
        assertTrue(coordinator.isCloudHistoryClosed)
    }
}
