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
