package com.unciv.ui.screens.devconsole

import com.unciv.ui.screens.devconsole.DevConsoleCommand.Companion.toCliInput

@Suppress("EnumEntryName")
enum class DevConsoleCommandUnit {
    add {
        override fun handle(console: DevConsolePopup, params: List<String>): String? {
            if (params.size != 2)
                return "Format: unit add <civName> <unitName>"
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return "No tile selected"
            val civ = console.getCivByName(params[0])
                ?: return "Unknown civ"
            val baseUnit = console.gameInfo.ruleset.units.values.firstOrNull { it.name.toCliInput() == params[3] }
                ?: return "Unknown unit"
            civ.units.placeUnitNearTile(selectedTile.position, baseUnit)
            return null
        }
    },
    remove {
        override fun handle(console: DevConsolePopup, params: List<String>): String? {
            val unit = console.getSelectedUnit()
                ?: return "Select tile with unit"
            unit.destroy()
            return null
        }
    },
    addpromotion {
        override fun handle(console: DevConsolePopup, params: List<String>): String? {
            if (params.size != 1)
                return "Format: unit addpromotion <promotionName>"
            val unit = console.getSelectedUnit()
                ?: return "Select tile with unit"
            val promotion = console.gameInfo.ruleset.unitPromotions.values.firstOrNull { it.name.toCliInput() == params[2] }
                ?: return "Unknown promotion"
            unit.promotions.addPromotion(promotion.name, true)
            return null
        }
    },
    removepromotion {
        override fun handle(console: DevConsolePopup, params: List<String>): String? {
            if (params.size != 1)
                return "Format: unit removepromotion <promotionName>"
            val unit = console.getSelectedUnit()
                ?: return "Select tile with unit"
            val promotion = unit.promotions.getPromotions().firstOrNull { it.name.toCliInput() == params[2] }
                ?: return "Promotion not found on unit"
            // No such action in-game so we need to manually update
            unit.promotions.promotions.remove(promotion.name)
            unit.updateUniques()
            unit.updateVisibleTiles()
            return null
        }
    },
    ;

    abstract fun handle(console: DevConsolePopup, params: List<String>): String?

    companion object {
        fun handle(console: DevConsolePopup, params: List<String>): String? {
            if (params.isEmpty())
                return "Available subcommands: " + DevConsoleCommandUnit.values().joinToString { it.name }
            val handler = values().firstOrNull { it.name == params[0] }
                ?: return "Invalid subcommand"
            return handler.handle(console, params.drop(1))
        }
    }
}
