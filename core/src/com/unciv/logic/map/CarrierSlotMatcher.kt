package com.unciv.logic.map

import com.unciv.logic.map.mapunit.MapUnit
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly

/**
 * Bipartite matching helper for carrier slot assignment.
 *
 * Guarantees optimal allocation of carried units to slot rules,
 * returning the maximum capacity available for a new unit.
 *
 * Time complexity: O(S * C), where S = total slots, C = carried units
 * (polynomial, not exponential)
 */
object CarrierSlotMatcher {

    data class SlotRule(
        val filter: String,
        val capacity: Int
    )

    private sealed class BipartiteMatchingResult {
        class Success(val allocations: Collection<Int>) : BipartiteMatchingResult()
        class Failure(val overAllocation: Int) : BipartiteMatchingResult()
    }

    @Readonly
    /**
     * Computes maximum available slots for [newUnit] given existing assignments.
     *
     * @param slotRules The carrier's [slot rules][SlotRule], (e.g., "Fighter" cap 1, "all" cap 2). Duplicates of filters are no problem and not even a performance penalty.
     * @param carriedUnits Units already carried by this carrier
     * @param newUnit The unit we want to fit
     * @return Number of available slots for newUnit (negative if carrier is overcapacity, counting number of units that would need to be removed to conform to the ruleset)
     */
    fun availableCapacity(
        slotRules: Collection<SlotRule>,
        carriedUnits: Sequence<MapUnit>,
        newUnit: MapUnit
    ): Int {
        if (slotRules.isEmpty()) return 0

        // Expand rules into individual slot instances
        @LocalState
        val slots = arrayListOf<String>() // filter string for each slot
        for (rule in slotRules) {
            repeat(rule.capacity) { slots.add(rule.filter) }
        }

        // Find optimal assignment of carried units including the new unit to slots
        @LocalState
        val allUnits = arrayListOf(newUnit)
        allUnits.addAll(carriedUnits)

        return when(val allocationResult = maxBipartiteMatching(slots, allUnits)) {
            is BipartiteMatchingResult.Failure -> {
                -allocationResult.overAllocation
            }
            is BipartiteMatchingResult.Success -> {
                // Count unmatched slots that newUnit can fill
                val unassignedSlotIndices = (slots.indices).filterNot { it in allocationResult.allocations }
                val availableSlots = unassignedSlotIndices.count { slotIdx ->
                    newUnit.matchesFilter(slots[slotIdx])
                }
                availableSlots + 1 // newUnit was allocated, count that slot too
            }
        }
    }

    /**
     * Maximum bipartite matching using augmenting paths (DFS-based).
     *
     * Assigns units to slots such that:
     * - Each unit gets at most one slot
     * - Each slot gets at most one unit
     * - Matching size is maximized
     *
     * @return [BipartiteMatchingResult.Success]: Map of [unitIndex -> slotIndex]
     *         [BipartiteMatchingResult.Failure]: Overallocation count (0: no place for new unit, 1: there's even one too many already loaded,...)
     */
    @Readonly
    private fun maxBipartiteMatching(
        slots: ArrayList<String>,
        units: ArrayList<MapUnit>
    ): BipartiteMatchingResult {
        @LocalState
        val unitToSlot = mutableMapOf<Int, Int>()  // unit index -> slot index
        @LocalState
        val slotUsed = BooleanArray(slots.size)

        fun countOverAllocation(): Int {
            if (unitToSlot.isEmpty()) return 0
            val slot = slots[unitToSlot[0]!!]
            return units.indices
                .filterNot { it in unitToSlot }
                .count { units[it].matchesFilter(slot) } - 1 // The -1 again because newUnit is included
        }

        // Process each unit, trying to assign it to a slot
        for (unitIdx in units.indices) {
            @LocalState
            val visited = BooleanArray(slots.size)
            if (!dfsAugmentingPath(
                unitIdx, units, slots,
                slotUsed, visited, unitToSlot
            )) return BipartiteMatchingResult.Failure(countOverAllocation())
        }

        return BipartiteMatchingResult.Success(unitToSlot.values)
    }

    @Readonly
    /**
     * DFS to find an augmenting path for a unit.
     *
     * Either assigns the unit to a free slot, or recursively reassigns 
     * the current occupant of a slot and takes it.
     */
    private fun dfsAugmentingPath(
        unitIdx: Int,
        units: ArrayList<MapUnit>,
        slots: ArrayList<String>,
        @LocalState
        slotUsed: BooleanArray,
        @LocalState
        visited: BooleanArray,
        @LocalState
        unitToSlot: MutableMap<Int, Int>
    ): Boolean {
        @LocalState
        val unit = units[unitIdx]

        // Try each slot that matches this unit's properties
        for (slotIdx in slots.indices) {
            if (visited[slotIdx] || !unit.matchesFilter(slots[slotIdx])) continue
            visited[slotIdx] = true

            // Case 1: Slot is free → assign unit directly
            if (!slotUsed[slotIdx]) {
                unitToSlot[unitIdx] = slotIdx
                slotUsed[slotIdx] = true
                return true
            }

            // Case 2: Slot is occupied → try to recursively reassign its current occupant
            @LocalState
            val currentOccupant = unitToSlot.entries.find { it.value == slotIdx }?.key
            if (currentOccupant != null) {
                if (dfsAugmentingPath(
                        currentOccupant, units, slots,
                        slotUsed, visited, unitToSlot
                    )) {
                    unitToSlot[unitIdx] = slotIdx
                    return true
                }
            }
        }

        return false
    }
}
