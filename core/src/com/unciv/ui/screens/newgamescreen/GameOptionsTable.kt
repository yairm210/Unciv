package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.Player
import com.unciv.platform.PlatformCapabilities
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.components.extensions.getCloseButton
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toImageButton
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.components.widgets.TranslatedSelectBox
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.multiplayerscreens.MultiplayerHelpers

class GameOptionsTable(
    private val previousScreen: IPreviousScreen,
    private val isPortrait: Boolean = false,
    private val updatePlayerPickerTable: (desiredCiv: String) -> Unit,
    private val updatePlayerPickerRandomLabel: () -> Unit
) : Table(BaseScreen.skin) {
    private var gameParameters = previousScreen.gameSetupInfo.gameParameters
    private var ruleset = previousScreen.ruleset
    internal var locked = false
    private var isUpdating = false

    private var baseRulesetHash = gameParameters.baseRuleset.hashCode()

    /** Holds the UI for the Extension Mods
     *
     *  Attention: This Widget is a little tricky due to the UI changes to support portrait mode:
     *  *  With `isPortrait==false`, this Table will **contain** `modCheckboxes`
     *  *  With `isPortrait==true`, this Table will **only initialize** `modCheckboxes` and [NewGameScreen] will fetch and place it.
     *
     *  The second reason this is public: [NewGameScreen] accesses [ModCheckboxTable.savedModcheckResult] for display.
     */
    internal val modCheckboxes = getModCheckboxes(isPortrait = isPortrait)

    // Remember this so we can unselect it when the pool dialog returns an empty pool
    private var randomNationsPoolCheckbox: CheckBox? = null
    // Allow resetting base ruleset from outside
    private var baseRulesetSelectBox: TranslatedSelectBox? = null

    init {
        var initStep = "start"
        try {
            initStep = "set background/top/defaults"
            background = BaseScreen.skinStrings.getUiBackground("NewGameScreen/GameOptionsTable", tintColor = BaseScreen.skinStrings.skinConfig.clearColor)
            top()
            defaults().pad(5f)
            initStep = "update"
            update()
        } catch (ex: Exception) {
            throw IllegalStateException("GameOptionsTable init failed at step: $initStep", ex)
        }
    }

    fun update() {
        if (isUpdating) return
        isUpdating = true
        var updateStep = "start"
        try {
        updateStep = "clear"
        clear()

        // Mods may have changed (e.g. custom map selection)
        modCheckboxes.updateSelection()
        val newBaseRulesetHash = gameParameters.baseRuleset.hashCode()
        if (newBaseRulesetHash != baseRulesetHash) {
            baseRulesetHash = newBaseRulesetHash
            modCheckboxes.setBaseRuleset(gameParameters.baseRuleset)
        }

        updateStep = "add basic select tables"
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
                    addMinMaxPlayersSliders()
                }
                if (gameParameters.randomNumberOfCityStates) {
                    addMinMaxCityStatesSliders()
                } else {
                    addCityStatesSlider()
                }
            }).colspan(2).fillX().row()
        }).row()
        updateStep = "add victory checkboxes"
        addVictoryTypeCheckboxes()

        updateStep = "build checkbox/select tables"
        val checkboxTable = Table().apply { defaults().left().pad(2.5f) }
        val selectBoxTable = Table()
        if (PlatformCapabilities.current.onlineMultiplayer) {
            checkboxTable.addIsOnlineMultiplayerCheckbox()
            if (gameParameters.isOnlineMultiplayer){
                checkboxTable.addAnyoneCanSpectateCheckbox()
                selectBoxTable.addDurationSelectBox("Time until skip turn:", { gameParameters.minutesUntilSkipTurn }, { gameParameters.minutesUntilSkipTurn = it }, 1, 0, 0)
                selectBoxTable.addDurationSelectBox("Total time to play:", { gameParameters.minutesUntilForceResign }, { gameParameters.minutesUntilForceResign = it }, 3, 0, 0)
                selectBoxTable.addDurationSelectBox("Time recovered per turn:", { gameParameters.minutesRecoveredPerTurn }, { gameParameters.minutesRecoveredPerTurn = it }, 3, 0, 0)
            }
        } else {
            gameParameters.isOnlineMultiplayer = false
        }
        updateStep = "attach checkbox/select tables"
        add(checkboxTable).center().row()
        add(selectBoxTable).center().row()

        updateStep = "advanced settings expander"
        val fillAdvancedSettings: (Table) -> Unit = {
            it.defaults().pad(5f, 0f)
            it.addNoCityRazingCheckbox()
            it.addNoBarbariansCheckbox()
            it.addRagingBarbariansCheckbox()
            it.addOneCityChallengeCheckbox()
            it.addNuclearWeaponsCheckbox()
            it.addEnableEspionageCheckbox()
            it.addNoStartBiasCheckbox()
            it.addRandomPlayersCheckbox()
            it.addRandomCityStatesCheckbox()
            it.addRandomNationsPoolCheckbox()
            if (gameParameters.enableRandomNationsPool) {
                it.addNationsSelectTextButton()
            }
        }
        if (PlatformCapabilities.current.backgroundThreadPools) {
            val expander = ExpanderTab(
                "Advanced Settings",
                startsOutOpened = gameParameters.enableRandomNationsPool,
                persistenceID = "GameOptionsTable.Advanced",
                initContent = fillAdvancedSettings
            )
            add(expander).pad(10f).row()
        } else {
            val advancedTable = Table()
            advancedTable.add("Advanced Settings".toLabel()).left().row()
            fillAdvancedSettings(advancedTable)
            add(advancedTable).pad(10f).row()
        }

        updateStep = "mods section"
        if (!isPortrait)
            add(modCheckboxes).padTop(0f).row()

        updateStep = "pack"
        pack()
        } catch (ex: Exception) {
            throw IllegalStateException("GameOptionsTable update failed at step: $updateStep", ex)
        } finally {
            isUpdating = false
        }
    }

    private fun Table.addCheckbox(
        text: String,
        initialState: Boolean,
        lockable: Boolean = true,
        onChange: (newValue: Boolean) -> Unit
    ): CheckBox {
        val checkbox = text.toCheckBox(initialState) { onChange(it) }
        checkbox.isDisabled = lockable && locked
        checkbox.align(Align.left)
        add(checkbox).colspan(2).row()
        return checkbox
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
            addCheckbox("Online Multiplayer", gameParameters.isOnlineMultiplayer, lockable = false)
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

    private fun Table.addRandomNationsPoolCheckbox() {
        randomNationsPoolCheckbox = addCheckbox(
            "Set available nations for random pool",
            gameParameters.enableRandomNationsPool
        ) {
            gameParameters.enableRandomNationsPool = it
            update()  // To show the button opening the chooser popup
        }
    }

    private fun Table.addNationsSelectTextButton() {
        val button = "Select nations".toTextButton()
        button.onClick {
            val popup = RandomNationPickerPopup(previousScreen, gameParameters) {
                if (gameParameters.randomNationsPool.isEmpty()) {
                    gameParameters.enableRandomNationsPool = false
                    randomNationsPoolCheckbox?.isChecked = false
                }
            }
            popup.open()
            popup.update()
        }
        add(button)
    }

    private fun numberOfMajorCivs() = ruleset.nations.values.count {
        it.isMajorCiv
    }

    private fun numberOfCityStates() = ruleset.nations.values.count {
        it.isCityState && !it.hasUnique(UniqueType.WillNotBeChosenForNewGames)
    }

    private fun Table.addNoStartBiasCheckbox() =
            addCheckbox("Disable starting bias", gameParameters.noStartBias)
            { gameParameters.noStartBias = it }

    private fun Table.addRandomPlayersCheckbox() =
            addCheckbox("Random number of Civilizations", gameParameters.randomNumberOfPlayers)
            { newRandomNumberOfPlayers ->
                gameParameters.randomNumberOfPlayers = newRandomNumberOfPlayers
                if (newRandomNumberOfPlayers) {
                    // remove all random AI from player picker
                    gameParameters.players = gameParameters.players.asSequence()
                        .filterNot { it.playerType == PlayerType.AI && it.chosenCiv == Constants.random }
                        .toCollection(ArrayList(gameParameters.players.size))
                    updatePlayerPickerTable("")
                } else {
                    // Fill up player picker with random AI until previously active min reached
                    val additionalRandom = gameParameters.minNumberOfPlayers - gameParameters.players.size
                    if (additionalRandom > 0) {
                        repeat(additionalRandom) {
                            gameParameters.players.add(Player(Constants.random))
                        }
                        updatePlayerPickerTable("")
                    }
                }
                update()  // To see the new sliders
            }

    private fun Table.addRandomCityStatesCheckbox() =
            addCheckbox("Random number of City-States", gameParameters.randomNumberOfCityStates)
            {
                gameParameters.run {
                    randomNumberOfCityStates = it
                    if (it) {
                        if (numberOfCityStates > maxNumberOfCityStates)
                            maxNumberOfCityStates = numberOfCityStates
                        if (numberOfCityStates < minNumberOfCityStates)
                            minNumberOfCityStates = numberOfCityStates
                    } else {
                        if (numberOfCityStates > maxNumberOfCityStates)
                            numberOfCityStates = maxNumberOfCityStates
                        if (numberOfCityStates < minNumberOfCityStates)
                            numberOfCityStates = minNumberOfCityStates
                    }
                }
                update()  // To see the changed sliders
            }

    private fun Table.addLinkedMinMaxSliders(
        minValue: Int, maxValue: Int,
        minText: String, maxText: String,
        minGetter: () -> Int,
        minSetter: (Int) -> Unit,
        maxGetter: () -> Int,
        maxSetter: (Int) -> Unit,
        onChangeCallback: (() -> Unit)? = null
    ) {
        if (maxValue < minValue) return

        lateinit var maxSlider: UncivSlider  // lateinit safe because the closure won't use it until the user operates a slider
        val minSlider = UncivSlider(minValue.toFloat(), maxValue.toFloat(), 1f, initial = minGetter().toFloat()) {
            val newMin = it.toInt()
            minSetter(newMin)
            if (newMin > maxSlider.value.toInt()) {
                maxSlider.value = it
                maxSetter(newMin)
            }
            onChangeCallback?.invoke()
        }
        minSlider.isDisabled = locked
        maxSlider = UncivSlider(minValue.toFloat(), maxValue.toFloat(), 1f, initial = maxGetter().toFloat()) {
            val newMax = it.toInt()
            maxSetter(newMax)
            if (newMax < minSlider.value.toInt()) {
                minSlider.value = it
                minSetter(newMax)
            }
            onChangeCallback?.invoke()
        }
        maxSlider.isDisabled = locked

        add(minText.toLabel()).left().expandX()
        add(minSlider).padTop(10f).row()
        add(maxText.toLabel()).left().expandX()
        add(maxSlider).padTop(10f).row()
    }

    private fun Table.addMinMaxPlayersSliders() {
        addLinkedMinMaxSliders(2, numberOfMajorCivs(),
            "{Min number of Civilizations}:", "{Max number of Civilizations}:",
            { gameParameters.minNumberOfPlayers }, { gameParameters.minNumberOfPlayers = it },
            { gameParameters.maxNumberOfPlayers }, { gameParameters.maxNumberOfPlayers = it },
            updatePlayerPickerRandomLabel
        )
    }

    private fun Table.addMinMaxCityStatesSliders() {
        addLinkedMinMaxSliders( 0, numberOfCityStates(),
            "{Min number of City-States}:", "{Max number of City-States}:",
            { gameParameters.minNumberOfCityStates }, { gameParameters.minNumberOfCityStates = it },
            { gameParameters.maxNumberOfCityStates }, { gameParameters.maxNumberOfCityStates = it }
        )
    }

    private fun Table.addCityStatesSlider() {
        val cityStatesAvailable = numberOfCityStates()
        if (cityStatesAvailable == 0) return

        add("{City-States}:".toLabel()).left().expandX()
        val slider = UncivSlider(0f, cityStatesAvailable.toFloat(), 1f, initial = gameParameters.numberOfCityStates.toFloat()) {
            gameParameters.numberOfCityStates = it.toInt()
        }
        slider.isDisabled = locked
        add(slider).padTop(10f).row()
    }

    private fun Table.addMaxTurnsSlider(): UncivSlider? {
        if (gameParameters.victoryTypes.none { ruleset.victories[it]?.enablesMaxTurns() == true })
            return null

        add("{Max Turns}:".toLabel()).left().expandX()
        val slider = UncivSlider(100f, 1500f, 5f, initial = gameParameters.maxTurns.toFloat()) {
            gameParameters.maxTurns = it.toInt()
        }
        slider.isDisabled = locked
        val snapValues = floatArrayOf(100f,150f,200f,250f,300f,350f,400f,450f,500f,550f,600f,650f,700f,750f,800f,900f,1000f,1250f,1500f)
        slider.setSnapToValues(threshold = 125f, *snapValues)
        return slider
    }

    private fun Table.addSelectBox(text: String, values: Collection<String>, initialState: String, onChange: (newValue: String) -> String?): TranslatedSelectBox {
        add(text.toLabel(hideIcons = true)).left()
        val selectBox = TranslatedSelectBox(values, initialState)
        selectBox.isDisabled = locked
        selectBox.onChange {
            val changedValue = onChange(selectBox.selected.value)
            if (changedValue != null) selectBox.setSelected(changedValue)
        }
        onChange(selectBox.selected.value)
        add(selectBox).fillX().row()
        return selectBox
    }

    private fun Table.addDifficultySelectBox() {
        addSelectBox("{Difficulty}:", ruleset.difficulties.keys, gameParameters.difficulty)
        { gameParameters.difficulty = it; null }
    }

    private fun Table.addBaseRulesetSelectBox() {
        fun onBaseRulesetSelected(newBaseRuleset: String): String? {
            val previousSelection = gameParameters.baseRuleset
            if (newBaseRuleset == previousSelection) return null

            // Check if this mod is well-defined
            val baseRulesetErrors = RulesetCache[newBaseRuleset]!!.getErrorList()
            if (baseRulesetErrors.isError()) {
                baseRulesetErrors.showWarnOrErrorToast(previousScreen as BaseScreen)
                return previousSelection
            }

            // If so, add it to the current ruleset
            gameParameters.baseRuleset = newBaseRuleset
            modCheckboxes.setBaseRuleset(newBaseRuleset)  // Treats declared incompatibility
            onChooseMod(newBaseRuleset)

            // Check if the ruleset in its entirety is still well-defined
            val modLinkErrors = ruleset.getErrorList()
            if (modLinkErrors.isError()) {
                modCheckboxes.disableAllCheckboxes()  // also clears gameParameters.mods
                reloadRuleset()
            }
            modLinkErrors.showWarnOrErrorToast(previousScreen as BaseScreen)

            return null
        }

        val sortedBaseRulesets = RulesetCache.getSortedBaseRulesets()
        if (sortedBaseRulesets.size < 2) return
        baseRulesetSelectBox = addSelectBox("{Base Ruleset}:", sortedBaseRulesets, gameParameters.baseRuleset, ::onBaseRulesetSelected)
    }

    private fun Table.addGameSpeedSelectBox() {
        addSelectBox("{Game Speed}:", ruleset.speeds.values.map { it.name }, gameParameters.speed)
        { gameParameters.speed = it; null }
    }

    private fun Table.addEraSelectBox() {
        if (ruleset.eras.isEmpty()) return // mod with no techs
        if (ruleset.modOptions.hasUnique(UniqueType.CanOnlyStartFromStartingEra)){
            gameParameters.startingEra = ruleset.eras.keys.first()
            return
        }
        val eras = ruleset.eras.keys
        addSelectBox("{Starting Era}:", eras, gameParameters.startingEra)
        { gameParameters.startingEra = it; null }
    }

    private fun Table.addDurationSelectBox(
        title: String,
        getter: () -> Int,
        setter: (Int) -> Unit,
        defaultDayValue: Int,
        defaultHourValue: Int,
        defaultMinuteValue: Int
    ) {
        add(title.toLabel(hideIcons = true)).right()

        val selector = DurationSelector(getter, setter, defaultDayValue, defaultHourValue, defaultMinuteValue)

        add(selector.dayBox)
        add(selector.hourBox)
        add(selector.minuteBox).row()
    }
    private class DurationSelector(
        private val getter: () -> Int,
        private val setter: (Int) -> Unit,
        private val defaultDayValue: Int,
        private val defaultHourValue: Int,
        private val defaultMinuteValue: Int,
        private val dayValues: Array<Int> = arrayOf(0,1,2,3,4,5,6,7,8,9,10,11),
        private val hourValues: Array<Int> = arrayOf(0,1,2,3,4,5,6,8,10,12,16,20),
        private val minuteValues: Array<Int> = arrayOf(0,3,5,10,15,20,25,30,35,40,45,50)
        
    ) {
        val dayBox: SelectBox<String> = createTimeCell(dayValues, defaultDayValue, "d")
        val hourBox: SelectBox<String> = createTimeCell(hourValues, defaultHourValue, "h")
        val minuteBox: SelectBox<String> = createTimeCell(minuteValues, defaultMinuteValue, "m")
        val boxes: List<SelectBox<String>> = listOf(dayBox, hourBox, minuteBox)

        init { for (box in boxes) {box.onChange {preventNullTime(); updateGameParameter()}} }

        fun preventNullTime() {
            if (dayBox.selected == "0d" && hourBox.selected == "0h" && minuteBox.selected == "0m") {
                minuteBox.selected ="3m"
            }
        }
            
        fun updateGameParameter() {
            val value = dayValues[dayBox.selectedIndex] * 24 * 60 +
                hourValues[hourBox.selectedIndex] * 60 +
                minuteValues[minuteBox.selectedIndex]

            setter(value)
        }
        
        fun createTimeCell(intValues: Array<Int>, initialValue: Int, suffix: String): SelectBox<String> {
            val timeBox = SelectBox<String>(BaseScreen.skin)
            val stringValues =  Array(intValues.size) { i -> "${intValues[i]}" + suffix }
            timeBox.setItems(*stringValues)
            timeBox.selected = "$initialValue" + suffix
            return timeBox
        }
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

    fun updateRuleset(ruleset: Ruleset) {
        this.ruleset = ruleset
        gameParameters.acceptedModCheckErrors = ""
        modCheckboxes.updateSelection()
        modCheckboxes.setBaseRuleset(gameParameters.baseRuleset)
    }

    fun resetRuleset() {
        val rulesetName = BaseRuleset.Civ_V_GnK.fullName
        gameParameters.baseRuleset = rulesetName
        modCheckboxes.setBaseRuleset(rulesetName)
        modCheckboxes.disableAllCheckboxes()
        baseRulesetSelectBox?.setSelected(rulesetName)
        reloadRuleset()
    }

    private fun reloadRuleset() {
        ruleset.clear()
        val newRuleset = RulesetCache.getComplexRuleset(gameParameters)
        ruleset.add(newRuleset)
        ruleset.mods += gameParameters.baseRuleset
        ruleset.mods += gameParameters.mods
        ruleset.modOptions = newRuleset.modOptions
        gameParameters.acceptedModCheckErrors = ""

        ImageGetter.setNewRuleset(ruleset)
        UncivGame.Current.musicController.setModList(gameParameters.getModsAndBaseRuleset())
    }

    private fun getModCheckboxes(isPortrait: Boolean = false): ModCheckboxTable {
        return ModCheckboxTable(gameParameters.mods, gameParameters.baseRuleset, previousScreen as BaseScreen, isPortrait) {
            onChooseMod(it)
        }
    }

    private fun onChooseMod(mod: String) {
        val activeMods = gameParameters.getModsAndBaseRuleset()
        UncivGame.Current.translations.translationActiveMods = activeMods
        reloadRuleset()
        update()

        var desiredCiv = ""
        if (gameParameters.mods.contains(mod)) {
            val modNations = RulesetCache[mod]?.nations?.values?.filter { it.isMajorCiv }

            if (modNations != null && modNations.any())
                desiredCiv = modNations.random().name

            val music = UncivGame.Current.musicController
            if (!music.chooseTrack(mod, MusicMood.Theme, MusicTrackChooserFlags.setSelectNation) && desiredCiv.isNotEmpty())
                music.chooseTrack(desiredCiv, MusicMood.themeOrPeace, MusicTrackChooserFlags.setSelectNation)
        }

        updatePlayerPickerTable(desiredCiv)
    }

    fun changeGameParameters(newGameParameters: GameParameters) {
        gameParameters = newGameParameters
        modCheckboxes.changeGameParameters(newGameParameters)
    }
}

private class RandomNationPickerPopup(
    previousScreen: IPreviousScreen,
    private val gameParameters: GameParameters,
    private val onExit: () -> Unit
) : Popup(previousScreen as BaseScreen) {
    companion object {
        // These are used for the Close/OK buttons in the lower left/right corners:
        const val buttonsCircleSize = 70f
        const val buttonsIconSize = 50f
        const val buttonsOffsetFromEdge = 5f
        val buttonsBackColor: Color = ImageGetter.CHARCOAL.cpy().apply { a = 0.67f }
    }

    // This Popup's body has two halves of same size, either side by side or arranged vertically
    // depending on screen proportions - determine height for one of those
    private val isPortrait = (previousScreen as BaseScreen).isPortrait()
    private val civBlocksWidth = stageToShowOn.width / 3 - 5f
    private val partHeight = stageToShowOn.height * (if (isPortrait) 0.45f else 0.8f)
    /** Widget offering Nations for the Pool (those that would be excluded) goes on the left/top */
    private val availableNationsListTable = Table()
    private val availableNationsListScroll = AutoScrollPane(availableNationsListTable)
    /** Widget selecting Nations for the Pool (those that could be chosen for a random slot) goes on the right/bottom */
    private val selectedNationsListTable = Table()
    private val selectedNationsListScroll = AutoScrollPane(selectedNationsListTable)
    /** sorted list of all major nations as ready-made button */
    private val allNationTables: ArrayList<NationTable>
    /** backing for the left side - unchosen Nations */
    private var availableNations: MutableSet<String>
    /** backing for the right side - chosen Nations */
    private var selectedNations: MutableSet<String>

    init {
        val sortedNations = previousScreen.ruleset.nations.values
                .filter { it.isMajorCiv }
                .filterNot { it.hasUnique(UniqueType.WillNotBeChosenForNewGames) }
                .sortedWith(compareBy(UncivGame.Current.settings.getCollatorFromLocale()) { it.name.tr(hideIcons = true) })
        allNationTables = ArrayList(
            sortedNations.map { NationTable(it, civBlocksWidth, 0f) }  // no need for min height
        )
        availableNations = sortedNations.map { it.name }.toMutableSet()
        selectedNations = gameParameters.randomNationsPool.intersect(availableNations) as MutableSet<String>
        availableNations.removeAll(selectedNations)

        availableNationsListScroll.setOverscroll(false, false)
        // size below uses civBlocksWidth +10, because the nation table has a 5f pad, for a total of +10f
        add("Banned nations".tr())
        if (isPortrait) {
            row()
            add(availableNationsListScroll).size( civBlocksWidth + 10f, partHeight ).row()
            addSeparator()
            add("Available nations".tr()).row()
        } else {
            add("Available nations".tr()).row()
            add(availableNationsListScroll).size( civBlocksWidth + 10f, partHeight )
        }
        selectedNationsListScroll.setOverscroll(false, false)
        add(selectedNationsListScroll).size(civBlocksWidth + 10f, partHeight)

        update()
        pack()

        val closeButton = getCloseButton(buttonsCircleSize, buttonsIconSize, buttonsBackColor, Color.FIREBRICK) { close() }
        closeButton.setPosition(buttonsOffsetFromEdge, buttonsOffsetFromEdge, Align.bottomLeft)
        innerTable.addActor(closeButton)
        clickBehindToClose = true

        val okButton = "OtherIcons/Checkmark".toImageButton(Color.LIME)
        okButton.onClick { returnSelected() }
        okButton.setPosition(innerTable.width - buttonsOffsetFromEdge, buttonsOffsetFromEdge, Align.bottomRight)
        innerTable.addActor(okButton)

        val switchButton = "OtherIcons/NationSwap".toImageButton(Color.YELLOW)
        switchButton.onClick { switchAllNations() }
        switchButton.setPosition(innerTable.width / 2, buttonsOffsetFromEdge + 35, Align.center)
        innerTable.addActor(switchButton)

        selectedNationsListTable.touchable = Touchable.enabled
    }

    fun update() {
        updateNationListTable(availableNationsListTable, availableNations) {
            nation -> { addNationToPool(nation) }
        }
        updateNationListTable(selectedNationsListTable, selectedNations) {
            nation -> { removeNationFromPool(nation) }
        }
    }

    private fun updateNationListTable(table: Table, nations: Set<String>, actionFactory: (Nation)->(()->Unit) ) {
        for (child in table.children) { child.listeners.clear() }
        table.clear()

        for (nationTable in allNationTables) {
            if (nationTable.nation.name !in nations) continue
            nationTable.onClick(actionFactory(nationTable.nation))
            table.add(nationTable).row()
        }
    }

    private fun String.toImageButton(overColor: Color) =
            toImageButton(buttonsIconSize, buttonsCircleSize, buttonsBackColor, overColor)

    private fun addNationToPool(nation: Nation) {
        availableNations.remove(nation.name)
        selectedNations.add(nation.name)

        update()
    }

    private fun removeNationFromPool(nation: Nation) {
        availableNations.add(nation.name)
        selectedNations.remove(nation.name)

        update()
    }

    private fun returnSelected() {
        close()
        gameParameters.randomNationsPool = ArrayList(selectedNations)
        onExit()
    }

    private fun switchAllNations() {
        val tempNations = availableNations
        availableNations = selectedNations
        selectedNations = tempNations
        update()
    }
}
