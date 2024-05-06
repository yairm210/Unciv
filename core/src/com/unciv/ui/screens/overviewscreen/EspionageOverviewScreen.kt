package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.Spy
import com.unciv.models.SpyAction
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparatorVertical
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

/** Screen used for moving spies between cities */
class EspionageOverviewScreen(val civInfo: Civilization, val worldScreen: WorldScreen) : PickerScreen(true) {
    private val collator = UncivGame.Current.settings.getCollatorFromLocale()

    private val spySelectionTable = Table(skin)
    private val spyScrollPane = AutoScrollPane(spySelectionTable)
    private val citySelectionTable = Table(skin)
    private val cityScrollPane = AutoScrollPane(citySelectionTable)
    private val middlePanes = Table(skin)

    private var selectedSpyButton: TextButton? = null
    private var selectedSpy: Spy? = null

    // if the value == null, this means the Spy Hideout.
    private var moveSpyHereButtons = hashMapOf<Button, City?>()

    init {
        spySelectionTable.defaults().pad(10f)
        citySelectionTable.defaults().pad(5f)
        middlePanes.add(spyScrollPane)
        middlePanes.addSeparatorVertical()
        middlePanes.add(cityScrollPane)
        topTable.add(middlePanes)

        update()

        closeButton.isVisible = true
        closeButton.onActivation {
            civInfo.cache.updateViewableTiles()
            game.popScreen()
        }
        closeButton.keyShortcuts.add(KeyCharAndCode.BACK)
        rightSideButton.isVisible = false
    }

    private fun update() {
        updateSpyList()
        updateCityList()
    }

    private fun updateSpyList() {
        spySelectionTable.clear()
        spySelectionTable.add("Spy".toLabel())
        spySelectionTable.add("Rank".toLabel())
        spySelectionTable.add("Location".toLabel())
        spySelectionTable.add("Action".toLabel()).row()
        for (spy in civInfo.espionageManager.spyList) {
            spySelectionTable.add(spy.name.toLabel())
            spySelectionTable.add(spy.rank.toLabel())
            spySelectionTable.add(spy.getLocationName().toLabel())
            val actionString =
                when (spy.action) {
                    SpyAction.None, SpyAction.StealingTech, SpyAction.Surveillance, SpyAction.CounterIntelligence -> spy.action.displayString
                    SpyAction.Moving, SpyAction.EstablishNetwork, SpyAction.Dead, SpyAction.RiggingElections -> "[${spy.action.displayString}] ${spy.turnsRemainingForAction}${Fonts.turn}"
                }
            spySelectionTable.add(actionString.toLabel())

            val moveSpyButton = "Move".toTextButton()
            moveSpyButton.onClick {
                if (selectedSpyButton == moveSpyButton) {
                    resetSelection()
                    return@onClick
                }
                resetSelection()
                selectedSpyButton = moveSpyButton
                selectedSpy = spy
                selectedSpyButton!!.label.setText(Constants.cancel.tr())
                for ((button, city) in moveSpyHereButtons) {
                    // Not own cities as counterintelligence isn't implemented
                    // Not city-state civs as rigging elections isn't implemented
                    button.isVisible = city == null // hideout
                        || (city.civ != civInfo && !city.espionage.hasSpyOf(civInfo))
                }
            }
            if (!worldScreen.canChangeState || !spy.isAlive()) {
                // Spectators aren't allowed to move the spies of the Civs they are viewing
                moveSpyButton.disable()
            }
            spySelectionTable.add(moveSpyButton).pad(5f, 10f, 5f, 20f).row()
        }
    }

    private fun updateCityList() {
        citySelectionTable.clear()
        moveSpyHereButtons.clear()
        citySelectionTable.add()
        citySelectionTable.add("City".toLabel())
        citySelectionTable.add("Spy present".toLabel()).row()

        // First add the hideout to the table

        citySelectionTable.add()
        citySelectionTable.add("Spy Hideout".toLabel())
        citySelectionTable.add()
        val moveSpyHereButton = getMoveToCityButton(null)
        citySelectionTable.add(moveSpyHereButton).row()

        // Then add all cities

        val sortedCities = civInfo.gameInfo.getCities()
            .filter { civInfo.hasExplored(it.getCenterTile()) }
            .sortedWith(
                compareBy<City> {
                    it.civ != civInfo
                }.thenBy {
                    it.civ.isCityState()
                }.thenBy(collator) {
                    it.civ.civName.tr(hideIcons = true)
                }.thenBy(collator) {
                    it.name.tr(hideIcons = true)
                }
            )
        for (city in sortedCities) {
            addCityToSelectionTable(city)
        }
    }

    private fun addCityToSelectionTable(city: City) {
        citySelectionTable.add(ImageGetter.getNationPortrait(city.civ.nation, 30f))
            .padLeft(20f)
        citySelectionTable.add(city.name.toLabel(hideIcons = true))
        if (city.espionage.hasSpyOf(civInfo)) {
            citySelectionTable.add(
                ImageGetter.getImage("OtherIcons/Spy_White").apply {
                    setSize(30f)
                    color = Color.WHITE
                }
            )
        } else {
            citySelectionTable.add()
        }

        val moveSpyHereButton = getMoveToCityButton(city)
        citySelectionTable.add(moveSpyHereButton)
        citySelectionTable.row()
    }

    // city == null is interpreted as 'spy hideout'
    private fun getMoveToCityButton(city: City?): Button {
        val moveSpyHereButton = Button(skin)
        moveSpyHereButton.add(ImageGetter.getArrowImage(Align.left).apply { color = Color.WHITE })
        moveSpyHereButton.onClick {
            selectedSpy!!.moveTo(city)
            resetSelection()
            update()
        }
        moveSpyHereButtons[moveSpyHereButton] = city
        moveSpyHereButton.isVisible = false
        return moveSpyHereButton
    }

    private fun resetSelection() {
        selectedSpy = null
        selectedSpyButton?.label?.setText("Move".tr())
        selectedSpyButton = null
        for ((button, _) in moveSpyHereButtons)
            button.isVisible = false
    }
}
