package com.unciv.ui.screens.devconsole

import com.unciv.ui.screens.devconsole.CliInput.Companion.getAutocompleteString
import com.unciv.ui.screens.devconsole.CliInput.Companion.orEmpty

internal class ConsoleUnitCommands : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "checkfilter" to ConsoleAction("unit checkfilter <unitFilter>") { console, params ->
            val unit = console.getSelectedUnit()
            DevConsoleResponse.hint(unit.matchesFilter(params[0].originalUnquoted()).toString())
        },

        "add" to ConsoleAction("unit add <civName> <unitName>") { console, params ->
            val selectedTile = console.getSelectedTile()
            val civ = console.getCivByName(params[0])
            val baseUnit = params[1].find(console.gameInfo.ruleset.units.values)
            civ.units.placeUnitNearTile(selectedTile.position, baseUnit)
            DevConsoleResponse.OK
        },

        "remove" to ConsoleAction("unit remove [all]") { console, params ->
            if (params.isNotEmpty() && params[0].equals("all")) {
                for (civ in console.gameInfo.civilizations)
                    for (unit in civ.units.getCivUnits())
                        unit.destroy()
            } else {
                val unit = console.getSelectedUnit()
                unit.destroy()
            }
            DevConsoleResponse.OK
        },

        "addpromotion" to object : ConsoleAction("unit addpromotion <promotionName>", { console, params ->
            val unit = console.getSelectedUnit()
            val promotion = params[0].find(console.gameInfo.ruleset.unitPromotions.values)
            unit.promotions.addPromotion(promotion.name, true)
            DevConsoleResponse.OK
        }) {
            override fun autocomplete(console: DevConsolePopup, params: List<CliInput>): String? {
                // Note: filtering by unit.type.name in promotion.unitTypes sounds good (No [Zero]-Ability on an Archer),
                // but would also prevent promotions that can be legally obtained like Morale and Rejuvenation
                val promotions = console.getSelectedUnit().promotions.promotions
                val options = console.gameInfo.ruleset.unitPromotions.keys.asSequence()
                    .filter { it !in promotions }
                    .map { it.replace("[","").replace("]","") }
                    .asIterable()
                return getAutocompleteString(params.lastOrNull().orEmpty(), options, console)
            }
        },

        "removepromotion" to object : ConsoleAction("unit removepromotion <promotionName>", { console, params ->
            val unit = console.getSelectedUnit()
            val promotion = params[0].findOrNull(unit.promotions.getPromotions())
                ?: throw ConsoleErrorException("Promotion not found on unit")
            // No such action in-game so we need to manually update
            unit.promotions.promotions.remove(promotion.name)
            unit.updateUniques()
            unit.updateVisibleTiles()
            DevConsoleResponse.OK
        }) {
            override fun autocomplete(console: DevConsolePopup, params: List<CliInput>) =
                getAutocompleteString(params.lastOrNull().orEmpty(), console.getSelectedUnit().promotions.promotions, console)
        },

        "setmovement" to ConsoleAction("unit setmovement [amount]") { console, params ->
            // Note amount defaults to maxMovement, but is not limited by it - it's an arbitrary choice to allow that
            val unit = console.getSelectedUnit()
            val movement = params.firstOrNull()?.takeIf { !it.isEmpty() }?.toFloat() ?: unit.getMaxMovement().toFloat()
            if (movement < 0f) throw ConsoleErrorException("Number out of range")
            unit.currentMovement = movement
            DevConsoleResponse.OK
        },

        "sethealth" to ConsoleAction("unit sethealth [amount]") { console, params ->
            val health = params.firstOrNull()?.takeIf { !it.isEmpty() }?.toInt() ?: 100
            if (health !in 1..100) throw ConsoleErrorException("Number out of range")
            val unit = console.getSelectedUnit()
            unit.health = health
            DevConsoleResponse.OK
        },

        "setxp" to ConsoleAction("unit setxp [amount]") { console, params ->
            val xp = params.firstOrNull()?.toInt() ?: throw ConsoleErrorException("No XP provided")
            if (xp < 0) throw ConsoleErrorException("Number out of range")
            val unit = console.getSelectedUnit()
            unit.promotions.XP = xp
            DevConsoleResponse.OK
        }
    )
}
