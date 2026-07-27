package com.unciv.logic.map.astar

import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

// This crams all the information we need about prioritizing a node into a single Long, avoiding allocations
@JvmInline
@VisibleForTesting
value class PrioritizedNode(val bits: Long) {
    constructor(node: RouteNode, underestimatedTotal: FixedPointMovement)
        : this(
        (node.bits and RouteNode.UNDERESTIMATED_TOTAL_HI_MASK.inv()) or
            toUnderestimatedTotalbits(underestimatedTotal)
    ) {
        require(underestimatedTotal > 0) { "underestimatedTotal $underestimatedTotal must be positive" }
        require(underestimatedTotal <= RouteNode.MAX_UNDERESTIMATED_TOTAL) { "underestimatedTotal $underestimatedTotal exceeds max ${RouteNode.MAX_UNDERESTIMATED_TOTAL}" }
    }

    val tileIdx: Int get() { require(initialized); return ((bits shr RouteNode.TILE_IDX_OFFSET) and RouteNode.TILE_IDX_LO_MASK).toInt() }

    val underestimatedTotal: FixedPointMovement get() {
        val b = ((bits shr RouteNode.UNDERESTIMATED_TOTAL_OFFSET) and RouteNode.UNDERESTIMATED_TOTAL_LO_MASK)
        return FixedPointMovement.fpmFromFixedPointBits(b.toInt())
    }

    val initialized: Boolean get() = bits > 0

    @Readonly
    override fun toString(): String = "PrioritizedNode[underestimatedTotal=$underestimatedTotal ${RouteNode(bits)}]"

    companion object {
        @Pure
        private fun toUnderestimatedTotalbits(priority: FixedPointMovement): Long
            = priority.bits.toLong() shl RouteNode.UNDERESTIMATED_TOTAL_OFFSET
    }
}
