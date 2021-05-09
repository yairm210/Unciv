package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.map.TileInfo
import com.unciv.models.UncivSound
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import kotlin.math.roundToInt

class CityScreenTileTable(private val cityScreen: CityScreen): Table() {
    private val innerTable = Table()
    val city = cityScreen.city

    init {
        innerTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))
        add(innerTable).pad(2f).fill()
        background = ImageGetter.getBackground(Color.WHITE)
    }

    fun update(selectedTile: TileInfo?) {
        innerTable.clear()
        if (selectedTile == null) {
            isVisible = false
            return
        }
        isVisible = true
        innerTable.clearChildren()

        val stats = selectedTile.getTileStats(city, city.civInfo)
        innerTable.pad(5f)

        innerTable.add(selectedTile.toString(city.civInfo).toLabel()).colspan(2)
        innerTable.row()
        innerTable.add(getTileStatsTable(stats)).row()

        if (selectedTile.getOwner() == null && selectedTile.neighbors.any { it.getCity() == city }
                && selectedTile in city.tilesInRange) {
            val goldCostOfTile = city.expansion.getGoldCostOfTile(selectedTile)

            val buyTileButton = "Buy for [$goldCostOfTile] gold".toTextButton()
            buyTileButton.onClick(UncivSound.Coin) {
                val purchasePrompt = "Currently you have [${city.civInfo.gold}] gold.".tr() + "\n" +
                        "Would you like to purchase [Tile] for [$goldCostOfTile] gold?".tr()
                YesNoPopup(purchasePrompt, { city.expansion.buyTile(selectedTile);UncivGame.Current.setScreen(CityScreen(city)) }, cityScreen).open()
            }
            val canPurchase = goldCostOfTile == 0 || city.civInfo.gold >= goldCostOfTile
            if (!canPurchase && !city.civInfo.gameInfo.gameParameters.godMode
                    || city.isPuppet
                    || !cityScreen.canChangeState)
                buyTileButton.disable()

            innerTable.add(buyTileButton).row()
        }

        if (city.civInfo.cities.filterNot { it == city }.any { it.isWorked(selectedTile) })
            innerTable.add("Worked by [${selectedTile.getWorkingCity()!!.name}]".toLabel()).row()

        if (city.isWorked(selectedTile)) {
            if (selectedTile.isLocked()) {
                val unlockButton = "Unlock".toTextButton()
                unlockButton.onClick {
                    city.lockedTiles.remove(selectedTile.position)
                    update(selectedTile)
                    cityScreen.update()
                }
                if (!cityScreen.canChangeState) unlockButton.disable()
                innerTable.add(unlockButton).row()
            } else {
                val lockButton = "Lock".toTextButton()
                lockButton.onClick {
                    city.lockedTiles.add(selectedTile.position)
                    update(selectedTile)
                    cityScreen.update()
                }
                if (!cityScreen.canChangeState) lockButton.disable()
                innerTable.add(lockButton).row()
            }
        }
        if (selectedTile.isCityCenter() && selectedTile.getCity() != city && selectedTile.getCity()!!.civInfo == city.civInfo)
            innerTable.add("Move to city".toTextButton().onClick { cityScreen.game.setScreen(CityScreen(selectedTile.getCity()!!)) })

        innerTable.pack()
        pack()
    }

    private fun getTileStatsTable(stats: Stats): Table {
        val statsTable = Table()
        statsTable.defaults().pad(2f)
        for (entry in stats.toHashMap().filterNot { it.value == 0f }) {
            statsTable.add(ImageGetter.getStatIcon(entry.key.toString())).size(20f)
            statsTable.add(entry.value.roundToInt().toString().toLabel()).padRight(5f)
        }
        return statsTable
    }
}