package com.unciv.ui.worldscreen.bottombar

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.TileInfo
import com.unciv.ui.worldscreen.WorldScreen
import com.unciv.ui.worldscreen.unit.UnitTable

class WorldScreenBottomBar(val worldScreen: WorldScreen) : Table(){
    val unitTable = UnitTable(worldScreen)
    val tileInfoTable = TileInfoTable(worldScreen)

    init {
        add(unitTable).width(worldScreen.stage.width/3).fill()
        add().width(worldScreen.stage.width/3) // empty space for the battle table
        add(tileInfoTable).width(worldScreen.stage.width/3).fill()

        pack()
    }

    fun update(selectedTile: TileInfo?){
        unitTable.update()
        if(selectedTile!=null) tileInfoTable.updateTileTable(selectedTile)
        pack()
    }
}

