package com.unciv.logic.map.astar

import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.Pure
import kotlin.math.roundToInt

/**
 * ### Fixed-point movement value (base 30) for pathfinding, avoiding heap allocations.
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
 * #### Roundtrip Guarantee:
 * Values that are multiples of 1/30 convert cleanly to Float
 * and back via [fpmFromMovement] and [toFloat]. Values between multiples round via HALF_UP.
 *
 * @property bits The internal fixed-point representation (0 to 16384 exclusive).
 */
@JvmInline
value class FixedPointMovement private constructor(val bits: Int) : Comparable<FixedPointMovement> {
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

    /** Debug visualization only */
    override fun toString(): String {
        val whole = bits / MOVE_SPEED_BASE
        val frac = bits % MOVE_SPEED_BASE
        return if (frac == 0) whole.toString() else "$whole+$frac/30"
    }

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
