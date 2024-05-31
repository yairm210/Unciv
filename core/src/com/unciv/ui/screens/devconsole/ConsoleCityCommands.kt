package com.unciv.ui.screens.devconsole

import com.unciv.models.ruleset.Building

class ConsoleCityCommands : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "checkfilter" to ConsoleAction("city checkfilter <cityFilter>") { console, params ->
            val city = console.getSelectedCity()
            DevConsoleResponse.hint(city.matchesFilter(params[0]).toString())
        },

        "add" to ConsoleAction("city add <civName>") { console, params ->
            val civ = console.getCivByName(params[0])
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
            val newPop = console.getInt(params[0])
            if (newPop < 1) throw ConsoleErrorException("Population must be at least 1")
            city.population.setPopulation(newPop)
            DevConsoleResponse.OK
        },

        "setname" to ConsoleAction("city setname <\"name\">") { console, params ->
            val city = console.getSelectedCity()
            city.name = params[0]
            DevConsoleResponse.OK
        },

        "addtile" to ConsoleAction("city addtile <cityName>") { console, params ->
            val selectedTile = console.getSelectedTile()
            val city = console.getCity(params[0])
            if (selectedTile.neighbors.none { it.getCity() == city })
                throw ConsoleErrorException("Tile is not adjacent to any tile already owned by the city")
            if (selectedTile.isCityCenter()) throw ConsoleErrorException("Cannot tranfer city center")
            city.expansion.takeOwnership(selectedTile)
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
            val religion = city.civ.gameInfo.religions.keys.findCliInput(params[0])
                ?: throw ConsoleErrorException("'${params[0]}' is not a known religion")
            val pressure = console.getInt(params[1])
            city.religion.addPressure(religion, pressure.coerceAtLeast(-city.religion.getPressures()[religion]))
            city.religion.updatePressureOnPopulationChange(0)
            DevConsoleResponse.OK
        },

        "sethealth" to ConsoleAction("city sethealth [amount]") { console, params ->
            val city = console.getSelectedCity()
            val maxHealth = city.getMaxHealth()
            val health = params.firstOrNull()?.run {
                toIntOrNull() ?: throw ConsoleErrorException("Invalid number")
            } ?: maxHealth
            if (health !in 1..maxHealth) throw ConsoleErrorException("Number out of range")
            city.health = health
            DevConsoleResponse.OK
        },

        "addbuilding" to ConsoleAction("city addbuilding [buildingName]") { console, params ->
            val city = console.getSelectedCity()
            val building = console.findCliInput<Building>(params[0])
                ?: throw ConsoleErrorException("Unknown building")
            city.cityConstructions.addBuilding(building)
            DevConsoleResponse.OK
        },
        "removebuilding" to ConsoleAction("city removebuilding [buildingName]") { console, params ->
            val city = console.getSelectedCity()
            val building = console.findCliInput<Building>(params[0])
                ?: throw ConsoleErrorException("Unknown building")
            city.cityConstructions.removeBuilding(building)
            DevConsoleResponse.OK
        },
    )
}
