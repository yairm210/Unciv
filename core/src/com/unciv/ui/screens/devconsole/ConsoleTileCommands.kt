package com.unciv.ui.screens.devconsole

import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapgenerator.RiverGenerator
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.tile.TerrainType

class ConsoleTileCommands: ConsoleCommandNode {
    // Note: We *don't* call `TileInfoNormalizer.normalizeToRuleset(selectedTile, console.gameInfo.ruleset)`
    // - we want the console to allow invalid tile configurations.

    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "setimprovement" to ConsoleAction("tile setimprovement <improvementName> [civName]") { console, params ->
            val selectedTile = console.getSelectedTile()
            val improvement = console.gameInfo.ruleset.tileImprovements.values.findCliInput(params[0])
                ?: throw ConsoleErrorException("Unknown improvement")
            var civ: Civilization? = null
            if (params.size == 2) {
                civ = console.getCivByName(params[1])
            }
            selectedTile.improvementFunctions.changeImprovement(improvement.name, civ)
            selectedTile.getCity()?.reassignPopulation()
            DevConsoleResponse.OK
        },

        "removeimprovement" to ConsoleAction("tile removeimprovement") { console, _ ->
            val selectedTile = console.getSelectedTile()
            selectedTile.improvementFunctions.changeImprovement(null)
            selectedTile.getCity()?.reassignPopulation()
            DevConsoleResponse.OK
        },

        "removeroad" to ConsoleAction("tile removeroad") { console, _ ->
            val selectedTile = console.getSelectedTile()
            selectedTile.roadStatus = RoadStatus.None
            //todo this covers many cases but not all - do we really need to loop over all civs?
            selectedTile.getOwner()?.cache?.updateCitiesConnectedToCapital()
            DevConsoleResponse.OK
        },

        "addfeature" to ConsoleAction("tile addfeature <featureName>") { console, params ->
            val selectedTile = console.getSelectedTile()
            val feature = getTerrainFeature(console, params[0])

            if (feature.name == Constants.river)
                RiverGenerator.continueRiverOn(selectedTile)
            else
                selectedTile.addTerrainFeature(feature.name)

            selectedTile.getCity()?.reassignPopulation()
            DevConsoleResponse.OK
        },

        "removefeature" to ConsoleAction("tile removefeature <featureName>") { console, params ->
            val selectedTile = console.getSelectedTile()
            val feature = getTerrainFeature(console, params[0])
            if (feature.name == Constants.river)
                throw ConsoleHintException("Rivers cannot be removed like a terrain feature - use tile removeriver <direction>")
            selectedTile.removeTerrainFeature(feature.name)
            selectedTile.getCity()?.reassignPopulation()
            DevConsoleResponse.OK
        },

        "setterrain" to ConsoleAction("tile setterrain <terrainName>") { console, params ->
            val selectedTile = console.getSelectedTile()
            val terrain = console.gameInfo.ruleset.terrains.values.findCliInput(params[0])
                ?: throw ConsoleErrorException("Unknown terrain")
            if (terrain.type != selectedTile.getBaseTerrain().type)
                throw ConsoleErrorException("Changing terrain type is not allowed")
            selectedTile.baseTerrain = terrain.name
            selectedTile.setTerrainTransients()
            selectedTile.getCity()?.reassignPopulation()
            DevConsoleResponse.OK
        },

        "setresource" to ConsoleAction("tile setresource <resourceName>") { console, params ->
            val selectedTile = console.getSelectedTile()
            val resource = console.gameInfo.ruleset.tileResources.values.findCliInput(params[0])
                ?: throw ConsoleErrorException("Unknown resource")
            selectedTile.resource = resource.name
            selectedTile.setTerrainTransients()
            selectedTile.getCity()?.reassignPopulation()
            DevConsoleResponse.OK
        },

        "removeresource" to ConsoleAction("tile removeresource") { console, _ ->
            val selectedTile = console.getSelectedTile()
            selectedTile.resource = null
            selectedTile.setTerrainTransients()
            selectedTile.getCity()?.reassignPopulation()
            DevConsoleResponse.OK
        },

        "addriver" to ConsoleRiverAction("tile addriver <direction>", true),
        "removeriver" to ConsoleRiverAction("tile removeriver <direction>", false),
    )

    private fun getTerrainFeature(console: DevConsolePopup, param: String) =
        console.gameInfo.ruleset.terrains.values.asSequence()
        .filter { it.type == TerrainType.TerrainFeature }.findCliInput(param)
        ?: throw ConsoleErrorException("Unknown feature")

    private enum class RiverDirections(val clockPosition: Int) {
        North(12),
        NorthEast(2),
        SouthEast(4),
        South(6),
        SouthWest(8),
        NorthWest(10)
    }

    private class ConsoleRiverAction(format: String, newValue: Boolean) : ConsoleAction(
        format,
        action = { console, params -> action(console, params, newValue) }
    ) {
        override fun autocomplete(console: DevConsolePopup, params: List<String>): String? {
            if (params.isEmpty()) return null
            // Note this could filter which directions are allowed on the selected tile... too lazy
            val options = RiverDirections.values().map { it.name }
            return getAutocompleteString(params.last(), options, console)
        }

        companion object {
            private fun action(console: DevConsolePopup, params: List<String>, newValue: Boolean): DevConsoleResponse {
                val selectedTile = console.getSelectedTile()
                val direction = findCliInput<RiverDirections>(params[0])
                    ?: throw ConsoleErrorException("Unknown direction - use " + RiverDirections.values().joinToString { it.name })
                val otherTile = selectedTile.tileMap.getClockPositionNeighborTile(selectedTile, direction.clockPosition)
                    ?: throw ConsoleErrorException("tile has no neighbor to the " + direction.name)
                if (!otherTile.isLand)
                    throw ConsoleErrorException("there's no land to the " + direction.name)
                selectedTile.setConnectedByRiver(otherTile, newValue)
                return DevConsoleResponse.OK
            }
        }
    }
}
