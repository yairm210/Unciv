package com.unciv.logic.civilization

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.JsonParser
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.automation.NextTurnAutomation
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.logic.trade.TradeRequest
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Difficulty
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.ruleset.tech.TechEra
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.roundToInt

class CivilizationInfo {

    @Transient private val jsonParser = JsonParser()

    @Transient lateinit var gameInfo: GameInfo
    @Transient lateinit var nation:Nation
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

    var playerType = PlayerType.AI
    /** Used in online multiplayer for human players */ var playerId = ""
    var gold = 0
    @Deprecated("As of 2.11.1") var difficulty = "Chieftain"
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
    var allyCivName = ""
    var naturalWonders = ArrayList<String>()

    //** for trades here, ourOffers is the current civ's offers, and theirOffers is what the requesting civ offers  */
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
        toReturn.playerId = playerId
        toReturn.civName = civName
        toReturn.tech = tech.clone()
        toReturn.policies = policies.clone()
        toReturn.goldenAges = goldenAges.clone()
        toReturn.greatPeople = greatPeople.clone()
        toReturn.victoryManager = victoryManager.clone()
        toReturn.allyCivName = allyCivName
        for (diplomacyManager in diplomacy.values.map { it.clone() })
            toReturn.diplomacy.put(diplomacyManager.otherCivName, diplomacyManager)
        toReturn.cities = cities.map { it.clone() }

        // This is the only thing that is NOT switched out, which makes it a source of ConcurrentModification errors.
        // Cloning it by-pointer is a horrific move, since the serialization would go over it ANYWAY and still led to concurrency prolems.
        // Cloning it  by iiterating on the tilemap values may seem ridiculous, but it's a perfectly thread-safe way to go about it, unlike the other solutions.
        toReturn.exploredTiles.addAll(gameInfo.tileMap.values.asSequence().map { it.position }.filter { it in exploredTiles })
        toReturn.notifications.addAll(notifications)
        toReturn.citiesCreated = citiesCreated
        toReturn.popupAlerts.addAll(popupAlerts)
        toReturn.tradeRequests.addAll(tradeRequests)
        toReturn.naturalWonders.addAll(naturalWonders)
        return toReturn
    }

    //region pure functions
    fun getDifficulty():Difficulty {
        if (isPlayerCivilization()) return gameInfo.getDifficulty()
        return gameInfo.ruleSet.difficulties["Chieftain"]!!
    }

    fun getTranslatedNation(): Nation {
        val language = UncivGame.Current.settings.language.replace(" ","_")
        val filePath = "jsons/Nations/Nations_$language.json"
        if(!Gdx.files.internal(filePath).exists()) return nation
        val translatedNation = jsonParser.getFromJson(Array<Nation>::class.java, filePath)
                .firstOrNull { it.name==civName}
        if(translatedNation==null)  // this language's trnslation doesn't contain this nation yet,
            return nation      // default to english
        return translatedNation
    }

    fun getDiplomacyManager(civInfo: CivilizationInfo) = getDiplomacyManager(civInfo.civName)
    fun getDiplomacyManager(civName: String) = diplomacy[civName]!!
    /** Returns only undefeated civs, aka the ones we care about */
    fun getKnownCivs() = diplomacy.values.map { it.otherCiv() }.filter { !it.isDefeated() }
    fun knows(otherCivName: String) = diplomacy.containsKey(otherCivName)
    fun knows(otherCiv: CivilizationInfo) = knows(otherCiv.civName)

    fun getCapital()=cities.first { it.isCapital() }
    fun isPlayerCivilization() =  playerType==PlayerType.Human
    fun isOneCityChallenger() = (
            playerType==PlayerType.Human &&
            gameInfo.gameParameters.oneCityChallenge)
    fun isCurrentPlayer() =  gameInfo.getCurrentPlayerCivilization()==this
    fun isBarbarian() =  nation.isBarbarian()
    fun isCityState(): Boolean = nation.isCityState()
    fun getCityStateType(): CityStateType = nation.cityStateType!!
    fun isMajorCiv() = nation.isMajorCiv()

    fun victoryType(): VictoryType {
        if(gameInfo.gameParameters.victoryTypes.size==1)
            return gameInfo.gameParameters.victoryTypes.first() // That is the most relevant one
        val victoryType = nation.preferredVictoryType
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
        for(resource in gameInfo.ruleSet.tileResources.keys) hashMap[resource]=0
        for(entry in getCivResources())
            hashMap[entry.resource.name] = entry.amount
        return hashMap
    }

    fun hasResource(resourceName:String): Boolean = getCivResourcesByName()[resourceName]!!>0

    fun containsBuildingUnique(unique:String) = cities.any { it.containsBuildingUnique(unique) }


    //region Units
    fun getCivUnits(): Sequence<MapUnit> = units.asSequence()

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

    fun shouldGoToDueUnit() = UncivGame.Current.settings.checkForDueUnits && getDueUnits().any()

    fun getNextDueUnit(): MapUnit? {
        val dueUnits = getDueUnits()
        if(dueUnits.any()) {
            val unit = dueUnits.first()
            unit.due = false
            return unit
        }
        return null
    }
    //endregion

    fun shouldOpenTechPicker() = tech.freeTechs != 0
            || tech.currentTechnology()==null && cities.isNotEmpty()



    fun getEquivalentBuilding(buildingName:String): Building {
        val baseBuilding = gameInfo.ruleSet.buildings[buildingName]!!.getBaseBuilding(gameInfo.ruleSet)

        for(building in gameInfo.ruleSet.buildings.values)
            if(building.replaces==baseBuilding.name && building.uniqueTo==civName)
                return building
        return baseBuilding
    }

    fun getEquivalentUnit(baseUnitName:String):BaseUnit {
        for (unit in gameInfo.ruleSet.units.values)
            if (unit.replaces == baseUnitName && unit.uniqueTo == civName)
                return unit
        return gameInfo.ruleSet.units[baseUnitName]!!
    }

    fun meetCivilization(otherCiv: CivilizationInfo) {
        diplomacy[otherCiv.civName] = DiplomacyManager(this, otherCiv.civName)
                .apply { diplomaticStatus = DiplomaticStatus.Peace }

        otherCiv.popupAlerts.add(PopupAlert(AlertType.FirstContact,civName))

        otherCiv.diplomacy[civName] = DiplomacyManager(otherCiv, civName)
                .apply { diplomaticStatus = DiplomaticStatus.Peace }

        popupAlerts.add(PopupAlert(AlertType.FirstContact,otherCiv.civName))
        if(isCurrentPlayer() || otherCiv.isCurrentPlayer())
            UncivGame.Current.settings.addCompletedTutorialTask("Meet another civilization")
    }

    fun discoverNaturalWonder(naturalWonderName: String)
    {
        naturalWonders.add(naturalWonderName)
    }

    override fun toString(): String {return civName} // for debug

    /** Returns true if the civ was fully initialized and has no cities or settlers remaining */
    fun isDefeated()= cities.isEmpty()
            && exploredTiles.isNotEmpty()  // Dirty hack: exploredTiles are empty only before starting units are placed
            && (citiesCreated > 0 || !getCivUnits().any { it.name == Constants.settler })

    fun getEra(): TechEra {
        val maxEraOfTech =  tech.researchedTechnologies
                .asSequence()
                .map { it.era() }
                .max()
        if(maxEraOfTech!=null) return maxEraOfTech
        else return TechEra.Ancient
    }

    fun isAtWarWith(otherCiv:CivilizationInfo): Boolean {
        if(otherCiv.isBarbarian() || isBarbarian()) return true
        if(!diplomacy.containsKey(otherCiv.civName)) // not encountered yet
            return false
        return getDiplomacyManager(otherCiv).diplomaticStatus == DiplomaticStatus.War
    }

    fun isAtWar() = diplomacy.values.any { it.diplomaticStatus== DiplomaticStatus.War && !it.otherCiv().isDefeated() }

    fun getLeaderDisplayName(): String {
        var leaderName = getTranslatedNation().getLeaderDisplayName().tr()
        if (playerType == PlayerType.AI)
            leaderName += " (" + "AI".tr() + ")"
        else if (gameInfo.civilizations.count { it.playerType == PlayerType.Human } > 1)
            leaderName += " (" + "Human".tr() + " - " + "Hotseat".tr() + ")"
        else leaderName += " (" + "Human".tr() + " - " + "Multiplayer".tr() + ")"
        return leaderName
    }

    fun canSignResearchAgreement(): Boolean {
        if(!tech.getTechUniques().contains("Enables Research agreements")) return false
        if(gold < getResearchAgreementCost()) return false
        if (gameInfo.ruleSet.technologies.values
                .none { tech.canBeResearched(it.name) && !tech.isResearched(it.name) }) return false
        return true
    }
    //endregion

    //region state-changing functions

    /** This is separate because the REGULAR setTransients updates the viewable ties,
     *  and the updateVisibleTiles tries to meet civs...
     *  And if they civs on't yet know who they are then they don;t know if they're barbarians =\
     *  */
    fun setNationTransient(){
        nation = gameInfo.ruleSet.nations[civName]!!
    }

    fun setTransients() {
        goldenAges.civInfo = this
        policies.civInfo = this
        if(policies.adoptedPolicies.size>0 && policies.numberOfAdoptedPolicies == 0)
            policies.numberOfAdoptedPolicies = policies.adoptedPolicies.count { !it.endsWith("Complete") }

        if(citiesCreated==0 && cities.any())
            citiesCreated = cities.filter { it.name in nation.cities }.count()

        tech.civInfo = this
        tech.setTransients()

        diplomacy.values.forEach {
            it.civInfo=this
            it.updateHasOpenBorders()
        }

        victoryManager.civInfo=this

        // As of 2.11.3 scienceVictory is deprecated
        if(victoryManager.currentsSpaceshipParts.values.sum() == 0
                && scienceVictory.currentParts.values.sum()>0)
            victoryManager.currentsSpaceshipParts = scienceVictory.currentParts

        for (cityInfo in cities) {
            cityInfo.civInfo = this // must be before the city's setTransients because it depends on the tilemap, that comes from the currentPlayerCivInfo
            cityInfo.setTransients()
        }
    }

    fun updateSightAndResources() {
        updateViewableTiles()
        updateHasActiveGreatWall()
        updateDetailedCivResources()
    }

    // implementation in a seperate class, to not clog up CivInfo
    fun initialSetCitiesConnectedToCapitalTransients() = transients().setCitiesConnectedToCapitalTransients(true)
    fun updateHasActiveGreatWall() = transients().updateHasActiveGreatWall()
    fun updateViewableTiles() = transients().updateViewableTiles()
    fun updateDetailedCivResources() = transients().updateDetailedCivResources()

    fun startTurn() {
        policies.startTurn()
        updateStatsForNextTurn() // for things that change when turn passes e.g. golden age, city state influence

        // Generate great people at the start of the turn,
        // so they won't be generated out in the open and vulnerable to enemy attacks before you can control them
        if (cities.isNotEmpty()) { //if no city available, addGreatPerson will throw exception
            val greatPerson = greatPeople.getNewGreatPerson()
            if (greatPerson != null) addGreatPerson(greatPerson)
        }

        updateViewableTiles() // adds explored tiles so that the units will be able to perform automated actions better
        transients().setCitiesConnectedToCapitalTransients()
        for (city in cities) city.startTurn()

        getCivUnits().toList().forEach { it.startTurn() }

        for(tradeRequest in tradeRequests.toList()) { // remove trade requests where one of the sides can no longer supply
            val offeringCiv = gameInfo.getCivilization(tradeRequest.requestingCiv)
            if (offeringCiv.isDefeated() || !TradeEvaluation().isTradeValid(tradeRequest.trade,this, offeringCiv)) {
                tradeRequests.remove(tradeRequest)
                // Yes, this is the right direction. I checked.
                offeringCiv.addNotification("Our proposed trade is no longer relevant!", Color.GOLD)
            }
        }
    }

    fun endTurn() {
        notifications.clear()

        val nextTurnStats = statsForNextTurn

        policies.endTurn(nextTurnStats.culture.toInt())

        // disband units until there are none left OR the gold values are normal
        if (!isBarbarian() && gold < -100 && nextTurnStats.gold.toInt() < 0) {
            for (i in 1 until (gold / -100)) {
                var civMilitaryUnits = getCivUnits().filter { !it.type.isCivilian() }
                if (civMilitaryUnits.any()) {
                    val unitToDisband = civMilitaryUnits.first()
                    unitToDisband.destroy()
                    civMilitaryUnits -= unitToDisband
                    val unitName = unitToDisband.name
                    addNotification("Cannot provide unit upkeep for [$unitName] - unit has been disbanded!", null, Color.RED)
                }
            }
        }

        gold += nextTurnStats.gold.toInt()

        if (cities.isNotEmpty()) tech.nextTurn(nextTurnStats.science.toInt())

        if (isMajorCiv()) greatPeople.addGreatPersonPoints(getGreatPersonPointsForNextTurn()) // City-states don't get great people!

        for (city in cities.toList()) { // a city can be removed while iterating (if it's being razed) so we need to iterate over a copy
            city.endTurn()
        }

        goldenAges.endTurn(getHappiness())
        getCivUnits().forEach { it.endTurn() }
        diplomacy.values.forEach { it.nextTurn() }
        updateAllyCivForCityState()
        updateHasActiveGreatWall()
    }

    fun getGreatPersonPointsForNextTurn(): Stats {
        val stats = Stats()
        for (city in cities) stats.add(city.getGreatPersonPoints())
        return stats
    }

    fun canEnterTiles(otherCiv: CivilizationInfo): Boolean {
        if(otherCiv==this) return true
        if(nation.isBarbarian() && gameInfo.turns >= gameInfo.difficultyObject.turnBarbariansCanEnterPlayerTiles) return true
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

    fun addGreatPerson(greatPerson: String){
        if(cities.isEmpty()) return
        addGreatPerson(greatPerson, cities.random())
    }

    fun addGreatPerson(greatPerson: String, city:CityInfo) {
        val greatPersonName = getEquivalentUnit(greatPerson).name
        placeUnitNearTile(city.location, greatPersonName)
        addNotification("A [$greatPersonName] has been born in [${city.name}]!", city.location, Color.GOLD)
    }

    fun placeUnitNearTile(location: Vector2, unitName: String, removeImprovement: Boolean = false): MapUnit? {
        return gameInfo.tileMap.placeUnitNearTile(location, unitName, this, removeImprovement = removeImprovement)
    }

    fun addCity(location: Vector2) {
        val newCity = CityInfo(this, location)
        newCity.cityConstructions.chooseNextConstruction()
    }


    fun destroy(){
        val destructionText = if(isMajorCiv()) "The civilization of [$civName] has been destroyed!"
        else "The City-State of [$civName] has been destroyed!"
        for(civ in gameInfo.civilizations)
            civ.addNotification(destructionText, null, Color.RED)
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
        otherCiv.updateAllyCivForCityState()
        updateStatsForNextTurn()
    }

    fun getResearchAgreementCost(): Int {
        // https://forums.civfanatics.com/resources/research-agreements-bnw.25568/
        val basicGoldCostOfSignResearchAgreement = when(getEra()){
            TechEra.Medieval, TechEra.Renaissance -> 250
            TechEra.Industrial -> 300
            TechEra.Modern -> 350
            TechEra.Information, TechEra.Future -> 400
            else -> 0
        }
        return (basicGoldCostOfSignResearchAgreement * gameInfo.gameParameters.gameSpeed.modifier).toInt()
    }

    fun giftMilitaryUnitTo(otherCiv: CivilizationInfo) {
        val city = NextTurnAutomation().getClosestCities(this, otherCiv).city1
        val militaryUnit = city.cityConstructions.getConstructableUnits()
                .filter { !it.unitType.isCivilian() && it.unitType.isLandUnit() }
                .toList().random()
        placeUnitNearTile(city.location, militaryUnit.name)
        addNotification("[${otherCiv.civName}] gave us a [${militaryUnit.name}] as gift near [${city.name}]!", null, Color.GREEN)
    }

    fun getAllyCiv(): String {
        return allyCivName
    }

    fun updateAllyCivForCityState() {
        var newAllyName = ""
        if (!isCityState()) return
        val maxInfluence = diplomacy
                .filter{ !it.value.otherCiv().isCityState() && !it.value.otherCiv().isDefeated() }
                .maxBy { it.value.influence }
        if (maxInfluence != null && maxInfluence.value.influence >= 60) {
            newAllyName = maxInfluence.key
        }

        if (allyCivName != newAllyName) {
            val oldAllyName = allyCivName
            allyCivName = newAllyName

            // If the city-state is captured by a civ, it stops being the ally of the civ it was previously an ally of.
            //  This means that it will NOT HAVE a capital at that time, so if we run getCapital we'll get a crash!
            val capitalLocation = if(cities.isNotEmpty()) getCapital().location else null

            if (newAllyName != "") {
                val newAllyCiv = gameInfo.getCivilization(newAllyName)
                newAllyCiv.addNotification("We have allied with [${civName}].", capitalLocation, Color.GREEN)
                newAllyCiv.updateViewableTiles()
                newAllyCiv.updateDetailedCivResources()
            }
            if (oldAllyName != "") {
                val oldAllyCiv = gameInfo.getCivilization(oldAllyName)
                oldAllyCiv.addNotification("We have lost alliance with [${civName}].", capitalLocation, Color.RED)
                oldAllyCiv.updateViewableTiles()
                oldAllyCiv.updateDetailedCivResources()
            }
        }
    }

    //endregion
}
