package com.unciv.logic.map.mapgenerator

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.*
import com.unciv.logic.map.mapgenerator.mapregions.MapRegions
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Counter
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps
import com.unciv.logic.map.tile.TileNormalizer
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.utils.debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.ulp
import kotlin.sequences.filter


/** Map generator, used by new game, map editor and main menu background
 *
 *  Class instance only keeps [ruleset] and [coroutineScope] for easier access, input and output are through methods, namely [generateMap] and [generateSingleStep].
 *
 *  @param ruleset The Ruleset supplying terrain and resource definitions
 *  @param coroutineScope Enables early abort if this returns `isActive == false`
 */
class MapGenerator(val ruleset: Ruleset, private val coroutineScope: CoroutineScope? = null) {

    companion object {
        private const val consoleTimings = false
    }

    private var randomness = MapGenerationRandomness()
    private val terrainConditions = ruleset.terrains.values.asSequence()
        .filter { !it.hasUnique(UniqueType.NoNaturalGeneration)}
        .flatMap { it.getGenerationConditions() }
        .toList()
        .sortedBy { it.isConstrained }
    private val baseTerrainPicker = terrainConditions
        .filter{ it.terrain.type == TerrainType.Land || it.terrain.type == TerrainType.Water }
    private val terrainFeaturePicker = terrainConditions
        .filter{ it.terrain.type == TerrainType.TerrainFeature }

    /** Associates [terrain] with a range of temperatures and a range of humidities (both open to closed) */
    internal class TerrainOccursRange(
        val terrain: Terrain,
        val tempFrom: Float, val tempTo: Float,
        val humidFrom: Float, val humidTo: Float,
        val isConstrained: Boolean
    ) {
        val name get() = terrain.name
        val occursInChains: Boolean = terrain.hasUnique(UniqueType.OccursInChains)
        val occursInGroups: Boolean = terrain.hasUnique(UniqueType.OccursInGroups)
        val hasVegitation: Boolean = terrain.hasUnique(UniqueType.Vegetation)
        val freshWater: Boolean = terrain.hasUnique(UniqueType.FreshWater)
        val rareFeature: Boolean = terrain.hasUnique(UniqueType.RareFeature)
        
        /** builds a [TerrainOccursRange] for [terrain] from a [unique] (type [UniqueType.TileGenerationConditions]) */
        constructor(terrain: Terrain, unique: Unique)
                : this(terrain,
            unique.params[0].toFloatMakeInclusive(-1f), unique.params[1].toFloat(),
            unique.params[2].toFloatMakeInclusive(0f), unique.params[3].toFloat(),
            isConstrained = true)
        
        /** builds a [TerrainOccursRange] for [terrain] without a [unique] (type [UniqueType.TileGenerationConditions]) */
        constructor(terrain: Terrain)
            : this(terrain, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, isConstrained = false)
        
        
        private fun matchesBaseTerrain(tile: Tile): Boolean {
            return if (terrain.type == TerrainType.Water) 
                    tile.getBaseTerrain().type == TerrainType.Water
                        && tile.getBaseTerrain().hasUnique(UniqueType.FreshWater) == freshWater
                else if (terrain.type == TerrainType.Land)
                    tile.getBaseTerrain().type == TerrainType.Land
                        && tile.getBaseTerrain().hasUnique(UniqueType.OccursInChains) == occursInChains
                else
                    terrain.occursOn.contains(tile.lastTerrain.name)
        }
        
        /** Checks if both [temperature] and [humidity] satisfy their ranges (>From, <=To)
         *  Note the lowest allowed limit has been made inclusive (temp -1 nudged down by 1 [Float.ulp], humidity at 0)
         */
        // Yes this does implicit conversions Float/Double
        fun matches(tile: Tile) = matches(tile, tile.temperature ?: 0.0)
        
        fun matches(tile: Tile, overrideTileTemp: Double) =
            tempFrom < overrideTileTemp && overrideTileTemp <= tempTo &&
                humidFrom < (tile.humidity ?: 0.0) && (tile.humidity?:0.0) <= humidTo &&
                matchesBaseTerrain(tile)

        fun matchesIce() = terrain.type == TerrainType.TerrainFeature && terrain.impassable && terrain.occursOn.contains(Constants.ocean) && !rareFeature

        override fun toString(): String {
            return name
        }

        companion object {
            /** A [toFloat] that also nudges the value slightly down if it matches [limit] to make the resulting range inclusive on the lower end */
            private fun String.toFloatMakeInclusive(limit: Float): Float {
                val result = toFloat()
                if (result != limit) return result
                return result - result.ulp
            }
        }
    }
    private fun Terrain.getGenerationConditions() =
        getMatchingUniques(UniqueType.TileGenerationConditions)
            .map { unique -> TerrainOccursRange(this, unique) }
            .ifEmpty { sequenceOf(TerrainOccursRange(this)) }

    fun generateMap(mapParameters: MapParameters, gameParameters: GameParameters = GameParameters(), civilizations: List<Civilization> = emptyList()): TileMap {
        val mapSize = mapParameters.mapSize
        val mapType = mapParameters.type

        if (mapParameters.seed == 0L)
            mapParameters.seed = System.currentTimeMillis()

        randomness.seedRNG(mapParameters.seed)

        val map: TileMap = if (mapParameters.shape == MapShape.rectangular)
            TileMap(mapSize.width, mapSize.height, ruleset, mapParameters.worldWrap)
        else
            TileMap(mapSize.radius, ruleset, mapParameters.worldWrap)

        mapParameters.createdWithVersion = UncivGame.VERSION.toSerializeString()
        map.mapParameters = mapParameters

        if (mapType == MapType.empty) {
            for (tile in map.values) {
                tile.baseTerrain = Constants.ocean
                tile.setTerrainTransients()
            }

            return map
        }

        if (consoleTimings) debug("\nMapGenerator run with parameters %s", mapParameters)
        runAndMeasure("MapLandmassGenerator") {
            MapLandmassGenerator(map, ruleset, randomness).generateLand()
        }
        runAndMeasure("applyHumidityAndTemperature") {
            applyHumidityAndTemperature(map)
        }
        runAndMeasure("raiseMountainsAndHills") {
            MapElevationGenerator(map, ruleset, terrainConditions, randomness).raiseMountainsAndHills()
        }
        runAndMeasure("spawnLakesAndCoasts") {
            spawnLakesAndCoasts(map)
        }
        runAndMeasure("spawnVegetation") {
            spawnVegetation(map)
        }
        runAndMeasure("spawnRareFeatures") {
            spawnRareFeatures(map)
        }
        runAndMeasure("spawnIce") {
            spawnIce(map)
        }
        runAndMeasure("assignContinents") {
            map.assignContinents(TileMap.AssignContinentsMode.Assign)
        }
        runAndMeasure("RiverGenerator") {
            RiverGenerator(map, randomness, ruleset).spawnRivers()
        }
        convertTerrains(map.values)

        // Region based map generation - not used when generating maps in map editor
        if (civilizations.isNotEmpty()) {
            val regions = MapRegions(ruleset)
            runAndMeasure("generateRegions") {
                regions.generateRegions(map, civilizations.count { ruleset.nations[it.civName]!!.isMajorCiv })
            }
            runAndMeasure("assignRegions") {
                regions.assignRegions(map, civilizations.filter { ruleset.nations[it.civName]!!.isMajorCiv }, gameParameters)
            }
            // Natural wonders need to go before most resources since there is a minimum distance
            runAndMeasure("NaturalWonderGenerator") {
                NaturalWonderGenerator(ruleset, randomness).spawnNaturalWonders(map)
            }
            runAndMeasure("placeResourcesAndMinorCivs") {
                regions.placeResourcesAndMinorCivs(map, civilizations.filter { ruleset.nations[it.civName]!!.isCityState })
            }
        } else {
            runAndMeasure("NaturalWonderGenerator") {
                NaturalWonderGenerator(ruleset, randomness).spawnNaturalWonders(map)
            }
            // Fallback spread resources function - used when generating maps in map editor
            runAndMeasure("spreadResources") { spreadResources(map) }
        }
        runAndMeasure("spreadAncientRuins") { spreadAncientRuins(map) }
        
        mirror(map)

        // Map generation may generate incompatible terrain/feature combinations
        for (tile in map.values)
            TileNormalizer.normalizeToRuleset(tile, ruleset)

        return map
    }
    
    private fun flipTopBottom(vector: Vector2): Vector2 = Vector2(-vector.y, -vector.x)
    private fun flipTopBottom(vector: HexCoord): HexCoord = HexCoord.of(-vector.y, -vector.x)
    private fun flipLeftRight(vector: Vector2): Vector2 = Vector2(vector.y, vector.x)
    private fun flipLeftRight(vector: HexCoord): HexCoord = HexCoord.of(vector.y, vector.x)

    private fun mirror(map: TileMap) {
        fun getMirrorTile(tile: Tile, mirroringType: String): Tile? {
            val mirrorTileVector = when (mirroringType) {
                MirroringType.topbottom -> if (tile.getRow() <= 0) return null else flipTopBottom(tile.position)
                MirroringType.leftright -> if (tile.getColumn() <= 0) return null else flipLeftRight(tile.position)
                MirroringType.aroundCenterTile -> if (tile.getRow() <= 0) return null else flipLeftRight(flipTopBottom(tile.position))
                MirroringType.fourway -> when {
                    tile.getRow() < 0 && tile.getColumn() < 0 -> return null
                    tile.getRow() < 0 && tile.getColumn() >= 0 -> flipLeftRight(tile.position)
                    tile.getRow() >= 0 && tile.getColumn() < 0 -> flipTopBottom(tile.position)
                    else -> flipLeftRight(flipTopBottom(tile.position))
                }

                else -> return null
            }
            return map.getIfTileExistsOrNull(mirrorTileVector.x, mirrorTileVector.y)
        }
        
        fun copyTile(tile: Tile, mirroringType: String) {
            val mirrorTile = getMirrorTile(tile, mirroringType) ?: return
            
            tile.setBaseTerrain(mirrorTile.getBaseTerrain())
            tile.naturalWonder = mirrorTile.naturalWonder
            tile.setTerrainFeatures(mirrorTile.terrainFeatures)
            tile.tileResource = mirrorTile.tileResource
            tile.improvement = mirrorTile.improvement
            
            for (neighbor in tile.neighbors){
                val neighborMirror = getMirrorTile(neighbor, mirroringType) ?: continue
                if (neighborMirror !in mirrorTile.neighbors) continue // we landed on the edge here
                tile.setConnectedByRiver(neighbor, mirrorTile.isConnectedByRiver(neighborMirror))
            }
        }

        if (map.mapParameters.mirroring == MirroringType.none) return
        for (tile in map.values) {
            copyTile(tile, map.mapParameters.mirroring)
        }
    }

    fun generateSingleStep(map: TileMap, step: MapGeneratorSteps) {
        if (map.mapParameters.seed == 0L)
            map.mapParameters.seed = System.currentTimeMillis()

        randomness.seedRNG(map.mapParameters.seed)

        runAndMeasure("SingleStep $step") {
            when (step) {
                MapGeneratorSteps.None -> Unit
                MapGeneratorSteps.All -> throw IllegalArgumentException("MapGeneratorSteps.All cannot be used in generateSingleStep")
                MapGeneratorSteps.Landmass -> MapLandmassGenerator(map, ruleset, randomness).generateLand()
                MapGeneratorSteps.HumidityAndTemperature -> applyHumidityAndTemperature(map)
                MapGeneratorSteps.Elevation -> MapElevationGenerator(map, ruleset, terrainConditions, randomness).raiseMountainsAndHills()
                MapGeneratorSteps.LakesAndCoast -> spawnLakesAndCoasts(map)
                MapGeneratorSteps.Vegetation -> spawnVegetation(map)
                MapGeneratorSteps.RareFeatures -> spawnRareFeatures(map)
                MapGeneratorSteps.Ice -> spawnIce(map)
                MapGeneratorSteps.Continents -> map.assignContinents(TileMap.AssignContinentsMode.Reassign)
                MapGeneratorSteps.NaturalWonders -> NaturalWonderGenerator(ruleset, randomness).spawnNaturalWonders(map)
                MapGeneratorSteps.Rivers -> {
                    val resultingTiles = mutableSetOf<Tile>()
                    RiverGenerator(map, randomness, ruleset).spawnRivers(resultingTiles)
                    convertTerrains(resultingTiles)
                }
                MapGeneratorSteps.Resources -> spreadResources(map)
                MapGeneratorSteps.AncientRuins -> spreadAncientRuins(map)
            }
        }
    }


    private fun runAndMeasure(text: String, action: ()->Unit) {
        if (coroutineScope?.isActive == false) return
        if (!consoleTimings) return action()
        val startNanos = System.nanoTime()
        action()
        val delta = System.nanoTime() - startNanos
        debug("MapGenerator.%s took %s.%sms", text, delta/1000000L, (delta/10000L).rem(100))
    }

    fun convertTerrains(tiles: Iterable<Tile>) = Helpers.convertTerrains(ruleset, tiles)
    object Helpers {
        fun convertTerrains(ruleset: Ruleset, tiles: Iterable<Tile>) {
            for (tile in tiles) {
                val conversionUnique =
                    tile.getBaseTerrain().getMatchingUniques(UniqueType.ChangesTerrain, GameContext(tile = tile))
                        .firstOrNull { tile.isAdjacentTo(it.params[1]) }
                        ?: continue
                val terrain = ruleset.terrains[conversionUnique.params[0]] ?: continue

                if (terrain.type != TerrainType.TerrainFeature)
                    tile.baseTerrain = terrain.name
                else if (!terrain.occursOn.contains(tile.lastTerrain.name)) continue
                else
                    tile.addTerrainFeature(terrain.name)
                tile.setTerrainTransients()
            }
        }
    }

    private fun spreadCoast(map: TileMap) {
        for (i in 1..map.mapParameters.maxCoastExtension) {
            val toCoast = mutableListOf<Tile>()
            for (tile in map.values.filter { it.baseTerrain == Constants.ocean }) {
                val tilesInDistance = tile.getTilesInDistance(1)
                for (neighborTile in tilesInDistance) {
                    if (neighborTile.isLand) {
                        toCoast.add(tile)
                        break
                    } else if (neighborTile.baseTerrain == Constants.coast) {
                        val randbool = randomness.RNG.nextBoolean()
                        if (randbool) {
                            toCoast.add(tile)
                        }
                        break
                    }
                }
            }
            for (tile in toCoast) {
                tile.baseTerrain = Constants.coast
                tile.setTransients()
            }
        }
    }

    private fun spawnLakesAndCoasts(map: TileMap) {
        if (ruleset.terrains.containsKey(Constants.lakes)) {
            val lakeTerrains = terrainConditions.filter { it.freshWater && !it.rareFeature }
            //define lakes
            val waterTiles = map.values.filter { it.isWater }.toMutableList()

            val tilesInArea = ArrayList<Tile>()
            val tilesToCheck = ArrayList<Tile>()

            val maxLakeSize = ruleset.modOptions.constants.maxLakeSize

            while (waterTiles.isNotEmpty()) {
                val initialWaterTile = waterTiles.removeAt(0)
                tilesInArea += initialWaterTile
                tilesToCheck += initialWaterTile

                // Floodfill to cluster water tiles
                while (tilesToCheck.isNotEmpty()) {
                    val tileWeAreChecking = tilesToCheck.removeAt(0)
                    for (vector in tileWeAreChecking.neighbors){
                        if (tilesInArea.contains(vector)) continue
                        if (!waterTiles.contains(vector)) continue
                        tilesInArea += vector
                        tilesToCheck += vector
                        waterTiles -= vector
                    }
                }

                val lakeTerrain = lakeTerrains.filter { it.matches(initialWaterTile) }
                    .ifEmpty {lakeTerrains}
                    .random(randomness.RNG)
                if (tilesInArea.size <= maxLakeSize) {
                    for (tile in tilesInArea) {
                        tile.baseTerrain = lakeTerrain.name
                        tile.setTransients()
                    }
                }
                tilesInArea.clear()
            }
        }

        //Coasts
        if (ruleset.terrains.containsKey(Constants.coast)) {
            spreadCoast(map)
        }
    }

    private fun spreadAncientRuins(map: TileMap) {
        val ruinsEquivalents = ruleset.tileImprovements.filter {
            it.value.isAncientRuinsEquivalent()
        }
        if (map.mapParameters.noRuins || ruinsEquivalents.isEmpty()) return

        fun isPlaceable(improvement: TileImprovement, tile: Tile) =
            tile.improvementFunctions.canImprovementBeBuiltHere(improvement, gameContext = GameContext.IgnoreConditionals)

        val suitableTiles = map.values.filter { it.isLand && !it.isImpassible()
                && ruinsEquivalents.values.any { improvement -> isPlaceable(improvement, it) } }
        val locations = randomness.chooseSpreadOutLocations(
                (suitableTiles.size * ruleset.modOptions.constants.ancientRuinCountMultiplier).roundToInt(),
                suitableTiles,
                map.mapParameters.mapSize.radius)
        for (tile in locations)
            tile.improvement = ruinsEquivalents.values.filter { isPlaceable(it, tile) }.random().name
    }

    private fun spreadResources(tileMap: TileMap) {
        val mapRadius = tileMap.mapParameters.mapSize.radius
        for (tile in tileMap.values)
            tile.tileResource = null

        spreadStrategicResources(tileMap, mapRadius)
        spreadResources(tileMap, mapRadius, ResourceType.Luxury)
        spreadResources(tileMap, mapRadius, ResourceType.Bonus)
    }

    // Here, we need each specific resource to be spread over the map - it matters less if specific resources are near each other
    private fun spreadStrategicResources(tileMap: TileMap, mapRadius: Int) {
        val strategicResources = ruleset.tileResources.values.filter { it.resourceType == ResourceType.Strategic }
        // passable land tiles (no mountains, no wonders) without resources yet
        // can't be next to NW
        val candidateTiles = tileMap.values.filter { it.resource == null && !it.isImpassible()
                && it.neighbors.none { neighbor -> neighbor.isNaturalWonder() }}
        val totalNumberOfResources = candidateTiles.count { it.isLand } * tileMap.mapParameters.resourceRichness
        val resourcesPerType = (totalNumberOfResources/strategicResources.size).toInt()
        for (resource in strategicResources) {
            // remove the tiles where previous resources have been placed
            val suitableTiles = candidateTiles
                    .filterNot { it.baseTerrain == Constants.snow && it.isHill() }
                    .filter { it.resource == null && resource.generatesNaturallyOn(it) }

            val locations = randomness.chooseSpreadOutLocations(resourcesPerType, suitableTiles, mapRadius)

            for (location in locations) location.setTileResource(resource, rng = randomness.RNG)
        }
    }

    /**
     * Spreads resources of type [resourceType] picking locations at a minimum distance from each other,
     * which is determined from [mapRadius] and then tuned down until the desired number fits.
     * [MapParameters.resourceRichness] used to control how many resources to spawn.
     */
    private fun spreadResources(tileMap: TileMap, mapRadius: Int, resourceType: ResourceType) {
        val resourcesOfType = ruleset.tileResources.values.filter { it.resourceType == resourceType }

        val suitableTiles = tileMap.values
                .filterNot { it.baseTerrain == Constants.snow && it.isHill() }
                .filter { it.resource == null
                    && it.neighbors.none { neighbor -> neighbor.isNaturalWonder() }
                    && resourcesOfType.any { r -> r.generatesNaturallyOn(it) }
                }
        val numberOfResources = tileMap.values.count { it.isLand && !it.isImpassible() } *
                tileMap.mapParameters.resourceRichness
        val locations = randomness.chooseSpreadOutLocations(numberOfResources.toInt(), suitableTiles, mapRadius)

        val resourceToNumber = Counter<String>()

        for (tile in locations) {
            val possibleResources = resourcesOfType.filter { it.generatesNaturallyOn(tile) }
            if (possibleResources.isEmpty()) continue
            val resourceWithLeastAssignments = possibleResources.minByOrNull { resourceToNumber[it.name] }!!
            resourceToNumber.add(resourceWithLeastAssignments.name, 1)
            tile.setTileResource(resourceWithLeastAssignments, rng = randomness.RNG)
        }
    }


    /**
     * [MapParameters.tilesPerBiomeArea] to set biomes size
     * [MapParameters.temperatureintensity] to favor very high and very low temperatures
     * [MapParameters.temperatureShift] to shift temperature towards cold (negative) or hot (positive)
     */
    private fun applyHumidityAndTemperature(tileMap: TileMap) {
        val humiditySeed = randomness.RNG.nextInt().toDouble()
        val temperatureSeed = randomness.RNG.nextInt().toDouble()

        tileMap.setTransients(ruleset)

        val scale = tileMap.mapParameters.tilesPerBiomeArea.toDouble()
        val temperatureintensity = tileMap.mapParameters.temperatureintensity
        val temperatureShift = tileMap.mapParameters.temperatureShift
        val humidityShift = if (temperatureShift > 0) -temperatureShift / 2 else 0f

        // List is OK here as it's only sequentially scanned
        val landTerrains = baseTerrainPicker.filter { it.terrain.type == TerrainType.Land && !it.rareFeature }
        val noTerrainUniques = landTerrains.none { it.isConstrained}
        val elevationTerrains = baseTerrainPicker.filter {  it.occursInChains }.mapTo(mutableSetOf()) { it.name }

        for (tile in tileMap.values.asSequence()) {
            if (tile.isWater || tile.baseTerrain in elevationTerrains)
                continue

            val humidityRandom = randomness.getPerlinNoise(tile, humiditySeed, scale = scale, nOctaves = 1)
            val humidity = ((humidityRandom + 1.0) / 2.0 + humidityShift).coerceIn(0.0..1.0)
            tile.humidity = humidity

            val expectedTemperature = if (tileMap.mapParameters.shape == MapShape.flatEarth) {
                // Flat Earth uses radius because North is center of map
                val radius = getTileRadius(tile, tileMap)
                val radiusTemperature = getTemperatureAtRadius(radius)
                radiusTemperature
            } else {
                // Globe Earth uses latitude because North is top of map
                val latitudeTemperature = 1.0 - 2.0 * abs(tile.latitude) / tileMap.maxLatitude
                latitudeTemperature
            }

            val randomTemperature = randomness.getPerlinNoise(tile, temperatureSeed, scale = scale, nOctaves = 1)
            var temperature = (5.0 * expectedTemperature + randomTemperature) / 6.0
            temperature = abs(temperature).pow(1.0 - temperatureintensity) * temperature.sign
            temperature = (temperature + temperatureShift).coerceIn(-1.0..1.0)
            tile.temperature = temperature

            // Old, static map generation rules - necessary for existing base ruleset mods to continue to function
            if (noTerrainUniques) {
                val autoTerrain = when {
                    temperature < -0.4 -> if (humidity < 0.5) Constants.snow else Constants.tundra
                    temperature < 0.8 -> if (humidity < 0.5) Constants.plains else Constants.grassland
                    temperature <= 1.0 -> if (humidity < 0.7) Constants.desert else Constants.plains
                    else -> {
                        debug("applyHumidityAndTemperature: Invalid temperature %s", temperature)
                        continue
                    }
                }
                if (ruleset.terrains.containsKey(autoTerrain)) {
                    tile.baseTerrain = autoTerrain
                    tile.setTerrainTransients()
                }
                continue
            }

            val matchingTerrain = landTerrains.filter{ it.matches(tile) }.randomOrNull(randomness.RNG)

            if (matchingTerrain != null) {
                tile.baseTerrain = matchingTerrain.name
                tile.setTerrainTransients()
            } else {
                debug("applyHumidityAndTemperature: No terrain found for temperature: %s, humidity: %s", temperature, humidity)
            }
        }
    }

    private fun getTileRadius(tile: Tile, tileMap: TileMap): Float {
        // Numbers betwee 0.0-1.0
        val latitudeRatio = abs(tile.latitude) / tileMap.maxLatitude.toFloat()
        val longitudeRatio = abs(tile.longitude) / tileMap.maxLongitude.toFloat()
        return sqrt(latitudeRatio.pow(2) + longitudeRatio.pow(2))
    }

    private fun getTemperatureAtRadius(radius: Float): Double {
        /*
        Radius is in the range of 0.0 to 1.0
        Temperature is in the range of -1.0 to 1.0

        Radius of 0.0 (arctic) is -1.0 (cold)
        Radius of 0.25 (mid North) is 0.0 (temperate)
        Radius of 0.5 (equator) is 1.0 (hot)
        Radius of 0.75 (mid South) is 0.0 (temperate)
        Radius of 1.0 (antarctic) is -1.0 (cold)

        Scale the radius range to the temperature range
        */
        return when {
            /*
            North Zone
            Starts cold at arctic and gets hotter as it goes South to equator
            x1 is set to 0.05 instead of 0.0 to offset the ice in the center of the map
            */
            radius < 0.5 -> scaleToRange(0.05, 0.5, -1.0, 1.0, radius)

            /*
            South Zone
            Starts hot at equator and gets colder as it goes South to antarctic
            x2 is set to 0.95 instead of 1.0 to offset the ice on the edges of the map
            */
            radius > 0.5 -> scaleToRange(0.5, 0.95, 1.0, -1.0, radius)

            /*
            Equator
            Always hot
            radius == 0.5
            */
            else -> 1.0
        }
    }

    /**
     * @x1 start of the original range
     * @x2 end of the original range
     * @y1 start of the new range
     * @y2 end of the new range
     * @value value to be scaled from the original range to the new range
     *
     * @returns value in new scale
     * special thanks to @letstalkaboutdune for the math
     */
    private fun scaleToRange(x1: Double, x2: Double, y1: Double, y2: Double, value: Float): Double {
        val gain = (y2 - y1) / (x2 - x1)
        val offset = y2 - (gain * x2)
        return (gain * value) + offset
    }

    /**
     * [MapParameters.vegetationRichness] is the threshold for vegetation spawn
     */
    private fun spawnVegetation(tileMap: TileMap) {
        val vegetationSeed = randomness.RNG.nextInt().toDouble()
        val vegetationTerrains = terrainFeaturePicker.filter { it.hasVegitation && !it.rareFeature }
            .ifEmpty {terrainFeaturePicker.filter { Constants.vegetation.contains(it.name)}}
        val candidateTerrains = vegetationTerrains.flatMap{ it.terrain.occursOn }
        // Checking it.baseTerrain in candidateTerrains to make sure forest does not spawn on desert hill
        for (tile in tileMap.values.asSequence().filter { it.baseTerrain in candidateTerrains
                && it.lastTerrain.name in candidateTerrains }) {

            val vegetation = (randomness.getPerlinNoise(tile, vegetationSeed, scale = 3.0, nOctaves = 1) + 1.0) / 2.0

            if (vegetation <= tileMap.mapParameters.vegetationRichness) {
                val possibleVegetation = vegetationTerrains.filter { it.matches(tile)
                    && NaturalWonderGenerator.fitsTerrainUniques(it.terrain, tile)
                }
                if (possibleVegetation.isEmpty()) continue
                val randomVegetation = possibleVegetation.random(randomness.RNG)
                tile.hasVegetation = true
                tile.addTerrainFeature(randomVegetation.name)
            }
        }
    }

    /**
     * [MapParameters.rareFeaturesRichness] is the probability of spawning a rare feature
     */
    private fun spawnRareFeatures(tileMap: TileMap) {
        val rareFeatures = terrainFeaturePicker.filter { it.rareFeature }
        for (tile in tileMap.values.asSequence().filter { it.terrainFeatures.isEmpty() }) {
            if (randomness.RNG.nextDouble() <= tileMap.mapParameters.rareFeaturesRichness) {
                val possibleFeatures = rareFeatures.filter { it.matches(tile) 
                    && (!tile.isHill() || it.terrain.occursOn.contains(Constants.hill))
                    && NaturalWonderGenerator.fitsTerrainUniques(it.terrain, tile)
                }
                if (possibleFeatures.any())
                    tile.addTerrainFeature(possibleFeatures.random(randomness.RNG).name)
            }
        }
    }

    /**
     * [MapParameters.temperatureintensity] as in [applyHumidityAndTemperature]
     */
    private fun spawnIce(tileMap: TileMap) {
        val waterTerrain: Set<String> =
            ruleset.terrains.values.asSequence()
            .filter { it.type == TerrainType.Water }
            .map { it.name }.toSet()
        val iceTerrains: List<TerrainOccursRange> = terrainFeaturePicker.filter { it.matchesIce() }

        if (tileMap.mapParameters.shape == MapShape.flatEarth) {
            spawnFlatEarthIceWalls(tileMap, iceTerrains)
        }

        if (iceTerrains.isEmpty()) return

        tileMap.setTransients(ruleset)
        val temperatureSeed = randomness.RNG.nextInt().toDouble()
        for (tile in tileMap.values) {
            if (tile.baseTerrain !in waterTerrain || tile.terrainFeatures.isNotEmpty())
                continue

            val randomTemperature = randomness.getPerlinNoise(tile, temperatureSeed, scale = tileMap.mapParameters.tilesPerBiomeArea.toDouble(), nOctaves = 1)
            val latitudeTemperature = 1.0 - 2.0 * abs(tile.latitude) / tileMap.maxLatitude
            var iceTemperature = ((latitudeTemperature + randomTemperature) / 2.0)
            iceTemperature = abs(iceTemperature).pow(1.0 - tileMap.mapParameters.temperatureintensity) * iceTemperature.sign
            iceTemperature = (iceTemperature + tileMap.mapParameters.temperatureShift).coerceIn(-1.0..1.0)
            // This is quite different from the normal tile temperature. TODO: Unify

            val iceTerrain = iceTerrains
                .filter { it.matches(tile, iceTemperature) 
                    && NaturalWonderGenerator.fitsTerrainUniques(it.terrain, tile)
                }.map { it.terrain.name }
                .randomOrNull(randomness.RNG)
            if (iceTerrain != null)
                tile.addTerrainFeature(iceTerrain)
        }
    }

    private fun spawnFlatEarthIceWalls(tileMap: TileMap, iceTerrains: List<TerrainOccursRange>) {
        val snowTerrains = listOf(baseTerrainPicker.first { it.terrain.type == TerrainType.Land && it.tempFrom <= -1 && it.humidTo >= 1 && !it.rareFeature })
        val mountainTerrains = baseTerrainPicker.filter { it.terrain.impassable && it.occursInChains && !it.rareFeature }
        val allArcticTerrains = iceTerrains + snowTerrains + mountainTerrains

        // Skip the tile loop if nothing can be done in it
        if (allArcticTerrains.isEmpty()) return

        // Flat Earth needs a 1 tile wide perimeter of ice/mountain/snow and a 2 radius cluster of ice in the center.
        for (tile in tileMap.values) {
            val isCenterTile = tile.latitude == 0 && tile.longitude == 0
            val isEdgeTile = tile.neighbors.count() < 6

            // Make center tiles ice or snow or mountain depending on availability
            if (isCenterTile) {
                spawnFlatEarthCenterIceWall(tile, iceTerrains, mountainTerrains)
            }

            // Make edge tiles randomly ice or snow or mountain if available
            if (isEdgeTile) {
                spawnFlatEarthEdgeIceWall(tile, allArcticTerrains, iceTerrains)
            }
        }
    }
    
    private fun spawnBestIce(
        tile: Tile,
        iceTerrains: List<TerrainOccursRange>,
        mountainTerrains: List<TerrainOccursRange>) {
        val ice = iceTerrains.filter { it.matches(tile, -1.0) }.randomOrNull(randomness.RNG)
            ?: iceTerrains.randomOrNull(randomness.RNG)
        if (ice != null) {
            if (!ice.matches(tile, -1.0)) {
                tile.baseTerrain = Constants.ocean             
            }
            tile.removeTerrainFeatures()
            tile.addTerrainFeature(ice.terrain.name)
            tile.setTerrainTransients()
        } else {
            val mountain =
                mountainTerrains.filter { it.matches(tile, -1.0) }.randomOrNull(randomness.RNG)
                    ?: mountainTerrains.random(randomness.RNG)
            tile.baseTerrain = mountain.terrain.name
            tile.removeTerrainFeatures()
        }
        tile.setTerrainTransients()
    }

    private fun spawnFlatEarthCenterIceWall(
        tile: Tile, 
        iceTerrains: List<TerrainOccursRange>, 
        mountainTerrains: List<TerrainOccursRange>) {
        // Spawn ice on center tile
        spawnBestIce(tile, iceTerrains, mountainTerrains)

        // Spawn circle of ice around center tile
        for (neighbor in tile.neighbors) {
            spawnBestIce(neighbor, iceTerrains, mountainTerrains)

            // Spawn partial circle of ice around circle of ice
            for (neighbor2 in neighbor.neighbors) {
                // Do nothing most of the time at random.
                if (randomness.RNG.nextDouble() > 0.75)
                    spawnBestIce(neighbor2, iceTerrains, mountainTerrains)
            }
        }
    }

    private fun spawnRandomIce(tile: Tile, arcticTerrain: TerrainOccursRange, iceTerrains: List<TerrainOccursRange>) {
        tile.removeTerrainFeatures()
        if (arcticTerrain.terrain.type == TerrainType.Land && arcticTerrain.terrain.impassable) {
            tile.baseTerrain = arcticTerrain.terrain.name
        }  else if (arcticTerrain.terrain.type == TerrainType.Land) {
            // legacy code forced ice on snow even though that's impossible
            tile.baseTerrain = arcticTerrain.terrain.name
            tile.addTerrainFeature(iceTerrains.random(randomness.RNG).terrain.name)
        } else if (arcticTerrain.terrain.type == TerrainType.TerrainFeature && arcticTerrain.matches(tile)) {
            tile.addTerrainFeature(arcticTerrain.terrain.name)                
        } else {
            // legacy code forced ice on ocean without checking if possible
            tile.baseTerrain = Constants.ocean
            tile.addTerrainFeature(arcticTerrain.terrain.name)
        }
        tile.setTerrainTransients()
    }

    private fun spawnFlatEarthEdgeIceWall(tile: Tile, arcticTerrains: List<TerrainOccursRange>, iceTerrains: List<TerrainOccursRange>) {
        val arcticTerrain = arcticTerrains.filter { it.matches(tile, -1.0) }.randomOrNull(randomness.RNG)
            ?: arcticTerrains.random(randomness.RNG)
        spawnRandomIce(tile, arcticTerrain, iceTerrains)

        // Spawn partial circle of arctic tiles next to the edge
        for (neighbor in tile.neighbors) {
            val neighborIsEdgeTile = neighbor.neighbors.count() < 6
            // Do not redo edge tile. It is already done.
            // Do nothing most of the time at random.
            if (!neighborIsEdgeTile && randomness.RNG.nextDouble() >= 0.75) {
                spawnRandomIce(tile, arcticTerrain, iceTerrains)
            }
        }
    }
}
