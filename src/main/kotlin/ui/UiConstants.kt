package ui

import java.awt.Insets
import javax.swing.border.EmptyBorder

/**
 * Shared UI constants for consistent padding, spacing, and alignment across all Ollama UI components.
 * Keeps the extension looking classy and cohesive.
 */
object UiConstants {

    /** Standard padding for panels (top, left, bottom, right) */
    val PANEL_PADDING = Insets(12, 12, 12, 12)

    /** Tighter padding for nested panels */
    val PANEL_PADDING_SMALL = Insets(8, 8, 8, 8)

    /** Extra padding for main content areas */
    val PANEL_PADDING_LARGE = Insets(16, 16, 16, 16)

    /** Gap between toolbar buttons */
    const val TOOLBAR_GAP = 8

    /** Gap between form rows */
    const val FORM_ROW_GAP = 6

    /** Horizontal gap in FlowLayout toolbars */
    const val FLOW_HGAP = 10

    /** Vertical gap in FlowLayout toolbars */
    const val FLOW_VGAP = 6

    /** Text area margin (inset from edges) */
    val TEXT_AREA_MARGIN = Insets(8, 8, 8, 8)

    /** Border for padded panels */
    fun paddedBorder(insets: Insets = PANEL_PADDING) = EmptyBorder(insets)

    /** Standard panel padding border */
    val standardBorder get() = EmptyBorder(PANEL_PADDING)

    /** Large panel padding border */
    val largeBorder get() = EmptyBorder(PANEL_PADDING_LARGE)
}
