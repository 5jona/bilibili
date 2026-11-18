package pochita.history.hook

import android.os.Bundle
import android.view.View
import pochita.bilibili.contract.BiliContracts.ANDROIDX_FRAGMENT
import pochita.bilibili.contract.BiliContracts.HistoryUi
import pochita.bilibili.ui.HistorySettingsUiController
import pochita.xposed.HookRuntime
import pochita.xposed.ReflectOps

/**
 * Installs lifecycle and Compose-menu hooks for Bilibili's `HistoryFragmentV3`,
 * `HistoryContentFragment`, and three-dot History Settings dialogs.
 */
internal class HistorySettingsUiHooks(
    private val runtime: HookRuntime,
    private val controller: HistorySettingsUiController,
) {
    fun install() {
        // Keep native HistoryFragmentV3 active instead of redirecting to Compose MVI history.
        runtime.hookAllConstructors(HistoryUi.ROUTE_INTERCEPTOR) { chain ->
            ReflectOps.setField(chain.thisObject, "a", false)
        }

        runtime.hookMethod(
            HistoryUi.HISTORY_FRAGMENT,
            "onViewCreated",
            View::class.java,
            Bundle::class.java,
        ) { chain ->
            val result = chain.proceed()
            controller.onHistoryFragmentActive(chain.thisObject)
            result
        }

        runtime.hookMethod(
            "${HistoryUi.HISTORY_FRAGMENT}\$onViewCreated\$2\$2",
            "invokeSuspend",
            Any::class.java,
        ) { chain ->
            if (controller.isCloudSwitchIdle()) {
                controller.updateCloudStateFromFmJ(
                    ReflectOps.getField(chain.thisObject, "L\$0"),
                    fromSwitchCompletion = false,
                )
            }
            val result = chain.proceed()
            controller.onHistoryFragmentActive(ReflectOps.getField(chain.thisObject, "this\$0"))
            result
        }

        runtime.hookMethod(
            HistoryUi.HISTORY_CONTENT_FRAGMENT,
            "onViewCreated",
            View::class.java,
            Bundle::class.java,
        ) { chain ->
            val result = chain.proceed()
            controller.onContentFragmentActive(chain.thisObject)
            result
        }

        runtime.hookAllMethodsByName(ANDROIDX_FRAGMENT, "onResume") { chain ->
            val result = chain.proceed()
            val target = chain.thisObject
            when (target?.javaClass?.name) {
                HistoryUi.HISTORY_FRAGMENT -> controller.onHistoryFragmentActive(target)
                HistoryUi.HISTORY_CONTENT_FRAGMENT -> controller.onContentFragmentActive(target)
            }
            result
        }

        runtime.hookAllConstructors(HistoryUi.HISTORY_MORE_DIALOG) { chain ->
            controller.onSettingsDialogCreated(chain.thisObject)
        }
        runtime.hookAllConstructors(HistoryUi.THREE_POINT_DIALOG) { chain ->
            controller.onSettingsDialogCreated(chain.thisObject)
        }
        runtime.hookAllConstructors(HistoryUi.DRAG_MODEL_G) { chain ->
            runCatching { controller.enhanceHistorySettingsModel(chain.thisObject) }
                .onFailure { runtime.logWarn("enhanceHistorySettingsModel error", it) }
        }

        if (runtime.loadClassOrNull(HistoryUi.HISTORY_DIALOG_SWITCH_WRAPPER) != null) {
            runtime.hookAllConstructors(HistoryUi.HISTORY_DIALOG_SWITCH_WRAPPER) { chain ->
                controller.registerSwitchStateBinding(chain.thisObject, modelField = "e", stateField = "a")
            }
        }
        if (runtime.loadClassOrNull(HistoryUi.BOTTOM_SHEET_SWITCH_WRAPPER) != null) {
            runtime.hookAllConstructors(HistoryUi.BOTTOM_SHEET_SWITCH_WRAPPER) { chain ->
                controller.registerSwitchStateBinding(chain.thisObject, modelField = "d", stateField = "a")
            }
        }
    }
}
