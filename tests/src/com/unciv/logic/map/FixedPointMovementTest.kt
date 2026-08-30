package com.unciv.logic.map

import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestCase
import com.unciv.testing.runTestParcours
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt


@RunWith(GdxTestRunner::class) // Note: Only benefit is support of our stdout redirection and MeasureDuration annotation
class FixedPointMovementTest {

    @Test
    fun testRoundtripIntegerMovements() {
        // All integer movements should roundtrip cleanly through Float
        runTestParcours(
            "Integer roundtrip: Int -> FPM -> Float -> Int",
            { movement: Int ->
                FixedPointMovement.fpmFromMovement(movement).toFloat().roundToInt()
            },
            0, 0,
            1, 1,
            5, 5,
            10, 10,
            50, 50,
            -1, -1,
            -5, -5,
            -50, -50
        )
    }

    @Test
    fun testRoundtripHalfMovements() {
        // Test roundtrip of .5 fractional movements (should be exact with HALF_UP)
        runTestParcours(
            "Half-unit roundtrip: 0.5 -> FPM -> Float preserves .5 fraction",
            { movement: Float ->
                FixedPointMovement.fpmFromMovement(movement).toFloat()
            },
            0.5f, 0.5f,
            1.5f, 1.5f,
            2.5f, 2.5f,
            5.5f, 5.5f,
            10.5f, 10.5f,
            -0.5f, -0.5f,
            -1.5f, -1.5f,
            -10.5f, -10.5f
        )
    }

    @Test
    fun testHALFUPRoundingEdgeCases() {
        // Test that HALF_UP rounds away from zero with values that fall exactly BETWEEN two representable fixed-point values
        runTestParcours("Test fpm HALF_UP rounding",
            { input: Float -> FixedPointMovement.fpmFromMovement(input).toFloat() },
            1.4166666f, 1.4333333f, // 1.41666... (= 42.5/30) is halfway between 42/30 and 43/30: Should round UP to 43/30 ≈ 1.4333
            0.48333332f, 0.5f,      // 0.48333... (= 14.5/30) is halfway between 14/30 and 15/30: Should round UP to 15/30 = 0.5
            0.4833333f, 0.46666667f  // Just below midpoint: should round DOWN
        )
    }

    @Test
    fun testArithmeticMaintainsRoundingForHalves() {
        // Verify that arithmetic operations on half-value FPMs work correctly
        val half = FixedPointMovement.fpmFromMovement(0.5f)
        val oneAndHalf = FixedPointMovement.fpmFromMovement(1.5f)

        // 0.5 + 1.5 = 2.0 exactly
        val sum = half + oneAndHalf
        Assert.assertEquals(2.0f, sum.toFloat(), 0.0001f)

        // 0.5 * 2 = 1.0
        val doubled = half * 2
        Assert.assertEquals(1.0f, doubled.toFloat(), 0.0001f)

        // 1.5 / 3 = 0.5
        val halved = oneAndHalf / 3
        Assert.assertEquals(0.5f, halved.toFloat(), 0.0001f)
    }

    @Test
    fun testComparisonWithFixedPointValues() {
        runTestParcours(
            "FPM compareTo FPM",
            { pair: Pair<Float, Float> ->
                val a = FixedPointMovement.fpmFromMovement(pair.first)
                val b = FixedPointMovement.fpmFromMovement(pair.second)
                a.compareTo(b)
            },
            0.5f to 0.5f, 0,
            1.0f to 0.5f, 1,
            0.5f to 1.0f, -1,
            -1.0f to 1.0f, -1,
            -1.0f to -0.5f, -1
        )
    }

    @Test
    fun testComparisonWithInt() {
        runTestParcours(
            "FPM compareTo Int",
            { pair: Pair<Float, Int> ->
                val fpm = FixedPointMovement.fpmFromMovement(pair.first)
                fpm.compareTo(pair.second)
            },
            0.5f to 0, 1,      // 0.5 > 0
            1.0f to 1, 0,      // 1.0 == 1
            0.5f to 1, -1,     // 0.5 < 1
            2.0f to 1, 1       // 2.0 > 1
        )
    }

    @Test
    fun testComparisonWithFloat() {
        runTestParcours(
            "FPM compareTo Float",
            { pair: Pair<Float, Float> ->
                val fpm = FixedPointMovement.fpmFromMovement(pair.first)
                fpm.compareTo(pair.second)
            },
            0.5f to 0.5f, 0,
            1.0f to 0.5f, 1,
            0.5f to 1.0f, -1,
            -0.5f to -0.5f, 0
        )
    }

    @Test
    fun testOperatorPlusPreservesValues() {
        runTestParcours(
            "FPM + various types",
            { (input: Float, other: Any): Pair<Float, Any> ->
                val fpm = FixedPointMovement.fpmFromMovement(input)
                when (other) {
                    is Int -> fpm + other
                    is Float -> fpm + other
                    is FixedPointMovement -> fpm + other
                    else -> error("Unknown type")
                }.toFloat()
            },
            1.0f to 1, 2.0f,
            0.5f to 0.5f, 1.0f,
            1.5f to 1, 2.5f,
            4.2f to FixedPointMovement.fpmFromMovement(4.8f), 9f
        )
    }

    @Test
    fun testCoercionMethods() {
        val min = FixedPointMovement.fpmFromMovement(1.0f)
        val max = FixedPointMovement.fpmFromMovement(5.0f)
        val value = FixedPointMovement.fpmFromMovement(3.0f)

        Assert.assertEquals(3.0f, value.coerceIn(min, max).toFloat(), 0.0001f)
        Assert.assertEquals(1.0f, FixedPointMovement.fpmFromMovement(0.5f).coerceAtLeast(min).toFloat(), 0.0001f)
        Assert.assertEquals(5.0f, FixedPointMovement.fpmFromMovement(6.0f).coerceAtMost(max).toFloat(), 0.0001f)
    }

    @Test
    fun testDivisionKnownValues() = runTestParcours(
        "FPM / FPM",
        { (dividend: Float, divisor: Float): Pair<Float, Float> ->
            (FixedPointMovement.fpmFromMovement(dividend) / FixedPointMovement.fpmFromMovement(divisor)).toFloat()
        },
        // Integer fixed-point truncates some quotients; only assert pairs that stay exact.
        1.0f to 2.0f, 0.5f,
        2.0f to 2.0f, 1.0f,
        3.0f to 2.0f, 1.5f,
        0.1f to 3.0f, 0.033333335f
    )

    @Test
    fun testMultiplicationKnownValues() = runTestParcours(
        "FPM * FPM",
        { (a: Float, b: Float): Pair<Float, Float> ->
            (FixedPointMovement.fpmFromMovement(a) * FixedPointMovement.fpmFromMovement(b)).toFloat()
        },
        1.0f to 2.0f, 2.0f,
        //1.5f to 1.5f, 2.25f, Nope: That's not exactly representable as FPM
        1.5f to 1.6666667f, 2.5f,
        6.0f to 7.0f, 42.0f,
        0.03333333f to 3f, 0.1f
    )

    @Test
    fun testRepresentativePrimeBitsFloatRoundtrip() {
        // FPM is squeezed into max. 14 bits in routing nodes; test representatives within that range.
        val parcours =
            intArrayOf(2, 3, 5, 7, 11, 13, 29, 31, 61, 127, 257, 509, 1021, 2047, 4093, 8191)
                .map { TestCase(FixedPointMovement.fpmFromFixedPointBits(it), it) }
                .toTypedArray()
        runTestParcours("FPM with representative bit patterns", *parcours) {
            FixedPointMovement.fpmFromMovement(it.toFloat()).bits
        }
    }
}
