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

    @Readonly
    /**
     * Computes maximum available slots for [newUnit] given existing assignments.
     *
     * @param slotRules The carrier's slot rules (e.g., "Fighter" cap 1, "all" cap 2)
     * @param carriedUnits Units already carried by this carrier
     * @param newUnit The unit we want to fit
     * @return Number of available slots for newUnit (-1 if system is overcapacity)
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

        // Sanity check: if we're overcapacity, flag it
        if (carriedUnits.count() > slots.size) {
            return -1  // System is already overcapacity; this shouldn't happen in normal play
        }

        // Find optimal assignment of carried units including the new unit to slots
        @LocalState
        val allUnits = arrayListOf(newUnit)
        allUnits.addAll(carriedUnits)
        @LocalState
        val slotAssignment = maxBipartiteMatching(slots, allUnits)
            ?: return 0

        // Count unmatched slots that newUnit can fill
        @LocalState
        val unassignedSlotIndices = (slots.indices).filterNot { it in slotAssignment.values }
        @LocalState
        val availableSlots = unassignedSlotIndices.count { slotIdx ->
            newUnit.matchesFilter(slots[slotIdx])
        }

        return availableSlots + 1
    }

    /**
     * Maximum bipartite matching using augmenting paths (DFS-based).
     *
     * Assigns units to slots such that:
     * - Each unit gets at most one slot
     * - Each slot gets at most one unit
     * - Matching size is maximized
     *
     * @return Map of [unitIndex -> slotIndex]
     */
    @Readonly
    private fun maxBipartiteMatching(
        slots: ArrayList<String>,
        units: ArrayList<MapUnit>
    ): Map<Int, Int>? {
        @LocalState
        val unitToSlot = mutableMapOf<Int, Int>()  // unit index -> slot index
        @LocalState
        val slotUsed = BooleanArray(slots.size)

        // Process each unit, trying to assign it to a slot
        for (unitIdx in units.indices) {
            @LocalState
            val visited = BooleanArray(slots.size)
            if (!dfsAugmentingPath(
                unitIdx, units, slots,
                slotUsed, visited, unitToSlot
            )) return null
        }

        return unitToSlot
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
