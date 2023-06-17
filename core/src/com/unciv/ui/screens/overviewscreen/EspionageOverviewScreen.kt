package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.Spy
import com.unciv.logic.civilization.managers.SpyAction
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.components.AutoScrollPane
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.extensions.addSeparatorVertical
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton

/** Screen used for moving spies between cities */
class EspionageOverviewScreen(val civInfo: Civilization) : PickerScreen(true) {
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
        spySelectionTable.add("Spy".toLabel()).pad(10f)
        spySelectionTable.add("Location".toLabel()).pad(10f)
        spySelectionTable.add("Action".toLabel()).pad(10f).row()
        for (spy in civInfo.espionageManager.spyList) {
            spySelectionTable.add(spy.name.toLabel()).pad(10f)
            spySelectionTable.add(spy.getLocationName().toLabel()).pad(10f)
            val actionString =
                if (spy.action == SpyAction.None) SpyAction.None.stringName
                else "[${spy.action.stringName}] ${spy.timeTillActionFinish}${Fonts.turn}"
            spySelectionTable.add(actionString.toLabel()).pad(10f)

            val moveSpyButton = "Move".toTextButton()
            moveSpyButton.onClick {
                if (selectedSpyButton == moveSpyButton) {
                    resetSelection()
                    return@onClick
                }
                resetSelection()
                selectedSpyButton = moveSpyButton
                selectedSpy = spy
                selectedSpyButton!!.label.setText("Cancel".tr())
                for ((button, city) in moveSpyHereButtons)
                // For now, only allow spies to be send to cities of other major civs and their hideout
                // Not own cities as counterintelligence isn't implemented
                // Not city-state civs as rigging elections isn't implemented
                // Technically, stealing techs from other civs also isn't implemented, but its the first thing I'll add so this makes the most sense to allow.
                    if (city == null // hideout
                        || (city.civ.isMajorCiv()
                            && city.civ != civInfo
                            && !city.espionage.hasSpyOf(civInfo)
                        )
                    ) {
                        button.isVisible = true
                    }
            }
            spySelectionTable.add(moveSpyButton).pad(5f).row()
        }
    }

    private fun updateCityList() {
        citySelectionTable.clear()
        moveSpyHereButtons.clear()
        citySelectionTable.add().pad(5f)
        citySelectionTable.add("City".toLabel()).pad(5f)
        citySelectionTable.add("Spy present".toLabel()).pad(5f).row()

        // First add the hideout to the table

        citySelectionTable.add().pad(5f)
        citySelectionTable.add("Spy Hideout".toLabel()).pad(5f)
        citySelectionTable.add().pad(5f)
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
        citySelectionTable.add(ImageGetter.getNationPortrait(city.civ.nation, 30f)).pad(5f)
        citySelectionTable.add(city.name.toLabel()).pad(5f)
        if (city.espionage.hasSpyOf(civInfo)) {
            citySelectionTable.add(
                ImageGetter.getImage("OtherIcons/Spy_White").apply {
                    setSize(30f)
                    color = Color.WHITE
                }
            ).pad(5f)
        } else {
            citySelectionTable.add().pad(5f)
        }

        val moveSpyHereButton = getMoveToCityButton(city)
        citySelectionTable.add(moveSpyHereButton).pad(5f)
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
        if (selectedSpyButton != null)
            selectedSpyButton!!.label.setText("Move".tr())
        selectedSpyButton = null
        for ((button, _) in moveSpyHereButtons)
            button.isVisible = false
    }
}
