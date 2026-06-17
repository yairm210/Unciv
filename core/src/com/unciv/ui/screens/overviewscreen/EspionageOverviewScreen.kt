package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
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
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import yairm210.purity.annotations.Pure

/** On this screen, possible locations of spies are a [City] or the Spy Hideout which we represent as `null` */
private typealias Location = City?

/** Screen used for moving spies between cities */
class EspionageOverviewScreen(val civInfo: Civilization, val worldScreen: WorldScreen) : PickerScreen(true) {
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

    /** Readability shortcut */
    private val manager get() = civInfo.espionageManager

    init {
        spySelectionTable.defaults().space(10f)
        spySelectionTable.pad(10f).top()
        locationSelectionTable.defaults().space(5f)
        locationSelectionTable.pad(10f).top()

        update()

        middlePanes.minSplitAmount = 0.25f
        middlePanes.maxSplitAmount = 0.75f
        middlePanes.splitAmount = getPrefSplitAmount()
        topTable.add(middlePanes).grow()

        closeButton.isVisible = true
        closeButton.onActivation {
            civInfo.cache.updateViewableTiles()
            game.popScreen()
        }
        closeButton.keyShortcuts.add(KeyCharAndCode.BACK)
        rightSideButton.isVisible = false
    }

    private fun getPrefSplitAmount(): Float {
        val handleWidth = middlePanes.style.handle.minWidth
        val freeSpace = stage.width - handleWidth - spySelectionTable.prefWidth - locationSelectionTable.prefWidth
        val x = spySelectionTable.prefWidth + freeSpace.coerceAtLeast(handleWidth) / 2
        return (x / stage.width).coerceIn(0.3f, 0.7f)
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
        // "Rank" column
        spySelectionTable.add(spy.rank.toLabel())
        // icon column
        val spyLocation = spy.getCityOrNull()
        spySelectionTable.add(spyLocation.getIcon()).padRight(5f)
        // "Location" column
        spySelectionTable.add(spy.getLocationName().toLabel(hideIcons = true)).left()
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
        spySelectionTable.add(moveSpyButton).row()
        moveSpyButtons[spy] = moveSpyButton
    }

    private fun updateLocationList() {
        locationSelectionTable.clear()
        spyActionButtons.clear()
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
        val label = location.getLocationName().toLabel(hideIcons = true)
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

        // If we have 10 or more ranks, display them with a bigger star
        if (spy.rank >= 10) {
            val star = ImageGetter.getImage("OtherIcons/Star")
            star.color = getColor(spy.rank / 10)
            add(star).size(20f).pad(3f)
        }

        val color = getColor(spy.rank)
        val starTable = Table()
        // Create a grid of up to 9 stars
        repeat(spy.rank % 10) {
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
        if (!isCapital() || civ.isCityState) return icon

        icon.addActor(ImageGetter.getImage("OtherIcons/Capital").apply {
            color = Color.BLACK.cpy().apply { a = 0.4f }
            setSize(22f)
            setPosition(29f, 27f, Align.center)
        })
        icon.addActor(ImageGetter.getImage("OtherIcons/Capital").apply {
            setSize(18f)
            setPosition(28f, 28f, Align.center)
        })
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
        worldScreen.bottomUnitTable.selectSpy(spy)
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
                ConfirmPopup(this@EspionageOverviewScreen,
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
                ConfirmPopup(this@EspionageOverviewScreen,
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
