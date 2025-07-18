package com.unciv.logic.map.tile

import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toStringSigned
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.utils.DebugUtils

object TileDescription {

    /** Get info on a selected tile, used on WorldScreen (right side above minimap), CityScreen or MapEditorViewTab. */
    fun toMarkup(tile: Tile, viewingCiv: Civilization?): ArrayList<FormattedLine> {
        val lineList = ArrayList<FormattedLine>()
        val isViewableToPlayer = viewingCiv == null || DebugUtils.VISIBLE_MAP
                || viewingCiv.viewableTiles.contains(tile)

        if (tile.isCityCenter()) {
            val city = tile.getCity()!!
            var cityString = city.name.tr()
            if (isViewableToPlayer) cityString += " (${city.health})"
            lineList += FormattedLine(cityString)
            if (DebugUtils.VISIBLE_MAP || city.civ == viewingCiv)
                lineList += city.cityConstructions.getProductionMarkup(tile.ruleset)
        }

        lineList += FormattedLine(tile.baseTerrain, link="Terrain/${tile.baseTerrain}")
        for (terrainFeature in tile.terrainFeatures)
            lineList += FormattedLine(terrainFeature, link="Terrain/$terrainFeature")
        if (tile.resource != null && (viewingCiv == null || tile.hasViewableResource(viewingCiv)))
            lineList += if (tile.tileResource.resourceType == ResourceType.Strategic)
                FormattedLine("{${tile.resource}} (${tile.resourceAmount})", link="Resource/${tile.resource}")
            else
                FormattedLine(tile.resource!!, link="Resource/${tile.resource}")
        if (tile.resource != null && viewingCiv != null && tile.hasViewableResource(viewingCiv)) {
            val resourceImprovement = tile.tileResource.getImprovements().firstOrNull { tile.improvementFunctions.canBuildImprovement(tile.ruleset.tileImprovements[it]!!, viewingCiv.state) }
            val tileImprovement = tile.ruleset.tileImprovements[resourceImprovement]
            if (tileImprovement?.techRequired != null
                    && !viewingCiv.tech.isResearched(tileImprovement.techRequired!!)) {
                lineList += FormattedLine(
                    "Requires [${tileImprovement.techRequired}]",
                    link="Technology/${tileImprovement.techRequired}",
                    color= "#FAA"
                )
            }
        }
        if (tile.naturalWonder != null)
            lineList += FormattedLine(tile.naturalWonder!!, link="Terrain/${tile.naturalWonder}")
        if (tile.roadStatus !== RoadStatus.None && !tile.isCityCenter()) {
            val pillageText = if (tile.roadIsPillaged) " (Pillaged!)" else ""
            lineList += FormattedLine("[${tile.roadStatus.name}]$pillageText", link = "Improvement/${tile.roadStatus.name}")
        }
        val shownImprovement = tile.getShownImprovement(viewingCiv)
        if (shownImprovement != null) {
            val pillageText = if (tile.improvementIsPillaged) " (Pillaged!)" else ""
            lineList += FormattedLine("[$shownImprovement]$pillageText", link = "Improvement/$shownImprovement")
        }

        if (tile.improvementInProgress != null && isViewableToPlayer) {
            // Negative turnsToImprovement is used for UniqueType.CreatesOneImprovement
            val line = "{${tile.improvementInProgress}}" +
                    if (tile.turnsToImprovement > 0) " - ${tile.turnsToImprovement}${Fonts.turn}" else " ({Under construction})"
            lineList += FormattedLine(line, link="Improvement/${tile.improvementInProgress}")
        }

        if (tile.civilianUnit != null && isViewableToPlayer)
            lineList += FormattedLine(tile.civilianUnit!!.name.tr() + " - " + tile.civilianUnit!!.civ.civName.tr(),
                link="Unit/${tile.civilianUnit!!.name}")
        if (tile.militaryUnit != null && isViewableToPlayer && (viewingCiv == null || !tile.militaryUnit!!.isInvisible(viewingCiv))) {
            val milUnitString = tile.militaryUnit!!.name.tr() +
                    (if (tile.militaryUnit!!.health < 100) "(" + tile.militaryUnit!!.health + ")" else "") +
                    " - " + tile.militaryUnit!!.civ.civName.tr()
            lineList += FormattedLine(milUnitString, link="Unit/${tile.militaryUnit!!.name}")
        }

        val defenceBonus = tile.getDefensiveBonus()
        if (defenceBonus != 0f) {
            val defencePercentString = (defenceBonus * 100).toInt().toStringSigned() + "%"
            lineList += FormattedLine("[$defencePercentString] to unit defence")
        }
        if (tile.isImpassible()) lineList += FormattedLine(Constants.impassable)
        if (tile.isLand && tile.isAdjacentTo(Constants.freshWater)) lineList += FormattedLine(Constants.freshWater)

        return lineList
    }

}
