package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.extensions.toPercent
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

open class TileInfo : IsPartOfGameInfoSerialization {
    @Transient
    lateinit var tileMap: TileMap

    @Transient
    lateinit var ruleset: Ruleset  // a tile can be a tile with a ruleset, even without a map.

    @Transient
    private var isCityCenterInternal = false

    @Transient
    var owningCity: CityInfo? = null
        private set

    fun setOwningCity(city:CityInfo?){
        owningCity = city
        isCityCenterInternal = getCity()?.location == position
    }

    @Transient
    private lateinit var baseTerrainObject: Terrain

    // These are for performance - checked with every tile movement and "canEnter" check, which makes them performance-critical
    @Transient
    var isLand = false

    @Transient
    var isWater = false

    @Transient
    var isOcean = false

    var militaryUnit: MapUnit? = null
    var civilianUnit: MapUnit? = null
    var airUnits = ArrayList<MapUnit>()

    var position: Vector2 = Vector2.Zero
    lateinit var baseTerrain: String
    var terrainFeatures: List<String> = listOf()
        private set

    @Transient
    var terrainFeatureObjects: List<Terrain> = listOf()
        private set

    @Transient
    /** Saves a sequence of a list */
    var allTerrains: Sequence<Terrain> = sequenceOf()
        private set

    @Transient
    var terrainUniqueMap = UniqueMap()
        private set



    var naturalWonder: String? = null
    var resource: String? = null
        set(value) {
            tileResourceCache = null
            field = value
        }
    var resourceAmount: Int = 0
    var improvement: String? = null
    var improvementInProgress: String? = null

    var roadStatus = RoadStatus.None
    var turnsToImprovement: Int = 0

    fun isHill() = baseTerrain == Constants.hill || terrainFeatures.contains(Constants.hill)

    var hasBottomRightRiver = false
    var hasBottomRiver = false
    var hasBottomLeftRiver = false

    private var continent = -1

    val latitude: Float
        get() = HexMath.getLatitude(position)
    val longitude: Float
        get() = HexMath.getLongitude(position)

    fun clone(): TileInfo {
        val toReturn = TileInfo()
        toReturn.tileMap = tileMap
        toReturn.ruleset = ruleset
        toReturn.isCityCenterInternal = isCityCenterInternal
        toReturn.owningCity = owningCity
        toReturn.baseTerrainObject = baseTerrainObject
        toReturn.isLand = isLand
        toReturn.isWater = isWater
        toReturn.isOcean = isOcean
        if (militaryUnit != null) toReturn.militaryUnit = militaryUnit!!.clone()
        if (civilianUnit != null) toReturn.civilianUnit = civilianUnit!!.clone()
        for (airUnit in airUnits) toReturn.airUnits.add(airUnit.clone())
        toReturn.position = position.cpy()
        toReturn.baseTerrain = baseTerrain
        toReturn.terrainFeatures = terrainFeatures // immutable lists can be directly passed around
        toReturn.terrainFeatureObjects = terrainFeatureObjects
        toReturn.naturalWonder = naturalWonder
        toReturn.resource = resource
        toReturn.resourceAmount = resourceAmount
        toReturn.improvement = improvement
        toReturn.improvementInProgress = improvementInProgress
        toReturn.roadStatus = roadStatus
        toReturn.turnsToImprovement = turnsToImprovement
        toReturn.hasBottomLeftRiver = hasBottomLeftRiver
        toReturn.hasBottomRightRiver = hasBottomRightRiver
        toReturn.hasBottomRiver = hasBottomRiver
        toReturn.continent = continent
        return toReturn
    }

    //region pure functions

    fun containsGreatImprovement(): Boolean {
        return getTileImprovement()?.isGreatImprovement() == true
    }

    fun containsUnfinishedGreatImprovement(): Boolean {
        if (improvementInProgress == null) return false
        return ruleset.tileImprovements[improvementInProgress!!]!!.isGreatImprovement()
    }

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

    /** Return null if military on tile, or no civilian */
    fun getUnguardedCivilian(attacker: MapUnit): MapUnit? {
        if (militaryUnit != null && militaryUnit != attacker) return null
        if (civilianUnit != null) return civilianUnit!!
        return null
    }

    fun getCity(): CityInfo? = owningCity

    fun getLastTerrain(): Terrain = when {
        terrainFeatures.isNotEmpty() -> ruleset.terrains[terrainFeatures.last()]
                ?: getBaseTerrain()  // defense against rare edge cases involving baseTerrain Hill deprecation
        naturalWonder != null -> getNaturalWonder()
        else -> getBaseTerrain()
    }

    @Transient
    private var tileResourceCache: TileResource? = null
    val tileResource: TileResource
        get() {
            if (tileResourceCache == null) {
                if (resource == null) throw Exception("No resource exists for this tile!")
                if (!ruleset.tileResources.containsKey(resource!!)) throw Exception("Resource $resource does not exist in this ruleset!")
                tileResourceCache = ruleset.tileResources[resource!!]!!
            }
            return tileResourceCache!!
        }

    private fun getNaturalWonder(): Terrain =
            if (naturalWonder == null) throw Exception("No natural wonder exists for this tile!")
            else ruleset.terrains[naturalWonder!!]!!

    fun isCityCenter(): Boolean = isCityCenterInternal
    fun isNaturalWonder(): Boolean = naturalWonder != null
    fun isImpassible() = getLastTerrain().impassable

    fun getTileImprovement(): TileImprovement? = if (improvement == null) null else ruleset.tileImprovements[improvement!!]
    fun getTileImprovementInProgress(): TileImprovement? = if (improvementInProgress == null) null else ruleset.tileImprovements[improvementInProgress!!]

    fun getShownImprovement(viewingCiv: CivilizationInfo?): String? {
        return if (viewingCiv == null || viewingCiv.playerType == PlayerType.AI || viewingCiv.isSpectator())
            improvement
        else
            viewingCiv.lastSeenImprovement[position]
    }


    // This is for performance - since we access the neighbors of a tile ALL THE TIME,
    // and the neighbors of a tile never change, it's much more efficient to save the list once and for all!
    @delegate:Transient
    val neighbors: Sequence<TileInfo> by lazy { getTilesAtDistance(1).toList().asSequence() }
    // We have to .toList() so that the values are stored together once for caching,
    // and the toSequence so that aggregations (like neighbors.flatMap{it.units} don't take up their own space

    /** Returns the left shared neighbor of `this` and [neighbor] (relative to the view direction `this`->[neighbor]), or null if there is no such tile. */
    fun getLeftSharedNeighbor(neighbor: TileInfo): TileInfo? {
        return tileMap.getClockPositionNeighborTile(this,(tileMap.getNeighborTileClockPosition(this, neighbor) - 2) % 12)
    }

    /** Returns the right shared neighbor of `this` and [neighbor] (relative to the view direction `this`->[neighbor]), or null if there is no such tile. */
    fun getRightSharedNeighbor(neighbor: TileInfo): TileInfo? {
        return tileMap.getClockPositionNeighborTile(this,(tileMap.getNeighborTileClockPosition(this, neighbor) + 2) % 12)
    }

    @delegate:Transient
    val height : Int by lazy {
        allTerrains.flatMap { it.uniqueObjects }
            .filter { it.isOfType(UniqueType.VisibilityElevation) }
            .map { it.params[0].toInt() }.sum()
    }


    fun getBaseTerrain(): Terrain = baseTerrainObject

    fun getOwner(): CivilizationInfo? {
        val containingCity = getCity() ?: return null
        return containingCity.civInfo
    }

    fun isFriendlyTerritory(civInfo: CivilizationInfo): Boolean {
        val tileOwner = getOwner()
        return when {
            tileOwner == null -> false
            tileOwner == civInfo -> true
            !civInfo.knows(tileOwner) -> false
            else -> civInfo.getDiplomacyManager(tileOwner).isConsideredFriendlyTerritory()
        }
    }

    fun isEnemyTerritory(civInfo: CivilizationInfo): Boolean {
        val tileOwner = getOwner() ?: return false
        return civInfo.isAtWarWith(tileOwner)
    }

    fun isRoughTerrain() = allTerrains.any{ it.isRough() }

    /** Checks whether any of the TERRAINS of this tile has a certain unique */
    fun terrainHasUnique(uniqueType: UniqueType) = terrainUniqueMap.getUniques(uniqueType).any()
    /** Get all uniques of this type that any TERRAIN on this tile has */
    fun getTerrainMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals = StateForConditionals(tile=this) ): Sequence<Unique> {
        return terrainUniqueMap.getMatchingUniques(uniqueType, stateForConditionals)
    }

    /** Get all uniques of this type that any part of this tile has: terrains, improvement, resource */
    fun getMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals = StateForConditionals(tile=this)): Sequence<Unique> {
        var uniques = getTerrainMatchingUniques(uniqueType, stateForConditionals)
        if (improvement != null){
            val tileImprovement = getTileImprovement()
            if (tileImprovement != null) {
                uniques += tileImprovement.getMatchingUniques(uniqueType, stateForConditionals)
            }
        }
        if (resource != null)
            uniques += tileResource.getMatchingUniques(uniqueType, stateForConditionals)
        return uniques
    }

    fun getWorkingCity(): CityInfo? {
        val civInfo = getOwner() ?: return null
        return civInfo.cities.firstOrNull { it.isWorked(this) }
    }

    fun isWorked(): Boolean = getWorkingCity() != null
    fun providesYield() = getCity() != null && (isCityCenter() || isWorked()
            || getTileImprovement()?.hasUnique(UniqueType.TileProvidesYieldWithoutPopulation) == true
            || terrainHasUnique(UniqueType.TileProvidesYieldWithoutPopulation))

    fun isLocked(): Boolean {
        val workingCity = getWorkingCity()
        return workingCity != null && workingCity.lockedTiles.contains(position)
    }

    fun getTileStats(observingCiv: CivilizationInfo?): Stats = getTileStats(getCity(), observingCiv)

    fun getTileStats(city: CityInfo?, observingCiv: CivilizationInfo?,
                     localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)
    ): Stats {
        var stats = getBaseTerrain().cloneStats()

        val stateForConditionals = StateForConditionals(civInfo = observingCiv, cityInfo = city, tile = this)

        for (terrainFeatureBase in terrainFeatureObjects) {
            when {
                terrainFeatureBase.hasUnique(UniqueType.NullifyYields) ->
                    return terrainFeatureBase.cloneStats()
                terrainFeatureBase.overrideStats -> stats = terrainFeatureBase.cloneStats()
                else -> stats.add(terrainFeatureBase)
            }
        }

        if (naturalWonder != null) {
            val wonderStats = getNaturalWonder().cloneStats()

            // Spain doubles tile yield
            if (city != null && city.civInfo.hasUnique(UniqueType.DoubleStatsFromNaturalWonders, stateForConditionals)) {
                wonderStats.timesInPlace(2f)
            }

            if (getNaturalWonder().overrideStats)
                stats = wonderStats
            else
                stats.add(wonderStats)
        }

        if (city != null) {
            var tileUniques = city.getMatchingUniques(UniqueType.StatsFromTiles, stateForConditionals)
                .filter { city.matchesFilter(it.params[2]) }
            tileUniques += city.getMatchingUniques(UniqueType.StatsFromObject, stateForConditionals)
            for (unique in localUniqueCache.get("StatsFromTilesAndObjects", tileUniques)) {
                val tileType = unique.params[1]
                if (!matchesTerrainFilter(tileType, observingCiv)) continue
                stats.add(unique.stats)
                if (naturalWonder != null
                    && tileType == "Natural Wonder"
                    && city.civInfo.hasUnique(UniqueType.DoubleStatsFromNaturalWonders)
                ) {
                    stats.add(unique.stats)
                }
            }

            for (unique in localUniqueCache.get("StatsFromTilesWithout",
                city.getMatchingUniques(UniqueType.StatsFromTilesWithout, stateForConditionals))
            ) {
                if (
                    matchesTerrainFilter(unique.params[1]) &&
                    !matchesTerrainFilter(unique.params[2]) &&
                    city.matchesFilter(unique.params[3])
                )
                    stats.add(unique.stats)
            }
        }

        if (isAdjacentToRiver()) stats.gold++

        if (observingCiv != null) {
            // resource base
            if (hasViewableResource(observingCiv)) stats.add(tileResource)

            val improvement = getTileImprovement()
            if (improvement != null)
                stats.add(getImprovementStats(improvement, observingCiv, city, localUniqueCache))

            if (stats.gold != 0f && observingCiv.goldenAges.isGoldenAge())
                stats.gold++
        }
        if (isCityCenter()) {
            if (stats.food < 2) stats.food = 2f
            if (stats.production < 1) stats.production = 1f
        }

        for ((stat, value) in stats)
            if (value < 0f) stats[stat] = 0f

        for ((stat, value) in getTilePercentageStats(observingCiv, city)) {
            stats[stat] *= value.toPercent()
        }

        return stats
    }

    // Only gets the tile percentage bonus, not the improvement percentage bonus
    @Suppress("MemberVisibilityCanBePrivate")
    fun getTilePercentageStats(observingCiv: CivilizationInfo?, city: CityInfo?): Stats {
        val stats = Stats()
        val stateForConditionals = StateForConditionals(civInfo = observingCiv, cityInfo = city, tile = this)

        if (city != null) {
            for (unique in city.getMatchingUniques(UniqueType.StatPercentFromObject, stateForConditionals)) {
                val tileFilter = unique.params[2]
                if (matchesTerrainFilter(tileFilter, observingCiv))
                    stats[Stat.valueOf(unique.params[1])] += unique.params[0].toFloat()
            }

            for (unique in city.getMatchingUniques(UniqueType.AllStatsPercentFromObject, stateForConditionals)) {
                val tileFilter = unique.params[1]
                if (!matchesTerrainFilter(tileFilter, observingCiv)) continue
                val statPercentage = unique.params[0].toFloat()
                for (stat in Stat.values())
                    stats[stat] += statPercentage
            }

        } else if (observingCiv != null) {
            for (unique in observingCiv.getMatchingUniques(UniqueType.StatPercentFromObject, stateForConditionals)) {
                val tileFilter = unique.params[2]
                if (matchesTerrainFilter(tileFilter, observingCiv))
                    stats[Stat.valueOf(unique.params[1])] += unique.params[0].toFloat()
            }

            for (unique in observingCiv.getMatchingUniques(UniqueType.AllStatsPercentFromObject, stateForConditionals)) {
                val tileFilter = unique.params[1]
                if (!matchesTerrainFilter(tileFilter, observingCiv)) continue
                val statPercentage = unique.params[0].toFloat()
                for (stat in Stat.values())
                    stats[stat] += statPercentage
            }
        }

        return stats
    }

    fun getTileStartScore(): Float {
        var sum = 0f
        for (tile in getTilesInDistance(2)) {
            val tileYield = tile.getTileStartYield(tile == this)
            sum += tileYield
            if (tile in neighbors)
                sum += tileYield
        }

        if (isHill())
            sum -= 2f
        if (isAdjacentToRiver())
            sum += 2f
        if (neighbors.any { it.baseTerrain == Constants.mountain })
            sum += 2f
        if (isCoastalTile())
            sum += 3f
        if (!isCoastalTile() && neighbors.any { it.isCoastalTile() })
            sum -= 7f

        return sum
    }

    private fun getTileStartYield(isCenter: Boolean): Float {
        var stats = getBaseTerrain().cloneStats()

        for (terrainFeatureBase in terrainFeatureObjects) {
            if (terrainFeatureBase.overrideStats)
                stats = terrainFeatureBase.cloneStats()
            else
                stats.add(terrainFeatureBase)
        }
        if (resource != null) stats.add(tileResource)

        if (stats.production < 0) stats.production = 0f
        if (isCenter) {
            if (stats.food < 2) stats.food = 2f
            if (stats.production < 1) stats.production = 1f
        }

        return stats.food + stats.production + stats.gold
    }

    // For dividing the map into Regions to determine start locations
    fun getTileFertility(checkCoasts: Boolean): Int {
        var fertility = 0
        for (terrain in allTerrains) {
            if (terrain.hasUnique(UniqueType.OverrideFertility))
                return terrain.getMatchingUniques(UniqueType.OverrideFertility).first().params[0].toInt()
            else
                fertility += terrain.getMatchingUniques(UniqueType.AddFertility)
                    .sumOf { it.params[0].toInt() }
        }
        if (isAdjacentToRiver()) fertility += 1
        if (isAdjacentTo(Constants.freshWater)) fertility += 1 // meaning total +2 for river
        if (checkCoasts && isCoastalTile()) fertility += 2
        return fertility
    }

    // Also multiplies the stats by the percentage bonus for improvements (but not for tiles)
    fun getImprovementStats(
        improvement: TileImprovement,
        observingCiv: CivilizationInfo,
        city: CityInfo?,
        cityUniqueCache:LocalUniqueCache = LocalUniqueCache(false)
    ): Stats {
        val stats = improvement.cloneStats()
        if (hasViewableResource(observingCiv) && tileResource.isImprovedBy(improvement.name)
            && tileResource.improvementStats != null
        )
            stats.add(tileResource.improvementStats!!.clone()) // resource-specific improvement

        val conditionalState = StateForConditionals(civInfo = observingCiv, cityInfo = city, tile = this)
        for (unique in improvement.getMatchingUniques(UniqueType.Stats, conditionalState)) {
            stats.add(unique.stats)
        }

        for (unique in improvement.getMatchingUniques(UniqueType.ImprovementStatsForAdjacencies, conditionalState)) {
            val adjacent = unique.params[1]
            val numberOfBonuses = neighbors.count {
                it.matchesFilter(adjacent, observingCiv)
                    || it.roadStatus.name == adjacent
            }
            stats.add(unique.stats.times(numberOfBonuses.toFloat()))
        }

        if (city != null) stats.add(getImprovementStatsForCity(improvement, city, conditionalState, cityUniqueCache))

        for ((stat, value) in getImprovementPercentageStats(improvement, observingCiv, city, cityUniqueCache)) {
            stats[stat] *= value.toPercent()
        }

        return stats
    }

    fun getImprovementStatsForCity(
        improvement: TileImprovement,
        city: CityInfo,
        conditionalState: StateForConditionals,
        cityUniqueCache: LocalUniqueCache
    ):Stats{
        val stats = Stats()

        fun statsFromTiles(){
            // Since the conditionalState contains the current tile, it is different for each tile,
            //  therefore if we want the cache to be useful it needs to hold the pre-filtered uniques,
            //  and then for each improvement we'll filter the uniques locally.
            //  This is still a MASSIVE save of RAM!
            val tileUniques = cityUniqueCache.get(UniqueType.StatsFromTiles.name,
                city.getMatchingUniques(UniqueType.StatsFromTiles, StateForConditionals.IgnoreConditionals)
                    .filter { city.matchesFilter(it.params[2]) }) // These are the uniques for all improvements for this city,
                .filter { it.conditionalsApply(conditionalState) } // ...and this is those with applicable conditions
            val improvementUniques =
                    improvement.getMatchingUniques(UniqueType.ImprovementStatsOnTile, conditionalState)

            for (unique in tileUniques + improvementUniques) {
                if (improvement.matchesFilter(unique.params[1])
                        || unique.params[1] == Constants.freshWater && isAdjacentTo(Constants.freshWater)
                        || unique.params[1] == "non-fresh water" && !isAdjacentTo(Constants.freshWater)
                )
                    stats.add(unique.stats)
            }
        }
        statsFromTiles()

        fun statsFromObject() {
            // Same as above - cache holds unfiltered uniques for the city, while we use only the filtered ones
            val uniques = cityUniqueCache.get(UniqueType.StatsFromObject.name,
                city.getMatchingUniques(UniqueType.StatsFromObject, StateForConditionals.IgnoreConditionals))
                .filter { it.conditionalsApply(conditionalState) }
            for (unique in uniques) {
                if (improvement.matchesFilter(unique.params[1])) {
                    stats.add(unique.stats)
                }
            }
        }
        statsFromObject()
        return stats
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getImprovementPercentageStats(
        improvement: TileImprovement,
        observingCiv: CivilizationInfo,
        city: CityInfo?,
        cityUniqueCache: LocalUniqueCache
    ): Stats {
        val stats = Stats()
        val conditionalState = StateForConditionals(civInfo = observingCiv, cityInfo = city, tile = this)

        // I would love to make an interface 'canCallMatchingUniques'
        // from which both cityInfo and CivilizationInfo derive, so I don't have to duplicate all this code
        // But something something too much for this PR.

        if (city != null) {
            // As above, since the conditional is tile-dependant,
            //  we save uniques in the cache without conditional filtering, and use only filtered ones
            val allStatPercentUniques = cityUniqueCache.get(UniqueType.AllStatsPercentFromObject.name,
                city.getMatchingUniques(UniqueType.AllStatsPercentFromObject, StateForConditionals.IgnoreConditionals))
                    .filter { it.conditionalsApply(conditionalState) }
            for (unique in allStatPercentUniques) {
                if (!improvement.matchesFilter(unique.params[1])) continue
                for (stat in Stat.values()) {
                    stats[stat] += unique.params[0].toFloat()
                }
            }

            // Same trick different unique - not sure if worth generalizing this 'late apply' of conditions?
            val statPercentUniques = cityUniqueCache.get(UniqueType.StatPercentFromObject.name,
                city.getMatchingUniques(UniqueType.StatPercentFromObject, StateForConditionals.IgnoreConditionals))
                    .filter { it.conditionalsApply(conditionalState) }

            for (unique in statPercentUniques) {
                if (!improvement.matchesFilter(unique.params[2])) continue
                val stat = Stat.valueOf(unique.params[1])
                stats[stat] += unique.params[0].toFloat()
            }

        } else {
            for (unique in observingCiv.getMatchingUniques(UniqueType.AllStatsPercentFromObject, conditionalState)) {
                if (!improvement.matchesFilter(unique.params[1])) continue
                for (stat in Stat.values()) {
                    stats[stat] += unique.params[0].toFloat()
                }
            }
            for (unique in observingCiv.getMatchingUniques(UniqueType.StatPercentFromObject, conditionalState)) {
                if (!improvement.matchesFilter(unique.params[2])) continue
                val stat = Stat.valueOf(unique.params[1])
                stats[stat] += unique.params[0].toFloat()
            }
        }

        return stats
    }

    // This should be the only adjacency function
    fun isAdjacentTo(terrainFilter:String): Boolean {
        // Rivers are odd, as they aren't technically part of any specific tile but still count towards adjacency
        if (terrainFilter == "River") return isAdjacentToRiver()
        if (terrainFilter == Constants.freshWater && isAdjacentToRiver()) return true
        return (neighbors + this).any { neighbor -> neighbor.matchesFilter(terrainFilter) }
    }

    /** Returns true if the [improvement] can be built on this [TileInfo] */
    fun canBuildImprovement(improvement: TileImprovement, civInfo: CivilizationInfo): Boolean = getImprovementBuildingProblems(improvement, civInfo).none()

    enum class ImprovementBuildingProblem {
        WrongCiv, MissingTech, Unbuildable, NotJustOutsideBorders, OutsideBorders, UnmetConditional, Obsolete, MissingResources, Other
    }

    /** Generates a sequence of reasons that prevent building given [improvement].
     *  If the sequence is empty, improvement can be built immediately.
     */
    fun getImprovementBuildingProblems(improvement: TileImprovement, civInfo: CivilizationInfo): Sequence<ImprovementBuildingProblem> = sequence {
        val stateForConditionals = StateForConditionals(civInfo, tile = this@TileInfo)

        if (improvement.uniqueTo != null && improvement.uniqueTo != civInfo.civName)
            yield(ImprovementBuildingProblem.WrongCiv)
        if (improvement.techRequired != null && !civInfo.tech.isResearched(improvement.techRequired!!))
            yield(ImprovementBuildingProblem.MissingTech)
        if (improvement.hasUnique(UniqueType.Unbuildable, stateForConditionals))
            yield(ImprovementBuildingProblem.Unbuildable)

        if (getOwner() != civInfo && !improvement.hasUnique(UniqueType.CanBuildOutsideBorders, stateForConditionals)) {
            if (!improvement.hasUnique(UniqueType.CanBuildJustOutsideBorders, stateForConditionals))
                yield(ImprovementBuildingProblem.OutsideBorders)
            else if (neighbors.none { it.getOwner() == civInfo })
                yield(ImprovementBuildingProblem.NotJustOutsideBorders)
        }

        if (improvement.getMatchingUniques(UniqueType.OnlyAvailableWhen, StateForConditionals.IgnoreConditionals).any {
                !it.conditionalsApply(stateForConditionals)
            })
            yield(ImprovementBuildingProblem.UnmetConditional)

        if (improvement.getMatchingUniques(UniqueType.ObsoleteWith, stateForConditionals).any {
                civInfo.tech.isResearched(it.params[0])
            })
            yield(ImprovementBuildingProblem.Obsolete)

        if (improvement.getMatchingUniques(UniqueType.ConsumesResources, stateForConditionals).any {
                civInfo.getCivResourcesByName()[it.params[1]]!! < it.params[0].toInt()
            })
            yield(ImprovementBuildingProblem.MissingResources)

        val knownFeatureRemovals = ruleset.tileImprovements.values
            .filter { rulesetImprovement ->
                rulesetImprovement.name.startsWith(Constants.remove)
                && RoadStatus.values().none { it.removeAction == rulesetImprovement.name }
                && (rulesetImprovement.techRequired == null || civInfo.tech.isResearched(rulesetImprovement.techRequired!!))
            }

        if (!canImprovementBeBuiltHere(improvement, hasViewableResource(civInfo), knownFeatureRemovals, stateForConditionals))
            // There are way too many conditions in that functions, besides, they are not interesting
            // at least for the current usecases. Improve if really needed.
            yield(ImprovementBuildingProblem.Other)
    }

    /** Without regards to what CivInfo it is, a lot of the checks are just for the improvement on the tile.
     *  Doubles as a check for the map editor.
     */
    private fun canImprovementBeBuiltHere(
        improvement: TileImprovement,
        resourceIsVisible: Boolean = resource != null,
        knownFeatureRemovals: List<TileImprovement>? = null,
        stateForConditionals: StateForConditionals = StateForConditionals(tile=this)
    ): Boolean {

        fun TileImprovement.canBeBuildOnThisUnbuildableTerrain(
            knownFeatureRemovals: List<TileImprovement>? = null,
        ): Boolean {
            val topTerrain = getLastTerrain()
            // We can build if we are specifically allowed to build on this terrain
            if (isAllowedOnFeature(topTerrain.name)) return true

            // Otherwise, we can if this improvement removes the top terrain
            if (!hasUnique(UniqueType.RemovesFeaturesIfBuilt, stateForConditionals)) return false
            val removeAction = ruleset.tileImprovements[Constants.remove + topTerrain.name] ?: return false
            // and we have the tech to remove that top terrain
            if (removeAction.techRequired != null && (knownFeatureRemovals == null || removeAction !in knownFeatureRemovals)) return false
            // and we can build it on the tile without the top terrain
            val clonedTile = this@TileInfo.clone()
            clonedTile.removeTerrainFeature(topTerrain.name)
            return clonedTile.canImprovementBeBuiltHere(improvement, resourceIsVisible, knownFeatureRemovals, stateForConditionals)
        }

        return when {
            improvement.name == this.improvement -> false
            isCityCenter() -> false

            // First we handle a few special improvements

            // Can only cancel if there is actually an improvement being built
            improvement.name == Constants.cancelImprovementOrder -> (this.improvementInProgress != null)
            // Can only remove roads if that road is actually there
            RoadStatus.values().any { it.removeAction == improvement.name } -> roadStatus.removeAction == improvement.name
            // Can only remove features if that feature is actually there
            improvement.name.startsWith(Constants.remove) -> terrainFeatures.any { it == improvement.name.removePrefix(Constants.remove) }
            // Can only build roads if on land and they are better than the current road
            RoadStatus.values().any { it.name == improvement.name } -> !isWater && RoadStatus.valueOf(improvement.name) > roadStatus

            // Then we check if there is any reason to not allow this improvement to be build

            // Can't build if there is already an irremovable improvement here
            this.improvement != null && getTileImprovement()!!.hasUnique(UniqueType.Irremovable, stateForConditionals) -> false

            // Can't build if this terrain is unbuildable, except when we are specifically allowed to
            getLastTerrain().unbuildable && !improvement.canBeBuildOnThisUnbuildableTerrain(knownFeatureRemovals) -> false

            // Can't build if any terrain specifically prevents building this improvement
            getTerrainMatchingUniques(UniqueType.RestrictedBuildableImprovements, stateForConditionals).any {
                unique -> !improvement.matchesFilter(unique.params[0])
            } -> false

            // Can't build if the improvement specifically prevents building on some present feature
            improvement.getMatchingUniques(UniqueType.CannotBuildOnTile, stateForConditionals).any {
                unique -> matchesTerrainFilter(unique.params[0])
            } -> false

            // Can't build if an improvement is only allowed to be built on specific tiles and this is not one of them
            // If multiple uniques of this type exists, we want all to match (e.g. Hill _and_ Forest would be meaningful)
            improvement.getMatchingUniques(UniqueType.CanOnlyBeBuiltOnTile, stateForConditionals).let {
                it.any() && it.any { unique -> !matchesTerrainFilter(unique.params[0]) }
            } -> false

            // Can't build if the improvement requires an adjacent terrain that is not present
            improvement.getMatchingUniques(UniqueType.MustBeNextTo, stateForConditionals).any {
                !isAdjacentTo(it.params[0])
            } -> false

            // Can't build it if it is only allowed to improve resources and it doesn't improve this resource
            improvement.hasUnique(UniqueType.CanOnlyImproveResource, stateForConditionals) && (
                !resourceIsVisible || !tileResource.isImprovedBy(improvement.name)
            ) -> false

            // At this point we know this is a normal improvement and that there is no reason not to allow it to be built.

            // Lastly we check if the improvement may be built on this terrain or resource
            improvement.canBeBuiltOn(getLastTerrain().name) -> true
            isLand && improvement.canBeBuiltOn("Land") -> true
            isWater && improvement.canBeBuiltOn("Water") -> true
            // DO NOT reverse this &&. isAdjacentToFreshwater() is a lazy which calls a function, and reversing it breaks the tests.
            improvement.hasUnique(UniqueType.ImprovementBuildableByFreshWater, stateForConditionals)
                && isAdjacentTo(Constants.freshWater) -> true

            // I don't particularly like this check, but it is required to build mines on non-hill resources
            resourceIsVisible && tileResource.isImprovedBy(improvement.name) -> true
            // DEPRECATED since 4.0.14, REMOVE SOON:
            isLand && improvement.terrainsCanBeBuiltOn.isEmpty() && !improvement.hasUnique(UniqueType.CanOnlyImproveResource) -> true
            // No reason this improvement should be built here, so can't build it
            else -> false
        }
    }

    /** Implements [UniqueParameterType.TileFilter][com.unciv.models.ruleset.unique.UniqueParameterType.TileFilter] */
    fun matchesFilter(filter: String, civInfo: CivilizationInfo? = null): Boolean {
        if (matchesTerrainFilter(filter, civInfo)) return true
        if (improvement != null && ruleset.tileImprovements[improvement]!!.matchesFilter(filter)) return true
        return improvement == null && filter == "unimproved"
    }

    /** Implements [UniqueParameterType.TerrainFilter][com.unciv.models.ruleset.unique.UniqueParameterType.TerrainFilter] */
    fun matchesTerrainFilter(filter: String, observingCiv: CivilizationInfo? = null): Boolean {
        return when (filter) {
            "All" -> true
            baseTerrain -> true
            "Water" -> isWater
            "Land" -> isLand
            Constants.coastal -> isCoastalTile()
            "River" -> isAdjacentToRiver()
            naturalWonder -> true
            "Open terrain" -> !isRoughTerrain()
            "Rough terrain" -> isRoughTerrain()
            "Foreign Land", "Foreign" -> observingCiv != null && !isFriendlyTerritory(observingCiv)
            "Friendly Land", "Friendly" -> observingCiv != null && isFriendlyTerritory(observingCiv)
            "Enemy Land", "Enemy" -> observingCiv != null && isEnemyTerritory(observingCiv)
            resource -> observingCiv != null && hasViewableResource(observingCiv)
            "Water resource" -> isWater && observingCiv != null && hasViewableResource(observingCiv)
            "Natural Wonder" -> naturalWonder != null
            "Featureless" -> terrainFeatures.isEmpty()
            Constants.freshWaterFilter -> isAdjacentTo(Constants.freshWater)
            else -> {
                if (terrainFeatures.contains(filter)) return true
                if (terrainUniqueMap.getUniques(filter).any()) return true

                // Resource type check is last - cannot succeed if no resource here
                if (resource == null) return false

                // Checks 'luxury resource', 'strategic resource' and 'bonus resource' - only those that are visible of course
                // not using hasViewableResource as observingCiv is often not passed in,
                // and we want to be able to at least test for non-strategic in that case.
                val resourceObject = tileResource
                val hasResourceWithFilter =
                        tileResource.name == filter
                                || tileResource.hasUnique(filter)
                                || tileResource.resourceType.name + " resource" == filter
                if (!hasResourceWithFilter) return false

                // Now that we know that this resource matches the filter - can the observer see that there's a resource here?
                if (resourceObject.revealedBy == null) return true  // no need for tech
                if (observingCiv == null) return false  // can't check tech
                return observingCiv.tech.isResearched(resourceObject.revealedBy!!)
            }
        }
    }

    fun hasImprovementInProgress() = improvementInProgress != null && turnsToImprovement > 0

    @delegate:Transient
    private val _isCoastalTile: Boolean by lazy { neighbors.any { it.baseTerrain == Constants.coast } }
    fun isCoastalTile() = _isCoastalTile

    fun hasViewableResource(civInfo: CivilizationInfo): Boolean =
            resource != null && (tileResource.revealedBy == null || civInfo.tech.isResearched(
                tileResource.revealedBy!!))

    fun getViewableTilesList(distance: Int): List<TileInfo> =
            tileMap.getViewableTiles(position, distance)

    fun getTilesInDistance(distance: Int): Sequence<TileInfo> =
            tileMap.getTilesInDistance(position, distance)

    fun getTilesInDistanceRange(range: IntRange): Sequence<TileInfo> =
            tileMap.getTilesInDistanceRange(position, range)

    fun getTilesAtDistance(distance: Int): Sequence<TileInfo> =
            tileMap.getTilesAtDistance(position, distance)

    fun getDefensiveBonus(): Float {
        var bonus = baseTerrainObject.defenceBonus
        if (terrainFeatureObjects.isNotEmpty()) {
            val otherTerrainBonus = terrainFeatureObjects.maxOf { it.defenceBonus }
            if (otherTerrainBonus != 0f) bonus = otherTerrainBonus  // replaces baseTerrainObject
        }
        if (naturalWonder != null) bonus += getNaturalWonder().defenceBonus
        val tileImprovement = getTileImprovement()
        if (tileImprovement != null) {
            for (unique in tileImprovement.getMatchingUniques(UniqueType.DefensiveBonus, StateForConditionals(tile = this)))
                bonus += unique.params[0].toFloat() / 100
        }
        return bonus
    }

    fun aerialDistanceTo(otherTile: TileInfo): Int {
        val xDelta = position.x - otherTile.position.x
        val yDelta = position.y - otherTile.position.y
        val distance = maxOf(abs(xDelta), abs(yDelta), abs(xDelta - yDelta))

        var wrappedDistance = Float.MAX_VALUE
        if (tileMap.mapParameters.worldWrap) {
            val otherTileUnwrappedPos = tileMap.getUnWrappedPosition(otherTile.position)
            val xDeltaWrapped = position.x - otherTileUnwrappedPos.x
            val yDeltaWrapped = position.y - otherTileUnwrappedPos.y
            wrappedDistance = maxOf(abs(xDeltaWrapped), abs(yDeltaWrapped), abs(xDeltaWrapped - yDeltaWrapped))
        }

        return min(distance, wrappedDistance).toInt()
    }

    fun canBeSettled(): Boolean {
        val modConstants = tileMap.gameInfo.ruleSet.modOptions.constants
        if (isWater || isImpassible())
            return false
        if (getTilesInDistance(modConstants.minimalCityDistanceOnDifferentContinents)
                .any { it.isCityCenter() && it.getContinent() != getContinent() }
            || getTilesInDistance(modConstants.minimalCityDistance)
                .any { it.isCityCenter() && it.getContinent() == getContinent() }
        ) {
            return false
        }
        return true
    }

    /** Shows important properties of this tile for debugging _only_, it helps to see what you're doing */
    override fun toString(): String {
        val lineList = arrayListOf("TileInfo @$position")
        if (!this::baseTerrain.isInitialized) return lineList[0] + ", uninitialized"
        if (isCityCenter()) lineList += getCity()!!.name
        lineList += baseTerrain
        for (terrainFeature in terrainFeatures) lineList += terrainFeature
        if (resource != null) {
            lineList += if (tileResource.resourceType == ResourceType.Strategic)
                    "{$resourceAmount} {$resource}"
                else
                    resource!!
        }
        if (naturalWonder != null) lineList += naturalWonder!!
        if (roadStatus !== RoadStatus.None && !isCityCenter()) lineList += roadStatus.name
        if (improvement != null) lineList += improvement!!
        if (civilianUnit != null) lineList += civilianUnit!!.name + " - " + civilianUnit!!.civInfo.civName
        if (militaryUnit != null) lineList += militaryUnit!!.name + " - " + militaryUnit!!.civInfo.civName
        if (this::baseTerrainObject.isInitialized && isImpassible()) lineList += Constants.impassable
        return lineList.joinToString()
    }

    /** The two tiles have a river between them */
    fun isConnectedByRiver(otherTile: TileInfo): Boolean {
        if (otherTile == this) throw Exception("Should not be called to compare to self!")

        return when (tileMap.getNeighborTileClockPosition(this, otherTile)) {
            2 -> otherTile.hasBottomLeftRiver // we're to the bottom-left of it
            4 -> hasBottomRightRiver // we're to the top-left of it
            6 -> hasBottomRiver // we're directly above it
            8 -> hasBottomLeftRiver // we're to the top-right of it
            10 -> otherTile.hasBottomRightRiver // we're to the bottom-right of it
            12 -> otherTile.hasBottomRiver // we're directly below it
            else -> throw Exception("Should never call this function on a non-neighbor!")
        }
    }

    @delegate:Transient
    private val isAdjacentToRiverLazy by lazy { neighbors.any { isConnectedByRiver(it) } }
    fun isAdjacentToRiver() = isAdjacentToRiverLazy

    /**
     * @returns whether units of [civInfo] can pass through this tile, considering only civ-wide filters.
     * Use [UnitMovementAlgorithms.canPassThrough] to check whether a specific unit can pass through a tile.
     */
    fun canCivPassThrough(civInfo: CivilizationInfo): Boolean {
        val tileOwner = getOwner()
        // comparing the CivInfo objects is cheaper than comparing strings
        if (tileOwner == null || tileOwner == civInfo) return true
        if (isCityCenter() && civInfo.isAtWarWith(tileOwner)
                && !getCity()!!.hasJustBeenConquered) return false
        if (!civInfo.canPassThroughTiles(tileOwner)) return false
        return true
    }

    /** Get info on a selected tile, used on WorldScreen (right side above minimap), CityScreen or MapEditorViewTab. */
    fun toMarkup(viewingCiv: CivilizationInfo?): ArrayList<FormattedLine> {
        val lineList = ArrayList<FormattedLine>()
        val isViewableToPlayer = viewingCiv == null || UncivGame.Current.viewEntireMapForDebug
                || viewingCiv.viewableTiles.contains(this)

        if (isCityCenter()) {
            val city = getCity()!!
            var cityString = city.name.tr()
            if (isViewableToPlayer) cityString += " (${city.health})"
            lineList += FormattedLine(cityString)
            if (UncivGame.Current.viewEntireMapForDebug || city.civInfo == viewingCiv)
                lineList += city.cityConstructions.getProductionMarkup(ruleset)
        }

        lineList += FormattedLine(baseTerrain, link="Terrain/$baseTerrain")
        for (terrainFeature in terrainFeatures)
            lineList += FormattedLine(terrainFeature, link="Terrain/$terrainFeature")
        if (resource != null && (viewingCiv == null || hasViewableResource(viewingCiv)))
            lineList += if (tileResource.resourceType == ResourceType.Strategic)
                    FormattedLine("{$resource} ($resourceAmount)", link="Resource/$resource")
                else
                    FormattedLine(resource!!, link="Resource/$resource")
        if (resource != null && viewingCiv != null && hasViewableResource(viewingCiv)) {
            val resourceImprovement = tileResource.getImprovements().firstOrNull { canBuildImprovement(ruleset.tileImprovements[it]!!, viewingCiv) }
            val tileImprovement = ruleset.tileImprovements[resourceImprovement]
            if (tileImprovement?.techRequired != null
                && !viewingCiv.tech.isResearched(tileImprovement.techRequired!!)) {
                lineList += FormattedLine(
                    "Requires [${tileImprovement.techRequired}]",
                    link="Technology/${tileImprovement.techRequired}",
                    color= "#FAA"
                )
            }
        }
        if (naturalWonder != null)
            lineList += FormattedLine(naturalWonder!!, link="Terrain/$naturalWonder")
        if (roadStatus !== RoadStatus.None && !isCityCenter())
            lineList += FormattedLine(roadStatus.name, link="Improvement/${roadStatus.name}")
        val shownImprovement = getShownImprovement(viewingCiv)
        if (shownImprovement != null)
            lineList += FormattedLine(shownImprovement, link="Improvement/$shownImprovement")

        if (improvementInProgress != null && isViewableToPlayer) {
            // Negative turnsToImprovement is used for UniqueType.CreatesOneImprovement
            val line = "{$improvementInProgress}" +
                if (turnsToImprovement > 0) " - $turnsToImprovement${Fonts.turn}" else " ({Under construction})"
            lineList += FormattedLine(line, link="Improvement/$improvementInProgress")
        }

        if (civilianUnit != null && isViewableToPlayer)
            lineList += FormattedLine(civilianUnit!!.name.tr() + " - " + civilianUnit!!.civInfo.civName.tr(),
                link="Unit/${civilianUnit!!.name}")
        if (militaryUnit != null && isViewableToPlayer) {
            val milUnitString = militaryUnit!!.name.tr() +
                (if (militaryUnit!!.health < 100) "(" + militaryUnit!!.health + ")" else "") +
                " - " + militaryUnit!!.civInfo.civName.tr()
            lineList += FormattedLine(milUnitString, link="Unit/${militaryUnit!!.name}")
        }

        val defenceBonus = getDefensiveBonus()
        if (defenceBonus != 0f) {
            var defencePercentString = (defenceBonus * 100).toInt().toString() + "%"
            if (!defencePercentString.startsWith("-")) defencePercentString = "+$defencePercentString"
            lineList += FormattedLine("[$defencePercentString] to unit defence")
        }
        if (isImpassible()) lineList += FormattedLine(Constants.impassable)
        if (isLand && isAdjacentTo(Constants.freshWater)) lineList += FormattedLine(Constants.freshWater)

        return lineList
    }

    fun hasEnemyInvisibleUnit(viewingCiv: CivilizationInfo): Boolean {
        val unitsInTile = getUnits()
        if (unitsInTile.none()) return false
        if (unitsInTile.first().civInfo != viewingCiv &&
                unitsInTile.firstOrNull { it.isInvisible(viewingCiv) } != null) {
            return true
        }
        return false
    }

    fun hasConnection(civInfo: CivilizationInfo) =
            roadStatus != RoadStatus.None || forestOrJungleAreRoads(civInfo)


    private fun forestOrJungleAreRoads(civInfo: CivilizationInfo) =
            civInfo.nation.forestsAndJunglesAreRoads
                    && (terrainFeatures.contains(Constants.jungle) || terrainFeatures.contains(Constants.forest))
                    && isFriendlyTerritory(civInfo)

    fun getRulesetIncompatibility(ruleset: Ruleset): HashSet<String> {
        val out = HashSet<String>()
        if (!ruleset.terrains.containsKey(baseTerrain))
            out.add("Base terrain [$baseTerrain] does not exist in ruleset!")
        for (terrainFeature in terrainFeatures.filter { !ruleset.terrains.containsKey(it) })
            out.add("Terrain feature [$terrainFeature] does not exist in ruleset!")
        if (resource != null && !ruleset.tileResources.containsKey(resource))
            out.add("Resource [$resource] does not exist in ruleset!")
        if (improvement != null && !ruleset.tileImprovements.containsKey(improvement))
            out.add("Improvement [$improvement] does not exist in ruleset!")
        if (naturalWonder != null && !ruleset.terrains.containsKey(naturalWonder))
            out.add("Natural Wonder [$naturalWonder] does not exist in ruleset!")
        return out
    }

    fun getContinent() = continent

    /** Checks if this tile is marked as target tile for a building with a [UniqueType.CreatesOneImprovement] unique */
    fun isMarkedForCreatesOneImprovement() =
        turnsToImprovement < 0 && improvementInProgress != null
    /** Checks if this tile is marked as target tile for a building with a [UniqueType.CreatesOneImprovement] unique creating a specific [improvement] */
    fun isMarkedForCreatesOneImprovement(improvement: String) =
        turnsToImprovement < 0 && improvementInProgress == improvement

    //endregion
    //region state-changing functions

    fun setTransients() {
        setTerrainTransients()
        setUnitTransients(true)
    }

    fun setTerrainTransients() {
        // Uninitialized tilemap - when you're displaying a tile in the civilopedia or map editor
        if (::tileMap.isInitialized) convertHillToTerrainFeature()
        if (!ruleset.terrains.containsKey(baseTerrain))
            throw Exception("Terrain $baseTerrain does not exist in ruleset!")
        baseTerrainObject = ruleset.terrains[baseTerrain]!!
        setTerrainFeatures(terrainFeatures)
        isWater = getBaseTerrain().type == TerrainType.Water
        isLand = getBaseTerrain().type == TerrainType.Land
        isOcean = baseTerrain == Constants.ocean

        // Resource amounts missing - Old save or bad mapgen?
        if (::tileMap.isInitialized && resource != null && tileResource.resourceType == ResourceType.Strategic && resourceAmount == 0) {
            // Let's assume it's a small deposit
            setTileResource(tileResource, majorDeposit = false)
        }
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

    /**
     * Sets this tile's [resource] and, if [newResource] is a Strategic resource, [resourceAmount] fields.
     *
     * [resourceAmount] is determined by [MapParameters.mapResources] and [majorDeposit], and
     * if the latter is `null` a random choice between major and minor deposit is made, approximating
     * the frequency [MapRegions][com.unciv.logic.map.mapgenerator.MapRegions] would use.
     * A randomness source ([rng]) can optionally be provided for that step (not used otherwise).
     */
    fun setTileResource(newResource: TileResource, majorDeposit: Boolean? = null, rng: Random = Random.Default) {
        resource = newResource.name

        if (newResource.resourceType != ResourceType.Strategic) return

        for (unique in newResource.getMatchingUniques(UniqueType.ResourceAmountOnTiles, StateForConditionals(tile = this))) {
            if (matchesTerrainFilter(unique.params[0])) {
                resourceAmount = unique.params[1].toInt()
                return
            }
        }

        val majorDepositFinal = majorDeposit ?: (rng.nextDouble() < approximateMajorDepositDistribution())
        val depositAmounts = if (majorDepositFinal) newResource.majorDepositAmount else newResource.minorDepositAmount
        resourceAmount = when (tileMap.mapParameters.mapResources) {
            MapResources.sparse -> depositAmounts.sparse
            MapResources.abundant -> depositAmounts.abundant
            else -> depositAmounts.default
        }
    }

    private fun approximateMajorDepositDistribution(): Double {
        // We can't replicate the MapRegions resource distributor, so let's try to get
        // a close probability of major deposits per tile
        var probability = 0.0
        for (unique in allTerrains.flatMap { it.getMatchingUniques(UniqueType.MajorStrategicFrequency) }) {
            val frequency = unique.params[0].toIntOrNull() ?: continue
            if (frequency <= 0) continue
            // The unique param is literally "every N tiles", so to get a probability p=1/f
            probability += 1.0 / frequency
        }
        return if (probability == 0.0) 0.04  // This is the default of 1 per 25 tiles
            else probability
    }

    fun setTerrainFeatures(terrainFeatureList:List<String>) {
        terrainFeatures = terrainFeatureList
        terrainFeatureObjects = terrainFeatureList.mapNotNull { ruleset.terrains[it] }
        allTerrains = sequence {
            yield(baseTerrainObject) // There is an assumption here that base terrains do not change
            if (naturalWonder != null) yield(getNaturalWonder())
            yieldAll(terrainFeatureObjects)
        }.toList().asSequence() //Save in memory, and return as sequence

        val newUniqueMap = UniqueMap()
        for (terrain in allTerrains)
            newUniqueMap.addUniques(terrain.uniqueObjects)
        terrainUniqueMap = newUniqueMap
    }

    fun addTerrainFeature(terrainFeature:String) =
        setTerrainFeatures(ArrayList(terrainFeatures).apply { add(terrainFeature) })

    fun removeTerrainFeature(terrainFeature: String) =
        setTerrainFeatures(ArrayList(terrainFeatures).apply { remove(terrainFeature) })

    fun removeTerrainFeatures() =
        setTerrainFeatures(listOf())


    /** If the unit isn't in the ruleset we can't even know what type of unit this is! So check each place
     * This works with no transients so can be called from gameInfo.setTransients with no fear
     */
    fun removeUnit(mapUnit: MapUnit) {
        when {
            airUnits.contains(mapUnit) -> airUnits.remove(mapUnit)
            civilianUnit == mapUnit -> civilianUnit = null
            militaryUnit == mapUnit -> militaryUnit = null
        }
    }

    fun startWorkingOnImprovement(improvement: TileImprovement, civInfo: CivilizationInfo, unit: MapUnit) {
        improvementInProgress = improvement.name
        turnsToImprovement = if (civInfo.gameInfo.gameParameters.godMode) 1
            else improvement.getTurnsToBuild(civInfo, unit)
    }

    /** Clears [improvementInProgress] and [turnsToImprovement] */
    fun stopWorkingOnImprovement() {
        improvementInProgress = null
        turnsToImprovement = 0
    }

    /** Sets tile improvement to pillaged (without prior checks for validity)
     *  and ensures that matching [UniqueType.CreatesOneImprovement] queued buildings are removed. */
    fun setPillaged() {
        // http://well-of-souls.com/civ/civ5_improvements.html says that naval improvements are destroyed upon pillage
        //    and I can't find any other sources so I'll go with that
        if (isLand) {
            // Setting turnsToImprovement might interfere with UniqueType.CreatesOneImprovement
            removeCreatesOneImprovementMarker()
            improvementInProgress = improvement
            turnsToImprovement = 2
        }
        improvement = null
    }

    /** Marks tile as target tile for a building with a [UniqueType.CreatesOneImprovement] unique */
    fun markForCreatesOneImprovement(improvement: String) {
        improvementInProgress = improvement
        turnsToImprovement = -1
    }
    /** Un-Marks a tile as target tile for a building with a [UniqueType.CreatesOneImprovement] unique,
     *  and ensures that matching queued buildings are removed. */
    fun removeCreatesOneImprovementMarker() {
        if (!isMarkedForCreatesOneImprovement()) return
        owningCity?.cityConstructions?.removeCreateOneImprovementConstruction(improvementInProgress!!)
        stopWorkingOnImprovement()
    }

    fun normalizeToRuleset(ruleset: Ruleset) {
        if (naturalWonder != null && !ruleset.terrains.containsKey(naturalWonder))
            naturalWonder = null
        if (naturalWonder != null) {
            baseTerrain = this.getNaturalWonder().turnsInto!!
            setTerrainFeatures(listOf())
            resource = null
            improvement = null
        }

        if (!ruleset.terrains.containsKey(baseTerrain))
            baseTerrain = ruleset.terrains.values.first { it.type == TerrainType.Land && !it.impassable }.name

        val newFeatures = ArrayList<String>()
        for (terrainFeature in terrainFeatures) {
            val terrainFeatureObject = ruleset.terrains[terrainFeature]
                ?: continue
            if (terrainFeatureObject.occursOn.isNotEmpty() && !terrainFeatureObject.occursOn.contains(baseTerrain))
                continue
            newFeatures.add(terrainFeature)
        }
        if (newFeatures.size != terrainFeatures.size)
            setTerrainFeatures(newFeatures)

        if (resource != null && !ruleset.tileResources.containsKey(resource)) resource = null
        if (resource != null) {
            val resourceObject = ruleset.tileResources[resource]!!
            if (resourceObject.terrainsCanBeFoundOn.none { it == baseTerrain || terrainFeatures.contains(it) })
                resource = null
        }

        // If we're checking this at gameInfo.setTransients, we can't check the top terrain
        if (improvement != null && ::baseTerrainObject.isInitialized) normalizeTileImprovement(ruleset)
        if (isWater || isImpassible())
            roadStatus = RoadStatus.None
    }

    private fun normalizeTileImprovement(ruleset: Ruleset) {
        val improvementObject = ruleset.tileImprovements[improvement]
        if (improvementObject == null) {
            improvement = null
            return
        }
        improvement = null // Unset, and check if it can be reset. If so, do it, if not, invalid.
        if (canImprovementBeBuiltHere(improvementObject, stateForConditionals = StateForConditionals.IgnoreConditionals))
            improvement = improvementObject.name
    }

    private fun convertHillToTerrainFeature() {
        if (baseTerrain == Constants.hill &&
                ruleset.terrains[Constants.hill]?.type == TerrainType.TerrainFeature) {
            val mostCommonBaseTerrain = neighbors.filter { it.isLand && !it.isImpassible() }
                    .groupBy { it.baseTerrain }.maxByOrNull { it.value.size }
            baseTerrain = mostCommonBaseTerrain?.key ?: Constants.grassland
            //We have to add hill as first terrain feature
            val copy = terrainFeatures.toTypedArray()
            val newTerrainFeatures = ArrayList<String>()
            newTerrainFeatures.add(Constants.hill)
            newTerrainFeatures.addAll(copy)
            // We set this directly since this is BEFORE the initial setTerrainFeatures
            terrainFeatures = newTerrainFeatures
        }
    }

    /**
     * Assign a continent ID to this tile.
     *
     * Should only be set once at map generation.
     * @param continent Numeric ID >= 0
     * @throws Exception when tile already has a continent ID
     */
    fun setContinent(continent: Int) {
        if (this.continent != -1)
            throw Exception("Continent already assigned @ $position")
        this.continent = continent
    }

    /** Clear continent ID, for map editor */
    fun clearContinent() { continent = -1 }

    //endregion
}
