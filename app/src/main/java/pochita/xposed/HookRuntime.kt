package pochita.xposed

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

/**
 * Lightweight LSPosed interception DSL and classloading runtime bound to a target [ClassLoader].
 */
internal class HookRuntime(
    private val module: XposedModule,
    val classLoader: ClassLoader,
    private val logTag: String = DEFAULT_TAG,
) {
    private val coroutineSuspendedSentinel: Any? by lazy {
        CoroutineProbe.resolveSentinel(classLoader)
    }

    fun loadClassOrNull(name: String): Class<*>? =
        runCatching { classLoader.loadClass(name) }.getOrNull()

    fun isCoroutineSuspended(result: Any?): Boolean =
        CoroutineProbe.isSuspended(result, coroutineSuspendedSentinel)

    inline fun hookMethod(
        className: String,
        methodName: String,
        vararg paramTypes: Class<*>,
        crossinline block: (XposedInterface.Chain) -> Any?,
    ) {
        val cls = loadClassOrNull(className) ?: run {
            logWarn("Class not found: $className")
            return
        }
        val method = runCatching { cls.getDeclaredMethod(methodName, *paramTypes) }
            .getOrElse {
                logWarn("Method not found: $className.$methodName", it)
                return
            }
        interceptExecutable(method) { chain -> block(chain) }
    }

    inline fun hookMethodIfPresent(
        className: String,
        methodName: String,
        vararg paramTypeNames: String,
        crossinline block: (XposedInterface.Chain) -> Any?,
    ) {
        val paramClasses = paramTypeNames.map { name ->
            loadClassOrNull(name) ?: return
        }.toTypedArray()
        hookMethod(className, methodName, *paramClasses, block = block)
    }

    inline fun hookInvokeSuspendIfPresent(
        className: String,
        crossinline after: (XposedInterface.Chain) -> Unit,
    ) {
        if (loadClassOrNull(className) == null) return
        hookMethod(className, "invokeSuspend", Any::class.java) { chain ->
            val result = chain.proceed()
            runCatching { after(chain) }
                .onFailure { logWarn("invokeSuspend hook error on $className", it) }
            result
        }
    }

    inline fun hookAllMethodsByName(
        className: String,
        methodName: String,
        crossinline block: (XposedInterface.Chain) -> Any?,
    ) {
        val cls = loadClassOrNull(className) ?: run {
            logWarn("Class not found: $className")
            return
        }
        val matched = cls.declaredMethods.filter { it.name == methodName }
        if (matched.isEmpty()) {
            logWarn("No methods named $methodName on $className")
            return
        }
        for (method in matched) {
            interceptExecutable(method) { chain -> block(chain) }
        }
    }

    inline fun hookAllConstructors(
        className: String,
        crossinline after: (XposedInterface.Chain) -> Unit,
    ) {
        val cls = loadClassOrNull(className) ?: run {
            logWarn("Class not found: $className")
            return
        }
        for (ctor in cls.declaredConstructors) {
            interceptExecutable(ctor) { chain ->
                val result = chain.proceed()
                runCatching { after(chain) }
                    .onFailure { logWarn("Constructor hook error on $className", it) }
                result
            }
        }
    }

    inline fun interceptExecutable(
        executable: Executable,
        crossinline block: (XposedInterface.Chain) -> Any?,
    ) {
        module.hook(executable).intercept { chain -> block(chain) }
    }

    fun logInfo(message: String) {
        module.log(Log.INFO, logTag, message)
    }

    fun logWarn(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            module.log(Log.WARN, logTag, message, throwable)
        } else {
            module.log(Log.WARN, logTag, message)
        }
    }

    companion object {
        private const val DEFAULT_TAG = "BiliLocalHistory"
    }
}
