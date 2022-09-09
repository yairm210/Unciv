package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.CityStateType
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.multiplayer.MultiplayerHelpers
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.UncivSlider
import com.unciv.ui.utils.extensions.onChange
import com.unciv.ui.utils.extensions.toCheckBox
import com.unciv.ui.utils.extensions.toLabel

class GameOptionsTable(
    val previousScreen: IPreviousScreen,
    val isPortrait: Boolean = false,
    val updatePlayerPickerTable:(desiredCiv:String)->Unit
) : Table(BaseScreen.skin) {
    var gameParameters = previousScreen.gameSetupInfo.gameParameters
    val ruleset = previousScreen.ruleset
    var locked = false
    var modCheckboxes: ModCheckboxTable? = null
    private set

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

        // We assign this first to make sure addBaseRulesetSelectBox doesn't reference a null object
        modCheckboxes =
            if (isPortrait)
                getModCheckboxes(isPortrait = true)
            else getModCheckboxes()

        add(Table().apply {
            defaults().pad(5f)
            addBaseRulesetSelectBox()
            addDifficultySelectBox()
            addGameSpeedSelectBox()
            addEraSelectBox()
            // align left and right edges with other SelectBoxes but allow independent dropdown width
            add(Table().apply {
                val turnSlider = addMaxTurnsSlider()
                if (turnSlider != null)
                    add(turnSlider).padTop(10f).row()
                addCityStatesSlider()
            }).colspan(2).fillX().row()
        }).row()
        addVictoryTypeCheckboxes()


        val checkboxTable = Table().apply { defaults().left().pad(2.5f) }
        checkboxTable.addNoBarbariansCheckbox()
        checkboxTable.addRagingBarbariansCheckbox()
        checkboxTable.addOneCityChallengeCheckbox()
        checkboxTable.addNuclearWeaponsCheckbox()
        checkboxTable.addIsOnlineMultiplayerCheckbox()
        if (gameParameters.isOnlineMultiplayer)
            checkboxTable.addAnyoneCanSpectateCheckbox()
        if (UncivGame.Current.settings.enableEspionageOption)
            checkboxTable.addEnableEspionageCheckbox()
        checkboxTable.addNoStartBiasCheckbox()
        add(checkboxTable).center().row()

        if (!isPortrait)
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
            addCheckbox("Enable Nuclear Weapons", gameParameters.nuclearWeaponsEnabled)
            { gameParameters.nuclearWeaponsEnabled = it }

    private fun Table.addIsOnlineMultiplayerCheckbox() =
            addCheckbox("Online Multiplayer", gameParameters.isOnlineMultiplayer)
            { shouldUseMultiplayer ->
                gameParameters.isOnlineMultiplayer = shouldUseMultiplayer
                updatePlayerPickerTable("")
                if (shouldUseMultiplayer) {
                    MultiplayerHelpers.showDropboxWarning(previousScreen as BaseScreen)
                }
                update()
            }

    private fun Table.addAnyoneCanSpectateCheckbox() =
            addCheckbox("Allow anyone to spectate", gameParameters.anyoneCanSpectate)
            {
                gameParameters.anyoneCanSpectate = it
            }

    private fun Table.addEnableEspionageCheckbox() =
        addCheckbox("Enable Espionage", gameParameters.espionageEnabled)
        { gameParameters.espionageEnabled = it }


    private fun numberOfCityStates() = ruleset.nations.values.count {
        it.isCityState()
        && !it.hasUnique(UniqueType.CityStateDeprecated)
    }

    private fun Table.addNoStartBiasCheckbox() =
            addCheckbox("Disable starting bias", gameParameters.noStartBias)
            { gameParameters.noStartBias = it }

    private fun Table.addCityStatesSlider() {
        val maxCityStates = numberOfCityStates()
        if (maxCityStates == 0) return

        add("{Number of City-States}:".toLabel()).left().expandX()
        val slider = UncivSlider(0f, maxCityStates.toFloat(), 1f, initial = gameParameters.numberOfCityStates.toFloat()) {
            gameParameters.numberOfCityStates = it.toInt()
        }
        slider.permanentTip = true
        slider.isDisabled = locked
        add(slider).padTop(10f).row()
    }

    private fun Table.addMaxTurnsSlider(): UncivSlider? {
        if (gameParameters.victoryTypes.none { ruleset.victories[it]?.enablesMaxTurns() == true })
            return null

        add("{Max Turns}:".toLabel()).left().expandX()
        val slider = UncivSlider(250f, 1500f, 50f, initial = gameParameters.maxTurns.toFloat()) {
            gameParameters.maxTurns = it.toInt()
        }
        slider.permanentTip = true
        slider.isDisabled = locked
        val snapValues = floatArrayOf(250f,300f,350f,400f,450f,500f,550f,600f,650f,700f,750f,800f,900f,1000f,1250f,1500f)
        slider.setSnapToValues(snapValues, 250f)
        return slider
    }

    private fun Table.addSelectBox(text: String, values: Collection<String>, initialState: String, onChange: (newValue: String) -> String?) {
        add(text.toLabel()).left()
        val selectBox = TranslatedSelectBox(values, initialState, BaseScreen.skin)
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
                ToastPopup(toastMessage, previousScreen as BaseScreen, 5000L)
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
                ToastPopup(toastMessage, previousScreen as BaseScreen, 5000L)

                modCheckboxes!!.disableAllCheckboxes()
            } else if (modLinkErrors.isWarnUser()) {
                val toastMessage =
                    "{The mod combination you selected has problems.}\n{You can play it, but don't expect everything to work!}".tr() +
                    "\n\n${modLinkErrors.getErrorText()}"
                ToastPopup(toastMessage, previousScreen as BaseScreen, 5000L)
            }

            modCheckboxes!!.setBaseRuleset(newBaseRuleset)

            null
        }
    }

    private fun Table.addGameSpeedSelectBox() {
        addSelectBox("{Game Speed}:", ruleset.speeds.values.map { it.name }, gameParameters.speed)
        { gameParameters.speed = it; null }
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
        val victoryConditionsTable = Table().apply { defaults().pad(5f) }
        for ((i, victoryType) in ruleset.victories.values.withIndex()) {
            val victoryCheckbox = victoryType.name.toCheckBox(gameParameters.victoryTypes.contains(victoryType.name)) {
                // If the checkbox is checked, adds the victoryTypes else remove it
                if (it) {
                    gameParameters.victoryTypes.add(victoryType.name)
                } else {
                    gameParameters.victoryTypes.remove(victoryType.name)
                }
                // show or hide the max turns select box
                if (victoryType.enablesMaxTurns())
                    update()
            }
            victoryCheckbox.name = victoryType.name
            victoryCheckbox.isDisabled = locked
            victoryConditionsTable.add(victoryCheckbox).left()
            if ((i + 1) % 2 == 0) victoryConditionsTable.row()
        }
        add(victoryConditionsTable).colspan(2).row()
    }

    fun reloadRuleset() {
        ruleset.clear()
        val newRuleset = RulesetCache.getComplexRuleset(gameParameters)
        ruleset.add(newRuleset)
        ruleset.mods += gameParameters.baseRuleset
        ruleset.mods += gameParameters.mods
        ruleset.modOptions = newRuleset.modOptions

        ImageGetter.setNewRuleset(ruleset)
        UncivGame.Current.musicController.setModList(gameParameters.getModsAndBaseRuleset())
    }

    private fun getModCheckboxes(isPortrait: Boolean = false): ModCheckboxTable {
        return ModCheckboxTable(gameParameters.mods, gameParameters.baseRuleset, previousScreen as BaseScreen, isPortrait) {
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

