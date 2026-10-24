package com.unciv.logic.map.pathingmap

import yairm210.purity.annotations.Pure

/**
 * ### Fixed-point movement value (base 30) for pathfinding, avoiding heap allocations.
 * 
 * This class represents movement distances using fixed-point arithmetic with a scaling
 * base of 30, meaning 1.0f movement = 30 internal units. This allows integer arithmetic
 * without floating-point overhead during A* pathfinding.
 *
 * #### Constraint:
 * FPM values are constrained to a few bits when packed into routing node structures
 * (see [RouteNode.pbmMoveThisTurn]: 9 bits, [RouteNode.moveUsedThisTurn]: 9 bits, [PrioritizedNode.underestimatedTotal]: 14 bits).
 * The internal `bits` field must fit within [0, 16384) for multi-turn movement costs, or [0, 512) for single-turn costs.
 * Valid multi-turn movement values therefore range from 0.0f to 546.1f, and single-turn ones from 0f to 17.03333f.
 *
 * #### Rounding:
 * Non-integer Float -> FixedPointMovement rounds to nearest 1/30th via HALF_UP.
 * FixedPointMovement -> Float holds the closest value that Float supports, which is *never* exact.
 * Ergo, FPM->Float->FPM round trips losslessly, but Float->FPM->Float will be rounded to a value 
 * extremely close to a multiple of 1/30th.
 *
 * @property bits The internal fixed-point representation (0 to 16384 exclusive).
 */
@JvmInline
value class FixedPointMovement private constructor(val bits: Int) {
    // Not all of these are currently used, but are present to ensure correctnes for future usage.
    // Operator coverage is complete for "fpm" on the left, but intentionally incomplete for Inf/Float on the left.
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
    operator fun times(other: FixedPointMovement) = FixedPointMovement((bits.toLong() * other.bits / MOVE_SPEED_BASE).toInt())
    @Pure
    operator fun times(multiplier: Int) = FixedPointMovement(bits * multiplier)
    @Pure
    operator fun times(multiplier: Float) = fpmFromMovement(bits * multiplier)
    @Pure
    operator fun div(other: FixedPointMovement) = FixedPointMovement((bits.toLong() * MOVE_SPEED_BASE / other.bits).toInt())
    @Pure
    operator fun div(divisor: Int) = FixedPointMovement(bits / divisor)
    @Pure
    operator fun div(divisor: Float) = fpmFromMovement(bits / divisor)
    @Pure
    operator fun compareTo(other: FixedPointMovement) = bits.compareTo(other.bits)
    @Pure
    operator fun compareTo(other: Int) = bits.compareTo((other * MOVE_SPEED_BASE))
    @Pure
    operator fun compareTo(other: Float) = compareTo(fpmFromMovement(other))

    @Pure
    fun toFloat() = bits / 30f
    @Pure
    fun coerceAtMost(max: FixedPointMovement) = FixedPointMovement(bits.coerceAtMost(max.bits))
    @Pure
    fun coerceAtLeast(min: FixedPointMovement) = FixedPointMovement(bits.coerceAtLeast(min.bits))
    @Pure
    fun coerceIn(min: FixedPointMovement, max: FixedPointMovement) = FixedPointMovement(bits.coerceIn(min.bits, max.bits))

    override fun toString() = "FPM{$bits/30=${toFloat()}}"
    
    companion object {
        private const val MOVE_SPEED_BASE = 30
        val FPM_ZERO = FixedPointMovement(0)
        val FPM_POINT_FIVE = FixedPointMovement(MOVE_SPEED_BASE/2)
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
    }
}
