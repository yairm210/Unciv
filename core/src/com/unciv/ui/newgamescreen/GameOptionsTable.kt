package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.translations.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onChange
import com.unciv.ui.utils.toLabel

class GameOptionsTable(val previousScreen: IPreviousScreen, val updatePlayerPickerTable:(desiredCiv:String)->Unit)
    : Table(CameraStageBaseScreen.skin) {
    var gameParameters = previousScreen.gameSetupInfo.gameParameters
    val ruleset = previousScreen.ruleset
    var locked = false

    init {
        getGameOptionsTable()
    }

    fun update() {
        clear()
        getGameOptionsTable()
    }

    private fun getGameOptionsTable() {
        top()
        defaults().pad(5f)

        add("Game Options".toLabel(fontSize = 24)).padTop(0f).padBottom(20f).colspan(2).row()
        add(Table().apply {
            defaults().pad(5f)
            //addBaseRulesetSelectBox()
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

    private fun Table.addCheckbox(text: String, initialState: Boolean, lockable: Boolean = true, onChange: (newValue: Boolean) -> Unit) {
        val checkbox = CheckBox(text.tr(), CameraStageBaseScreen.skin)
        checkbox.isChecked = initialState
        checkbox.isDisabled = lockable && locked
        checkbox.onChange { onChange(checkbox.isChecked) }
        add(checkbox).colspan(2).left().row()
    }

    private fun Table.addBarbariansCheckbox() =
            addCheckbox("No Barbarians", gameParameters.noBarbarians)
            { gameParameters.noBarbarians = it }

    private fun Table.addOneCityChallengeCheckbox() =
            addCheckbox("One City Challenge", gameParameters.oneCityChallenge)
            { gameParameters.oneCityChallenge = it }

    private fun Table.addNuclearWeaponsCheckbox() =
            addCheckbox("Enable nuclear weapons", gameParameters.nuclearWeaponsEnabled)
            { gameParameters.nuclearWeaponsEnabled = it }


    private fun Table.addIsOnlineMultiplayerCheckbox() =
            addCheckbox("Online Multiplayer", gameParameters.isOnlineMultiplayer)
            {
                gameParameters.isOnlineMultiplayer = it
                updatePlayerPickerTable("")
            }

    private fun addCityStatesSelectBox() {
        add("{Number of City-States}:".toLabel())
        val cityStatesSelectBox = SelectBox<Int>(CameraStageBaseScreen.skin)

        val numberOfCityStates = ruleset.nations.filter { it.value.isCityState() }.size

        val cityStatesArray = Array<Int>(numberOfCityStates + 1)
        (0..numberOfCityStates).forEach { cityStatesArray.add(it) }

        cityStatesSelectBox.items = cityStatesArray
        cityStatesSelectBox.selected = gameParameters.numberOfCityStates
        add(cityStatesSelectBox).width(50f).row()
        cityStatesSelectBox.isDisabled = locked
        cityStatesSelectBox.onChange {
            gameParameters.numberOfCityStates = cityStatesSelectBox.selected
        }
    }

    fun Table.addSelectBox(text: String, values: Collection<String>, initialState: String, onChange: (newValue: String) -> Unit) {
        add(text.toLabel()).left()
        val selectBox = TranslatedSelectBox(values, initialState, CameraStageBaseScreen.skin)
        selectBox.isDisabled = locked
        selectBox.onChange { onChange(selectBox.selected.value) }
        onChange(selectBox.selected.value)
        add(selectBox).fillX().row()
    }

    private fun Table.addDifficultySelectBox() {
        addSelectBox("{Difficulty}:", ruleset.difficulties.keys, gameParameters.difficulty)
        { gameParameters.difficulty = it }
    }

    private fun Table.addBaseRulesetSelectBox() {
        addSelectBox("{Base Ruleset}:", BaseRuleset.values().map { it.fullName }, gameParameters.baseRuleset.fullName)
        {
            gameParameters.baseRuleset = BaseRuleset.values().first { br -> br.fullName == it }
            reloadRuleset()
        }
    }

    private fun Table.addGameSpeedSelectBox() {
        addSelectBox("{Game Speed}:", GameSpeed.values().map { it.name }, gameParameters.gameSpeed.name)
        { gameParameters.gameSpeed = GameSpeed.valueOf(it) }
    }

    private fun Table.addEraSelectBox() {
        if (ruleset.technologies.isEmpty()) return // mod with no techs
        val eras = ruleset.technologies.values.filter { !it.uniques.contains("Starting tech") }.map { it.era() }.distinct()
        addSelectBox("{Starting Era}:", eras, gameParameters.startingEra)
        { gameParameters.startingEra = it }
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
            victoryCheckbox.isChecked = gameParameters.victoryTypes.contains(victoryType)
            victoryCheckbox.isDisabled = locked
            victoryCheckbox.onChange {
                // If the checkbox is checked, adds the victoryTypes else remove it
                if (victoryCheckbox.isChecked) {
                    gameParameters.victoryTypes.add(victoryType)
                } else {
                    gameParameters.victoryTypes.remove(victoryType)
                }
            }
            victoryConditionsTable.add(victoryCheckbox).left()
            if (++i % 2 == 0) victoryConditionsTable.row()
        }
        add(victoryConditionsTable).colspan(2).row()
    }

    fun reloadRuleset() {
        ruleset.clear()
        val newRuleset = RulesetCache.getComplexRuleset(gameParameters.mods)
        ruleset.add(newRuleset)
        ruleset.mods += gameParameters.mods
        ruleset.modOptions = newRuleset.modOptions

        ImageGetter.ruleset = ruleset
        ImageGetter.reload()
    }

    fun Table.addModCheckboxes() {
        val table = ModCheckboxTable(gameParameters.mods, previousScreen as CameraStageBaseScreen) {
            reloadRuleset()
            update()

            var desiredCiv = ""
            if (gameParameters.mods.contains(it)) {
                val modNations = RulesetCache[it]?.nations
                if (modNations != null && modNations.size > 0) {
                    desiredCiv = modNations.keys.first()
                }
            }

            updatePlayerPickerTable(desiredCiv)
        }
        add(table).row()
    }

}

