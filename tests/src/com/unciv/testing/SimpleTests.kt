package com.unciv.testing

import com.unciv.ui.components.formatting.IRNGSeedFormat
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
/**
 *  Container for tests that **don't** require loading **any** of Unciv's assets
 */
class SimpleTests {

    // Differs from `object RNGSeedFormat` in:
    // - Localization-independent (can run with UncivGame.Current not initialized)
    // - Pretty format already activated
    // - proxies internals for direct testing (no @VisibleForTesting needed)
    private class TestRNGSeedFormat : IRNGSeedFormat() {
        override fun format(value: Long) = value.prettyFormat()
        override fun parseNumber(text: String) = text.toLong()
        val maxBase2XValue get() = Companion.maxBase2XValue
        fun longHashCode(text: String) = text.hashCode64()
    }

    @Test
    fun testRngSeedFormatting() {
        val rngSeedFormat = TestRNGSeedFormat()

        val data = listOf(
            0L to "BBB-BBB-BBB",
            1751239825L to "BBL-FYL-DYC",
            1751830624431L to "RXY-YSU-VWR",  // System.currentTimeMillis() some time May 2025
            rngSeedFormat.maxBase2XValue to "ZZZ-ZZZ-ZZZ",
            0x101010101010101L to "AQE-BAQ-EBA-QE",
            0x4040404040404040L to "QEB-AQE-BAQ-EA",
            0x78A6B5C4D3E2F100L to "eKa-1xN-Pi8-QA",
            Long.MAX_VALUE to "f//-///-///-/8",
            Long.MIN_VALUE to "gAA-AAA-AAA-AA",
        )

        var fails = 0
        for ((value, expectedFormattedString) in data) {
            try {
                val actualFormattedString = rngSeedFormat.format(value)
                if (rngSeedFormat.parse(actualFormattedString) != value) {
                    println("UncivTextField.RNGSeed: value $value did not make the round-trip")
                    fails++
                }
                val plain = value.toString()
                if (rngSeedFormat.parse(plain) != value) {
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
        Assert.assertEquals("RNGSeedFormat should not fail", 0, fails)
    }

    @Test
    fun testLongStringHash() {
        val text = "Unciv is tremendous fun for the whole family!"
        val rngSeedFormat = TestRNGSeedFormat()
        val hash = rngSeedFormat.longHashCode(text)
        Assert.assertEquals(-7280134815231108590, hash)
    }
}
