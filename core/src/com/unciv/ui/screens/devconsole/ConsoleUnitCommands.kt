package com.unciv.ui.screens.devconsole

class ConsoleUnitCommands : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "add" to ConsoleAction("unit add <civName> <unitName>") { console, params ->
            val selectedTile = console.getSelectedTile()
            val civ = console.getCivByName(params[0])
            val baseUnit = console.gameInfo.ruleset.units.values.findCliInput(params[1])
                ?: throw ConsoleErrorException("Unknown unit")
            civ.units.placeUnitNearTile(selectedTile.position, baseUnit)
            DevConsoleResponse.OK
        },

        "remove" to ConsoleAction("unit remove") { console, _ ->
            val unit = console.getSelectedUnit()
            unit.destroy()
            DevConsoleResponse.OK
        },

        "addpromotion" to object : ConsoleAction("unit addpromotion <promotionName>", { console, params ->
            val unit = console.getSelectedUnit()
            val promotion = console.gameInfo.ruleset.unitPromotions.values.findCliInput(params[0])
                ?: throw ConsoleErrorException("Unknown promotion")
            unit.promotions.addPromotion(promotion.name, true)
            DevConsoleResponse.OK
        }) {
            override fun autocomplete(console: DevConsolePopup, params: List<String>): String? {
                if (params.isEmpty()) return null
                val promotions = console.getSelectedUnit().promotions.promotions
                val options = console.gameInfo.ruleset.unitPromotions.keys.asSequence()
                    .filter { it !in promotions }
                    .asIterable()
                return getAutocompleteString(params.last(), options, console)
            }
        },

        "removepromotion" to ConsoleAction("unit removepromotion <promotionName>") { console, params ->
            val unit = console.getSelectedUnit()
            val promotion = unit.promotions.getPromotions().findCliInput(params[0])
                ?: throw ConsoleErrorException("Promotion not found on unit")
            // No such action in-game so we need to manually update
            unit.promotions.promotions.remove(promotion.name)
            unit.updateUniques()
            unit.updateVisibleTiles()
            DevConsoleResponse.OK
        },

        "setmovement" to ConsoleAction("unit setmovement [amount]") { console, params ->
            // Note amount defaults to maxMovement, but is not limited by it - it's an arbitrary choice to allow that
            val unit = console.getSelectedUnit()
            val movement = params.firstOrNull()?.run {
                toFloatOrNull() ?: throw ConsoleErrorException("Invalid number")
            } ?: unit.getMaxMovement().toFloat()
            if (movement < 0f) throw ConsoleErrorException("Number out of range")
            unit.currentMovement = movement
            DevConsoleResponse.OK
        },

        "sethealth" to ConsoleAction("unit sethealth [amount]") { console, params ->
            val health = params.firstOrNull()?.run {
                toIntOrNull() ?: throw ConsoleErrorException("Invalid number")
            } ?: 100
            if (health !in 1..100) throw ConsoleErrorException("Number out of range")
            val unit = console.getSelectedUnit()
            unit.health = health
            DevConsoleResponse.OK
        }
    )
}
