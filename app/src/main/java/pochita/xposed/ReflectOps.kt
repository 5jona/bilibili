package pochita.xposed

import java.lang.reflect.Method

/**
 * Reflection utilities shared across hook adapters, metadata resolution, and protobuf builders.
 *
 * All reflective member lookups traverse the class hierarchy when necessary and quietly return
 * `null` or the caller-supplied fallback so obfuscation differences degrade gracefully.
 */
internal object ReflectOps {

    fun getField(target: Any?, fieldName: String): Any? {
        if (target == null) return null
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                return cls.getDeclaredField(fieldName).apply { isAccessible = true }.get(target)
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            } catch (_: Throwable) {
                return null
            }
        }
        return null
    }

    fun getField(target: Any?, ownerClass: Class<*>, fieldName: String): Any? {
        if (target == null) return null
        return runCatching {
            ownerClass.getDeclaredField(fieldName).apply { isAccessible = true }.get(target)
        }.getOrNull()
    }

    fun getStaticField(ownerClass: Class<*>, fieldName: String): Any? =
        runCatching {
            ownerClass.getDeclaredField(fieldName).apply { isAccessible = true }.get(null)
        }.getOrNull()

    fun setStaticField(ownerClass: Class<*>, fieldName: String, value: Any?): Boolean =
        runCatching {
            ownerClass.getDeclaredField(fieldName).apply { isAccessible = true }.set(null, value)
            true
        }.getOrDefault(false)

    fun setField(target: Any?, fieldName: String, value: Any?): Boolean {
        if (target == null) return false
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                cls.getDeclaredField(fieldName).apply { isAccessible = true }.set(target, value)
                return true
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            } catch (_: Throwable) {
                return false
            }
        }
        return false
    }

    fun getLong(target: Any?, fieldName: String, default: Long = 0L): Long =
        (getField(target, fieldName) as? Number)?.toLong() ?: default

    fun getInt(target: Any?, fieldName: String, default: Int = 0): Int =
        (getField(target, fieldName) as? Number)?.toInt() ?: default

    fun getString(target: Any?, fieldName: String): String =
        getField(target, fieldName) as? String ?: ""

    fun invokeZeroArg(target: Any?, methodName: String): Any? {
        if (target == null) return null
        return runCatching { findMethod(target.javaClass, methodName)?.invoke(target) }.getOrNull()
    }

    fun invokeOneArg(target: Any?, methodName: String, paramType: Class<*>, arg: Any?): Any? {
        if (target == null) return null
        return runCatching { findMethod(target.javaClass, methodName, paramType)?.invoke(target, arg) }.getOrNull()
    }

    fun invokeStaticZeroArg(cls: Class<*>, methodName: String): Any? =
        runCatching {
            cls.getDeclaredMethod(methodName).apply { isAccessible = true }.invoke(null)
        }.getOrNull()

    fun invokeStaticOneArg(cls: Class<*>, methodName: String, paramType: Class<*>, arg: Any?): Any? =
        runCatching {
            findMethod(cls, methodName, paramType)?.invoke(null, arg)
        }.getOrNull()

    fun newInstanceNoArg(cls: Class<*>): Any? =
        runCatching {
            cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        }.getOrNull()

    fun findMethod(cls: Class<*>, name: String, vararg paramTypes: Class<*>): Method? {
        var cur: Class<*>? = cls
        while (cur != null) {
            try {
                return cur.getDeclaredMethod(name, *paramTypes).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                cur = cur.superclass
            }
        }
        return runCatching { cls.getMethod(name, *paramTypes).apply { isAccessible = true } }.getOrNull()
    }

    /**
     * Evaluates a host-classloader `kotlin.Lazy` instance via direct or interface dispatch.
     */
    fun unwrapKotlinLazy(lazyObj: Any?, lazyInterfaceClass: Class<*>?): Any? {
        if (lazyObj == null) return null
        invokeZeroArg(lazyObj, "getValue")?.let { return it }
        return runCatching {
            lazyInterfaceClass?.getMethod("getValue")?.invoke(lazyObj)
        }.getOrNull()
    }
}
