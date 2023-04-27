package com.unciv.logic.map.mapgenerator

import com.unciv.Constants
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps.Elevation
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps.HumidityAndTemperature
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps.LakesAndCoast
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps.Vegetation
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps.RareFeatures
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps.Ice
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps.NaturalWonders
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps.Rivers
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps.Resources
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps.AncientRuins


// todo: unify tile Equivalents with MapGenerator

/**
 * This is a helpful tool to retract some steps in Map Generation
 */
class MapRegression(private val ruleset: Ruleset) {

    companion object {

        fun retractStep(step: MapGeneratorSteps, map: TileMap, ruleset: Ruleset) {
            val regression = MapRegression(ruleset)
            when (step) {
                Elevation -> regression.retractElevation(map)
                HumidityAndTemperature -> regression.retractHumidityAndTemperature(map)
                LakesAndCoast -> regression.retractLakesAndCoast(map)
                Vegetation -> regression.retractVegetation(map)
                RareFeatures -> regression.retractRareFeatures(map)
                Ice -> regression.retractIce(map)
                NaturalWonders -> regression.retractNaturalWonders(map)
                Rivers -> regression.retractRivers(map)
                Resources -> regression.retractResources(map)
                AncientRuins -> regression.retractAncientRuins(map)
                else -> {}
            }
        }
    }

    fun retractElevation(map: TileMap) {
        // mountain
        val mountain = ruleset.terrains.values.filter { it.hasUnique(UniqueType.OccursInChains) }
        map.values
            .filterBaseTerrain(mountain.map { it.name })
            .forEach { tile ->
                tile.baseTerrain = Constants.grassland // retract to grassland
                tile.setTerrainTransients()
            }
        // hill
        val hill = ruleset.terrains.values.filter { it.hasUnique(UniqueType.RoughTerrain) }
        map.values
            .filter { it.isLand }
            .filterFeatures(hill.map { it.name })
            .forEach { tile ->
                tile.retractTerrainFeatures(hill.map { it.name })
                tile.setTerrainTransients()
            }
    }

    /**
     * _this can not fully retract_
     */
    fun retractHumidityAndTemperature(map: TileMap) {
        val elevationTerrains = ruleset.terrains.values.asSequence()
            .filter {
                it.hasUnique(UniqueType.OccursInChains)
            }.mapTo(mutableSetOf()) { it.name }
        map.values
            .filter { tile -> tile.isLand && tile.baseTerrain !in elevationTerrains }
            .forEach { tile ->
                tile.baseTerrain = Constants.grassland // retract to grassland
                tile.temperature = 0.0
                tile.humidity = 0.5
                tile.setTerrainTransients()
            }
    }

    /**
     * _this can not fully retract_
     */
    fun retractLakes(map: TileMap) {
        map.values.filter { it.baseTerrain == Constants.lakes }
            .forEach { tile ->
                tile.baseTerrain = Constants.grassland // todo: better guess, maybe not grassland
                tile.setTerrainTransients()
            }
    }

    fun retractCoast(map: TileMap) {
        map.values.filter { it.baseTerrain == Constants.coast }
            .forEach { tile ->

                tile.baseTerrain = Constants.ocean
                tile.setTerrainTransients()
            }
    }

    fun retractLakesAndCoast(map: TileMap) {
        retractCoast(map)
        retractLakes(map)
    }


    fun retractVegetation(map: TileMap) {
        val vegetationEquivalents =
                ruleset.terrains.values.filter { it.hasUnique(UniqueType.Vegetation) }.map { it.name }
        map.values.filterFeatures(vegetationEquivalents)
            .forEach { tile ->
                tile.retractTerrainFeatures(vegetationEquivalents)
            }
    }

    fun retractRareFeatures(map: TileMap) {
        val rareFeatures = ruleset.terrains.values.filter {
            it.type == TerrainType.TerrainFeature && it.hasUnique(UniqueType.RareFeature)
        }.map { it.name }

        map.values
            .filterFeatures(rareFeatures)
            .forEach { tile ->
                tile.retractTerrainFeatures(rareFeatures)
            }
    }


    fun retractIce(map: TileMap) {
        val waterTerrain: Set<String> =
                ruleset.terrains.values.asSequence()
                    .filter { it.type == TerrainType.Water }
                    .map { it.name }.toSet()

        val iceEquivalents = ruleset.terrains.values.asSequence()
            .filter { terrain ->
                terrain.type == TerrainType.TerrainFeature &&
                        terrain.impassable &&
                        terrain.occursOn.all { it in waterTerrain }
            }.map { it.name }.toList()

        map.values
            .filterFeatures(iceEquivalents)
            .forEach { tile ->
                tile.retractTerrainFeatures(iceEquivalents)
            }
    }

    /**
     * _this can not fully retract_, but we try to do the best
     */
    fun retractNaturalWonders(map: TileMap) {

        map.values
            .filter { it.isNaturalWonder() }
            .forEach { tile ->
                removeNatureWonder(ruleset, tile)
            }
    }


    // todo: better guess on previous tile
    private fun removeNatureWonder(ruleset: Ruleset, tile: Tile) {

        val naturalWonder = tile.getNaturalWonder()
        val wonderCandidateTiles = naturalWonder.occursOn.mapNotNull { ruleset.terrains[it] }

        val onWater = wonderCandidateTiles.any { it.type == TerrainType.Water }
        val onLand = wonderCandidateTiles.any { it.type == TerrainType.Land }

        val nearBy = tile.getTilesAtDistance(1)

        val retractedTile = when {
            onWater -> {
                if (nearBy.filter { it.isOcean }.count() >= nearBy.count() * 2 / 3) {
                    // more than 2/3 nearby are ocean
                    Constants.ocean
                } else {
                    Constants.coast
                }
            }

            onLand -> {
                if (nearBy.filter { it.isHill() }.count() >= nearBy.count() * 2 / 3) {
                    // more than 2/3 nearby are hill
                    Constants.hill
                } else {
                    Constants.grassland
                }
            }

            else -> { // what?
                wonderCandidateTiles.first().name
            }
        }

        tile.naturalWonder = null
        tile.baseTerrain = retractedTile
        tile.setTerrainTransients()
    }


    fun retractRivers(map: TileMap) {
        map.values.forEach { tile ->
            tile.hasBottomRightRiver = false
            tile.hasBottomRiver = false
            tile.hasBottomLeftRiver = false
        }
    }

    fun retractResources(map: TileMap) {
        map.values.forEach { tile -> tile.resource = null }
    }

    fun retractAncientRuins(map: TileMap) {
        val ruinsEquivalents = ruleset.tileImprovements.filter { it.value.isAncientRuinsEquivalent() }
        if (ruinsEquivalents.isEmpty()) return

        map.values
            .filter { tile -> tile.improvement in ruinsEquivalents.keys }
            .forEach { tile ->
                tile.changeImprovement(null)
            }
    }

    // fun retract(map: TileMap) {}

    private fun Tile.retractTerrainFeatures(features: List<String>) =
            setTerrainFeatures(
                terrainFeatures.toMutableList().also { it.removeAny(features) }
            )

    private fun Collection<Tile>.filterFeatures(terrainFeature: List<String>) =
            filter {
                it.terrainFeatures.containsAny(terrainFeature)
            }

    private fun Collection<Tile>.filterBaseTerrain(terrain: List<String>) =
            filter { it.baseTerrain in terrain }

    private fun <E> Collection<E>.containsAny(list: List<E>): Boolean =
            list.map { contains(it) }.reduce { a, b -> a || b }

    private fun <E> MutableCollection<E>.removeAny(list: List<E>): Boolean =
            list.map { remove(it) }.reduce { a, b -> a || b }
}
