package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.multiplayer.MultiplayerHelpers
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.ExpanderTab
import com.unciv.ui.utils.UncivSlider
import com.unciv.ui.utils.extensions.onChange
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toCheckBox
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton

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
    var keepAdvancedTabOpenForNationsPool = false
    var keepAdvancedTabOpenForCSPool = false

    init {
        getGameOptionsTable()
        background = BaseScreen.skinStrings.getUiBackground("NewGameScreen/GameOptionsTable", tintColor = BaseScreen.skinStrings.skinConfig.clearColor)
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
                if (gameParameters.randomNumberOfPlayers) {
                    addMinPlayersSlider()
                    addMaxPlayersSlider()
                }
                if (gameParameters.randomNumberOfCityStates) {
                    addMinCityStatesSlider()
                    addMaxCityStatesSlider()
                } else {
                    addCityStatesSlider()
                }
            }).colspan(2).fillX().row()
        }).row()
        addVictoryTypeCheckboxes()



        val checkboxTable = Table().apply { defaults().left().pad(2.5f) }
        checkboxTable.addIsOnlineMultiplayerCheckbox()
        if (gameParameters.isOnlineMultiplayer)
            checkboxTable.addAnyoneCanSpectateCheckbox()
        add(checkboxTable).center().row()

        val expander = ExpanderTab("Advanced Settings", startsOutOpened = keepAdvancedTabOpenForNationsPool || keepAdvancedTabOpenForCSPool) {
            it.addNoCityRazingCheckbox()
            it.addNoBarbariansCheckbox()
            it.addRagingBarbariansCheckbox()
            it.addOneCityChallengeCheckbox()
            it.addNuclearWeaponsCheckbox()
            if (UncivGame.Current.settings.enableEspionageOption)
                it.addEnableEspionageCheckbox()
            it.addNoStartBiasCheckbox()
            it.addRandomPlayersCheckbox()
            it.addRandomNrCityStatesCheckbox()
            it.addRandomNationsPoolCheckbox()
            if (gameParameters.enableRandomNationsPool) {
                it.addBlacklistRandomNationsPool()
                it.addNationsSelectTextButton()
            }
            it.addRandomCityStatesPoolCheckbox()
            if (gameParameters.enableRandomCityStatesPool){
                it.addBlacklistRandomCityStatesPool()
                it.addCityStatesSelectTextButton()
            }
        }
        add(expander).pad(10f).padTop(10f).growX().row()


        if (!isPortrait)
            add(modCheckboxes).row()

        pack()
    }

    private fun Table.addCheckbox(text: String, initialState: Boolean, lockable: Boolean = true, onChange: (newValue: Boolean) -> Unit) {
        val checkbox = text.toCheckBox(initialState) { onChange(it) }
        checkbox.isDisabled = lockable && locked
        checkbox.align(Align.left)
        add(checkbox).colspan(2).row()
    }

    private fun Table.addNoCityRazingCheckbox() =
            addCheckbox("No City Razing", gameParameters.noCityRazing)
            { gameParameters.noCityRazing = it }

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

    private fun Table.addRandomNationsPoolCheckbox() =
            addCheckbox("Random nations pool", gameParameters.enableRandomNationsPool) {
                gameParameters.enableRandomNationsPool = it
                keepAdvancedTabOpenForNationsPool = it
                update()
            }

    private fun Table.addRandomCityStatesPoolCheckbox() =
            addCheckbox("Random city states pool", gameParameters.enableRandomCityStatesPool) {
                gameParameters.enableRandomCityStatesPool = it
                keepAdvancedTabOpenForCSPool = it
                update()
            }

    private fun Table.addBlacklistRandomNationsPool() =
            addCheckbox("Blacklist random nations pool", gameParameters.blacklistRandomNationsPool) {
                gameParameters.blacklistRandomNationsPool = it
            }

    private fun Table.addBlacklistRandomCityStatesPool() =
            addCheckbox("Blacklist random city states pool", gameParameters.blacklistRandomCityStatesPool) {
                gameParameters.blacklistRandomCityStatesPool = it
            }

    private fun Table.addNationsSelectTextButton() {
        val button = "Select nations".toTextButton()
        button.onClick {
            val popup = MultiNationSelectPopup(previousScreen, gameParameters, true)
            popup.open()
            popup.update()
        }
        add(button).row()
    }

    private fun Table.addCityStatesSelectTextButton() {
        val button = "Select city states".toTextButton()
        button.onClick {
            val popup = MultiNationSelectPopup(previousScreen, gameParameters, false)
            popup.open()
            popup.update()
        }
        add(button).row()
    }

    private fun numberOfPlayable() = ruleset.nations.values.count {
        it.isMajorCiv()
    }

    private fun numberOfCityStates() = ruleset.nations.values.count {
        it.isCityState()
        && !it.hasUnique(UniqueType.CityStateDeprecated)
    }

    private fun Table.addNoStartBiasCheckbox() =
            addCheckbox("Disable starting bias", gameParameters.noStartBias)
            { gameParameters.noStartBias = it }

    private fun Table.addRandomPlayersCheckbox() =
            addCheckbox("Random number of Civilizations", gameParameters.randomNumberOfPlayers)
            {
                gameParameters.randomNumberOfPlayers = it
                update()
            }

    private fun Table.addRandomNrCityStatesCheckbox() =
            addCheckbox("Random number of City-States", gameParameters.randomNumberOfCityStates)
            {
                gameParameters.randomNumberOfCityStates = it
                update()
            }

    private fun Table.addMinPlayersSlider() {
        val playableAvailable = numberOfPlayable()
        if (playableAvailable == 0) return

        add("{Min number of Civilizations}:".toLabel()).left().expandX()
        val slider = UncivSlider(2f, playableAvailable.toFloat(), 1f, initial = gameParameters.minNumberOfPlayers.toFloat()) {
            gameParameters.minNumberOfPlayers = it.toInt()
        }
        slider.permanentTip = true
        slider.isDisabled = locked
        add(slider).padTop(10f).row()
    }

    private fun Table.addMaxPlayersSlider() {
        val playableAvailable = numberOfPlayable()
        if (playableAvailable == 0) return

        add("{Max number of Civilizations}:".toLabel()).left().expandX()
        val slider = UncivSlider(2f, playableAvailable.toFloat(), 1f, initial = gameParameters.maxNumberOfPlayers.toFloat()) {
            gameParameters.maxNumberOfPlayers = it.toInt()
        }
        slider.permanentTip = true
        slider.isDisabled = locked
        add(slider).padTop(10f).row()
    }

    private fun Table.addMinCityStatesSlider() {
        val cityStatesAvailable = numberOfCityStates()
        if (cityStatesAvailable == 0) return

        add("{Min number of City-States}:".toLabel()).left().expandX()
        val slider = UncivSlider(0f, cityStatesAvailable.toFloat(), 1f, initial = gameParameters.minNumberOfCityStates.toFloat()) {
            gameParameters.minNumberOfCityStates = it.toInt()
        }
        slider.permanentTip = true
        slider.isDisabled = locked
        add(slider).padTop(10f).row()
    }

    private fun Table.addMaxCityStatesSlider() {
        val cityStatesAvailable = numberOfCityStates()
        if (cityStatesAvailable == 0) return

        add("{Max number of City-States}:".toLabel()).left().expandX()
        val slider = UncivSlider(0f, cityStatesAvailable.toFloat(), 1f, initial = gameParameters.maxNumberOfCityStates.toFloat()) {
            gameParameters.maxNumberOfCityStates = it.toInt()
        }
        slider.permanentTip = true
        slider.isDisabled = locked
        add(slider).padTop(10f).row()
    }

    private fun Table.addCityStatesSlider() {
        val cityStatesAvailable = numberOfCityStates()
        if (cityStatesAvailable == 0) return

        add("{Number of City-States}:".toLabel()).left().expandX()
        val slider = UncivSlider(0f, cityStatesAvailable.toFloat(), 1f, initial = gameParameters.numberOfCityStates.toFloat()) {
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
            val modNations = RulesetCache[mod]?.nations?.values?.filter { it.isMajorCiv() }

            if (modNations != null && modNations.any())
                desiredCiv = modNations.random().name

            val music = UncivGame.Current.musicController
            if (!music.chooseTrack(mod, MusicMood.Theme, MusicTrackChooserFlags.setSelectNation) && desiredCiv.isNotEmpty())
                music.chooseTrack(desiredCiv, MusicMood.themeOrPeace, MusicTrackChooserFlags.setSelectNation)
        }

        updatePlayerPickerTable(desiredCiv)
    }
}
