package pochita.bilibili.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import pochita.bilibili.contract.BiliUiStrings.PlaybackSpeed as Strings
import pochita.speed.SpeedSpec
import kotlin.math.roundToInt

/**
 * Native Android view rendering unlocked playback speed presets, one-time custom speed control,
 * and persistent long-press speed configuration inside video player speed panels.
 */
internal class PlaybackSpeedPanelView(
    context: Context,
    private val mode: DisplayMode,
    darkTheme: Boolean,
) : LinearLayout(context) {

    enum class DisplayMode {
        FULLSCREEN_DRAWER,
        PORTRAIT_SHEET,
    }

    private data class Palette(
        val cardBg: Int,
        val activeFill: Int,
        val accent: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val divider: Int,
    )

    private data class PresetRowBinding(
        val speed: Float,
        val container: LinearLayout,
        val titleView: TextView,
        val checkView: TextView,
    )

    private data class CustomRowBinding(
        val container: LinearLayout,
        val titleView: TextView,
        val rangeHintView: TextView,
        val valueView: TextView,
    )

    private val density = context.resources.displayMetrics.density
    private var currentDarkTheme: Boolean = darkTheme
    private var palette: Palette = resolvePalette(mode, darkTheme)

    private var currentNormalSpeed: Float = SpeedSpec.DEFAULT_NORMAL_SPEED
    private var currentLongPressSpeed: Float = SpeedSpec.DEFAULT_LONG_PRESS_SPEED

    var onSelectNormalSpeed: (Float) -> Unit = {}
    var onSelectLongPressSpeed: (Float) -> Unit = {}

    private lateinit var normalHeaderView: TextView
    private val normalRows = mutableListOf<PresetRowBinding>()
    private lateinit var normalCustomRow: CustomRowBinding

    private lateinit var dividerView: View

    private lateinit var longPressHeaderView: TextView
    private val longPressRows = mutableListOf<PresetRowBinding>()
    private lateinit var longPressCustomRow: CustomRowBinding

    init {
        orientation = VERTICAL
        if (mode == DisplayMode.FULLSCREEN_DRAWER) {
            setPadding(0, dp(16), 0, dp(16))
        } else {
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        buildContent()
        applyPalette()
        updateSelections()
    }

    /**
     * Synchronizes theme styling and highlighted speeds without recreating child views.
     */
    fun bindState(
        normalSpeed: Float,
        longPressSpeed: Float,
        darkTheme: Boolean,
    ) {
        currentNormalSpeed = SpeedSpec.normalizeSpeed(normalSpeed)
        currentLongPressSpeed = SpeedSpec.normalizeLongPressSpeed(longPressSpeed)
        if (darkTheme != currentDarkTheme) {
            currentDarkTheme = darkTheme
            palette = resolvePalette(mode, darkTheme)
            applyPalette()
        }
        updateSelections()
    }

    private fun buildContent() {
        normalHeaderView = createSectionHeader(Strings.SECTION_NORMAL_SPEED)
        addView(normalHeaderView)

        for (speed in SpeedSpec.NORMAL_PRESETS_DESC) {
            val row = createPresetRow(speed) {
                currentNormalSpeed = speed
                updateSelections()
                onSelectNormalSpeed(speed)
            }
            normalRows.add(row)
            addView(row.container, createRowLayoutParams())
        }

        normalCustomRow = createCustomRow {
            CustomSpeedInputDialog.show(
                context = context,
                isLongPressConfig = false,
                initialSpeed = currentNormalSpeed,
            ) { confirmed ->
                currentNormalSpeed = confirmed
                updateSelections()
                onSelectNormalSpeed(confirmed)
            }
        }
        addView(normalCustomRow.container, createRowLayoutParams())

        dividerView = View(context)
        addView(
            dividerView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1).coerceAtLeast(1)).apply {
                val marginH = if (mode == DisplayMode.FULLSCREEN_DRAWER) dp(16) else dp(8)
                setMargins(marginH, dp(10), marginH, dp(10))
            },
        )

        longPressHeaderView = createSectionHeader(Strings.SECTION_LONG_PRESS)
        addView(longPressHeaderView)

        for (speed in SpeedSpec.LONG_PRESS_PRESETS) {
            val row = createPresetRow(speed) {
                currentLongPressSpeed = speed
                onSelectLongPressSpeed(speed)
                updateSelections()
            }
            longPressRows.add(row)
            addView(row.container, createRowLayoutParams())
        }

        longPressCustomRow = createCustomRow {
            CustomSpeedInputDialog.show(
                context = context,
                isLongPressConfig = true,
                initialSpeed = currentLongPressSpeed,
            ) { confirmed ->
                currentLongPressSpeed = SpeedSpec.normalizeLongPressSpeed(confirmed)
                onSelectLongPressSpeed(currentLongPressSpeed)
                updateSelections()
            }
        }
        addView(longPressCustomRow.container, createRowLayoutParams())
    }

    private fun createSectionHeader(title: String): TextView = TextView(context).apply {
        text = title
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val startPad = if (mode == DisplayMode.FULLSCREEN_DRAWER) dp(20) else dp(12)
        setPadding(startPad, dp(2), dp(12), dp(4))
    }

    private fun createRowLayoutParams(): LayoutParams = LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        topMargin = dp(2)
    }

    private fun createInteractiveRowContainer(rowHeight: Int, onClick: () -> Unit): LinearLayout {
        val startPad = if (mode == DisplayMode.FULLSCREEN_DRAWER) dp(20) else dp(12)
        val endPad = if (mode == DisplayMode.FULLSCREEN_DRAWER) dp(16) else dp(12)
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = rowHeight
            setPadding(startPad, dp(8), endPad, dp(8))
            isClickable = true
            isFocusable = true
            foreground = resolveSelectableForeground()
            setOnClickListener { onClick() }
        }
    }

    private fun resolveSelectableForeground() = TypedValue().let { outValue ->
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true) &&
            outValue.resourceId != 0
        ) {
            context.getDrawable(outValue.resourceId)
        } else {
            null
        }
    }

    private fun createPresetRow(speed: Float, onClick: () -> Unit): PresetRowBinding {
        val rowHeight = if (mode == DisplayMode.FULLSCREEN_DRAWER) dp(42) else dp(44)
        val container = createInteractiveRowContainer(rowHeight, onClick)
        val titleView = TextView(context).apply {
            text = SpeedSpec.formatSpeedLabel(speed)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (mode == DisplayMode.FULLSCREEN_DRAWER) 14f else 15f)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        val checkView = TextView(context).apply {
            text = "\u2713"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.DEFAULT_BOLD
        }
        container.addView(titleView, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(checkView, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return PresetRowBinding(speed, container, titleView, checkView)
    }

    private fun createCustomRow(onClick: () -> Unit): CustomRowBinding {
        val rowHeight = if (mode == DisplayMode.FULLSCREEN_DRAWER) dp(46) else dp(48)
        val container = createInteractiveRowContainer(rowHeight, onClick)
        val leftColumn = LinearLayout(context).apply {
            orientation = VERTICAL
        }
        val titleView = TextView(context).apply {
            text = Strings.ACTION_CUSTOM
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (mode == DisplayMode.FULLSCREEN_DRAWER) 14f else 15f)
        }
        val rangeHintView = TextView(context).apply {
            text = Strings.HINT_SPEED_RANGE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
        }
        leftColumn.addView(titleView)
        leftColumn.addView(rangeHintView)

        val valueView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        container.addView(leftColumn, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(valueView, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return CustomRowBinding(container, titleView, rangeHintView, valueView)
    }

    private fun applyPalette() {
        if (mode == DisplayMode.PORTRAIT_SHEET) {
            background = GradientDrawable().apply {
                setColor(palette.cardBg)
                cornerRadius = dp(12).toFloat()
            }
        } else {
            background = null
        }

        normalHeaderView.setTextColor(palette.textSecondary)
        longPressHeaderView.setTextColor(palette.textSecondary)
        dividerView.setBackgroundColor(palette.divider)
    }

    private fun updateSelections() {
        updatePresetRowGroup(normalRows, currentNormalSpeed)
        updateCustomRow(
            binding = normalCustomRow,
            isCustomActive = !SpeedSpec.isPresetNormalSpeed(currentNormalSpeed),
            activeSpeed = currentNormalSpeed,
        )

        updatePresetRowGroup(longPressRows, currentLongPressSpeed)
        updateCustomRow(
            binding = longPressCustomRow,
            isCustomActive = !SpeedSpec.isPresetLongPressSpeed(currentLongPressSpeed),
            activeSpeed = currentLongPressSpeed,
        )
    }

    private fun applyRowSelectionStyle(
        container: LinearLayout,
        titleView: TextView,
        selected: Boolean,
    ) {
        titleView.setTextColor(if (selected) palette.accent else palette.textPrimary)
        titleView.typeface = if (selected) {
            Typeface.create("sans-serif-medium", Typeface.BOLD)
        } else {
            Typeface.DEFAULT
        }
        container.background = if (selected) {
            GradientDrawable().apply {
                setColor(palette.activeFill)
                cornerRadius = dp(8).toFloat()
            }
        } else {
            null
        }
    }

    private fun updatePresetRowGroup(rows: List<PresetRowBinding>, activeSpeed: Float) {
        for (row in rows) {
            val selected = SpeedSpec.isSameSpeed(row.speed, activeSpeed)
            applyRowSelectionStyle(row.container, row.titleView, selected)
            row.checkView.visibility = if (selected) VISIBLE else INVISIBLE
            row.checkView.setTextColor(palette.accent)
        }
    }

    private fun updateCustomRow(
        binding: CustomRowBinding,
        isCustomActive: Boolean,
        activeSpeed: Float,
    ) {
        applyRowSelectionStyle(binding.container, binding.titleView, isCustomActive)
        binding.rangeHintView.setTextColor(palette.textSecondary)
        binding.valueView.visibility = VISIBLE
        if (isCustomActive) {
            binding.valueView.text = "${SpeedSpec.formatSpeedLabel(activeSpeed)} \u2713"
            binding.valueView.setTextColor(palette.accent)
        } else {
            binding.valueView.text = "\u203A"
            binding.valueView.setTextColor(palette.textSecondary)
        }
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    /**
     * Fullscreen drawer wrapper holding [panelView] and adapting horizontal safe-area padding when
     * orientation changes without recreating the underlying Android view hierarchy.
     */
    internal class FullscreenDrawerContainer(
        context: Context,
        initialLandscape: Boolean,
        panelView: PlaybackSpeedPanelView,
    ) : FrameLayout(context) {

        private val scrollHost: ScrollView = createVerticallyCenteredScroll(context, panelView)
        private var landscape: Boolean = initialLandscape
        private var systemRightInsetPx: Int = 0

        init {
            setBackgroundColor(0xD6000000.toInt())
            isClickable = true
            applyHorizontalPadding()
            setOnApplyWindowInsetsListener { _, insets ->
                systemRightInsetPx = if (android.os.Build.VERSION.SDK_INT >= 30) {
                    insets.getInsets(
                        WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                    ).right
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetRight
                }
                applyHorizontalPadding()
                insets
            }
            addView(
                scrollHost,
                LayoutParams(
                    drawerContentWidthPx(context),
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        fun updateOrientation(isLandscape: Boolean) {
            if (landscape != isLandscape) {
                landscape = isLandscape
                applyHorizontalPadding()
            }
            val expectedWidth = drawerContentWidthPx(context)
            val params = scrollHost.layoutParams
            if (params != null && params.width != expectedWidth) {
                params.width = expectedWidth
                scrollHost.layoutParams = params
            }
        }

        private fun applyHorizontalPadding() {
            val density = context.resources.displayMetrics.density
            val startPad = (16 * density).roundToInt()
            val endPad = ((if (landscape) 56 else 16) * density).roundToInt() + systemRightInsetPx
            setPadding(startPad, 0, endPad, 0)
        }
    }

    companion object {
        const val FULLSCREEN_DRAWER_CONTENT_WIDTH_DP = 208
        const val COLOR_PINK = 0xFFFF6699.toInt()
        private const val COLOR_PINK_FILL = 0x26FF6699

        fun isSystemNightMode(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        fun drawerContentWidthPx(context: Context): Int =
            (FULLSCREEN_DRAWER_CONTENT_WIDTH_DP * context.resources.displayMetrics.density).roundToInt()

        /**
         * Creates a right-aligned dark drawer wrapper suitable for fullscreen Compose `AndroidView` hosting.
         */
        fun wrapForFullscreenDrawer(
            context: Context,
            isLandscape: Boolean,
            panelView: PlaybackSpeedPanelView,
        ): FullscreenDrawerContainer = FullscreenDrawerContainer(context, isLandscape, panelView)

        /**
         * Creates a vertically centered `ScrollView` for insertion inside an existing drawer root view.
         */
        fun createVerticallyCenteredScroll(
            context: Context,
            panelView: PlaybackSpeedPanelView,
        ): ScrollView {
            val scrollView = ScrollView(context).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            }
            val wrapper = FrameLayout(context)
            wrapper.addView(
                panelView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL,
                ),
            )
            scrollView.addView(
                wrapper,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            return scrollView
        }

        /**
         * Wraps [panelView] in a height-capped scrollable sheet container so all controls remain
         * reachable on compact portrait screens.
         */
        fun wrapForSheetDialog(
            context: Context,
            panelView: PlaybackSpeedPanelView,
        ): FrameLayout {
            val density = context.resources.displayMetrics.density
            val outer = FrameLayout(context).apply {
                val pad = (16 * density).roundToInt()
                setPadding(pad, pad, pad, pad)
            }
            val maxHeightPx = (context.resources.displayMetrics.heightPixels * 0.65f).roundToInt()
            val scrollView = MaxHeightScrollView(context, maxHeightPx).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            }
            scrollView.addView(
                panelView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            outer.addView(
                scrollView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            return outer
        }

        private fun resolvePalette(mode: DisplayMode, darkTheme: Boolean): Palette =
            when {
                mode == DisplayMode.FULLSCREEN_DRAWER -> Palette(
                    cardBg = 0x00000000,
                    activeFill = COLOR_PINK_FILL,
                    accent = COLOR_PINK,
                    textPrimary = 0xFFFFFFFF.toInt(),
                    textSecondary = 0xFFA2A7B0.toInt(),
                    divider = 0x1FFFFFFF,
                )
                darkTheme -> Palette(
                    cardBg = 0xFF1C1D20.toInt(),
                    activeFill = COLOR_PINK_FILL,
                    accent = COLOR_PINK,
                    textPrimary = 0xFFF2F3F5.toInt(),
                    textSecondary = 0xFF9499A0.toInt(),
                    divider = 0x18FFFFFF,
                )
                else -> Palette(
                    cardBg = 0xFFFFFFFF.toInt(),
                    activeFill = COLOR_PINK_FILL,
                    accent = COLOR_PINK,
                    textPrimary = 0xFF18191C.toInt(),
                    textSecondary = 0xFF61666D.toInt(),
                    divider = 0x14000000,
                )
            }
    }

    private class MaxHeightScrollView(
        context: Context,
        private val maxHeightPx: Int,
    ) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cappedHeightSpec = if (maxHeightPx > 0) {
                val incomingSize = MeasureSpec.getSize(heightMeasureSpec)
                val incomingMode = MeasureSpec.getMode(heightMeasureSpec)
                val effectiveMax = if (incomingMode == MeasureSpec.UNSPECIFIED) {
                    maxHeightPx
                } else {
                    minOf(incomingSize, maxHeightPx)
                }
                MeasureSpec.makeMeasureSpec(effectiveMax, MeasureSpec.AT_MOST)
            } else {
                heightMeasureSpec
            }
            super.onMeasure(widthMeasureSpec, cappedHeightSpec)
        }
    }
}
