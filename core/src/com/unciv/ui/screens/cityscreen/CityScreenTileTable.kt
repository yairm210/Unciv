package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.ui.objectdescriptions.TileDescription
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.AnimatedMenuPopup
import com.unciv.ui.popups.AnimatedMenuPopup.Companion.addContextMenu
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine.IconDisplay
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import com.unciv.view.CityView
import com.unciv.view.TileView
import yairm210.purity.annotations.Readonly
import kotlin.math.roundToInt

class CityScreenTileTable(private val cityScreen: CityScreen) : Table() {
    private val innerTable = Table()
    val cityView: CityView = cityScreen.cityView

    init {
        innerTable.background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/CityScreenTileTable/InnerTable",
            tintColor = BaseScreen.skinStrings.skinConfig.baseColor.darken(0.5f)
        )
        add(innerTable).pad(2f).fill()
        background = BaseScreen.skinStrings.getUiBackground("CityScreen/CityScreenTileTable/Background", tintColor = Color.WHITE)
    }

    fun update(tileView: TileView?) {
        innerTable.clear()
        if (tileView == null) {
            isVisible = false
            return
        }
        isVisible = true
        innerTable.clearChildren()

        val stats = tileView.getTileStats(cityView.viewingCiv(), cityView)
        innerTable.pad(5f)

        innerTable.add(MarkupRenderer.render(TileDescription.toMarkup(
            tileView,
            cityView.viewingCiv(),
            hideUnits = cityScreen.isSpying,
            spyCity = if (cityScreen.isSpying) cityView else null
        ), iconDisplay = IconDisplay.None) {
            cityScreen.openCivilopedia(it)
        })
        innerTable.row()
        innerTable.add(getTileStatsTable(stats)).row()

        if (cityView.canBuyTile(tileView)) {
            val goldCostOfTile = cityView.getGoldCostOfTile(tileView)
            val buyTileButton = "Buy for [$goldCostOfTile] gold".toTextButton()
            buyTileButton.onActivation(binding = KeyboardBinding.BuyTile) {
                buyTileButton.disable()
                cityScreen.askToBuyTile(tileView)
            }
            buyTileButton.addContextMenu { TileBuyMenu(buyTileButton) }
            buyTileButton.isEnabled = cityScreen.canChangeState && cityView.viewingCiv().hasStatToBuy(Stat.Gold, goldCostOfTile)
            innerTable.add(buyTileButton).padTop(5f).row()
        }

        val owningCity = tileView.owningCity()
        if (owningCity != null)
            innerTable.add("Owned by [${owningCity.name}]".toLabel()).row()

        val workingCity = tileView.getWorkingCity()
        if (workingCity != null)
            innerTable.add("Worked by [${workingCity.name}]".toLabel()).row()

        if (cityView.isWorked(tileView)) {
            if (tileView.isLocked()) {
                val unlockButton = "Unlock".toTextButton()
                unlockButton.onClick {
                    cityView.tryUnlockTile(tileView)
                    update(tileView)
                    cityScreen.updateAsync()
                }
                if (!cityScreen.canChangeState) unlockButton.disable()
                innerTable.add(unlockButton).padTop(5f).row()
            } else {
                val lockButton = "Lock".toTextButton()
                lockButton.onClick {
                    cityView.tryLockTile(tileView)
                    update(tileView)
                    cityScreen.updateAsync()
                }
                if (!cityScreen.canChangeState) lockButton.disable()
                innerTable.add(lockButton).padTop(5f).row()
            }
        }

        if (tileView.isCityCenter()) {
            val otherCityView = tileView.owningCity()?.tryGetCityView()
            if (otherCityView != null && otherCityView != cityView)
                innerTable.add("Move to city".toTextButton().onClick {
                    cityScreen.game.replaceCurrentScreen { CityScreen(otherCityView) }
                })
        }

        innerTable.pack()
        pack()
    }

    private fun getTileStatsTable(stats: Stats): Table {
        val statsTable = Table()
        statsTable.defaults().pad(2f)
        for ((key, value) in stats) {
            statsTable.add(ImageGetter.getStatIcon(key.name)).size(20f)
            statsTable.add(value.roundToInt().toLabel()).padRight(5f)
        }
        return statsTable
    }

    private inner class TileBuyMenu(buyTileButton: TextButton) : AnimatedMenuPopup(stage, buyTileButton) {
        override fun createContentTable(): Table? {
            val maxRing = cityView.getWorkRange()
            val counts = IntArray(maxRing + 1) { countBuyableInRing(it) }
            if (counts.sum() < 2) return null
            return super.createContentTable()!!.apply {
                add("Currently you have [${cityView.viewingCiv().gold}] [Gold].".toLabel(alignment = Align.center)).growX().row()
                for (ring in 0..maxRing) {
                    val count = counts[ring]
                    if (count == 0 || ring > 0 && count == counts[ring - 1]) continue
                    val cost = getRingCost(ring)
                    val text = "Buy [$count] tiles in ring [$ring] for [$cost][${Stat.Gold.character}]"
                    val button = getButton(text, KeyboardBinding.None) { buyRing(ring) }
                    button.isDisabled = cost > cityView.viewingCiv().gold
                    add(button).row()
                }
            }
        }

        @Readonly private fun getRing(ring: Int) = cityView.centerTile().getVisibleTilesInDistance(ring).filter { it.owningCity() == null }
        @Readonly private fun countBuyableInRing(ring: Int) = getRing(ring).count()
        @Readonly private fun getRingCost(ring: Int) = getRing(ring).withIndex()
            .sumOf { cityView.getGoldCostOfTile(it.value, it.index) }
        private fun buyRing(ring: Int) {
            for (tileView in getRing(ring)) {
                if (!cityView.tryBuyTile(tileView))
                    break
            }
            SoundPlayer.play(Stat.Gold.purchaseSound)
            cityScreen.game.replaceCurrentScreen { CityScreen(cityView) } // update doesn't redo the tiles
        }
    }
}
