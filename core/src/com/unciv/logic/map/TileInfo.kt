package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.UniqueAbility
import com.unciv.logic.HexMath
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.*
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import kotlin.math.abs

open class TileInfo {
    @Transient lateinit var tileMap: TileMap
    @Transient lateinit var ruleset: Ruleset  // a tile can be a tile with a ruleset, even without a map.
    @Transient var owningCity:CityInfo?=null
    @Transient private lateinit var baseTerrainObject:Terrain

    // These are for performance - checked with every tile movement and "canEnter" check, which makes them performance-critical
    @Transient var isLand = false
    @Transient var isWater = false
    @Transient var isOcean = false

    var militaryUnit:MapUnit?=null
    var civilianUnit:MapUnit?=null
    var airUnits=ArrayList<MapUnit>()

    var position: Vector2 = Vector2.Zero
    lateinit var baseTerrain: String
    var terrainFeature: String? = null
    var naturalWonder: String? = null
    var resource: String? = null
    var improvement: String? = null
    var improvementInProgress: String? = null

    var roadStatus = RoadStatus.None
    var turnsToImprovement: Int = 0

    var hasBottomRightRiver = false
    var hasBottomRiver = false
    var hasBottomLeftRiver = false

    val latitude: Float
        get() = HexMath.getLatitude(position)
    val longitude: Float
        get() = HexMath.getLongitude(position)

    fun clone(): TileInfo {
        val toReturn = TileInfo()
        if(militaryUnit!=null) toReturn.militaryUnit=militaryUnit!!.clone()
        if(civilianUnit!=null) toReturn.civilianUnit=civilianUnit!!.clone()
        for(airUnit in airUnits) toReturn.airUnits.add(airUnit.clone())
        toReturn.position=position.cpy()
        toReturn.baseTerrain=baseTerrain
        toReturn.terrainFeature=terrainFeature
        toReturn.naturalWonder=naturalWonder
        toReturn.resource=resource
        toReturn.improvement=improvement
        toReturn.improvementInProgress=improvementInProgress
        toReturn.roadStatus=roadStatus
        toReturn.turnsToImprovement=turnsToImprovement
        return toReturn
    }

    fun containsGreatImprovement(): Boolean {
        if (improvement in Constants.greatImprovements) return true
        return false
    }

    fun containsUnfinishedGreatImprovement(): Boolean {
        if (improvementInProgress in Constants.greatImprovements) return true
        return false
    }

    fun containsUnique(unique: String): Boolean =
            isNaturalWonder() && getNaturalWonder().uniques.contains(unique)
    //region pure functions

    /** Returns military, civilian and air units in tile */
    fun getUnits() = sequence {
        if (militaryUnit != null) yield(militaryUnit!!)
        if (civilianUnit != null) yield(civilianUnit!!)
        if (airUnits.isNotEmpty()) yieldAll(airUnits)
    }

    /** This is for performance reasons of canPassThrough() - faster than getUnits().firstOrNull() */
    fun getFirstUnit(): MapUnit? {
        if (militaryUnit != null) return militaryUnit!!
        if (civilianUnit != null) return civilianUnit!!
        if (airUnits.isNotEmpty()) return airUnits.first()
        return null
    }

    fun getCity(): CityInfo? = owningCity

    fun getLastTerrain(): Terrain = if (terrainFeature != null) getTerrainFeature()!! else if(naturalWonder != null) getNaturalWonder() else getBaseTerrain()

    fun getTileResource(): TileResource =
            if (resource == null) throw Exception("No resource exists for this tile!")
            else ruleset.tileResources[resource!!]!!

    fun getNaturalWonder() : Terrain =
            if (naturalWonder == null) throw Exception("No natural wonder exists for this tile!")
            else ruleset.terrains[naturalWonder!!]!!

    fun isCityCenter(): Boolean = getCity()?.location == position
    fun isNaturalWonder() : Boolean = naturalWonder != null

    fun getTileImprovement(): TileImprovement? = if (improvement == null) null else ruleset.tileImprovements[improvement!!]


    // This is for performance - since we access the neighbors of a tile ALL THE TIME,
    // and the neighbors of a tile never change, it's much more efficient to save the list once and for all!
    @delegate:Transient
    val neighbors: Sequence<TileInfo> by lazy { getTilesAtDistance(1).toList().asSequence() }
    // We have to .toList() so that the values are stored together once for caching,
    // and the toSequence so that aggregations (like neighbors.flatMap{it.units} don't take up their own space

    fun getHeight(): Int {
        if (baseTerrain == Constants.mountain) return 4
        if (baseTerrain == Constants.hill) return 2
        if (terrainFeature == Constants.forest || terrainFeature == Constants.jungle) return 1
        return 0
    }

    fun getBaseTerrain(): Terrain = baseTerrainObject

    fun getOwner(): CivilizationInfo? {
        val containingCity = getCity()
        if(containingCity==null) return null
        return containingCity.civInfo
    }

    fun isFriendlyTerritory(civInfo: CivilizationInfo): Boolean {
        val tileOwner = getOwner()
        return when {
            tileOwner == null -> false
            tileOwner == civInfo -> true
            !civInfo.knows(tileOwner) -> false
            else -> tileOwner.getDiplomacyManager(civInfo).isConsideredFriendlyTerritory()
        }
    }

    fun getTerrainFeature(): Terrain? =
            if (terrainFeature == null) null else ruleset.terrains[terrainFeature!!]


    fun isWorked(): Boolean {
        val city = getCity()
        return city!=null && city.workedTiles.contains(position)
    }

    fun isLocked(): Boolean{
        val city = getCity()
        return city!=null && city.lockedTiles.contains(position)
    }

    fun getTileStats(observingCiv: CivilizationInfo): Stats = getTileStats(getCity(), observingCiv)

    fun getTileStats(city: CityInfo?, observingCiv: CivilizationInfo): Stats {
        var stats = getBaseTerrain().clone()

        if((baseTerrain== Constants.ocean||baseTerrain==Constants.coast) && city!=null
                && city.containsBuildingUnique("+1 food from Ocean and Coast tiles"))
            stats.food += 1

        if (terrainFeature != null) {
            val terrainFeatureBase = getTerrainFeature()
            if (terrainFeatureBase!!.overrideStats)
                stats = terrainFeatureBase.clone()
            else
                stats.add(terrainFeatureBase)

            if (terrainFeature == Constants.jungle && city != null
                    && city.containsBuildingUnique("+2 Science from each worked Jungle tile"))
                stats.science += 2f
            if (terrainFeature == "Oasis" && city != null
                    && city.containsBuildingUnique("+2 Gold for each source of Oil and oasis"))
                stats.gold += 2
            if (terrainFeature == Constants.forest && city != null
                    && city.containsBuildingUnique("+1 Production from each worked Forest tile"))
                stats.production += 1
        }

        if (naturalWonder != null) {
            val wonder = getNaturalWonder()
            stats.add(wonder)

            // Spain doubles tile yield
            if (city != null && city.civInfo.nation.unique == UniqueAbility.SEVEN_CITIES_OF_GOLD) {
                stats.add(wonder)
            }
        }

        if (hasViewableResource(observingCiv)) {
            val resource = getTileResource()
            stats.add(getTileResource()) // resource base
            if (resource.building != null && city != null && city.cityConstructions.isBuilt(resource.building!!)) {
                val resourceBuilding = tileMap.gameInfo.ruleSet.buildings[resource.building!!]!!
                stats.add(resourceBuilding.resourceBonusStats!!) // resource-specific building (eg forge, stable) bonus
            }
            if (resource.resourceType==ResourceType.Strategic
                    && observingCiv.nation.unique == UniqueAbility.SIBERIAN_RICHES)
                stats.production += 1
            if (city != null) {
                if (resource.name == "Oil"
                        && city.containsBuildingUnique("+2 Gold for each source of Oil and oasis"))
                    stats.gold += 2
                if ((resource.name == "Marble" || resource.name == "Stone")
                        && city.containsBuildingUnique("+2 Gold for each source of Marble and Stone"))
                    stats.gold += 2
                if (isWater) {
                    if(city.containsBuildingUnique("+1 production from all sea resources worked by the city"))
                        stats.production += 1
                    if(city.containsBuildingUnique("+1 production and gold from all sea resources worked by the city")){
                        stats.production += 1
                        stats.gold += 1
                    }
                }
            }
        }

        val improvement = getTileImprovement()
        if (improvement != null)
            stats.add(getImprovementStats(improvement, observingCiv, city))

        if(city!=null && isWater && city.containsBuildingUnique("+1 gold from worked water tiles in city"))
            stats.gold += 1

        if (isCityCenter()) {
            if (stats.food < 2) stats.food = 2f
            if (stats.production < 1) stats.production = 1f
        }

        if (stats.gold != 0f && observingCiv.goldenAges.isGoldenAge())
            stats.gold++

        if (stats.production < 0) stats.production = 0f

        return stats
    }

    fun getImprovementStats(improvement: TileImprovement, observingCiv: CivilizationInfo, city: CityInfo?): Stats {
        val stats =
            if (hasViewableResource(observingCiv) && getTileResource().improvement == improvement.name)
                getTileResource().improvementStats!!.clone() // resource-specific improvement
            else
                improvement.clone() // basic improvement

        if (improvement.improvingTech != null && observingCiv.tech.isResearched(improvement.improvingTech!!)) stats.add(improvement.improvingTechStats!!) // eg Chemistry for mines
        if (improvement.name == "Trading post" && city != null
                && city.civInfo.policies.hasEffect("+1 science from every trading post, +17% science from universities"))
            stats.science += 1f
        if (improvement.name == "Trading post" && city != null
                && city.civInfo.policies.hasEffect("+1 gold from every trading post, double gold from Great Merchant trade missions"))
            stats.gold += 1f
        if (containsGreatImprovement()
                && observingCiv.policies.hasEffect("Tile yield from great improvement +100%, golden ages increase by 50%"))
            stats.add(improvement) // again, for the double effect
        if (containsGreatImprovement() && city != null && city.civInfo.nation.unique == UniqueAbility.SCHOLARS_OF_THE_JADE_HALL)
            stats.science += 2

        if (improvement.uniques.contains("+1 additional Culture for each adjacent Moai"))
            stats.culture += neighbors.count { it.improvement == "Moai" }
        if (improvement.uniques.contains("+1 food for each adjacent Mountain"))
            stats.food += neighbors.count { it.baseTerrain == Constants.mountain }

        return stats
    }

    /** Returns true if the [improvement] can be built on this [TileInfo] */
    fun canBuildImprovement(improvement: TileImprovement, civInfo: CivilizationInfo): Boolean {
        val topTerrain = getLastTerrain()
        return when {
            isCityCenter() -> false
            improvement.name == this.improvement -> false
            improvement.uniqueTo != null && improvement.uniqueTo != civInfo.civName -> false
            improvement.techRequired?.let { civInfo.tech.isResearched(it) } == false -> false
            "Cannot be built on bonus resource" in improvement.uniques && resource != null
                            && getTileResource().resourceType == ResourceType.Bonus -> false
            improvement.terrainsCanBeBuiltOn.contains(topTerrain.name) -> true
            improvement.name == "Road" && roadStatus == RoadStatus.None -> true
            improvement.name == "Railroad" && this.roadStatus != RoadStatus.Railroad -> true
            improvement.name == "Remove Road" && this.roadStatus == RoadStatus.Road -> true
            improvement.name == "Remove Railroad" && this.roadStatus == RoadStatus.Railroad -> true
            improvement.name == Constants.cancelImprovementOrder && this.improvementInProgress != null -> true
            topTerrain.unbuildable && (topTerrain.name !in improvement.resourceTerrainAllow) -> false
            "Can only be built on Coastal tiles" in improvement.uniques && isCoastalTile() -> true
            else -> hasViewableResource(civInfo) && getTileResource().improvement == improvement.name
        }
    }

    fun hasImprovementInProgress() = improvementInProgress!=null

    @delegate:Transient
    private val _isCoastalTile: Boolean by lazy { neighbors.any { it.baseTerrain==Constants.coast } }
    fun isCoastalTile() = _isCoastalTile

    fun hasViewableResource(civInfo: CivilizationInfo): Boolean =
            resource != null && (getTileResource().revealedBy == null || civInfo.tech.isResearched(getTileResource().revealedBy!!))

    fun getViewableTilesList(distance:Int): List<TileInfo> =
            tileMap.getViewableTiles(position, distance)

    fun getTilesInDistance(distance: Int): Sequence<TileInfo> =
            tileMap.getTilesInDistance(position,distance)

    fun getTilesInDistanceRange(range: IntRange): Sequence<TileInfo> =
            tileMap.getTilesInDistanceRange(position, range)

    fun getTilesAtDistance(distance:Int): Sequence<TileInfo> =
            tileMap.getTilesAtDistance(position, distance)

    fun getDefensiveBonus(): Float {
        var bonus = getBaseTerrain().defenceBonus
        if(terrainFeature!=null) bonus += getTerrainFeature()!!.defenceBonus
        return bonus
    }

    fun aerialDistanceTo(otherTile:TileInfo): Int {
        val xDelta = position.x-otherTile.position.x
        val yDelta = position.y-otherTile.position.y
        return listOf(abs(xDelta),abs(yDelta), abs(xDelta-yDelta)).max()!!.toInt()
    }

    fun isRoughTerrain() = getBaseTerrain().rough || getTerrainFeature()?.rough == true

    override fun toString():String { // for debugging, it helps to see what you're doing
        return toString(null)
    }

    fun toString(viewingCiv: CivilizationInfo?): String {
        val lineList = ArrayList<String>() // more readable than StringBuilder, with same performance for our use-case
        val isViewableToPlayer = viewingCiv==null || UncivGame.Current.viewEntireMapForDebug
                || viewingCiv.viewableTiles.contains(this)

        if (isCityCenter()) {
            val city = getCity()!!
            var cityString = city.name.tr()
            if(isViewableToPlayer) cityString += " ("+city.health+")"
            lineList += cityString
            if(UncivGame.Current.viewEntireMapForDebug || city.civInfo == viewingCiv)
                lineList += city.cityConstructions.getProductionForTileInfo()
        }
        lineList += baseTerrain.tr()
        if (terrainFeature != null) lineList += terrainFeature!!.tr()
        if (resource!=null && (viewingCiv==null || hasViewableResource(viewingCiv))) lineList += resource!!.tr()
        if (naturalWonder != null) lineList += naturalWonder!!.tr()
        if (roadStatus !== RoadStatus.None && !isCityCenter()) lineList += roadStatus.toString().tr()
        if (improvement != null) lineList += improvement!!.tr()
        if (improvementInProgress != null && isViewableToPlayer)
            lineList += "{$improvementInProgress}\r\n{in} $turnsToImprovement {turns}".tr() // todo change to [] translation notation
        if (civilianUnit != null && isViewableToPlayer)
            lineList += civilianUnit!!.name.tr()+" - "+civilianUnit!!.civInfo.civName.tr()
        if(militaryUnit!=null && isViewableToPlayer){
            var milUnitString = militaryUnit!!.name.tr()
            if(militaryUnit!!.health<100) milUnitString += "(" + militaryUnit!!.health + ")"
            milUnitString += " - "+militaryUnit!!.civInfo.civName.tr()
            lineList += milUnitString
        }
        var defenceBonus = getDefensiveBonus()
        val tileImprovement = getTileImprovement()
        if (tileImprovement != null) {
            defenceBonus += when {
                tileImprovement.hasUnique("Gives a defensive bonus of 50%") -> 0.5f
                tileImprovement.hasUnique("Gives a defensive bonus of 100%") -> 1.0f
                else -> 0.0f
            }
        }
        if(defenceBonus != 0.0f){
            var defencePercentString = (defenceBonus*100).toInt().toString()+"%"
            if(!defencePercentString.startsWith("-")) defencePercentString = "+$defencePercentString"
            lineList += "[$defencePercentString] to unit defence".tr()
        }
        if(getBaseTerrain().impassable) lineList += Constants.impassable.tr()

        return lineList.joinToString("\n")
    }

    //endregion

    //region state-changing functions
    fun setTransients(){
        setTerrainTransients()
        setUnitTransients(true)
    }

    fun setTerrainTransients(){
        baseTerrainObject = ruleset.terrains[baseTerrain]!! // This is a HACK.
        isWater = getBaseTerrain().type==TerrainType.Water
        isLand = getBaseTerrain().type==TerrainType.Land
        isOcean = baseTerrain == Constants.ocean
    }

    fun setUnitTransients(unitCivTransients: Boolean) {
        for (unit in getUnits()) {
            unit.currentTile = this
            if(unitCivTransients)
                unit.assignOwner(tileMap.gameInfo.getCivilization(unit.owner),false)
            unit.setTransients(ruleset)
        }
    }

    fun startWorkingOnImprovement(improvement: TileImprovement, civInfo: CivilizationInfo) {
        improvementInProgress = improvement.name
        turnsToImprovement = improvement.getTurnsToBuild(civInfo)
    }
    fun stopWorkingOnImprovement() {
        improvementInProgress = null
        turnsToImprovement = 0
    }

    fun hasEnemySubmarine(viewingCiv:CivilizationInfo): Boolean {
        val unitsInTile = getUnits()
        if (unitsInTile.none()) return false
        if (unitsInTile.first().civInfo!=viewingCiv &&
                unitsInTile.firstOrNull { it.isInvisible() } != null) {
            return true
        }
        return false
    }

    fun hasConnection(civInfo: CivilizationInfo) =
            roadStatus != RoadStatus.None || forestOrJungleAreRoads(civInfo)

    fun hasRoad(civInfo: CivilizationInfo) =
            roadStatus == RoadStatus.Road || forestOrJungleAreRoads(civInfo)

    fun hasRailroad() =
            roadStatus == RoadStatus.Railroad

    private fun forestOrJungleAreRoads(civInfo: CivilizationInfo) =
            civInfo.nation.forestsAndJunglesAreRoads
                    && (terrainFeature == Constants.jungle || terrainFeature == Constants.forest)
                    && isFriendlyTerritory(civInfo)
    //endregion
}
