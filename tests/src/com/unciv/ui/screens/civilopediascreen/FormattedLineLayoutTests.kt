package com.unciv.ui.screens.civilopediascreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedLineLayoutTests {

    private val columnWidth = FormattedLine.iconColumnWidth(FormattedLine.minIconSize)

    @Test
    fun `icon grid text column uses two reserved slots`() {
        assertEquals(
            2 * columnWidth + FormattedLine.iconPad,
            FormattedLine.textColumnOffset(columnWidth)
        )
    }

    @Test
    fun `subline without icons uses indent pad only`() {
        assertEquals(FormattedLine.indentPad, FormattedLine.sublineTextOffset(1))
    }

    @Test
    fun `subline indent is smaller than icon grid text column`() {
        assertTrue(
            FormattedLine.sublineTextOffset(1) <
                FormattedLine.textColumnOffset(columnWidth)
        )
    }
}
