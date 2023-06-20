package com.unciv.ui.screens.pickerscreens.promotion

import com.unciv.GUI
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.translations.tr
import com.unciv.utils.Log

internal class PromotionTree(val unit: MapUnit) {
    val nodes: LinkedHashMap<String, PromotionNode>

    class PromotionNode(
        val promotion: Promotion,
        val isAdopted: Boolean
    ) {
        var depth = Int.MIN_VALUE
        var distanceToAdopted = Int.MAX_VALUE

        val parents = mutableSetOf<PromotionNode>()
        var preferredParent: PromotionNode? = null
        val children = mutableSetOf<PromotionNode>()

        var pathIsUnique = true
        var unreachable = false

        val isRoot get() = parents.isEmpty()

        override fun toString() = promotion.name
    }

    init {
        val collator = GUI.getSettings().getCollatorFromLocale()
        val rulesetPromotions = unit.civ.gameInfo.ruleset.unitPromotions.values
        val unitType = unit.baseUnit.unitType
        val adoptedPromotions = unit.promotions.promotions

        // The following sort is mostly redundant with our vanilla rulesets. Still, want to make sure
        // processing will be left to right, top to bottom.
        val possiblePromotions = rulesetPromotions.asSequence()
            .filter {
                unitType in it.unitTypes || it.name in adoptedPromotions
            }
            .toSortedSet(
                compareBy<Promotion> { if (it.row >= 0) it.column else Int.MAX_VALUE }
                    .thenBy { it.row }
                    .thenBy(collator) { it.name.tr() }
            )

        // Create incomplete node objects
        nodes = possiblePromotions.asSequence()
            .map { it.name to PromotionNode(it, it.name in adoptedPromotions) }
            .toMap(LinkedHashMap(possiblePromotions.size))

        // Fill parent/child relations, ignoring prerequisites not in possiblePromotions
        for (node in nodes.values) {
            for (prerequisite in node.promotion.prerequisites) {
                val parent = nodes[prerequisite] ?: continue
                if (node in allChildren(parent)) {
                    Log.debug("Ignoring circular reference: %s requires %s", node, parent)
                    continue
                }
                node.parents += parent
                parent.children += node
            }
        }

        // Determine unreachable / disabled nodes
        val state = StateForConditionals(unit.civ, unit = unit, tile = unit.getTile())
        for (node in nodes.values) {
            // defensive - I don't know how to provoke the situation, but if it ever occurs, disallow choosing that promotion
            if (node.promotion.prerequisites.isNotEmpty() && node.parents.isEmpty())
                node.unreachable = true
            if (node.promotion.getMatchingUniques(UniqueType.OnlyAvailableWhen, StateForConditionals.IgnoreConditionals)
                    .any { !it.conditionalsApply(state) })
                node.unreachable = true
        }

        // Calculate depth and distanceToAdopted - nonrecursively, shallows first.
        // Also determine preferredParent / pathIsUnique by weighing distanceToAdopted
        // Could be done in one nested loop, but - lazy
        for (node in allRoots()) {
            node.depth = 0
            node.distanceToAdopted = if (node.unreachable) Int.MAX_VALUE else if (node.isAdopted) 0 else 1
        }
        for (depth in 0..99) {
            var complete = true
            for (node in nodes.values) {
                if (node.depth == Int.MIN_VALUE) {
                    complete = false
                    continue
                }
                if (node.depth < depth) continue
                for (child in node.children) {
                    val distance = if (node.distanceToAdopted == Int.MAX_VALUE) Int.MAX_VALUE
                        else if (child.isAdopted) 0 else node.distanceToAdopted + 1
                    when {
                        child.depth == Int.MIN_VALUE -> Unit // "New" node / first reached
                        child.distanceToAdopted < distance -> continue  // Already reached a better way
                        child.distanceToAdopted == distance -> {  // Already reached same distance
                            child.pathIsUnique = false
                            continue
                        }
                        // else: Already reached, but a worse way - overwrite fully
                    }
                    child.depth = depth + 1
                    child.distanceToAdopted = distance
                    child.preferredParent = node
                    child.pathIsUnique = true
                }
            }
            if (complete) break
        }
    }

    fun allChildren(node: PromotionNode): Sequence<PromotionNode> {
        return sequenceOf(node) + node.children.flatMap { allChildren(it) }
    }

    fun allRoots() = nodes.values.asSequence().filter { it.isRoot }

    fun canBuyUpTo(promotion: Promotion): Boolean = unit.promotions.run {
        val node = nodes[promotion.name] ?: return false
        if (node.unreachable || node.distanceToAdopted == Int.MAX_VALUE) return false
        return XP >= xpForNextNPromotions(node.distanceToAdopted)
    }
}
