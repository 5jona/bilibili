package pochita.xposed

/**
 * Utilities for resolving and recognizing Kotlin's `COROUTINE_SUSPENDED` sentinel across
 * module and host classloaders.
 */
internal object CoroutineProbe {
    private const val INTRINSICS_CLASS = "kotlin.coroutines.intrinsics.IntrinsicsKt"
    private const val SINGLETONS_CLASS = "kotlin.coroutines.intrinsics.CoroutineSingletons"
    private const val SUSPENDED_ENUM_NAME = "COROUTINE_SUSPENDED"

    /**
     * Resolves `COROUTINE_SUSPENDED` from [classLoader], falling back from the inherited static
     * getter on `IntrinsicsKt` (which requires `getMethod` + `isAccessible`) to the enum constant.
     */
    fun resolveSentinel(classLoader: ClassLoader): Any? =
        runCatching {
            classLoader.loadClass(INTRINSICS_CLASS)
                .getMethod("getCOROUTINE_SUSPENDED")
                .apply { isAccessible = true }
                .invoke(null)
        }.getOrNull() ?: runCatching {
            classLoader.loadClass(SINGLETONS_CLASS)
                .enumConstants
                ?.firstOrNull { it.toString() == SUSPENDED_ENUM_NAME }
        }.getOrNull()

    /**
     * Returns `true` when [result] represents a suspended coroutine execution.
     */
    fun isSuspended(result: Any?, cachedSentinel: Any? = null): Boolean {
        if (result == null) return false
        if (cachedSentinel != null && result === cachedSentinel) return true
        return result.javaClass.name == SINGLETONS_CLASS &&
            result.toString() == SUSPENDED_ENUM_NAME
    }
}
