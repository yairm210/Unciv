package com.unciv.ui.screens.devconsole

import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.mapgenerator.RiverGenerator
import com.unciv.logic.map.mapgenerator.RiverGenerator.RiverDirections
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType

internal class ConsoleTileCommands: ConsoleCommandNode {
    // Note: We *don't* call `TileNormalizer.normalizeToRuleset(selectedTile, console.gameInfo.ruleset)`
    // - we want the console to allow invalid tile configurations.

    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "checkfilter" to ConsoleAction("tile checkfilter <tileFilter>") { console, params ->
            val selectedTile = console.getSelectedTile()
            DevConsoleResponse.hint(selectedTile.matchesFilter(params[0].originalUnquoted()).toString())
        },

        "setimprovement" to ConsoleAction("tile setimprovement <improvementName> [civName]") { console, params ->
            val selectedTile = console.getSelectedTile()
            val improvement = params[0].find(console.gameInfo.ruleset.tileImprovements.values)
            val civ = params.getOrNull(1)?.let { console.getCivByName(it) }
            selectedTile.improvementFunctions.setImprovement(improvement.name, civ)
            selectedTile.getCity()?.reassignPopulation()
            DevConsoleResponse.OK
        },

        "removeimprovement" to ConsoleAction("tile removeimprovement") { console, _ ->
            val selectedTile = console.getSelectedTile()
            selectedTile.improvementFunctions.setImprovement(null)
            selectedTile.getCity()?.reassignPopulation()
            DevConsoleResponse.OK
        },

        "removeroad" to ConsoleAction("tile removeroad") { console, _ ->
            val selectedTile = console.getSelectedTile()
            selectedTile.removeRoad()
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
            val terrain = params[0].find(console.gameInfo.ruleset.terrains.values)
            if (terrain.type == TerrainType.NaturalWonder)
                setNaturalWonder(selectedTile, terrain)
            else
                setBaseTerrain(selectedTile, terrain)
        },

        "setresource" to ConsoleAction("tile setresource <resourceName>") { console, params ->
            val selectedTile = console.getSelectedTile()
            val resource = params[0].find(console.gameInfo.ruleset.tileResources.values)
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

        "setowner" to ConsoleAction("tile setowner [civName|cityName]") { console, params ->
            val selectedTile = console.getSelectedTile()
            val oldOwner = selectedTile.getCity()
            val newOwner = getOwnerCity(console, params, selectedTile)
            // for simplicity, treat assign to civ without cities same as un-assign
            oldOwner?.expansion?.relinquishOwnership(selectedTile) // redundant if new owner is not null, but simpler for un-assign
            newOwner?.expansion?.takeOwnership(selectedTile)
            DevConsoleResponse.OK
        },

        "find" to ConsoleAction("tile find <tileFilter>") { console, params ->
            val filter = params[0]
            val locations = console.gameInfo.tileMap.tileList
                .filter { it.matchesFilter(filter.toString()) }
                .map { it.position }
            if (locations.isEmpty()) DevConsoleResponse.hint("None found")
            else {
                val notification = Notification("tile find [$filter]", arrayOf(NotificationIcon.Spy),
                    LocationAction(locations).asIterable(), NotificationCategory.General)
                console.screen.notificationsScroll.oneTimeNotification = notification
                notification.execute(console.screen)
                DevConsoleResponse.OK
            }
        },
    )

    private fun setBaseTerrain(tile: Tile, terrain: Terrain): DevConsoleResponse {
        if (terrain.type != tile.getBaseTerrain().type)
            throw ConsoleErrorException("Changing terrain type is not allowed")
        setBaseTerrain(tile, terrain.name)
        return DevConsoleResponse.OK
    }
    private fun setBaseTerrain(tile: Tile, terrainName: String) {
        tile.baseTerrain = terrainName
        tile.setTerrainTransients()
        tile.getCity()?.reassignPopulation()
    }
    private fun setNaturalWonder(tile: Tile, wonder: Terrain): DevConsoleResponse {
        tile.removeTerrainFeatures()
        tile.naturalWonder = wonder.name
        setBaseTerrain(tile, wonder.turnsInto ?: tile.baseTerrain)
        for (civ in tile.tileMap.gameInfo.civilizations) {
            if (wonder.name in civ.naturalWonders) continue
            if (civ.isDefeated() || civ.isBarbarian || civ.isSpectator()) continue
            if (!civ.hasExplored(tile)) continue
            civ.cache.discoverNaturalWonders()
            civ.updateStatsForNextTurn()
        }
        return DevConsoleResponse.OK
    }

    private fun getTerrainFeature(console: DevConsolePopup, param: CliInput) = param.find(
        console.gameInfo.ruleset.terrains.values.asSequence()
            .filter { it.type == TerrainType.TerrainFeature }
    )

    private class ConsoleRiverAction(format: String, newValue: Boolean) : ConsoleAction(
        format,
        action = { console, params -> action(console, params, newValue) }
    ) {
        companion object {
            private fun action(console: DevConsolePopup, params: List<CliInput>, newValue: Boolean): DevConsoleResponse {
                val selectedTile = console.getSelectedTile()
                val direction = params[0].enumValue<RiverDirections>()
                val otherTile = direction.getNeighborTile(selectedTile)
                    ?: throw ConsoleErrorException("tile has no neighbor to the " + direction.name)
                if (!otherTile.isLand)
                    throw ConsoleErrorException("there's no land to the " + direction.name)
                selectedTile.setConnectedByRiver(otherTile, newValue)
                return DevConsoleResponse.OK
            }
        }
    }

    private fun getOwnerCity(console: DevConsolePopup, params: List<CliInput>, selectedTile: Tile): City? {
        val param = params.getOrNull(0) ?: return null
        if (param.isEmpty()) return null
        // Look for a city by name to assign the Tile to
        val namedCity = param.findOrNull(console.gameInfo.civilizations.flatMap { civ -> civ.cities })
        if (namedCity != null) return namedCity
        // If the user didn't specify a City, they must have given us a Civilization instead
        val namedCiv = console.getCivByNameOrNull(param)
            ?: throw ConsoleErrorException("$param is neither a city nor a civilization")
        return namedCiv.cities.minByOrNull { it.getCenterTile().aerialDistanceTo(selectedTile) + (if (it.isBeingRazed) 5 else 0) }
    }
}
