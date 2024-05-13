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
import com.unciv.models.translations.tr
import com.unciv.ui.components.SmallButtonStyle
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
import com.unciv.ui.components.input.onRightClick
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
    private var moveSpyHereButtons = hashMapOf<MoveToCityButton, City?>()

    /** Readability shortcut */
    private val manager get() = civInfo.espionageManager

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
        for (spy in manager.spyList) {
            spySelectionTable.add(spy.name.toLabel())
            spySelectionTable.add(spy.rank.toLabel())
            spySelectionTable.add(spy.getLocationName().toLabel())
            val actionString = if (spy.action.hasTurns) "[${spy.action.displayString}] ${spy.turnsRemainingForAction}${Fonts.turn}"
                else spy.action.displayString
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
                    if (city == spy.getCityOrNull()) {
                        button.isVisible = true
                        button.setDirection(Align.right)
                    } else {
                        button.isVisible = city == null // hideout
                            || !city.espionage.hasSpyOf(civInfo)
                        button.setDirection(Align.left)
                    }
                }
            }
            moveSpyButton.onRightClick { 
                worldScreen.bottomUnitTable.selectSpy(spy)
                worldScreen.game.popScreen()
                worldScreen.shouldUpdate = true
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
        citySelectionTable.add("City".toLabel()).padTop(10f)
        citySelectionTable.add("Spy present".toLabel()).padTop(10f).row()

        // First add the hideout to the table

        citySelectionTable.add()
        citySelectionTable.add("Spy Hideout".toLabel())
        citySelectionTable.add(getSpyIcons(manager.getIdleSpies()))
        val moveSpyHereButton = MoveToCityButton(null)
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
        val label = city.name.toLabel(hideIcons = true)
        label.onClick {
            worldScreen.game.popScreen() // If a detour to this screen (i.e. not directly from worldScreen) is made possible, use resetToWorldScreen instead
            worldScreen.mapHolder.setCenterPosition(city.location)
        }
        citySelectionTable.add(label).fill()
        citySelectionTable.add(getSpyIcons(manager.getSpiesInCity(city)))

        val moveSpyHereButton = MoveToCityButton(city)
        citySelectionTable.add(moveSpyHereButton)
        citySelectionTable.row()
    }

    private fun getSpyIcon(spy: Spy) = Table().apply {
        add (ImageGetter.getImage("OtherIcons/Spy_White").apply {
            color = Color.WHITE
        }).size(30f)
        val color = when(spy.rank) {
            1 -> Color.BROWN
            2 -> Color.LIGHT_GRAY
            3 -> Color.GOLD
            else -> return@apply
        }
        val starTable = Table()
        repeat(spy.rank) {
            val star = ImageGetter.getImage("OtherIcons/Star")
            star.color = color
            starTable.add(star).size(8f).pad(1f).row()
        }
        add(starTable).center().padLeft(-4f)
    }

    private fun getSpyIcons(spies: Iterable<Spy>) = Table().apply {
        defaults().space(0f, 2f, 0f, 2f)
        for (spy in spies)
            add(getSpyIcon(spy))
    }

    // city == null is interpreted as 'spy hideout'
    private inner class MoveToCityButton(city: City?) : Button(SmallButtonStyle()) {
        val arrow = ImageGetter.getArrowImage(Align.left)
        init {
            arrow.setSize(24f)
            add(arrow).size(24f)
            arrow.setOrigin(Align.center)
            arrow.color = Color.WHITE
            onClick {
                selectedSpy!!.moveTo(city)
                resetSelection()
                update()
            }
            moveSpyHereButtons[this] = city
            isVisible = false
        }

        fun setDirection(align: Int) {
            arrow.rotation = if (align == Align.right) 0f else 180f
            isDisabled = align == Align.right
        }
    }

    private fun resetSelection() {
        selectedSpy = null
        selectedSpyButton?.label?.setText("Move".tr())
        selectedSpyButton = null
        for ((button, _) in moveSpyHereButtons)
            button.isVisible = false
    }
}
