package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.*
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.utils.Fonts
import kotlin.math.abs

open class TileInfo {
    @Transient
    lateinit var tileMap: TileMap

    @Transient
    lateinit var ruleset: Ruleset  // a tile can be a tile with a ruleset, even without a map.

    @Transient
    var owningCity: CityInfo? = null

    @Transient
    private lateinit var baseTerrainObject: Terrain

    // These are for performance - checked with every tile movement and "canEnter" check, which makes them performance-critical
    @Transient
    var isLand = false

    @Transient
    var isWater = false

    @Transient
    var isOcean = false

    // This will be called often - farm can be built on Hill and tundra if adjacent to fresh water
    // and farms on adjacent to fresh water tiles will have +1 additional Food after researching Civil Service
    @delegate:Transient
    val isAdjacentToFreshwater: Boolean by lazy { matchesUniqueFilter("River") || matchesUniqueFilter("Fresh water")
            || neighbors.any { it.matchesUniqueFilter("Fresh water") } }

    var militaryUnit: MapUnit? = null
    var civilianUnit: MapUnit? = null
    var airUnits = ArrayList<MapUnit>()

    var position: Vector2 = Vector2.Zero
    lateinit var baseTerrain: String
    var terrainFeature: String? = null
    var naturalWonder: String? = null
    var resource: String? = null
    var improvement: String? = null
    var improvementInProgress: String? = null

    var roadStatus = RoadStatus.None
    var turnsToImprovement: Int = 0

    fun isHill() = baseTerrain == Constants.hill

    var hasBottomRightRiver = false
    var hasBottomRiver = false
    var hasBottomLeftRiver = false

    val latitude: Float
        get() = HexMath.getLatitude(position)
    val longitude: Float
        get() = HexMath.getLongitude(position)

    fun clone(): TileInfo {
        val toReturn = TileInfo()
        if (militaryUnit != null) toReturn.militaryUnit = militaryUnit!!.clone()
        if (civilianUnit != null) toReturn.civilianUnit = civilianUnit!!.clone()
        for (airUnit in airUnits) toReturn.airUnits.add(airUnit.clone())
        toReturn.position = position.cpy()
        toReturn.baseTerrain = baseTerrain
        toReturn.terrainFeature = terrainFeature
        toReturn.naturalWonder = naturalWonder
        toReturn.resource = resource
        toReturn.improvement = improvement
        toReturn.improvementInProgress = improvementInProgress
        toReturn.roadStatus = roadStatus
        toReturn.turnsToImprovement = turnsToImprovement
        toReturn.hasBottomLeftRiver = hasBottomLeftRiver
        toReturn.hasBottomRightRiver = hasBottomRightRiver
        toReturn.hasBottomRiver = hasBottomRiver
        return toReturn
    }

    fun containsGreatImprovement(): Boolean {
        return getTileImprovement()?.isGreatImprovement() == true
    }

    fun containsUnfinishedGreatImprovement(): Boolean {
        if (improvementInProgress == null) return false
        return ruleset.tileImprovements[improvementInProgress!!]!!.isGreatImprovement()
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

    fun getLastTerrain(): Terrain = when {
        terrainFeature != null -> getTerrainFeature()!!
        naturalWonder != null -> getNaturalWonder()
        else -> getBaseTerrain()
    }

    fun getTileResource(): TileResource =
            if (resource == null) throw Exception("No resource exists for this tile!")
            else if(!ruleset.tileResources.containsKey(resource!!)) throw Exception("Resource $resource does not exist in this ruleset!")
            else ruleset.tileResources[resource!!]!!

    fun getNaturalWonder(): Terrain =
            if (naturalWonder == null) throw Exception("No natural wonder exists for this tile!")
            else ruleset.terrains[naturalWonder!!]!!

    fun isCityCenter(): Boolean = getCity()?.location == position
    fun isNaturalWonder(): Boolean = naturalWonder != null
    fun isImpassible() = getLastTerrain().impassable

    fun getTileImprovement(): TileImprovement? = if (improvement == null) null else ruleset.tileImprovements[improvement!!]


    // This is for performance - since we access the neighbors of a tile ALL THE TIME,
    // and the neighbors of a tile never change, it's much more efficient to save the list once and for all!
    @delegate:Transient
    val neighbors: Sequence<TileInfo> by lazy { getTilesAtDistance(1).toList().asSequence() }
    // We have to .toList() so that the values are stored together once for caching,
    // and the toSequence so that aggregations (like neighbors.flatMap{it.units} don't take up their own space

    fun getHeight(): Int {
        if (baseTerrain == Constants.mountain) return 4
        if (isHill()) return 2
        if (terrainFeature == Constants.forest || terrainFeature == Constants.jungle) return 1
        return 0
    }

    fun getBaseTerrain(): Terrain = baseTerrainObject

    fun getOwner(): CivilizationInfo? {
        val containingCity = getCity()
        if (containingCity == null) return null
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

    fun getWorkingCity(): CityInfo? {
        val civInfo = getOwner()
        if (civInfo == null) return null
        return civInfo.cities.firstOrNull { it.isWorked(this) }
    }

    fun isWorked(): Boolean = getWorkingCity() != null

    fun isLocked(): Boolean {
        val workingCity = getWorkingCity()
        return workingCity != null && workingCity.lockedTiles.contains(position)
    }

    fun getTileStats(observingCiv: CivilizationInfo): Stats = getTileStats(getCity(), observingCiv)

    fun getTileStats(city: CityInfo?, observingCiv: CivilizationInfo): Stats {
        var stats = getBaseTerrain().clone()

        if (terrainFeature != null) {
            val terrainFeatureBase = getTerrainFeature()
            if (terrainFeatureBase!!.overrideStats)
                stats = terrainFeatureBase.clone()
            else
                stats.add(terrainFeatureBase)
        }

        if (city != null) {
            val cityWideUniques = city.cityConstructions.builtBuildingUniqueMap.getUniques("[] from [] tiles in this city")
            val civWideUniques = city.civInfo.getMatchingUniques("[] from every []")
            for (unique in cityWideUniques + civWideUniques) {
                val tileType = unique.params[1]
                if (tileType == improvement) continue // This is added to the calculation in getImprovementStats. we don't want to add it twice
                if (matchesUniqueFilter(tileType, observingCiv)
                        || (tileType == "Strategic resource" && hasViewableResource(observingCiv) && getTileResource().resourceType == ResourceType.Strategic)
                        || (tileType == "Luxury resource" && hasViewableResource(observingCiv) && getTileResource().resourceType == ResourceType.Luxury)
                        || (tileType == "Bonus resource" && hasViewableResource(observingCiv) && getTileResource().resourceType == ResourceType.Bonus)
                        || (tileType == "Water resource" && isWater && hasViewableResource(observingCiv))
                ) stats.add(unique.stats)
            }
        }

        if (naturalWonder != null) {
            val wonder = getNaturalWonder()
            stats.add(wonder)

            // Spain doubles tile yield
            if (city != null && city.civInfo.hasUnique("Tile yields from Natural Wonders doubled")) {
                stats.add(wonder)
            }
        }

        if (hasViewableResource(observingCiv)) {
            val resource = getTileResource()
            stats.add(getTileResource()) // resource base
            if (resource.building != null && city != null && city.cityConstructions.isBuilt(resource.building!!)) {
                val resourceBuilding = tileMap.gameInfo.ruleSet.buildings[resource.building!!]
                if (resourceBuilding != null && resourceBuilding.resourceBonusStats != null)
                    stats.add(resourceBuilding.resourceBonusStats!!) // resource-specific building (eg forge, stable) bonus
            }
        }

        val improvement = getTileImprovement()
        if (improvement != null)
            stats.add(getImprovementStats(improvement, observingCiv, city))

        if (isCityCenter()) {
            if (stats.food < 2) stats.food = 2f
            if (stats.production < 1) stats.production = 1f
        }

        if (stats.gold != 0f && observingCiv.goldenAges.isGoldenAge())
            stats.gold++

        if (isAdjacentToRiver()) stats.gold++

        if (stats.production < 0) stats.production = 0f

        return stats
    }

    fun getImprovementStats(improvement: TileImprovement, observingCiv: CivilizationInfo, city: CityInfo?): Stats {
        val stats = improvement.clone()
        if (hasViewableResource(observingCiv) && getTileResource().improvement == improvement.name)
            stats.add(getTileResource().improvementStats!!.clone()) // resource-specific improvement

        for (unique in improvement.uniqueObjects)
            if (unique.placeholderText == "[] once [] is discovered" && observingCiv.tech.isResearched(unique.params[1]))
                stats.add(unique.stats)

        if (city != null) {
            val cityWideUniques = city.cityConstructions.builtBuildingUniqueMap.getUniques("[] from [] tiles in this city")
            val civWideUniques = city.civInfo.getMatchingUniques("[] from every []")
            val improvementUniques = improvement.uniqueObjects.filter {
                it.placeholderText == "[] on [] tiles once [] is discovered"
                        && observingCiv.tech.isResearched(it.params[2])
            }
            for (unique in cityWideUniques + civWideUniques + improvementUniques) {
                if (improvement.name == unique.params[1]
                        || (unique.params[1] == "Great Improvement" && improvement.isGreatImprovement())
                        || (unique.params[1] == "fresh water" && isAdjacentToFreshwater)
                        || (unique.params[1] == "non-fresh water" && !isAdjacentToFreshwater)
                )
                    stats.add(unique.stats)
            }
        }

        if (containsGreatImprovement()
                && observingCiv.hasUnique("Tile yield from Great Improvements +100%"))
            stats.add(improvement) // again, for the double effect

        for (unique in improvement.uniqueObjects)
            if (unique.placeholderText == "[] for each adjacent []") {
                val adjacent = unique.params[1]
                val numberOfBonuses = neighbors.count {
                    it.improvement == adjacent
                            || it.matchesUniqueFilter(adjacent, observingCiv)
                            || it.roadStatus.name == adjacent
                }
                stats.add(unique.stats.times(numberOfBonuses.toFloat()))
            }

        return stats
    }

    /** Returns true if the [improvement] can be built on this [TileInfo] */
    fun canBuildImprovement(improvement: TileImprovement, civInfo: CivilizationInfo): Boolean {
        return when {
            improvement.uniqueTo != null && improvement.uniqueTo != civInfo.civName -> false
            improvement.techRequired != null && !civInfo.tech.isResearched(improvement.techRequired!!) -> false
            getOwner() != civInfo && !improvement.hasUnique("Can be built outside your borders") -> false
            else -> canImprovementBeBuiltHere(improvement, hasViewableResource(civInfo))
        }
    }

    /** Without regards to what civinfo it is, a lot of the checks are just for the improvement on the tile.
     *  Doubles as a check for the map editor.
     */
    fun canImprovementBeBuiltHere(improvement: TileImprovement, resourceIsVisible: Boolean = resource != null): Boolean {
        val topTerrain = getLastTerrain()

        return when {
            improvement.name == this.improvement -> false
            isCityCenter() -> false
            "Cannot be built on bonus resource" in improvement.uniques && resource != null
                    && getTileResource().resourceType == ResourceType.Bonus -> false

            // Road improvements can change on tiles withh irremovable improvements - nothing else can, though.
            improvement.name != RoadStatus.Railroad.name && improvement.name != RoadStatus.Railroad.name
                    && improvement.name != "Remove Road" && improvement.name != "Remove Railroad"
                    && getTileImprovement().let { it!=null && it.hasUnique("Irremovable") } -> false

            // Tiles with no terrains, and no turns to build, are like great improvements - they're placeable
            improvement.terrainsCanBeBuiltOn.isEmpty() && improvement.turnsToBuild==0 && isLand -> true
            improvement.terrainsCanBeBuiltOn.contains(topTerrain.name) -> true
            improvement.uniqueObjects.filter { it.placeholderText == "Must be next to []" }.any {
                val filter = it.params[0]
                if (filter == "River") return@any !isAdjacentToRiver()
                else return@any !neighbors.any { it.matchesUniqueFilter(filter) }
            } -> false
            improvement.name == "Road" && roadStatus == RoadStatus.None && !isWater -> true
            improvement.name == "Railroad" && this.roadStatus != RoadStatus.Railroad && !isWater -> true
            improvement.name == "Remove Road" && this.roadStatus == RoadStatus.Road -> true
            improvement.name == "Remove Railroad" && this.roadStatus == RoadStatus.Railroad -> true
            improvement.name == Constants.cancelImprovementOrder && this.improvementInProgress != null -> true
            topTerrain.unbuildable && (topTerrain.name !in improvement.resourceTerrainAllow) -> false
            // DO NOT reverse this &&. isAdjacentToFreshwater() is a lazy which calls a function, and reversing it breaks the tests.
            improvement.hasUnique("Can also be built on tiles adjacent to fresh water") && isAdjacentToFreshwater -> true
            "Can only be built on Coastal tiles" in improvement.uniques && isCoastalTile() -> true
            else -> resourceIsVisible && getTileResource().improvement == improvement.name
        }
    }

    fun matchesUniqueFilter(filter: String, civInfo: CivilizationInfo?=null): Boolean {
        return filter == baseTerrain
                || filter == Constants.hill && isHill()
                || filter == "River" && isAdjacentToRiver()
                || filter == terrainFeature
                || baseTerrainObject.uniques.contains(filter)
                || terrainFeature != null && getTerrainFeature()!!.uniques.contains(filter)
                || improvement == filter
                || civInfo != null && hasViewableResource(civInfo) && resource == filter
                || filter == "Water" && isWater
                || filter == "Land" && isLand
    }

    fun hasImprovementInProgress() = improvementInProgress != null

    @delegate:Transient
    private val _isCoastalTile: Boolean by lazy { neighbors.any { it.baseTerrain == Constants.coast } }
    fun isCoastalTile() = _isCoastalTile

    fun hasViewableResource(civInfo: CivilizationInfo): Boolean =
            resource != null && (getTileResource().revealedBy == null || civInfo.tech.isResearched(getTileResource().revealedBy!!))

    fun getViewableTilesList(distance: Int): List<TileInfo> =
            tileMap.getViewableTiles(position, distance)

    fun getTilesInDistance(distance: Int): Sequence<TileInfo> =
            tileMap.getTilesInDistance(position, distance)

    fun getTilesInDistanceRange(range: IntRange): Sequence<TileInfo> =
            tileMap.getTilesInDistanceRange(position, range)

    fun getTilesAtDistance(distance: Int): Sequence<TileInfo> =
            tileMap.getTilesAtDistance(position, distance)

    fun getDefensiveBonus(): Float {
        var bonus = getLastTerrain().defenceBonus
        val tileImprovement = getTileImprovement()
        if (tileImprovement != null) {
            for (unique in tileImprovement.uniqueObjects)
                if (unique.placeholderText == "Gives a defensive bonus of []%")
                    bonus += unique.params[0].toFloat() / 100
        }
        return bonus
    }

    fun aerialDistanceTo(otherTile: TileInfo): Int {
        val xDelta = position.x - otherTile.position.x
        val yDelta = position.y - otherTile.position.y
        return listOf(abs(xDelta), abs(yDelta), abs(xDelta - yDelta)).max()!!.toInt()
    }

    fun isRoughTerrain() = getBaseTerrain().rough || getTerrainFeature()?.rough == true

    override fun toString(): String { // for debugging, it helps to see what you're doing
        return toString(null)
    }

    /** The two tiles have a river between them */
    fun isConnectedByRiver(otherTile: TileInfo): Boolean {
        if (otherTile == this) throw Exception("Should not be called to compare to self!")

        return when (tileMap.getNeighborTileClockPosition(this, otherTile)) {
            2 -> otherTile.hasBottomLeftRiver // we're to the bottom-left of it
            4 -> hasBottomRightRiver // we're to the top-right of it
            6 -> hasBottomRiver // we're directly above it
            8 -> hasBottomLeftRiver // we're to the top-left of it
            10 -> otherTile.hasBottomRightRiver // we're to the bottom-right of it
            12 -> otherTile.hasBottomRiver // we're directly below it
            else -> throw Exception("Should never call this function on a non-neighbor!")
        }
    }

    fun isAdjacentToRiver() = neighbors.any { isConnectedByRiver(it) }

    fun canCivEnter(civInfo: CivilizationInfo): Boolean {
        val tileOwner = getOwner()
        if (tileOwner == null || tileOwner == civInfo) return true
        // comparing the CivInfo objects is cheaper than comparing strings
        if (isCityCenter() && civInfo.isAtWarWith(tileOwner)
                && !getCity()!!.hasJustBeenConquered) return false
        if (!civInfo.canEnterTiles(tileOwner)
                && !(civInfo.isPlayerCivilization() && tileOwner.isCityState())) return false
        // AIs won't enter city-state's border.
        return true
    }

    fun toString(viewingCiv: CivilizationInfo?): String {
        val lineList = ArrayList<String>() // more readable than StringBuilder, with same performance for our use-case
        val isViewableToPlayer = viewingCiv == null || UncivGame.Current.viewEntireMapForDebug
                || viewingCiv.viewableTiles.contains(this)

        if (isCityCenter()) {
            val city = getCity()!!
            var cityString = city.name.tr()
            if (isViewableToPlayer) cityString += " (" + city.health + ")"
            lineList += cityString
            if (UncivGame.Current.viewEntireMapForDebug || city.civInfo == viewingCiv)
                lineList += city.cityConstructions.getProductionForTileInfo()
        }
        lineList += baseTerrain.tr()
        if (terrainFeature != null) lineList += terrainFeature!!.tr()
        if (resource != null && (viewingCiv == null || hasViewableResource(viewingCiv))) lineList += resource!!.tr()
        if (naturalWonder != null) lineList += naturalWonder!!.tr()
        if (roadStatus !== RoadStatus.None && !isCityCenter()) lineList += roadStatus.name.tr()
        if (improvement != null) lineList += improvement!!.tr()
        if (improvementInProgress != null && isViewableToPlayer) {
            var line = "{$improvementInProgress}"
            if (turnsToImprovement > 0) line += " - $turnsToImprovement${Fonts.turn}"
            else line += " ({Under construction})"
            lineList += line.tr()
        }
        if (civilianUnit != null && isViewableToPlayer)
            lineList += civilianUnit!!.name.tr() + " - " + civilianUnit!!.civInfo.civName.tr()
        if (militaryUnit != null && isViewableToPlayer) {
            var milUnitString = militaryUnit!!.name.tr()
            if (militaryUnit!!.health < 100) milUnitString += "(" + militaryUnit!!.health + ")"
            milUnitString += " - " + militaryUnit!!.civInfo.civName.tr()
            lineList += milUnitString
        }
        val defenceBonus = getDefensiveBonus()
        if (defenceBonus != 0f) {
            var defencePercentString = (defenceBonus * 100).toInt().toString() + "%"
            if (!defencePercentString.startsWith("-")) defencePercentString = "+$defencePercentString"
            lineList += "[$defencePercentString] to unit defence".tr()
        }
        if (isImpassible()) lineList += Constants.impassable.tr()

        return lineList.joinToString("\n")
    }



    fun hasEnemyInvisibleUnit(viewingCiv: CivilizationInfo): Boolean {
        val unitsInTile = getUnits()
        if (unitsInTile.none()) return false
        if (unitsInTile.first().civInfo != viewingCiv &&
                unitsInTile.firstOrNull { it.isInvisible() } != null) {
            return true
        }
        return false
    }

    fun hasConnection(civInfo: CivilizationInfo) =
            roadStatus != RoadStatus.None || forestOrJungleAreRoads(civInfo)


    private fun forestOrJungleAreRoads(civInfo: CivilizationInfo) =
            civInfo.nation.forestsAndJunglesAreRoads
                    && (terrainFeature == Constants.jungle || terrainFeature == Constants.forest)
                    && isFriendlyTerritory(civInfo)

    fun getRulesetIncompatability(ruleset: Ruleset):String{
        if (!ruleset.terrains.containsKey(baseTerrain)) return "Base terrain $baseTerrain does not exist in ruleset!"
        if (terrainFeature != null && !ruleset.terrains.containsKey(terrainFeature)) return "Terrain feature $terrainFeature does not exist in ruleset!"
        if (resource != null && !ruleset.tileResources.containsKey(resource)) return "Resource $resource does not exist in ruleset!"
        if (improvement != null && !improvement!!.startsWith("StartingLocation")
                && !ruleset.tileImprovements.containsKey(improvement)) return "Improvement $improvement does not exist in ruleset!"
        return ""
    }


    //endregion

    //region state-changing functions
    fun setTransients() {
        setTerrainTransients()
        setUnitTransients(true)
    }

    fun setTerrainTransients() {
        if (!ruleset.terrains.containsKey(baseTerrain))
            throw Exception()
        baseTerrainObject = ruleset.terrains[baseTerrain]!!
        isWater = getBaseTerrain().type == TerrainType.Water
        isLand = getBaseTerrain().type == TerrainType.Land
        isOcean = baseTerrain == Constants.ocean
    }

    fun setUnitTransients(unitCivTransients: Boolean) {
        for (unit in getUnits()) {
            unit.currentTile = this
            if (unitCivTransients)
                unit.assignOwner(tileMap.gameInfo.getCivilization(unit.owner), false)
            unit.setTransients(ruleset)
        }
    }

    fun stripUnits() {
        for (unit in this.getUnits()) removeUnit(unit)
    }


    /** If the unit isn't in the ruleset we can't even know what type of unit this is! So check each place
     * This works with no transients so can be called from gameInfo.setTransients with no fear
     */
    fun removeUnit(mapUnit: MapUnit) {
        when {
            airUnits.contains(mapUnit) -> airUnits.remove(mapUnit)
            civilianUnit == mapUnit -> civilianUnit = null
            else -> militaryUnit = null
        }
    }

    fun startWorkingOnImprovement(improvement: TileImprovement, civInfo: CivilizationInfo) {
        improvementInProgress = improvement.name
        turnsToImprovement = if (civInfo.gameInfo.gameParameters.godMode) 1 else improvement.getTurnsToBuild(civInfo)
    }

    fun stopWorkingOnImprovement() {
        improvementInProgress = null
        turnsToImprovement = 0
    }

    //endregion
}
