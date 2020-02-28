package com.unciv.ui.victoryscreen

import com.unciv.logic.civilization.CivilizationInfo
import kotlin.math.roundToInt

class RankingManager(private val civInfo: CivilizationInfo) {

    fun getRanking(category: RankingType) : Int {
        return when(category) {
            RankingType.Population -> civInfo.cities.sumBy { it.population.population }
            RankingType.CropYield -> civInfo.statsForNextTurn.food.roundToInt()
            RankingType.Production -> civInfo.statsForNextTurn.production.roundToInt()
            RankingType.Gold -> civInfo.gold
            RankingType.Land -> civInfo.cities.sumBy { it.tiles.size }
            RankingType.Force -> civInfo.units.sumBy { it.baseUnit.strength }
            RankingType.Happiness -> civInfo.getHappiness()
            RankingType.Technologies -> civInfo.tech.researchedTechnologies.size
            RankingType.Culture -> civInfo.policies.storedCulture
        }
    }
}

