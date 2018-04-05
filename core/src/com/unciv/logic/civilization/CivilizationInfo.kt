package com.unciv.logic.civilization

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.GameInfo
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.Civilization
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.ResourceType
import com.unciv.models.gamebasics.TileResource
import com.unciv.models.linq.Counter
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.getRandom
import kotlin.math.max
import kotlin.math.pow


class CivilizationInfo {

    @Transient
    lateinit var gameInfo: GameInfo

    var gold = 0
    var happiness = 15
    var civName = "Babylon"

    var tech = TechManager()
    var policies = PolicyManager()
    var goldenAges = GoldenAgeManager()
    private var greatPeople = GreatPersonManager()
    var scienceVictory = ScienceVictoryManager()

    var cities = ArrayList<CityInfo>()

    fun getCivilization(): Civilization {return GameBasics.Civilizations[civName]!!}

    val capital: CityInfo
        get() = cities.first { it.cityConstructions.isBuilt("Palace") }

    fun isPlayerCivilization() =  gameInfo.getPlayerCivilization()==this


    // negative gold hurts science
    fun getStatsForNextTurn(): Stats {
        val statsForTurn = Stats()
        for (city in cities) statsForTurn.add(city.cityStats.currentCityStats)
        statsForTurn.happiness = getHappinessForNextTurn().toFloat()

        val transportationUpkeep = getTransportationUpkeep()
        statsForTurn.gold -= transportationUpkeep

        val unitUpkeep = getUnitUpkeep()
        statsForTurn.gold -= unitUpkeep

        if (policies.isAdopted("Mandate Of Heaven"))
            statsForTurn.culture += statsForTurn.happiness / 2

        if (statsForTurn.gold < 0) statsForTurn.science += statsForTurn.gold
        return statsForTurn
    }

    private fun getUnitUpkeep(): Int {
        val baseUnitCost = 0.5f
        val freeUnits = 3
        val totalPaidUnits = max(0,getCivUnits().count()-freeUnits)
        val gameProgress = gameInfo.turns/400f // as game progresses maintainance cost rises
        val cost = baseUnitCost*totalPaidUnits*(1+gameProgress)
        val finalCost = cost.pow(1+gameProgress/3) // Why 3? No reason.
        return finalCost.toInt()
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
    fun getHappinessForNextTurn(): Int {
        var happiness = 15
        var happinessPerUniqueLuxury = 5
        if (policies.isAdopted("Protectionism")) happinessPerUniqueLuxury += 1
        happiness += getCivResources().keys
                .count { it.resourceType === ResourceType.Luxury } * happinessPerUniqueLuxury
        happiness += cities.sumBy { it.cityStats.getCityHappiness().toInt() }
        if (buildingUniques.contains("HappinessPerSocialPolicy"))
            happiness += policies.getAdoptedPolicies().count { !it.endsWith("Complete") }
        return happiness
    }

    fun getCivResources(): Counter<TileResource> {
        val civResources = Counter<TileResource>()
        for (city in cities) civResources.add(city.getCityResources())
        return civResources
    }

    val buildingUniques: List<String>
        get() = cities.flatMap{ it.cityConstructions.getBuiltBuildings().map { it.unique }.filterNotNull() }.distinct()


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


    fun addCity(location: Vector2) {
        val newCity = CityInfo(this, location)
        newCity.cityConstructions.chooseNextConstruction()
    }

    fun nextTurn() {
        val nextTurnStats = getStatsForNextTurn()
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
        val randomCity = cities.getRandom()
        placeUnitNearTile(cities.getRandom().cityLocation, greatPerson)
        gameInfo.addNotification("A $greatPerson has been born!", randomCity.cityLocation)
    }

    fun placeUnitNearTile(location: Vector2, unitName: String) {
        gameInfo.tileMap.placeUnitNearTile(location, unitName, this)
    }

    fun getCivUnits(): List<MapUnit> {
        return gameInfo.tileMap.values.filter { it.unit!=null && it.unit!!.owner==civName }.map { it.unit!! }
    }

    fun getViewableTiles(): List<TileInfo> {
        var viewablePositions = emptyList<TileInfo>()
        viewablePositions += gameInfo.tileMap.values.filter { it.owner == civName }
                .flatMap { it.neighbors } // tiles adjacent to city tiles
        viewablePositions += gameInfo.tileMap.values.filter { it.unit != null && it.unit!!.owner == civName }
                .flatMap { it.getViewableTiles(2)} // Tiles within 2 tiles of units
        return viewablePositions
    }

}