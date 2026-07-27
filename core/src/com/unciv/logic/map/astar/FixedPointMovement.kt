package com.unciv.logic.map.astar

import yairm210.purity.annotations.Pure

@JvmInline
value class FixedPointMovement private constructor(val bits: Int) {
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
    @Pure
    operator fun times(multiplier: Int) = FixedPointMovement(bits * multiplier)
    @Pure
    operator fun times(multiplier: Float) = fpmFromMovement(bits * multiplier)
    // #times(FixedPointMovement) is currently unused, but implemented here because I'm afraid
    // someone will implement it later, and forget the division.
    @Pure
    operator fun times(other: FixedPointMovement) = FixedPointMovement((bits.toLong() * other.bits / MOVE_SPEED_BASE).toInt())
    @Pure
    operator fun div(other: FixedPointMovement) = FixedPointMovement((bits.toLong() * other.bits * MOVE_SPEED_BASE).toInt())
    @Pure
    operator fun div(multiplier: Int) = FixedPointMovement(bits / multiplier)
    @Pure
    operator fun div(multiplier: Float) = fpmFromMovement(bits / multiplier)
    @Pure
    operator fun compareTo(other: FixedPointMovement) = bits.compareTo(other.bits)
    @Pure
    operator fun compareTo(other: Int) = bits.compareTo((other * MOVE_SPEED_BASE))

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
        fun fpmFromFixedPointBits(bits: Int) = FixedPointMovement(bits)
        @Pure
        fun fpmFromMovement(move: Int) = FixedPointMovement((move * MOVE_SPEED_BASE))
        @Pure
        fun fpmFromMovement(move: Float): FixedPointMovement { // rounding HALF_UP
            val plusOneBit = (move * (MOVE_SPEED_BASE * 2)).toInt()
            return FixedPointMovement((plusOneBit shr 1) + (plusOneBit and 1))
        }
        @Pure operator fun Int.minus(other: FixedPointMovement) = fpmFromMovement(this) - other
        @Pure fun Float.toFixedPointMove() = fpmFromMovement(this)
    }
}
