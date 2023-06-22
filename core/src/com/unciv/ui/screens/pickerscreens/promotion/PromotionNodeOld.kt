package com.unciv.ui.screens.pickerscreens.promotion

import com.unciv.models.ruleset.unit.Promotion

internal class PromotionNodeOld(val promotion: Promotion) {
    var maxDepth = 0

    /** How many level this promotion has */
    var levels = 1

    val successors: ArrayList<PromotionNodeOld> = ArrayList()
    val predecessors: ArrayList<PromotionNodeOld> = ArrayList()

    val baseName = getBasePromotionName()

    fun isRoot() : Boolean {
        return predecessors.isEmpty()
    }

    fun calculateDepth(excludeNodes: ArrayList<PromotionNodeOld>, currentDepth: Int) {
        maxDepth = Integer.max(maxDepth, currentDepth)
        excludeNodes.add(this)
        successors.filter { !excludeNodes.contains(it) }.forEach { it.calculateDepth(excludeNodes,currentDepth+1) }
    }

    private fun getBasePromotionName(): String {
        val nameWithoutBrackets = promotion.name.replace("[", "").replace("]", "")
        val level = when {
            nameWithoutBrackets.endsWith(" I") -> 1
            nameWithoutBrackets.endsWith(" II") -> 2
            nameWithoutBrackets.endsWith(" III") -> 3
            else -> 0
        }
        return nameWithoutBrackets.dropLast(if (level == 0) 0 else level + 1)
    }

    class CustomComparator(
        private val baseNode: PromotionNodeOld
    ) : Comparator<PromotionNodeOld> {
        override fun compare(a: PromotionNodeOld, b: PromotionNodeOld): Int {
            val baseName = baseNode.baseName
            val aName = a.baseName
            val bName = b.baseName
            return when (aName) {
                baseName -> -1
                bName -> 0
                else -> 1
            }
        }
    }

}


/*
        val unitType = unit.type
        val promotionsForUnitType = unit.civ.gameInfo.ruleset.unitPromotions.values.filter {
            it.unitTypes.contains(unitType.name) || unit.promotions.promotions.contains(it.name)
        }

        val map = LinkedHashMap<String, PromotionNodeOld>()

        val availablePromotions = unit.promotions.getAvailablePromotions().toSet()  // toSet because we get a Sequence, and it's checked repeatedly with contains()

        // Create nodes
        // Pass 1 - create nodes for all promotions
        for (promotion in promotionsForUnitType)
            map[promotion.name] = PromotionNodeOld(promotion)

        // Pass 2 - remove nodes which are unreachable (dependent only on absent promotions)
        for (promotion in promotionsForUnitType) {
            if (promotion.prerequisites.isNotEmpty()) {
                val isReachable = promotion.prerequisites.any { map.containsKey(it) }
                if (!isReachable)
                    map.remove(promotion.name)
            }
        }

        // Pass 3 - fill nodes successors/predecessors, based on promotions prerequisites
        for (node in map.values) {
            for (prerequisiteName in node.promotion.prerequisites) {
                val prerequisiteNode = map[prerequisiteName] ?: continue
                node.predecessors.add(prerequisiteNode)
                prerequisiteNode.successors.add(node)
                // Prerequisite has the same base name -> +1 more level
                if (prerequisiteNode.baseName == node.baseName)
                    prerequisiteNode.levels += 1
            }
        }

        // Traverse each root node tree and calculate max possible depths of each node
        for (node in map.values) {
            if (node.isRoot())
                node.calculateDepth(arrayListOf(node), 0)
        }

        // For each non-root node remove all predecessors except the one with the least max depth.
        // This is needed to compactify trees and remove circular dependencies (A -> B -> C -> A)
        for (node in map.values) {
            if (node.isRoot())
                continue

            // Choose best predecessor - the one with less depth
            var best: PromotionNodeOld? = null
            for (predecessor in node.predecessors) {
                if (best == null || predecessor.maxDepth < best.maxDepth)
                    best = predecessor
            }

            // Remove everything else, leave only best
            for (predecessor in node.predecessors)
                predecessor.successors.remove(node)
            node.predecessors.clear()
            node.predecessors.add(best!!)
            best.successors.add(node)
        }

        // Sort nodes successors so promotions with same base name go first
        for (node in map.values) {
            node.successors.sortWith(PromotionNodeOld.CustomComparator(node))
        }
 */
