package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.MapGeneratedMainType
import com.unciv.ui.components.extensions.onChange
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen

class MapOptionsTable(private val newGameScreen: NewGameScreen): Table() {

    private val mapParameters = newGameScreen.gameSetupInfo.mapParameters
    private var mapTypeSpecificTable = Table()
    internal val generatedMapOptionsTable = MapParametersTable(newGameScreen, mapParameters, MapGeneratedMainType.generated)
    private val randomMapOptionsTable = MapParametersTable(newGameScreen, mapParameters, MapGeneratedMainType.randomGenerated)
    private val savedMapOptionsTable = MapFileSelectTable(newGameScreen, mapParameters)
    internal val mapTypeSelectBox: TranslatedSelectBox

    init {
        //defaults().pad(5f) - each nested table having the same can give 'stairs' effects,
        // better control directly. Besides, the first Labels/Buttons should have 10f to look nice
        background = BaseScreen.skinStrings.getUiBackground("NewGameScreen/MapOptionsTable", tintColor = BaseScreen.skinStrings.skinConfig.clearColor)

        val mapTypes = arrayListOf(MapGeneratedMainType.generated, MapGeneratedMainType.randomGenerated)
        if (savedMapOptionsTable.isNotEmpty()) mapTypes.add(MapGeneratedMainType.custom)
        mapTypeSelectBox = TranslatedSelectBox(mapTypes, "Generated", BaseScreen.skin)

        fun updateOnMapTypeChange() {
            mapTypeSpecificTable.clear()
            when (mapTypeSelectBox.selected.value) {
                MapGeneratedMainType.custom -> {
                    savedMapOptionsTable.fillMapFileSelectBox()
                    mapParameters.type = MapGeneratedMainType.custom
                    mapTypeSpecificTable.add(savedMapOptionsTable)
                    newGameScreen.unlockTables()
                }
                MapGeneratedMainType.generated -> {
                    mapParameters.name = ""
                    mapParameters.type = generatedMapOptionsTable.mapTypeSelectBox.selected.value
                    mapTypeSpecificTable.add(generatedMapOptionsTable)
                    newGameScreen.unlockTables()

                }
                MapGeneratedMainType.randomGenerated -> {
                    mapParameters.name = ""
                    mapTypeSpecificTable.add(randomMapOptionsTable)
                    newGameScreen.unlockTables()
                }
            }
            newGameScreen.gameSetupInfo.gameParameters.godMode = false
            newGameScreen.updateTables()
        }

        // Pre-select custom if any map saved within last 15 minutes
        if (savedMapOptionsTable.recentlySavedMapExists())
            mapTypeSelectBox.selected =
                    TranslatedSelectBox.TranslatedString(MapGeneratedMainType.custom)

        // activate once, so when we had a file map before we'll have the right things set for another one
        updateOnMapTypeChange()

        mapTypeSelectBox.onChange { updateOnMapTypeChange() }

        val mapTypeSelectWrapper = Table()  // wrap to center-align Label and SelectBox easier
        mapTypeSelectWrapper.add("{Map Type}:".toLabel()).left().expandX()
        mapTypeSelectWrapper.add(mapTypeSelectBox).right()
        add(mapTypeSelectWrapper).pad(10f).fillX().row()
        add(mapTypeSpecificTable).row()
    }

    internal fun cancelBackgroundJobs() = savedMapOptionsTable.cancelBackgroundJobs()
}
