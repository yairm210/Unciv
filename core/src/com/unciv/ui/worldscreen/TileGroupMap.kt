package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.logic.HexMath
import com.unciv.ui.tilegroups.TileGroup

class TileGroupMap<T: TileGroup>(tileGroups:Collection<T>, padding:Float): Group(){
    init{
        var topX = -Float.MAX_VALUE
        var topY = -Float.MAX_VALUE
        var bottomX = Float.MAX_VALUE
        var bottomY = Float.MAX_VALUE

        for(tileGroup in tileGroups){
            val positionalVector = HexMath().hex2WorldCoords(tileGroup.tileInfo.position)
            val groupSize = 50
            tileGroup.setPosition(positionalVector.x * 0.8f * groupSize.toFloat(),
                    positionalVector.y * 0.8f * groupSize.toFloat())

            addActor(tileGroup)
            topX = Math.max(topX, tileGroup.x + groupSize)
            topY = Math.max(topY, tileGroup.y + groupSize)
            bottomX = Math.min(bottomX, tileGroup.x)
            bottomY = Math.min(bottomY, tileGroup.y)
        }

        for (group in tileGroups) {
            group.moveBy(-bottomX + padding, -bottomY + padding)
        }

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        setSize(topX - bottomX + padding*2, topY - bottomY + padding*2)
    }
}