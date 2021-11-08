package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.CityStateType
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
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
    private var modCheckboxes: ModCheckboxTable? = null

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
            modCheckboxes = getModCheckboxes()
            add(modCheckboxes).row()

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
        it.isCityState()
        && (it.cityStateType != CityStateType.Religious || gameParameters.religionEnabled)
        && !it.hasUnique(UniqueType.CityStateDeprecated)
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

    private fun Table.addSelectBox(text: String, values: Collection<String>, initialState: String, onChange: (newValue: String) -> String?) {
        add(text.toLabel()).left()
        val selectBox = TranslatedSelectBox(values, initialState, CameraStageBaseScreen.skin)
        selectBox.isDisabled = locked
        selectBox.onChange { 
            val changedValue = onChange(selectBox.selected.value) 
            if (changedValue != null) selectBox.setSelected(changedValue)
        }
        onChange(selectBox.selected.value)
        add(selectBox).fillX().row()
    }

    private fun Table.addDifficultySelectBox() {
        addSelectBox("{Difficulty}:", ruleset.difficulties.keys, gameParameters.difficulty)
        { gameParameters.difficulty = it; null }
    }

    private fun Table.addBaseRulesetSelectBox() {
        val sortedBaseRulesets = RulesetCache.getSortedBaseRulesets()
        if (sortedBaseRulesets.size < 2) return
        
        addSelectBox(
            "{Base Ruleset}:",
            sortedBaseRulesets,
            gameParameters.baseRuleset
        ) { newBaseRuleset ->
            val previousSelection = gameParameters.baseRuleset
            if (newBaseRuleset == gameParameters.baseRuleset) return@addSelectBox null 
            
            // Check if this mod is well-defined
            val baseRulesetErrors = RulesetCache[newBaseRuleset]!!.checkModLinks()
            if (baseRulesetErrors.isError()) {
                val toastMessage = "The mod you selected is incorrectly defined!".tr() + "\n\n${baseRulesetErrors.getErrorText()}"
                ToastPopup(toastMessage, previousScreen as CameraStageBaseScreen, 5000L)
                return@addSelectBox previousSelection
            }
            
            // If so, add it to the current ruleset
            gameParameters.baseRuleset = newBaseRuleset
            onChooseMod(newBaseRuleset)

            // Check if the ruleset in it's entirety is still well-defined
            val modLinkErrors = ruleset.checkModLinks()
            if (modLinkErrors.isError()) {
                gameParameters.mods.clear()
                reloadRuleset()
                val toastMessage =
                    "This base ruleset is not compatible with the previously selected\nextension mods. They have been disabled.".tr()
                ToastPopup(toastMessage, previousScreen as CameraStageBaseScreen, 5000L)
                
                // _technically_, [modCheckBoxes] can be [null] at this point, 
                // but only if you change the option while the table is still loading, 
                // but the timeframe for that is so small I'm just going to ignore that
                modCheckboxes!!.disableAllCheckboxes()
            } else if (modLinkErrors.isWarnUser()) {
                val toastMessage =
                    "{The mod combination you selected has problems.}\n{You can play it, but don't expect everything to work!}".tr() + 
                    "\n\n${modLinkErrors.getErrorText()}"
                ToastPopup(toastMessage, previousScreen as CameraStageBaseScreen, 5000L)
            }
            
            modCheckboxes!!.setBaseRuleset(newBaseRuleset)
            
            null
        }
    }

    private fun Table.addGameSpeedSelectBox() {
        addSelectBox("{Game Speed}:", GameSpeed.values().map { it.name }, gameParameters.gameSpeed.name)
        { gameParameters.gameSpeed = GameSpeed.valueOf(it); null }
    }

    private fun Table.addEraSelectBox() {
        if (ruleset.technologies.isEmpty()) return // mod with no techs
        val eras = ruleset.eras.keys
        addSelectBox("{Starting Era}:", eras, gameParameters.startingEra)
        { gameParameters.startingEra = it; null }
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
        UncivGame.Current.musicController.setModList(gameParameters.getModsAndBaseRuleset())
    }

    fun getModCheckboxes(isPortrait: Boolean = false): ModCheckboxTable {
        return ModCheckboxTable(gameParameters.mods, gameParameters.baseRuleset, previousScreen as CameraStageBaseScreen, isPortrait) {
            onChooseMod(it)
        }
    }
    
    private fun onChooseMod(mod: String) {
        val activeMods: LinkedHashSet<String> = LinkedHashSet(gameParameters.getModsAndBaseRuleset())
        UncivGame.Current.translations.translationActiveMods = activeMods
        reloadRuleset()
        update()

        var desiredCiv = ""
        if (gameParameters.mods.contains(mod)) {
            val modNations = RulesetCache[mod]?.nations
            if (modNations != null && modNations.size > 0) desiredCiv = modNations.keys.first()

            val music = UncivGame.Current.musicController
            if (!music.chooseTrack(mod, MusicMood.Theme, MusicTrackChooserFlags.setSelectNation) && desiredCiv.isNotEmpty())
                music.chooseTrack(desiredCiv, MusicMood.themeOrPeace, MusicTrackChooserFlags.setSelectNation)
        }

        updatePlayerPickerTable(desiredCiv)
    }
}

