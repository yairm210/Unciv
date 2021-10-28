package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.CityStateType
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.utils.*

class GameOptionsTable(
    val previousScreen: IPreviousScreen,
    val withoutMods: Boolean = false,
    val updatePlayerPickerTable:(desiredCiv:String)->Unit
) : Table(CameraStageBaseScreen.skin) {
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
        val cityStateSlider: UncivSlider?
        top()
        defaults().pad(5f)

        add(Table().apply {
            defaults().pad(5f)
            addBaseRulesetSelectBox()
            addDifficultySelectBox()
            addGameSpeedSelectBox()
            addEraSelectBox()
            // align left and right edges with other SelectBoxes but allow independent dropdown width
            add(Table().apply {
                cityStateSlider = addCityStatesSlider()
            }).colspan(2).fillX().row()
        }).row()
        addVictoryTypeCheckboxes()

        val checkboxTable = Table().apply { defaults().left().pad(2.5f) }
        checkboxTable.addNoBarbariansCheckbox()
        checkboxTable.addRagingBarbariansCheckbox()
        checkboxTable.addOneCityChallengeCheckbox()
        checkboxTable.addNuclearWeaponsCheckbox()
        checkboxTable.addIsOnlineMultiplayerCheckbox()
        checkboxTable.addReligionCheckbox(cityStateSlider)
        add(checkboxTable).center().row()

        if (!withoutMods)
            add(getModCheckboxes()).row()

        pack()
    }

    private fun Table.addCheckbox(text: String, initialState: Boolean, lockable: Boolean = true, onChange: (newValue: Boolean) -> Unit) {
        val checkbox = text.toCheckBox(initialState) { onChange(it) }
        checkbox.isDisabled = lockable && locked
        add(checkbox).colspan(2).row()
    }

    private fun Table.addNoBarbariansCheckbox() =
            addCheckbox("No Barbarians", gameParameters.noBarbarians)
            { gameParameters.noBarbarians = it }

    private fun Table.addRagingBarbariansCheckbox() =
        addCheckbox("Raging Barbarians", gameParameters.ragingBarbarians)
        { gameParameters.ragingBarbarians = it }

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

    private fun numberOfCityStates() = ruleset.nations.values.count {
        it.isCityState() &&
                (it.cityStateType != CityStateType.Religious || gameParameters.religionEnabled) &&
                !it.hasUnique(UniqueType.CityStateDeprecated)
    }

    private fun Table.addReligionCheckbox(cityStateSlider: UncivSlider?) =
        addCheckbox("Enable Religion", gameParameters.religionEnabled) {
            gameParameters.religionEnabled = it
            cityStateSlider?.run { setRange(0f, numberOfCityStates().toFloat()) }
        }

    private fun Table.addCityStatesSlider(): UncivSlider? {
        val maxCityStates = numberOfCityStates()
        if (maxCityStates == 0) return null

        add("{Number of City-States}:".toLabel()).left().expandX()
        val slider = UncivSlider(0f, maxCityStates.toFloat(), 1f) {
            gameParameters.numberOfCityStates = it.toInt()
        }
        slider.permanentTip = true
        slider.isDisabled = locked
        add(slider).padTop(10f).row()
        slider.value = gameParameters.numberOfCityStates.toFloat()
        return slider
    }

    private fun Table.addSelectBox(text: String, values: Collection<String>, initialState: String, onChange: (newValue: String) -> Unit) {
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
        val baseRulesets = 
            RulesetCache.values
                .filter { it.modOptions.isBaseRuleset }
                .map { it.name }
                .distinct()
        if (baseRulesets.size < 2) return
        
        // We sort the base rulesets such that the ones unciv provides are on the top,
        // and the rest is alphabetically ordered.
        val sortedBaseRulesets = baseRulesets.sortedWith(
            compareBy(
                { ruleset ->
                    BaseRuleset.values()
                        .firstOrNull { br -> br.fullName == ruleset }?.ordinal
                        ?: BaseRuleset.values().size
                },
                { it }
            )
        )
        addSelectBox(
            "{Base Ruleset}:",
            sortedBaseRulesets,
            gameParameters.baseRuleset
        ) { modToAdd ->
            if (modToAdd == gameParameters.baseRuleset) return@addSelectBox
            gameParameters.baseRuleset = modToAdd
            reloadRuleset()
            update()
        }
    }

    private fun Table.addGameSpeedSelectBox() {
        addSelectBox("{Game Speed}:", GameSpeed.values().map { it.name }, gameParameters.gameSpeed.name)
        { gameParameters.gameSpeed = GameSpeed.valueOf(it) }
    }

    private fun Table.addEraSelectBox() {
        if (ruleset.technologies.isEmpty()) return // mod with no techs
        val eras = ruleset.eras.keys
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
            val victoryCheckbox = victoryType.name.toCheckBox(gameParameters.victoryTypes.contains(victoryType)) {
                // If the checkbox is checked, adds the victoryTypes else remove it
                if (it) {
                    gameParameters.victoryTypes.add(victoryType)
                } else {
                    gameParameters.victoryTypes.remove(victoryType)
                }
            }
            victoryCheckbox.name = victoryType.name
            victoryCheckbox.isDisabled = locked
            victoryConditionsTable.add(victoryCheckbox).left()
            if (++i % 2 == 0) victoryConditionsTable.row()
        }
        add(victoryConditionsTable).colspan(2).row()
    }

    fun reloadRuleset() {
        ruleset.clear()
        val newRuleset = RulesetCache.getComplexRuleset(gameParameters.mods, gameParameters.baseRuleset)
        ruleset.add(newRuleset)
        ruleset.mods += gameParameters.baseRuleset
        ruleset.mods += gameParameters.mods
        ruleset.modOptions = newRuleset.modOptions

        ImageGetter.setNewRuleset(ruleset)
        UncivGame.Current.musicController.setModList(gameParameters.mods.toHashSet().apply { add(gameParameters.baseRuleset) })
    }

    fun getModCheckboxes(isPortrait: Boolean = false): Table {
        return ModCheckboxTable(gameParameters.mods, previousScreen as CameraStageBaseScreen, isPortrait) {
            val activeMods: LinkedHashSet<String> = LinkedHashSet(listOf(*gameParameters.mods.toTypedArray(), gameParameters.baseRuleset)) 
            UncivGame.Current.translations.translationActiveMods = activeMods
            reloadRuleset()
            update()

            var desiredCiv = ""
            if (gameParameters.mods.contains(it)) {
                val modNations = RulesetCache[it]?.nations
                if (modNations != null && modNations.size > 0) desiredCiv = modNations.keys.first()

                val music = UncivGame.Current.musicController
                if (!music.chooseTrack(it, MusicMood.Theme, MusicTrackChooserFlags.setSelectNation) && desiredCiv.isNotEmpty())
                    music.chooseTrack(desiredCiv, MusicMood.themeOrPeace, MusicTrackChooserFlags.setSelectNation)
            }

            updatePlayerPickerTable(desiredCiv)
        }
    }

}

