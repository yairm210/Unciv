package com.unciv.view

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.ImprovementBuildingProblem
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import yairm210.purity.annotations.Readonly

/** View of a [Civilization] from the perspective of [viewer] via [gameView]. */
class CivView(civ: Civilization,
              viewer: Civilization,
              spectatorMode: Boolean = false,
              val gameView: GameView) : ForeignCivView(civ, viewer, spectatorMode) {

    // Navigation
    @Readonly fun getCity(city: City): CityView = gameView.getCityView(city)
    @Readonly fun cities(): List<CityView> = civ.cities.map { getCity(it) }
    @Readonly fun getTradeView(otherCiv: ForeignCivView): TradeView = TradeView(civ, otherCiv.getCiv())

    // Data retrieval
    @Readonly fun isResearched(techName: String): Boolean = civ.tech.isResearched(techName)

    @Readonly fun hasStatToBuy(stat: Stat, price: Int): Boolean = civ.hasStatToBuy(stat, price)

    @Readonly fun canSeeTile(tileView: TileView): Boolean = tileView.getTile().isVisible(civ)
    @Readonly fun canSeeResource(resource: TileResource?): Boolean = civ.canSeeResource(resource)
    @Readonly fun isOwnerOf(cityView: ForeignCityView): Boolean = civ === cityView.getCity().civ
    @Readonly fun canBuildImprovementOn(improvement: TileImprovement, tileView: TileView): Boolean =
        tileView.getTile().improvementFunctions.canBuildImprovement(improvement, civ.state)
    @Readonly fun getImprovementBuildingProblems(improvement: TileImprovement, tileView: TileView): Sequence<ImprovementBuildingProblem> =
        tileView.getTile().improvementFunctions.getImprovementBuildingProblems(improvement, civ.state)
    @Readonly fun technologyByName(name: String?): Technology? = civ.gameInfo.ruleset.technologies[name]

    @Readonly fun hasUnique(type: UniqueType): Boolean = civ.hasUnique(type)
    @Readonly fun isReligionEnabled(): Boolean = civ.gameInfo.isReligionEnabled()
    @Readonly fun getGreatPersonPoints(name: String): Int = civ.greatPeople.greatPersonPointsCounter[name]
    @Readonly fun getPointsRequiredForGreatPerson(name: String): Int = civ.greatPeople.getPointsRequiredForGreatPerson(name)
    @Readonly fun isCivConstructionDisabled(name: String): Boolean = name in civ.disabledCityConstructions

    @Readonly fun isSpectator(): Boolean = civ.isSpectator()
    @Readonly fun hasExplored(tileView: TileView): Boolean = civ.hasExplored(tileView.getTile())

    // Actions
    fun tryDisableCivConstruction(name: String) {
        civ.cities.forEach { it.disabledConstructions.add(name) }
        civ.disabledCityConstructions.add(name)
    }
    fun tryEnableCivConstruction(name: String) {
        civ.cities.forEach { it.disabledConstructions.remove(name) }
        civ.disabledCityConstructions.remove(name)
    }
}
