package com.unciv.logic.civilization

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UnCivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.automation.NextTurnAutomation
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.trade.TradeRequest
import com.unciv.models.gamebasics.*
import com.unciv.models.gamebasics.tech.TechEra
import com.unciv.models.gamebasics.tile.ResourceSupplyList
import com.unciv.models.stats.Stats
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.roundToInt

class CivilizationInfo {
    @Transient lateinit var gameInfo: GameInfo
    /**
     * We never add or remove from here directly, could cause comodification problems.
     * Instead, we create a copy list with the change, and replace this list.
     * The other solution, casting toList() every "get", has a performance cost
     */
    @Transient private var units=listOf<MapUnit>()
    @Transient var viewableTiles = setOf<TileInfo>()
    @Transient var viewableInvisibleUnitsTiles = setOf<TileInfo>()

    /** Contains cities from ALL civilizations connected by trade routes to the capital */
    @Transient var citiesConnectedToCapital = listOf<CityInfo>()

    /** This is for performance since every movement calculation depends on this, see MapUnit comment */
    @Transient var hasActiveGreatWall = false
    @Transient var statsForNextTurn = Stats()
    @Transient var detailedCivResources = ResourceSupplyList()

    var gold = 0
    @Deprecated("As of 2.11.1") var difficulty = "Chieftain"
    var playerType = PlayerType.AI
    var civName = ""
    var tech = TechManager()
    var policies = PolicyManager()
    var goldenAges = GoldenAgeManager()
    var greatPeople = GreatPersonManager()
    @Deprecated("As of 2.11.3") var scienceVictory = ScienceVictoryManager()
    var victoryManager=VictoryManager()
    var diplomacy = HashMap<String, DiplomacyManager>()
    var notifications = ArrayList<Notification>()
    val popupAlerts = ArrayList<PopupAlert>()
    val tradeRequests = ArrayList<TradeRequest>()

    // if we only use lists, and change the list each time the cities are changed,
    // we won't get concurrent modification exceptions.
    // This is basically a way to ensure our lists are immutable.
    var cities = listOf<CityInfo>()
    var citiesCreated = 0
    var exploredTiles = HashSet<Vector2>()

    constructor()

    constructor(civName: String) {
        this.civName = civName
        tech.techsResearched.add("Agriculture") // can't be .addTechnology because the civInfo isn't assigned yet
    }

    fun clone(): CivilizationInfo {
        val toReturn = CivilizationInfo()
        toReturn.gold = gold
        toReturn.playerType = playerType
        toReturn.civName = civName
        toReturn.tech = tech.clone()
        toReturn.policies = policies.clone()
        toReturn.goldenAges = goldenAges.clone()
        toReturn.greatPeople = greatPeople.clone()
        toReturn.victoryManager = victoryManager.clone()
        for(diplomacyManager in diplomacy.values.map { it.clone() })
            toReturn.diplomacy.put(diplomacyManager.otherCivName, diplomacyManager)
        toReturn.cities = cities.map { it.clone() }
        toReturn.exploredTiles.addAll(exploredTiles)
        toReturn.notifications.addAll(notifications)
        toReturn.citiesCreated = citiesCreated
        return toReturn
    }

    //region pure functions
    fun getDifficulty():Difficulty {
        if (isPlayerCivilization()) return gameInfo.getDifficulty()
        return GameBasics.Difficulties["Chieftain"]!!
    }

    fun getNation() = GameBasics.Nations[civName]!!
    fun getTranslatedNation(): Nation {
        val language = UnCivGame.Current.settings.language.replace(" ","_")
        if(!Gdx.files.internal("jsons/Nations_$language.json").exists()) return getNation()
        val translatedNation = GameBasics.getFromJson(Array<Nation>::class.java, "Nations_$language")
                .firstOrNull { it.name==civName}
        if(translatedNation==null)  // this language's trnslation doesn't contain this nation yet,
            return getNation()      // default to english
        return translatedNation
    }

    fun getDiplomacyManager(civInfo: CivilizationInfo) = getDiplomacyManager(civInfo.civName)
    fun getDiplomacyManager(civName: String) = diplomacy[civName]!!
    fun getKnownCivs() = diplomacy.values.map { it.otherCiv() }
    fun knows(otherCivName: String) = diplomacy.containsKey(otherCivName)
    fun knows(otherCiv: CivilizationInfo) = knows(otherCiv.civName)

    fun getCapital()=cities.first { it.isCapital() }
    fun isPlayerCivilization() =  playerType==PlayerType.Human
    fun isCurrentPlayer() =  gameInfo.getCurrentPlayerCivilization()==this
    fun isBarbarianCivilization() =  civName=="Barbarians"
    fun isCityState(): Boolean = getNation().isCityState()
    fun getCityStateType(): CityStateType = getNation().cityStateType!!
    fun isMajorCiv() = !isBarbarianCivilization() && !isCityState()

    fun victoryType(): VictoryType {
        if(gameInfo.gameParameters.victoryTypes.size==1)
            return gameInfo.gameParameters.victoryTypes.first() // That is the most relevant one
        val victoryType = getNation().preferredVictoryType
        if(gameInfo.gameParameters.victoryTypes.contains(victoryType)) return victoryType
        else return VictoryType.Neutral
    }

    fun stats() = CivInfoStats(this)
    private fun transients() = CivInfoTransientUpdater(this)

    fun updateStatsForNextTurn(){
        statsForNextTurn = stats().getStatMapForNextTurn().values.toList().reduce{a,b->a+b}
    }



    fun getHappiness() = stats().getHappinessBreakdown().values.sum().roundToInt()


    fun getCivResources(): ResourceSupplyList {
        val newResourceSupplyList=ResourceSupplyList()
        for(resourceSupply in detailedCivResources)
            newResourceSupplyList.add(resourceSupply.resource,resourceSupply.amount,"All")
        return newResourceSupplyList
    }


    /**
     * Returns a dictionary of ALL resource names, and the amount that the civ has of each
     */
    fun getCivResourcesByName():HashMap<String,Int>{
        val hashMap = HashMap<String,Int>()
        for(resource in GameBasics.TileResources.keys) hashMap[resource]=0
        for(entry in getCivResources())
            hashMap[entry.resource.name] = entry.amount
        return hashMap
    }

    fun hasResource(resourceName:String): Boolean = getCivResourcesByName()[resourceName]!!>0

    fun containsBuildingUnique(unique:String) = cities.any { it.containsBuildingUnique(unique) }

    fun getCivUnits(): List<MapUnit> = units

    fun addUnit(mapUnit: MapUnit, updateCivInfo:Boolean=true){
        val newList = ArrayList(units)
        newList.add(mapUnit)
        units=newList

        if(updateCivInfo) {
            // Not relevant when updating tileinfo transients, since some info of the civ itself isn't yet available,
            // and in any case it'll be updated once civ info transients are
            updateStatsForNextTurn() // unit upkeep
            updateDetailedCivResources()
        }
    }

    fun removeUnit(mapUnit: MapUnit){
        val newList = ArrayList(units)
        newList.remove(mapUnit)
        units=newList
        updateStatsForNextTurn() // unit upkeep
        updateDetailedCivResources()
    }

    fun getIdleUnits() = getCivUnits().filter { it.isIdle() }

    fun getDueUnits() = getCivUnits().filter { it.due && it.isIdle() }

    fun shouldOpenTechPicker() = tech.freeTechs != 0
            || tech.currentTechnology()==null && cities.isNotEmpty()

    fun shouldGoToDueUnit() = UnCivGame.Current.settings.checkForDueUnits && getDueUnits().isNotEmpty()

    fun getNextDueUnit(): MapUnit? {
        val dueUnits = getDueUnits()
        if(dueUnits.isNotEmpty()) {
            val unit = dueUnits[0]
            unit.due = false
            return unit
        }
        return null
    }

    fun getEquivalentBuilding(buildingName:String): Building {
        val baseBuilding = GameBasics.Buildings[buildingName]!!.getBaseBuilding()

        for(building in GameBasics.Buildings.values)
            if(building.replaces==baseBuilding.name && building.uniqueTo==civName)
                return building
        return baseBuilding
    }

    fun meetCivilization(otherCiv: CivilizationInfo) {
        diplomacy[otherCiv.civName] = DiplomacyManager(this, otherCiv.civName)
                .apply { diplomaticStatus = DiplomaticStatus.Peace }

        otherCiv.popupAlerts.add(PopupAlert(AlertType.FirstContact,civName))

        otherCiv.diplomacy[civName] = DiplomacyManager(otherCiv, civName)
                .apply { diplomaticStatus = DiplomaticStatus.Peace }

        popupAlerts.add(PopupAlert(AlertType.FirstContact,otherCiv.civName))
    }

    override fun toString(): String {return civName} // for debug

    fun isDefeated()= cities.isEmpty() && (citiesCreated > 0 || !getCivUnits().any{it.name== Constants.settler})

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
        return getDiplomacyManager(otherCiv).diplomaticStatus == DiplomaticStatus.War
    }

    fun isAtWar() = diplomacy.values.any { it.diplomaticStatus== DiplomaticStatus.War && !it.otherCiv().isDefeated() }
    //endregion

    //region state-changing functions
    fun setTransients() {
        goldenAges.civInfo = this
        policies.civInfo = this
        if(policies.adoptedPolicies.size>0 && policies.numberOfAdoptedPolicies == 0)
            policies.numberOfAdoptedPolicies = policies.adoptedPolicies.count { !it.endsWith("Complete") }

        if(citiesCreated==0 && cities.any())
            citiesCreated = cities.filter { it.name in getNation().cities }.count()

        tech.civInfo = this
        tech.setTransients()
        diplomacy.values.forEach { it.civInfo=this}

        victoryManager.civInfo=this

        // As of 2.11.3 scienceVictory is deprecated
        if(victoryManager.currentsSpaceshipParts.values.sum() == 0
                && scienceVictory.currentParts.values.sum()>0)
            victoryManager.currentsSpaceshipParts = scienceVictory.currentParts

        for (cityInfo in cities) {
            cityInfo.civInfo = this // must be before the city's setTransients because it depends on the tilemap, that comes from the currentPlayerCivInfo
            cityInfo.setTransients()
        }
        updateViewableTiles()
        updateHasActiveGreatWall()
        updateDetailedCivResources()
    }

    // implementation in a seperate class, to not clog up CivInfo
    fun setCitiesConnectedToCapitalTransients() = transients().setCitiesConnectedToCapitalTransients()
    fun updateHasActiveGreatWall() = transients().updateHasActiveGreatWall()
    fun updateViewableTiles() = transients().updateViewableTiles()
    fun updateDetailedCivResources() = transients().updateDetailedCivResources()

    fun startTurn(){
        updateStatsForNextTurn() // for things that change when turn passes e.g. golden age, city state influence

        // Generate great people at the start of the turn,
        // so they won't be generated out in the open and vulnerable to enemy attacks before you can control them
        if (cities.isNotEmpty()) { //if no city available, addGreatPerson will throw exception
            val greatPerson = greatPeople.getNewGreatPerson()
            if (greatPerson != null) addGreatPerson(greatPerson)
        }

        updateViewableTiles() // adds explored tiles so that the units will be able to perform automated actions better
        setCitiesConnectedToCapitalTransients()
        for (city in cities) city.startTurn()

        getCivUnits().toList().forEach { it.startTurn() }
    }

    fun endTurn() {
        notifications.clear()

        val nextTurnStats = statsForNextTurn

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

        goldenAges.endTurn(getHappiness())
        getCivUnits().forEach { it.endTurn() }
        diplomacy.values.forEach{it.nextTurn()}
        updateHasActiveGreatWall()
    }

    fun getGreatPersonPointsForNextTurn(): Stats {
        val stats = Stats()
        for (city in cities) stats.add(city.getGreatPersonPoints())
        return stats
    }

    fun canEnterTiles(otherCiv: CivilizationInfo): Boolean {
        if(otherCiv==this) return true
        if(!diplomacy.containsKey(otherCiv.civName)) // not encountered yet
            return false
        if(isAtWarWith(otherCiv)) return true
        if(getDiplomacyManager(otherCiv).hasOpenBorders) return true
        return false
    }

    fun addNotification(text: String, location: Vector2?, color: Color) {
        val locations = if (location != null) listOf(location) else emptyList()
        addNotification(text, color, LocationAction(locations))
    }

    fun addNotification(text: String, color: Color, action: NotificationAction?=null) {
        if (playerType == PlayerType.AI) return // no point in lengthening the saved game info if no one will read it
        notifications.add(Notification(text, color, action))
    }

    fun addGreatPerson(greatPerson: String, city:CityInfo = cities.random()) {
        placeUnitNearTile(city.location, greatPerson)
        addNotification("A [$greatPerson] has been born!".tr(), city.location, Color.GOLD)
    }

    fun placeUnitNearTile(location: Vector2, unitName: String): MapUnit? {
        return gameInfo.tileMap.placeUnitNearTile(location, unitName, this)
    }

    fun addCity(location: Vector2) {
        val newCity = CityInfo(this, location)
        newCity.cityConstructions.chooseNextConstruction()
    }


    fun destroy(){
        for(civ in gameInfo.civilizations)
            civ.addNotification("The civilization of [$civName] has been destroyed!", null, Color.RED)
        getCivUnits().forEach { it.destroy() }
        tradeRequests.clear() // if we don't do this then there could be resources taken by "pending" trades forever
        for(diplomacyManager in diplomacy.values){
            diplomacyManager.trades.clear()
            diplomacyManager.otherCiv().getDiplomacyManager(this).trades.clear()
            for(tradeRequest in diplomacyManager.otherCiv().tradeRequests.filter { it.requestingCiv==civName })
                diplomacyManager.otherCiv().tradeRequests.remove(tradeRequest) // it  would be really weird to get a trade request from a dead civ
        }
    }

    fun giveGoldGift(otherCiv: CivilizationInfo, giftAmount: Int) {
        if(!otherCiv.isCityState()) throw Exception("You can only gain influence with city states!")
        gold -= giftAmount
        otherCiv.getDiplomacyManager(this).influence += giftAmount/10
        updateStatsForNextTurn()
    }

    fun giftMilitaryUnitBy(otherCiv: CivilizationInfo) {
        val city = NextTurnAutomation().getClosestCities(this, otherCiv).city1
        if (city == null) {
            addNotification("Exception! Failed to find a suitable city to place gift unit.".tr(), null, Color.RED)
        } else {
            var militaryUnit = city.cityConstructions
                    .getConstructableUnits().filter { !it.unitType.isCivilian() && it.unitType.isLandUnit()}
                    .random()
            placeUnitNearTile(city.location, militaryUnit.name)
            addNotification("[${otherCiv.civName}] gave us a [${militaryUnit.name}] as gift near [${city.name}]!".tr(), null, Color.GREEN)
        }
    }

    //endregion
}
