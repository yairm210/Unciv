package com.unciv.logic.city

import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats


class CityStats {

    @Transient var baseStatList = LinkedHashMap<String,Stats>()
    @Transient var statModifiers = LinkedHashMap<String,Stats>()
    @Transient var currentCityStats: Stats = Stats()  // This is so we won't have to calculate this multiple times - takes a lot of time, especially on phones
    @Transient lateinit var cityInfo: CityInfo

    private fun getStatsFromTiles(): Stats {
        val stats = Stats()
        for (cell in cityInfo.getTilesInRange().filter { cityInfo.workedTiles.contains(it.position) || cityInfo.location==it.position})
            stats.add(cell.getTileStats(cityInfo, cityInfo.civInfo))
        return stats
    }

    private

    fun getStatsFromTradeRoute(): Stats {
        val stats = Stats()
        if (!cityInfo.isCapital() && isConnectedToCapital(RoadStatus.Road)) {
            val civInfo = cityInfo.civInfo
            var goldFromTradeRoute = civInfo.getCapital().population.population * 0.15 + cityInfo.population.population * 1.1 - 1 // Calculated by http://civilization.wikia.com/wiki/Trade_route_(Civ5)
            if (civInfo.policies.isAdopted("Trade Unions")) goldFromTradeRoute += 2.0
            if (civInfo.buildingUniques.contains("TradeRouteGoldIncrease")) goldFromTradeRoute *= 1.25 // Machu Pichu speciality
            stats.gold += goldFromTradeRoute.toFloat()
        }
        return stats
    }


    private fun getStatsFromProduction(production:Float): Stats {
        val stats = Stats()

        when(cityInfo.cityConstructions.currentConstruction) {
            "Gold" -> stats.gold += production / 4
            "Science" -> {
                var scienceProduced = production / 4
                if (cityInfo.civInfo.buildingUniques.contains("ScienceConversionIncrease")) scienceProduced *= 1.33f
                if (cityInfo.civInfo.policies.isAdopted("Rationalism")) scienceProduced *= 1.33f
                stats.science += scienceProduced
            }
        }
        return stats
    }


    private fun getStatPercentBonusesFromRailroad(): Stats {
        val stats = Stats()
        if (cityInfo.civInfo.tech.isResearched("Combustion")
                && (cityInfo.isCapital() || isConnectedToCapital(RoadStatus.Railroad)))
            stats.production += 25f
        return stats
    }

    private fun getStatPercentBonusesFromMarble(): Stats {
        val stats = Stats()
        val construction = cityInfo.cityConstructions.getCurrentConstruction()

        if (construction is Building
                && construction.isWonder
                && cityInfo.civInfo.getCivResources().containsKey(GameBasics.TileResources["Marble"]))
            stats.production += 15f

        return stats
    }

    private fun getStatPercentBonusesFromComputers(): Stats {
        val stats = Stats()

        if (cityInfo.civInfo.tech.isResearched("Computers")) {
            stats.production += 10f
            stats.science += 10f
        }

        return stats
    }

    private fun getGrowthBonusFromPolicies(): Float {
        var bonus = 0f
        if (cityInfo.civInfo.policies.isAdopted("Landed Elite") && cityInfo.isCapital())
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
            unhappinessFromCitizens -= cityInfo.population.getNumberOfSpecialists() * 0.5f
        if (civInfo.buildingUniques.contains("CitizenUnhappinessDecreased"))
            unhappinessFromCitizens *= 0.9f
        if (civInfo.policies.isAdopted("Aristocracy"))
            unhappinessFromCitizens *= 0.95f
        happiness -= unhappinessFromCitizens

        if (civInfo.policies.isAdopted("Aristocracy"))
            happiness += (cityInfo.population.population / 10).toFloat()
        if (civInfo.policies.isAdopted("Monarchy") && cityInfo.isCapital())
            happiness += (cityInfo.population.population / 2).toFloat()
        if (civInfo.policies.isAdopted("Meritocracy") && isConnectedToCapital(RoadStatus.Road))
            happiness += 1f

        happiness += cityInfo.cityConstructions.getStats().happiness.toInt().toFloat()

        return happiness
    }

    private fun getStatsFromSpecialists(specialists: Stats, policies: HashSet<String>): Stats {
        val stats = Stats()

        // Specialists
        stats.culture += specialists.culture * 3
        stats.production += specialists.production * 2
        stats.science += specialists.science * 3
        stats.gold += specialists.gold * 2
        val numOfSpecialists = cityInfo.population.getNumberOfSpecialists()
        if (policies.contains("Commerce Complete")) stats.gold += numOfSpecialists.toFloat()
        if (policies.contains("Secularism")) stats.science += (numOfSpecialists * 2).toFloat()

        return stats
    }

    private fun getStatsFromPolicies(adoptedPolicies: HashSet<String>): Stats {
        val stats = Stats()
        if (adoptedPolicies.contains("Tradition") && cityInfo.isCapital())
            stats.culture += 3f
        if (adoptedPolicies.contains("Landed Elite") && cityInfo.isCapital())
            stats.food += 2f
        if (adoptedPolicies.contains("Tradition Complete"))
            stats.food += 2f
        if (adoptedPolicies.contains("Monarchy") && cityInfo.isCapital())
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

    private fun getStatPercentBonusesFromPolicies(policies: HashSet<String>, cityConstructions: CityConstructions): Stats {
        val stats = Stats()

        if (policies.contains("Collective Rule") && cityInfo.isCapital()
                && "Settler" == cityConstructions.currentConstruction)
            stats.production += 50f
        if (policies.contains("Republic") && cityConstructions.getCurrentConstruction() is Building)
            stats.production += 5f
        if (policies.contains("Reformation") && cityConstructions.builtBuildings.any { GameBasics.Buildings[it]!!.isWonder })
            stats.culture += 33f
        if (policies.contains("Commerce") && cityInfo.isCapital())
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
        baseStatList = LinkedHashMap<String, Stats>()
        val civInfo = cityInfo.civInfo

        baseStatList["Population"] = Stats().add(Stat.Science,cityInfo.population.population.toFloat())
                .add(Stat.Production,cityInfo.population.getFreePopulation().toFloat())
        baseStatList["Tile yields"] = getStatsFromTiles()
        baseStatList["Specialists"] = getStatsFromSpecialists(cityInfo.population.getSpecialists(), civInfo.policies.adoptedPolicies)
        baseStatList["Trade route"] = getStatsFromTradeRoute()
        baseStatList["Buildings"] = cityInfo.cityConstructions.getStats()
        baseStatList["Policies"] = getStatsFromPolicies(civInfo.policies.adoptedPolicies)

        val statPercentBonuses = cityInfo.cityConstructions.getStatPercentBonuses()
        statPercentBonuses.add(getStatPercentBonusesFromGoldenAge(cityInfo.civInfo.goldenAges.isGoldenAge()))
        statPercentBonuses.add(getStatPercentBonusesFromPolicies(civInfo.policies.adoptedPolicies, cityInfo.cityConstructions))
        statPercentBonuses.add(getStatPercentBonusesFromRailroad())
        statPercentBonuses.add(getStatPercentBonusesFromMarble())
        statPercentBonuses.add(getStatPercentBonusesFromComputers())

        val stats = Stats()
        for (stat in baseStatList.values) stats.add(stat)
        stats.production *= 1 + statPercentBonuses.production / 100  // So they get bonuses for production and gold/science

        val statsFromProduction = getStatsFromProduction(stats.production)
        stats.add(statsFromProduction)
        baseStatList["Construction"] = statsFromProduction


        stats.gold *= 1 + statPercentBonuses.gold / 100
        stats.science *= 1 + statPercentBonuses.science / 100
        stats.culture *= 1 + statPercentBonuses.culture / 100

        val isUnhappy = civInfo.happiness < 0
        if (!isUnhappy) stats.food *= 1 + statPercentBonuses.food / 100 // Regular food bonus revoked when unhappy per https://forums.civfanatics.com/resources/complete-guide-to-happiness-vanilla.25584/
        val foodEaten = (cityInfo.population.population * 2).toFloat()
        baseStatList["Population"]!!.food -= foodEaten // to display it to the user
        stats.food -= foodEaten // Food reduced after the bonus
        if (civInfo.policies.isAdopted("Civil Society"))
            stats.food += cityInfo.population.getNumberOfSpecialists().toFloat()

        if (isUnhappy) stats.food /= 4f // Reduce excess food to 1/4 per the same
        stats.food *= 1 + getGrowthBonusFromPolicies()

        val buildingsMaintainance = cityInfo.cityConstructions.getMaintenanceCosts().toFloat() // this is AFTER the bonus calculation!
        baseStatList["Buildings"]!!.gold -= buildingsMaintainance
        stats.gold -= buildingsMaintainance
        this.currentCityStats = stats
    }

    private fun isConnectedToCapital(roadType: RoadStatus): Boolean {
        if(cityInfo.civInfo.cities.count()<2) return false// first city!
        val capitalTile = cityInfo.civInfo.getCapital().getCenterTile()
        val tilesReached = HashSet<TileInfo>()
        var tilesToCheck : List<TileInfo> = listOf(cityInfo.getCenterTile())
        while (tilesToCheck.isNotEmpty()) {
            val newTiles = tilesToCheck
                    .flatMap { it.neighbors }.distinct()
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
