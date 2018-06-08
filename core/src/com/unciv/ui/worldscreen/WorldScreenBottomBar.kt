package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.worldscreen.unit.UnitTable

class WorldScreenBottomBar(val worldScreen: WorldScreen) : Table(){
    val unitTable = UnitTable(worldScreen)
    val battleTable = BattleTable(worldScreen)
    val tileInfoTable = TileInfoTable(worldScreen)

    init {

        add(unitTable).width(worldScreen.stage.width/3)
        add(battleTable).width(worldScreen.stage.width/3).fill() // so that background fills entire middle third
        add(tileInfoTable).width(worldScreen.stage.width/3).fill()

        val tileTableBackground = ImageGetter.getDrawable(ImageGetter.WhiteDot)
                .tint(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))
        tileTableBackground.minHeight = 0f
        tileTableBackground.minWidth = 0f
        background = tileTableBackground

        pack()
    }

    fun update(selectedTile: TileInfo?){
        unitTable.update()
        battleTable.update()
        if(selectedTile!=null) tileInfoTable.updateTileTable(selectedTile)
        pack()
    }
}

