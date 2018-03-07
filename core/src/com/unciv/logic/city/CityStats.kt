package com.unciv.logic.city

import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.stats.Stats


class CityStats {

    @Transient @JvmField var currentCityStats: Stats = Stats()  // This is so we won't have to calculate this multiple times - takes a lot of time, especially on phones
    @Transient lateinit var cityInfo: CityInfo

    private val statsFromTiles: Stats
        get() {
            val stats = Stats()
            for (cell in cityInfo.tilesInRange.filter { cityInfo.name == it.workingCity })
                stats.add(cell.getTileStats(cityInfo, cityInfo.civInfo))
            return stats
        }

    private

    val statsFromTradeRoute: Stats
        get() {
            val stats = Stats()
            if (!isCapital && isConnectedToCapital(RoadStatus.Road)) {
                val civInfo = cityInfo.civInfo
                var goldFromTradeRoute = civInfo.capital.population.population * 0.15 + cityInfo.population.population * 1.1 - 1 // Calculated by http://civilization.wikia.com/wiki/Trade_route_(Civ5)
                if (civInfo.policies.isAdopted("Trade Unions")) goldFromTradeRoute += 2.0
                if (civInfo.buildingUniques.contains("TradeRouteGoldIncrease")) goldFromTradeRoute *= 1.25 // Machu Pichu speciality
                stats.gold += goldFromTradeRoute.toFloat()
            }
            return stats
        }


    private val statsFromProduction: Stats
        get() {
            val stats = Stats()

            if ("Gold" == cityInfo.cityConstructions.currentConstruction) stats.gold += stats.production / 4
            if ("Science" == cityInfo.cityConstructions.currentConstruction) {
                var scienceProduced = stats.production / 4
                if (cityInfo.civInfo.buildingUniques.contains("ScienceConversionIncrease")) scienceProduced *= 1.33f
                if (cityInfo.civInfo.policies.isAdopted("Rationalism")) scienceProduced *= 1.33f
                stats.science += scienceProduced
            }
            return stats
        }


    private val statPercentBonusesFromRailroad: Stats
        get() {
            val stats = Stats()
            if (cityInfo.civInfo.tech.isResearched("Combustion") && (isCapital || isConnectedToCapital(RoadStatus.Railroad)))
                stats.production += 25f
            return stats
        }

    private val statPercentBonusesFromMarble: Stats
        get() {
            val stats = Stats()
            val construction = cityInfo.cityConstructions.getCurrentConstruction()

            if (construction is Building
                    && construction.isWonder
                    && cityInfo.civInfo.getCivResources().containsKey(GameBasics.TileResources["Marble"]))
                stats.production += 15f

            return stats
        }

    private val statPercentBonusesFromComputers: Stats
        get() {
            val stats = Stats()

            if (cityInfo.civInfo.tech.isResearched("Computers")) {
                stats.production += 10f
                stats.science += 10f
            }

            return stats
        }

    private val growthBonusFromPolicies: Float
        get() {
            var bonus = 0f
            if (cityInfo.civInfo.policies.isAdopted("Landed Elite") && isCapital)
                bonus += 0.1f
            if (cityInfo.civInfo.policies.isAdopted("Tradition Complete"))
                bonus += 0.15f
            return bonus
        }

    // needs to be a separate function because we need to know the global happiness state
    // in order to determine how much food is produced in a city!
    // -3 happiness per city
    fun getCityHappiness(): Float {
        val civInfo = cityInfo.civInfo
        var happiness = -3f
        var unhappinessFromCitizens = cityInfo.population.population.toFloat()
        if (civInfo.policies.isAdopted("Democracy"))
            unhappinessFromCitizens -= cityInfo.population.numberOfSpecialists * 0.5f
        if (civInfo.buildingUniques.contains("CitizenUnhappinessDecreased"))
            unhappinessFromCitizens *= 0.9f
        if (civInfo.policies.isAdopted("Aristocracy"))
            unhappinessFromCitizens *= 0.95f
        happiness -= unhappinessFromCitizens

        if (civInfo.policies.isAdopted("Aristocracy"))
            happiness += (cityInfo.population.population / 10).toFloat()
        if (civInfo.policies.isAdopted("Monarchy") && isCapital)
            happiness += (cityInfo.population.population / 2).toFloat()
        if (civInfo.policies.isAdopted("Meritocracy") && isConnectedToCapital(RoadStatus.Road))
            happiness += 1f

        happiness += cityInfo.cityConstructions.getStats().happiness.toInt().toFloat()

        return happiness
    }

    private val isCapital: Boolean
        get() = cityInfo.civInfo.capital === cityInfo

    private fun getStatsFromSpecialists(specialists: Stats, policies: List<String>): Stats {
        val stats = Stats()

        // Specialists
        stats.culture += specialists.culture * 3
        stats.production += specialists.production * 2
        stats.science += specialists.science * 3
        stats.gold += specialists.gold * 2
        val numOfSpecialists = cityInfo.population.numberOfSpecialists
        if (policies.contains("Commerce Complete")) stats.gold += numOfSpecialists.toFloat()
        if (policies.contains("Secularism")) stats.science += (numOfSpecialists * 2).toFloat()

        return stats
    }

    private fun getStatsFromPolicies(adoptedPolicies: List<String>): Stats {
        val stats = Stats()
        if (adoptedPolicies.contains("Tradition") && isCapital)
            stats.culture += 3f
        if (adoptedPolicies.contains("Landed Elite") && isCapital)
            stats.food += 2f
        if (adoptedPolicies.contains("Tradition Complete"))
            stats.food += 2f
        if (adoptedPolicies.contains("Monarchy") && isCapital)
            stats.gold += (cityInfo.population.population / 2).toFloat()
        if (adoptedPolicies.contains("Liberty"))
            stats.culture += 1f
        if (adoptedPolicies.contains("Republic"))
            stats.production += 1f
        if (adoptedPolicies.contains("Universal Suffrage"))
            stats.production += (cityInfo.population.population / 5).toFloat()
        if (adoptedPolicies.contains("Free Speech"))
            stats.culture += (cityInfo.population.population / 2).toFloat()

        return stats
    }

    private fun getStatPercentBonusesFromGoldenAge(isGoldenAge: Boolean): Stats {
        val stats = Stats()
        if (isGoldenAge) stats.production += 20f
        return stats
    }

    private fun getStatPercentBonusesFromPolicies(policies: List<String>, cityConstructions: CityConstructions): Stats {
        val stats = Stats()

        if (policies.contains("Collective Rule") && isCapital
                && "Settler" == cityConstructions.currentConstruction)
            stats.production += 50f
        if (policies.contains("Republic") && cityConstructions.getCurrentConstruction() is Building)
            stats.production += 5f
        if (policies.contains("Reformation") && cityConstructions.builtBuildings.any { GameBasics.Buildings[it]!!.isWonder })
            stats.culture += 33f
        if (policies.contains("Commerce") && isCapital)
            stats.gold += 25f
        if (policies.contains("Sovereignty") && cityInfo.civInfo.happiness >= 0)
            stats.science += 15f
        if (policies.contains("Aristocracy")
                && cityConstructions.getCurrentConstruction() is Building
                && (cityConstructions.getCurrentConstruction() as Building).isWonder)
            stats.production += 15f

        return stats
    }


    fun update() {
        val civInfo = cityInfo.civInfo

        val stats = Stats()
        stats.science += cityInfo.population.population.toFloat()
        stats.production += cityInfo.population.freePopulation.toFloat()

        stats.add(statsFromTiles)
        stats.add(getStatsFromSpecialists(cityInfo.population.specialists, civInfo.policies.adoptedPolicies))
        stats.add(statsFromTradeRoute)
        stats.add(cityInfo.cityConstructions.getStats())
        stats.add(getStatsFromPolicies(civInfo.policies.adoptedPolicies))

        val statPercentBonuses = cityInfo.cityConstructions.getStatPercentBonuses()
        statPercentBonuses.add(getStatPercentBonusesFromGoldenAge(cityInfo.civInfo.goldenAges.isGoldenAge()))
        statPercentBonuses.add(getStatPercentBonusesFromPolicies(civInfo.policies.adoptedPolicies, cityInfo.cityConstructions))
        statPercentBonuses.add(statPercentBonusesFromRailroad)
        statPercentBonuses.add(statPercentBonusesFromMarble)
        statPercentBonuses.add(statPercentBonusesFromComputers)

        stats.production *= 1 + statPercentBonuses.production / 100  // So they get bonuses for production and gold/science

        stats.add(statsFromProduction)


        stats.gold *= 1 + statPercentBonuses.gold / 100
        stats.science *= 1 + statPercentBonuses.science / 100
        stats.culture *= 1 + statPercentBonuses.culture / 100

        val isUnhappy = civInfo.happiness < 0
        if (!isUnhappy) stats.food *= 1 + statPercentBonuses.food / 100 // Regular food bonus revoked when unhappy per https://forums.civfanatics.com/resources/complete-guide-to-happiness-vanilla.25584/
        stats.food -= (cityInfo.population.population * 2).toFloat() // Food reduced after the bonus
        if (civInfo.policies.isAdopted("Civil Society"))
            stats.food += cityInfo.population.numberOfSpecialists.toFloat()

        if (isUnhappy) stats.food /= 4f // Reduce excess food to 1/4 per the same
        stats.food *= 1 + growthBonusFromPolicies

        stats.gold -= cityInfo.cityConstructions.getMaintenanceCosts().toFloat() // this is AFTER the bonus calculation!
        this.currentCityStats = stats
    }


    private fun isConnectedToCapital(roadType: RoadStatus): Boolean {
        if(cityInfo.civInfo.cities.count()<2) return false// first city!
        val capitalTile = cityInfo.civInfo.capital.tile
        val tilesReached = HashSet<TileInfo>()
        var tilesToCheck : List<TileInfo> = listOf(cityInfo.tile)
        while (tilesToCheck.any()) {
            val newTiles = tilesToCheck
                    .flatMap { cityInfo.tileMap.getTilesInDistance(it.position, 1) }.distinct()
                    .filter{ !tilesReached.contains(it) && !tilesToCheck.contains(it)
                            && (roadType !== RoadStatus.Road || it.roadStatus !== RoadStatus.None)
                            && (roadType !== RoadStatus.Railroad || it.roadStatus === roadType) }

            if (newTiles.contains(capitalTile)) return true
            tilesReached.addAll(tilesToCheck)
            tilesToCheck = newTiles
        }
        return false
    }

}
