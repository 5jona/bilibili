package pochita.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import pochita.bilibili.contract.BiliContracts
import pochita.history.BiliLocalHistoryModule

/**
 * LSPosed (API 102) entrypoint registered in `META-INF/xposed/java_init.list`.
 */
class MainHook : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        super.onModuleLoaded(param)
        log(
            Log.INFO,
            TAG,
            "Module loaded in process: ${param.processName}, isSystemServer: ${param.isSystemServer}",
        )
    }

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)
        log(Log.INFO, TAG, "Package ready: ${param.packageName}")

        if (param.packageName == BiliContracts.TARGET_PACKAGE && param.isFirstPackage) {
            log(Log.INFO, TAG, "Installing local history hooks for ${param.packageName}")
            val runtime = HookRuntime(module = this, classLoader = param.classLoader)
            BiliLocalHistoryModule(runtime).install()
        }
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        log(Log.INFO, TAG, "Preparing module hot reload")
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        super.onHotReloaded(param)
        log(Log.INFO, TAG, "Module hot reloaded successfully")
    }

    companion object {
        private const val TAG = "PochitaHook"
    }
}
