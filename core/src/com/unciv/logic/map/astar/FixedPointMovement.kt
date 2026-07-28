package com.unciv.logic.map.astar

import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.Pure
import kotlin.math.roundToInt

@JvmInline
value class FixedPointMovement private constructor(val bits: Int) : Comparable<FixedPointMovement> {
    @Pure
    operator fun plus(other: FixedPointMovement) = FixedPointMovement(bits + other.bits)
    @Pure
    operator fun plus(value: Int) = FixedPointMovement(bits + value * MOVE_SPEED_BASE)
    @Pure
    operator fun plus(value: Float) = FixedPointMovement(bits + fpmFromMovement(value).bits)
    @Pure
    operator fun minus(other: FixedPointMovement) = FixedPointMovement(bits - other.bits)
    @Pure
    operator fun minus(value: Int) = FixedPointMovement(bits - value * MOVE_SPEED_BASE)
    @Pure
    operator fun minus(value: Float) = FixedPointMovement(bits - fpmFromMovement(value).bits)
    // #times(FixedPointMovement) is currently unused, but implemented here because I'm afraid
    // someone will implement it later, and forget the division.
    @Pure
    operator fun times(other: FixedPointMovement) = FixedPointMovement((bits.toLong() * other.bits / MOVE_SPEED_BASE).toInt())
    @Pure
    operator fun times(multiplier: Int) = FixedPointMovement(bits * multiplier)
    @Pure
    operator fun times(multiplier: Float) = FixedPointMovement((bits * multiplier).roundToInt())
    @Pure
    operator fun div(other: FixedPointMovement) = FixedPointMovement((bits.toLong() * MOVE_SPEED_BASE / other.bits).toInt())
    @Pure
    operator fun div(divisor: Int) = FixedPointMovement(bits / divisor)
    @Pure
    operator fun div(divisor: Float) = FixedPointMovement((bits / divisor).roundToInt())
    @Pure
    override operator fun compareTo(other: FixedPointMovement) = bits.compareTo(other.bits)
    @Pure
    operator fun compareTo(other: Int) = bits.compareTo(other * MOVE_SPEED_BASE)
    @Pure
    operator fun compareTo(other: Float) = bits.compareTo(fpmFromMovement(other).bits)

    @Pure
    fun toFloat() = bits / 30f
    @Pure
    fun coerceAtMost(max: FixedPointMovement) = FixedPointMovement(bits.coerceAtMost(max.bits))
    @Pure
    fun coerceAtLeast(min: FixedPointMovement) = FixedPointMovement(bits.coerceAtLeast(min.bits))
    @Pure
    fun coerceIn(min: FixedPointMovement, max: FixedPointMovement) = FixedPointMovement(bits.coerceIn(min.bits, max.bits))

    override fun toString() = toFloat().toString()

    companion object {
        private const val MOVE_SPEED_BASE = 30
        val FPM_ZERO = FixedPointMovement(0)
        val FPM_POINT_FIVE = FixedPointMovement(MOVE_SPEED_BASE / 2)
        val FPM_ONE = FixedPointMovement(MOVE_SPEED_BASE)

        @Pure
        @VisibleForTesting
        fun fpmFromFixedPointBits(bits: Int) = FixedPointMovement(bits)
        @Pure
        @VisibleForTesting
        fun fpmFromMovement(move: Int) = FixedPointMovement(move * MOVE_SPEED_BASE)
        @Pure
        fun fpmFromMovement(move: Float): FixedPointMovement { // rounding HALF_UP
            val plusOneBit = (move * (MOVE_SPEED_BASE * 2)).toInt()
            return FixedPointMovement((plusOneBit shr 1) + (plusOneBit and 1))
        }
        @Pure operator fun Int.minus(other: FixedPointMovement) = fpmFromMovement(this) - other
        @Pure internal fun Float.toFixedPointMove() = fpmFromMovement(this)
    }
}
