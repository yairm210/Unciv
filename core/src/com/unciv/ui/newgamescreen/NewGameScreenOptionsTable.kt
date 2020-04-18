package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.logic.MapSaver
import com.unciv.logic.map.MapType
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.ruleset.tech.TechEra
import com.unciv.models.translations.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onChange
import com.unciv.ui.utils.toLabel

class NewGameScreenOptionsTable(val newGameScreen: NewGameScreen, val updatePlayerPickerTable:(desiredCiv:String)->Unit)
    : Table(CameraStageBaseScreen.skin) {
    val newGameParameters = newGameScreen.newGameParameters
    val mapParameters = newGameScreen.mapParameters
    val ruleset = newGameScreen.ruleset

    private var mapTypeSpecificTable = Table()
    private val generatedMapOptionsTable = MapParametersTable(mapParameters)
    private val savedMapOptionsTable = Table()
    private lateinit var modPickerTable: ModPickerTable

    init {
        pad(10f)
        top()
        defaults().pad(5f)
        add("Map options".toLabel(fontSize = 24)).colspan(2).row()
        addMapTypeSelection()

        add("Game options".toLabel(fontSize = 24)).colspan(2).row()
        addDifficultySelectBox()
        addGameSpeedSelectBox()
        addEraSelectBox()
        addCityStatesSelectBox()
        addVictoryTypeCheckboxes()
        addBarbariansCheckbox()
        addOneCityChallengeCheckbox()
        addNuclearWeaponsCheckbox()
        addIsOnlineMultiplayerCheckbox()
        addModCheckboxes()

        pack()
    }

    private fun addMapTypeSelection() {
        add("{Map type}:".toLabel())
        val mapTypes = arrayListOf("Generated")
        if (MapSaver.getMaps().isNotEmpty()) mapTypes.add(MapType.custom)
        val mapTypeSelectBox = TranslatedSelectBox(mapTypes, "Generated", CameraStageBaseScreen.skin)

        val mapFileSelectBox = getMapFileSelectBox()
        savedMapOptionsTable.defaults().pad(5f)
        savedMapOptionsTable.add("{Map file}:".toLabel()).left()
        // because SOME people gotta give the hugest names to their maps
        savedMapOptionsTable.add(mapFileSelectBox).maxWidth(newGameScreen.stage.width / 2)
                .right().row()

        fun updateOnMapTypeChange() {
            mapTypeSpecificTable.clear()
            if (mapTypeSelectBox.selected.value == MapType.custom) {
                mapParameters.type = MapType.custom
                mapParameters.name = mapFileSelectBox.selected
                mapTypeSpecificTable.add(savedMapOptionsTable)
            } else {
                mapParameters.name = ""
                mapParameters.type = generatedMapOptionsTable.mapTypeSelectBox.selected.value
                mapTypeSpecificTable.add(generatedMapOptionsTable)
            }
        }

        // activate once, so when we had a file map before we'll have the right things set for another one
        updateOnMapTypeChange()

        mapTypeSelectBox.onChange { 
			updateOnMapTypeChange() 
            updateModsDependentOnMap()
		}

        add(mapTypeSelectBox).row()
        add(mapTypeSpecificTable).colspan(2).row()
    }


    private fun getMapFileSelectBox(): SelectBox<String> {
        val mapFileSelectBox = SelectBox<String>(CameraStageBaseScreen.skin)
        val mapNames = Array<String>()
        for (mapName in MapSaver.getMaps()) mapNames.add(mapName)
        mapFileSelectBox.items = mapNames
        if (mapParameters.name in mapNames) mapFileSelectBox.selected = mapParameters.name

        mapFileSelectBox.onChange { 
			mapParameters.name = mapFileSelectBox.selected!! 
            updateModsDependentOnMap()
		}
        return mapFileSelectBox
    }

    private fun addCheckbox(text:String, initialState:Boolean, onChange:(newValue:Boolean)->Unit){
        val checkbox = CheckBox(text.tr(), CameraStageBaseScreen.skin)
        checkbox.isChecked = initialState
        checkbox.onChange { onChange(checkbox.isChecked) }
        add(checkbox).colspan(2).row()
    }

    private fun addBarbariansCheckbox()  =
        addCheckbox("No barbarians", newGameParameters.noBarbarians)
            { newGameParameters.noBarbarians = it }

    private fun addOneCityChallengeCheckbox() =
        addCheckbox("One City Challenge", newGameParameters.oneCityChallenge)
            { newGameParameters.oneCityChallenge = it }

    private fun addNuclearWeaponsCheckbox() =
        addCheckbox("Enable nuclear weapons", newGameParameters.nuclearWeaponsEnabled)
            { newGameParameters.nuclearWeaponsEnabled = it }


    private fun addIsOnlineMultiplayerCheckbox() {

        val isOnlineMultiplayerCheckbox = CheckBox("Online Multiplayer".tr(), CameraStageBaseScreen.skin)
        isOnlineMultiplayerCheckbox.isChecked = newGameParameters.isOnlineMultiplayer
        isOnlineMultiplayerCheckbox.onChange {
            newGameParameters.isOnlineMultiplayer = isOnlineMultiplayerCheckbox.isChecked
            updatePlayerPickerTable("")
        }
        add(isOnlineMultiplayerCheckbox).colspan(2).row()
    }

    private fun addCityStatesSelectBox() {
        add("{Number of city-states}:".tr())
        val cityStatesSelectBox = SelectBox<Int>(CameraStageBaseScreen.skin)
        val cityStatesArray = Array<Int>()

        (0..ruleset.nations.filter { it.value.isCityState() }.size).forEach { cityStatesArray.add(it) }
        cityStatesSelectBox.items = cityStatesArray
        cityStatesSelectBox.selected = newGameParameters.numberOfCityStates
        add(cityStatesSelectBox).row()
        cityStatesSelectBox.onChange {
            newGameParameters.numberOfCityStates = cityStatesSelectBox.selected
        }
    }

    fun addSelectBox(text:String, values:Collection<String>, initialState:String, onChange: (newValue: String) -> Unit){
        add(text.tr())
        val selectBox = TranslatedSelectBox(values, initialState, CameraStageBaseScreen.skin)
        selectBox.onChange { onChange(selectBox.selected.value) }
        add(selectBox).fillX().row()
    }

    private fun addDifficultySelectBox() {
        addSelectBox("{Difficulty}:", ruleset.difficulties.keys, newGameParameters.difficulty)
            {newGameParameters.difficulty = it}
    }

    private fun addGameSpeedSelectBox() {
        addSelectBox("{Game Speed}:", GameSpeed.values().map { it.name }, newGameParameters.gameSpeed.name)
            {newGameParameters.gameSpeed = GameSpeed.valueOf(it)}
    }

    private fun addEraSelectBox() {
        val eras = ruleset.technologies.values.map { it.era() }.distinct()
        addSelectBox("{Starting Era}:", eras, newGameParameters.startingEra)
            { newGameParameters.startingEra = it }
    }


    private fun addVictoryTypeCheckboxes() {
        add("{Victory conditions}:".toLabel()).colspan(2).row()

        // Create a checkbox for each VictoryType existing
        var i = 0
        val victoryConditionsTable = Table().apply { defaults().pad(5f) }
        for (victoryType in VictoryType.values()) {
            if (victoryType == VictoryType.Neutral) continue
            val victoryCheckbox = CheckBox(victoryType.name.tr(), CameraStageBaseScreen.skin)
            victoryCheckbox.name = victoryType.name
            victoryCheckbox.isChecked = newGameParameters.victoryTypes.contains(victoryType)
            victoryCheckbox.onChange {
                // If the checkbox is checked, adds the victoryTypes else remove it
                if (victoryCheckbox.isChecked) {
                    newGameParameters.victoryTypes.add(victoryType)
                } else {
                    newGameParameters.victoryTypes.remove(victoryType)
                }
            }
            victoryConditionsTable.add(victoryCheckbox).left()
            if (++i % 2 == 0) victoryConditionsTable.row()
        }
        add(victoryConditionsTable).colspan(2).row()
    }


    private fun addModCheckboxes() {
        fun notifyChangedMods(modList: LinkedHashSet<String>, activatedMod: String) {
            ruleset.clear()
            ruleset.add(RulesetCache.getComplexRuleset(modList))
            ruleset.mods += modList

            ImageGetter.ruleset=ruleset
            ImageGetter.setTextureRegionDrawables()

            var desiredCiv = ""
            if (activatedMod.isNotEmpty()) {
                val modNations = RulesetCache[activatedMod]?.nations
                if (modNations != null && modNations.size > 0) {
                    desiredCiv = modNations.keys.first()
                }
            }
            updatePlayerPickerTable(desiredCiv)
        }

        modPickerTable = ModPickerTable(newGameParameters.mods) { modList, activatedMod -> notifyChangedMods(modList,activatedMod) }
        add(modPickerTable).colspan(2).row()
    }

    private fun updateModsDependentOnMap() {
/*  Upcoming...
        // Clarify for the user that mods required by the selected map are now mandatory
        var lockMods = HashSet<String>()
        if (mapParameters.type == MapType.custom) {
            try {
                val mapInfo = MapSaver.loadMapInfo(mapParameters.name)
                lockMods = mapInfo.requiredMods
            } catch (ex: Exception) {}
        }
        modPickerTable.updateLockedMods (lockMods)
*/
    }

}
