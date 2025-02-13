package com.unciv.ui.screens.devconsole

import com.unciv.models.ruleset.Building
import com.unciv.ui.screens.devconsole.CliInput.Companion.findCliInput

internal class ConsoleCityCommands : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "checkfilter" to ConsoleAction("city checkfilter <cityFilter>") { console, params ->
            val city = console.getSelectedCity()
            DevConsoleResponse.hint(city.matchesFilter(params[0].originalUnquoted()).toString())
        },

        "add" to ConsoleAction("city add <civName>") { console, params ->
            val civ = console.getCivByName(params[0])
            if (!civ.isMajorCiv() && !civ.isCityState) 
                throw ConsoleErrorException("Can only add cities to major civs or city states")
            val selectedTile = console.getSelectedTile()
            if (selectedTile.isCityCenter())
                throw ConsoleErrorException("Tile already contains a city center")
            civ.addCity(selectedTile.position)
            DevConsoleResponse.OK
        },

        "remove" to ConsoleAction("city remove") { console, _ ->
            val city = console.getSelectedCity()
            city.destroyCity(overrideSafeties = true)
            DevConsoleResponse.OK
        },

        "setpop" to ConsoleAction("city setpop <amount>") { console, params ->
            val city = console.getSelectedCity()
            val newPop = params[0].toInt()
            if (newPop < 1) throw ConsoleErrorException("Population must be at least 1")
            city.population.setPopulation(newPop)
            DevConsoleResponse.OK
        },

        "setname" to ConsoleAction("city setname <\"name\">") { console, params ->
            val city = console.getSelectedCity()
            city.name = params[0].originalUnquoted()
            DevConsoleResponse.OK
        },

        "addtile" to ConsoleAction("city addtile <cityName> [radius]") { console, params ->
            val selectedTile = console.getSelectedTile()
            val city = console.getCity(params[0])
            if (selectedTile.neighbors.none { it.getCity() == city })
                throw ConsoleErrorException("Tile is not adjacent to any tile already owned by the city")
            if (selectedTile.isCityCenter()) throw ConsoleErrorException("Cannot transfer city center")
            val radius = params.getOrNull(1)?.toInt() ?: 0
            for (tile in selectedTile.getTilesInDistance(radius)) {
                if (tile.getCity() != city && !tile.isCityCenter())
                    city.expansion.takeOwnership(tile)
            }
            DevConsoleResponse.OK
        },

        "removetile" to ConsoleAction("city removetile") { console, _ ->
            val selectedTile = console.getSelectedTile()
            val city = console.getSelectedCity()
            city.expansion.relinquishOwnership(selectedTile)
            DevConsoleResponse.OK
        },

        "religion" to ConsoleAction("city religion <religionName> <±pressure>") { console, params ->
            val city = console.getSelectedCity()
            val religion = params[0].findOrNull(console.gameInfo.religions.keys)
                ?: throw ConsoleErrorException("'${params[0]}' is not a known religion")
            val pressure = params[1].toInt()
            city.religion.addPressure(religion, pressure.coerceAtLeast(-city.religion.getPressures()[religion]))
            city.religion.updatePressureOnPopulationChange(0)
            DevConsoleResponse.OK
        },

        "sethealth" to ConsoleAction("city sethealth [amount]") { console, params ->
            val city = console.getSelectedCity()
            val maxHealth = city.getMaxHealth()
            val health = params.firstOrNull()?.toInt() ?: maxHealth
            if (health !in 1..maxHealth) throw ConsoleErrorException("Number out of range")
            city.health = health
            DevConsoleResponse.OK
        },

        "addbuilding" to ConsoleAction("city addbuilding <buildingName>") { console, params ->
            val city = console.getSelectedCity()
            val building = console.findCliInput<Building>(params[0])
                ?: throw ConsoleErrorException("Unknown building")
            city.cityConstructions.addBuilding(building)
            DevConsoleResponse.OK
        },
        "removebuilding" to ConsoleAction("city removebuilding <buildingName>") { console, params ->
            val city = console.getSelectedCity()
            val building = console.findCliInput<Building>(params[0])
                ?: throw ConsoleErrorException("Unknown building")
            city.cityConstructions.removeBuilding(building)
            DevConsoleResponse.OK
        },
    )
}
