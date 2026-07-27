package com.unciv.ui.screens.civilopediascreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattedLineLayoutTests {

    private val columnWidth = FormattedLine.iconColumnWidth(FormattedLine.minIconSize)

    @Test
    fun `indent 0 text column does not depend on whether the line has icons`() {
        assertEquals(
            FormattedLine.textColumnOffset(0, columnWidth),
            FormattedLine.textColumnOffset(0, columnWidth)
        )
    }

    @Test
    fun `indent 1 text column is further right than indent 0`() {
        assertTrue(
            FormattedLine.textColumnOffset(1, columnWidth) >
                FormattedLine.textColumnOffset(0, columnWidth)
        )
    }

    @Test
    fun `leading indent for indent 1 matches two icon columns`() {
        assertEquals(
            FormattedLine.indentOneAtNumIcons * columnWidth,
            FormattedLine.leadingIndent(1, columnWidth)
        )
    }
}
