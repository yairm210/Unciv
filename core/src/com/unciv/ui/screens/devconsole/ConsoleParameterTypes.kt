package com.unciv.ui.screens.devconsole

import com.unciv.logic.GameInfo
import com.unciv.logic.map.mapgenerator.RiverGenerator
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.stats.Stat

@Suppress("EnumEntryName", "unused")

/**
 *  Enum encapsulates knowledge about console command parameter types
 *  - Extensible
 *  - Currently limited to supplying autocomplete possibilities: use [Companion.getOptions]
 *  - Supports multi-type parameters via [Companion.multiOptions]
 */
internal enum class ConsoleParameterType(
    private val getOptions: GameInfo.() -> Iterable<String>
) {
    none( { emptyList() } ),
    civName( { civilizations.map { it.civName } } ),
    unitName( { ruleset.units.keys } ),
    promotionName( { ruleset.unitPromotions.keys } ),
    improvementName( { ruleset.tileImprovements.keys } ),
    featureName( { ruleset.terrains.values.filter { it.type == TerrainType.TerrainFeature }.map { it.name } } ),
    terrainName( { ruleset.terrains.values.filter { it.type.isBaseTerrain }.map { it.name } } ),
    resourceName( { ruleset.tileResources.keys } ),
    stat( { Stat.names() } ),
    religionName( { religions.keys } ),
    buildingName( { ruleset.buildings.keys } ),
    direction( { RiverGenerator.RiverDirections.names } ),
    policyName( { ruleset.policyBranches.keys + ruleset.policies.keys } ),
    cityName( { civilizations.flatMap { civ -> civ.cities.map { it.name } } } ),
    ;

    private fun getOptions(console: DevConsolePopup) = console.gameInfo.getOptions()

    companion object {
        fun safeValueOf(name: String): ConsoleParameterType = values().firstOrNull { it.name == name } ?: none
        fun getOptions(name: String, console: DevConsolePopup) = safeValueOf(name).getOptions(console)
    }
}
