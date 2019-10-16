package com.unciv.logic.city

import com.unciv.Constants
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CityStateType
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.BFS
import com.unciv.logic.map.RoadStatus
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.unit.BaseUnit
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats


class CityStats {

    @Transient
    var baseStatList = LinkedHashMap<String, Stats>()
    @Transient
    var statPercentBonusList = LinkedHashMap<String, Stats>()

    // Computed from baseStatList and statPercentBonusList - this is so the players can see a breakdown
    @Transient
    var finalStatList = LinkedHashMap<String, Stats>()

    @Transient
    var happinessList = LinkedHashMap<String, Float>()
    @Transient
    var foodEaten = 0f

    @Transient
    var currentCityStats: Stats = Stats()  // This is so we won't have to calculate this multiple times - takes a lot of time, especially on phones
    @Transient
    lateinit var cityInfo: CityInfo

    //region pure fuctions
    private fun getStatsFromTiles(): Stats {
        val stats = Stats()
        for (cell in cityInfo.tilesInRange
                .filter { cityInfo.location == it.position || cityInfo.workedTiles.contains(it.position) })
            stats.add(cell.getTileStats(cityInfo, cityInfo.civInfo))
        return stats
    }

    private fun getStatsFromTradeRoute(): Stats {
        val stats = Stats()
        if (!cityInfo.isCapital() && cityInfo.isConnectedToCapital()) {
            val civInfo = cityInfo.civInfo
            var goldFromTradeRoute = civInfo.getCapital().population.population * 0.15 + cityInfo.population.population * 1.1 - 1 // Calculated by http://civilization.wikia.com/wiki/Trade_route_(Civ5)
            if (civInfo.nation.unique == "+1 Gold from each Trade Route, Oil resources provide double quantity") goldFromTradeRoute += 1
            if (civInfo.policies.isAdopted("Trade Unions")) goldFromTradeRoute += 2
            if (civInfo.containsBuildingUnique("Gold from all trade routes +25%")) goldFromTradeRoute *= 1.25 // Machu Pichu speciality
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
                if (cityInfo.civInfo.containsBuildingUnique("Production to science conversion in cities increased by 33%"))
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

        if (cityInfo.civInfo.tech.getTechUniques().contains("+10% science and production in all cities")) {
            stats.production += 10f
            stats.science += 10f
        }

        return stats
    }

    private fun getStatsFromNationUnique(): Stats {
        val stats = Stats()

        val civUnique = cityInfo.civInfo.nation.unique
        if (civUnique == "+2 Culture per turn from cities before discovering Steam Power")
            stats.culture += 2

        return stats
    }

    private fun getStatsFromCityStates(): Stats {
        val stats = Stats()

        for (otherCiv in cityInfo.civInfo.getKnownCivs()) {
            if (otherCiv.isCityState() && otherCiv.getCityStateType() == CityStateType.Maritime
                    && otherCiv.getDiplomacyManager(cityInfo.civInfo).relationshipLevel() >= RelationshipLevel.Friend) {
                if (cityInfo.isCapital()) stats.food += 3
                else stats.food += 1
            }
        }

        return stats
    }

    private fun getStatPercentBonusesFromNationUnique(): Stats {
        val stats = Stats()

        val civUnique = cityInfo.civInfo.nation.unique
        val currentConstruction = cityInfo.cityConstructions.getCurrentConstruction()
        if (civUnique == "+25% Production towards any buildings that already exist in the Capital"
                && currentConstruction is Building
                && cityInfo.civInfo.getCapital().cityConstructions.builtBuildings
                        .contains(currentConstruction.name))
            stats.production += 25f

        if (civUnique == "+20% production towards Wonder construction"
                && currentConstruction is Building && currentConstruction.isWonder)
            stats.production += 20

        return stats
    }

    private fun getStatPercentBonusesFromPuppetCity(): Stats {
        val stats = Stats()
        if (cityInfo.isPuppet) {
            stats.science -= 25f
            stats.culture -= 25f
        }
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
    fun updateCityHappiness() {
        val civInfo = cityInfo.civInfo
        val newHappinessList = LinkedHashMap<String, Float>()
        var unhappinessModifier = civInfo.getDifficulty().unhappinessModifier
        if (!civInfo.isPlayerCivilization())
            unhappinessModifier *= civInfo.gameInfo.getDifficulty().aiUnhappinessModifier

        var unhappinessFromCity = -3f
        if (civInfo.nation.unique == "Unhappiness from number of Cities doubled, Unhappiness from number of Citizens halved.")
            unhappinessFromCity *= 2f//doubled for the Indian

        newHappinessList["Cities"] = unhappinessFromCity * unhappinessModifier

        var unhappinessFromCitizens = cityInfo.population.population.toFloat()
        if (civInfo.policies.isAdopted("Democracy"))
            unhappinessFromCitizens -= cityInfo.population.getNumberOfSpecialists() * 0.5f

        if (cityInfo.isPuppet)
            unhappinessFromCitizens *= 1.5f
        else if (hasExtraAnnexUnhappiness())
            unhappinessFromCitizens *= 2f
        if (civInfo.containsBuildingUnique("Unhappiness from population decreased by 10%"))
            unhappinessFromCitizens *= 0.9f
        if (civInfo.policies.isAdopted("Meritocracy"))
            unhappinessFromCitizens *= 0.95f
        if (civInfo.nation.unique == "Unhappiness from number of Cities doubled, Unhappiness from number of Citizens halved.")
            unhappinessFromCitizens *= 0.5f //halved for the Indian

        newHappinessList["Population"] = -unhappinessFromCitizens * unhappinessModifier

        var happinessFromPolicies = 0f
        if (civInfo.policies.isAdopted("Aristocracy"))
            happinessFromPolicies += (cityInfo.population.population / 10).toFloat()
        if (civInfo.policies.isAdopted("Monarchy") && cityInfo.isCapital())
            happinessFromPolicies += (cityInfo.population.population / 2).toFloat()
        if (civInfo.policies.isAdopted("Meritocracy") && cityInfo.isConnectedToCapital())
            happinessFromPolicies += 1f
        if (civInfo.policies.isAdopted("Military Caste") && cityInfo.getCenterTile().militaryUnit != null)
            happinessFromPolicies += 1

        newHappinessList["Policies"] = happinessFromPolicies

        if (hasExtraAnnexUnhappiness()) newHappinessList["Occupied City"] = -2f //annexed city

        val happinessFromBuildings = cityInfo.cityConstructions.getStats().happiness.toInt().toFloat()
        newHappinessList["Buildings"] = happinessFromBuildings

        if (civInfo.containsBuildingUnique("+1 happiness in each city"))
            newHappinessList["Wonders"] = 1f

        // we don't want to modify the existing happiness list because that leads
        // to concurrency problems if we iterate on it while changing
        happinessList = newHappinessList
    }


    private fun hasExtraAnnexUnhappiness() : Boolean {
        if (cityInfo.civInfo.civName == cityInfo.foundingCiv || cityInfo.foundingCiv == "" || cityInfo.isPuppet) return false
        return !cityInfo.containsBuildingUnique("Remove extra unhappiness from annexed cities")
    }

    fun getStatsOfSpecialist(stat: Stat, policies: HashSet<String>): Stats {
        val stats = Stats()
        if (stat == Stat.Culture || stat == Stat.Science) stats.add(stat, 3f)
        else stats.add(stat, 2f) // science and gold specialists

        if (policies.contains("Commerce Complete")) stats.gold += 1
        if (policies.contains("Secularism")) stats.science += 2
        if (cityInfo.civInfo.containsBuildingUnique("+1 Production from specialists"))
            stats.production += 1
        if(cityInfo.civInfo.nation.unique=="+2 Science for all specialists and Great Person tile improvements")
            stats.science+=2
        return stats
    }

    private fun getStatsFromSpecialists(specialists: Stats, policies: HashSet<String>): Stats {
        val stats = Stats()
        for (entry in specialists.toHashMap().filter { it.value > 0 })
            stats.add(getStatsOfSpecialist(entry.key, policies) * entry.value)
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
        if (adoptedPolicies.contains("Military Caste") && cityInfo.getCenterTile().militaryUnit != null)
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
        if (cityInfo.civInfo.containsBuildingUnique("Culture in all cities increased by 25%")) stats.culture += 25f

        val currentConstruction = cityInfo.cityConstructions.getCurrentConstruction()
        if (currentConstruction is Building && currentConstruction.uniques.contains("Spaceship part")) {
            if (cityInfo.civInfo.containsBuildingUnique("Increases production of spaceship parts by 25%"))
                stats.production += 25
            if (cityInfo.containsBuildingUnique("Increases production of spaceship parts by 50%"))
                stats.production += 50
        }

        if (currentConstruction is BaseUnit && currentConstruction.unitType == UnitType.Mounted
                && cityInfo.containsBuildingUnique("+15% Production when building Mounted Units in this city"))
            stats.production += 15

        return stats
    }

    private fun getStatPercentBonusesFromPolicies(policies: HashSet<String>, cityConstructions: CityConstructions): Stats {
        val stats = Stats()

        val currentConstruction = cityConstructions.getCurrentConstruction()
        if (policies.contains("Collective Rule") && cityInfo.isCapital()
                && currentConstruction.name == Constants.settler)
            stats.production += 50f
        if (policies.contains("Republic") && currentConstruction is Building)
            stats.production += 5f
        if (policies.contains("Warrior Code") && currentConstruction is BaseUnit && currentConstruction.unitType.isMelee())
            stats.production += 20
        if (policies.contains("Piety")
                && listOf("Monument", "Temple", "Opera House", "Museum", "Broadcast Tower").contains(currentConstruction.name))
            stats.production += 15f
        if (policies.contains("Reformation") && cityConstructions.getBuiltBuildings().any { it.isWonder })
            stats.culture += 33f
        if (policies.contains("Commerce") && cityInfo.isCapital())
            stats.gold += 25f
        if (policies.contains("Sovereignty") && cityInfo.civInfo.getHappiness() >= 0)
            stats.science += 15f
        if (policies.contains("Total War") && currentConstruction is BaseUnit && !currentConstruction.unitType.isCivilian())
            stats.production += 15f
        if (policies.contains("Aristocracy")
                && currentConstruction is Building
                && currentConstruction.isWonder)
            stats.production += 15f

        return stats
    }

    fun isConnectedToCapital(roadType: RoadStatus): Boolean {
        if (cityInfo.civInfo.cities.count() < 2) return false// first city!

        if (roadType == RoadStatus.Road) return cityInfo.isConnectedToCapital() // this transient is not applicable to connection via railroad.

        val capitalTile = cityInfo.civInfo.getCapital().getCenterTile()
        val bfs = BFS(capitalTile) { it.roadStatus == roadType }

        val cityTile = cityInfo.getCenterTile()
        bfs.stepUntilDestination(cityTile)
        return bfs.tilesReached.containsKey(cityTile)
    }
    //endregion

    fun updateBaseStatList() {
        val newBaseStatList = LinkedHashMap<String, Stats>() // we don't edit the existing baseStatList directly, in order to avoid concurrency exceptions
        val civInfo = cityInfo.civInfo

        newBaseStatList["Population"] = Stats().apply {
            science = cityInfo.population.population.toFloat()
            production = cityInfo.population.getFreePopulation().toFloat()
        }
        newBaseStatList["Tile yields"] = getStatsFromTiles()
        newBaseStatList["Specialists"] = getStatsFromSpecialists(cityInfo.population.specialists, civInfo.policies.adoptedPolicies)
        newBaseStatList["Trade routes"] = getStatsFromTradeRoute()
        newBaseStatList["Buildings"] = cityInfo.cityConstructions.getStats()
        newBaseStatList["Policies"] = getStatsFromPolicies(civInfo.policies.adoptedPolicies)
        newBaseStatList["National ability"] = getStatsFromNationUnique()
        newBaseStatList["City States"] = getStatsFromCityStates()

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
        newStatPercentBonusList["National ability"]=getStatPercentBonusesFromNationUnique()
        newStatPercentBonusList["Puppet City"]=getStatPercentBonusesFromPuppetCity()

        if(UnCivGame.Current.superchargedForDebug) {
            val stats = Stats()
            for(stat in Stat.values()) stats.add(stat,10000f)
            newStatPercentBonusList["Supercharged"] = stats
        }

        statPercentBonusList=newStatPercentBonusList
    }

    fun update() {
        updateCityHappiness()
        updateBaseStatList()
        updateStatPercentBonusList()

        updateFinalStatList() // again, we don't edit the existing currentCityStats directly, in order to avoid concurrency exceptions
        
        val newCurrentCityStats = Stats()
        for(stat in finalStatList.values) newCurrentCityStats.add(stat)
        currentCityStats = newCurrentCityStats

        cityInfo.civInfo.updateStatsForNextTurn()
    }

    private fun updateFinalStatList(){
        val newFinalStatList = LinkedHashMap<String, Stats>() // again, we don't edit the existing currentCityStats directly, in order to avoid concurrency exceptions

        for (entry in baseStatList)
            newFinalStatList[entry.key] = entry.value.clone()

        val statPercentBonusesSum = Stats()
        for (bonus in statPercentBonusList.values) statPercentBonusesSum.add(bonus)

        for (entry in newFinalStatList.values)
            entry.production *= 1 + statPercentBonusesSum.production / 100

        val statsFromProduction = getStatsFromProduction(newFinalStatList.values.map { it.production }.sum())
        baseStatList = LinkedHashMap(baseStatList).apply { put("Construction", statsFromProduction) } // concurrency-safe addition
        newFinalStatList["Construction"] = statsFromProduction

        val isUnhappy = cityInfo.civInfo.getHappiness() < 0
        for (entry in newFinalStatList.values) {
            entry.gold *= 1 + statPercentBonusesSum.gold / 100
            entry.science *= 1 + statPercentBonusesSum.science / 100
            entry.culture *= 1 + statPercentBonusesSum.culture / 100
            if (!isUnhappy) entry.food *= 1 + statPercentBonusesSum.food / 100 // Regular food bonus revoked when unhappy per https://forums.civfanatics.com/resources/complete-guide-to-happiness-vanilla.25584/
        }

        //
        /* Okay, food calculation is complicated.
        First we see how much food we generate. Then we apply production bonuses to it.
        Up till here, business as usual.
        Then, we deduct food eaten (from the total produced).
        Now we have the excess food, whih has its own things. If we're unhappy, cut it by 1/4.
        Some policies have bonuses for excess food only, not general food production.
         */

        updateFoodEaten()
        newFinalStatList["Population"]!!.food -= foodEaten

        var totalFood = newFinalStatList.values.map { it.food }.sum()

        if (isUnhappy && totalFood > 0) { // Reduce excess food to 1/4 per the same
            val foodReducedByUnhappiness = Stats().apply { food = totalFood * (-3 / 4f) }
            baseStatList = LinkedHashMap(baseStatList).apply { put("Unhappiness", foodReducedByUnhappiness) } // concurrency-safe addition
            newFinalStatList["Unhappiness"] = foodReducedByUnhappiness
        }

        totalFood = newFinalStatList.values.map { it.food }.sum() // recalculate because of previous change

        // Since growth bonuses are special, (applied afterwards) they will be displayed separately in the user interface as well.
        val foodFromGrowthBonuses = getGrowthBonusFromPolicies() * totalFood
        newFinalStatList["Policies"]!!.food += foodFromGrowthBonuses

        // Same here - will have a different UI display.
        var buildingsMaintenance = cityInfo.cityConstructions.getMaintenanceCosts().toFloat() // this is AFTER the bonus calculation!
        if (!cityInfo.civInfo.isPlayerCivilization()) {
            buildingsMaintenance *= cityInfo.civInfo.gameInfo.getDifficulty().aiBuildingMaintenanceModifier
        }
        newFinalStatList["Maintenance"] = Stats().apply { gold -= buildingsMaintenance.toInt() }

        if (cityInfo.resistanceCounter > 0)
            newFinalStatList.clear()  // NOPE

        if (newFinalStatList.values.map { it.production }.sum() < 1)  // Minimum production for things to progress
            newFinalStatList["Production"] = Stats().apply { production += 1 }
        finalStatList = newFinalStatList
    }

    private fun updateFoodEaten() {
        foodEaten = (cityInfo.population.population * 2).toFloat()
        if (cityInfo.civInfo.policies.isAdopted("Civil Society"))
            foodEaten -= cityInfo.population.getNumberOfSpecialists()
    }

}
