package com.unciv.logic.civilization

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.GameInfo
import com.unciv.models.linq.Linq
import com.unciv.models.linq.LinqCounter
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ResourceType
import com.unciv.models.gamebasics.TileResource
import com.unciv.models.stats.Stats


class CivilizationInfo {

    @Transient
    var gameInfo: GameInfo = GameInfo()

    var gold = 0
    var happiness = 15
    var civName = "Babylon"

    var tech = TechManager()
    var policies = PolicyManager()
    var goldenAges = GoldenAgeManager()
    private var greatPeople = GreatPersonManager()
    var scienceVictory = ScienceVictoryManager()

    var cities = Linq<CityInfo>()

    val capital: CityInfo
        get() = cities.first { it.cityConstructions.isBuilt("Palace") }

    // negative gold hurts science
    fun getStatsForNextTurn(): Stats {
        val statsForTurn = Stats()
        for (city in cities) statsForTurn.add(city.cityStats.currentCityStats)
        statsForTurn.happiness = getHappinessForNextTurn().toFloat()

        val transportationUpkeep = getTransportationUpkeep()
        statsForTurn.gold -= transportationUpkeep.toFloat()

        if (policies.isAdopted("Mandate Of Heaven"))
            statsForTurn.culture += statsForTurn.happiness / 2

        if (statsForTurn.gold < 0) statsForTurn.science += statsForTurn.gold
        return statsForTurn
    }

    private fun getTransportationUpkeep(): Int {
        var transportationUpkeep = 0
        for (it in gameInfo.tileMap.values.filterNot { it.isCityCenter }) {
            when(it.roadStatus) {
                RoadStatus.Road -> transportationUpkeep += 1
                RoadStatus.Railroad -> transportationUpkeep += 2
            }
        }
        if (policies.isAdopted("Trade Unions")) transportationUpkeep *= (2 / 3f).toInt()
        return transportationUpkeep
    }

    // base happiness
    private fun getHappinessForNextTurn(): Int {
        var happiness = 15
        var happinessPerUniqueLuxury = 5
        if (policies.isAdopted("Protectionism")) happinessPerUniqueLuxury += 1
        happiness += getCivResources().keys
                .count { it.resourceType === ResourceType.Luxury } * happinessPerUniqueLuxury
        happiness += cities.sumBy { it.cityStats.cityHappiness.toInt() }
        if (buildingUniques.contains("HappinessPerSocialPolicy"))
            happiness += policies.getAdoptedPolicies().count { !it.endsWith("Complete") }
        return happiness
    }

    fun getCivResources(): LinqCounter<TileResource> {
        val civResources = LinqCounter<TileResource>()
        for (city in cities) civResources.add(city.getCityResources())
        return civResources
    }

    val buildingUniques: Linq<String>
        get() = cities.selectMany { it.cityConstructions.getBuiltBuildings().select { it.unique }.filterNotNull() }.unique()


    constructor()

    constructor(civName: String, startingLocation: Vector2, gameInfo: GameInfo) {
        this.civName = civName
        this.gameInfo = gameInfo
        this.placeUnitNearTile(startingLocation, "Settler")
        this.placeUnitNearTile(startingLocation, "Scout")
    }

    fun setTransients() {
        goldenAges.civInfo = this
        policies.civInfo = this
        tech.civInfo = this

        for (cityInfo in cities) {
            cityInfo.setTransients()
            cityInfo.civInfo = this
        }
    }

    fun turnsToTech(TechName: String): Int {
        return Math.ceil(((GameBasics.Technologies[TechName]!!.cost - tech.researchOfTech(TechName))
                / getStatsForNextTurn().science).toDouble()).toInt()
    }

    fun addCity(location: Vector2) {
        val newCity = CityInfo(this, location)
        newCity.cityConstructions.chooseNextConstruction()
    }

    fun nextTurn() {
        val nextTurnStats = getStatsForNextTurn()
        happiness = nextTurnStats.happiness.toInt()
        policies.nextTurn(nextTurnStats.culture.toInt())
        gold += nextTurnStats.gold.toInt()

        if (cities.size > 0) tech.nextTurn(nextTurnStats.science.toInt())

        for (city in cities) {
            city.nextTurn()
            greatPeople.addGreatPersonPoints(city.greatPersonPoints)
        }

        val greatPerson = greatPeople.getNewGreatPerson()
        if (greatPerson != null) {
            addGreatPerson(greatPerson)
        }

        goldenAges.nextTurn(happiness)
    }

    fun addGreatPerson(greatPerson: String) {
        val randomCity = cities.random
        placeUnitNearTile(cities.random.cityLocation, greatPerson)
        gameInfo.addNotification("A $greatPerson has been born!", randomCity!!.cityLocation)
    }

    fun placeUnitNearTile(location: Vector2, unitName: String) {
        gameInfo.tileMap.placeUnitNearTile(location, unitName, this)
    }
}

