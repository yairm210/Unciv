package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.onClick

class CityScreenTileTable(val city: CityInfo): Table(){
    val innerTable = Table()
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
        val tile = selectedTile
        innerTable.clearChildren()

        val stats = tile.getTileStats(city, city.civInfo)
        innerTable.pad(20f)

        innerTable.add(Label(tile.toString(), CameraStageBaseScreen.skin)).colspan(2)
        innerTable.row()

        val statsTable = Table()
        statsTable.defaults().pad(2f)
        for (entry in stats.toHashMap().filterNot { it.value==0f }) {
            statsTable.add(ImageGetter.getStatIcon(entry.key.toString())).size(20f)
            statsTable.add(Label(Math.round(entry.value).toString(), CameraStageBaseScreen.skin))
            statsTable.row()
        }
        innerTable.add(statsTable).row()

        if(tile.getOwner()==null && tile.neighbors.any{it.getCity()==city}){
            val goldCostOfTile = city.expansion.getGoldCostOfTile(tile)
            val buyTileButton = TextButton("Buy for [$goldCostOfTile] gold".tr(), CameraStageBaseScreen.skin)
            buyTileButton.onClick("coin") { city.expansion.buyTile(tile); UnCivGame.Current.screen = CityScreen(city) }
            if(goldCostOfTile>city.civInfo.gold) buyTileButton.disable()
            innerTable.add(buyTileButton)
        }
        if(city.canAcquireTile(tile)){
            val acquireTileButton = TextButton("Acquire".tr(), CameraStageBaseScreen.skin)
            acquireTileButton.onClick { city.expansion.takeOwnership(tile); UnCivGame.Current.screen = CityScreen(city) }
            innerTable.add(acquireTileButton)
        }
        innerTable.pack()
        pack()
    }
}