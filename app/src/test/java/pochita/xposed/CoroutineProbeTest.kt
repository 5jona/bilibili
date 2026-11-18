package pochita.xposed

import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CoroutineProbe] suspension sentinel detection across classloaders.
 */
class CoroutineProbeTest {

    @Test
    fun isSuspended_identifiesCoroutineSuspendedWithoutCachedSentinel() {
        assertTrue(CoroutineProbe.isSuspended(COROUTINE_SUSPENDED, cachedSentinel = null))
        assertFalse(CoroutineProbe.isSuspended(Unit, cachedSentinel = null))
        assertFalse(CoroutineProbe.isSuspended(null, cachedSentinel = null))
        assertFalse(CoroutineProbe.isSuspended("COROUTINE_SUSPENDED", cachedSentinel = null))
    }

    @Test
    fun resolveSentinel_resolvesInheritedIntrinsicsMethod() {
        val sentinel = CoroutineProbe.resolveSentinel(javaClass.classLoader!!)
        assertNotNull(sentinel)
        assertTrue(CoroutineProbe.isSuspended(COROUTINE_SUSPENDED, sentinel))
    }
}
