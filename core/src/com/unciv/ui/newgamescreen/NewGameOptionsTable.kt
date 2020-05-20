package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.translations.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onChange
import com.unciv.ui.utils.toLabel

class NewGameOptionsTable(newGameScreen: NewGameScreen, val updatePlayerPickerTable:(desiredCiv:String)->Unit)
    : Table(CameraStageBaseScreen.skin) {
    val newGameParameters = newGameScreen.gameSetupInfo.gameParameters
    val ruleset = newGameScreen.ruleset

    init {
        top()
        defaults().pad(5f)

        add("Game Options".toLabel(fontSize = 24)).padTop(0f).padBottom(20f).colspan(2).row()
        add(Table().apply {
            defaults().pad(5f)
            addDifficultySelectBox()
            addGameSpeedSelectBox()
            addEraSelectBox()
        }).colspan(2).row()
        addCityStatesSelectBox()
        addVictoryTypeCheckboxes()

        val checkboxTable = Table().apply { defaults().pad(5f) }
        checkboxTable.addBarbariansCheckbox()
        checkboxTable.addOneCityChallengeCheckbox()
        checkboxTable.addNuclearWeaponsCheckbox()
        checkboxTable.addIsOnlineMultiplayerCheckbox()
        checkboxTable.addModCheckboxes()
        add(checkboxTable).colspan(2).row()

        pack()
    }

    private fun Table.addCheckbox(text: String, initialState: Boolean, onChange: (newValue: Boolean) -> Unit) {
        val checkbox = CheckBox(text.tr(), CameraStageBaseScreen.skin)
        checkbox.isChecked = initialState
        checkbox.onChange { onChange(checkbox.isChecked) }
        add(checkbox).colspan(2).left().row()
    }

    private fun Table.addBarbariansCheckbox() =
            addCheckbox("No Barbarians", newGameParameters.noBarbarians)
            { newGameParameters.noBarbarians = it }

    private fun Table.addOneCityChallengeCheckbox() =
            addCheckbox("One City Challenge", newGameParameters.oneCityChallenge)
            { newGameParameters.oneCityChallenge = it }

    private fun Table.addNuclearWeaponsCheckbox() =
            addCheckbox("Enable nuclear weapons", newGameParameters.nuclearWeaponsEnabled)
            { newGameParameters.nuclearWeaponsEnabled = it }


    private fun Table.addIsOnlineMultiplayerCheckbox()  =
        addCheckbox("Online Multiplayer", newGameParameters.isOnlineMultiplayer)
        { newGameParameters.isOnlineMultiplayer = it
            updatePlayerPickerTable("") }

    private fun addCityStatesSelectBox() {
        add("{Number of City-States}:".toLabel())
        val cityStatesSelectBox = SelectBox<Int>(CameraStageBaseScreen.skin)

        val numberOfCityStates = ruleset.nations.filter { it.value.isCityState() }.size

        val cityStatesArray = Array<Int>(numberOfCityStates + 1)
        (0..numberOfCityStates).forEach { cityStatesArray.add(it) }

        cityStatesSelectBox.items = cityStatesArray
        cityStatesSelectBox.selected = newGameParameters.numberOfCityStates
        add(cityStatesSelectBox).width(50f).row()
        cityStatesSelectBox.onChange {
            newGameParameters.numberOfCityStates = cityStatesSelectBox.selected
        }
    }

    fun Table.addSelectBox(text: String, values: Collection<String>, initialState: String, onChange: (newValue: String) -> Unit) {
        add(text.toLabel()).left()
        val selectBox = TranslatedSelectBox(values, initialState, CameraStageBaseScreen.skin)
        selectBox.onChange { onChange(selectBox.selected.value) }
        add(selectBox).fillX().row()
    }

    private fun Table.addDifficultySelectBox() {
        addSelectBox("{Difficulty}:", ruleset.difficulties.keys, newGameParameters.difficulty)
        { newGameParameters.difficulty = it }
    }

    private fun Table.addGameSpeedSelectBox() {
        addSelectBox("{Game Speed}:", GameSpeed.values().map { it.name }, newGameParameters.gameSpeed.name)
        { newGameParameters.gameSpeed = GameSpeed.valueOf(it) }
    }

    private fun Table.addEraSelectBox() {
        val eras = ruleset.technologies.values.map { it.era() }.distinct()
        addSelectBox("{Starting Era}:", eras, newGameParameters.startingEra)
        { newGameParameters.startingEra = it }
    }


    private fun addVictoryTypeCheckboxes() {
        add("{Victory Conditions}:".toLabel()).colspan(2).row()

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


    fun Table.addModCheckboxes() {
        val modRulesets = RulesetCache.filter { it.key != "" }.values
        if (modRulesets.isEmpty()) return

        fun reloadMods() {
            ruleset.clear()
            val newRuleset = RulesetCache.getComplexRuleset(newGameParameters.mods)
            ruleset.add(newRuleset)
            ruleset.mods += newGameParameters.mods
            ruleset.modOptions = newRuleset.modOptions

            ImageGetter.ruleset = ruleset
            ImageGetter.setTextureRegionDrawables()
        }

        add("Mods:".toLabel(fontSize = 24)).padTop(16f).colspan(2).row()
        val modCheckboxTable = Table().apply { defaults().pad(5f) }
        for (mod in modRulesets) {
            val checkBox = CheckBox(mod.name.tr(), CameraStageBaseScreen.skin)
            if (mod.name in newGameParameters.mods) checkBox.isChecked = true
            checkBox.onChange {
                if (checkBox.isChecked) newGameParameters.mods.add(mod.name)
                else newGameParameters.mods.remove(mod.name)
                reloadMods()
                var desiredCiv = ""
                if (checkBox.isChecked) {
                    val modNations = RulesetCache[mod.name]?.nations
                    if (modNations != null && modNations.size > 0) {
                        desiredCiv = modNations.keys.first()
                    }
                }
                updatePlayerPickerTable(desiredCiv)
            }
            modCheckboxTable.add(checkBox).row()
        }

        add(modCheckboxTable).colspan(2).row()
    }

}