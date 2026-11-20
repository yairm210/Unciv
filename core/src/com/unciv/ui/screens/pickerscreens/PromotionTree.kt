package com.unciv.ui.screens.pickerscreens

import com.unciv.GUI
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.AvailablePromotion
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.translations.tr
import com.unciv.view.MapUnitView

class PromotionTree(
    private val availablePromotions: List<AvailablePromotion>,
    /** Whether the unit has enough XP to buy the next `count` non-free promotions - queried live */
    private val canAffordPromotions: (count: Int) -> Boolean
) {
    constructor(unit: MapUnit) : this(unit.promotions.getPromotionTreeCandidates(), unit.promotions::canAffordPromotions)
    constructor(unit: MapUnitView) : this(unit.getAvailablePromotions(), unit::canAffordPromotions)

    /** Ordered set of Promotions to show - by Json column/row and translated name */
    // Not using SortedSet - that uses needlessly complex implementations that remember the comparator
    lateinit var possiblePromotions: LinkedHashSet<Promotion>
    /** Ordered map, key is the Promotion name, same order as [possiblePromotions] */
    private lateinit var nodes: LinkedHashMap<String, PromotionNode>

    class PromotionNode(
        val promotion: Promotion,
        val isAdopted: Boolean
    ) {
        /** How many prerequisite steps are needed to reach a [isRoot] promotion */
        var depth = Int.MIN_VALUE
        /** How many unit-promoting steps are needed to reach this node */
        var distanceToAdopted = Int.MAX_VALUE

        /** The nodes for direct prerequisites of this one (unordered)
         *  Note this is not necessarily cover all prerequisites of the node's promotion - see [unreachable] */
        val parents = mutableSetOf<PromotionNode>()
        /** Follow this to get an unambiguous path to a root */
        var preferredParent: PromotionNode? = null
        /** All nodes having this one as direct prerequisite - must preserve order as UI uses it */
        val children = linkedSetOf<PromotionNode>()

        /** Off if there is only one "best" path of equal cost to adopt this node's promotion */
        var pathIsAmbiguous = false
        /** On for promotions having unavailable prerequisites (missing in ruleset, or not allowed for the unit's
         *  UnitType, and not already adopted either); or currently disabled by a [UniqueType.OnlyAvailable] unique.
         *  (should never be on with a vanilla ruleset) */
        var unreachable = false

        /** Name of this node's promotion with [level] suffixes removed, and [] brackets removed */
        val baseName: String
        /** "Level" of this node's promotion (e.g. Drill I: 1, Drill III: 3 - 0 for promotions without such a suffix) */
        val level: Int
        /** How many levels of this promotion there are below (including this), minimum 1 (Drill I: 3 / Drill III: 1) */
        var levels = 1

        /** `true` if this node's promotion has no prerequisites */
        val isRoot get() = parents.isEmpty()

        override fun toString() = promotion.name

        init {
            val splitName = Promotion.getBaseNameAndLevel(promotion.name)
            this.level = splitName.level
            this.baseName = splitName.basePromotionName
        }
    }

    init {
        update()
    }

    fun update() {
        val collator = GUI.getSettings().getCollatorFromLocale()

        // The following sort is mostly redundant with our vanilla rulesets.
        // Still, want to make sure processing left to right, top to bottom will be usable.
        possiblePromotions = availablePromotions.asSequence()
            .map { it.promotion }
            .sortedWith(
                // Remember to make sure row=0/col=0 stays on top while those without explicit pos go to the end
                // Also remember the names are historical, row means column on our current screen design.
                compareBy<Promotion> {
                    if (it.row < 0) Int.MAX_VALUE
                    else if (it.row == 0) Int.MIN_VALUE + it.column
                    else it.column
                }
                .thenBy { it.row }
                .thenBy(collator) { it.name.tr(hideIcons = true) }
            )
            .toCollection(linkedSetOf())

        val adopted = availablePromotions.filter { it.isAdopted }.map { it.promotion.name }.toSet()
        val blockedByUniques = availablePromotions.filter { it.isBlockedByUniques }.map { it.promotion.name }.toSet()

        // Create incomplete node objects
        nodes = possiblePromotions.asSequence()
            .map { it.name to PromotionNode(it, it.name in adopted) }
            .toMap(LinkedHashMap(possiblePromotions.size))

        // Fill parent/child relations, ignoring prerequisites not in possiblePromotions
        for (node in nodes.values) {
            if (detectLoop(node)) continue
            for (prerequisite in node.promotion.prerequisites) {
                val parent = nodes[prerequisite] ?: continue
                node.parents += parent
                parent.children += node
                if (node.level > 0 && node.baseName == parent.baseName)
                    parent.levels++
            }
        }

        // Determine unreachable / disabled nodes
        for (node in nodes.values) {
            // defensive - I don't know how to provoke the situation, but if it ever occurs, disallow choosing that promotion
            if (node.promotion.prerequisites.isNotEmpty() && node.parents.isEmpty())
                node.unreachable = true

            if (node.promotion.name in blockedByUniques) node.unreachable = true
        }

        // Calculate depth and distanceToAdopted - nonrecursively, shallows first.
        // Also determine preferredParent / pathIsAmbiguous by weighing distanceToAdopted
        for (node in allRoots()) {
            node.depth = 0
            node.distanceToAdopted = if (node.isAdopted) 0
                else if (node.unreachable) Int.MAX_VALUE else 1
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
                    val distance = if (child.isAdopted) 0
                        else if (node.distanceToAdopted == Int.MAX_VALUE) Int.MAX_VALUE else if (child.unreachable) Int.MAX_VALUE
                        else node.distanceToAdopted + 1
                    when {
                        child.depth == Int.MIN_VALUE -> Unit // "New" node / first reached
                        child.distanceToAdopted < distance -> continue  // Already reached a better way
                        child.distanceToAdopted == distance -> {  // Already reached same distance
                            child.pathIsAmbiguous = true
                            child.preferredParent = null
                            continue
                        }
                        // else: Already reached, but a worse way - overwrite fully
                    }
                    child.depth = depth + 1
                    child.distanceToAdopted = distance
                    child.pathIsAmbiguous = node.pathIsAmbiguous
                    child.preferredParent = node.takeUnless { node.pathIsAmbiguous }
                }
            }
            if (complete) break
        }
    }

    fun allNodes() = nodes.values.asSequence()
    fun allRoots() = allNodes().filter { it.isRoot }

    private fun detectLoop(node: PromotionNode): Boolean {
        val loopCheck = HashSet<PromotionNode>(nodes.size)
        fun detectRecursive(node: PromotionNode, level: Int, loopCheck: HashSet<PromotionNode>): Boolean {
            if (level > 99) return true
            if (node in loopCheck) return true
            loopCheck.add(node)
            for (parent in node.parents) {
                if (detectRecursive(parent, level + 1, loopCheck)) return true
            }
            return false
        }
        return detectRecursive(node, 0, loopCheck)
    }

    fun getNode(promotion: Promotion): PromotionNode? = nodes[promotion.name]

    private fun getReachableNode(promotion: Promotion): PromotionNode? =
        nodes[promotion.name]?.takeUnless { it.distanceToAdopted == Int.MAX_VALUE }

    fun canBuyUpTo(promotion: Promotion): Boolean {
        val node = getReachableNode(promotion) ?: return false
        if (node.isAdopted) return false
        // Free promotions don't consume XP; only count non-free promotions in the path
        val nonFreeCount = getPathTo(promotion).count { !it.hasUnique(UniqueType.FreePromotion) }
        return canAffordPromotions(nonFreeCount)
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

    // These exist to allow future optimization - this is safe, but more than actually needed
    fun getMaxRows() = nodes.size
    fun getMaxColumns() = nodes.values.maxOfOrNull {
            it.promotion.row.coerceAtLeast(it.depth) + it.levels
        } ?: 0 // nodes can be empty (civilians with statuses)
}
