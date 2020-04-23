package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.logic.map.TileInfo
import com.unciv.models.UncivSound
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import kotlin.math.roundToInt

class CityScreenTileTable(val cityScreen: CityScreen): Table(){
    val innerTable = Table()
    val city = cityScreen.city
    init{
        innerTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))
        add(innerTable).pad(2f).fill()
        background = ImageGetter.getBackground(Color.WHITE)
    }

    fun update(selectedTile: TileInfo?) {
        innerTable.clear()
        if (selectedTile == null){
            isVisible=false
            return
        }
        isVisible=true
        innerTable.clearChildren()

        val stats = selectedTile.getTileStats(city, city.civInfo)
        innerTable.pad(20f)

        innerTable.add(selectedTile.toString(city.civInfo).toLabel()).colspan(2)
        innerTable.row()
        innerTable.add(getTileStatsTable(stats)).row()


        if(selectedTile.getOwner()==null && selectedTile.neighbors.any {it.getCity()==city}
            && selectedTile in city.tilesInRange){
            val goldCostOfTile = city.expansion.getGoldCostOfTile(selectedTile)

            val buyTileButton = "Buy for [$goldCostOfTile] gold".toTextButton()
            buyTileButton.onClick(UncivSound.Coin) {
                city.expansion.buyTile(selectedTile)
                UncivGame.Current.setScreen(CityScreen(city))
            }
            if(goldCostOfTile>city.civInfo.gold || city.isPuppet || !UncivGame.Current.worldScreen.isPlayersTurn)
                buyTileButton.disable()

            innerTable.add(buyTileButton).row()
            innerTable.add("You have [${city.civInfo.gold}] gold".toLabel(Color.YELLOW, 16)).padTop(2f)
        }
        if(city.canAcquireTile(selectedTile)) {
            val acquireTileButton = "Acquire".toTextButton()
            acquireTileButton.onClick {
                city.expansion.takeOwnership(selectedTile)
                UncivGame.Current.setScreen(CityScreen(city))
            }
            innerTable.add(acquireTileButton).row()
        }

        if(city.workedTiles.contains(selectedTile.position)){
            if(selectedTile.isLocked()) {
                val unlockButton = "Unlock".toTextButton()
                unlockButton.onClick {
                    city.lockedTiles.remove(selectedTile.position)
                    update(selectedTile)
                    cityScreen.update()
                }
                innerTable.add(unlockButton).row()
            }
            else {
                val lockButton = "Lock".toTextButton()
                lockButton.onClick {
                    city.lockedTiles.add(selectedTile.position)
                    update(selectedTile)
                    cityScreen.update()
                }
                innerTable.add(lockButton).row()
            }
        }
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