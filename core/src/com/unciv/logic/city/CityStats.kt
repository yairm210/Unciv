package com.unciv.logic.city

import com.unciv.UnCivGame
import com.unciv.logic.map.BFS
import com.unciv.logic.map.RoadStatus
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.unit.BaseUnit
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats


class CityStats {

    @Transient var baseStatList = LinkedHashMap<String, Stats>()
    @Transient var statPercentBonusList = LinkedHashMap<String, Stats>()
    @Transient var happinessList = LinkedHashMap<String, Float>()
    @Transient var foodEaten=0f

    @Transient var currentCityStats: Stats = Stats()  // This is so we won't have to calculate this multiple times - takes a lot of time, especially on phones
    @Transient lateinit var cityInfo: CityInfo

    //region pure fuctions
    private fun getStatsFromTiles(): Stats {
        val stats = Stats()
        for (cell in cityInfo.getTilesInRange().filter { cityInfo.workedTiles.contains(it.position) || cityInfo.location == it.position })
            stats.add(cell.getTileStats(cityInfo, cityInfo.civInfo))
        return stats
    }

    private fun getStatsFromTradeRoute(): Stats {
        val stats = Stats()
        if (!cityInfo.isCapital() && isConnectedToCapital(RoadStatus.Road)) {
            val civInfo = cityInfo.civInfo
            var goldFromTradeRoute = civInfo.getCapital().population.population * 0.15 + cityInfo.population.population * 1.1 - 1 // Calculated by http://civilization.wikia.com/wiki/Trade_route_(Civ5)
            if(civInfo.getNation().unique=="+1 Gold from each Trade Route, Oil resources provide double quantity") goldFromTradeRoute += 1
            if (civInfo.policies.isAdopted("Trade Unions")) goldFromTradeRoute += 2
            if (civInfo.getBuildingUniques().contains("Gold from all trade routes +25%")) goldFromTradeRoute *= 1.25 // Machu Pichu speciality
            stats.gold += goldFromTradeRoute.toFloat()
        }
        return stats
    }

    private fun getStatsFromProduction(production: Float): Stats {
        val stats = Stats()

        when (cityInfo.cityConstructions.currentConstruction) {
            "Gold" -> stats.gold += production / 4
            "Science" -> {
                var scienceProduced = production / 4
                if (cityInfo.civInfo.getBuildingUniques().contains("Production to science conversion in cities increased by 33%"))
                    scienceProduced *= 1.33f
                if (cityInfo.civInfo.policies.isAdopted("Rationalism")) scienceProduced *= 1.33f
                stats.science += scienceProduced
            }
        }
        return stats
    }

    private fun getStatPercentBonusesFromRailroad(): Stats {
        val stats = Stats()
        val techEnablingRailroad = GameBasics.TileImprovements["Railroad"]!!.techRequired!!
        // If we conquered enemy cities connected by railroad, but we don't yet have that tech,
        // we shouldn't get bonuses, it's as if the tracks aare layed out but we can't operate them.
        if (cityInfo.civInfo.tech.isResearched(techEnablingRailroad)
                && (cityInfo.isCapital() || isConnectedToCapital(RoadStatus.Railroad)))
            stats.production += 25f
        return stats
    }

    private fun getStatPercentBonusesFromMarble(): Stats {
        val stats = Stats()
        val construction = cityInfo.cityConstructions.getCurrentConstruction()

        if (construction is Building
                && construction.isWonder
                && cityInfo.civInfo.hasResource("Marble"))
            stats.production += 15f

        return stats
    }

    private fun getStatPercentBonusesFromComputers(): Stats {
        val stats = Stats()

        if (cityInfo.civInfo.tech.getUniques().contains("+10% science and production in all cities")) {
            stats.production += 10f
            stats.science += 10f
        }

        return stats
    }

    private fun getStatPercentBonusesFromDifficulty(): Stats {
        val stats = Stats()

        val civ = cityInfo.civInfo
        if (!civ.isPlayerCivilization()) {
            val modifier = civ.gameInfo.getCurrentPlayerCivilization().getDifficulty().aiYieldModifier
            stats.production += modifier
            stats.science += modifier
            stats.food += modifier
            stats.gold += modifier
            stats.culture += modifier
        }

        return stats
    }

    private fun getStatsFromNationUnique(): Stats {
        val stats = Stats()

        val civUnique = cityInfo.civInfo.getNation().unique
        if(civUnique == "+2 Culture per turn from cities before discovering Steam Power")
            stats.culture += 2

        return stats
    }

    private fun getStatPercentBonusesFromNationUnique(): Stats {
        val stats = Stats()

        val civUnique = cityInfo.civInfo.getNation().unique
        val currentConstruction = cityInfo.cityConstructions.getCurrentConstruction()
        if(civUnique=="+25% Production towards any buildings that already exist in the Capital"
            && currentConstruction is Building
            && cityInfo.civInfo.getCapital().cityConstructions.builtBuildings
                        .contains(currentConstruction.name))
            stats.production+=25f

        if(civUnique=="+20% production towards Wonder construction"
            && currentConstruction is Building && currentConstruction.isWonder)
            stats.production+=20

        return stats
    }


    fun getGrowthBonusFromPolicies(): Float {
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
    fun getCityHappiness(): LinkedHashMap<String, Float> {
        val civInfo = cityInfo.civInfo
        val newHappinessList = LinkedHashMap<String,Float>()
        var unhappinessModifier = civInfo.getDifficulty().unhappinessModifier
        if(!civInfo.isPlayerCivilization())
            unhappinessModifier *= civInfo.gameInfo.getDifficulty().aiUnhappinessModifier

        newHappinessList ["Cities"] = -3f * unhappinessModifier

        var unhappinessFromCitizens = cityInfo.population.population.toFloat()
        if (civInfo.policies.isAdopted("Democracy"))
            unhappinessFromCitizens -= cityInfo.population.getNumberOfSpecialists() * 0.5f
        if (civInfo.getBuildingUniques().contains("Unhappiness from population decreased by 10%"))
            unhappinessFromCitizens *= 0.9f
        if (civInfo.policies.isAdopted("Meritocracy"))
            unhappinessFromCitizens *= 0.95f

        newHappinessList["Population"] = -unhappinessFromCitizens * unhappinessModifier

        var happinessFromPolicies = 0f
        if (civInfo.policies.isAdopted("Aristocracy"))
            happinessFromPolicies += (cityInfo.population.population / 10).toFloat()
        if (civInfo.policies.isAdopted("Monarchy") && cityInfo.isCapital())
            happinessFromPolicies += (cityInfo.population.population / 2).toFloat()
        if (civInfo.policies.isAdopted("Meritocracy") && isConnectedToCapital(RoadStatus.Road))
            happinessFromPolicies += 1f
        if(civInfo.policies.isAdopted("Military Caste") && cityInfo.getCenterTile().militaryUnit!=null)
            happinessFromPolicies+=1

        newHappinessList ["Policies"] = happinessFromPolicies

        val happinessFromBuildings = cityInfo.cityConstructions.getStats().happiness.toInt().toFloat()
        newHappinessList ["Buildings"] = happinessFromBuildings

        if(civInfo.getBuildingUniques().contains("+1 happiness in each city"))
            newHappinessList["Wonders"] = 1f

        // we don't want to modify the existing happiness list because that leads
        // to concurrency problems if we iterate on it while changing
        happinessList=newHappinessList
        return newHappinessList
    }

    fun getStatsOfSpecialist(stat:Stat, policies: HashSet<String>): Stats {
        val stats = Stats()
        if(stat==Stat.Culture||stat==Stat.Science)  stats.add(stat,3f)
        else stats.add(stat,2f) // science and gold specialists

        if (policies.contains("Commerce Complete")) stats.gold += 1
        if (policies.contains("Secularism")) stats.science += 2
        if(cityInfo.getBuildingUniques().contains("+1 Production from specialists"))
            stats.production += 1
        return stats
    }

    private fun getStatsFromSpecialists(specialists: Stats, policies: HashSet<String>): Stats {
        val stats = Stats()
        for(entry in specialists.toHashMap().filter { it.value>0 })
            stats.add(getStatsOfSpecialist(entry.key,policies)*entry.value)
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
        if(adoptedPolicies.contains("Military Caste") && cityInfo.getCenterTile().militaryUnit!=null)
            stats.culture += 2
        if (adoptedPolicies.contains("Universal Suffrage"))
            stats.production += (cityInfo.population.population / 5).toFloat()
        if (adoptedPolicies.contains("Free Speech"))
            stats.culture += (cityInfo.population.population / 2).toFloat()

        return stats
    }

    private fun getStatPercentBonusesFromGoldenAge(isGoldenAge: Boolean): Stats {
        val stats = Stats()
        if (isGoldenAge) {
            stats.production += 20f
            stats.culture += 20f
        }
        return stats
    }

    private fun getStatPercentBonusesFromBuildings(): Stats {
        val stats = cityInfo.cityConstructions.getStatPercentBonuses()
        val civUniques = cityInfo.civInfo.getBuildingUniques()
        if (civUniques.contains("Culture in all cities increased by 25%")) stats.culture += 25f

        val currentConstruction = cityInfo.cityConstructions.getCurrentConstruction()
        if(currentConstruction is Building && currentConstruction.uniques.contains("Spaceship part")){
            if(civUniques.contains("Increases production of spaceship parts by 25%"))
                stats.production += 25
            if(cityInfo.getBuildingUniques().contains("Increases production of spaceship parts by 50%"))
                stats.production += 50
        }

        if(currentConstruction is BaseUnit && currentConstruction.unitType==UnitType.Mounted
            && cityInfo.getBuildingUniques().contains("+15% Production when building Mounted Units in this city"))
            stats.production += 15

        return stats
    }

    private fun getStatPercentBonusesFromPolicies(policies: HashSet<String>, cityConstructions: CityConstructions): Stats {
        val stats = Stats()

        val currentConstruction = cityConstructions.getCurrentConstruction()
        if (policies.contains("Collective Rule") && cityInfo.isCapital()
                && currentConstruction.name == "Settler")
            stats.production += 50f
        if (policies.contains("Republic") && currentConstruction is Building)
            stats.production += 5f
        if(policies.contains("Warrior Code") && currentConstruction is BaseUnit && currentConstruction.unitType.isMelee())
            stats.production += 20
        if (policies.contains("Piety")
            && listOf("Monument", "Temple", "Opera House", "Museum", "Broadcast Tower").contains(currentConstruction.name)) 
            stats.production += 15f
        if (policies.contains("Reformation") && cityConstructions.getBuiltBuildings().any { it.isWonder })
            stats.culture += 33f
        if (policies.contains("Commerce") && cityInfo.isCapital())
            stats.gold += 25f
        if (policies.contains("Sovereignty") && cityInfo.civInfo.happiness >= 0)
            stats.science += 15f
        if (policies.contains("Total War") && currentConstruction is BaseUnit && !currentConstruction.unitType.isCivilian() )
            stats.production += 15f
        if (policies.contains("Aristocracy")
                && currentConstruction is Building
                && currentConstruction.isWonder)
            stats.production += 15f

        return stats
    }

    fun isConnectedToCapital(roadType: RoadStatus): Boolean {
        if (cityInfo.civInfo.cities.count() < 2) return false// first city!

        if(roadType==RoadStatus.Road)  return cityInfo.isConnectedToCapital // this transient is not applicable to connection via railroad.

        val capitalTile = cityInfo.civInfo.getCapital().getCenterTile()
        val bfs = BFS(capitalTile){it.roadStatus == roadType}

        val cityTile = cityInfo.getCenterTile()
        bfs.stepUntilDestination(cityTile)
        return bfs.tilesReached.containsKey(cityTile)
    }
    //endregion

    fun updateBaseStatList(){
        val newBaseStatList = LinkedHashMap<String, Stats>() // we don't edit the existing baseStatList directly, in order to avoid concurrency exceptions
        val civInfo = cityInfo.civInfo

        newBaseStatList["Population"] = Stats().add(Stat.Science, cityInfo.population.population.toFloat())
                .add(Stat.Production, cityInfo.population.getFreePopulation().toFloat())
        newBaseStatList["Tile yields"] = getStatsFromTiles()
        newBaseStatList["Specialists"] = getStatsFromSpecialists(cityInfo.population.specialists, civInfo.policies.adoptedPolicies)
        newBaseStatList["Trade routes"] = getStatsFromTradeRoute()
        newBaseStatList["Buildings"] = cityInfo.cityConstructions.getStats()
        newBaseStatList["Policies"] = getStatsFromPolicies(civInfo.policies.adoptedPolicies)
        newBaseStatList["National ability"] = getStatsFromNationUnique()

        baseStatList = newBaseStatList
    }

    fun updateStatPercentBonusList(){
        val newStatPercentBonusList = LinkedHashMap<String,Stats>()
        newStatPercentBonusList["Golden Age"]=getStatPercentBonusesFromGoldenAge(cityInfo.civInfo.goldenAges.isGoldenAge())
        newStatPercentBonusList["Policies"]=getStatPercentBonusesFromPolicies(cityInfo.civInfo.policies.adoptedPolicies, cityInfo.cityConstructions)
        newStatPercentBonusList["Buildings"]=getStatPercentBonusesFromBuildings()
        newStatPercentBonusList["Railroad"]=getStatPercentBonusesFromRailroad()
        newStatPercentBonusList["Marble"]=getStatPercentBonusesFromMarble()
        newStatPercentBonusList["Computers"]=getStatPercentBonusesFromComputers()
        newStatPercentBonusList["Difficulty"]=getStatPercentBonusesFromDifficulty()
        newStatPercentBonusList["National ability"]=getStatPercentBonusesFromNationUnique()

        if(UnCivGame.Current.superchargedForDebug) {
            val stats = Stats()
            for(stat in Stat.values()) stats.add(stat,10000f)
            newStatPercentBonusList["Supercharged"] = stats
        }

        statPercentBonusList=newStatPercentBonusList
    }

    fun update() {
        updateBaseStatList()
        updateStatPercentBonusList()

        val newCurrentCityStats = Stats() // again, we don't edit the existing currentCityStats directly, in order to avoid concurrency exceptions
        for(stat in baseStatList.values) newCurrentCityStats.add(stat)

        val statPercentBonusesSum = Stats()
        for(bonus in statPercentBonusList.values) statPercentBonusesSum.add(bonus)


        newCurrentCityStats.production *= 1 + statPercentBonusesSum.production / 100

        val statsFromProduction = getStatsFromProduction(newCurrentCityStats.production)
        newCurrentCityStats.add(statsFromProduction)
        baseStatList = LinkedHashMap(baseStatList).apply { put("Construction",statsFromProduction) } // concurrency-safe addition

        newCurrentCityStats.gold *= 1 + statPercentBonusesSum.gold / 100
        newCurrentCityStats.science *= 1 + statPercentBonusesSum.science / 100
        newCurrentCityStats.culture *= 1 + statPercentBonusesSum.culture / 100

        val isUnhappy = cityInfo.civInfo.happiness < 0
        if (!isUnhappy) // Regular food bonus revoked when unhappy per https://forums.civfanatics.com/resources/complete-guide-to-happiness-vanilla.25584/
            newCurrentCityStats.food *= 1 + statPercentBonusesSum.food / 100

        //
        /* Okay, food calculation is complicated.
        First we see how much food we generate. Then we apply production bonuses to it.
        Up till here, business as usual.
        Then, we deduct food eaten (from the total produced).
        Now we have the excess food, whih has its own things. If we're unhappy, cut it by 1/4.
        Some policies have bonuses for excess food only, not general food production.
         */

        updateFoodEaten()
        newCurrentCityStats.food -= foodEaten

        if (isUnhappy && newCurrentCityStats.food > 0) { // Reduce excess food to 1/4 per the same
            val foodReducedByUnhappiness = Stats().add(Stat.Food,newCurrentCityStats.food * (-3/4f))
            baseStatList = LinkedHashMap(baseStatList).apply { put("Unhappiness", foodReducedByUnhappiness) } // concurrency-safe addition
            newCurrentCityStats.add(foodReducedByUnhappiness)
        }

        // Since growth bonuses are special, (applied afterwards) they will be displayed separately in the user interface as well.
        val foodFromGrowthBonuses = getGrowthBonusFromPolicies() * newCurrentCityStats.food
        newCurrentCityStats.food += foodFromGrowthBonuses

        // Same here - will have a different UI display.
        val buildingsMaintenance = cityInfo.cityConstructions.getMaintenanceCosts() // this is AFTER the bonus calculation!
        newCurrentCityStats.gold -= buildingsMaintenance

        if(newCurrentCityStats.production<1) newCurrentCityStats.production=1f

        if (cityInfo.resistanceCounter > 0)
            currentCityStats = Stats().add(Stat.Production,1f) // You get nothing, Jon Snow
        else currentCityStats = newCurrentCityStats
    }

    private fun updateFoodEaten() {
        foodEaten = (cityInfo.population.population * 2).toFloat()
        if (cityInfo.civInfo.policies.isAdopted("Civil Society"))
            foodEaten -= cityInfo.population.getNumberOfSpecialists()
    }

}
