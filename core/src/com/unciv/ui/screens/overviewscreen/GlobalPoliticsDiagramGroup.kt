package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.HexMath
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.getContrastRatio
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.input.ClickableCircle
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ShadowedLabel
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.AnimatedMenuPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import kotlin.collections.set

/** This is the 'spider net'-like polygon showing one line per civ-civ relation
 *  @param undefeatedCivs Civs to display - note the viewing player is always included, so it's possible the name is off and there's a dead civ included.
 *  @param freeSize Width and height this [Group] sizes itself to
 */
class GlobalPoliticsDiagramGroup(
    undefeatedCivs: List<Civilization>,
    freeSize: Float
): Group() {
    @Suppress("ConstPropertyName")
    companion object {
        const val iconSize = 30f
        const val iconCircleSize = 42f
        const val selectedIconCircleAlpha = .5f
        const val deselectedIconAlpha = .5f
        const val lineWidth = 2f
        const val legendLineWidth = 3f  // a little thicker than the actual diagram
        const val legendLowContrastWidth = 4f
        const val legendLineLength = 120f
    }

    /** Wrapper around [RelationshipLevel], adding War, DefensivePact, and a Z-Order property */
    private sealed class Relation {
        abstract val ordinal: Int
        abstract val label: String
        abstract val color: Color
        abstract val zOrder: Int

        object War : Relation() {
            override val ordinal = 0
            override val label = "War"
            override val color: Color = Color.RED
            override val zOrder = 100
        }
        private class Wrapped(level: RelationshipLevel) : Relation() {
            override val ordinal = level.ordinal + 1
            override val label = level.name
            override val color: Color = level.color
            override val zOrder = getZOrder(level)
        }
        object DefensivePact : Relation() {
            override val ordinal = 99
            override val label = Constants.defensivePact
            override val color: Color = Color.PURPLE
            override val zOrder = 99
        }

        companion object {
            private val relationshipLevels: Array<Relation> = RelationshipLevel.entries.map { Wrapped(it) }.toTypedArray()

            operator fun get(level: RelationshipLevel) = relationshipLevels[level.ordinal]

            val entries = listOf(
                War,
                *relationshipLevels,
                DefensivePact,
            )

            private fun getZOrder(level: RelationshipLevel) = when(level) {
                RelationshipLevel.Unforgivable -> 7
                RelationshipLevel.Enemy -> 5
                RelationshipLevel.Afraid -> 4
                RelationshipLevel.Competitor -> 2
                RelationshipLevel.Neutral -> 0
                RelationshipLevel.Favorable -> 1
                RelationshipLevel.Friend -> 3
                RelationshipLevel.Ally -> 6
            }
        }

        // These are for cleaner Set support - would likely work without
        override fun hashCode() = ordinal.hashCode()
        override fun equals(other: Any?) = other is Relation && ordinal == other.ordinal
    }

    /** Wrapper for either civ Nation Portrait (with selection circle) or a relationship line */
    private sealed class WidgetEntry {
        abstract val widget: Actor
        abstract val zOrder: Int
        abstract fun isRelationSelected(selection: Set<Relation>): Boolean
        abstract fun isCivSelected(selection: Set<Civilization>): Boolean
        abstract fun setSelected(selected: Boolean)
        fun setSelected(selectedCivs: Set<Civilization>, selectedRelations: Set<Relation>) =
            setSelected(isCivSelected(selectedCivs) && isRelationSelected(selectedRelations))

        class Icon(val icon: IconCircleGroup, val civ: Civilization) : WidgetEntry() {
            override val widget get() = icon
            override val zOrder = 200
            override fun isRelationSelected(selection: Set<Relation>) = true
            override fun isCivSelected(selection: Set<Civilization>) = civ in selection
            override fun setSelected(selected: Boolean) {
                icon.circle.color.a = if (selected) selectedIconCircleAlpha else 0f
                icon.actor.color.a = if (selected) 1f else deselectedIconAlpha
            }
            val x get() = widget.x + widget.width / 2
            val y get() = widget.y + widget.height / 2
        }

        class Line(val line: Image, val relation: Relation, val civ1: Civilization, val civ2: Civilization) : WidgetEntry() {
            override val widget get() = line
            override val zOrder = relation.zOrder
            override fun isRelationSelected(selection: Set<Relation>) = relation in selection
            override fun isCivSelected(selection: Set<Civilization>) = civ1 in selection && civ2 in selection
            override fun setSelected(selected: Boolean) {
                widget.isVisible = selected
            }
        }
    }

    private class WidgetIndex(
        undefeatedCivs: List<Civilization>,
        freeSize: Float,
        val toggleSelection: (Civilization) -> Unit
    ) {
        // Not used after init, kept as property for convenience
        val civCount = undefeatedCivs.size
        /** Nation Portraits */
        val civWidgets = LinkedHashMap<Civilization, WidgetEntry.Icon>(civCount)
        /** Tracks which civ have any lines. Dead _and_ isolated civs should not react to icon clicks. */
        val hasLines = HashSet<Civilization>(civCount)
        /** All portraits and lines, each line only once */
        val all = ArrayList<WidgetEntry>((civCount * (civCount + 1)) / 2)

        init {
            // icons - not etheir center coordinates are used for the line creation
            for ((index, civ) in undefeatedCivs.withIndex())
                addCivIcon(index, civ, freeSize)

            // lines
            for ((fromIndex, civ) in undefeatedCivs.withIndex()) {
                if (civ.isDefeated()) continue // if you're dead, icon but no lines (One more turn mode after losing)
                val civWidget = civWidgets[civ]!! // !! is covered because every item in undefeatedCivs is guaranteed to be in the map by the "icons" loop
                for (toIndex in 0 until fromIndex) {
                    val otherCiv = undefeatedCivs[toIndex]
                    // Note: toIndex < fromIndex, so we're getting a relation _from_ a city-state _to_ a major civ, not vice versa (so we see "Afraid"),
                    // and major-major or cs-cs relations should be symmetrical
                    if (otherCiv.isDefeated()) continue
                    val diplomacy = civ.diplomacy[otherCiv.civID] ?: continue // Skip line if the civs don't know each other
                    val otherCivWidget = civWidgets[otherCiv]!! // !! is covered same as above
                    addLine(civ, otherCiv, civWidget, otherCivWidget, diplomacy.getRelation())
                }
            }

            all.sortBy { it.zOrder }
        }

        fun DiplomacyManager.getRelation() = when {
            diplomaticStatus == DiplomaticStatus.War ->
                Relation.War
            diplomaticStatus == DiplomaticStatus.DefensivePact && !(civInfo.isCityState || otherCiv.isCityState) ->
                Relation.DefensivePact
            civInfo.isHuman() && otherCiv.isHuman() && hasModifier(DiplomaticModifiers.DeclarationOfFriendship) ->
                Relation[RelationshipLevel.Friend]
            (civInfo.isCityState && civInfo.allyCiv == otherCiv) || (otherCiv.isCityState && otherCiv.allyCiv == civInfo) ->
                Relation[RelationshipLevel.Ally]
            else ->
                Relation[relationshipLevel()]
        }

        fun addCivIcon(index: Int, civ: Civilization, freeSize: Float) {
            val civGroup = ImageGetter.getNationPortrait(civ.nation, iconSize)
                .surroundWithCircle(iconCircleSize, false, Color.CLEAR_WHITE) // Not CLEAR, we simply control alpha later for some gray.

            val vector = HexMath.getVectorForAngle(2 * Math.PI.toFloat() * index / civCount)
            civGroup.setPosition(freeSize / 2, freeSize / 2, Align.center)
            civGroup.moveBy(vector.x * freeSize / 2.25f, vector.y * freeSize / 2.25f)
            civGroup.touchable = Touchable.enabled
            civGroup.onClick { toggleSelection(civ) }
            civGroup.addTooltip(civ.civName, tipAlign = Align.left, hideIcons = true)

            val entry = WidgetEntry.Icon(civGroup, civ)
            civWidgets[civ] = entry
            all += entry
        }

        fun addLine(civ: Civilization, otherCiv: Civilization, civWidget: WidgetEntry.Icon, otherCivWidget: WidgetEntry.Icon, relation: Relation) {
            val statusLine = ImageGetter.getLine(
                startX = civWidget.x,
                startY = civWidget.y,
                endX = otherCivWidget.x,
                endY = otherCivWidget.y,
                width = lineWidth
            )
            statusLine.color = relation.color
            val entry = WidgetEntry.Line(statusLine, relation, civ, otherCiv)
            hasLines += civ
            hasLines += otherCiv
            all += entry
        }
    }

    private val index = WidgetIndex(undefeatedCivs, freeSize, ::toggleSelection)
    private val selectedCivs = mutableSetOf<Civilization>()
    private val selectedRelations = mutableSetOf<Relation>()

    init {
        setSize(freeSize, freeSize)

        // An Image Actor does not respect alpha for its hit area, it's always square, but we want a clickable _circle_
        // Radius to show legend should be no larger than freeSize / 2.25f - 15f (see above), let's make it a little smaller
        val clickableArea = ClickableCircle(freeSize / 1.25f - 25f)
        clickableArea.onActivation {
            DiagramLegendPopup(stage, this)
        }
        clickableArea.center(this)
        addActor(clickableArea)

        for (entry in index.all)
            addActor(entry.widget)

        selectedCivs.addAll(undefeatedCivs)
        selectedRelations.addAll(Relation.entries)
    }

    private fun toggleSelection(civ: Civilization) {
        // ignore the clicks on "dead" civilizations
        if (civ !in index.hasLines) return

        if (civ in selectedCivs) selectedCivs.remove(civ) else selectedCivs.add(civ)
        if (selectedCivs.size < 2) selectedCivs.addAll(index.civWidgets.keys)
        updateVisibility()
    }

    private fun toggleSelection(relation: Relation) {
        if (relation in selectedRelations) selectedRelations.remove(relation) else selectedRelations.add(relation)
        if (selectedRelations.isEmpty()) selectedRelations.addAll(Relation.entries)
        updateVisibility()
    }

    private fun updateVisibility() {
        for (entry in index.all)
            entry.setSelected(selectedCivs, selectedRelations)
    }

    private inner class DiagramLegendPopup(stage: Stage, diagram: Actor) : AnimatedMenuPopup(stage, diagram, Align.center) {
        val checkBoxes = mutableMapOf<Relation, CheckBox>()

        override fun createContentTable(): Table {
            val legend = Table()
            legend.background = ImageGetter.getDrawable("OtherIcons/Politics-diagram-bg")
            legend.add(ShadowedLabel("Diagram line colors", Constants.headingFontSize)).colspan(3).row()
            for (relation in Relation.entries) {
                legend.addLegendRow(relation)
            }
            return super.createContentTable()!!.apply {
                add(legend).grow()
            }
        }

        fun updateCheckBoxes() {
            // Checked stati are only out of sync if toggleSelection replaced the empty selection with all entries
            if (checkBoxes.all { it.value.isChecked == it.key in selectedRelations }) return
            checkBoxes.values.forEach { it.isChecked = true }
        }

        fun Table.addLegendRow(relation: Relation) {
            val checkBox = CheckBox(null, BaseScreen.skin)
            checkBox.isChecked = relation in selectedRelations
            checkBox.onClick {
                toggleSelection(relation)
                updateCheckBoxes()
            }
            checkBoxes[relation] = checkBox
            add(checkBox)

            // empiric hack to equalize the "visual impact" a little. Afraid is worst at contrast 1.4, Enemy has 9.8
            val contrast = getContrastRatio(Color.DARK_GRAY, relation.color).toFloat()
            val width = legendLineWidth + (legendLowContrastWidth - legendLineWidth) / contrast.coerceAtLeast(1f)
            val line = ImageGetter.getLine(0f, width / 2, legendLineLength, width / 2, width)
            line.color = relation.color
            add(line).size(legendLineLength, width).padTop(5f)

            add(ShadowedLabel(relation.label)).padLeft(5f).padTop(10f).row()
        }
    }
}
