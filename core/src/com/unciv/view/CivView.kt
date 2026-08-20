package com.unciv.view

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.ImprovementBuildingProblem
import com.unciv.models.Counter
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.StatMap
import yairm210.purity.annotations.Readonly

/** View of a [Civilization] from the perspective of [viewer] via [gameView]. */
class CivView(civ: Civilization,
              viewer: Civilization,
              spectatorMode: Boolean = false,
              val gameView: GameView) : ForeignCivView(civ, viewer, spectatorMode) {

    // Navigation
    @Readonly fun getCity(city: City): CityView = gameView.getCityView(city)
    @Readonly fun cities(): List<CityView> = civ.cities.map { getCity(it) }
    @Readonly fun getTradeView(otherCiv: ForeignCivView): TradeView = TradeView(civ, otherCiv.unwrap())

    // Data retrieval
    @Readonly fun isResearched(techName: String): Boolean = civ.tech.isResearched(techName)

    @Readonly fun hasStatToBuy(stat: Stat, price: Int): Boolean = civ.hasStatToBuy(stat, price)

    @Readonly fun canSeeTile(tileView: TileView): Boolean = tileView.unwrap().isVisible(civ)
    @Readonly fun canSeeResource(resource: TileResource?): Boolean = civ.canSeeResource(resource)
    @Readonly fun isOwnerOf(cityView: ForeignCityView): Boolean = civ === cityView.unwrap().civ
    @Readonly fun canBuildImprovementOn(improvement: TileImprovement, tileView: TileView): Boolean =
        tileView.unwrap().improvementFunctions.canBuildImprovement(improvement, civ.state)
    @Readonly fun getImprovementBuildingProblems(improvement: TileImprovement, tileView: TileView): Sequence<ImprovementBuildingProblem> =
        tileView.unwrap().improvementFunctions.getImprovementBuildingProblems(improvement, civ.state)
    @Readonly fun technologyByName(name: String?): Technology? = civ.gameInfo.ruleset.technologies[name]

    @Readonly fun hasUnique(type: UniqueType): Boolean = civ.hasUnique(type)
    @Readonly fun isReligionEnabled(): Boolean = civ.gameInfo.isReligionEnabled()
    @Readonly fun getGreatPersonPoints(name: String): Int = civ.greatPeople.greatPersonPointsCounter[name]
    @Readonly fun getPointsRequiredForGreatPerson(name: String): Int = civ.greatPeople.getPointsRequiredForGreatPerson(name)
    @Readonly fun getGreatPersonPointsCounter(): Counter<String> = civ.greatPeople.greatPersonPointsCounter
    @Readonly fun getGreatPersonPointsForNextTurn(): Counter<String> = civ.greatPeople.getGreatPersonPointsForNextTurn()
    @Readonly fun getGreatGeneralPointsCounter(): Counter<String> = civ.greatPeople.greatGeneralPointsCounter
    @Readonly fun getPointsForNextGreatGeneralCounter(): Counter<String> = civ.greatPeople.pointsForNextGreatGeneralCounter
    @Readonly fun isCivConstructionDisabled(name: String): Boolean = name in civ.disabledCityConstructions

    @Readonly fun isSpectator(): Boolean = civ.isSpectator()
    @Readonly fun hasExplored(tileView: TileView): Boolean = civ.hasExplored(tileView.unwrap())

    @Readonly fun getStatMapForNextTurn(): StatMap = civ.stats.getStatMapForNextTurn()
    @Readonly fun getHappinessBreakdown(): HashMap<String, Float> = civ.stats.getHappinessBreakdown()
    @Readonly fun getMatchingUniques(uniqueType: UniqueType): Sequence<Unique> = civ.getMatchingUniques(uniqueType)
    @Readonly fun getGoldPercentConvertedToScience(): Float = civ.tech.goldPercentConvertedToScience
    @Readonly fun calculateScoreBreakdown(): HashMap<String, Double> = civ.calculateScoreBreakdown()

    // Actions
    fun tryDisableCivConstruction(name: String) {
        civ.cities.forEach { it.disabledConstructions.add(name) }
        civ.disabledCityConstructions.add(name)
    }
    fun tryEnableCivConstruction(name: String) {
        civ.cities.forEach { it.disabledConstructions.remove(name) }
        civ.disabledCityConstructions.remove(name)
    }
    fun trySetGoldPercentConvertedToScience(value: Float): Boolean {
        civ.tech.goldPercentConvertedToScience = value
        return true
    }
    fun tryUpdateAllCityStats(): Boolean {
        civ.cities.forEach { it.cityStats.update() }
        return true
    }
}
