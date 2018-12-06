package com.unciv.logic.civilization

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.unciv.logic.GameInfo
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.BFS
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.Counter
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tech.TechEra
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.models.gamebasics.tile.TileResource
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.getRandom
import com.unciv.ui.utils.tr
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt


class CivilizationInfo {
    @Transient lateinit var gameInfo: GameInfo
    /**
     * never add or remove from here directly, could cause comodification problems.
     * Instead, create a copy list with the change, and replace this list.
     * The other solution, casting toList() every "get", has a performance cost
     */
    @Transient private var units=ArrayList<MapUnit>()
    @Transient var viewableTiles = HashSet<TileInfo>()

    var gold = 0
    var happiness = 15
    var difficulty = "Chieftain"
    var civName = ""
    var tech = TechManager()
    var policies = PolicyManager()
    var goldenAges = GoldenAgeManager()
    var greatPeople = GreatPersonManager()
    var scienceVictory = ScienceVictoryManager()
    var diplomacy = HashMap<String,DiplomacyManager>()

    // if we only use lists, and change the list each time the cities are changed,
    // we won't get concurrent modification exceptions.
    // This is basically a way to ensure our lists are immutable.
    var cities = listOf<CityInfo>()
    var exploredTiles = HashSet<Vector2>()

    constructor()

    constructor(civName: String) {
        this.civName = civName
        tech.techsResearched.add("Agriculture")
    }

    fun clone(): CivilizationInfo {
        val toReturn = CivilizationInfo()
        toReturn.gold = gold
        toReturn.happiness=happiness
        toReturn.difficulty=difficulty
        toReturn.civName=civName
        toReturn.tech = tech.clone()
        toReturn.policies = policies.clone()
        toReturn.goldenAges = goldenAges.clone()
        toReturn.greatPeople=greatPeople.clone()
        toReturn.scienceVictory = scienceVictory.clone()
        toReturn.diplomacy.putAll(diplomacy.values.map { it.clone() }.associateBy { it.otherCivName })
        toReturn.cities = cities.map { it.clone() }
        toReturn.exploredTiles.addAll(exploredTiles)
        return toReturn
    }

    //region pure functions
    fun getDifficulty() =  GameBasics.Difficulties[difficulty]!!
    fun getNation() = GameBasics.Nations[civName]!!
    fun getCapital()=cities.first { it.isCapital() }
    fun isPlayerCivilization() =  gameInfo.getPlayerCivilization()==this
    fun isBarbarianCivilization() =  gameInfo.getBarbarianCivilization()==this

    fun getStatsForNextTurn():Stats{
        return getStatMapForNextTurn().values.toList().reduce{a,b->a+b}
    }

    fun getStatMapForNextTurn(): HashMap<String, Stats> {
        val statMap = HashMap<String,Stats>()
        for (city in cities){
            for(entry in city.cityStats.baseStatList){
                if(statMap.containsKey(entry.key))
                    statMap[entry.key] = statMap[entry.key]!! + entry.value
                else statMap[entry.key] = entry.value
            }
        }

        for (entry in getHappinessForNextTurn()) {
            if (!statMap.containsKey(entry.key))
                statMap[entry.key] = Stats()
            statMap[entry.key]!!.happiness += entry.value
        }

        statMap["Transportation upkeep"] = Stats().apply { gold=- getTransportationUpkeep().toFloat()}
        statMap["Unit upkeep"] = Stats().apply { gold=- getUnitUpkeep().toFloat()}

        if (policies.isAdopted("Mandate Of Heaven")) {
            val happiness = statMap.values.map { it.happiness }.sum()
            if(happiness>0) {
                if (!statMap.containsKey("Policies")) statMap["Policies"] = Stats()
                statMap["Policies"]!!.culture += happiness / 2
            }
        }

        // negative gold hurts science
        // if we have - or 0, then the techs will never be complete and the tech button
        // will show a negative number of turns and int.max, respectively
        if (statMap.values.map { it.gold }.sum() < 0) {
            val scienceDeficit = max(statMap.values.map { it.gold }.sum(),
                    1 - statMap.values.map { it.science }.sum())// Leave at least 1
            statMap["Treasury deficit"] = Stats().apply { science = scienceDeficit }
        }
        val goldDifferenceFromTrade = diplomacy.values.sumBy { it.goldPerTurn() }
        if(goldDifferenceFromTrade!=0)
            statMap["Trade"] = Stats().apply { gold= goldDifferenceFromTrade.toFloat() }

        return statMap
    }

    private fun getUnitUpkeep(): Int {
        val baseUnitCost = 0.5f
        val freeUnits = 3
        var unitsToPayFor = getCivUnits()
        if(policies.isAdopted("Oligarchy")) unitsToPayFor = unitsToPayFor.filterNot { it.getTile().isCityCenter() }
        val totalPaidUnits = max(0,unitsToPayFor.count()-freeUnits)
        val gameProgress = gameInfo.turns/400f // as game progresses Maintenance cost rises
        var cost = baseUnitCost*totalPaidUnits*(1+gameProgress)
        cost = cost.pow(1+gameProgress/3) // Why 3? To spread 1 to 1.33
        if(!isPlayerCivilization())
            cost *= gameInfo.getPlayerCivilization().getDifficulty().aiUnitMaintenanceModifier
        if(policies.isAdopted("Autocracy")) cost *= 0.66f
        return cost.toInt()
    }

    private fun getTransportationUpkeep(): Int {
        var transportationUpkeep = 0
        for (it in gameInfo.tileMap.values.filter { it.getOwner()==this }.filterNot { it.isCityCenter() }) {
            when(it.roadStatus) {
                RoadStatus.Road -> transportationUpkeep += 1
                RoadStatus.Railroad -> transportationUpkeep += 2
            }
        }
        if (policies.isAdopted("Trade Unions")) transportationUpkeep *= (2 / 3f).toInt()
        return transportationUpkeep
    }

    fun getHappinessForNextTurn(): HashMap<String, Float> {
        val statMap = HashMap<String,Float>()
        statMap["Base happiness"] = getDifficulty().baseHappiness.toFloat()

        var happinessPerUniqueLuxury = 5f
        if (policies.isAdopted("Protectionism")) happinessPerUniqueLuxury += 1
        statMap["Luxury resources"]= getCivResources().keys
                .count { it.resourceType === ResourceType.Luxury } * happinessPerUniqueLuxury

        for(city in cities.toList()){
            for(keyvalue in city.cityStats.getCityHappiness()){
                if(statMap.containsKey(keyvalue.key))
                    statMap[keyvalue.key] = statMap[keyvalue.key]!!+keyvalue.value
                else statMap[keyvalue.key] = keyvalue.value
            }
        }

        if (getBuildingUniques().contains("Provides 1 happiness per social policy")) {
            if(!statMap.containsKey("Policies")) statMap["Policies"]=0f
            statMap["Policies"] = statMap["Policies"]!! +
                    policies.getAdoptedPolicies().count { !it.endsWith("Complete") }.toFloat()
        }

        return statMap
    }

    /**
     * Returns a counter of non-zero resources that the civ has
     */
    fun getCivResources(): Counter<TileResource> {
        val civResources = Counter<TileResource>()
        for (city in cities) civResources.add(city.getCityResources())
        for (dip in diplomacy.values) civResources.add(dip.resourcesFromTrade())
        for(resource in getCivUnits().mapNotNull { it.baseUnit.requiredResource }.map { GameBasics.TileResources[it] })
            civResources.add(resource,-1)
        return civResources
    }

    /**
     * Returns a dictionary of ALL resource names, and the amount that the civ has of each
     */
    fun getCivResourcesByName():HashMap<String,Int>{
        val hashMap = HashMap<String,Int>()
        for(resource in GameBasics.TileResources.keys) hashMap[resource]=0
        for(entry in getCivResources()) hashMap[entry.key.name] = entry.value
        return hashMap
    }

    fun hasResource(resourceName:String): Boolean = getCivResourcesByName()[resourceName]!!>0

    fun getBuildingUniques(): List<String> = cities.flatMap { it.getBuildingUniques()}.distinct()

    fun getCivUnits(): List<MapUnit> = units

    fun addUnit(mapUnit: MapUnit){
        val newList = ArrayList(units)
        newList.add(mapUnit)
        units=newList
    }

    fun removeUnit(mapUnit: MapUnit){
        val newList = ArrayList(units)
        newList.remove(mapUnit)
        units=newList
    }


    fun updateViewableTiles() {
        val newViewableTiles = HashSet<TileInfo>()
        newViewableTiles.addAll(cities.flatMap { it.getTiles() }.flatMap { it.neighbors }) // tiles adjacent to city tiles
        newViewableTiles.addAll(getCivUnits().flatMap { it.getViewableTiles()})
        viewableTiles = newViewableTiles // to avoid concurrent modification problems

        // updating the viewable tiles also affects the explored tiles, obvs

        val newExploredTiles = HashSet<Vector2>(exploredTiles)
        newExploredTiles.addAll(newViewableTiles.asSequence().map { it.position }
                .filterNot { exploredTiles.contains(it) })
        exploredTiles = newExploredTiles // ditto


        val viewedCivs = HashSet<CivilizationInfo>()
        for(tile in viewableTiles){
            val tileOwner = tile.getOwner()
            if(tileOwner!=null) viewedCivs+=tileOwner
            for(unit in tile.getUnits()) viewedCivs+=unit.civInfo
        }

        for(otherCiv in viewedCivs.filterNot { it==this || it.isBarbarianCivilization() })
            if(!diplomacy.containsKey(otherCiv.civName)){
                meetCivilization(otherCiv)
                addNotification("We have encountered [${otherCiv.civName}]!".tr(),null, Color.GOLD)
            }
    }

    fun meetCivilization(otherCiv: CivilizationInfo) {
        diplomacy[otherCiv.civName] = DiplomacyManager(this, otherCiv.civName)
                .apply { diplomaticStatus = DiplomaticStatus.Peace }
        otherCiv.diplomacy[civName] = DiplomacyManager(otherCiv, civName)
                .apply { diplomaticStatus = DiplomaticStatus.Peace }
    }

    override fun toString(): String {return civName} // for debug

    fun isDefeated()= cities.isEmpty() && !getCivUnits().any{it.name=="Settler"}
    fun getEra(): TechEra {
        val maxEraOfTech =  tech.researchedTechnologies
                .asSequence()
                .map { it.era() }
                .max()
        if(maxEraOfTech!=null) return maxEraOfTech
        else return TechEra.Ancient
    }

    fun isAtWarWith(otherCiv:CivilizationInfo): Boolean {
        if(otherCiv.isBarbarianCivilization() || isBarbarianCivilization()) return true
        if(!diplomacy.containsKey(otherCiv.civName)) // not encountered yet
            return false
        return diplomacy[otherCiv.civName]!!.diplomaticStatus == DiplomaticStatus.War
    }

    fun isAtWar() = diplomacy.values.any { it.diplomaticStatus==DiplomaticStatus.War && !it.otherCiv().isDefeated() }
    //endregion

    //region state-changing functions
    fun setTransients() {
        goldenAges.civInfo = this
        policies.civInfo = this
        if(policies.adoptedPolicies.size>0 && policies.numberOfAdoptedPolicies == 0)
            policies.numberOfAdoptedPolicies = policies.adoptedPolicies.count { !it.endsWith("Complete") }

        tech.civInfo = this
        tech.setTransients()
        diplomacy.values.forEach { it.civInfo=this}


        for (cityInfo in cities) {
            cityInfo.civInfo = this // must be before the city's setTransients because it depends on the tilemap, that comes from the playerCivInfo
            cityInfo.setTransients()
        }
        setCitiesConnectedToCapitalTransients()
        updateViewableTiles()
    }

    fun endTurn() {
        val nextTurnStats = getStatsForNextTurn()

        policies.endTurn(nextTurnStats.culture.toInt())

        // disband units until there are none left OR the gold values are normal
        if(!isBarbarianCivilization() && gold < -100 && nextTurnStats.gold.toInt() < 0) {
            for (i in 1 until (gold / -100)) {
                var civMilitaryUnits = getCivUnits().filter { !it.type.isCivilian() }
                if (civMilitaryUnits.isNotEmpty()) {
                    val unitToDisband = civMilitaryUnits.first()
                    unitToDisband.destroy()
                    civMilitaryUnits -= unitToDisband
                    addNotification("Cannot provide unit upkeep for " + unitToDisband.name + " - unit has been disbanded!".tr(), null, Color.RED)
                }
            }
        }

        gold += nextTurnStats.gold.toInt()

        if (cities.isNotEmpty()) tech.nextTurn(nextTurnStats.science.toInt())

        greatPeople.addGreatPersonPoints(getGreatPersonPointsForNextTurn())

        for (city in cities.toList()) { // a city can be removed while iterating (if it's being razed) so we need to iterate over a copy
            city.endTurn()
        }

        val greatPerson = greatPeople.getNewGreatPerson()
        if (greatPerson != null) {
            addGreatPerson(greatPerson)
        }

        goldenAges.endTurn(happiness)
        getCivUnits().forEach { it.endTurn() }
        diplomacy.values.forEach{it.nextTurn()}
    }

    fun getGreatPersonPointsForNextTurn(): Stats {
        val stats = Stats()
        for (city in cities) stats.add(city.getGreatPersonPoints())
        return stats
    }

    fun startTurn(){
        updateViewableTiles() // adds explored tiles so that the units will be able to perform automated actions better
        setCitiesConnectedToCapitalTransients()
        for (city in cities)
            city.cityStats.update()
        happiness = getHappinessForNextTurn().values.sum().roundToInt()
        getCivUnits().toList().forEach { it.startTurn() }
    }

    fun canEnterTiles(otherCiv: CivilizationInfo): Boolean {
        if(otherCiv==this) return true
        if(isAtWarWith(otherCiv)) return true
        return false
    }

    fun addNotification(text: String, location: Vector2?,color: Color) {
        if(isPlayerCivilization())
            gameInfo.notifications.add(Notification(text, location,color))
    }

    fun addGreatPerson(greatPerson: String) {
        val randomCity = cities.getRandom()
        placeUnitNearTile(cities.getRandom().location, greatPerson)
        addNotification("A [$greatPerson] has been born!".tr(), randomCity.location, Color.GOLD)
    }

    fun placeUnitNearTile(location: Vector2, unitName: String): MapUnit {
        return gameInfo.tileMap.placeUnitNearTile(location, unitName, this)
    }

    fun addCity(location: Vector2) {
        val newCity = CityInfo(this, location)
        newCity.cityConstructions.chooseNextConstruction()
    }

    fun setCitiesConnectedToCapitalTransients(){
        if(cities.isEmpty()) return // eg barbarians

        // We map which cities we've reached, to the mediums they've been reached by -
        // this is so we know that if we've seen which cities can be connected by port A, and one
        // of those is city B, then we don't need to check the cities that B can connect to by port,
        // since we'll get the same cities we got from A, since they're connected to the same sea.
        val citiesReachedToMediums = HashMap<CityInfo,ArrayList<String>>()
        var citiesToCheck = mutableListOf(getCapital())
        citiesReachedToMediums[getCapital()] = arrayListOf("Start")
        while(citiesToCheck.isNotEmpty() && citiesReachedToMediums.size<cities.size){
            val newCitiesToCheck = mutableListOf<CityInfo>()
            for(cityToConnectFrom in citiesToCheck){
                val reachedMediums = citiesReachedToMediums[cityToConnectFrom]!!

                // This is copypasta and can be cleaned up
                if(!reachedMediums.contains("Road")){
                    val roadBfs = BFS(cityToConnectFrom.getCenterTile()){it.roadStatus!=RoadStatus.None}
                    roadBfs.stepToEnd()
                    val reachedCities = cities.filter { roadBfs.tilesReached.containsKey(it.getCenterTile())}
                    for(reachedCity in reachedCities){
                        if(!citiesReachedToMediums.containsKey(reachedCity)){
                            newCitiesToCheck.add(reachedCity)
                            citiesReachedToMediums[reachedCity] = arrayListOf()
                        }
                        val cityReachedByMediums = citiesReachedToMediums[reachedCity]!!
                        if(!cityReachedByMediums.contains("Road"))
                            cityReachedByMediums.add("Road")
                    }
                    citiesReachedToMediums[cityToConnectFrom]!!.add("Road")
                }

                if(!reachedMediums.contains("Harbor")
                        && cityToConnectFrom.cityConstructions.containsBuildingOrEquivalent("Harbor")){
                    val seaBfs = BFS(cityToConnectFrom.getCenterTile()){it.isWater() || it.isCityCenter()}
                    seaBfs.stepToEnd()
                    val reachedCities = cities.filter { seaBfs.tilesReached.containsKey(it.getCenterTile())}
                    for(reachedCity in reachedCities){
                        if(!citiesReachedToMediums.containsKey(reachedCity)){
                            newCitiesToCheck.add(reachedCity)
                            citiesReachedToMediums[reachedCity] = arrayListOf()
                        }
                        val cityReachedByMediums = citiesReachedToMediums[reachedCity]!!
                        if(!cityReachedByMediums.contains("Harbor"))
                            cityReachedByMediums.add("Harbor")
                    }
                    citiesReachedToMediums[cityToConnectFrom]!!.add("Harbor")
                }
            }
            citiesToCheck = newCitiesToCheck
        }

        for(city in cities){
            city.isConnectedToCapital = citiesReachedToMediums.containsKey(city)
        }
    }

    //endregion
}