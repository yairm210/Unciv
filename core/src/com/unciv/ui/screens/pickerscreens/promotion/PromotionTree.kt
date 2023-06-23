package com.unciv.ui.screens.pickerscreens.promotion

import com.unciv.GUI
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.translations.tr
import com.unciv.utils.Log
import java.util.SortedSet

internal class PromotionTree(val unit: MapUnit) {
    val nodes: LinkedHashMap<String, PromotionNode>
    val possiblePromotions: SortedSet<Promotion>

    class PromotionNode(
        val promotion: Promotion,
        val isAdopted: Boolean
    ) {
        /** How many prerequisite steps are needed to reach a [isRoot] promotion */
        var depth = Int.MIN_VALUE
        /** How many unit-promoting steps are needed to reach this node */
        var distanceToAdopted = Int.MAX_VALUE

        /** The nodes for direct prerequisites of this one
         *  Note this is not necessarily cover all prerequisites of the node's promotion - see [unreachable] */
        val parents = mutableSetOf<PromotionNode>()
        /** Follow this to get an unambiguous path to a root */
        var preferredParent: PromotionNode? = null
        /** All nodes having this one as direct prerequisite */
        val children = mutableSetOf<PromotionNode>()

        /** On if there is only one "best" path of equal cost to adopt this node's promotion */
        var pathIsUnique = true
        /** On for promotions having unavailable prerequisites (missing in ruleset, or not allowed for the unit's
         *  UnitType, and not already adopted either); or currently disabled by a [UniqueType.OnlyAvailableWhen] unique.
         *  (should never be on with a vanilla ruleset) */
        var unreachable = false

        /** Name of this node's promotion with [level] suffixes removed, and [] brackets removed */
        val baseName: String
        /** "Level" of this node's promotion (e.g. Drill I: 1, Drill III: 3 - 0 for promotions without such a suffix) */
        val level: Int

        /** `true` if this node's promotion has no prerequisites */
        val isRoot get() = parents.isEmpty()

        override fun toString() = promotion.name

        init {
            val (_, level, basePromotionName) = Promotion.getBaseNameAndLevel(promotion.name)
            this.level = level
            this.baseName = basePromotionName
        }
    }

    init {
        val collator = GUI.getSettings().getCollatorFromLocale()
        val rulesetPromotions = unit.civ.gameInfo.ruleset.unitPromotions.values
        val unitType = unit.baseUnit.unitType
        val adoptedPromotions = unit.promotions.promotions

        // The following sort is mostly redundant with our vanilla rulesets.
        // Still, want to make sure processing will be left to right, top to bottom.
        possiblePromotions = rulesetPromotions.asSequence()
            .filter {
                unitType in it.unitTypes || it.name in adoptedPromotions
            }
            .toSortedSet(
                // Remember to make sure row=0/col=0 stays on top while those without explicit pos go to the end
                // But: the latter did not keep their definition!
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
                if (node.depth != depth) continue
                for (child in node.children) {
                    val distance = if (node.distanceToAdopted == Int.MAX_VALUE) Int.MAX_VALUE
                        else if (child.isAdopted) 0 else node.distanceToAdopted + 1
                    when {
                        child.depth == Int.MIN_VALUE -> Unit // "New" node / first reached
                        child.distanceToAdopted < distance -> continue  // Already reached a better way
                        child.distanceToAdopted == distance -> {  // Already reached same distance
                            child.pathIsUnique = false
                            child.preferredParent = null
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

    private fun getReachableNode(promotion: Promotion): PromotionNode? =
        nodes[promotion.name]?.takeUnless { it.distanceToAdopted == Int.MAX_VALUE }

    fun canBuyUpTo(promotion: Promotion): Boolean = unit.promotions.run {
        val node = getReachableNode(promotion) ?: return false
        if (node.isAdopted) return false
        return XP >= xpForNextNPromotions(node.distanceToAdopted)
    }

    fun getPathTo(promotion: Promotion): List<Promotion> {
        var node = getReachableNode(promotion) ?: return emptyList()
        val result = mutableListOf(node.promotion)
        while (true) {
            node = node.preferredParent ?: break
            if (node.isAdopted) break
            result.add(node.promotion)
        }
        return result.asReversed()
    }

    // These exist to allow future optimization - this is safe, but far more than actually needed
    fun getMaxRows() = nodes.size
    fun getMaxColumns() = nodes.size
}
