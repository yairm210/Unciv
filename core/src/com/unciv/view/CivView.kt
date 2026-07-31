package com.unciv.view

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.ImprovementBuildingProblem
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import yairm210.purity.annotations.Readonly

/** View of a [Civilization] from the perspective of [viewer]. */
class CivView(internal val civ: Civilization, internal val viewer: Civilization) {
    val gold: Int get() = civ.gold
    val tech: TechManagerView get() = TechManagerView(civ.tech)

    @Readonly fun hasStatToBuy(stat: Stat, price: Int): Boolean = civ.hasStatToBuy(stat, price)
    @Readonly fun cities(): List<CityView> = civ.cities.map { CityView(it, viewer) }

    @Readonly fun canSeeTile(tile: Tile): Boolean = tile in civ.viewableTiles
    @Readonly fun canSeeResource(resource: TileResource?): Boolean = civ.canSeeResource(resource)
    @Readonly fun canSeeUnit(unit: MapUnit): Boolean = !unit.isInvisible(civ)
    @Readonly fun isOwnerOf(city: City): Boolean = civ === city.civ
    @Readonly fun getShownImprovementOn(tile: Tile): String? = tile.getShownImprovement(civ)
    @Readonly fun canBuildImprovementOn(improvement: TileImprovement, tile: Tile): Boolean =
        tile.improvementFunctions.canBuildImprovement(improvement, civ.state)
    @Readonly fun getImprovementBuildingProblems(improvement: TileImprovement, tile: Tile): Sequence<ImprovementBuildingProblem> =
        tile.improvementFunctions.getImprovementBuildingProblems(improvement, civ.state)
    @Readonly fun technologyByName(name: String?): Technology? = civ.gameInfo.ruleset.technologies[name]

    @Readonly fun hasUnique(type: UniqueType): Boolean = civ.hasUnique(type)
    @Readonly fun isReligionEnabled(): Boolean = civ.gameInfo.isReligionEnabled()
    @Readonly fun getGreatPersonPoints(name: String): Int = civ.greatPeople.greatPersonPointsCounter[name]
    @Readonly fun getPointsRequiredForGreatPerson(name: String): Int = civ.greatPeople.getPointsRequiredForGreatPerson(name)
    @Readonly fun isCivConstructionDisabled(name: String): Boolean = name in civ.disabledCityConstructions

    @Readonly fun isSpectator(): Boolean = civ.isSpectator()
    @Readonly fun hasExplored(tile: Tile): Boolean = civ.hasExplored(tile)

    fun tryDisableCivConstruction(name: String) {
        civ.cities.forEach { it.disabledConstructions.add(name) }
        civ.disabledCityConstructions.add(name)
    }
    fun tryEnableCivConstruction(name: String) {
        civ.cities.forEach { it.disabledConstructions.remove(name) }
        civ.disabledCityConstructions.remove(name)
    }
}
