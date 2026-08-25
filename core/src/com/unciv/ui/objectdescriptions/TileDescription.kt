package com.unciv.ui.objectdescriptions

import com.unciv.Constants
import com.unciv.logic.map.tile.ImprovementBuildingProblem
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toStringSigned
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.utils.DebugUtils
import com.unciv.view.CityView
import com.unciv.view.CivView
import com.unciv.view.TileView

object TileDescription {

    /** Get info on a selected tile, used on WorldScreen (right side above minimap), CityScreen or MapEditorViewTab. */
    fun toMarkup(tileView: TileView, viewingCiv: CivView?, hideUnits: Boolean = false, spyCity: CityView? = null): ArrayList<FormattedLine> {
        val lineList = ArrayList<FormattedLine>()
        val isViewableToPlayer = viewingCiv == null || DebugUtils.VISIBLE_MAP
                || viewingCiv.canSeeTile(tileView)

        if (tileView.isCityCenter()) {
            val cityView = tileView.owningCity()!!
            var cityString = cityView.name.tr()
            if (isViewableToPlayer) cityString += " (${cityView.getHealth()})"
            lineList += FormattedLine(cityString)
            if (DebugUtils.VISIBLE_MAP || viewingCiv != null && viewingCiv.isOwnerOf(cityView)
                    && (spyCity == null || spyCity == cityView))
                lineList += cityView.getProductionMarkup()
        }

        lineList += FormattedLine(tileView.baseTerrain, link = "Terrain/${tileView.baseTerrain}")
        for (terrainFeature in tileView.terrainFeatures)
            lineList += FormattedLine(terrainFeature, link = "Terrain/$terrainFeature")

        val resource = tileView.getViewableResource(viewingCiv)
        if (resource != null)
            lineList += if (resource.resourceType == ResourceType.Strategic)
                FormattedLine("{${tileView.resource}} (${tileView.resourceAmount})", link = "Resource/${tileView.resource}")
            else
                FormattedLine(resource.name, link = "Resource/${resource.name}")

        if (viewingCiv != null && resource != null)
            addNeedsResearchLine(lineList, tileView, viewingCiv, resource)

        if (tileView.naturalWonder != null)
            lineList += FormattedLine(tileView.naturalWonder!!, link = "Terrain/${tileView.naturalWonder}")

        if (tileView.roadStatus !== RoadStatus.None && !tileView.isCityCenter()) {
            val pillageText = if (tileView.roadIsPillaged) " (Pillaged!)" else ""
            lineList += FormattedLine("[${tileView.roadStatus.name}]$pillageText", link = "Improvement/${tileView.roadStatus.name}")
        }

        val shownImprovement = tileView.getShownImprovement()
        if (shownImprovement != null) {
            val pillageText = if (tileView.improvementIsPillaged) " (Pillaged!)" else ""
            lineList += FormattedLine("[$shownImprovement]$pillageText", link = "Improvement/$shownImprovement")
        }

        if (tileView.improvementInProgress != null && isViewableToPlayer) {
            // Negative turnsToImprovement is used for UniqueType.CreatesOneImprovement
            val line = "{${tileView.improvementInProgress}}" +
                    if (tileView.turnsToImprovement > 0) " - ${tileView.turnsToImprovement}${Fonts.turn}" else " ({Under construction})"
            lineList += FormattedLine(line, link = "Improvement/${tileView.improvementInProgress}")
        }

        if (tileView.civilianUnit != null && isViewableToPlayer && !hideUnits)
            lineList += FormattedLine(
                tileView.civilianUnit!!.name.tr() + " - " + tileView.civilianUnit!!.civName.tr(),
                link = "Unit/${tileView.civilianUnit!!.name}"
            )
        if (tileView.militaryUnit != null && isViewableToPlayer && !hideUnits) {
            val milUnitString = tileView.militaryUnit!!.name.tr() +
                    (if (tileView.militaryUnit!!.health < 100) "(" + tileView.militaryUnit!!.health + ")" else "") +
                    " - " + tileView.militaryUnit!!.civName.tr()
            lineList += FormattedLine(milUnitString, link = "Unit/${tileView.militaryUnit!!.name}")
        }

        val defenceBonus = tileView.getDefensiveBonus()
        if (defenceBonus != 0f) {
            val defencePercentString = (defenceBonus * 100).toInt().toStringSigned() + "%"
            lineList += FormattedLine("[$defencePercentString] to unit defence")
        }

        if (tileView.isImpassible()) lineList += FormattedLine(Constants.impassable)
        if (tileView.isLand && tileView.isAdjacentTo(Constants.freshWater)) lineList += FormattedLine(Constants.freshWater)

        return lineList
    }

    private fun addNeedsResearchLine(lineList: ArrayList<FormattedLine>, tileView: TileView, viewingCiv: CivView, resource: TileResource) {
        val tileImprovements = resource.getImprovements()
            .mapNotNull { tileView.getRuleset().tileImprovements[it] }
        if (tileImprovements.any { viewingCiv.canBuildImprovementOn(it, tileView) })
            return

        val researchableImprovements = tileImprovements.filter { improvement ->
            viewingCiv.getImprovementBuildingProblems(improvement, tileView)
                .filterNot { it == ImprovementBuildingProblem.OutsideBorders }
                .run { any() && all { it == ImprovementBuildingProblem.MissingTech } }
        }
        if (researchableImprovements.isEmpty()) return

        val techRequired = researchableImprovements
            .mapNotNull { viewingCiv.technologyByName(it.techRequired) }
            .filterNot { viewingCiv.isResearched(it.name) }
            .minByOrNull { it.cost }
            ?: return

        lineList += FormattedLine(
            "Requires [${techRequired.name}]",
            link = techRequired.makeLink(),
            color = "#FAA"
        )
    }
}
