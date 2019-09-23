package com.unciv.logic.city

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UnCivGame
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.ResourceSupplyList
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.withoutItem
import kotlin.math.min
import kotlin.math.roundToInt

class CityInfo {
    @Transient lateinit var civInfo: CivilizationInfo
    @Transient lateinit var ccenterTile:TileInfo  // cached for better performance
    @Transient val range = 2
    @Transient lateinit var tileMap: TileMap
    @Transient lateinit var tilesInRange:HashSet<TileInfo>

    var location: Vector2 = Vector2.Zero
    var name: String = ""
    var foundingCiv = ""
    var health = 200
    var resistanceCounter = 0

    var population = PopulationManager()
    var cityConstructions = CityConstructions()
    var expansion = CityExpansionManager()
    var cityStats = CityStats()

    var tiles = HashSet<Vector2>()
    var workedTiles = HashSet<Vector2>()
    var isBeingRazed = false
    var attackedThisTurn = false
    var hasSoldBuildingThisTurn = false
    var isPuppet = false

    constructor()   // for json parsing, we need to have a default constructor
    constructor(civInfo: CivilizationInfo, cityLocation: Vector2) {  // new city!
        this.civInfo = civInfo
        foundingCiv = civInfo.civName
        this.location = cityLocation
        setTransients()

        val nationCities = civInfo.getTranslatedNation().cities
        val cityNameIndex = civInfo.citiesCreated % nationCities.size
        val cityName = nationCities[cityNameIndex]

        val cityNameRounds = civInfo.citiesCreated / nationCities.size
        val cityNamePrefix = if(cityNameRounds==0) ""
        else if(cityNameRounds==1) "New "
        else "Neo "

        name = cityNamePrefix + cityName

        civInfo.citiesCreated++

        civInfo.cities = civInfo.cities.toMutableList().apply { add(this@CityInfo) }
        civInfo.addNotification("[$name] has been founded!", cityLocation, Color.PURPLE)

        if (civInfo.policies.isAdopted("Legalism") && civInfo.cities.size <= 4) cityConstructions.addCultureBuilding()
        if (civInfo.cities.size == 1) {
            cityConstructions.addBuilding("Palace")
            cityConstructions.currentConstruction = Constants.worker // Default for first city only!
        }

        expansion.reset()

        val tile = getCenterTile()

        tryUpdateRoadStatus()

        if (listOf(Constants.forest, Constants.jungle, "Marsh").contains(tile.terrainFeature))
            tile.terrainFeature = null

        workedTiles = hashSetOf() //reassign 1st working tile
        population.autoAssignPopulation()
        cityStats.update()

        triggerCitiesSettledNearOtherCiv()
    }


    //region pure functions
    fun clone(): CityInfo {
        val toReturn = CityInfo()
        toReturn.location=location
        toReturn.name=name
        toReturn.health=health
        toReturn.population = population.clone()
        toReturn.cityConstructions=cityConstructions.clone()
        toReturn.expansion = expansion.clone()
        toReturn.tiles = tiles
        toReturn.workedTiles = workedTiles
        toReturn.isBeingRazed=isBeingRazed
        toReturn.attackedThisTurn = attackedThisTurn
        toReturn.resistanceCounter = resistanceCounter
        toReturn.foundingCiv = foundingCiv
        toReturn.isPuppet = isPuppet
        return toReturn
    }



    fun getCenterTile(): TileInfo = ccenterTile
    fun getTiles(): List<TileInfo> = tiles.map { tileMap[it] }
    fun getWorkableTiles() = getTiles().filter { it in tilesInRange }

    fun getCityResources(): ResourceSupplyList {
        val cityResources = ResourceSupplyList()

        for (tileInfo in getTiles().filter { it.resource != null }) {
            val resource = tileInfo.getTileResource()
            if(resource.revealedBy!=null && !civInfo.tech.isResearched(resource.revealedBy!!)) continue
            if (resource.improvement == tileInfo.improvement || tileInfo.isCityCenter()){
                var amountToAdd = 1
                if(resource.resourceType == ResourceType.Strategic){
                    amountToAdd = 2
                    if(civInfo.policies.isAdopted("Facism")) amountToAdd*=2
                    if(civInfo.nation.unique=="Strategic Resources provide +1 Production, and Horses, Iron and Uranium Resources provide double quantity"
                        && resource.name in listOf("Horses","Iron","Uranium"))
                        amountToAdd *= 2
                    if(resource.name=="Oil" && civInfo.nation.unique=="+1 Gold from each Trade Route, Oil resources provide double quantity")
                        amountToAdd *= 2
                }
                if(resource.resourceType == ResourceType.Luxury
                        && containsBuildingUnique("Provides 1 extra copy of each improved luxury resource near this City"))
                    amountToAdd*=2

                cityResources.add(resource, amountToAdd, "Tiles")
            }

        }

        for (building in cityConstructions.getBuiltBuildings().filter { it.requiredResource != null }) {
            val resource = GameBasics.TileResources[building.requiredResource]!!
            cityResources.add(resource, -1, "Buildings")
        }

        return cityResources
    }

    fun getBuildingUniques(): List<String> = cityConstructions.getBuiltBuildings().flatMap { it.uniques }
    fun containsBuildingUnique(unique:String) = cityConstructions.getBuiltBuildings().any { it.uniques.contains(unique) }

    fun getGreatPersonMap():HashMap<String,Stats>{
        val stats = HashMap<String,Stats>()
        if(population.specialists.toString()!="")
            stats["Specialists"] = population.specialists.times(3f)

        val buildingStats = Stats()
        for (building in cityConstructions.getBuiltBuildings())
            if (building.greatPersonPoints != null)
                buildingStats.add(building.greatPersonPoints!!)
        if(buildingStats.toString()!="")
            stats["Buildings"] = buildingStats

        for(entry in stats){
            if(civInfo.nation.unique=="Receive free Great Scientist when you discover Writing, Earn Great Scientists 50% faster")
                entry.value.science *= 1.5f
            if (civInfo.policies.isAdopted("Entrepreneurship"))
                entry.value.gold *= 1.25f

            if (civInfo.containsBuildingUnique("+33% great person generation in all cities"))
                stats[entry.key] = stats[entry.key]!!.times(1.33f)
            if (civInfo.policies.isAdopted("Freedom"))
                stats[entry.key] = stats[entry.key]!!.times(1.25f)
        }

        return stats
    }

    fun getGreatPersonPoints(): Stats {
        val stats=Stats()
        for(entry in getGreatPersonMap().values)
            stats.add(entry)
        return stats
    }

    fun isCapital() = cityConstructions.isBuilt("Palace")
    fun isConnectedToCapital() = civInfo.citiesConnectedToCapital.contains(this)

    internal fun getMaxHealth(): Int {
        return 200 + cityConstructions.getBuiltBuildings().sumBy { it.cityHealth }
    }

    override fun toString(): String {return name} // for debug
    //endregion

    //region state-changing functions
    fun setTransients() {
        tileMap = civInfo.gameInfo.tileMap
        ccenterTile = tileMap[location]
        tilesInRange = getCenterTile().getTilesInDistance( 3).toHashSet()
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
        if (resistanceCounter > 0) resistanceCounter--

        if (isPuppet) reassignWorkers()
    }

    fun reassignWorkers() {
        workedTiles = hashSetOf()
        population.specialists.clear()
        for (i in 0..population.population)
            population.autoAssignPopulation()
        cityStats.update()
    }

    fun endTurn() {
        val stats = cityStats.currentCityStats
        if (cityConstructions.currentConstruction == Constants.settler && stats.food > 0) {
            stats.production += stats.food
            stats.food = 0f
        }

        cityConstructions.endTurn(stats)
        expansion.nextTurn(stats.culture)
        if(isBeingRazed){
            population.population--
            if(population.population<=0){ // there are strange cases where we geet to -1
                civInfo.addNotification("[$name] has been razed to the ground!",location, Color.RED)
                destroyCity()
                if(isCapital() && civInfo.cities.isNotEmpty()) // Yes, we actually razed the capital. Some people do this.
                    civInfo.cities.first().cityConstructions.addBuilding("Palace")
            }else{//if not razed yet:
                if(population.foodStored>=population.getFoodToNextPopulation()) {//if surplus in the granary...
                    population.foodStored=population.getFoodToNextPopulation()-1//...reduce below the new growth treshold
                }
            }
        }
        else population.nextTurn(stats.food)

        if(this in civInfo.cities) { // city was not destroyed
            health = min(health + 20, getMaxHealth())
            population.unassignExtraPopulation()
        }
    }

    fun destroyCity() {
        // Edge case! What if a water unit is in a city, and you raze the city?
        // Well, the water unit has to return to the water!
        for(unit in getCenterTile().getUnits())
            if(!unit.movement.canPassThrough(getCenterTile()))
                unit.movement.teleportToClosestMoveableTile()

        civInfo.cities = civInfo.cities.toMutableList().apply { remove(this@CityInfo) }
        getTiles().forEach { expansion.relinquishOwnership(it) }
        getCenterTile().improvement="City ruins"
    }

    fun annexCity(conqueringCiv: CivilizationInfo) {
        puppetCity(conqueringCiv)

        if(!conqueringCiv.policies.isAdopted("Police State")) {
            expansion.cultureStored = 0
            expansion.reset()
        }

        isPuppet=false

        UnCivGame.Current.worldScreen.shouldUpdate=true
    }

    /** This happens when we either puppet OR annex, basically whenever we conquer a city and don't liberate it */
    fun puppetCity(conqueringCiv: CivilizationInfo) {
        if (!conqueringCiv.isMajorCiv()){
            destroyCity()
        }

        val oldCiv = civInfo
        moveToCiv(conqueringCiv)
        if(oldCiv.isDefeated()) {
            oldCiv.destroy()
            conqueringCiv.popupAlerts.add(PopupAlert(AlertType.Defeated,oldCiv.civName))
        }

        health = getMaxHealth() / 2 // I think that cities recover to half health when conquered?

        diplomaticReprecussionsForConqueringCity(oldCiv, conqueringCiv)

        if(population.population>1) population.population -= 1 + population.population/4 // so from 2-4 population, remove 1, from 5-8, remove 2, etc.

        resistanceCounter = population.population  // I checked, and even if you puppet there's resistance for conquering
        reassignWorkers()

        isPuppet = true
    }

    private fun diplomaticReprecussionsForConqueringCity(oldCiv: CivilizationInfo, conqueringCiv: CivilizationInfo) {
        val currentPopulation = population.population
        val percentageOfCivPopulationInThatCity = currentPopulation * 100f / civInfo.cities.sumBy { it.population.population }
        val aggroGenerated = 10f + percentageOfCivPopulationInThatCity.roundToInt()
        oldCiv.getDiplomacyManager(conqueringCiv)
                .addModifier(DiplomaticModifiers.CapturedOurCities, -aggroGenerated)

        for (thirdPartyCiv in conqueringCiv.getKnownCivs().filter { it.isMajorCiv() }) {
            val aggroGeneratedForOtherCivs = (aggroGenerated / 10).roundToInt().toFloat()
            if (thirdPartyCiv.isAtWarWith(civInfo)) // You annoyed our enemy?
                thirdPartyCiv.getDiplomacyManager(conqueringCiv)
                        .addModifier(DiplomaticModifiers.SharedEnemy, aggroGeneratedForOtherCivs) // Cool, keep at at! =D
            else thirdPartyCiv.getDiplomacyManager(conqueringCiv)
                    .addModifier(DiplomaticModifiers.WarMongerer, -aggroGeneratedForOtherCivs) // Uncool bro.
        }
    }

    /* Liberating is returning a city to its founder - makes you LOSE warmongering points **/
    fun liberateCity(conqueringCiv: CivilizationInfo) {
        if (foundingCiv == "") { // this should never happen but just in case...
            annexCity(conqueringCiv)
            return
        }

        val oldOwningCiv = civInfo
        val foundingCiv = civInfo.gameInfo.civilizations.first { it.civName == foundingCiv }

        val currentPopulation = population.population

        val percentageOfCivPopulationInThatCity = currentPopulation*100f / (foundingCiv.cities.sumBy { it.population.population } + currentPopulation)
        val respecForLiberatingOurCity = 10f+percentageOfCivPopulationInThatCity.roundToInt()
        foundingCiv.getDiplomacyManager(conqueringCiv)
                .addModifier(DiplomaticModifiers.CapturedOurCities, respecForLiberatingOurCity)

        val otherCivsRespecForLiberating = (respecForLiberatingOurCity/10).roundToInt().toFloat()
        for(thirdPartyCiv in conqueringCiv.getKnownCivs().filter { it.isMajorCiv() && it != oldOwningCiv }){
            thirdPartyCiv.getDiplomacyManager(conqueringCiv)
                        .addModifier(DiplomaticModifiers.WarMongerer, otherCivsRespecForLiberating) // Cool, keep at at! =D
        }

        moveToCiv(foundingCiv)
        health = getMaxHealth() / 2 // I think that cities recover to half health when conquered?

        reassignWorkers()

        if(foundingCiv.cities.size == 1) { // Resurrection!
            cityConstructions.addBuilding("Palace")
        }

        UnCivGame.Current.worldScreen.shouldUpdate=true
    }

    // todo should be in cityStats
    fun hasExtraAnnexUnhappiness() : Boolean {
        if (civInfo.civName == foundingCiv || foundingCiv == "" || isPuppet) return false
        return !containsBuildingUnique("Remove extra unhappiness from annexed cities")
    }

    fun moveToCiv(newCivInfo: CivilizationInfo){
        val oldCiv = civInfo
        civInfo.cities = civInfo.cities.toMutableList().apply { remove(this@CityInfo) }
        newCivInfo.cities = newCivInfo.cities.toMutableList().apply { add(this@CityInfo) }
        civInfo = newCivInfo

        // now that the tiles have changed, we need to reassign population
        workedTiles.filterNot { tiles.contains(it) }
                .forEach { workedTiles = workedTiles.withoutItem(it); population.autoAssignPopulation() }

        // Remove all national wonders
        for(building in cityConstructions.getBuiltBuildings().filter { it.requiredBuildingInAllCities!=null })
            cityConstructions.removeBuilding(building.name)

        // Remove/relocate palace
        if(cityConstructions.isBuilt("Palace")){
            cityConstructions.removeBuilding("Palace")
            if(oldCiv.cities.isNotEmpty()){
                oldCiv.cities.first().cityConstructions.addBuilding("Palace") // relocate palace
            }
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

    fun canAcquireTile(newTileInfo: TileInfo): Boolean {
        val owningCity = newTileInfo.getCity()
        if (owningCity!=null && owningCity!=this
                && newTileInfo.getOwner()!!.isPlayerCivilization()
                && newTileInfo.arialDistanceTo(getCenterTile()) <= 3
                && newTileInfo.neighbors.any{it.getCity()==this}) {
            return true
        }
        return false
    }

    private fun tryUpdateRoadStatus(){
        if(getCenterTile().roadStatus==RoadStatus.None
                && GameBasics.TileImprovements["Road"]!!.techRequired in civInfo.tech.techsResearched)
            getCenterTile().roadStatus=RoadStatus.Road

        else if(getCenterTile().roadStatus!=RoadStatus.Railroad
                && GameBasics.TileImprovements["Railroad"]!!.techRequired in civInfo.tech.techsResearched)
            getCenterTile().roadStatus=RoadStatus.Railroad
    }

    fun getGoldForSellingBuilding(buildingName:String) = GameBasics.Buildings[buildingName]!!.cost / 10

    fun sellBuilding(buildingName:String){
        cityConstructions.builtBuildings.remove(buildingName)
        cityConstructions.removeBuilding(buildingName)
        civInfo.gold += getGoldForSellingBuilding(buildingName)
        hasSoldBuildingThisTurn=true

        cityStats.update()
        civInfo.updateDetailedCivResources() // this building could be a resource-requiring one
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
    fun triggerCitiesSettledNearOtherCiv(){
        val citiesWithin6Tiles = civInfo.gameInfo.civilizations.filter { it.isMajorCiv() && it!=civInfo }
                .flatMap { it.cities }
                .filter { it.getCenterTile().arialDistanceTo(getCenterTile()) <= 6 }
        val civsWithCloseCities = citiesWithin6Tiles.map { it.civInfo }.distinct()
                .filter { it.knows(civInfo) && it.exploredTiles.contains(location) }
        for(otherCiv in civsWithCloseCities)
            otherCiv.getDiplomacyManager(civInfo).setFlag(DiplomacyFlags.SettledCitiesNearUs,30)
    }
    //endregion
}
