package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen

class DevConsolePopup(val screen: WorldScreen): Popup(screen){

    val textField = TextField("", BaseScreen.skin)
    internal val gameInfo = screen.gameInfo

    init {
        add(textField).width(stageToShowOn.width / 2).row()
        val label = "".toLabel(Color.RED)
        add(label)
        textField.keyShortcuts.add(Input.Keys.ENTER) {
            val handleCommandResponse = handleCommand(textField.text)
            if (handleCommandResponse == null) {
                screen.shouldUpdate = true
                close()
            }
            else label.setText(handleCommandResponse)
        }
        // Without this, console popup will always contain a `
        textField.addAction(Actions.delay(0.05f, Actions.run { textField.text = "" }))
        open(true)
        keyShortcuts.add(KeyCharAndCode.ESC){ close() }
    }

    private fun handleCommand(text:String): String?{
        val params = text.split(" ").filter { it.isNotEmpty() }.map { it.lowercase() }
        if (params.isEmpty()) return "No command"
        return when(params[0]) {
            "city" -> handleCityCommand(params)
            "unit" -> handleUnitCommand(params)
            else -> null
        }
    }

    private fun handleCityCommand(params: List<String>): String? {
        if (params.size == 1) return "Available commands: setpop, addtile, removetile"
        when (params[1]){
            "setpop" -> {
                if (params.size != 4) return "Format: city setpop <cityName> <amount>"
                val newPop = params[3].toIntOrNull() ?: return "Invalid amount "+params[3]
                if (newPop < 1) return "Invalid amount $newPop"
                val city = gameInfo.getCities().firstOrNull { it.name.lowercase() == params[2] } ?: return "Unknown city"
                city.population.setPopulation(newPop)
            }
            "addtile" -> {
                val selectedTile = screen.mapHolder.selectedTile ?: return "No tile selected"
                val city = gameInfo.getCities().firstOrNull { it.name.lowercase() == params[2] } ?: return "Unknown city"
                if (selectedTile.neighbors.none { it.getCity() == city }) return "Tile is not adjacent to city"
                city.expansion.takeOwnership(selectedTile)
            }
            "removetile" -> {
                val selectedTile = screen.mapHolder.selectedTile ?: return "No tile selected"
                val city = gameInfo.getCities().firstOrNull { it.name.lowercase() == params[2] } ?: return "Unknown city"
                if (city.tiles.contains(selectedTile.position)) city.expansion.relinquishOwnership(selectedTile)
            }
            else -> return "Unknown command"
        }
        return null
    }

    private fun getCivByName(name:String) = gameInfo.civilizations.firstOrNull { it.civName.lowercase().replace(" ","-") == name }

    private fun handleUnitCommand(params: List<String>): String? {
        if (params.size == 1) return "Available commands: add"
        when (params[1]){
            "add" -> {
                if (params.size != 4) return "Format: unit add <civName> <unitName>"
                val selectedTile = screen.mapHolder.selectedTile ?: return "No tile selected"
                val civ = getCivByName(params[2]) ?: return "Unknown civ"
                val baseUnit = gameInfo.ruleset.units.values.firstOrNull { it.name.lowercase() == params[3] } ?: return "Unknown unit"
                civ.units.placeUnitNearTile(selectedTile.position, baseUnit)
            }
            "remove" -> {
                val selectedTile = screen.mapHolder.selectedTile ?: return "No tile selected"
                if (selectedTile.getFirstUnit() == null) return "No unit in tile"
                val units = selectedTile.getUnits().toList()
                val selectedUnit = screen.bottomUnitTable.selectedUnit
                val unit = if (selectedUnit!=null && selectedUnit.getTile() == selectedTile) selectedUnit
                    else units.first()
                unit.destroy()
            }
            else -> return "Unknown command"
        }
        return null
    }
}
