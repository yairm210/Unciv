package com.unciv.ui.screens.espionage

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.models.Spy
import com.unciv.models.SpyAction
import com.unciv.models.translations.tr
import com.unciv.ui.components.SmallButtonStyle
import com.unciv.ui.components.extensions.addCapitalIndicator
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.getCloseButton
import com.unciv.ui.components.extensions.scrollTo
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import yairm210.purity.annotations.Pure
import kotlin.collections.iterator

/** On this screen, possible locations of spies are a [com.unciv.logic.city.City] or the Spy Hideout which we represent as `null` */
private typealias Location = City?

/** Screen used for moving spies between cities */
class EspionageScreen(val civInfo: Civilization, val worldScreen: WorldScreen) : BaseScreen() {
    private val collator = UncivGame.Current.settings.getCollatorFromLocale()

    private val spySelectionTable = Table()
    private val spyScrollPane = AutoScrollPane(spySelectionTable)
    private val locationSelectionTable = Table()
    private val locationScrollPane = AutoScrollPane(locationSelectionTable)
    private val middlePanes = SplitPane(spyScrollPane, locationScrollPane, false, skin)

    private var selectedSpyButton: TextButton? = null
    private var selectedSpy: Spy? = null

    // if the value == null, this means the Spy Hideout.
    private val spyActionButtons = hashMapOf<SpyLocationActionButton, Location>()
    private val moveSpyButtons = hashMapOf<Spy, TextButton>()

    private val locationLabels = mutableMapOf<String?, Actor>()
    private val closeButton: Group

    // With dynamic button text, the layout "flutters" when selecting/deselecting a Spy - so control the minWidth:
    // Which one is fatter is obvious in English, but in other languages not so much
    private val moveButtonWidth = "Move".toTextButton().prefWidth.coerceAtLeast(Constants.cancel.toTextButton().prefWidth)

    /** Readability shortcut */
    private val manager get() = civInfo.espionageManager

    private val isPortrait: Boolean // To detect when a resize needs re-layout

    init {
        spySelectionTable.defaults().space(10f)
        spySelectionTable.pad(10f).top()
        locationSelectionTable.defaults().space(5f)
        locationSelectionTable.pad(10f).top()

        update()

        middlePanes.minSplitAmount = 0.25f
        middlePanes.maxSplitAmount = 0.75f
        isPortrait = if (isPortrait()) {
            middlePanes.splitAmount = 0.33f
            middlePanes.isVertical = true
            true
        } else {
            middlePanes.splitAmount = getPrefSplitAmount()
            false
        }
        middlePanes.setFillParent(true)
        stage.addActor(middlePanes)

        closeButton = getCloseButton {
            civInfo.cache.updateViewableTiles()
            game.popScreen()
        }
        closeButton.keyShortcuts.add(KeyCharAndCode.BACK)
        positionCloseButton()
        stage.addActor(closeButton)
        globalShortcuts.add(KeyboardBinding.Civilopedia) { openCivilopedia("Tutorial/Espionage") }
    }

    private fun getPrefSplitAmount(): Float {
        val handleWidth = middlePanes.style.handle.minWidth
        val freeSpace = stage.width - handleWidth - spySelectionTable.prefWidth - locationSelectionTable.prefWidth
        val x = spySelectionTable.prefWidth + freeSpace.coerceAtLeast(handleWidth) / 2
        return (x / stage.width).coerceIn(0.3f, 0.7f)
    }

    private fun positionCloseButton() {
        closeButton.setPosition(stage.width - 10f, stage.height - 10f, Align.topRight)
    }

    // Not using `RecreateOnResize` because this one is fine with resizes, exept we want to flip SplitPane arrangement for portrait
    override fun resize(width: Int, height: Int) {
        super.resize(width, height)
        positionCloseButton()
        if (isPortrait() == isPortrait) return
        game.replaceCurrentScreen { EspionageScreen(civInfo, worldScreen) }
    }

    //region Update
    private fun initSpyHeader() = spySelectionTable.apply {
        add("Spy".toLabel())
        add("Rank".toLabel())
        add() // icon column
        add("Location".toLabel()).left()
        add("Action".toLabel())
        // button column: No header cell
        row()
    }

    private fun initLocationHeader() = locationSelectionTable.apply {
        add() // Empty column for location icon
        add("City".toLabel()).left()
        add("Spy present".toLabel())
        // button column: no header cell
        row()
    }

    private fun update() {
        updateSpyList()
        updateLocationList()
    }

    private fun updateSpyList() {
        spySelectionTable.clear()
        moveSpyButtons.clear()
        initSpyHeader()
        // Show spies in insertion order = order of seniority
        for (spy in manager.spyList) {
            addSpyToSelectionTable(spy)
        }
    }

    private fun addSpyToSelectionTable(spy: Spy) {
        // "Spy" column
        spySelectionTable.add(spy.name.toLabel(hideIcons = true))
        // "Rank" column — show effective rank (includes counter-intel building bonuses)
        spySelectionTable.add(spy.getEffectiveRank().toLabel())
        // icon column
        val spyLocation = spy.getCityOrNull()
        spySelectionTable.add(spyLocation.getIcon()).padRight(5f)
        // "Location" column
        val key = spy.getCityOrNull()?.id
        val label = spy.getLocationName().toLabel(hideIcons = true)
        spySelectionTable.add(label).left()
        label.onActivation {
            val actor = locationLabels[key]
            if (actor != null) locationScrollPane.scrollTo(actor, true)
        }
        // "Action" column
        val actionString = if (spy.action.showTurns)
            "[${spy.action.displayString}] ${spy.turnsRemainingForAction}${Fonts.turn}"
        else spy.action.displayString
        spySelectionTable.add(actionString.toLabel())
        // Move button column
        val moveSpyButton = "Move".toTextButton()
        moveSpyButton.onClick {
            onSpyClicked(moveSpyButton, spy)
        }
        moveSpyButton.onRightClick {
            onSpyRightClicked(spy)
        }
        if (!worldScreen.canChangeState || !spy.isAlive() || civInfo.isDefeated()) {
            // Spectators aren't allowed to move the spies of the Civs they are viewing
            moveSpyButton.disable()
        }
        spySelectionTable.add(moveSpyButton).minWidth(moveButtonWidth).row()
        moveSpyButtons[spy] = moveSpyButton
    }

    private fun updateLocationList() {
        locationSelectionTable.clear()
        spyActionButtons.clear()
        locationLabels.clear()
        initLocationHeader()

        @Suppress("DEPRECATION") // The replacement doesn't allow chaining filters
        val sortedLocations =
            sequenceOf<Location>(null) + // First add the hideout to the table
            civInfo.gameInfo.getCities() // Then add all cities
            .filter { civInfo.hasExplored(it.getCenterTile()) }
            .sortedWith(
                compareBy<City> {
                    it.civ != civInfo
                }.thenBy {
                    it.civ.isCityState
                }.thenBy(collator) {
                    it.civ.civName.tr(hideIcons = true)
                }.thenBy(collator) {
                    it.name.tr(hideIcons = true)
                }
            )
        for (location in sortedLocations) {
            addLocationToSelectionTable(location)
        }
    }

    private fun addLocationToSelectionTable(location: Location) {
        locationSelectionTable.add(location.getIcon()).padLeft(20f).padRight(10f)
        val key = location?.id
        val label = location.getLocationName().toLabel(hideIcons = true)
        locationLabels[key] = label
        if (location != null) {
            label.onClick {
                worldScreen.game.popScreen() // If a detour to this screen (i.e. not directly from worldScreen) is made possible, use resetToWorldScreen instead
                worldScreen.mapHolder.setCenterPosition(location.location.toHexCoord())
            }
        }
        locationSelectionTable.add(label).left()
        val spies = location.getSpies()
        locationSelectionTable.add(getSpyIcons(spies))

        val spy = spies.firstOrNull()
        if (location != null && location.civ.isCityState && spy != null && spy.canDoCoup()) {
            val coupButton = CoupButton(location, spy.action == SpyAction.Coup)
            locationSelectionTable.add(coupButton)
        } else {
            val moveSpyHereButton = MoveToLocationButton(location)
            locationSelectionTable.add(moveSpyHereButton)
        }
        locationSelectionTable.row()
    }

    private fun getSpyIcon(spy: Spy) = Table().apply {
        add(ImageGetter.getImage("OtherIcons/Spy_White").apply {
            color = Color.WHITE
        }).size(30f)
        @Pure
        fun getColor(rank: Int): Color = when (rank) {
            1 -> Color.BROWN
            2 -> Color.LIGHT_GRAY
            else -> Color.GOLD
        }

        val displayRank = spy.getEffectiveRank()

        // If we have 10 or more ranks, display them with a bigger star
        if (displayRank >= 10) {
            val star = ImageGetter.getImage("OtherIcons/Star")
            star.color = getColor(displayRank / 10)
            add(star).size(20f).pad(3f)
        }

        val color = getColor(displayRank)
        val starTable = Table()
        // Create a grid of up to 9 stars
        repeat(displayRank % 10) {
            val star = ImageGetter.getImage("OtherIcons/Star")
            star.color = color
            starTable.add(star).size(8f).pad(1f)
            if (it % 3 == 2)
                starTable.row()
        }
        add(starTable).center().padLeft(-4f)

        // Spectators aren't allowed to move the spies of the Civs they are viewing
        if (worldScreen.canChangeState && spy.isAlive() && !civInfo.isDefeated()) {
            onClick {
                onSpyClicked(moveSpyButtons[spy]!!, spy)
            }
            onRightClick {
                onSpyRightClicked(spy)
            }
        }
    }

    private fun getSpyIcons(spies: Iterable<Spy>) = Table().apply {
        defaults().space(0f, 2f, 0f, 2f)
        for (spy in spies)
            add(getSpyIcon(spy))
    }

    /** Get the name of a location: City name or Spy Hideout for (`[this]==null`) */
    private fun Location.getLocationName() =
        this?.name ?: "Spy Hideout"

    /** Get the spies at a location: Spies assigned to a City or Spies in the Hideout (`[this]==null`) */
    private fun Location.getSpies() =
        if (this == null) manager.getIdleSpies() else manager.getSpiesInCity(this)

    /** Get an icon for a location:
     *  City as Nation icon with optional Capital indicator, or the Hideout (`[this]==null`) */
    private fun Location.getIcon(): Actor {
        if (this == null)
            return ImageGetter.getImage("OtherIcons/Hideout").apply { setSize(33f) }

        val icon = ImageGetter.getNationPortrait(civ.nation, 30f)
        if (isCapital() && !civ.isCityState)
            icon.addCapitalIndicator()
        return icon
    }
    //endregion

    //region Interaction helpers
    private fun onSpyClicked(moveSpyButton: TextButton, spy: Spy) {
        if (selectedSpyButton == moveSpyButton) {
            resetSelection()
            return
        }
        resetSelection()
        selectedSpyButton = moveSpyButton
        selectedSpy = spy
        selectedSpyButton!!.label.setText(Constants.cancel.tr())
        for ((button, location) in spyActionButtons) {
            if (location == spy.getCityOrNull()) {
                button.isVisible = true
                button.setDirection(Align.right)
            } else {
                button.isVisible = location == null // hideout
                    || !location.espionage.hasSpyOf(civInfo)
                button.setDirection(Align.left)
            }
        }
    }

    private fun onSpyRightClicked(spy: Spy) {
        worldScreen.bottomUnitTable.selectSpy(worldScreen.selectedGameView.getSpyView(spy))
        worldScreen.game.popScreen()
        worldScreen.shouldUpdate = true
    }

    private fun resetSelection() {
        selectedSpy = null
        selectedSpyButton?.label?.setText("Move".tr())
        selectedSpyButton = null
        for ((button, _) in spyActionButtons)
            button.isVisible = false
    }
    //endregion

    //region Spy Movement Button classes
    private abstract inner class SpyLocationActionButton(protected val location: Location) : Button(SmallButtonStyle()) {
        init {
            onClick(::clickHandler)
            spyActionButtons[this] = location
            isVisible = false
        }
        protected abstract fun clickHandler()
        open fun setDirection(align: Int) {}
    }

    private inner class MoveToLocationButton(location: Location) : SpyLocationActionButton(location) {
        val arrow = ImageGetter.getArrowImage(Align.left)

        init {
            arrow.setSize(24f)
            add(arrow).size(24f)
            arrow.setOrigin(Align.center)
            arrow.color = Color.WHITE
        }

        private fun move() {
            selectedSpy!!.moveTo(location)
            resetSelection()
            update()
        }

        override fun clickHandler() {
            if (location != null
                && location.civ.civName != civInfo.civName
                && location.civ.isMajorCiv()
                && location.civ.knows(civInfo) // ensures the !! below won't crash
                && location.civ.getDiplomacyManager(civInfo)!!.hasFlag(DiplomacyFlags.AgreedToNotSendSpies)) {
                ConfirmPopup(
                    this@EspionageScreen,
                    // The promise isn't broken when you move the spy - only when they catch you stealing a tech
                    "This may result in breaking your promise to [${location.civ.civName}]",
                    "Move",
                    action = ::move
                ).open(force = true)
            } else {
                move()
            }
        }

        override fun setDirection(align: Int) {
            arrow.rotation = if (align == Align.right) 0f else 180f
            isDisabled = align == Align.right
        }
    }

    private inner class CoupButton(city: City, private val isCurrentAction: Boolean) : SpyLocationActionButton(city) {
        val fist = ImageGetter.getStatIcon("Resistance")

        init {
            fist.setSize(24f)
            add(fist).size(24f)
            fist.setOrigin(Align.center)
            fist.color = if (isCurrentAction) Color.WHITE else Color.DARK_GRAY
        }

        override fun clickHandler() {
            val spy = selectedSpy!!
            if (!isCurrentAction) {
                ConfirmPopup(
                    this@EspionageScreen,
                    "Do you want to stage a coup in [${location!!.civ.civName}] with a " +
                        "[${(selectedSpy!!.getCoupChanceOfSuccess(false) * 100f).toInt()}]% " +
                        "chance of success?",
                    "Stage Coup"
                ) {
                    spy.setAction(SpyAction.Coup, 1)
                    fist.color = Color.DARK_GRAY
                    update()
                }.open()
            } else {
                spy.setAction(SpyAction.CounterIntelligence, 10)
                fist.color = Color.WHITE
                update()
            }
        }
    }
    //endregion
}
