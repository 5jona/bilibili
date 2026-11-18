package pochita.bilibili.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import pochita.bilibili.contract.BiliUiStrings.PlaybackSpeed as Strings
import pochita.speed.SpeedSpec

/**
 * Interactive dialog allowing precise custom playback speed selection between `0.1X` and `8.0X`
 * via stepper controls, seekbar scrubbing, or direct numeric entry.
 */
internal object CustomSpeedInputDialog {

    private const val COLOR_TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFFA2A7B0.toInt()
    private const val COLOR_SURFACE = 0xFF232529.toInt()
    private const val COLOR_SURFACE_BORDER = 0xFF3A3D44.toInt()
    private const val COLOR_ERROR = 0xFFFF5C5C.toInt()
    private const val STEP_DELTA = 0.1f

    private data class DialogSpec(
        val title: String,
        val subtitle: String,
        val resetLabel: String,
        val resetValue: Float,
    )

    private class DialogViews(
        val root: LinearLayout,
        val previewView: TextView,
        val minusButton: TextView,
        val plusButton: TextView,
        val seekBar: SeekBar,
        val inputBox: EditText,
        val errorView: TextView,
    )

    fun show(
        context: Context,
        isLongPressConfig: Boolean,
        initialSpeed: Float,
        onConfirmed: (Float) -> Unit,
    ) {
        val hostContext = unwrapActivityContext(context)
        val spec = resolveSpec(isLongPressConfig)
        val initialNormalized = SpeedSpec.normalizeSpeed(initialSpeed, spec.resetValue)
        val views = buildDialogViews(hostContext, spec, initialNormalized)

        val dialog = AlertDialog.Builder(hostContext, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(spec.title)
            .setView(views.root)
            .setPositiveButton(Strings.BUTTON_CONFIRM, null)
            .setNeutralButton(spec.resetLabel, null)
            .setNegativeButton(Strings.BUTTON_CANCEL, null)
            .create()

        bindDialogEvents(dialog, views, spec, initialNormalized, onConfirmed)
        dialog.show()
    }

    private fun resolveSpec(isLongPressConfig: Boolean): DialogSpec =
        if (isLongPressConfig) {
            DialogSpec(
                title = Strings.DIALOG_TITLE_LONG_PRESS,
                subtitle = Strings.DIALOG_SUBTITLE_LONG_PRESS,
                resetLabel = Strings.BUTTON_RESET_LONG_PRESS,
                resetValue = SpeedSpec.DEFAULT_LONG_PRESS_SPEED,
            )
        } else {
            DialogSpec(
                title = Strings.DIALOG_TITLE_NORMAL,
                subtitle = Strings.DIALOG_SUBTITLE_NORMAL,
                resetLabel = Strings.BUTTON_RESET_NORMAL,
                resetValue = SpeedSpec.DEFAULT_NORMAL_SPEED,
            )
        }

    private fun buildDialogViews(
        hostContext: Context,
        spec: DialogSpec,
        initialSpeed: Float,
    ): DialogViews {
        val density = hostContext.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val root = LinearLayout(hostContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }

        val subtitleView = TextView(hostContext).apply {
            text = spec.subtitle
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        root.addView(subtitleView)

        val previewView = TextView(hostContext).apply {
            text = SpeedSpec.formatSpeedLabel(initialSpeed)
            setTextColor(PlaybackSpeedPanelView.COLOR_PINK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(8))
        }
        root.addView(previewView)

        val stepperRow = LinearLayout(hostContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        val minusButton = createStepperButton(hostContext, "-0.1", ::dp)
        val plusButton = createStepperButton(hostContext, "+0.1", ::dp)
        val seekBar = SeekBar(hostContext).apply {
            max = SpeedSpec.SLIDER_MAX_PROGRESS
            progress = SpeedSpec.speedToSliderProgress(initialSpeed)
        }
        stepperRow.addView(minusButton, LinearLayout.LayoutParams(dp(56), dp(36)))
        stepperRow.addView(
            seekBar,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
            },
        )
        stepperRow.addView(plusButton, LinearLayout.LayoutParams(dp(56), dp(36)))
        root.addView(stepperRow)

        val inputBox = EditText(hostContext).apply {
            hint = Strings.INPUT_HINT
            setText(SpeedSpec.formatSpeedNumber(initialSpeed))
            setTextColor(COLOR_TEXT_PRIMARY)
            setHintTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(COLOR_SURFACE)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), COLOR_SURFACE_BORDER)
            }
        }
        root.addView(
            inputBox,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            },
        )

        val errorView = TextView(hostContext).apply {
            text = Strings.ERROR_INVALID_RANGE
            setTextColor(COLOR_ERROR)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            visibility = View.GONE
            setPadding(0, dp(6), 0, 0)
        }
        root.addView(errorView)

        return DialogViews(root, previewView, minusButton, plusButton, seekBar, inputBox, errorView)
    }

    private fun bindDialogEvents(
        dialog: AlertDialog,
        views: DialogViews,
        spec: DialogSpec,
        initialSpeed: Float,
        onConfirmed: (Float) -> Unit,
    ) {
        var selectedSpeed = initialSpeed
        var updatingUi = false

        fun applySpeedToControls(newSpeed: Float, syncEditText: Boolean) {
            selectedSpeed = SpeedSpec.normalizeSpeed(newSpeed, spec.resetValue)
            updatingUi = true
            try {
                views.previewView.text = SpeedSpec.formatSpeedLabel(selectedSpeed)
                views.seekBar.progress = SpeedSpec.speedToSliderProgress(selectedSpeed)
                if (syncEditText) {
                    val formatted = SpeedSpec.formatSpeedNumber(selectedSpeed)
                    views.inputBox.setText(formatted)
                    views.inputBox.setSelection(formatted.length)
                }
                views.errorView.visibility = View.GONE
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
            } finally {
                updatingUi = false
            }
        }

        fun confirmCurrentInput() {
            val validated = SpeedSpec.parseSpeedInput(views.inputBox.text?.toString().orEmpty())
            if (validated == null) {
                views.errorView.visibility = View.VISIBLE
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                return
            }
            dialog.dismiss()
            onConfirmed(validated)
        }

        views.seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser || updatingUi) return
                    applySpeedToControls(SpeedSpec.sliderProgressToSpeed(progress), syncEditText = true)
                }

                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            },
        )

        views.minusButton.setOnClickListener {
            applySpeedToControls(SpeedSpec.stepSpeed(selectedSpeed, -STEP_DELTA), syncEditText = true)
        }
        views.plusButton.setOnClickListener {
            applySpeedToControls(SpeedSpec.stepSpeed(selectedSpeed, STEP_DELTA), syncEditText = true)
        }

        views.inputBox.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (updatingUi) return
                    val parsed = SpeedSpec.parseSpeedInput(s?.toString().orEmpty())
                    if (parsed == null) {
                        views.errorView.visibility = View.VISIBLE
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                    } else {
                        applySpeedToControls(parsed, syncEditText = false)
                    }
                }
            },
        )
        views.inputBox.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                confirmCurrentInput()
                true
            } else {
                false
            }
        }

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            positive?.setTextColor(PlaybackSpeedPanelView.COLOR_PINK)
            positive?.setOnClickListener { confirmCurrentInput() }
            neutral?.setOnClickListener {
                applySpeedToControls(spec.resetValue, syncEditText = true)
            }
        }
    }

    private fun createStepperButton(
        context: Context,
        label: String,
        dp: (Int) -> Int,
    ): TextView = TextView(context).apply {
        text = label
        setTextColor(COLOR_TEXT_PRIMARY)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        background = GradientDrawable().apply {
            setColor(COLOR_SURFACE)
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), COLOR_SURFACE_BORDER)
        }
    }

    private fun unwrapActivityContext(context: Context): Context {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) {
                return current
            }
            current = current.baseContext
        }
        return context
    }
}
