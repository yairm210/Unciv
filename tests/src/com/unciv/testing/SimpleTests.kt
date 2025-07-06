package com.unciv.testing

import com.unciv.ui.components.formatting.RNGSeedFormat
import com.unciv.ui.components.formatting.RNGSeedFormat.hashCode64
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
            0L to "BBB-BBB-BBB",
            Long.MAX_VALUE to "f//-///-///-/8",
            Long.MIN_VALUE to "gAA-AAA-AAA-AA",
            1751239825L to "BBZ-LVS-KKR",
            1751830624431L to "AAA-Bl+-E9+-K8",
            0x101010101010101L to "AQE-BAQ-EBA-QE",
            0x4040404040404040L to "QEB-AQE-BAQ-EA",
            0x78A6B5C4D3E2F100L to "eKa-1xN-Pi8-QA",
            RNGSeedFormat.maxBase21Value to "ZZZ-ZZZ-ZZZ",
        )
        var fails = 0
        for ((value, expectedFormattedString) in data) {
            try {
                val actualFormattedString = RNGSeedFormat.unitTestFormat(value)
                if (RNGSeedFormat.parse(actualFormattedString) != value) {
                    println("UncivTextField.RNGSeed: value $value did not make the round-trip")
                    fails++
                }
                val plain = value.toString()
                if (RNGSeedFormat.parse(plain) != value) {
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
        val hash = text.hashCode64()
        Assert.assertEquals(-7280134815231108590, hash)
    }
}
