package com.unciv.testing

import com.unciv.ui.components.widgets.UncivTextField
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
/**
 *  Container for tests that **don't** require loading **any** of Unciv's assets
 */
class SimpleTests {
    @Test
    fun testRngSeedFormatting() {
        val data = listOf(
            0L to "AAA-AAA-AAA-AA",
            Long.MAX_VALUE to "f//-///-///-/8",
            Long.MIN_VALUE to "gAA-AAA-AAA-AA",
            0x101010101010101L to "AQE-BAQ-EBA-QE",
            0x4040404040404040L to "QEB-AQE-BAQ-EA",
            0x78A6B5C4D3E2F100L to "eKa-1xN-Pi8-QA",
        )
        var fails = 0
        for ((value, expectedFormattedString) in data) {
            try {
                val actualFormattedString = UncivTextField.RNGSeed.unitTestFormat(value)
                if (UncivTextField.RNGSeed.unitTestParse(actualFormattedString) != value) {
                    println("UncivTextField.RNGSeed: value $value did not make the round-trip")
                    fails++
                }
                val plain = value.toString()
                if (UncivTextField.RNGSeed.unitTestParse(plain) != value) {
                    println("UncivTextField.RNGSeed: value $value failed parsing back the plain toString()")
                    fails++
                }
                if (actualFormattedString != expectedFormattedString) {
                    println("UncivTextField.RNGSeed: value $value formats to `$actualFormattedString`, expected: `$expectedFormattedString`")
                    fails++
                }
            } catch (ex: NumberFormatException) {
                fails++
            }
        }
        Assert.assertEquals("RNGSeed formatting should not fail", 0, fails)
    }
}
