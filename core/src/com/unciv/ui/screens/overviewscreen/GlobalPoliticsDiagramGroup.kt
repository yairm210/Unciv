package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.HexMath
import com.unciv.models.ruleset.nation.getContrastRatio
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.input.ClickableCircle
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ShadowedLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.AnimatedMenuPopup

/** This is the 'spider net'-like polygon showing one line per civ-civ relation
 *  @param undefeatedCivs Civs to display - note the viewing player is always included, so it's possible the name is off and there's a dead civ included.
 *  @param freeSize Width and height this [Group] sizes itself to
 */
class GlobalPoliticsDiagramGroup(
    undefeatedCivs: List<Civilization>,
    freeSize: Float
): Group() {
    init {
        setSize(freeSize, freeSize)

        // An Image Actor does not respect alpha for its hit area, it's always square, but we want a clickable _circle_
        // Radius to show legend should be no larger than freeSize / 2.25f - 15f (see below), let's make it a little smaller
        val clickableArea = ClickableCircle(freeSize / 1.25f - 25f)
        clickableArea.onActivation {
            DiagramLegendPopup(stage, this)
        }
        clickableArea.center(this)
        addActor(clickableArea)

        val civGroups = HashMap<String, Actor>()
        val civLines = HashMap<String, MutableSet<Actor>>()
        val civCount = undefeatedCivs.count()

        for ((i, civ) in undefeatedCivs.withIndex()) {
            val civGroup = ImageGetter.getNationPortrait(civ.nation, 30f)

            val vector = HexMath.getVectorForAngle(2 * Math.PI.toFloat() * i / civCount)
            civGroup.center(this)
            civGroup.moveBy(vector.x * freeSize / 2.25f, vector.y * freeSize / 2.25f)
            civGroup.touchable = Touchable.enabled
            civGroup.onClick {
                onCivClicked(civLines, civ.civName)
            }
            civGroup.addTooltip(civ.civName, tipAlign = Align.bottomLeft)

            civGroups[civ.civName] = civGroup
            addActor(civGroup)
        }

        for (civ in undefeatedCivs) {
            if (civ.isDefeated()) continue // if you're dead, icon but no lines (One more turn mode after losing)
            for (diplomacy in civ.diplomacy.values) {
                val otherCiv = diplomacy.otherCiv
                if (otherCiv !in undefeatedCivs || otherCiv.isDefeated()) continue
                val civGroup = civGroups[civ.civName]!!
                val otherCivGroup = civGroups[diplomacy.otherCiv.name]!!

                val statusLine = ImageGetter.getLine(
                    startX = civGroup.x + civGroup.width / 2,
                    startY = civGroup.y + civGroup.height / 2,
                    endX = otherCivGroup.x + otherCivGroup.width / 2,
                    endY = otherCivGroup.y + otherCivGroup.height / 2,
                    width = 2f
                )

                statusLine.color = if (diplomacy.diplomaticStatus == DiplomaticStatus.War) Color.RED
                // Color defensive pact for major civs only
                else if (diplomacy.diplomaticStatus == DiplomaticStatus.DefensivePact
                    && !(civ.isCityState || otherCiv.isCityState)) Color.PURPLE
                else if (civ.isHuman() && otherCiv.isHuman() && diplomacy.hasModifier(DiplomaticModifiers.DeclarationOfFriendship))
                    RelationshipLevel.Friend.color
                // Test for alliance with city state
                else if ((civ.isCityState && civ.allyCiv == diplomacy.otherCiv)
                    || (otherCiv.isCityState && otherCiv.allyCiv == civ)) RelationshipLevel.Ally.color
                // Else the color depends on opinion between major civs, OR city state relationship with major civ
                else diplomacy.relationshipLevel().color

                if (!civLines.containsKey(civ.civName)) civLines[civ.civName] = mutableSetOf()
                civLines[civ.civName]!!.add(statusLine)

                addActorAt(0, statusLine)
            }
        }
    }

    private fun onCivClicked(civLines: HashMap<String, MutableSet<Actor>>, name: String) {
        // ignore the clicks on "dead" civilizations, and remember the selected one
        val selectedLines = civLines[name] ?: return

        // let's check whether lines of all civs are visible (except selected one)
        var atLeastOneLineVisible = false
        var allAreLinesInvisible = true
        for (lines in civLines.values) {
            // skip the civilization selected by user, and civilizations with no lines
            if (lines == selectedLines || lines.isEmpty()) continue

            val visibility = lines.first().isVisible
            atLeastOneLineVisible = atLeastOneLineVisible || visibility
            allAreLinesInvisible = allAreLinesInvisible && visibility

            // check whether both visible and invisible lines are present
            if (atLeastOneLineVisible && !allAreLinesInvisible) {
                // invert visibility of the selected civ's lines
                selectedLines.forEach { it.isVisible = !it.isVisible }
                return
            }
        }

        if (selectedLines.first().isVisible) {
            // invert visibility of all lines except selected one
            civLines.filter { it.key != name }
                .forEach { it.value.forEach { line -> line.isVisible = !line.isVisible } }
        } else {
            // it happens only when all are visible except selected one
            // invert visibility of the selected civ's lines
            selectedLines.forEach { it.isVisible = !it.isVisible }
        }
    }

    private class DiagramLegendPopup(stage: Stage, diagram: Actor) : AnimatedMenuPopup(stage, diagram.getCenterInStageCoordinates()) {
        init {
            touchable = Touchable.enabled
            onActivation { close() }
        }

        companion object {
            private fun Actor.getCenterInStageCoordinates(): Vector2 = localToStageCoordinates(Vector2(width / 2, height / 2))

            const val lineWidth = 3f  // a little thicker than the actual diagram
            const val lowContrastWidth = 4f
            const val lineLength = 120f
        }

        override fun createContentTable(): Table {
            val legend = Table()
            legend.background = ImageGetter.getDrawable("OtherIcons/Politics-diagram-bg")
            legend.add(ShadowedLabel("Diagram line colors", Constants.headingFontSize)).colspan(2).row()
            //todo Rethink hardcoding together with the statusLine.color one in GlobalPoliticsDiagramGroup
            legend.addLegendRow("War", Color.RED)
            for (level in RelationshipLevel.entries) {
                legend.addLegendRow(level.name, level.color)
            }
            legend.addLegendRow(Constants.defensivePact, Color.PURPLE)
            return super.createContentTable()!!.apply {
                add(legend).grow()
            }
        }

        fun Table.addLegendRow(text: String, color: Color) {
            // empiric hack to equalize the "visual impact" a little. Afraid is worst at contrast 1.4, Enemy has 9.8
            val contrast = getContrastRatio(Color.DARK_GRAY, color).toFloat()
            val width = lineWidth + (lowContrastWidth - lineWidth) / contrast.coerceAtLeast(1f)
            val line = ImageGetter.getLine(0f, width / 2, lineLength, width / 2, width)
            line.color = color
            add(line).size(lineLength, width).padTop(5f)
            add(ShadowedLabel(text)).padLeft(5f).padTop(10f).row()
        }
    }
}
