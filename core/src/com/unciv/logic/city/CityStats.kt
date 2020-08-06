package com.unciv.logic.city

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.CityStateType
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.RoadStatus
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Unique
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.UnitType
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
            stats.gold = civInfo.getCapital().population.population * 0.15f + cityInfo.population.population * 1.1f - 1 // Calculated by http://civilization.wikia.com/wiki/Trade_route_(Civ5)
            for (unique in civInfo.getMatchingUniques("[] from each Trade Route"))
                stats.add(Stats.parse(unique.params[0]))
            if (civInfo.hasUnique("Gold from all trade routes +25%")) stats.gold *= 1.25f // Machu Pichu speciality
        }
        return stats
    }

    private fun getStatsFromProduction(production: Float): Stats {
        val stats = Stats()

        when (cityInfo.cityConstructions.currentConstructionFromQueue) {
            "Gold" -> stats.gold += production / 4
            "Science" -> stats.science += production * getScienceConversionRate()
        }
        return stats
    }

    fun getScienceConversionRate(): Float {
        var conversionRate = 1/4f
        if (cityInfo.civInfo.hasUnique("Production to science conversion in cities increased by 33%"))
            conversionRate *= 1.33f
        return conversionRate
    }

    private fun getStatPercentBonusesFromRailroad(): Stats {
        val stats = Stats()
        val railroadImprovement = cityInfo.getRuleset().tileImprovements["Railroad"]
        if (railroadImprovement == null) return stats // for mods
        val techEnablingRailroad = railroadImprovement.techRequired!!
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
                && cityInfo.civInfo.getCivResources()
                        .any { it.amount > 0 && it.resource.unique == "+15% production towards Wonder construction" })
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

        stats.add(getStatsFromUniques(cityInfo.civInfo.nation.uniqueObjects.asSequence()))

        if (cityInfo.civInfo.hasUnique("+2 Culture per turn from cities before discovering Steam Power")
                && !cityInfo.civInfo.tech.isResearched("Steam Power"))
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

                if (cityInfo.civInfo.hasUnique("Food and Culture from Friendly City-States are increased by 50%"))
                    stats.food *= 1.5f
            }
        }

        return stats
    }

    private fun getStatPercentBonusesFromNationUnique(): Stats {
        val stats = Stats()

        stats.add(getStatPercentBonusesFromUniques(cityInfo.civInfo.nation.uniqueObjects.asSequence()))

        val currentConstruction = cityInfo.cityConstructions.getCurrentConstruction()
        if (currentConstruction is Building
                && cityInfo.civInfo.getCapital().cityConstructions.builtBuildings.contains(currentConstruction.name)
                && cityInfo.civInfo.hasUnique("+25% Production towards any buildings that already exist in the Capital"))
            stats.production += 25f

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
        if (cityInfo.civInfo.hasUnique("+10% food growth in capital") && cityInfo.isCapital())
            bonus += 0.1f
        if (cityInfo.civInfo.hasUnique("+15% growth in all cities"))
            bonus += 0.15f
        return bonus
    }

    // needs to be a separate function because we need to know the global happiness state
    // in order to determine how much food is produced in a city!
    fun updateCityHappiness() {
        val civInfo = cityInfo.civInfo
        val newHappinessList = LinkedHashMap<String, Float>()
        var unhappinessModifier = civInfo.getDifficulty().unhappinessModifier
        if (!civInfo.isPlayerCivilization())
            unhappinessModifier *= civInfo.gameInfo.getDifficulty().aiUnhappinessModifier

        var unhappinessFromCity = -3f     // -3 happiness per city
        if (civInfo.hasUnique("Unhappiness from number of Cities doubled"))
            unhappinessFromCity *= 2f//doubled for the Indian

        newHappinessList["Cities"] = unhappinessFromCity * unhappinessModifier

        var unhappinessFromCitizens = cityInfo.population.population.toFloat()
        if (civInfo.hasUnique("Specialists produce half normal unhappiness"))
            unhappinessFromCitizens -= cityInfo.population.getNumberOfSpecialists() * 0.5f

        if (cityInfo.isPuppet)
            unhappinessFromCitizens *= 1.5f
        else if (hasExtraAnnexUnhappiness())
            unhappinessFromCitizens *= 2f

        for(unique in civInfo.getMatchingUniques("Unhappiness from population decreased by []%"))
            unhappinessFromCitizens *= (1-unique.params[0].toFloat()/100)

        newHappinessList["Population"] = -unhappinessFromCitizens * unhappinessModifier

        var happinessFromPolicies = 0f
        if (civInfo.hasUnique("+1 happiness for every 10 citizens in a city"))
            happinessFromPolicies += (cityInfo.population.population / 10).toFloat()
        if (civInfo.hasUnique("+1 gold and -1 unhappiness for every 2 citizens in capital")
                && cityInfo.isCapital())
            happinessFromPolicies += (cityInfo.population.population / 2).toFloat()
        if (civInfo.hasUnique("+1 happiness for every city connected to capital")
                && cityInfo.isConnectedToCapital())
            happinessFromPolicies += 1f

        if (cityInfo.getCenterTile().militaryUnit != null)
            for (unique in civInfo.getMatchingUniques("[] in all cities with a garrison"))
                happinessFromPolicies += Stats.parse(unique.params[0]).happiness

        newHappinessList["Policies"] = happinessFromPolicies

        if (hasExtraAnnexUnhappiness()) newHappinessList["Occupied City"] = -2f //annexed city

        val happinessFromBuildings = cityInfo.cityConstructions.getStats().happiness.toInt().toFloat()
        newHappinessList["Buildings"] = happinessFromBuildings

        if (civInfo.hasUnique("+1 happiness in each city"))
            newHappinessList["Wonders"] = 1f

        newHappinessList["Tile yields"] = getStatsFromTiles().happiness

        // we don't want to modify the existing happiness list because that leads
        // to concurrency problems if we iterate on it while changing
        happinessList = newHappinessList
    }


    private fun hasExtraAnnexUnhappiness() : Boolean {
        if (cityInfo.civInfo.civName == cityInfo.foundingCiv || cityInfo.foundingCiv == "" || cityInfo.isPuppet) return false
        return !cityInfo.containsBuildingUnique("Remove extra unhappiness from annexed cities")
    }

    fun getStatsOfSpecialist(stat: Stat): Stats {
        val stats = Stats()
        if (stat == Stat.Culture || stat == Stat.Science) stats.add(stat, 3f)
        else stats.add(stat, 2f) // science and gold specialists

        for(unique in cityInfo.civInfo.getMatchingUniques("[] from every specialist"))
            stats.add(Stats.parse(unique.params[0]))
        if (cityInfo.civInfo.hasUnique("+1 Production from specialists"))
            stats.production += 1
        return stats
    }

    private fun getStatsFromSpecialists(specialists: Stats, policies: HashSet<String>): Stats {
        val stats = Stats()
        for (entry in specialists.toHashMap().filter { it.value > 0 })
            stats.add(getStatsOfSpecialist(entry.key) * entry.value)
        return stats
    }

    private fun getStatsFromUniques(uniques: Sequence<Unique>):Stats {
        val stats = Stats()

        for (unique in uniques) {
            if ((unique.placeholderText == "[] in capital" && cityInfo.isCapital())
                    || unique.placeholderText == "[] in all cities"
                    || (unique.placeholderText == "[] in all cities with a garrison" && cityInfo.getCenterTile().militaryUnit != null))
                stats.add(Stats.parse(unique.params[0]))
            if (unique.placeholderText == "[] per [] population in all cities") {
                val amountOfEffects = (cityInfo.population.population / unique.params[1].toInt()).toFloat()
                stats.add(Stats.parse(unique.params[0]).times(amountOfEffects))
            }
            if (unique.text == "+1 gold and -1 unhappiness for every 2 citizens in capital" && cityInfo.isCapital())
                stats.gold += (cityInfo.population.population / 2).toFloat()
        }

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
        val stats               = cityInfo.cityConstructions.getStatPercentBonuses()
        val currentConstruction = cityInfo.cityConstructions.getCurrentConstruction()

        if (currentConstruction is Building && currentConstruction.uniques.contains("Spaceship part")) {
            if (cityInfo.containsBuildingUnique("Increases production of spaceship parts by 15%"))
                stats.production += 15
            if (cityInfo.civInfo.hasUnique("Increases production of spaceship parts by 25%"))
                stats.production += 25
            if (cityInfo.containsBuildingUnique("Increases production of spaceship parts by 50%"))
                stats.production += 50
        }

        if (currentConstruction is BaseUnit) {
            if (currentConstruction.unitType == UnitType.Mounted
                    && cityInfo.containsBuildingUnique("+15% Production when building Mounted Units in this city"))
                stats.production += 15
            if (currentConstruction.unitType.isLandUnit()
                    && cityInfo.containsBuildingUnique("+15% production of land units"))
                stats.production += 15
            if (currentConstruction.unitType.isWaterUnit()
                    && cityInfo.containsBuildingUnique("+15% production of naval units"))
                stats.production += 15
        }

        return stats
    }

    private fun getStatPercentBonusesFromUniques(uniques: Sequence<Unique>): Stats {
        val stats = Stats()

        val currentConstruction = cityInfo.cityConstructions.getCurrentConstruction()
        if (currentConstruction.name == Constants.settler && cityInfo.isCapital()
                && uniques.any { it.text == "Training of settlers increased +50% in capital" })
            stats.production += 50f

        if (currentConstruction is Building && !currentConstruction.isWonder)
            for (unique in uniques.filter { it.placeholderText == "+[]% Production when constructing [] buildings" }) {
                val stat = Stat.valueOf(unique.params[1])
                if (currentConstruction.isStatRelated(stat))
                    stats.production += unique.params[0].toInt()
            }

        for (unique in uniques.filter { it.placeholderText == "+[]% Production when constructing []" }) {
            val filter = unique.params[1]
            if (currentConstruction.name == filter
                    || (filter == "military units" && currentConstruction is BaseUnit && !currentConstruction.unitType.isCivilian())
                    || (filter == "melee units" && currentConstruction is BaseUnit && currentConstruction.unitType.isMelee())
                    || (filter == "Buildings" && currentConstruction is Building && !currentConstruction.isWonder)
                    || (filter == "Wonders" && currentConstruction is Building && currentConstruction.isWonder))
                stats.production += unique.params[0].toInt()
        }

        if (cityInfo.cityConstructions.getBuiltBuildings().any { it.isWonder }
                && uniques.any { it.text == "+33% culture in all cities with a world wonder" })
            stats.culture += 33f
        if (uniques.any { it.text == "+25% gold in capital" } && cityInfo.isCapital())
            stats.gold += 25f
        if (cityInfo.civInfo.getHappiness() >= 0 && uniques.any { it.text == "+15% science while empire is happy" })
            stats.science += 15f

        if (uniques.any { it.text == "Culture in all cities increased by 25%" })
            stats.culture += 25f

        return stats
    }

    fun isConnectedToCapital(roadType: RoadStatus): Boolean {
        if (cityInfo.civInfo.cities .count() < 2) return false// first city!

        // Railroad, or harbor from railroad
        if (roadType == RoadStatus.Railroad) return cityInfo.isConnectedToCapital { it.any { it.contains("Railroad") } }
        else return cityInfo.isConnectedToCapital()
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
        newBaseStatList["Policies"] = getStatsFromUniques(civInfo.policies.policyUniques.getAllUniques())
        newBaseStatList["National ability"] = getStatsFromNationUnique()
        newBaseStatList["Wonders"] = getStatsFromUniques(civInfo.cities.asSequence().flatMap { it.cityConstructions.builtBuildingUniqueMap.getAllUniques() })
        newBaseStatList["City-States"] = getStatsFromCityStates()

        baseStatList = newBaseStatList
    }


    fun updateStatPercentBonusList() {
        val newStatPercentBonusList = LinkedHashMap<String, Stats>()
        newStatPercentBonusList["Golden Age"] = getStatPercentBonusesFromGoldenAge(cityInfo.civInfo.goldenAges.isGoldenAge())
        newStatPercentBonusList["Policies"] = getStatPercentBonusesFromUniques(cityInfo.civInfo.policies.policyUniques.getAllUniques())
        newStatPercentBonusList["Buildings"] = getStatPercentBonusesFromBuildings()
        newStatPercentBonusList["Wonders"] = getStatPercentBonusesFromUniques(cityInfo.civInfo.getBuildingUniques())
        newStatPercentBonusList["Railroad"] = getStatPercentBonusesFromRailroad()
        newStatPercentBonusList["Marble"] = getStatPercentBonusesFromMarble()
        newStatPercentBonusList["Computers"] = getStatPercentBonusesFromComputers()
        newStatPercentBonusList["National ability"] = getStatPercentBonusesFromNationUnique()
        newStatPercentBonusList["Puppet City"] = getStatPercentBonusesFromPuppetCity()

        if (UncivGame.Current.superchargedForDebug) {
            val stats = Stats()
            for (stat in Stat.values()) stats.add(stat, 10000f)
            newStatPercentBonusList["Supercharged"] = stats
        }

        statPercentBonusList = newStatPercentBonusList
    }

    fun update() {
        // We need to compute Tile yields before happiness
        updateBaseStatList()
        updateCityHappiness()
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
        if(totalFood>0) {
            val foodFromGrowthBonuses = getGrowthBonusFromPolicies() * totalFood
            newFinalStatList["Policies"]!!.food += foodFromGrowthBonuses
            totalFood = newFinalStatList.values.map { it.food }.sum() // recalculate again
        }


        // Same here - will have a different UI display.
        var buildingsMaintenance = cityInfo.cityConstructions.getMaintenanceCosts().toFloat() // this is AFTER the bonus calculation!
        if (!cityInfo.civInfo.isPlayerCivilization()) {
            buildingsMaintenance *= cityInfo.civInfo.gameInfo.getDifficulty().aiBuildingMaintenanceModifier
        }
        newFinalStatList["Maintenance"] = Stats().apply { gold -= buildingsMaintenance.toInt() }


        if (cityInfo.cityConstructions.currentConstructionFromQueue == Constants.settler && totalFood > 0) {
            newFinalStatList["Excess food to production"] =
                    Stats().apply { production = totalFood; food = -totalFood }
        }

        if (cityInfo.isInResistance())
            newFinalStatList.clear()  // NOPE

        if (newFinalStatList.values.map { it.production }.sum() < 1)  // Minimum production for things to progress
            newFinalStatList["Production"] = Stats().apply { production += 1 }
        finalStatList = newFinalStatList
    }

    private fun updateFoodEaten() {
        foodEaten = (cityInfo.population.population * 2).toFloat()
        if (cityInfo.civInfo.hasUnique("-50% food consumption by specialists"))
            foodEaten -= cityInfo.population.getNumberOfSpecialists()
    }

}
