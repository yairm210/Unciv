package com.unciv.ui.screens.devconsole

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.tile.TerrainType

class ConsoleTileCommands: ConsoleCommandNode {
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
            DevConsoleResponse.OK
        },

        "removeimprovement" to ConsoleAction("tile removeimprovement") { console, _ ->
            val selectedTile = console.getSelectedTile()
            selectedTile.improvementFunctions.changeImprovement(null)
            DevConsoleResponse.OK
        },

        "removeroad" to ConsoleAction("tile removeroad") { console, _ ->
            val selectedTile = console.getSelectedTile()
            selectedTile.roadStatus = RoadStatus.None
            DevConsoleResponse.OK
        },

        "addfeature" to ConsoleAction("tile addfeature <featureName>") { console, params ->
            val selectedTile = console.getSelectedTile()
            val feature = getTerrainFeature(console, params[0])
            selectedTile.addTerrainFeature(feature.name)
            DevConsoleResponse.OK
        },

        "removefeature" to ConsoleAction("tile addfeature <featureName>") { console, params ->
            val selectedTile = console.getSelectedTile()
            val feature = getTerrainFeature(console, params[0])
            selectedTile.removeTerrainFeature(feature.name)
            DevConsoleResponse.OK
        }
    )

    private fun getTerrainFeature(console: DevConsolePopup, param: String) =
        console.gameInfo.ruleset.terrains.values.asSequence()
        .filter { it.type == TerrainType.TerrainFeature }.findCliInput(param)
        ?: throw ConsoleErrorException("Unknown feature")
}
