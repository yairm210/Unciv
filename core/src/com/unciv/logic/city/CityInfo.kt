package com.unciv.logic.city

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.UniqueAbility
import com.unciv.logic.battle.Battle
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeType
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stats
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.ui.utils.withoutItem
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.*

class CityInfo {
    @Transient lateinit var civInfo: CivilizationInfo
    @Transient lateinit private var centerTileInfo:TileInfo  // cached for better performance
    @Transient val range = 2
    @Transient lateinit var tileMap: TileMap
    @Transient lateinit var tilesInRange:HashSet<TileInfo>
    @Transient var hasJustBeenConquered = false  // this is so that military units can enter the city, even before we decide what to do with it

    var location: Vector2 = Vector2.Zero
    var id: String = UUID.randomUUID().toString()
    var name: String = ""
    var foundingCiv = ""
    var turnAcquired = 0
    var health = 200
    var resistanceCounter = 0

    var population = PopulationManager()
    var cityConstructions = CityConstructions()
    var expansion = CityExpansionManager()
    var cityStats = CityStats()

    /** All tiles that this city controls */
    var tiles = HashSet<Vector2>()
    /** Tiles that have population assigned to them */
    var workedTiles = HashSet<Vector2>()
    /** Tiles that the population in them won't be reassigned */
    var lockedTiles = HashSet<Vector2>()
    var isBeingRazed = false
    var attackedThisTurn = false
    var hasSoldBuildingThisTurn = false
    var isPuppet = false
    /** The very first found city is the _original_ capital,
     * while the _current_ capital can be any other city after the original one is captured.
     * It is important to distinct them since the original cannot be razed and defines the Domination Victory. */
    var isOriginalCapital = false

    constructor()   // for json parsing, we need to have a default constructor
    constructor(civInfo: CivilizationInfo, cityLocation: Vector2) {  // new city!
        this.civInfo = civInfo
        foundingCiv = civInfo.civName
        turnAcquired = civInfo.gameInfo.turns
        this.location = cityLocation
        setTransients()

        val nationCities = civInfo.nation.cities
        val cityNameIndex = civInfo.citiesCreated % nationCities.size
        val cityName = nationCities[cityNameIndex]

        val cityNameRounds = civInfo.citiesCreated / nationCities.size
        val cityNamePrefix = if(cityNameRounds==0) ""
        else if(cityNameRounds==1) "New "
        else "Neo "

        name = cityNamePrefix + cityName

        isOriginalCapital = civInfo.citiesCreated == 0
        civInfo.citiesCreated++

        civInfo.cities = civInfo.cities.toMutableList().apply { add(this@CityInfo) }
        civInfo.addNotification("[$name] has been founded!", cityLocation, Color.PURPLE)

        if (civInfo.cities.size == 1) {
            cityConstructions.addBuilding("Palace")
        }

        civInfo.policies.tryAddLegalismBuildings()

        expansion.reset()

        val tile = getCenterTile()

        tryUpdateRoadStatus()

        if (getRuleset().tileImprovements.containsKey("Remove "+tile.terrainFeature))
            tile.terrainFeature = null

        workedTiles = hashSetOf() //reassign 1st working tile
        population.autoAssignPopulation()
        cityStats.update()

        triggerCitiesSettledNearOtherCiv()
    }


    //region pure functions
    fun clone(): CityInfo {
        val toReturn = CityInfo()
        toReturn.location = location
        toReturn.id = id
        toReturn.name = name
        toReturn.health = health
        toReturn.population = population.clone()
        toReturn.cityConstructions = cityConstructions.clone()
        toReturn.expansion = expansion.clone()
        toReturn.tiles = tiles
        toReturn.workedTiles = workedTiles
        toReturn.lockedTiles = lockedTiles
        toReturn.isBeingRazed = isBeingRazed
        toReturn.attackedThisTurn = attackedThisTurn
        toReturn.resistanceCounter = resistanceCounter
        toReturn.foundingCiv = foundingCiv
        toReturn.turnAcquired = turnAcquired
        toReturn.isPuppet = isPuppet
        toReturn.isOriginalCapital = isOriginalCapital
        return toReturn
    }



    fun getCenterTile(): TileInfo = centerTileInfo
    fun getTiles(): Sequence<TileInfo> = tiles.asSequence().map { tileMap[it] }
    fun getWorkableTiles() = tilesInRange.asSequence().filter { it.getOwner() == civInfo }

    fun isCapital() = cityConstructions.isBuilt("Palace")
    fun isConnectedToCapital(connectionTypePredicate: (Set<String>) -> Boolean = {true}): Boolean {
        val mediumTypes = civInfo.citiesConnectedToCapitalToMediums[this] ?: return false
        return connectionTypePredicate(mediumTypes)
    }
    fun isInResistance() = resistanceCounter > 0


    fun getRuleset() = civInfo.gameInfo.ruleSet

    fun getCityResources(): ResourceSupplyList {
        val cityResources = ResourceSupplyList()

        for (tileInfo in getTiles().filter { it.resource != null }) {
            val resource = tileInfo.getTileResource()
            val amount = getTileResourceAmount(tileInfo)
            if (amount > 0) cityResources.add(resource, amount, "Tiles")
        }

        for (building in cityConstructions.getBuiltBuildings().filter { it.requiredResource != null }) {
            val resource = getRuleset().tileResources[building.requiredResource]!!
            cityResources.add(resource, -1, "Buildings")
        }

        return cityResources
    }

    fun getCityResourcesForAlly(): ResourceSupplyList {
        val cityResources = ResourceSupplyList()

        for (tileInfo in getTiles().filter { it.resource != null }) {
            val resource = tileInfo.getTileResource()
            val amount = getTileResourceAmount(tileInfo)
            if (amount > 0) {
                cityResources.add(resource, amount, "City-States")
            }
        }
        return cityResources
    }

    fun getTileResourceAmount(tileInfo: TileInfo): Int {
        if (tileInfo.resource == null) return 0
        val resource = tileInfo.getTileResource()
        if (resource.revealedBy!=null && !civInfo.tech.isResearched(resource.revealedBy!!)) return 0

        // Even if the improvement exists (we conquered an enemy city or somesuch) or we have a city on it, we won't get the resource until the correct tech is researched
        if (resource.improvement!=null) {
            val improvement = getRuleset().tileImprovements[resource.improvement!!]!!
            if (improvement.techRequired != null && !civInfo.tech.isResearched(improvement.techRequired!!)) return 0
        }

        if (resource.improvement == tileInfo.improvement || tileInfo.isCityCenter()
                // Per https://gaming.stackexchange.com/questions/53155/do-manufactories-and-customs-houses-sacrifice-the-strategic-or-luxury-resources
                || (resource.resourceType==ResourceType.Strategic && tileInfo.containsGreatImprovement())){
            var amountToAdd = 1
            if(resource.resourceType == ResourceType.Strategic) {
                amountToAdd = 2
                if (civInfo.policies.hasEffect("Quantity of strategic resources produced by the empire increased by 100%"))
                    amountToAdd *= 2
                if (civInfo.nation.unique == UniqueAbility.SIBERIAN_RICHES && resource.name in listOf("Horses", "Iron", "Uranium"))
                    amountToAdd *= 2
                if (resource.name == "Oil" && civInfo.nation.unique == UniqueAbility.TRADE_CARAVANS)
                    amountToAdd *= 2
            }
            if(resource.resourceType == ResourceType.Luxury
                    && containsBuildingUnique("Provides 1 extra copy of each improved luxury resource near this City"))
                amountToAdd*=2

            return amountToAdd
        }
        return 0
    }

    fun isGrowing(): Boolean {
        return foodForNextTurn() > 0 && cityConstructions.currentConstructionFromQueue != Constants.settler
    }

    fun isStarving(): Boolean = foodForNextTurn() < 0

    private fun foodForNextTurn() = cityStats.currentCityStats.food.roundToInt()

    /** Take null to mean infinity. */
    fun getNumTurnsToNewPopulation(): Int? {
        if (isGrowing()) {
            val roundedFoodPerTurn = foodForNextTurn().toFloat()
            val remainingFood = population.getFoodToNextPopulation() - population.foodStored
            var turnsToGrowth = ceil( remainingFood / roundedFoodPerTurn).toInt()
            if (turnsToGrowth < 1) turnsToGrowth = 1
            return turnsToGrowth
        }

        return null
    }

    /** Take null to mean infinity. */
    fun getNumTurnsToStarvation(): Int? {
        if (isStarving()) {
            return population.foodStored / -foodForNextTurn() + 1
        }

        return null
    }

    fun getBuildingUniques(): Sequence<String> = cityConstructions.getBuiltBuildings().flatMap { it.uniques.asSequence() }
    fun containsBuildingUnique(unique:String) = cityConstructions.getBuiltBuildings().any { it.uniques.contains(unique) }

    fun getGreatPersonMap():HashMap<String,Stats> {
        val stats = HashMap<String, Stats>()
        if (population.specialists.toString() != "")
            stats["Specialists"] = population.specialists.times(3f)

        val buildingStats = Stats()
        for (building in cityConstructions.getBuiltBuildings())
            if (building.greatPersonPoints != null)
                buildingStats.add(building.greatPersonPoints!!)
        if (buildingStats.toString() != "")
            stats["Buildings"] = buildingStats

        for (entry in stats) {
            if (civInfo.nation.unique == UniqueAbility.INGENUITY)
                entry.value.science *= 1.5f
            if (civInfo.hasUnique("Great Merchants are earned 25% faster"))
                entry.value.gold *= 1.25f

            for (unique in civInfo.getMatchingUniques("+[]% great person generation in all cities"))
                stats[entry.key] = stats[entry.key]!!.times(1 + (unique.getPlaceholderParameters()[0].toFloat() / 100))
        }

        return stats
    }

    fun getGreatPersonPoints(): Stats {
        val stats=Stats()
        for(entry in getGreatPersonMap().values)
            stats.add(entry)
        return stats
    }

    internal fun getMaxHealth(): Int {
        return 200 + cityConstructions.getBuiltBuildings().sumBy { it.cityHealth }
    }

    override fun toString(): String {return name} // for debug
    //endregion

    //region state-changing functions
    fun setTransients() {
        tileMap = civInfo.gameInfo.tileMap
        centerTileInfo = tileMap[location]
        tilesInRange = getCenterTile().getTilesInDistance(3).toHashSet()
        population.cityInfo = this
        expansion.cityInfo = this
        expansion.setTransients()
        cityStats.cityInfo = this
        cityConstructions.cityInfo = this
        cityConstructions.setTransients()
    }

    fun startTurn(){
        // Construct units at the beginning of the turn,
        // so they won't be generated out in the open and vulnerable to enemy attacks before you can control them
        cityConstructions.constructIfEnough()
        cityStats.update()
        tryUpdateRoadStatus()
        attackedThisTurn = false
        if (isInResistance()) resistanceCounter--

        if (isPuppet) reassignPopulation()
    }

    fun reassignPopulation() {
        var foodWeight = 1f
        var foodPerTurn = 0f
        while (foodWeight < 3 && foodPerTurn <= 0) {
            workedTiles = hashSetOf()
            population.specialists.clear()
            for (i in 0..population.population)
                population.autoAssignPopulation(foodWeight)
            cityStats.update()

            foodPerTurn = foodForNextTurn().toFloat()
            foodWeight += 0.5f
        }
    }

    fun endTurn() {
        val stats = cityStats.currentCityStats

        cityConstructions.endTurn(stats)
        expansion.nextTurn(stats.culture)
        if (isBeingRazed) {
            population.population--
            if (population.population <= 0) { // there are strange cases where we get to -1
                civInfo.addNotification("[$name] has been razed to the ground!", location, Color.RED)
                destroyCity()
            } else { //if not razed yet:
                if (population.foodStored >= population.getFoodToNextPopulation()) { //if surplus in the granary...
                    population.foodStored = population.getFoodToNextPopulation() - 1 //...reduce below the new growth threshold
                }
            }
        } else population.nextTurn(foodForNextTurn())

        if (this in civInfo.cities) { // city was not destroyed
            health = min(health + 20, getMaxHealth())
            population.unassignExtraPopulation()
        }
    }

    fun destroyCity() {
        for(airUnit in getCenterTile().airUnits.toList()) airUnit.destroy() //Destroy planes stationed in city

        // Edge case! What if a water unit is in a city, and you raze the city?
        // Well, the water unit has to return to the water!
        for(unit in getCenterTile().getUnits()) {
            if (!unit.movement.canPassThrough(getCenterTile()))
                unit.movement.teleportToClosestMoveableTile()
        }

        // The relinquish ownership MUST come before removing the city,
        // because it updates the city stats which assumes there is a capital, so if you remove the capital it crashes
        getTiles().forEach { expansion.relinquishOwnership(it) }
        civInfo.cities = civInfo.cities.toMutableList().apply { remove(this@CityInfo) }
        getCenterTile().improvement="City ruins"

        if (isCapital() && civInfo.cities.isNotEmpty()) // Move the capital if destroyed (by a nuke or by razing)
            civInfo.cities.first().cityConstructions.addBuilding("Palace")
    }

    fun annexCity() {
        isPuppet = false
        cityConstructions.inProgressConstructions.clear() // undo all progress of the previous civ on units etc.
        cityStats.update()
        if (!UncivGame.Current.consoleMode)
            UncivGame.Current.worldScreen.shouldUpdate = true
    }

    /** This happens when we either puppet OR annex, basically whenever we conquer a city and don't liberate it */
    fun puppetCity(conqueringCiv: CivilizationInfo) {

        // Gain gold for plundering city
        val goldPlundered = getGoldForCapturingCity(conqueringCiv)
        conqueringCiv.gold += goldPlundered
        conqueringCiv.addNotification("Received [$goldPlundered] Gold for capturing [$name]", centerTileInfo.position, Color.GOLD)

        val oldCiv = civInfo
        // must be before moving the city to the conquering civ,
        // so the repercussions are properly checked
        diplomaticRepercussionsForConqueringCity(oldCiv, conqueringCiv)

        moveToCiv(conqueringCiv)
        Battle.destroyIfDefeated(oldCiv, conqueringCiv)

        if(population.population>1) population.population -= 1 + population.population/4 // so from 2-4 population, remove 1, from 5-8, remove 2, etc.
        reassignPopulation()

        resistanceCounter = population.population  // I checked, and even if you puppet there's resistance for conquering
        isPuppet = true
        health = getMaxHealth() / 2 // I think that cities recover to half health when conquered?
        cityStats.update()
        // The city could be producing something that puppets shouldn't, like units
        cityConstructions.currentConstructionIsUserSet = false
        cityConstructions.constructionQueue.clear()
        cityConstructions.chooseNextConstruction()
    }

    private fun diplomaticRepercussionsForConqueringCity(oldCiv: CivilizationInfo, conqueringCiv: CivilizationInfo) {
        val currentPopulation = population.population
        val percentageOfCivPopulationInThatCity = currentPopulation * 100f /
                oldCiv.cities.sumBy { it.population.population }
        val aggroGenerated = 10f + percentageOfCivPopulationInThatCity.roundToInt()

        // How can you conquer a city but not know the civ you conquered it from?!
        // I don't know either, but some of our players have managed this, and crashed their game!
        if(!conqueringCiv.knows(oldCiv))
            conqueringCiv.meetCivilization(oldCiv)

        oldCiv.getDiplomacyManager(conqueringCiv)
                .addModifier(DiplomaticModifiers.CapturedOurCities, -aggroGenerated)

        for (thirdPartyCiv in conqueringCiv.getKnownCivs().filter { it.isMajorCiv() }) {
            val aggroGeneratedForOtherCivs = (aggroGenerated / 10).roundToInt().toFloat()
            if (thirdPartyCiv.isAtWarWith(oldCiv)) // You annoyed our enemy?
                thirdPartyCiv.getDiplomacyManager(conqueringCiv)
                        .addModifier(DiplomaticModifiers.SharedEnemy, aggroGeneratedForOtherCivs) // Cool, keep at at! =D
            else thirdPartyCiv.getDiplomacyManager(conqueringCiv)
                    .addModifier(DiplomaticModifiers.WarMongerer, -aggroGeneratedForOtherCivs) // Uncool bro.
        }
    }

    /* Liberating is returning a city to its founder - makes you LOSE warmongering points **/
    fun liberateCity(conqueringCiv: CivilizationInfo) {
        if (foundingCiv == "") { // this should never happen but just in case...
            puppetCity(conqueringCiv)
            annexCity()
            return
        }

        val oldCiv = civInfo

        val foundingCiv = civInfo.gameInfo.civilizations.first { it.civName == foundingCiv }
        if (foundingCiv.isDefeated()) // resurrected civ
            for (diploManager in foundingCiv.diplomacy.values)
                if (diploManager.diplomaticStatus == DiplomaticStatus.War)
                    diploManager.makePeace()

        diplomaticRepercussionsForLiberatingCity(conqueringCiv)
        moveToCiv(foundingCiv)
        Battle.destroyIfDefeated(oldCiv, conqueringCiv)

        health = getMaxHealth() / 2 // I think that cities recover to half health when conquered?
        reassignPopulation()

        if (foundingCiv.cities.size == 1) cityConstructions.addBuilding("Palace") // Resurrection!
        isPuppet = false
        cityStats.update()

        // Move units out of the city when liberated
        for (unit in getTiles().flatMap { it.getUnits() }.toList())
            if (!unit.movement.canPassThrough(unit.currentTile))
                unit.movement.teleportToClosestMoveableTile()

        UncivGame.Current.worldScreen.shouldUpdate = true
    }

    private fun diplomaticRepercussionsForLiberatingCity(conqueringCiv: CivilizationInfo) {
        val oldOwningCiv = civInfo
        val foundingCiv = civInfo.gameInfo.civilizations.first { it.civName == foundingCiv }
        val percentageOfCivPopulationInThatCity = population.population *
                100f / (foundingCiv.cities.sumBy { it.population.population } + population.population)
        val respecForLiberatingOurCity = 10f + percentageOfCivPopulationInThatCity.roundToInt()

        // In order to get "plus points" in Diplomacy, you have to establish diplomatic relations if you haven't yet
        if(!conqueringCiv.knows(foundingCiv))
            conqueringCiv.meetCivilization(foundingCiv)

        if(foundingCiv.isMajorCiv()) {
            foundingCiv.getDiplomacyManager(conqueringCiv)
                    .addModifier(DiplomaticModifiers.CapturedOurCities, respecForLiberatingOurCity)
        } else {
            //Liberating a city state gives a large amount of influence, and peace
            foundingCiv.getDiplomacyManager(conqueringCiv).influence = 90f
            if (foundingCiv.isAtWarWith(conqueringCiv)) {
                val tradeLogic = TradeLogic(foundingCiv, conqueringCiv)
                tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty))
                tradeLogic.currentTrade.theirOffers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty))
                tradeLogic.acceptTrade()
            }
        }

        val otherCivsRespecForLiberating = (respecForLiberatingOurCity / 10).roundToInt().toFloat()
        for (thirdPartyCiv in conqueringCiv.getKnownCivs().filter { it.isMajorCiv() && it != oldOwningCiv }) {
            thirdPartyCiv.getDiplomacyManager(conqueringCiv)
                    .addModifier(DiplomaticModifiers.LiberatedCity, otherCivsRespecForLiberating) // Cool, keep at at! =D
        }
    }

    fun moveToCiv(newCivInfo: CivilizationInfo){
        val oldCiv = civInfo
        civInfo.cities = civInfo.cities.toMutableList().apply { remove(this@CityInfo) }
        newCivInfo.cities = newCivInfo.cities.toMutableList().apply { add(this@CityInfo) }
        civInfo = newCivInfo
        hasJustBeenConquered = false
        turnAcquired = civInfo.gameInfo.turns

        // now that the tiles have changed, we need to reassign population
        workedTiles.filterNot { tiles.contains(it) }
                .forEach { workedTiles = workedTiles.withoutItem(it); population.autoAssignPopulation() }

        // Remove all national wonders
        for(building in cityConstructions.getBuiltBuildings().filter { it.requiredBuildingInAllCities!=null })
            cityConstructions.removeBuilding(building.name)

        // Remove/relocate palace for old Civ
        if(cityConstructions.isBuilt("Palace")){
            cityConstructions.removeBuilding("Palace")
            if(oldCiv.cities.isNotEmpty()){
                oldCiv.cities.first().cityConstructions.addBuilding("Palace") // relocate palace
            }
        }

        // Locate palace for newCiv if this is the only city they have
        if (newCivInfo.cities.count() == 1) {
            cityConstructions.addBuilding("Palace")
        }

        isBeingRazed=false

        // Transfer unique buildings
        for(building in cityConstructions.getBuiltBuildings()) {
            val civEquivalentBuilding = newCivInfo.getEquivalentBuilding(building.name)
            if(building != civEquivalentBuilding) {
                cityConstructions.removeBuilding(building.name)
                cityConstructions.addBuilding(civEquivalentBuilding.name)
            }
        }

        tryUpdateRoadStatus()
    }

    private fun tryUpdateRoadStatus(){
        if(getCenterTile().roadStatus==RoadStatus.None){
            val roadImprovement = getRuleset().tileImprovements["Road"]
            if(roadImprovement!=null && roadImprovement.techRequired in civInfo.tech.techsResearched)
            getCenterTile().roadStatus=RoadStatus.Road
        }

        else if (getCenterTile().roadStatus != RoadStatus.Railroad) {
            val railroadImprovement = getRuleset().tileImprovements["Railroad"]
            if (railroadImprovement != null && railroadImprovement.techRequired in civInfo.tech.techsResearched)
                getCenterTile().roadStatus = RoadStatus.Railroad
        }
    }

    fun getGoldForSellingBuilding(buildingName:String) = getRuleset().buildings[buildingName]!!.cost / 10

    fun sellBuilding(buildingName:String){
        cityConstructions.builtBuildings.remove(buildingName)
        cityConstructions.removeBuilding(buildingName)
        civInfo.gold += getGoldForSellingBuilding(buildingName)
        hasSoldBuildingThisTurn=true

        cityStats.update()
        civInfo.updateDetailedCivResources() // this building could be a resource-requiring one
    }

    fun getGoldForCapturingCity(conqueringCiv: CivilizationInfo): Int {
        val baseGold = 20 + 10 * population.population + Random().nextInt(40)
        val turnModifier = max(0, min(50, civInfo.gameInfo.turns - turnAcquired)) / 50f
        val cityModifier = if (containsBuildingUnique("Doubles Gold given to enemy if city is captured")) 2f else 1f
        val conqueringCivModifier = if (conqueringCiv.nation.unique == UniqueAbility.RIVER_WARLORD) 3f else 1f

        val goldPlundered = baseGold * turnModifier * cityModifier * conqueringCivModifier
        return goldPlundered.toInt()
    }

    /*
     When someone settles a city within 6 tiles of another civ,
        this makes the AI unhappy and it starts a rolling event.
     The SettledCitiesNearUs flag gets added to the AI so it knows this happened,
        and on its turn it asks the player to stop (with a DemandToStopSettlingCitiesNear alert type)
     If the player says "whatever, I'm not promising to stop", they get a -10 modifier which gradually disappears in 40 turns
     If they DO agree, then if they keep their promise for ~100 turns they get a +10 modifier for keeping the promise,
     But if they don't keep their promise they get a -20 that will only fully disappear in 160 turns.
     There's a lot of triggering going on here.
     */
    private fun triggerCitiesSettledNearOtherCiv(){
        val citiesWithin6Tiles = civInfo.gameInfo.civilizations.filter { it.isMajorCiv() && it!=civInfo }
                .flatMap { it.cities }
                .filter { it.getCenterTile().aerialDistanceTo(getCenterTile()) <= 6 }
        val civsWithCloseCities = citiesWithin6Tiles.map { it.civInfo }.distinct()
                .filter { it.knows(civInfo) && it.exploredTiles.contains(location) }
        for(otherCiv in civsWithCloseCities)
            otherCiv.getDiplomacyManager(civInfo).setFlag(DiplomacyFlags.SettledCitiesNearUs,30)
    }

    fun canPurchase(construction : IConstruction) : Boolean {
        if (construction is BaseUnit)
        {
            val tile = getCenterTile()
            if (construction.unitType.isCivilian())
                return tile.civilianUnit == null
            if (construction.unitType.isAirUnit())
                return tile.airUnits.filter { !it.isTransported }.size < 6
            else return tile.militaryUnit == null
        }
        return true
    }
    //endregion
}
